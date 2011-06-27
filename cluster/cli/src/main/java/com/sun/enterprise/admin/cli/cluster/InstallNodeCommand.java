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


package com.sun.enterprise.admin.cli.cluster;

import com.sun.enterprise.util.SystemPropertyConstants;
import com.sun.enterprise.util.io.FileListerRelative;
import com.sun.enterprise.util.io.FileUtils;
import com.sun.enterprise.util.zip.ZipFileException;
import com.sun.enterprise.util.zip.ZipWriter;
import com.trilead.ssh2.SCPClient;
import org.glassfish.api.Param;
import org.glassfish.api.admin.CommandException;
import org.glassfish.cluster.ssh.launcher.SSHLauncher;
import org.glassfish.cluster.ssh.util.SSHUtil;
import org.glassfish.cluster.ssh.sftp.SFTPClient;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.PerLookup;
import org.jvnet.hk2.component.Habitat;
import org.glassfish.internal.api.Globals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author Rajiv Mordani
 */ 


@Service(name = "install-node")
@Scoped(PerLookup.class)

public class InstallNodeCommand extends SSHCommandsBase {

    @Param(name="archive", optional = true)
    private String archive;

    @Param(name = "installdir", optional = true, defaultValue = "${com.sun.aas.productRoot}")
    private String installDir;

    @Param(optional = true, defaultValue = "false")
    private boolean create;

    @Param(optional = true, defaultValue = "false")
    private boolean save;

    @Param(name = "force", optional = true, defaultValue = "false")
    private boolean force;

    @Inject
    private Habitat habitat;
    
    @Inject
    private SSHLauncher sshLauncher;

    private String archiveName;

    private boolean delete = true;

    @Override
    protected void validate() throws CommandException {
        Globals.setDefaultHabitat(habitat);
        
        installDir = resolver.resolve(installDir);
        if (!force) {
            for (String host: hosts) {
                if(checkIfNodeExistsForHost(host, installDir)) {
                    throw new CommandException(Strings.get("node.already.configured", host, installDir));
                }
            }
        }       
        sshuser = resolver.resolve(sshuser);
        if (sshkeyfile == null) {
            //if user hasn't specified a key file check if key exists in
            //default location
            String existingKey = SSHUtil.getExistingKeyFile();
            if (existingKey == null) {
                promptPass=true;
            } else {
                sshkeyfile = existingKey;
            }
        } else {
            validateKeyFile(sshkeyfile);
        }
        
        //we need the key passphrase if key is encrypted
        if(sshkeyfile != null && isEncryptedKey()){
            sshkeypassphrase=getSSHPassphrase(true);
        }
        
    }

    @Override
    protected int executeCommand() throws CommandException {

        File zipFile = null;
        try {

            String baseRootValue = getSystemProperty(SystemPropertyConstants.PRODUCT_ROOT_PROPERTY) ;
            ArrayList<String>  binDirFiles = new ArrayList<String>();
            zipFile = createZipFileIfNeeded(baseRootValue, binDirFiles);
            copyToHosts(zipFile, binDirFiles);            
        } catch (IOException ioe) {
            throw new CommandException(ioe);
        } catch (ZipFileException ze) {
            throw new CommandException(ze);
        } catch (InterruptedException e) {
            throw new CommandException(e);
        } finally {
            if (!save && delete) {
                if (zipFile != null)
                    zipFile.delete();
            }
        }

        return SUCCESS;
    }

