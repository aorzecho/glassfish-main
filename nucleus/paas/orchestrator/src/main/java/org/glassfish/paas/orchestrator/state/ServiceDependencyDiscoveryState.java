/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.paas.orchestrator.state;

import org.glassfish.api.deployment.DeploymentContext;
import org.glassfish.api.deployment.archive.ReadableArchive;
import org.glassfish.paas.orchestrator.*;
import org.glassfish.paas.orchestrator.service.ServiceType;
import org.glassfish.paas.orchestrator.service.metadata.ServiceDescription;
import org.glassfish.paas.orchestrator.service.metadata.ServiceMetadata;
import org.glassfish.paas.orchestrator.service.metadata.ServiceReference;
import org.glassfish.paas.orchestrator.service.spi.Plugin;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.Habitat;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Jagadish Ramu
 */
@Service
public class ServiceDependencyDiscoveryState extends AbstractPaaSDeploymentState {

    @Inject
    private Habitat habitat;

    private static Logger logger = Logger.getLogger(ServiceOrchestratorImpl.class.getName());

    public void handle(PaaSDeploymentContext context) throws PaaSDeploymentException{
        try{
            ServiceMetadata appServiceMetadata = serviceDependencyDiscovery(context);
            final ServiceOrchestratorImpl orchestrator = context.getOrchestrator();
            String appName = context.getAppName();
            //registering metadata with Orchestrator must be the last operation (only if service dependency discovery
            //completes without any errors).
            orchestrator.addServiceMetadata(appName, appServiceMetadata);
        }catch(Exception e){
            throw new PaaSDeploymentException(e);
        }
    }

    public Class getRollbackState() {
        return null;
    }

    private ServiceMetadata serviceDependencyDiscovery(PaaSDeploymentContext context) throws PaaSDeploymentException {
        logger.entering(getClass().getName(), "serviceDependencyDiscovery");

        final DeploymentContext dc = context.getDeploymentContext();
        String appName = context.getAppName();
        final ReadableArchive archive = dc.getSource();

        return getServiceDependencyMetadata(context, appName, archive);
    }

