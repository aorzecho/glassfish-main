/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.elasticity.engine.commands;

import com.sun.enterprise.config.serverbeans.Domain;
import org.glassfish.api.ActionReport;
import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.elasticity.config.serverbeans.ElasticService;
import org.glassfish.elasticity.config.serverbeans.ElasticServices;
import org.glassfish.elasticity.config.serverbeans.Actions;
import org.glassfish.elasticity.config.serverbeans.LogAction;
import javax.inject.Inject;
import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.PerLookup;
import org.jvnet.hk2.config.*;

import java.beans.PropertyVetoException;
import java.util.logging.Logger;

/**
 * Created by IntelliJ IDEA.
 * User: cmott
 * Date: 11/21/11
 */
@Service(name="delete-log-action")
@I18n("delete.log.action")
@Scoped(PerLookup.class)
public class DeleteLogActionCommand implements AdminCommand{

    @Inject
    Domain domain;

   @Inject
   ElasticServices elasticServices;

   @Param(name="name", primary = true)
    String name;

   @Param(name="service")
   String servicename;

    @Override
   public void execute(AdminCommandContext context) {
       ActionReport report = context.getActionReport();
       Logger logger= context.logger;

       if (elasticServices == null)   {
           //service doesn't exist
           String msg = Strings.get("elasticity.not.found", servicename);
           logger.warning(msg);
           report.setActionExitCode(ActionReport.ExitCode.FAILURE);
           report.setMessage(msg);
           return;
       }

       ElasticService elasticService= elasticServices.getElasticService(servicename);
       if (elasticService == null) {
           //service doesn't exist
           String msg = Strings.get("noSuchService", servicename);
           logger.warning(msg);
           report.setActionExitCode(ActionReport.ExitCode.FAILURE);
           report.setMessage(msg);
           return;
       }

       if (elasticService.getActions().getLogAction(name) != null) {
            try {
                deleteActionElement(name);
           } catch(TransactionFailure e) {
               logger.warning("failed.to.delete.action " + name);
               report.setActionExitCode(ActionReport.ExitCode.FAILURE);
               report.setMessage(e.getMessage());
            }
        } else {
           logger.warning("failed.to.delete.action " + name);
           report.setActionExitCode(ActionReport.ExitCode.FAILURE);
           report.setMessage("action.not.found "+ name);

       }
   }
        public void deleteActionElement(final String name) throws TransactionFailure {
       ConfigSupport.apply(new SingleConfigCode() {
           @Override
           public Object run(ConfigBeanProxy param) throws PropertyVetoException, TransactionFailure {
               // get the transaction
               Transaction t = Transaction.getTransaction(param);
               if (t != null) {
                   ElasticService elasticService = elasticServices.getElasticService(servicename);
                   if (elasticService != null) {
                       Actions writeableAction = elasticService.getActions();
                       if (writeableAction != null) {
                           writeableAction = t.enroll(writeableAction);
                           LogAction action = writeableAction.getLogAction(name);
                           writeableAction.getLogAction().remove(action);
                       }
                       //nothing to delete

                   }
               }
               return Boolean.TRUE;
           }

       }, domain);
   }

}
