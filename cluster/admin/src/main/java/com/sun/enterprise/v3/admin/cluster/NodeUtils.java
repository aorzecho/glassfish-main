/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

import org.jvnet.hk2.component.Habitat;
import org.glassfish.internal.api.RelativePathResolver;
import com.sun.enterprise.universal.process.ProcessManagerException;
import com.sun.enterprise.config.serverbeans.Node;
import com.sun.enterprise.config.serverbeans.SshConnector;
import com.sun.enterprise.config.serverbeans.SshAuth;
import com.sun.enterprise.util.StringUtils;
import com.sun.enterprise.util.ExceptionUtil;
import com.sun.enterprise.util.net.NetUtils;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.*;
import org.glassfish.api.admin.CommandValidationException;
import com.sun.enterprise.universal.glassfish.TokenResolver;
import org.glassfish.cluster.ssh.launcher.SSHLauncher;
import org.glassfish.cluster.ssh.connect.NodeRunner;
import java.util.logging.Logger;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Utility methods for operating on Nodes
 *
 * @author Joe Di Pol
 */
public class NodeUtils {

    static final String NODE_DEFAULT_SSH_PORT = "22";
    static final String NODE_DEFAULT_SSH_USER = "${user.name}";
    static final String NODE_DEFAULT_INSTALLDIR =
                                                "${com.sun.aas.productRoot}";

    // Command line option parameter names
    static final String PARAM_NODEHOST = "nodehost";
    static final String PARAM_INSTALLDIR = "installdir";
    static final String PARAM_NODEDIR = "nodedir";
    static final String PARAM_SSHPORT = "sshport";
    static final String PARAM_SSHUSER = "sshuser";
    static final String PARAM_SSHKEYFILE = "sshkeyfile";
    static final String PARAM_SSHPASSWORD = "sshpassword";
    static final String PARAM_SSHKEYPASSPHRASE = "sshkeypassphrase";
    static final String PARAM_TYPE = "type";
    static final String PARAM_INSTALL = "install";
    static final String LANDMARK_FILE = "glassfish/modules/admin-cli.jar";    

    private static final String NL = System.getProperty("line.separator");

    private TokenResolver resolver = null;
    private Logger logger = null;
    private Habitat habitat = null;

    SSHLauncher sshL = null;

    NodeUtils(Habitat habitat, Logger logger) {
        this.logger = logger;
        this.habitat = habitat;

        // Create a resolver that can replace system properties in strings
        Map<String, String> systemPropsMap =
                new HashMap<String, String>((Map)(System.getProperties()));
        resolver = new TokenResolver(systemPropsMap);
        sshL = habitat.getComponent(SSHLauncher.class);
    }

    static boolean isSSHNode(Node node) {
        if (node == null)
            return false;
       return node.getType().equals("SSH");
    }

    /**
     * Get the version string from a glassfish installation on the node.
     * @param node
     * @return version string
     */
    String getGlassFishVersionOnNode(Node node) throws CommandValidationException {
        if (node == null)
            return "";

        List<String> command = new ArrayList<String>();
        command.add("version");
        command.add("--local");
        command.add("--terse");
        NodeRunner nr = new NodeRunner(habitat, logger);

        StringBuilder output = new StringBuilder();
        try {
            int commandStatus = nr.runAdminCommandOnNode(node, output, command);
            if (commandStatus != 0) {
                return "unknown version: " + output.toString();
            }
        } catch (Exception e) {
            throw new CommandValidationException(
                    Strings.get("failed.to.run", command.toString(),
                                    node.getNodeHost()), e);
        }
        return output.toString().trim();
    }

    void validate(Node node) throws
            CommandValidationException {

        // Put node values into parameter map and validate
        ParameterMap map = new ParameterMap();
        map.add("DEFAULT", node.getName());
        map.add(NodeUtils.PARAM_INSTALLDIR, node.getInstallDir());
        map.add(NodeUtils.PARAM_NODEHOST, node.getNodeHost());
        map.add(NodeUtils.PARAM_NODEDIR, node.getNodeDirAbsolute());

        SshConnector sshc = node.getSshConnector();
        if (sshc != null) {
            map.add(NodeUtils.PARAM_SSHPORT, sshc.getSshPort());
            SshAuth ssha = sshc.getSshAuth();
            map.add(NodeUtils.PARAM_SSHUSER, ssha.getUserName());
            map.add(NodeUtils.PARAM_SSHKEYFILE, ssha.getKeyfile());
            map.add(NodeUtils.PARAM_SSHPASSWORD, ssha.getPassword());
            map.add(NodeUtils.PARAM_SSHKEYPASSPHRASE, ssha.getKeyPassphrase());
            map.add(NodeUtils.PARAM_TYPE,"SSH");
        }

        validate(map);
        return;
    }