    public ServiceMetadata getServiceDependencyMetadata(PaaSDeploymentContext context,
                                                        String appName, ReadableArchive archive)
    throws PaaSDeploymentException {

        final ServiceOrchestratorImpl orchestrator = context.getOrchestrator();
        Set<Plugin> installedPlugins = orchestrator.getPlugins();
        try {
            //1. SERVICE DISCOVERY
            //parse glassfish-services.xml to get all declared SRs and SDs
            //Get the first ServicesXMLParser implementation

            ServicesXMLParser parser = habitat.getByContract(ServicesXMLParser.class);

            //1.1 discover all Service References and Descriptions already declared for this application
            ServiceMetadata appServiceMetadata = parser.discoverDeclaredServices(appName, archive);

            //if no meta-data is found, create empty ServiceMetadata
            if (appServiceMetadata == null) {
                appServiceMetadata = new ServiceMetadata();
                appServiceMetadata.setAppName(appName);
            }

            logger.log(Level.INFO, "Discovered declared service metadata via glassfish-services.xml = " + appServiceMetadata);

            Map<ServiceDescription, Plugin> pluginsToHandleSDs = new LinkedHashMap<ServiceDescription, Plugin>();

            for (ServiceDescription sd : appServiceMetadata.getServiceDescriptions()) {
                //Get the list of plugins that handle a particular service-description.
                List<Plugin> pluginsList = new ArrayList<Plugin>();
                for (Plugin svcPlugin : installedPlugins) {
                    if (svcPlugin.handles(sd)) {
                        pluginsList.add(svcPlugin);
                    }
                }
                //resolve the list of plugins to one plugin.
                if (pluginsList.size() == 1) {
                    pluginsToHandleSDs.put(sd, pluginsList.get(0));
                    sd.setPlugin(pluginsList.get(0));
                } else if (pluginsList.size() > 1) {
                    //resolve the conflict via default plugin defined in configuration.
                    Plugin defaultPlugin = null;
                    ServiceType type = pluginsList.get(0).getServiceType();
                    defaultPlugin = orchestrator.getDefaultPlugin(pluginsList, type.toString());
                    if (defaultPlugin != null) {
                        pluginsToHandleSDs.put(sd, defaultPlugin);
                        sd.setPlugin(defaultPlugin);
                    } else {
                        throw new PaaSDeploymentException("Unable to resolve conflict between multiple " +
                                "service-provisioning-engines that handle service-description [" + sd.getName() + "]");
                    }
                }
            }


            //determine the list of plugins that handle the archive.
            Map<ServiceType, List<Plugin>> matchingPlugins = new HashMap<ServiceType, List<Plugin>>();
            for (Plugin svcPlugin : installedPlugins) {
                if (svcPlugin.handles(archive)) {
                    List<Plugin> plugins = matchingPlugins.get(svcPlugin.getServiceType());
                    if (plugins == null) {
                        plugins = new ArrayList<Plugin>();
                        matchingPlugins.put(svcPlugin.getServiceType(), plugins);
                    }
                    plugins.add(svcPlugin);
                }
            }

            //resolve the list to one plugin per service-type
            List<Plugin> resolvedPluginsList = new ArrayList<Plugin>();
            //check for duplicate plugins and resolve them.
            for (ServiceType type : matchingPlugins.keySet()) {
                List<Plugin> plugins = matchingPlugins.get(type);
                if (plugins.size() > 1) {
                    Plugin plugin = orchestrator.getDefaultPlugin(plugins, type.toString());
                    if (plugin != null) {
                        resolvedPluginsList.add(plugin);
                    } else {
                        throw new PaaSDeploymentException("Unable to resolve conflict between multiple " +
                                "service-provisioning-engines of type [" + type + "] that handle the archive");
                    }
                }else if(plugins.size() == 1){
                    resolvedPluginsList.add(plugins.get(0));
                }
            }

            //1.2 Get implicit service-descriptions (for instance a war is deployed, and it has not
            //specified a javaee service-description in its orchestration.xml, the PaaS runtime
            //through the GlassFish plugin that a default javaee service-description
            //is implied
            for (Plugin svcPlugin : resolvedPluginsList) {
                //if (svcPlugin.handles(archive)) {
                //If a ServiceDescription has not been declared explicitly in
                //the application for the plugin's type, ask the plugin (since it
                //supports this type of archive) if it has any implicit
                //service-description for this application
                if (!serviceDescriptionExistsForType(appServiceMetadata, svcPlugin.getServiceType())) {
                    Set<ServiceDescription> implicitServiceDescs = svcPlugin.getImplicitServiceDescriptions(archive, appName);

                    for (ServiceDescription sd : implicitServiceDescs) {
                        pluginsToHandleSDs.put(sd, svcPlugin);
                        sd.setPlugin(svcPlugin);
                    }
                    for (ServiceDescription sd : implicitServiceDescs) {
                        logger.log(Level.INFO, "Implicit ServiceDescription:" + sd);
                        appServiceMetadata.addServiceDescription(sd);
                    }
                }
                //}
            }

            setPluginForSD(orchestrator, pluginsToHandleSDs, installedPlugins, appServiceMetadata);

            logger.log(Level.INFO, "After adding implicit ServiceDescriptions = " + appServiceMetadata);


            //1.2 Get implicit ServiceReferences
            for (Plugin svcPlugin : resolvedPluginsList) {
                //if (svcPlugin.handles(archive)) {
                Set<ServiceReference> implicitServiceRefs = svcPlugin.getServiceReferences(appName, archive);
                for (ServiceReference sr : implicitServiceRefs) {
                    logger.log(Level.INFO, "ServiceReference:" + sr);
                    appServiceMetadata.addServiceReference(sr);
                }
                //}
            }
            logger.log(Level.INFO, "After adding ServiceReferences = " + appServiceMetadata);
            Map<String, Plugin> existingSDs = new HashMap<String, Plugin>();
            Map<ServiceReference, ServiceDescription> serviceRefToSD = new HashMap<ServiceReference, ServiceDescription>();

            //1.3 Ensure all service references have a related service description
            Set<ServiceDescription> appSDs = appServiceMetadata.getServiceDescriptions();
            Set<ServiceReference> appSRs = appServiceMetadata.getServiceReferences();
            for (ServiceReference sr : appSRs) {
                String targetSD = sr.getTarget();
                String svcRefType = sr.getServiceRefType();
                boolean serviceDescriptionExists = false;
                for (ServiceDescription sd : appSDs) {
                    //XXX: For now we assume all SRs are satisfied by app-scoped SDs
                    //In the future this has to be modified to search in global SDs
                    //as well
                    if (sd.getName().equals(targetSD)) {
                        serviceDescriptionExists = true;
                        sd.addServiceReference(sr);
                    }
                }

                if (!serviceDescriptionExists) {
                    List<Plugin> pluginsList = new ArrayList<Plugin>();
                    for (Plugin plugin : installedPlugins) {
                        if (plugin.isReferenceTypeSupported(sr.getServiceRefType())) {
                            pluginsList.add(plugin);
                        }
                    }
                    Plugin matchingPlugin = null;
                    if (pluginsList.size() == 1) {
                        matchingPlugin = pluginsList.get(0);
                    } else if (pluginsList.size() == 0) {
                        throw new PaaSDeploymentException("No service-provisioning-engine available to handle service-ref [ " + sr + " ]");
                    } else {
                        matchingPlugin = orchestrator.getDefaultPluginForServiceRef(sr.getServiceRefType());
                    }

                    if (matchingPlugin == null) {
                        //we could not find a matching plugin as there is no default plugin.
                        //get a plugin that handles this service-ref
                        Collection<Plugin> plugins = pluginsToHandleSDs.values();
                        for (Plugin plugin : plugins) {
                            if (plugin.isReferenceTypeSupported(sr.getServiceRefType())) {
                                matchingPlugin = plugin;
                                break;
                            }
                        }
                    }


                    ServiceDescription matchingSDForServiceRef = null;

                    if (pluginsToHandleSDs.values().contains(matchingPlugin)) {
                        //get an existing SD for the plugin in question.
                        for (Map.Entry<ServiceDescription, Plugin> entry : pluginsToHandleSDs.entrySet()) {
                            if (entry.getValue().equals(matchingPlugin)) {
                                matchingSDForServiceRef = entry.getKey();
                                break;
                            }
                        }
                    } else {
                        //get the default SD for the plugin.
                        matchingSDForServiceRef = matchingPlugin.getDefaultServiceDescription(appName, sr);
                        appServiceMetadata.addServiceDescription(matchingSDForServiceRef);
                        pluginsToHandleSDs.put(matchingSDForServiceRef, matchingPlugin);
                    }
                    serviceRefToSD.put(sr, matchingSDForServiceRef);
                    matchingSDForServiceRef.addServiceReference(sr);
                }

/*
                Set<ServiceDescription> matchingSDs = new HashSet<ServiceDescription>();
                if(!serviceDescriptionExists){
                    for(ServiceDescription sd : appSDs){
                        Plugin plugin = orchestrator.getPluginForServiceType(orchestrator.getPlugins(), sd.getServiceType());
                        //Plugin plugin = pluginsToHandleSDs.get(sd);
                        if(plugin != null){
                            if(plugin.isReferenceTypeSupported(sr.getServiceRefType())){
                                matchingSDs.add(sd);
                            }
                        }
                    }
                    if(matchingSDs.size() == 1){
                        //we found exactly one matching service-description.
                        serviceDescriptionExists = true;
                    }
                }

                if (!serviceDescriptionExists) {
                    //create a default SD for this service ref and add to application's
                    //service metadata
                    for (Plugin svcPlugin : installedPlugins) {
                        if (svcPlugin.isReferenceTypeSupported(svcRefType)) {
                            ServiceDescription defSD = svcPlugin.getDefaultServiceDescription(appName, sr);
                            if (existingSDs.containsKey(defSD.getName())) {
                                Plugin plugin = existingSDs.get(defSD.getName());
                                if (svcPlugin.getClass().equals(plugin.getClass())
                                        && svcPlugin.getServiceType().equals(plugin.getServiceType())) {
                                    //service description provided by same plugin, avoid adding the service-description.
                                    continue;
                                } else {
                                    existingSDs.put(defSD.getName(), svcPlugin);
                                }
                            } else {
                                existingSDs.put(defSD.getName(), svcPlugin);
                            }
                            addServiceDescriptionWithoutDuplicate(appServiceMetadata, defSD);
                            continue; //ignore rest of the plugins
                        }
                    }
                }*/
            }
            setPluginForSD(orchestrator, pluginsToHandleSDs, installedPlugins, appServiceMetadata);


            assertMetadataComplete(appSDs, appSRs);

            //set virtual-cluster name
            String virtualClusterName = orchestrator.getVirtualClusterName(appServiceMetadata);
            for (ServiceDescription sd : appServiceMetadata.getServiceDescriptions()) {
                sd.setVirtualClusterName(virtualClusterName);
            }

            logger.log(Level.INFO, "Final Service Metadata = " + appServiceMetadata);
            context.setAction(PaaSDeploymentContext.Action.PROCEED);
            return appServiceMetadata;
        } catch (Exception e) {
            context.setAction(PaaSDeploymentContext.Action.ROLLBACK);
            throw new PaaSDeploymentException(e);
        }
        //return null;
    }

