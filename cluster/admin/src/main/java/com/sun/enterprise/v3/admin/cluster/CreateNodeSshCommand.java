/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.v3.admin.cluster;

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.File;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import com.sun.enterprise.util.StringUtils;
import com.sun.enterprise.util.SystemPropertyConstants;
import com.sun.enterprise.config.serverbeans.*;
import org.glassfish.api.ActionReport;
import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.admin.*;
import org.glassfish.api.admin.CommandValidationException;
import org.glassfish.api.admin.CommandRunner.CommandInvocation;
import org.jvnet.hk2.annotations.*;
import org.jvnet.hk2.component.*;

import org.glassfish.cluster.ssh.util.SSHUtil;
import org.glassfish.cluster.ssh.launcher.SSHLauncher;

import com.sun.enterprise.universal.process.ProcessManager;
import com.sun.enterprise.universal.process.ProcessManagerException;

/**
 * Remote AdminCommand to create and ssh node.  This command is run only on DAS.
 * Register the node with SSH info on DAS
 *
 * @author Carla Mott
 */
@Service(name = "create-node-ssh")
@I18n("create.node.ssh")
@Scoped(PerLookup.class)
@ExecuteOn({RuntimeType.DAS})
public class CreateNodeSshCommand implements AdminCommand  {
    private static final int DEFAULT_TIMEOUT_MSEC = 300000; // 5 minutes
    
    @Inject
    private CommandRunner cr;

    @Inject
    Habitat habitat;
    
    @Inject
    Nodes nodes;

    @Param(name="name", primary = true)
    private String name;

    @Param(name="nodehost")
    private String nodehost;

    @Param(name = "installdir", optional=true, defaultValue = NodeUtils.NODE_DEFAULT_INSTALLDIR)
    private String installdir;

    @Param(name="nodedir", optional=true)
    private String nodedir;

    @Param(name="sshport", optional=true, defaultValue = NodeUtils.NODE_DEFAULT_SSH_PORT)
    private String sshport;

    @Param(name = "sshuser", optional = true, defaultValue = NodeUtils.NODE_DEFAULT_SSH_USER)
    private String sshuser;

    @Param(name = "sshkeyfile", optional = true)
    private String sshkeyfile;

    @Param(name = "sshpassword", optional = true, password = true)
    private String sshpassword;

    @Param(name = "sshkeypassphrase", optional = true, password=true)
    private String sshkeypassphrase;

    @Param(name = "force", optional = true, defaultValue = "false")
    private boolean force;
    
    @Param(optional = true, defaultValue = "false")
    boolean install;

    @Param(optional = true)
    String archive;
    
    private static final String NL = System.getProperty("line.separator");

    private Logger logger = null;
    
    @Override
    public void execute(AdminCommandContext context) {
        ActionReport report = context.getActionReport();
        StringBuilder msg = new StringBuilder();

        logger = context.getLogger();

        checkDefaults();

        ParameterMap map = new ParameterMap();
        map.add("DEFAULT", name);
        map.add(NodeUtils.PARAM_INSTALLDIR, installdir);
        map.add(NodeUtils.PARAM_NODEHOST, nodehost);
        map.add(NodeUtils.PARAM_NODEDIR, nodedir);
        map.add(NodeUtils.PARAM_SSHPORT, sshport);
        map.add(NodeUtils.PARAM_SSHUSER, sshuser);
        map.add(NodeUtils.PARAM_SSHKEYFILE, sshkeyfile);
        map.add(NodeUtils.PARAM_SSHPASSWORD, sshpassword);
        map.add(NodeUtils.PARAM_SSHKEYPASSPHRASE, sshkeypassphrase);
        map.add(NodeUtils.PARAM_TYPE,"SSH");
        map.add(NodeUtils.PARAM_INSTALL, Boolean.toString(install));

        try {
            NodeUtils nodeUtils = new NodeUtils(habitat, logger);
            nodeUtils.validate(map);
            if (install) {
                boolean s = installNode(context, nodeUtils.sshL);
                if(!s && !force) {
                    report.setActionExitCode(ActionReport.ExitCode.FAILURE);
                    return; 
                }
            }
        } catch (CommandValidationException e) {
            String m1 = Strings.get("node.ssh.invalid.params");
            if (!force) {
                String m2 = Strings.get("create.node.ssh.not.created");
                msg.append(StringUtils.cat(NL, m1, m2, e.getMessage()));
                report.setMessage(msg.toString());
                report.setActionExitCode(ActionReport.ExitCode.FAILURE);
                return;
            } else {
                String m2 = Strings.get("create.node.ssh.continue.force");
                msg.append(StringUtils.cat(NL, m1, e.getMessage(), m2));
            }
        }

        map.remove(NodeUtils.PARAM_INSTALL);
        CommandInvocation ci = cr.getCommandInvocation("_create-node", report);
        ci.parameters(map);
        ci.execute();

        NodeUtils.sanitizeReport(report);

        if (StringUtils.ok(report.getMessage())) {
            if (msg.length() > 0) {
                msg.append(NL);
            }
            msg.append(report.getMessage());
        }
  
        report.setMessage(msg.toString());
    }
    