    /**
     * Validate all the parameters used to create an ssh node
     * @param map   Map with all parameters used to create an ssh node.
     *              The map values can contain system property tokens.
     * @throws CommandValidationException
     */
    void validate(ParameterMap map) throws
            CommandValidationException {

        String sshkeyfile = map.getOne(PARAM_SSHKEYFILE);
        if (StringUtils.ok(sshkeyfile)) {
            // User specified a key file. Make sure we get use it
            File kfile = new File(resolver.resolve(sshkeyfile));
            if (! kfile.isAbsolute()) {
                throw new CommandValidationException(
                        Strings.get("key.path.not.absolute",
                        kfile.getPath()));
            }
            if (! kfile.exists()) {
                throw new CommandValidationException(
                        Strings.get("key.path.not.found",
                        kfile.getPath()));
            }
            if (! kfile.canRead() ) {
                throw new CommandValidationException(
                        Strings.get("key.path.not.readable",
                        kfile.getPath(), System.getProperty("user.name")) );
            }
        }

        validatePassword(map.getOne(PARAM_SSHPASSWORD));
        validatePassword(map.getOne(PARAM_SSHKEYPASSPHRASE));

        String  nodehost = map.getOne(PARAM_NODEHOST);
        validateHostName(nodehost);

        if (sshL != null && ! nodehost.equals("localhost")) {
            validateSSHConnection(map);
        }
    }

    void validateHostName(String hostName)
            throws CommandValidationException {

        if (! StringUtils.ok(hostName)) {
            throw new CommandValidationException(
                    Strings.get("nodehost.required"));
        }
        try {
            // Check if hostName is valid by looking up it's address
            InetAddress addr = InetAddress.getByName(hostName);
        } catch (UnknownHostException e) {
            throw new CommandValidationException(
                    Strings.get("unknown.host", hostName),
                    e);
        }
    }

    private void validatePassword(String p) throws CommandValidationException {

        String expandedPassword = null;

        // Make sure if a password alias is used we can expand it
        if (StringUtils.ok(p)) {
            try {
                expandedPassword = RelativePathResolver.getRealPasswordFromAlias(p);
            } catch (IllegalArgumentException e) {
                throw new CommandValidationException(
                        Strings.get("no.such.password.alias", p));
            } catch (Exception e) {
                throw new CommandValidationException(
                        Strings.get("no.such.password.alias", p),
                        e);
            }

            if (expandedPassword == null) {
                throw new CommandValidationException(
                        Strings.get("no.such.password.alias", p));
            }
        }
    }

    /**
     * Make sure we can make an SSH connection using an existing node.
     *
     * @param node  Node to connect to
     * @throws CommandValidationException
     */
    void pingSSHConnection(Node node) throws
            CommandValidationException {

        validateHostName(node.getNodeHost());

        try {
            sshL.init(node, logger);
            sshL.pingConnection();
        } catch (Exception e) {
            String m1 = e.getMessage();
            String m2 = "";
            Throwable e2 = e.getCause();
            if (e2 != null) {
                m2 = e2.getMessage();
            }
            String msg = Strings.get("ssh.bad.connect", node.getNodeHost());
            logger.warning(StringUtils.cat(": ", msg, m1, m2,
                                            sshL.toString()));
            throw new CommandValidationException(StringUtils.cat(NL,
                                           msg, m1, m2));
        }
    }


    private void validateSSHConnection(ParameterMap map) throws
            CommandValidationException {

        String nodehost = map.getOne(PARAM_NODEHOST);
        String installdir = map.getOne(PARAM_INSTALLDIR);
        String nodedir = map.getOne(PARAM_NODEDIR);
        String sshport = map.getOne(PARAM_SSHPORT);
        String sshuser = map.getOne(PARAM_SSHUSER);
        String sshkeyfile = map.getOne(PARAM_SSHKEYFILE);
        String sshpassword = map.getOne(PARAM_SSHPASSWORD);
        String sshkeypassphrase = map.getOne(PARAM_SSHKEYPASSPHRASE);
        boolean installFlag = Boolean.parseBoolean(map.getOne(PARAM_INSTALL));

        // We use the resolver to expand any system properties
        if (! NetUtils.isPortStringValid(resolver.resolve(sshport))) {
            throw new CommandValidationException(Strings.get(
                    "ssh.invalid.port", sshport));
        }

        int port = Integer.parseInt(resolver.resolve(sshport));

        try {
            // sshpassword and sshkeypassphrase may be password alias.
            // Those aliases are handled by sshLauncher
            String resolvedInstallDir = resolver.resolve(installdir);

            sshL.validate(resolver.resolve(nodehost),
                          port,
                          resolver.resolve(sshuser),
                          sshpassword,      
                          resolver.resolve(sshkeyfile),
                          sshkeypassphrase, 
                          resolvedInstallDir,
                          // Landmark file to ensure valid GF install
                          LANDMARK_FILE,
                          logger);
        } catch (IOException e) {
            String m1 = e.getMessage();
            String m2 = "";
            Throwable e2 = e.getCause();
            if (e2 != null) {
                m2 = e2.getMessage();
            }
            if (e instanceof FileNotFoundException) {
                logger.warning(StringUtils.cat(": ", m1, m2, sshL.toString()));
                if (!installFlag)
                    throw new CommandValidationException(StringUtils.cat(NL,
                                            m1, m2));
            } else {
                String msg = Strings.get("ssh.bad.connect", nodehost);
                logger.warning(StringUtils.cat(": ", msg, m1, m2,
                                            sshL.toString()));
                throw new CommandValidationException(StringUtils.cat(NL,
                                            msg, m1, m2));
            }
        }
    }

