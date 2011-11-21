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

package org.glassfish.paas.spe.common;

import org.glassfish.internal.api.Globals;
import org.glassfish.paas.orchestrator.provisioning.ServiceInfo;
import org.glassfish.paas.orchestrator.provisioning.cli.ServiceUtil;
import org.glassfish.paas.orchestrator.service.HTTPLoadBalancerServiceType;
import org.glassfish.paas.orchestrator.service.JavaEEServiceType;
import org.glassfish.paas.orchestrator.service.RDBMSServiceType;
import org.glassfish.paas.orchestrator.service.ServiceStatus;
import org.glassfish.paas.orchestrator.service.ServiceType;
import org.glassfish.paas.orchestrator.service.metadata.ServiceDescription;
import org.glassfish.paas.orchestrator.service.spi.ProvisionedService;

import java.util.Properties;

/**
 * Represents a node (or instance) belonging to a service.
 *
 * @author bhavanishankar@java.net
 */

public class BasicProvisionedService implements ProvisionedService {

    private ServiceUtil serviceUtil;
    private ServiceDescription serviceDescription;
    private Properties serviceProperties;
    private ServiceStatus status;

    /*
    private ServiceInfo serviceInfo;

    public AbstractProvisionedService(ServiceDescription serviceDescription,
                                      ServiceInfo serviceInfo) {
    }
    */
    
    public BasicProvisionedService(ServiceDescription serviceDescription,
                                   Properties serviceProperties, ServiceStatus status) {
        this.serviceDescription = serviceDescription;
        this.serviceUtil = Globals.getDefaultHabitat().getComponent(ServiceUtil.class);
        this.status = status;
        
        // create the service info configuration in domain.xml
        ServiceInfo entry = new ServiceInfo();
        entry.setServerType(serviceDescription.getServiceType());
        entry.setServiceName(serviceDescription.getName());
        entry.setAppName(serviceDescription.getAppName());
        entry.setState(status.toString());
        for (String prop : serviceProperties.stringPropertyNames()) {
            entry.setProperty(prop, serviceProperties.getProperty(prop));
        }
        if (!serviceUtil.isServiceAlreadyConfigured(
                serviceDescription.getName(), serviceDescription.getAppName(), null)) {
            serviceUtil.registerCloudEntry(entry);
        } else { // update only the state.
            serviceUtil.updateState(serviceDescription.getName(),
                    serviceDescription.getAppName(), status.toString(), null);
        }
        this.serviceProperties = new InterceptedProperties(entry, serviceProperties);
    }

    public ServiceType getServiceType() {
        String serviceType = serviceDescription.getServiceType();
        // TODO :: FIXME >> make serviceType extensible
        if ("JavaEE".equalsIgnoreCase(serviceType)) {
            return new JavaEEServiceType();
        }
        if ("Database".equalsIgnoreCase(serviceType)) {
            return new RDBMSServiceType();
        }
        if ("LB".equalsIgnoreCase(serviceType)) {
            return new HTTPLoadBalancerServiceType();
        }
        return null;
    }

    public ServiceDescription getServiceDescription() {
        return serviceDescription;
    }

    public Properties getServiceProperties() {
        return serviceProperties;
    }

    public ServiceStatus getStatus() {
        return status;
    }

    public String getName() {
        return serviceDescription.getName();
    }

    public Properties getProperties() {
        return serviceProperties;
    }

    class InterceptedProperties extends Properties {

        ServiceInfo serviceInfo;

        InterceptedProperties(ServiceInfo serviceInfo,
                              Properties properties) {
            super(properties);
            this.serviceInfo = serviceInfo;
        }

        @Override
        public Object put(Object key, Object value) {
            Object object = super.put(key, value);
            // update the domain.xml configuration
            if (key instanceof String && value instanceof String) {
                serviceUtil.setProperty(serviceInfo.getServiceName(),
                        serviceInfo.getAppName(), (String) key, (String) value);
            }
            return object;
        }

        @Override
        public Object remove(Object key) {
            Object object = super.remove(key);
            // update the domain.xml configuration
            if (key instanceof String && object instanceof String) {
                serviceUtil.removeProperty(serviceInfo.getServiceName(),
                        serviceInfo.getAppName(), (String) key);
            }
            return object;
        }
    }

}
