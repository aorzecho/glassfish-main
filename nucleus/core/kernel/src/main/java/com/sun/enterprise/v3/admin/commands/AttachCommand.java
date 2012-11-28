/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.v3.admin.commands;

import com.sun.enterprise.admin.remote.AdminCommandStateImpl;
import com.sun.enterprise.util.LocalStringManagerImpl;
import javax.inject.Inject;
import org.glassfish.api.ActionReport;
import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommandEventBroker.AdminCommandListener;
import org.glassfish.api.admin.*;
import org.glassfish.api.admin.progress.JobInfo;
import org.glassfish.hk2.api.PerLookup;

import org.glassfish.security.services.common.SubjectUtil;
import org.jvnet.hk2.annotations.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.glassfish.api.admin.AdminCommandState.State.PREPARED;
import static org.glassfish.api.admin.AdminCommandState.State.RUNNING;
import static org.glassfish.api.admin.AdminCommandState.State.COMPLETED;


/**
 * Gets CommandInstance from registry based on given id and forwards all events.
 *
 * @author Martin Mares
 * @author Bhakti Mehta
 */
@Service(name = AttachCommand.COMMAND_NAME)
@PerLookup
@CommandLock(CommandLock.LockType.NONE)
@I18n(AttachCommand.COMMAND_NAME)
@ManagedJob
public class AttachCommand implements AdminCommand, AdminCommandListener,AdminCommandSecurity.AccessCheckProvider {

    
    public static final String COMMAND_NAME = "attach";
    private final static LocalStringManagerImpl strings = new LocalStringManagerImpl(AttachCommand.class);

    private AdminCommandEventBroker eventBroker;
    private Job attached;

    @Inject
    JobManager registry;

    @Param(primary=true, optional=false, multiple=false)
    String jobID;

    protected final List<AccessRequired.AccessCheck> accessChecks = new ArrayList<AccessRequired.AccessCheck>();

    @Override
    public void execute(AdminCommandContext context) {
        eventBroker = context.getEventBroker();
        ActionReport ar = context.getActionReport();
        attached = registry.get(jobID);
        JobInfo jobInfo = null;
        String jobName = null;
        String attachedUser = SubjectUtil.getUsernamesFromSubject(context.getSubject()).get(0);
        if (attached == null) {
            //try for completed jobs
            if (registry.getCompletedJobs() != null)
                jobInfo = (JobInfo) registry.getCompletedJobForId(jobID);
             if (jobInfo != null) {
                 jobName = jobInfo.jobName;
             }

        }

        if ((attached == null && jobInfo == null) || (attached != null && attached.getName().startsWith("_"))
                || (attached != null && AttachCommand.COMMAND_NAME.equals(attached.getName()))) {
            ar.setActionExitCode(ActionReport.ExitCode.FAILURE);
            ar.setMessage(strings.getLocalString("attach.wrong.commandinstance.id", "Job with id {0} does not exist.", jobID));
            return;
        }
        if (attached != null) {
            AdminCommandEventBroker attachedBroker = attached.getEventBroker();
            synchronized (attachedBroker) {
                onAdminCommandEvent(AdminCommandStateImpl.EVENT_STATE_CHANGED, attached);
                if (attached.getCommandProgress() != null) {
                    onAdminCommandEvent(CommandProgress.EVENT_PROGRESSSTATUS_STATE, attached.getCommandProgress());
                }
                attachedBroker.registerListener(".*", this);
            }
            synchronized (attached) {
                while(attached.getState().equals(PREPARED) ||
                        attached.getState().equals( RUNNING)) {
                    try {
                        attached.wait(1000*60*5); //5000L just to be sure
                    } catch (InterruptedException ex) {}
                }
                if (attached.getState().equals(COMPLETED)) {
                    String commandUser = SubjectUtil.getUsernamesFromSubject(attached.getSubject()).get(0);
                    //In most cases if the user who attaches to the command is the same
                    //as one who started it then purge the job once it is completed
                    if (commandUser != null && commandUser.equals(attachedUser))  {
                        purgeJob(attached.getId());

                    }
                    ar.setActionExitCode(attached.getActionReport().getActionExitCode());
                    ar.appendMessage(strings.getLocalString("attach.finished", "Command {0} executed {1}",attached.getName(),attached.getActionReport().getActionExitCode()));
                }
            }
        } else {

            if (jobInfo != null && jobInfo.exitCode.equals(COMPLETED.toString())) {

                //In most cases if the user who attaches to the command is the same
                //as one who started it then purge the job once it is completed
                if (attachedUser!= null && attachedUser.equals( jobInfo.user)) {
                    purgeJob(jobInfo.jobId);

                }
                ar.setActionExitCode(ActionReport.ExitCode.SUCCESS);
                ar.appendMessage(strings.getLocalString("attach.finished", "Command {0} executed{1}",jobName,ActionReport.ExitCode.SUCCESS));
            }
        }

    }

    @Override
    public void onAdminCommandEvent(String name, Object event) {
        if (name == null || name.startsWith("client.")) { //Skip nonsence or own events
            return;
        }
        if (AdminCommandStateImpl.EVENT_STATE_CHANGED.equals(name) && 
                ((Job) event).getState().equals(COMPLETED)) {
            synchronized (attached) {
                attached.notifyAll();
            }
        } else {
            eventBroker.fireEvent(name, event); //Forward
        }
    }


    @Override
    public Collection<? extends AccessRequired.AccessCheck> getAccessChecks() {
        accessChecks.add(new AccessRequired.AccessCheck("jobs/system/$user/$attached","READ"));
        return accessChecks;
    }

    private void purgeJob(String jobid) {
        registry.purgeJob(jobid);
        registry.purgeCompletedJobForId(jobid);

    }

}