    private void copyToHosts(File zipFile, ArrayList<String> binDirFiles) throws IOException, InterruptedException, CommandException {

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        
        for (String host: hosts) {
            sshLauncher.init(sshuser, host, sshport, sshpassword, sshkeyfile, sshkeypassphrase, logger);
            
            if (sshkeyfile != null && !sshLauncher.checkConnection()) {
                //key auth failed, so use password auth
                promptPass=true;
            }
            
            if (promptPass) {                
                sshpassword=getSSHPassword(host);
                //re-initialize
                sshLauncher.init(sshuser, host, sshport, sshpassword, sshkeyfile, sshkeypassphrase, logger);
            }

            installDir = installDir.replaceAll("\\\\","/");

            SFTPClient sftpClient = sshLauncher.getSFTPClient();
            SCPClient scpClient = sshLauncher.getSCPClient();
            try {
                if (!sftpClient.exists(installDir)) {
                    sftpClient.mkdirs(installDir, 0755);
                }
            } catch (IOException ioe){
                logger.info(Strings.get("mkdir.failed",installDir, host));
                throw new IOException(ioe);
            }

            if(checkIfAlreadyInstalled(host))
                continue;
            
            //delete the installDir contents if non-empty
            try {
                //get list of file in DAS installdir
                //File all = new File(resolver.resolve("${com.sun.aas.installRoot}"));
                String ins = resolver.resolve("${com.sun.aas.installRoot}") + "/../";

                File all = new File(ins);
                Set files = FileUtils.getAllFilesAndDirectoriesUnder(all);

                logger.finer("Total number of files under " + ins + " = " + files.size());
                String remoteDir = installDir;
                List<String> modList = new ArrayList<String>();
                if (!installDir.endsWith("/"))
                    remoteDir = remoteDir + "/";

                for (Object f:files) {
                    modList.add(remoteDir+FileUtils.makeForwardSlashes(((File)f).getPath()));
                }
            
                deleteRemoteFiles(sftpClient, modList, installDir, force);
            } catch (IOException ex) {
                logger.finer("Failed to remove installDir contents");
                throw new IOException (ex);
            }
            
            
            String zip = zipFile.getCanonicalPath();
            try {
                logger.info("Copying " + zip + " (" + zipFile.length() +" bytes)" +
                            " to " + host + ":" + installDir);
                // Looks like we need to quote the paths to scp in case they
                // contain spaces.
                scpClient.put(zipFile.getAbsolutePath(), FileUtils.quoteString(installDir));
                logger.finer("Copied " + zip + " to " + host + ":" + installDir);
            } catch (IOException ex){
                logger.info (Strings.get("cannot.copy.zip.file", zip, host));
                throw new IOException (ex);
            }

            try {
                logger.info("Installing " + archiveName + " into " + host + ":" + installDir);
                String unzipCommand = "cd '" + installDir + "'; jar -xvf " + archiveName;
                int status = sshLauncher.runCommand(unzipCommand, outStream);
                if (status != 0){
                    logger.info (Strings.get("jar.failed", host, outStream.toString()));
                    throw new CommandException ("Remote command output: " +outStream.toString());
                }
                logger.finer("Installed " + archiveName + " into " + host + ":" + installDir);
            } catch (IOException ioe){
                logger.info (Strings.get("jar.failed", host, outStream.toString()));
                throw new IOException (ioe);
            }

            try {
//                String zipFileName = archiveName;
            logger.info("Removing " + host + ":" + installDir + "/"+archiveName);
            sftpClient.rm(installDir + "/"+archiveName);
            logger.finer("Removed " + host + ":" + installDir + "/" + archiveName);
            } catch (IOException ioe){
                logger.info(Strings.get("remove.glassfish.failed",host, installDir));
                throw new IOException(ioe);
            }


            logger.info("Fixing file permissions of all files under " + host + ":" + installDir + "/bin");
            try {
                if (binDirFiles.isEmpty()) {
                    //binDirFiles can be empty if the archive isn't a fresh one
                    String cmd = "cd '" + installDir + "/glassfish/bin'; chmod 0755 *";
                    int status = sshLauncher.runCommand(cmd, outStream);
                    if (status != 0){
                        logger.info (Strings.get("jar.failed", host, outStream.toString()));
                        throw new CommandException ("Remote command output: " +outStream.toString());
                    } 
                } else {
                    for (String binDirFile: binDirFiles) {
                        sftpClient.chmod((installDir + "/" + binDirFile), 0755);
                    }
                }
                logger.finer("Fixed file permissions of all files under " + host + ":" + installDir + "/bin");
            } catch (IOException ioe){
                logger.info(Strings.get("fix.permissions.failed", host, installDir));
                throw new IOException(ioe);
            }
        }
    }

