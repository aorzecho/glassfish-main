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
package org.glassfish.paas.mysqldbplugin;

import org.glassfish.api.deployment.ApplicationContainer;
import org.glassfish.api.deployment.archive.ReadableArchive;
import org.glassfish.embeddable.CommandResult;
import org.glassfish.embeddable.CommandRunner;
import org.glassfish.paas.orchestrator.PaaSDeploymentContext;
import org.glassfish.paas.orchestrator.ServiceOrchestrator;
import org.glassfish.paas.orchestrator.provisioning.DatabaseProvisioner;
import org.glassfish.paas.orchestrator.provisioning.ServiceInfo;
import org.glassfish.paas.orchestrator.provisioning.cli.ServiceType;
import org.glassfish.paas.orchestrator.provisioning.cli.ServiceUtil;
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
 * Plugin for MySQL Database Service
 *
 * @author Shalini M
 */
@Scoped(PerLookup.class)
@Service
public class MySQLDBPlugin implements Plugin<RDBMSServiceType> {

    @Inject
    private CommandRunner commandRunner;

    @Inject
    private ServiceUtil serviceUtil;

    public static final String INIT_SQL_PROPERTY="database.init.sql";

    public static final String DATABASE_NAME = "database.name";

    private static final String DATASOURCE = "javax.sql.DataSource";

    public static final String RDBMS_ServiceType = "Database";

    private static Logger logger = Logger.getLogger(MySQLDBPlugin.class.getName());

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

        if (DATASOURCE.equals(svcRef.getType())) {

            DatabaseProvisioner dbProvisioner = new MySQLDbProvisioner();

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
        String serviceConfigurations = formatArgument(serviceDescription.getConfigurations(), ";");
        String appNameParam = "";
        if (serviceDescription.getAppName() != null) {
            appNameParam = "--appname=" + serviceDescription.getAppName();
        }
        logger.entering(getClass().getName(), "provisionService");

        ArrayList<String> params;
        String[] parameters;

        CommandResult result = null;
        // either template identifier or service characteristics are specified, not both.
        if (serviceDescription.getTemplateIdentifier() != null) {
            String templateId = serviceDescription.getTemplateIdentifier().getId();
            result = commandRunner.run("_create-mysql-db-service",
                    "--templateid=" + templateId,
                    "--serviceconfigurations", serviceConfigurations,
                    "--virtualcluster", serviceDescription.getVirtualClusterName(),
                    "--waitforcompletion=true", appNameParam, serviceName);
        } else if (serviceDescription.getServiceCharacteristics() != null) {
            String serviceCharacteristics = formatArgument(serviceDescription.
                    getServiceCharacteristics().getServiceCharacteristics());
            result = commandRunner.run("_create-mysql-db-service",
                    "--servicecharacteristics=" + serviceCharacteristics,
                    "--serviceconfigurations", serviceConfigurations,
                    "--virtualcluster", serviceDescription.getVirtualClusterName(),
                    "--waitforcompletion=true", appNameParam, serviceName);
        }
        if (result.getExitStatus().equals(CommandResult.ExitStatus.FAILURE)) {
            System.out.println("_create-mysql-db-service [" + serviceName + "] failed");
        }

        ServiceInfo entry = serviceUtil.getServiceInfo(serviceName,
                serviceDescription.getAppName(), ServiceType.DATABASE);
        if (entry == null) {
            throw new RuntimeException("unable to get DB service : " + serviceName);
        }

        String databaseName = entry.getProperty(DatabaseProvisioner.DATABASENAME);//serviceDescription.getConfiguration("database.name");


        return new MySQLDbProvisionedService(serviceDescription,
                getServiceProperties(entry, databaseName), ServiceStatus.STARTED);
    }

    public void associateServices(ProvisionedService serviceConsumer, ServiceReference svcRef,
                                  ProvisionedService serviceProvider, boolean beforeDeployment, PaaSDeploymentContext dc) {
        //no-op
    }

    public ApplicationContainer deploy(ReadableArchive cloudArchive) {
        return null;
    }