    private void setPluginForSD(ServiceOrchestratorImpl orchestrator, Map<ServiceDescription, Plugin> pluginsToHandleSDs,
                                Set<Plugin> installedPlugins, ServiceMetadata appServiceMetadata) throws PaaSDeploymentException {
        //make sure that each service-description has a plugin.
        for (ServiceDescription sd : appServiceMetadata.getServiceDescriptions()) {
            if (sd.getPlugin() == null) {
                List<Plugin> matchingPluginsForSDs = new ArrayList<Plugin>();
                for (Plugin plugin : installedPlugins) {
                    if (plugin.getServiceType().toString().equals(sd.getServiceType())) {
                        matchingPluginsForSDs.add(plugin);
                    }
                }

                if (matchingPluginsForSDs.size() == 1) {
                    sd.setPlugin(matchingPluginsForSDs.get(0));
                    pluginsToHandleSDs.put(sd, matchingPluginsForSDs.get(0));
                } else if (matchingPluginsForSDs.size() == 0) {
                    throw new PaaSDeploymentException("Unable to find a service-provisioning-engine that handles" +
                            "service-description [" + sd.getName() + "] of type [" + sd.getServiceType() + "]");
                } else {
                    Plugin plugin = orchestrator.getDefaultPlugin(matchingPluginsForSDs, sd.getServiceType());
                    if (plugin != null) {
                        sd.setPlugin(plugin);
                        pluginsToHandleSDs.put(sd, plugin);
                    } else {
                        throw new PaaSDeploymentException("Unable to resolve conflict among multiple service-provisioning-engines that handle" +
                                "service-description [" + sd.getName() + "] of type [" + sd.getServiceType() + "]");
                    }
                }
            }
        }
    }

