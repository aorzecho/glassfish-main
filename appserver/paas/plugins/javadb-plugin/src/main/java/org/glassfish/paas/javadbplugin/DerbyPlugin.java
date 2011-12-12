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
package org.glassfish.paas.javadbplugin;

import org.glassfish.api.deployment.ApplicationContainer;
import org.glassfish.api.deployment.archive.ReadableArchive;
import org.glassfish.embeddable.CommandResult;
import org.glassfish.embeddable.CommandRunner;
import org.glassfish.paas.javadbplugin.cli.DatabaseServiceUtil;
import org.glassfish.paas.orchestrator.PaaSDeploymentContext;
import org.glassfish.paas.orchestrator.ServiceOrchestrator;
import org.glassfish.paas.orchestrator.provisioning.ServiceInfo;
import org.glassfish.paas.orchestrator.provisioning.DatabaseProvisioner;
import org.glassfish.paas.orchestrator.provisioning.cli.ServiceType;
import org.glassfish.paas.orchestrator.service.RDBMSServiceType;
import org.glassfish.paas.orchestrator.service.ServiceStatus;
import org.glassfish.paas.orchestrator.service.metadata.Property;
import org.glassfish.paas.orchestrator.service.metadata.ServiceCharacteristics;
import org.glassfish.paas.orchestrator.service.metadata.ServiceDescription;
import org.glassfish.paas.orchestrator.service.metadata.ServiceReference;
import org.glassfish.paas.orchestrator.service.spi.Plugin;
import org.glassfish.paas.orchestrator.service.spi.ProvisionedService;
import org.glassfish.virtualization.spi.AllocationStrategy;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.PerLookup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Jagadish Ramu
 * @author Shalini M
 */
@Scoped(PerLookup.class)
@Service
public class DerbyPlugin implements Plugin<RDBMSServiceType> {

    @Inject
    private CommandRunner commandRunner;

    @Inject
    private DatabaseServiceUtil dbServiceUtil;

    public static final String INIT_SQL_PROPERTY="database.init.sql";

    public static final String DATABASE_NAME = "database.name";

    private static final String DATASOURCE = "javax.sql.DataSource";

    public static final String RDBMS_ServiceType = "Database";

    private static Logger logger = Logger.getLogger(DerbyPlugin.class.getName());

    public RDBMSServiceType getServiceType() {
        return new RDBMSServiceType();
    }

    public boolean handles(ReadableArchive cloudArchive) {
        //For prototype, DB Plugin has no role here.
        return true;
    }

    public boolean handles(ServiceDescription serviceDescription) {
        return false;
    }

    public boolean isReferenceTypeSupported(String referenceType) {
        return DATASOURCE.equalsIgnoreCase(referenceType);
    }

    public Set<ServiceReference> getServiceReferences(String appName, ReadableArchive cloudArchive) {
        //DB plugin does not scan anything for prototype
        return new HashSet<ServiceReference>();
    }

    public ServiceDescription getDefaultServiceDescription(String appName, ServiceReference svcRef) {

        if (DATASOURCE.equals(svcRef.getServiceRefType())) {

            DatabaseProvisioner dbProvisioner = new DerbyProvisioner();

            // create default service description.
            String defaultServiceName = dbProvisioner.getDefaultServiceName();
            List<Property> properties = new ArrayList<Property>();
            properties.add(new Property("service-type", RDBMS_ServiceType));

            String initSqlFile = "";
            String databaseName = "";
            List<Property> configurations = new ArrayList<Property>();
            configurations.add(new Property(INIT_SQL_PROPERTY, initSqlFile));
            configurations.add(new Property(DATABASE_NAME, databaseName));

            ServiceDescription sd = new ServiceDescription(defaultServiceName, appName,
                    "lazy", new ServiceCharacteristics(properties), configurations);

/*
            // Fill the required details in service reference.
            Properties defaultConnPoolProperties = dbProvisioner.getDefaultConnectionProperties();
            defaultConnPoolProperties.setProperty("serviceName", defaultServiceName);
            svcRef.setProperties(defaultConnPoolProperties);
*/

            return sd;
        } else {
            return null;
        }
    }

    private String formatArgument(List<Property> properties) {
        return formatArgument(properties, ":");
    }

