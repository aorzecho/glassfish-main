/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.orb.admin.cli;

import org.glassfish.orb.admin.config.IiopListener;
import org.glassfish.orb.admin.config.IiopService;
import com.sun.enterprise.v3.common.PropsFileActionReporter;
import com.sun.logging.LogDomains;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandRunner;
import org.glassfish.api.admin.ParameterMap;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.DomDocument;
import org.jvnet.hk2.config.SingleConfigCode;
import org.jvnet.hk2.config.TransactionFailure;

import java.beans.PropertyVetoException;
import java.util.List;


public class DeleteIiopListenerTest extends org.glassfish.tests.utils.ConfigApiTest {

    private Habitat services;
    private IiopService iiopService;
    private ParameterMap parameters;
    private CommandRunner cr;
    private AdminCommandContext context;

    public String getFileName() {
        return "DomainTest";
    }

    public DomDocument getDocument(Habitat services) {
        return new TestDocument(services);
    }

    @Before
    public void setUp() {
        services = getHabitat();
        iiopService = services.byType(IiopService.class).get();
        parameters = new ParameterMap();
        cr = services.byType(CommandRunner.class).get();
        context = new AdminCommandContext(
                LogDomains.getLogger(DeleteIiopListenerTest.class, LogDomains.ADMIN_LOGGER),
                new PropsFileActionReporter());
    }

    @After
    public void tearDown() throws TransactionFailure {
        ConfigSupport.apply(new SingleConfigCode<IiopService>() {
            public Object run(IiopService param) throws PropertyVetoException,
                    TransactionFailure {
                List<IiopListener> listenerList = param.getIiopListener();
                for (IiopListener listener : listenerList) {
                    String currListenerId = listener.getId();
                    if (currListenerId != null && currListenerId.equals
                            ("iiop_1")) {
                        listenerList.remove(listener);
                        break;
                    }
                }
                return listenerList;
            }
        }, iiopService);
    }


    /**
     * Test of execute method, of class DeleteIiopListener.
     * delete-iiop-listener iiop_1
     */
    @Test
    public void testExecuteSuccessDefaultTarget() {
        parameters.set("listeneraddress", "localhost");
        parameters.set("iiopport", "4440");
        parameters.set("listener_id", "iiop_1");
        CreateIiopListener createCommand = services.byType(CreateIiopListener.class).get();
        cr.getCommandInvocation("create-iiop-listener", context.getActionReport()).parameters(parameters).execute(createCommand);               
        assertEquals(ActionReport.ExitCode.SUCCESS, context.getActionReport().getActionExitCode());
        parameters = new ParameterMap();
        parameters.set("listener_id", "iiop_1");
        DeleteIiopListener deleteCommand = services.byType(DeleteIiopListener.class).get();
        cr.getCommandInvocation("delete-iiop-listener", context.getActionReport()).parameters(parameters).execute(deleteCommand);               

        assertEquals(ActionReport.ExitCode.SUCCESS, context.getActionReport().getActionExitCode());
        boolean isDeleted = true;
        List<IiopListener> listenerList = iiopService.getIiopListener();
        for (IiopListener listener : listenerList) {
            if (listener.getId().equals("iiop_1")) {
                isDeleted = false;
                logger.fine("IIOPListener name iiop_1 is not deleted.");
                break;
            }
        }
        assertTrue(isDeleted);
        logger.fine("msg: " + context.getActionReport().getMessage());
    }

    /**
     * Test of execute method, of class DeleteIiopListener.
     * delete-iiop-listener doesnotexist
     */
    @Test
    public void testExecuteFailDoesNotExist() {
        parameters.set("DEFAULT", "doesnotexist");
        DeleteIiopListener deleteCommand = services.byType(DeleteIiopListener.class).get();
        cr.getCommandInvocation("delete-iiop-listener", context.getActionReport()).parameters(parameters).execute(deleteCommand);               
        assertEquals(ActionReport.ExitCode.FAILURE, context.getActionReport().getActionExitCode());
        logger.fine("msg: " + context.getActionReport().getMessage());
    }
}