    /**
     * Prepares for invoking install-node on DAS
     * @param ctx command context
     * @return true if install-node succeeds, false otherwise
     */
    private boolean installNode(AdminCommandContext ctx, SSHLauncher launcher) throws CommandValidationException {
        boolean res = false;        
        ArrayList<String> command = new ArrayList<String>();

        command.add("install-node");

        command.add("--installdir");
        command.add(installdir);

        if (force) {
            command.add("--force");
        }
        
        if(archive != null) {
            File ar = new File(archive);
            if(ar.exists() && ar.canRead()) {
                command.add("--archive");
                command.add(archive);
            }
        }
        
        command.add("--sshuser");
        command.add(sshuser);
        
        command.add("--sshport");
        command.add(sshport);

        if (sshkeyfile == null) {
            sshkeyfile=SSHUtil.getExistingKeyFile();
        }
        
        if(sshkeyfile!= null) {
            command.add("--sshkeyfile");
            command.add(sshkeyfile);
        }

        command.add(nodehost);
        
        String firstErrorMessage = Strings.get("create.node.ssh.install.failed", nodehost);
        StringBuilder out = new StringBuilder();
        int exitCode = execCommand(command, launcher, out);
                
        ActionReport report = ctx.getActionReport();
        if (exitCode == 0) {
            // If it was successful say so and display the command output
            String msg = Strings.get("create.node.ssh.install.success", nodehost);
            report.setMessage(msg);
            res=true;
        } else {
            report.setMessage(firstErrorMessage);
        }
        return res;
    }
    
    /**
     * Sometimes the console passes an empty string for a parameter. This
     * makes sure those are defaulted correctly.
     */
    void checkDefaults() {
        if (!StringUtils.ok(installdir)) {
            installdir = NodeUtils.NODE_DEFAULT_INSTALLDIR;
        }
        if (!StringUtils.ok(sshport)) {
            sshport = NodeUtils.NODE_DEFAULT_SSH_PORT;
        }
        if (!StringUtils.ok(sshuser)) {
            sshuser = NodeUtils.NODE_DEFAULT_SSH_USER;
        }
    }

    /**
     * Invokes install-node using ProcessManager and returns the exit message/status.
     * @param cmdLine list of args
     * @param output contains output message
     * @return exit status of install-node
     */
    private int execCommand(List<String> cmdLine, SSHLauncher launcher, StringBuilder output) {
        int exit = -1;
        
        List<String> fullcommand = new ArrayList<String>();
        String installDir = nodes.getDefaultLocalNode().getInstallDirUnixStyle() + "/glassfish";
        if (!StringUtils.ok(installDir)) {
            throw new IllegalArgumentException(Strings.get("create.node.ssh.no.installdir"));
        }

        File asadmin = new File(SystemPropertyConstants.getAsAdminScriptLocation(installDir));
        fullcommand.add(asadmin.getAbsolutePath());
        
        BufferedWriter out = null;
        File f = null;
        
        //if password auth is used for creating node, use the same auth mechanism for
        //install-node as well. The passwords are passed using a temporary password file
        if(sshpassword != null) {        
            try {
                f = new File(System.getProperty("java.io.tmpdir"), "pass.tmp");
                out = new BufferedWriter(new FileWriter(f));
                out.newLine();
                out.write("AS_ADMIN_SSHPASSWORD=" + launcher.expandPasswordAlias(sshpassword) + "\n");
                if(sshkeypassphrase != null)
                    out.write("AS_ADMIN_SSHKEYPASSPHRASE=" + launcher.expandPasswordAlias(sshkeypassphrase) + "\n");
                out.flush();
            } catch (IOException ioe) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Failed to create the temporary password file: " + ioe.getMessage());
                }
                output.append(Strings.get("create.node.ssh.passfile.error"));
                return 1;
            }
            finally {
                try {
                    if (out != null)                        
                        out.close();
                } catch(final Exception ex){
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("Failed to close stream: " + ex.getMessage());
                    }
                }
            }
            
            fullcommand.add("--passwordfile");
            fullcommand.add(f.getAbsolutePath());
        }
        
        fullcommand.add("--interactive=false");     
        fullcommand.addAll(cmdLine);
        
        ProcessManager pm = new ProcessManager(fullcommand);

        if(logger.isLoggable(Level.INFO)) {
            logger.info("Running command on DAS: " + commandListToString(fullcommand));
        }
        pm.setTimeoutMsec(DEFAULT_TIMEOUT_MSEC);

        if (logger.isLoggable(Level.FINER))
            pm.setEcho(true);
        else
            pm.setEcho(false);

        try {
            exit = pm.execute();            
        }
        catch (ProcessManagerException ex) {
            if(logger.isLoggable(Level.FINE)) {
                logger.fine("Error while executing command: " + ex.getMessage());
            }
            exit = 1;
        }

        String stdout = pm.getStdout();
        String stderr = pm.getStderr();
        
        if (output != null) {
            if (StringUtils.ok(stdout)) {
                output.append(stdout);
            }

            if (StringUtils.ok(stderr)) {
                if (output.length() > 0) {
                    output.append(NL);
                }
                output.append(stderr);
            }
        }
        
        if (f != null) {
            boolean didDelete = f.delete();
            if(!didDelete) {
                if(logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, Strings.get("node.ssh.passfile.delete.error", f.getPath()));
                }
            }
        }
        return exit;
    }
    
    private String commandListToString(List<String> command) {
        StringBuilder fullCommand = new StringBuilder();

        for (String s : command) {
            fullCommand.append(" ");
            fullCommand.append(s);
        }

        return fullCommand.toString();
    }
}    