    private String formatArgument(List<Property> properties, String delimiter) {
        StringBuilder sb = new StringBuilder();
        if (properties != null) {
            for (Property p : properties) {
                sb.append(p.getName() + "=" + "'" + p.getValue() +"'" + delimiter);
            }
        }
        // remove the last occurrence of delimiter
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    public ProvisionedService provisionService(ServiceDescription serviceDescription, PaaSDeploymentContext dc) {

        String serviceName = serviceDescription.getName();
        logger.entering(getClass().getName(), "provisionService");

        CommandResult result = commandRunner.run("_list-derby-services");
        if (!result.getOutput().contains(serviceName)) {
            //create-derby-service
            String serviceConfigurations = formatArgument(serviceDescription.getConfigurations(), ";");

            List<String> params = new ArrayList<String>();
            params.add("--serviceconfigurations");
            params.add(serviceConfigurations);
            params.add("--virtualcluster");
            params.add(serviceDescription.getVirtualClusterName());
            params.add("--waitforcompletion=true");

            // either template identifier or service characteristics are specified, not both.
            if (serviceDescription.getTemplateIdentifier() != null) {
                params.add("--templateid=" + serviceDescription.getTemplateIdentifier().getId());
            } else if (serviceDescription.getServiceCharacteristics() != null) {
                String serviceCharacteristics = formatArgument(
                        serviceDescription.getServiceCharacteristics().getServiceCharacteristics());
                params.add("--servicecharacteristics=" + serviceCharacteristics);
            }
            if (serviceDescription.getAppName() != null) {
                params.add("--appname=" + serviceDescription.getAppName());
            }
            params.add(serviceName); //make sure that servicename (operand) is the last param.

            result = commandRunner.run("_create-derby-service", params.toArray(new String[params.size()]));
            if (result.getExitStatus().equals(CommandResult.ExitStatus.FAILURE)) {
                System.out.println("_create-derby-service [" + serviceName + "] failed");
            }
        }

        ServiceInfo entry = dbServiceUtil.retrieveCloudEntry(serviceName,
                serviceDescription.getAppName(), ServiceType.DATABASE);
        if (entry == null) {
            throw new RuntimeException("unable to get DB service : " + serviceName);
        }

        String databaseName = serviceDescription.getConfiguration("database.name");

        DerbyProvisionedService dps = new DerbyProvisionedService(serviceDescription,
                getServiceProperties(entry, databaseName));
        dps.setStatus(ServiceStatus.STARTED);
        return dps;
    }

    public ProvisionedService getProvisionedService(ServiceDescription serviceDescription, ServiceInfo serviceInfo){
        String databaseName = serviceInfo.getProperty(DatabaseProvisioner.DATABASENAME);

        DerbyProvisionedService dps = new DerbyProvisionedService(serviceDescription,
                        getServiceProperties(serviceInfo, databaseName));
        dps.setStatus(dbServiceUtil.getServiceStatus(serviceInfo));
        return dps;
    }

    public void associateServices(ProvisionedService serviceConsumer, ServiceReference svcRef,
                                  ProvisionedService serviceProvider, boolean beforeDeployment, PaaSDeploymentContext dc) {
        //no-op
    }

    public ApplicationContainer deploy(ReadableArchive cloudArchive) {
        return null;
    }

    public ProvisionedService startService(ServiceDescription serviceDescription, ServiceInfo serviceInfo) {
        String serviceName = serviceDescription.getName();
        logger.entering(getClass().getName(), "startService");

        ArrayList<String> params;
        String[] parameters;

        CommandResult result = commandRunner.run("_list-derby-services");
        if (result.getOutput().contains(serviceName)) {
            params = new ArrayList<String>();

            if(serviceDescription.getAppName() != null){
                params.add("--appname="+serviceDescription.getAppName());
            }
            params.add("--startvm=true");
            params.add("--virtualcluster");
            params.add(serviceDescription.getVirtualClusterName());
            params.add(serviceName);
            parameters = new String[params.size()];
            parameters = params.toArray(parameters);

            result = commandRunner.run("_start-derby-service", parameters);
            if (result.getExitStatus().equals(CommandResult.ExitStatus.FAILURE)) {
                logger.log(Level.WARNING, "_start-derby-service [" + serviceName + "] failed");
            }
        }

        ServiceInfo entry = dbServiceUtil.retrieveCloudEntry(serviceName, serviceDescription.getAppName(), ServiceType.DATABASE);
        if (entry == null) {
            throw new RuntimeException("unable to get DB service : " + serviceName);
        }

        String databaseName = entry.getProperty(DatabaseProvisioner.DATABASENAME);

        DerbyProvisionedService dps = new DerbyProvisionedService(serviceDescription,
                        getServiceProperties(entry, databaseName));
        dps.setStatus(ServiceStatus.STARTED);
        return dps;
    }

    public boolean stopService(ServiceDescription serviceDescription, ServiceInfo serviceInfo) {
        String serviceName = serviceDescription.getName();
        ArrayList params = new ArrayList<String>();
        logger.entering(getClass().getName(), "stopService");

        if (serviceDescription.getAppName() != null) {
            params.add("--appname");
            params.add(serviceDescription.getAppName());
        }
        params.add("--stopvm=true");
        params.add("--virtualcluster");
        params.add(serviceDescription.getVirtualClusterName());
        params.add(serviceName);
        String[] parameters = new String[params.size()];
        parameters = (String[]) params.toArray(parameters);

        CommandResult result = commandRunner.run("_stop-derby-service", parameters);
        logger.log(Level.INFO, "_stop-derby-service command output [" + result.getOutput() + "]");
        if (result.getExitStatus() == CommandResult.ExitStatus.SUCCESS) {
            return true;
        } else {
            //TODO throw exception ?
            if(result.getFailureCause() != null){
                result.getFailureCause().printStackTrace();
            }
            return false;
        }
    }

    public boolean isRunning(ProvisionedService provisionedSvc) {
        return provisionedSvc.getStatus().equals(ServiceStatus.STARTED);
    }

    public ProvisionedService match(ServiceReference svcRef) {
        throw new UnsupportedOperationException("Not implemented yet");
    }


    public Set<ServiceDescription> getImplicitServiceDescriptions(
            ReadableArchive cloudArchive, String appName) {
        //no-op. Just by looking at a orchestration archive
        //the db plugin cannot say that a DB needs to be provisioned. 
        return new HashSet<ServiceDescription>();
    }

    public boolean unprovisionService(ServiceDescription serviceDescription, PaaSDeploymentContext dc){

        List<String> params = new ArrayList<String>();
        params.add("--virtualcluster");
        params.add(serviceDescription.getVirtualClusterName());
        params.add("--waitforcompletion=true");

        if(serviceDescription.getAppName() != null){
            params.add("--appname="+serviceDescription.getAppName());
        }
        params.add(serviceDescription.getName());

        CommandResult result = commandRunner.run("_delete-derby-service", params.toArray(new String[params.size()]));
        System.out.println("_delete-derby-service command output [" + result.getOutput() + "]");
        if (result.getExitStatus() == CommandResult.ExitStatus.SUCCESS) {
            return true;
        } else {
            //TODO throw exception ?
            result.getFailureCause().printStackTrace();
            return false;
        }
    }

    public void dissociateServices(ProvisionedService serviceConsumer, ServiceReference svcRef,
                                   ProvisionedService serviceProvider, boolean beforeUndeploy, PaaSDeploymentContext dc){
        //no-op
    }

    @Override
    public ProvisionedService scaleService(ServiceDescription serviceDesc,
            int scaleCount, AllocationStrategy allocStrategy) {
        // TODO :: scale database service...
        return null;
    }

    @Override
    public boolean reconfigureServices(ProvisionedService oldPS,
            ProvisionedService newPS) {
        // TODO :: reconfigure database service after scaling.
        return true;
    }

    @Override
    public boolean reassociateServices(ProvisionedService svcConsumer, 
            ProvisionedService oldSvcProvider, ProvisionedService newSvcProvider, 
            ServiceOrchestrator.ReconfigAction reason) {
        // TODO :: reassociate services after scaling.
        return true;
    }

    private Properties getServiceProperties(ServiceInfo entry, String databaseName) {
        DatabaseProvisioner dbProvisioner = new DerbyProvisioner();
        Properties defaultConnPoolProperties = dbProvisioner.getDefaultConnectionProperties();
        Properties serviceProperties = new Properties();
        String ipAddress = entry.getIpAddress();
        serviceProperties.putAll(defaultConnPoolProperties);
        serviceProperties.put("host", ipAddress);
        serviceProperties.put("port", "1527"); // TODO :: grab the actual port.
	if(databaseName != null && databaseName.trim().length() > 0) {
            serviceProperties.put(DatabaseProvisioner.DATABASENAME, databaseName);
	}
        return serviceProperties;
    }
}
