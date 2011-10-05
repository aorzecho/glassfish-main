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
package org.glassfish.admin.rest.generator.client;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.glassfish.admin.rest.Util;
import org.glassfish.loader.util.ASClassLoaderUtil;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.config.ConfigModel;
import com.sun.appserv.server.util.Version;
import java.io.FileWriter;
import java.util.Scanner;

/**
 *
 * @author jasonlee
 */
public class JavaClientGenerator extends ClientGenerator {
    private File baseDirectory;
    private Map<String, URI> artifacts;

    public JavaClientGenerator(Habitat habitat) {
        super(habitat);
        baseDirectory = Util.createTempDirectory();
    }

    @Override
    public ClientClassWriter getClassWriter(ConfigModel model, String className, Class parent) {
        return new JavaClientClassWriter(model, className, parent, baseDirectory);
    }

    @Override
    public synchronized Map<String, URI> getArtifact() {
        if (artifacts == null) {
            artifacts = new HashMap<String, URI>();
            String versionString = version.getVersionNumber();
            createJar("rest-client-sources-" + versionString + ".jar", ".java");
            compileSources();
            createJar("rest-client-" + versionString + ".jar", ".class");
            addPom(versionString);

            Util.deleteDirectory(baseDirectory);
        }
        return artifacts;
    }

    private void compileSources() {
        try {
            List<File> files = new ArrayList<File>();
            gatherFiles(baseDirectory, files);

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

            List<String> options = new ArrayList<String>();
            options.add("-cp");
            StringBuilder sb = new StringBuilder();
            sb.append(ASClassLoaderUtil.getModuleClassPath(habitat, "", null));
            options.add(sb.toString());
            
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(files);
            boolean success = compiler.getTask(null, fileManager, null, options, null, compilationUnits).call();
            
            fileManager.close();
        } catch (IOException ex) {
            Logger.getLogger(JavaClientGenerator.class.getName()).
                    log(Level.SEVERE, null, ex);
        }
    }

    private void createJar(String fileName, String ext) {
        JarOutputStream target = null;
        try {
            File jarDir = Util.createTempDirectory();
            File jarFile = new File(jarDir, fileName);
            jarFile.createNewFile();
            jarFile.deleteOnExit();
            target = new JarOutputStream(new FileOutputStream(jarFile));

            addFiles(baseDirectory, target, ext);
            target.close();
            
            artifacts.put(jarFile.getName(), jarFile.toURI());
        } catch (Exception ex) {
            Logger.getLogger(JavaClientGenerator.class.getName()).
                    log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (target != null) {
                    target.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(JavaClientGenerator.class.getName()).
                        log(Level.SEVERE, null, ex);
            }
        }
    }
    
    private void gatherFiles(File file, List<File> list) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files.length != 0) {
                for (File f : files) {
                    gatherFiles(f, list);
                }
            }
        } else {
            list.add(file);
        }
    }
    
    private void addPom(String versionString)  {
        try {
            String pom = new Scanner(getClass().getResourceAsStream("/client/pom.xml.template")).useDelimiter("\\Z").next();
            pom = pom.replace("${glassfish.version}", versionString);
            File out = File.createTempFile("pom", "xml");
            out.deleteOnExit();
            FileWriter writer = new FileWriter(out);
            writer.write(pom);
            writer.close();
            
            artifacts.put("pom.xml", out.toURI());
        } catch (IOException ex) {
            Logger.getLogger(JavaClientGenerator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void addFiles(File dir, JarOutputStream target, String ext) throws IOException {
        if (dir == null || !dir.exists()) {
            return;
        }

        if (dir.isDirectory()) {
            File[] f = dir.listFiles();
            if (f.length != 0) {
                for (File file : f) {
                    addFiles(file, target, ext);
                }
            }
        } else {
            if (dir.getName().endsWith(ext)) {
                add(dir, target);
            }
        }
    }

    private void add(File source, JarOutputStream target) throws IOException {
        BufferedInputStream in = null;
        try {
            if (source.isDirectory()) {
                String name = source.getPath().replace("\\", "/");
                if (!name.isEmpty()) {
                    if (!name.endsWith("/")) {
                        name += "/";
                    }
                    JarEntry entry = new JarEntry(name);
                    entry.setTime(source.lastModified());
                    target.putNextEntry(entry);
                    target.closeEntry();
                }
                for (File nestedFile : source.listFiles()) {
                    add(nestedFile, target);
                }
                return;
            }

            String sourcePath = source.getPath()
                    .replace("\\\\", "/")
                    .substring(baseDirectory.getPath().length()+1);

            JarEntry entry = new JarEntry(sourcePath);
            entry.setTime(source.lastModified());
            target.putNextEntry(entry);
            in = new BufferedInputStream(new FileInputStream(source));

            byte[] buffer = new byte[1024];
            while (true) {
                int count = in.read(buffer);
                if (count == -1) {
                    break;
                }
                target.write(buffer, 0, count);
            }
            target.closeEntry();
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }
}