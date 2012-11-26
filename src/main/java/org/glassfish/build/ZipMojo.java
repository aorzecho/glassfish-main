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
 * https://glassfish.java.net/public/CDDL+GPL_1_1.html
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

package org.glassfish.build;

import com.sun.enterprise.build.DistributionArtifactHandler;
import java.io.File;
import java.util.Iterator;
import java.util.Properties;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.taskdefs.Zip.Duplicate;
import org.apache.tools.ant.types.ZipFileSet;

/**
 * 
 * Creates a zip file
 *
 * @goal zip
 * @phase package
 * @requiresProject
 * @requiresDependencyResolution runtime
 *
 * @author Romain Grecourt
 */
public class ZipMojo extends AbstractAntMojo {

    /**
     * The directory where the zip will be created.
     *
     * @parameter expression="${gfzip.outputDirectory}" default-value="${project.build.directory}"
     */
    protected File outputDirectory;

    /**
     * The file name of the created zip.
     *
     * @parameter expression="${gfzip.finalName}" default-value="${project.build.finalName}"
     */
    protected String finalName;

    /**
     * behavior when a duplicate file is found ;
     * Valid values are "add", "preserve", and "fail" ; default value is "add"
     *
     * @parameter expression="${gfzip.duplicate}" default-value="add"
     */
    protected String duplicate;

    /**
     * Filesets describe content to include in the zip
     *
     * @parameter expression="${gfzip.filesets}"
     */
    protected ZipFileSet[] filesets;

    /**
     * dir the root of the directory tree of the default FileSet ;
     * Only when not fileset(s) provided.
     * 
     * @parameter expression="${gfzip.dir}" default-value="${project.build.directory}"
     */
    protected File dir;

    /**
     * comma- or space-separated list of patterns of files that must be included ;
     * all files are included when omitted ; Only when not fileset(s) provided.
     *
     * @parameter expression="${gfzip.includes}"
     */
    protected String includes;

    /**
     * comma- or space-separated list of patterns of files that must be included ;
     * all files are included when omitted ; Only when not fileset(s) provided.
     *
     * @parameter expression="${gfzip.excludes}"
     */
    protected String excludes;
    
    /**
     * The extension of the generated file.
     * 
     * @parameter expression="${gfzip.extension}" default-value="zip"
     */
    protected String extension;
    
    /**
     * Attach the produced artifact
     * 
     * @parameter expression="${gfzip.attach}" default-value="true"
     */
    protected Boolean attach;  
    

    public void execute() throws MojoExecutionException, MojoFailureException {

        Project antProject = new Project();
        antProject.addBuildListener(this);
        
        this.project.addCompileSourceRoot(null);

        Properties mavenProperties = project.getProperties();
        Iterator it = mavenProperties.keySet().iterator();
        while(it.hasNext()){
            String key = (String) it.next();
            antProject.setProperty(key, mavenProperties.getProperty(key));
        }
        Zip zip = new Zip();
        zip.setProject(antProject);

        // compute the final file name
        StringBuilder finalFileName = new StringBuilder(finalName);
        finalFileName.append('.');
        finalFileName.append(extension);
        
        File target = new File(outputDirectory, finalFileName.toString());
        zip.setDestFile(target);

        Duplicate df = new Zip.Duplicate();
        df.setValue(duplicate);
        zip.setDuplicate(df);
        getLog().info("duplicate: " + duplicate);

        if(filesets.length > 0){
            for(ZipFileSet zfs : filesets){
                getLog().info("file set : " + zfs.getDir()
                        + " (excludes: "+displayStringArray(zfs.mergeExcludes(antProject))
                        + ", includes: "+displayStringArray(zfs.mergeIncludes(antProject))+")");
                zip.addZipfileset(zfs);
            }
        } else {
            ZipFileSet zfs = new ZipFileSet();
            zfs.setDir(dir);
            zfs.setIncludes(includes);
            zfs.setExcludes(excludes);
            zfs.setDirMode("755");
            // work around for http://issues.apache.org/bugzilla/show_bug.cgi?id=42122
            zfs.setFileMode("644");
            getLog().info("default file set: " + zfs.getDir()
                    + " ( excludes: [ "+excludes+" ]"
                    + ", includes: [ "+includes+" ])");

            zip.addZipfileset(zfs);
        }

        zip.executeMain();

        if (attach.booleanValue()) {
            project.getArtifact().setFile(target);
            project.getArtifact().setArtifactHandler(new DistributionArtifactHandler(extension, project.getPackaging()));
        }
    }

    public static String displayStringArray(String[] strArray){
        StringBuilder sb = new StringBuilder("[");
        if (strArray != null) {
            for(int i=0 ; i<strArray.length ; i++){
                sb.append(strArray[i]);
                if(i != strArray.length -1) {
                    sb.append(", ");
                }
            }
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    String prefix() {
        return "[zip]";
    }
}
