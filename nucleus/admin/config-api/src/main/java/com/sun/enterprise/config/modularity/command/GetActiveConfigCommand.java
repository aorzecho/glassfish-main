/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.config.modularity.command;

import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.DomainExtension;
import com.sun.enterprise.config.serverbeans.SystemPropertyBag;
import com.sun.enterprise.config.modularity.customization.ConfigBeanDefaultValue;
import com.sun.enterprise.config.modularity.customization.ConfigCustomizationToken;
import com.sun.enterprise.config.modularity.ZeroConfigUtils;
import com.sun.enterprise.util.LocalStringManagerImpl;
import com.sun.enterprise.util.SystemPropertyConstants;
import org.glassfish.api.ActionReport;
import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.api.admin.config.ConfigExtension;
import org.glassfish.config.support.CommandTarget;
import org.glassfish.config.support.TargetType;
import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.component.PerLookup;
import org.jvnet.hk2.config.ConfigBeanProxy;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Get the current active configuration of a service and print it out for the user's review.
 *
 * @author Masoud Kalali
 */
@TargetType(value = {CommandTarget.DAS, CommandTarget.DOMAIN, CommandTarget.CLUSTER, CommandTarget.STANDALONE_INSTANCE})
@ExecuteOn(RuntimeType.ALL)
@Service(name = "get-active-config")
@Scoped(PerLookup.class)
@I18n("get.active.config")
public final class GetActiveConfigCommand extends AbstractZeroConfigCommand implements AdminCommand {

    private final Logger LOG = Logger.getLogger(GetActiveConfigCommand.class.getName());
    final private static LocalStringManagerImpl localStrings =
            new LocalStringManagerImpl(GetActiveConfigCommand.class);
    private static final String DEFAULT_FORMAT = "";

    private ActionReport report;

    @Inject
    private Domain domain;

    @Inject
    private
    Habitat habitat;

    @Param(optional = true, defaultValue = SystemPropertyConstants.DAS_SERVER_CONFIG, name = "target")
    private String target;

    @Param(name = "serviceName", primary = true, optional = false)
    private String serviceName;

    @Override
    public void execute(AdminCommandContext context) {
        ActionReport report = context.getActionReport();
        if (serviceName == null) {
            report.setMessage(localStrings.getLocalString("get.active.config.service.name.required",
                    "You need to specify a service name to get it's active configuration."));
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            return;
        }
        if (target != null) {
            if (domain.getConfigNamed(target) == null) {
                report.setMessage(localStrings.getLocalString("get.active.config.target.name.invalid",
                        "The target name you specified is invalid. Please double check the target name and try again"));
                report.setActionExitCode(ActionReport.ExitCode.FAILURE);
                return;
            }
        }

        if (serviceName != null) {
            String className = ZeroConfigUtils.convertConfigElementNameToClassName(serviceName);
            Class configBeanType = ZeroConfigUtils.getClassFor(serviceName, habitat);
            if (configBeanType == null) {
                String msg = localStrings.getLocalString("get.active.config.not.such.a.service.found",
                        DEFAULT_FORMAT, className, serviceName);
                report.setActionExitCode(ActionReport.ExitCode.FAILURE);
                report.setMessage(msg);
                return;
            }
            try {
                String serviceDefaultConfig = getActiveConfigFor(configBeanType, habitat);
                if (serviceDefaultConfig != null) {
                    report.setMessage(serviceDefaultConfig);
                    report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
                }
            } catch (Exception e) {
                String msg = localStrings.getLocalString("get.active.config.getting.active.config.for.service.failed",
                        DEFAULT_FORMAT, serviceName, target, e.getMessage());
                LOG.log(Level.INFO, msg, e);
                report.setActionExitCode(ActionReport.ExitCode.FAILURE);
                report.setMessage(msg);
                report.setFailureCause(e);
            }
        }
    }

    private String getActiveConfigFor(Class configBeanType, Habitat habitat) throws InvocationTargetException, IllegalAccessException {

        if (ZeroConfigUtils.hasCustomConfig(configBeanType)) {
            List<ConfigBeanDefaultValue> defaults = ZeroConfigUtils.getDefaultConfigurations(configBeanType);
            return getCompleteConfiguration(defaults);
        }

        if (ConfigExtension.class.isAssignableFrom(configBeanType)) {
            Config targetConfig = domain.getConfigNamed(target);
            if (targetConfig.checkIfExtensionExists(configBeanType)) {
                return ZeroConfigUtils.serializeConfigBean(targetConfig.getExtensionByType(configBeanType));
            } else {
                return ZeroConfigUtils.serializeConfigBeanByType(configBeanType, habitat);
            }

        } else if (configBeanType.isAssignableFrom(DomainExtension.class)) {
            if (domain.checkIfExtensionExists(configBeanType)) {
                return ZeroConfigUtils.serializeConfigBean(domain.getExtensionByType(configBeanType));
            }
            return ZeroConfigUtils.serializeConfigBeanByType(configBeanType, habitat);
        }
        return null;
    }

    private String getCompleteConfiguration(List<ConfigBeanDefaultValue> defaults) throws InvocationTargetException, IllegalAccessException {
        StringBuilder builder = new StringBuilder();
        for (ConfigBeanDefaultValue value : defaults) {
            builder.append("At location: ");
            builder.append(replaceExpressionsWithValues(value.getLocation()));
            builder.append(System.getProperty("line.separator"));
            String substituted = replacePropertiesWithCurrentValue(
                    getDependentConfigElement(value), value);
            builder.append(substituted);
            builder.append(System.getProperty("line.separator"));
        }
        builder.deleteCharAt(builder.length() - 1);
        return builder.toString();

    }

    private String getDependentConfigElement(ConfigBeanDefaultValue defaultValue) throws InvocationTargetException, IllegalAccessException {
        ConfigBeanProxy configBean = ZeroConfigUtils.getCurrentConfigBeanForDefaultValue(defaultValue, habitat);
        if (configBean != null) {
            return ZeroConfigUtils.serializeConfigBean(configBean);
        } else {
            return defaultValue.getXmlConfiguration();
        }

    }

    private String replacePropertiesWithCurrentValue(String xmlConfiguration, ConfigBeanDefaultValue value) throws InvocationTargetException, IllegalAccessException {
        for (ConfigCustomizationToken token : value.getCustomizationTokens()) {
            String toReplace = "\\$\\{" + token.getKey() + "\\}";
            ConfigBeanProxy current = ZeroConfigUtils.getCurrentConfigBeanForDefaultValue(value, habitat);
            if (current != null) {
                String propertyValue = getPropertyValue(token, current);
                if (propertyValue != null) {
                    xmlConfiguration = xmlConfiguration.replaceAll(toReplace, propertyValue);
                }
            }
        }
        return xmlConfiguration;
    }


    private static String getPropertyValue(ConfigCustomizationToken token, ConfigBeanProxy finalConfigBean) {
        ConfigBeanProxy parent = finalConfigBean.getParent();
        while (!(parent instanceof SystemPropertyBag)) {
            parent = parent.getParent();
            if(parent==null) return null;
        }
            if (((SystemPropertyBag) parent).getSystemProperty(token.getKey()) != null) {
                return ((SystemPropertyBag) parent).getSystemProperty(token.getKey()).getValue();
            }

        else return token.getDefaultValue();
    }
}