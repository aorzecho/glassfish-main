/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.appserv.connectors.internal.api;

import com.sun.enterprise.deployment.util.ModuleDescriptor;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.component.Habitat;
import org.glassfish.internal.data.ApplicationInfo;
import org.glassfish.internal.data.ApplicationRegistry;
import org.glassfish.internal.api.ConnectorClassFinder;
import org.jvnet.hk2.config.types.Property;
import com.sun.enterprise.deployment.*;
import com.sun.enterprise.deployment.Application;
import com.sun.enterprise.deployment.util.DOLUtils;
import com.sun.enterprise.deployment.runtime.connector.SunConnector;
import com.sun.enterprise.deployment.runtime.connector.ResourceAdapter;
import com.sun.enterprise.config.serverbeans.*;
import com.sun.enterprise.config.serverbeans.Resource;
import com.sun.logging.LogDomains;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


@Service
public class AppSpecificConnectorClassLoaderUtil {

    @Inject
    ApplicationRegistry appRegistry;

    @Inject
    Habitat habitat;

    private Logger _logger = LogDomains.getLogger(ConnectorRuntime.class, LogDomains.RSR_LOGGER);

    /**
     * {@inheritDoc}
     */
    public void detectReferredRARs(String appName) {
        ApplicationInfo appInfo = appRegistry.get(appName);

        //call to detectReferredRAs can be called only when appInfo is available
        if (appInfo == null) {
            throw new IllegalStateException("ApplicationInfo is not available for application [ " + appName + " ]");
        }
        Application app = appInfo.getMetaData(Application.class);

        if(!appInfo.isJavaEEApp()){
            if(_logger.isLoggable(Level.FINEST)){
                _logger.finest("Application ["+appName+"] is not a Java EE application, skipping " +
                        "resource-adapter references detection");
            }
            return;
        }

        // Iterate through all bundle descriptors, ejb-descriptors, managed-bean descriptors
        // for references to resource-adapters
        //
        // References can be via :
        // resource-ref
        // resource-env-ref
        // ra-mid
        //
        // Resource definition can be found in :
        // domain.xml
        // sun-ra.xml
        // default connector resource

        //handle application.xml bundle descriptor
        processDescriptorForRAReferences(app, null, app);

        Collection<BundleDescriptor> bundleDescriptors = app.getBundleDescriptors();

        //bundle descriptors
        for (BundleDescriptor bundleDesc : bundleDescriptors) {

            String moduleName = getModuleName(bundleDesc, app);

            if (bundleDesc instanceof JndiNameEnvironment) {
                processDescriptorForRAReferences(app, moduleName, bundleDesc);
            }
            // ejb descriptors
            if (bundleDesc instanceof EjbBundleDescriptor) {
                EjbBundleDescriptor ejbDesc = (EjbBundleDescriptor) bundleDesc;
                Set<EjbDescriptor> ejbDescriptors = ejbDesc.getEjbs();
                for (EjbDescriptor ejbDescriptor : ejbDescriptors) {
                    processDescriptorForRAReferences(app, moduleName, ejbDescriptor);

                    if (ejbDescriptor instanceof EjbMessageBeanDescriptor) {
                        EjbMessageBeanDescriptor messageBeanDesc = (EjbMessageBeanDescriptor) ejbDescriptor;
                        String raMid = messageBeanDesc.getResourceAdapterMid();
                        //there seem to be applications that do not specify ra-mid
                        if (raMid != null) {
                            app.addResourceAdapter(raMid);
                        }
                    }
                }
                //ejb interceptors
                Set<EjbInterceptor> ejbInterceptors = ejbDesc.getInterceptors();
                for (EjbInterceptor ejbInterceptor : ejbInterceptors) {
                    processDescriptorForRAReferences(app, moduleName, ejbInterceptor);
                }

            }
            // managed bean descriptors
            Set<ManagedBeanDescriptor> managedBeanDescriptors = bundleDesc.getManagedBeans();
            for (ManagedBeanDescriptor mbd : managedBeanDescriptors) {
                processDescriptorForRAReferences(app, moduleName, mbd);
            }
        }
    }

