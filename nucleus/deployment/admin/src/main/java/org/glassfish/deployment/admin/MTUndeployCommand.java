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

package org.glassfish.deployment.admin;

import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandLock;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.api.admin.ParameterMap;
import org.glassfish.api.admin.CommandRunner;
import org.glassfish.api.ActionReport;
import org.glassfish.api.Param;
import org.glassfish.deployment.common.DeploymentUtils;
import org.glassfish.deployment.common.DeploymentProperties;
import com.sun.enterprise.config.serverbeans.Application;
import com.sun.enterprise.config.serverbeans.Applications;
import com.sun.enterprise.config.serverbeans.AppTenant;

import javax.inject.Inject;
import org.jvnet.hk2.annotations.Service;
import org.glassfish.hk2.api.PerLookup;

import java.io.File;

@Service(name="_mt-undeploy")
@org.glassfish.api.admin.ExecuteOn(value={RuntimeType.DAS})
@PerLookup
@CommandLock(CommandLock.LockType.NONE)
public class MTUndeployCommand implements AdminCommand {

    @Param(primary = true)
    public String name;

    @Inject
    CommandRunner commandRunner;

    @Inject
    Applications applications;

    public void execute(AdminCommandContext context) {

        final ActionReport report = context.getActionReport();

        // now unprovision the application from tenants if any was
        // provisioned
        unprovisionAppFromTenants(name, report);

        // invoke the undeploy command with domain target to undeploy the
        // application from domain

        CommandRunner.CommandInvocation inv = commandRunner.getCommandInvocation("undeploy", report);

        final ParameterMap parameters = new ParameterMap();

        parameters.set("DEFAULT", name);

        parameters.set(DeploymentProperties.TARGET, DeploymentUtils.DOMAIN_TARGET_NAME);
        inv.parameters(parameters).execute();
    }

    private void unprovisionAppFromTenants(String appName, ActionReport report) {
        Application app = applications.getApplication(appName);

        if (app == null || app.getAppTenants() == null) {
            return;
        }

        for (AppTenant tenant : app.getAppTenants().getAppTenant()) {
            ActionReport subReport = report.addSubActionsReport();
            CommandRunner.CommandInvocation inv = commandRunner.getCommandInvocation("_mt-unprovision", subReport);
            ParameterMap parameters = new ParameterMap();
            parameters.add("DEFAULT", appName);
            parameters.add("tenant", tenant.getTenant());
            inv.parameters(parameters).execute();
        }
    }
}
