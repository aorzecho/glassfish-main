/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.admin.cli.cluster;

import java.io.*;
import java.net.URL;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

import org.glassfish.internal.api.Globals;
import org.glassfish.internal.api.RelativePathResolver;
import org.glassfish.api.Param;
import org.glassfish.api.admin.*;
import com.sun.enterprise.admin.cli.CLICommand;
import org.glassfish.cluster.ssh.util.SSHUtil;
import org.glassfish.cluster.ssh.launcher.SSHLauncher;
import org.glassfish.cluster.ssh.sftp.SFTPClient;

import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Nodes;
import com.sun.enterprise.config.serverbeans.Node;

import com.sun.enterprise.universal.glassfish.TokenResolver;
import com.sun.enterprise.util.io.DomainDirs;
import com.sun.enterprise.util.SystemPropertyConstants;
import com.sun.enterprise.util.StringUtils;

import com.trilead.ssh2.SFTPv3DirectoryEntry;
import com.trilead.ssh2.Connection;

import org.jvnet.hk2.config.ConfigParser;
import org.jvnet.hk2.config.Dom;
import org.jvnet.hk2.config.DomDocument;
import org.jvnet.hk2.component.Habitat;

import org.glassfish.security.common.MasterPassword;

import com.sun.enterprise.security.store.PasswordAdapter;

/**
 *  Base class for SSH provisioning commands.
 *
 */
public abstract class SSHCommandsBase extends CLICommand {

    @Param(optional = true, defaultValue="${user.name}")
    protected String sshuser;

    @Param(optional=true, defaultValue="22")
    protected int sshport;

    @Param(optional = true)
    protected String sshkeyfile;

    @Param(optional = false, primary = true, multiple = true)
    protected String[] hosts;

    protected String sshpassword;
    protected String sshkeypassphrase=null;

    protected boolean promptPass=false;

    protected TokenResolver resolver = null;

    public SSHCommandsBase() {
        // Create a resolver that can replace system properties in strings
        Map<String, String> systemPropsMap =
                new HashMap<String, String>((Map)(System.getProperties()));
        resolver = new TokenResolver(systemPropsMap);
    }

    /**
     * Get SSH password from password file or user.
     */
    protected String getSSHPassword(String node) throws CommandException {
        String password = getFromPasswordFile("AS_ADMIN_SSHPASSWORD");
      
        if (password !=null ) {
            String alias = RelativePathResolver.getAlias(password);
            if (alias != null)
                password = expandPasswordAlias(node, alias, true);
        }
        
        //get password from user if not found in password file
        if (password == null) {
            if (programOpts.isInteractive()) {
                password=readPassword(Strings.get("SSHPasswordPrompt", sshuser, node));
            } else {
                throw new CommandException(Strings.get("SSHPasswordNotFound"));
            }
        }
        return password;
    }

    /**
     * Get SSH key passphrase from password file or user.
     */
    protected String getSSHPassphrase(boolean verifyConn) throws CommandException {
        String passphrase = getFromPasswordFile("AS_ADMIN_SSHKEYPASSPHRASE");

        if (passphrase != null) {
            String alias = RelativePathResolver.getAlias(passphrase);

            if (alias != null)
                passphrase = expandPasswordAlias(null, alias, verifyConn);
        }
        
        //get password from user if not found in password file
        if (passphrase == null) {
            if (programOpts.isInteractive()) {
                //i18n
                passphrase=readPassword(Strings.get("SSHPassphrasePrompt", sshkeyfile));
            } else {
                passphrase=""; //empty passphrase
            }
        }
        return passphrase;
    }

    /**
     * Get domain master password from password file or user.
     */
    String getMasterPassword(String domain) throws CommandException {
        String masterPass = getFromPasswordFile("AS_ADMIN_MASTERPASSWORD");

        //get password from user if not found in password file
        if (masterPass == null) {
            if (programOpts.isInteractive()) {
                //i18n
                masterPass=readPassword(Strings.get("DomainMasterPasswordPrompt", domain));
            } else {
                masterPass="changeit"; //default
            }
        }
        return masterPass;
    }
    
    private String getFromPasswordFile(String name) {
        return passwords.get(name);
    }

    protected boolean isValidAnswer(String val) {
        return val.equalsIgnoreCase("yes") || val.equalsIgnoreCase("no")
                || val.equalsIgnoreCase("y") || val.equalsIgnoreCase("n") ;
    }

    protected boolean isEncryptedKey() throws CommandException {
        boolean res = false;
        try {
            res = SSHUtil.isEncryptedKey(sshkeyfile);
        } catch (IOException ioe) {
            throw new CommandException(Strings.get("ErrorParsingKey", sshkeyfile, ioe.getMessage()));
        }
        return res;
    }
    