    public ProvisionedService getProvisionedService(ServiceDescription serviceDescription, ServiceInfo serviceInfo){
        String databaseName = serviceInfo.getProperty(DatabaseProvisioner.DATABASENAME);

        return new MySQLDbProvisionedService(serviceDescription,
                getServiceProperties(serviceInfo, databaseName),
                serviceUtil.getServiceStatus(serviceInfo));
    }

    public ProvisionedService startService(ServiceDescription serviceDescription, ServiceInfo serviceInfo) {
        String serviceName = serviceDescription.getName();
        logger.entering(getClass().getName(), "startService");
        ArrayList params = new ArrayList<String>();

        if (serviceDescription.getAppName() != null) {
            params.add("--appname");
            params.add(serviceDescription.getAppName());
        }
        params.add("--startvm=true");
        params.add("--virtualcluster");
        params.add(serviceDescription.getVirtualClusterName());
        params.add(serviceName);
        String[] parameters = new String[params.size()];
        parameters = (String[]) params.toArray(parameters);

        CommandResult result = commandRunner.run("_start-mysql-db-service", parameters);
        if (result.getExitStatus().equals(CommandResult.ExitStatus.FAILURE)) {
            logger.log(Level.WARNING, "Start MySQL DB Service [" + serviceName + "] failed");
        }

        return new MySQLDbProvisionedService(serviceDescription, new Properties(), ServiceStatus.STARTED);
    }

    public boolean stopService(ServiceDescription serviceDescription, ServiceInfo serviceInfo) {
        logger.entering(getClass().getName(), "stopService");
        String serviceName = serviceDescription.getName();
        ArrayList params = new ArrayList<String>();

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

        CommandResult result = commandRunner.run("_stop-mysql-db-service", parameters);
        if (result.getExitStatus().equals(CommandResult.ExitStatus.FAILURE)) {
            logger.log(Level.WARNING, "Stop MySQL DB Service [" + serviceName + "] failed");
        }

        return true;
    }

    public boolean isRunning(ProvisionedService provisionedSvc) {
        return provisionedSvc.getStatus().equals(ServiceStatus.STARTED);
    }

    public ProvisionedService match(ServiceReference svcRef) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public boolean reconfigureServices(ProvisionedService oldPS, ProvisionedService newPS) {
	//TODO reconfigure database service after scaling
	return true;
    }

    public Set<ServiceDescription> getImplicitServiceDescriptions(
            ReadableArchive cloudArchive, String appName) {
        //no-op. Just by looking at a orchestration archive
        //the db plugin cannot say that a DB needs to be provisioned.
        return new HashSet<ServiceDescription>();
    }

    public boolean unprovisionService(ServiceDescription serviceDescription, PaaSDeploymentContext dc){
        String appNameParam="";
        if(serviceDescription.getAppName() != null){
            appNameParam="--appname="+serviceDescription.getAppName();
        }
        CommandResult result = commandRunner.run("_delete-mysql-db-service",
                "--waitforcompletion=true",
                "--virtualcluster", serviceDescription.getVirtualClusterName(),
                appNameParam, serviceDescription.getName());
        System.out.println("_delete-mysql-db-service command output [" + result.getOutput() + "]");
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
        //no-op
	//TODO : scale database service
	return null;
    }

    @Override
    public boolean reassociateServices(ProvisionedService svcConsumer, 
            ProvisionedService oldSvcProvider, ProvisionedService newSvcProvider, 
            ServiceOrchestrator.ReconfigAction reason) {
        // TODO :: reassociate services after scaling.
        return true;
    }

    private Properties getServiceProperties(ServiceInfo entry, String databaseName) {
        DatabaseProvisioner dbProvisioner = new MySQLDbProvisioner();
        Properties defaultConnPoolProperties = dbProvisioner.getDefaultConnectionProperties();
        Properties serviceProperties = new Properties();
        String ipAddress = entry.getIpAddress();
        serviceProperties.putAll(defaultConnPoolProperties);
        serviceProperties.put("host", ipAddress);
        serviceProperties.put("URL", "jdbc\\:mysql\\://" + ipAddress + "\\:3306/" + databaseName); // TODO :: grab the actual port.
        if(databaseName != null && databaseName.trim().length() > 0) {
            serviceProperties.put(DatabaseProvisioner.DATABASENAME, databaseName);
        }
        return serviceProperties;
    }
}