    private File createZipFileIfNeeded(String baseRootValue, ArrayList<String> binDirFiles) throws IOException, ZipFileException {

        File installRoot = new File(baseRootValue); 

        File zipFileLocation = null;
        File glassFishZipFile = null;        

        if (archive != null) {
            archive = archive.replaceAll("\\\\","/");
            archiveName = archive.substring(archive.lastIndexOf("/") + 1, archive.length());
            zipFileLocation = new File(archive.substring(0,archive.lastIndexOf("/")));
            glassFishZipFile = new File(archive);
            if (glassFishZipFile.exists() && !create) {
                logger.finer("Found " + glassFishZipFile.getCanonicalPath());
                delete = false;
                return glassFishZipFile;
            } else if (!zipFileLocation.canWrite()) {
                throw new IOException ("Cannot write to " + archive);
            }
        } else {
            zipFileLocation = new File(".");
            if (!zipFileLocation.canWrite()) {
                zipFileLocation = new File(System.getProperty("java.io.tmpdir"));
            }
            glassFishZipFile = File.createTempFile("glassfish", ".zip", zipFileLocation);
            String filePath  = glassFishZipFile.getCanonicalPath();
            filePath = filePath.replaceAll("\\\\","/");
            archiveName = filePath.substring(filePath.lastIndexOf("/") + 1, filePath.length());            
        }

        FileListerRelative lister = new FileListerRelative(installRoot);
        lister.keepEmptyDirectories();
        String[] files = lister.getFiles();

        List<String> resultFiles1 = Arrays.asList(files);
        ArrayList<String> resultFiles = new ArrayList<String>(resultFiles1);

        logger.finer("Number of files to be zipped = " + resultFiles.size());

        Iterator<String> iter = resultFiles.iterator();
        while(iter.hasNext()) {
            String fileName = iter.next();
            String fPath = fileName.substring(fileName.lastIndexOf("/") + 1);            
            if (fPath.equals(glassFishZipFile.getName())) {
                logger.finer("Removing file = " + fileName);
                iter.remove();
                continue;
            }
            if (fileName.contains("domains") || fileName.contains("nodes")) {
                iter.remove();
            } else if (fileName.startsWith("bin") || fileName.startsWith("glassfish/bin")) {
                binDirFiles.add(fileName);
            }
        }
        
        logger.finer("Final number of files to be zipped = " +resultFiles.size());

        String [] filesToZip = new String[resultFiles.size()];
        filesToZip = resultFiles.toArray(filesToZip);

        ZipWriter writer = new ZipWriter(FileUtils.safeGetCanonicalPath(glassFishZipFile), installRoot.toString(), filesToZip);
        writer.safeWrite();
        logger.info("Created installation zip " + glassFishZipFile.getCanonicalPath());

        return glassFishZipFile;
    }
    
    private void validateKeyFile(String file) throws CommandException {
        File f = new File(file);
        if (!f.exists()) {
            throw new CommandException(Strings.get("KeyDoesNotExist", file));
        }
    }
    
    /**
     * Determines if GlassFish is installed on remote host at specified location.
     * Uses SSH launcher to execute 'asadmin version'
     * @param host remote host
     * @return true if GlassFish install is found, false otherwise.
     * @throws CommandException
     * @throws IOException
     * @throws InterruptedException
     */
    private boolean checkIfAlreadyInstalled(String host) throws CommandException, IOException, InterruptedException {
        //check if an installation already exists on remote host
        boolean res = false;
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        try {
            String cmd = "'" + installDir + "/glassfish/bin/asadmin' version --local --terse";
            int status = sshLauncher.runCommand(cmd, outStream);
            if (status == 0){
                logger.finer(host + ":'" + cmd + "'" + " returned ["+ outStream.toString() + "]");
                logger.info(Strings.get("found.glassfish.install", host, installDir));
                res = true;
            } else {
                logger.finer(host + ":'" + cmd + "'" + " failed ["+ outStream.toString() + "]");
            }
        } catch (IOException ex) {
            logger.info (Strings.get("glassfish.install.check.failed", host));
            throw new IOException (ex);
        }
        return res;
    }
}