    /**
     * Method to delete files and directories on remote host
     * 'nodes' directory is not considered for deletion since it would contain 
     * configuration information.
     * @param sftpClient sftp client instance
     * @param dasFiles file layout on DAS
     * @param dir directory to be removed
     * @param force true means delete all files, false means leave non-GlassFish files
     *              untouched
     * @throws IOException in case of error
     */
    protected void deleteRemoteFiles(SFTPClient sftpClient, List<String> dasFiles, String dir, boolean force)
    throws IOException {
 
        for (SFTPv3DirectoryEntry directoryEntry: (List<SFTPv3DirectoryEntry>)sftpClient.ls(dir)) {
            if (directoryEntry.filename.equals(".") || directoryEntry.filename.equals("..")
                    || directoryEntry.filename.equals("nodes")) {
                continue;
            } else if (directoryEntry.attributes.isDirectory()) {
                String f1 = dir+"/"+directoryEntry.filename;
                deleteRemoteFiles(sftpClient, dasFiles, f1, force);
                //only if file is present in DAS, it is targeted for removal on remote host
                //using force deletes all files on remote host
                if(force) {
                    logger.fine("Force removing directory " + f1);
                    sftpClient.rmdir(f1); 
                } else {                    
                    if (dasFiles.contains(f1))
                        sftpClient.rmdir(f1);
                }
            } else {
                String f2 = dir+"/"+directoryEntry.filename;
                if(force) {
                    logger.fine("Force removing file " + f2);
                    sftpClient.rm(f2); 
                } else {
                    if (dasFiles.contains(f2))
                        sftpClient.rm(f2);
                }
            }
        }
    }
    
    /** 
     * Parses static domain.xml of all domains to determine if a node is configured
     * for use.
     * @param host remote host
     * @return true|false
     */
    protected boolean checkIfNodeExistsForHost(String host) {
        boolean result = false;
        try {
            File domainsDirFile = DomainDirs.getDefaultDomainsDir();
            
            File[] files = domainsDirFile.listFiles(new FileFilter() {
                        public boolean accept(File f) {
                            return f.isDirectory();
                        }
                    });                    

            for (File file: files) {
                DomainDirs dir = new DomainDirs(file);
                File domainXMLFile = dir.getServerDirs().getDomainXml();
                logger.finer("Domain XML file = " + domainXMLFile);
                try {
                    Habitat habitat = Globals.getStaticHabitat();
                    ConfigParser parser = new ConfigParser(habitat);
                    URL domainURL = domainXMLFile.toURI().toURL();
                    DomDocument doc = parser.parse(domainURL);
                    Dom domDomain = doc.getRoot();
                    Domain domain = domDomain.createProxy(Domain.class);
                    Nodes nodes = domain.getNodes();

                    for (Node node: nodes.getNode()) {
                        if (node.getNodeHost().equals(host)) {
                            result = true;
                        }
                    }
                } catch (Exception e) {
                    if(logger.isLoggable(Level.FINE)) {
                        e.printStackTrace();
                    }
                }

            }

           
        } catch (IOException ioe) {
            if(logger.isLoggable(Level.FINE)) {
                ioe.printStackTrace();
            }
        }
        return result;        
    }
    
    /**
     * Obtains the real password from the domain specific keystore given an alias
     * @param host host that we are connecting to
     * @param alias password alias of form ${ALIAS=xxx}
     * @return real password of ssh user, null if not found
     */
    protected String expandPasswordAlias(String host, String alias, boolean verifyConn) {
        String expandedPassword = null;
        boolean connStatus = false;
        
        try {
            File domainsDirFile = DomainDirs.getDefaultDomainsDir();

            //get the list of domains
            File[] files = domainsDirFile.listFiles(new FileFilter() {
                        public boolean accept(File f) {
                            return f.isDirectory();
                        }
                    });

            for (File f:files) {
                //the following property is required for initializing the password helper
                System.setProperty(SystemPropertyConstants.INSTANCE_ROOT_PROPERTY, f.getAbsolutePath());
                try {
                    final MasterPassword masterPasswordHelper = Globals.getDefaultHabitat()
                            .getComponent(MasterPassword.class, "Security SSL Password Provider Service");
                    
                    final PasswordAdapter pa = masterPasswordHelper.getMasterPasswordAdapter();
                    final boolean     exists = pa.aliasExists(alias);
                    if (exists) {
                        String mPass = getMasterPassword(f.getName());
                        masterPasswordHelper.setMasterPassword(mPass.toCharArray());
                        expandedPassword = masterPasswordHelper.getMasterPasswordAdapter().getPasswordForAlias(alias);
                    }
                } catch (Exception e) {
                    if(logger.isLoggable(Level.FINER)) {
                        logger.finer(StringUtils.cat(": ", alias, e.getMessage()));
                    }
                    logger.warning(Strings.get("GetPasswordFailure", f.getName()));
                    continue;
                }
                
                if(expandedPassword != null) {                    
                    SSHLauncher sshL = new SSHLauncher();
                    if (host != null) {
                        sshpassword = expandedPassword;
                        sshL.init(sshuser, host,  sshport, sshpassword, null, null, logger);
                        connStatus = sshL.checkPasswordAuth();
                        if (!connStatus) {
                            logger.warning(Strings.get("PasswordAuthFailure", f.getName()));
                        }
                    } else {
                        sshkeypassphrase = expandedPassword;
                        if (verifyConn) {
                            sshL.init(sshuser, hosts[0],  sshport, sshpassword, sshkeyfile, sshkeypassphrase, logger);
                            connStatus = sshL.checkConnection();
                            if (!connStatus) {
                                logger.warning(Strings.get("PasswordAuthFailure", f.getName()));
                            }
                        }
                    }
                    
                    if (connStatus) {
                        break;
                    }
                }
            }
        } catch (IOException ioe) {
            if(logger.isLoggable(Level.FINER)) {
                logger.finer(ioe.getMessage());
            }
        }
        return expandedPassword;
    }
}
