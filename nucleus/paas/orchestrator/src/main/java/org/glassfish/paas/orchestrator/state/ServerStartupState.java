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

import org.glassfish.paas.orchestrator.PaaSDeploymentContext;
import org.glassfish.paas.orchestrator.PaaSDeploymentException;
import org.glassfish.paas.orchestrator.PaaSDeploymentState;
import org.glassfish.paas.orchestrator.ServiceOrchestratorImpl;
import org.glassfish.paas.orchestrator.provisioning.ServiceInfo;
import org.glassfish.paas.orchestrator.provisioning.cli.ServiceUtil;
import org.glassfish.paas.orchestrator.service.metadata.ServiceDescription;
import org.glassfish.paas.orchestrator.service.metadata.ServiceMetadata;
import org.glassfish.paas.orchestrator.service.spi.Plugin;
import org.glassfish.paas.orchestrator.service.spi.ProvisionedService;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.annotations.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Jagadish Ramu
 */
@Service
public class ServerStartupState extends AbstractPaaSDeploymentState {

    @Inject
    private ServiceUtil serviceUtil;

    private static Logger logger = Logger.getLogger(ServiceOrchestratorImpl.class.getName());

    public void handle(PaaSDeploymentContext context) throws PaaSDeploymentException {
        retrieveProvisionedServices(context);
    }

    public Set<ProvisionedService> retrieveProvisionedServices(PaaSDeploymentContext context) {
        logger.entering(getClass().getName(), "retrieveProvisionedServices");
        final ServiceOrchestratorImpl orchestrator = context.getOrchestrator();
        final Set<Plugin> installedPlugins = orchestrator.getPlugins();
        String appName = context.getAppName();
        final ServiceMetadata appServiceMetadata = orchestrator.getServiceMetadata(appName);

        final Set<ProvisionedService> appPSs = new HashSet<ProvisionedService>();
        String virtualClusterName = orchestrator.getVirtualClusterName(appServiceMetadata);
        logger.log(Level.INFO, "Retrieve PS for app=" + appName + " virtualCluster=" + virtualClusterName);
        Set<ServiceDescription> appSDs = appServiceMetadata.getServiceDescriptions();
        for (final ServiceDescription sd : appSDs) {
                //Temporary workaround to set virtual-cluster in all ProvisionedServices
                //sd.setVirtualClusterName(virtualClusterName);

                Plugin<?> chosenPlugin = orchestrator.getPluginForServiceType(installedPlugins, sd.getServiceType());
                logger.log(Level.INFO, "Retrieving provisioned Service for " + sd + " through " + chosenPlugin);
                ServiceInfo serviceInfo = serviceUtil.retrieveCloudEntry(sd.getName(), appName, null );
                if(serviceInfo != null){
                    ProvisionedService ps = chosenPlugin.getProvisionedService(sd, serviceInfo);
                    appPSs.add(ps);
                }else{
                    logger.warning("unable to retrieve service-info for service : " + sd.getName() + " of application : " + appName);
                }
        }
        orchestrator.addProvisionedServices(appName, appPSs);
        return appPSs;
    }

    public Class<PaaSDeploymentState> getRollbackState() {
        return null;
    }
}