    /**
     * Takes an action report and updates the message in the report with
     * the message from the root cause of the report.
     *
     * @param report
     */
    static void sanitizeReport(ActionReport report) {
        if (report != null && report.hasFailures() &&
                              report.getFailureCause() != null) {
            Throwable rootCause = ExceptionUtil.getRootCause(
                    report.getFailureCause());
            if (rootCause != null && StringUtils.ok(rootCause.getMessage())) {
                report.setMessage(rootCause.getMessage());
            }
        }
    }

  /**
     * Run on admin command on a node and handle setting the message in the
     * ActionReport on an error. Note that on success no message is set in
     * the action report
     *
     * @param node  The node to run the command on. Can be local or remote
     * @param command  asadmin command to run. The list must contain all
     *                  parameters to asadmin, but not "asadmin" itself.
     * @param context   The command context. The ActionReport in this
     *                  context will be updated on an error to contain an
     *                  appropriate error message.
     * @param firstErrorMessage The first message to use if an error is
     *                          encountered. Usually something like
     *                          "Could not start instance".
     * @param humanCommand  The command the user should run on the node if
     *                      we failed to run the passed command.
     * @param output        Output from the run command.
     * @param waitForReaderThreads True: wait for the command IO to complete.
     *                      False: don't wait for IO to complete, just for
     *                      process to end.
     *                      Currently this only applies to locally run commands
     *                      and should only be set to false by start-instance
     *                      (see bug 12777). 
     */
   void runAdminCommandOnNode(Node node, List<String> command,
           AdminCommandContext context, String firstErrorMessage,
           String humanCommand, StringBuilder output,
           boolean waitForReaderThreads) {

        ActionReport report = context.getActionReport();
        boolean failure = true;
        String msg1 = firstErrorMessage;
        String msg2 = "";
        String msg3 = "";
        String nodeHost = node.getNodeHost();
        String nodeName = node.getName();
        String installDir = node.getInstallDir();

        if (output == null) {
            output = new StringBuilder();
        }

        if (StringUtils.ok(humanCommand)) {
            msg3 = Strings.get("node.ssh.tocomplete",
                        nodeHost, installDir, humanCommand);
        }

        NodeRunner nr = new NodeRunner(habitat, logger);
        try {
            int status = nr.runAdminCommandOnNode(node, output, waitForReaderThreads,
                                                  command);
            if (status != 0) {
                // Command ran, but didn't succeed. Log full information
                msg2 = Strings.get("node.command.failed", nodeName,
                        nodeHost, output.toString().trim(), nr.getLastCommandRun());
                logger.warning(StringUtils.cat(": ", msg1, msg2, msg3));
                // Don't expose command name to user in case it is a hidden command
                msg2 = Strings.get("node.command.failed.short", nodeName,
                        nodeHost, output.toString().trim());
            } else {
                failure = false;
                logger.info(output.toString().trim());
            }
        } catch (SSHCommandExecutionException ec) {
            msg2 = Strings.get("node.ssh.bad.connect",
                        nodeName, nodeHost, ec.getMessage());
            // Log some extra info
            String msg = Strings.get("node.command.failed.ssh.details",
                    nodeName, nodeHost, ec.getCommandRun(), ec.getMessage(),
                    ec.getSSHSettings());
            logger.warning(StringUtils.cat(": ", msg1, msg, msg3));
        } catch (ProcessManagerException ex) {
            msg2 = Strings.get("node.command.failed.local.details",
                    ex.getMessage(), nr.getLastCommandRun());
            logger.warning(StringUtils.cat(": ", msg1, msg2, msg3));
            // User message doesn't have command that was run
            msg2 = Strings.get("node.command.failed.local.exception",
                    ex.getMessage());
        } catch (UnsupportedOperationException e) {
            msg2 = Strings.get("node.not.ssh", nodeName, nodeHost);
            logger.warning(StringUtils.cat(": ", msg1, msg2, msg3));
        } catch (IllegalArgumentException e) {
            msg2 = e.getMessage();
            logger.warning(StringUtils.cat(": ", msg1, msg2, msg3));
        }

        if (failure) {
            report.setMessage(StringUtils.cat(NL + NL, msg1, msg2, msg3));
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
        } else {
            report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
       }
       

       return;
   }

   void runAdminCommandOnNode(Node node, List<String> command,
           AdminCommandContext context, String firstErrorMessage,
           String humanCommand, StringBuilder output) {

       runAdminCommandOnNode(node, command, context, firstErrorMessage,
               humanCommand, output, true);
   }
}