    private String getModuleName(BundleDescriptor bundleDesc, Application app) {
        Set<ModuleDescriptor<BundleDescriptor>> moduleDescriptors = app.getModules();
        if(moduleDescriptors != null){
            for(ModuleDescriptor moduleDesc : moduleDescriptors){
                if(bundleDesc.equals(moduleDesc.getDescriptor())){
                    return moduleDesc.getModuleName();
                }
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public Set<String> getRARsReferredByApplication(String appName) {
        ApplicationInfo appInfo = appRegistry.get(appName);
        if (appInfo != null) {
            Application app = appInfo.getMetaData(Application.class);
            if(appInfo.isJavaEEApp()){
                return app.getResourceAdapters();
            }
        }
        return new HashSet<String>();
    }

    private void processDescriptorForRAReferences(com.sun.enterprise.deployment.Application app,
                                                  String moduleName, Descriptor descriptor) {
        if (descriptor instanceof JndiNameEnvironment) {
            JndiNameEnvironment jndiEnv = (JndiNameEnvironment) descriptor;

            // resource-ref
            for (Object resourceRef : jndiEnv.getResourceReferenceDescriptors()) {
                ResourceReferenceDescriptor resRefDesc = (ResourceReferenceDescriptor) resourceRef;
                String jndiName = resRefDesc.getJndiName();
                //ignore refs where jndi-name is not available
                if(jndiName != null){
                    detectResourceInRA(app, moduleName, jndiName);
                }
            }

            // resource-env-ref
            for (Object jmsDestRef : jndiEnv.getJmsDestinationReferenceDescriptors()) {
                JmsDestinationReferenceDescriptor jmsDestRefDesc = (JmsDestinationReferenceDescriptor) jmsDestRef;
                String jndiName = jmsDestRefDesc.getJndiName();
                //ignore refs where jndi-name is not available
                if(jndiName != null){
                    detectResourceInRA(app, moduleName, jndiName);
                }
            }
        }
    }

    private void detectResourceInRA(Application app, String moduleName, String jndiName) {
        //domain.xml
        Resource res = null;

        if(jndiName.startsWith(ConnectorConstants.JAVA_APP_SCOPE_PREFIX) /*|| jndiName.startsWith("java:global/")*/  ){
            ApplicationInfo appInfo = appRegistry.get(app.getName());
            res = getApplicationScopedResource(jndiName, BindableResource.class, appInfo);
        }else if(jndiName.startsWith(ConnectorConstants.JAVA_MODULE_SCOPE_PREFIX)){
            ApplicationInfo appInfo = appRegistry.get(app.getName());
            res = getModuleScopedResource(jndiName, moduleName, BindableResource.class, appInfo);
        }else{
            res = getResources().getResourceByName(BindableResource.class, jndiName);
        }
        //embedded ra's resources may not be created yet as they can be created only after .ear deploy
        //  (and .ear may refer to these resources in DD)
        if (res != null) {
            if (ConnectorResource.class.isAssignableFrom(res.getClass())) {
                ConnectorResource connResource = (ConnectorResource)res;
                String poolName = connResource.getPoolName();
                Resource pool ;
                ApplicationInfo appInfo = appRegistry.get(app.getName());
                if(jndiName.startsWith(ConnectorConstants.JAVA_APP_SCOPE_PREFIX) /*|| jndiName.startsWith("java:global/")*/){
                    pool = getApplicationScopedResource(poolName, ResourcePool.class, appInfo);
                } else if(jndiName.startsWith(ConnectorConstants.JAVA_MODULE_SCOPE_PREFIX)){
                    pool = getModuleScopedResource(poolName, moduleName, ResourcePool.class, appInfo);
                } else{
                    pool = getResources().getResourceByName(ResourcePool.class, poolName);
                }
                if (ConnectorConnectionPool.class.isAssignableFrom(pool.getClass())) {
                    String raName = ((ConnectorConnectionPool) pool).getResourceAdapterName();
                    app.addResourceAdapter(raName);
                }
            } else if (AdminObjectResource.class.isAssignableFrom(res.getClass())) {
                String raName = ((AdminObjectResource) res).getResAdapter();
                app.addResourceAdapter(raName);
            }
        } else {
            boolean found = false;
            //detect sun-ra.xml

            // find all the standalone connector modules
            List<com.sun.enterprise.config.serverbeans.Application> applications =
                    getApplications().getApplicationsWithSnifferType(com.sun.enterprise.config.serverbeans.Application.CONNECTOR_SNIFFER_TYPE, true);
            Iterator itr = applications.iterator();
            while (itr.hasNext()) {
                com.sun.enterprise.config.serverbeans.Application application =
                        (com.sun.enterprise.config.serverbeans.Application) itr.next();
                        String appName = application.getName();
                        ApplicationInfo appInfo = appRegistry.get(appName);
                        Application dolApp = appInfo.getMetaData(Application.class);
                        Collection<ConnectorDescriptor> rarDescriptors = dolApp.getRarDescriptors();
                        for (ConnectorDescriptor desc : rarDescriptors) {
                            SunConnector sunraDesc = desc.getSunDescriptor();
                            if (sunraDesc != null) {
                                String sunRAJndiName = (String) sunraDesc.getResourceAdapter().
                                        getValue(ResourceAdapter.JNDI_NAME);
                                if (jndiName.equals(sunRAJndiName)) {
                                    app.addResourceAdapter(desc.getName());
                                    found = true;
                                    break;
                                }
                            } else {
                                //check whether it is default resource in the connector
                                if (desc.getDefaultResourcesNames().contains(jndiName)) {
                                    app.addResourceAdapter(desc.getName());
                                    found = true;
                                    break;
                                }
                            }
                        }
            }

            if (!found) {
                if(DOLUtils.getDefaultLogger().isLoggable(Level.FINEST)) {
                    DOLUtils.getDefaultLogger().log(Level.FINEST, "could not find resource by name : " + jndiName);
                }
            }
        }
    }

    private <T> Resource getApplicationScopedResource(String name, Class<T> type, ApplicationInfo appInfo){
        Resource foundRes = null;
        if(appInfo != null){

            com.sun.enterprise.config.serverbeans.Application app =
                    appInfo.getTransientAppMetaData(com.sun.enterprise.config.serverbeans.Application.APPLICATION, 
                    com.sun.enterprise.config.serverbeans.Application.class);
            Resources resources = null;
            if(app != null){
                resources = appInfo.getTransientAppMetaData(app.getName()+"-resources", Resources.class);
            }
            if(resources != null){

            boolean bindableResource = BindableResource.class.isAssignableFrom(type);
            boolean poolResource = ResourcePool.class.isAssignableFrom(type);
            boolean workSecurityMap = WorkSecurityMap.class.isAssignableFrom(type);
            boolean rac = ResourceAdapterConfig.class.isAssignableFrom(type);

            Iterator itr = resources.getResources().iterator();
                while(itr.hasNext()){
                    String resourceName = null;
                    Resource res = (Resource)itr.next();
                    if(bindableResource && res instanceof BindableResource){
                        resourceName = ((BindableResource)res).getJndiName();
                    } else if(poolResource && res instanceof ResourcePool){
                        resourceName = ((ResourcePool)res).getName();
                    } else if(rac && res instanceof ResourceAdapterConfig){
                        resourceName = ((ResourceAdapterConfig)res).getName();
                    } else if(workSecurityMap && res instanceof WorkSecurityMap){
                        resourceName = ((WorkSecurityMap)res).getName();
                    }
                    if(resourceName != null){
                        if(!(resourceName.startsWith(ConnectorConstants.JAVA_APP_SCOPE_PREFIX) /*||
                                resourceName.startsWith(ConnectorConstants.JAVA_GLOBAL_SCOPE_PREFIX)*/)){
                            resourceName = ConnectorConstants.JAVA_APP_SCOPE_PREFIX + resourceName;
                        }
                        if(!(name.startsWith(ConnectorConstants.JAVA_APP_SCOPE_PREFIX) /*||
                         name.startsWith(ConnectorConstants.JAVA_GLOBAL_SCOPE_PREFIX)*/)){
                            name = ConnectorConstants.JAVA_APP_SCOPE_PREFIX + name;
                        }
                        if(name.equals(resourceName)){
                            foundRes = res;
                            break;
                        }
                    }
                }
            }
        }
        return foundRes;
    }

    private <T> Resource getModuleScopedResource(String name, String moduleName, Class<T> type, ApplicationInfo appInfo){
        Resource foundRes = null;
        if(appInfo != null){

            com.sun.enterprise.config.serverbeans.Application app =
                    appInfo.getTransientAppMetaData(com.sun.enterprise.config.serverbeans.Application.APPLICATION,
                    com.sun.enterprise.config.serverbeans.Application.class);
            Resources resources = null;
            if(app != null){
                Module module = null;
                List<Module> modules = app.getModule();
                for(Module m : modules){
                    if(ConnectorsUtil.getActualModuleName(m.getName()).equals(moduleName)){
                        module = m;
                        break;
                    }
                }
                if(module != null){
                    resources = appInfo.getTransientAppMetaData(module.getName()+"-resources", Resources.class);
                }
            }
            if(resources != null){

            boolean bindableResource = BindableResource.class.isAssignableFrom(type);
            boolean poolResource = ResourcePool.class.isAssignableFrom(type);
            boolean workSecurityMap = WorkSecurityMap.class.isAssignableFrom(type);
            boolean rac = ResourceAdapterConfig.class.isAssignableFrom(type);

            Iterator itr = resources.getResources().iterator();
            while(itr.hasNext()){
                String resourceName = null;
                Resource res = (Resource)itr.next();
                if(bindableResource && res instanceof BindableResource){
                    resourceName = ((BindableResource)res).getJndiName();
                } else if(poolResource && res instanceof ResourcePool){
                    resourceName = ((ResourcePool)res).getName();
                } else if(rac && res instanceof ResourceAdapterConfig){
                    resourceName = ((ResourceAdapterConfig)res).getName();
                } else if(workSecurityMap && res instanceof WorkSecurityMap){
                    resourceName = ((WorkSecurityMap)res).getName();
                }
                if(resourceName != null){
                    if(!(resourceName.startsWith(ConnectorConstants.JAVA_MODULE_SCOPE_PREFIX) /*|| resourceName.startsWith("java:global/")*/)){
                        resourceName = ConnectorConstants.JAVA_MODULE_SCOPE_PREFIX + resourceName;
                    }
                    if(!(name.startsWith(ConnectorConstants.JAVA_MODULE_SCOPE_PREFIX) /*|| name.startsWith("java:global/")*/)){
                        name = ConnectorConstants.JAVA_MODULE_SCOPE_PREFIX + name;
                    }
                    if(name.equals(resourceName)){
                        foundRes = res;
                        break;
                    }
                }
            }
            }
        }
        return foundRes;
    }

    public Collection<ConnectorClassFinder> getSystemRARClassLoaders() {
        try {
            return getConnectorsClassLoaderUtil().getSystemRARClassLoaders();
        } catch (ConnectorRuntimeException cre) {
            throw new RuntimeException(cre.getMessage(), cre);
        }
    }

    public boolean useGlobalConnectorClassLoader() {
        boolean flag = false;
        ConnectorService connectorService = habitat.getComponent(ConnectorService.class);
        //it is possible that connector-service is not yet defined in domain.xml
        if(connectorService != null){
            String classLoadingPolicy = connectorService.getClassLoadingPolicy();
            if (classLoadingPolicy != null &&
                    classLoadingPolicy.equals(ConnectorConstants.CLASSLOADING_POLICY_GLOBAL_ACCESS)) {
                flag = true;
            }
        }
        return flag;
    }

    public Collection<String> getRequiredResourceAdapters(String appName) {
        List<String> requiredRars = new ArrayList<String>();
        if (appName != null) {
            ConnectorService connectorService = habitat.getComponent(ConnectorService.class);
            //it is possible that connector-service is not yet defined in domain.xml

            if (connectorService != null) {
                if (appName != null && appName.trim().length() > 0) {
                    Property property = connectorService.getProperty(appName.trim());
                    if (property != null) {
                        String requiredRarsString = property.getValue();
                        StringTokenizer tokenizer = new StringTokenizer(requiredRarsString, ",");
                        while (tokenizer.hasMoreTokens()) {
                            String token = tokenizer.nextToken().trim();
                            requiredRars.add(token);
                        }
                    }
                }
            }
        }
        return requiredRars;
    }

    private ConnectorsClassLoaderUtil getConnectorsClassLoaderUtil() {
        return habitat.getComponent(ConnectorsClassLoaderUtil.class);
    }

    private Resources getResources() {
        return habitat.getComponent(Domain.class).getResources();
    }

    private Applications getApplications() {
        return habitat.getComponent(Applications.class);
    }
}