    private void addServiceDescriptionWithoutDuplicate(ServiceMetadata appServiceMetadata, ServiceDescription defSD) {
        Set<ServiceDescription> serviceDescriptions = appServiceMetadata.getServiceDescriptions();
        for (ServiceDescription sd : serviceDescriptions) {
            if (sd.getName().equals(defSD.getName())) {
                if (sd.getServiceType().equals(defSD.getServiceType())) {
                    return; //duplicate. We may also have to check whether its provided by same plugin
                    //or implement equals in service-description so as to make it easier for comparisons.
                }
            }
        }
        appServiceMetadata.addServiceDescription(defSD);
    }

    private void assertMetadataComplete(Set<ServiceDescription> appSDs,
                                        Set<ServiceReference> appSRs) {
        //Assert that all SRs have their corresponding SDs
        for (ServiceReference sr : appSRs) {
            String targetSD = sr.getTarget();
            boolean serviceDescriptionExists = false;
            for (ServiceDescription sd : appSDs) {
                if (sd.getName().equals(targetSD)) {
                    serviceDescriptionExists = true;
                }
            }
            assert serviceDescriptionExists;
        }
    }

    private boolean serviceDescriptionExistsForType(
            ServiceMetadata appServiceMetadata, ServiceType svcType) {
        for (ServiceDescription sd : appServiceMetadata.getServiceDescriptions()) {
            if (sd.getServiceType().equalsIgnoreCase(svcType.toString())) return true;
        }
        return false;
    }
}
