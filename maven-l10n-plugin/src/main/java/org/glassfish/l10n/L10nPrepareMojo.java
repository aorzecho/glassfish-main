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

package org.glassfish.l10n;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.io.IOException;
import java.util.List;

/**
 * Goal which picks up original file froms enDirectory and
 * generate modified ones under l10nDirectory.
 * Expects original resource files copied over to enDirectory
 * before this goal is executed.
 * Also generates simple translation status report.
 *
 * @goal l10n-prepare
 * 
 * @phase process-resources
 *
 * @author Shinya Ogino
 */
public class L10nPrepareMojo
    extends AbstractMojo
{
    /**
     * Resource files location.
     * @parameter expression="${basedir}/src/main/resources_en/"
     * @required
     */
    private File enDirectory;
 
    /**
     * Localization resource files location.
     * @parameter expression="${basedir}/src/main/resources/"
     * @required
     */
    private File l10nDirectory;

    /**
     * Localization status report file.
     * @parameter expression="${basedir}/l10n.status"
     * @required
     */
    private File report;

    public void execute()
        throws MojoExecutionException
    {
        if (!enDirectory.exists()) {
            throw new MojoExecutionException("Error: Directory " + enDirectory.getAbsolutePath()
                    + " doesn't exist.");
        }
        if (!l10nDirectory.exists()) {
                getLog().info("Creating directory " + l10nDirectory.toString() + " ...");
                l10nDirectory.mkdirs();
        }

        LineNumberReader reader = null;
        FileWriter writer = null;
        FileWriter reportWriter = null;
        try {
            // get List of English .properties files under enDirectory
            List<File> files = L10nUtil.findFiles(enDirectory, ".*\\.properties", ".*_\\w\\w\\.properties");
            // For each English .properties file,
            for (File f : files) {
                // rename the file
                File tmpf = new File(f.getAbsolutePath() + ".tmp");
                f.renameTo(tmpf);
                // open files.
                FileReader r = new FileReader(tmpf);
                reader = new LineNumberReader(r);
                writer = new FileWriter(f);
                getLog().info("Processing " + f.toString() + " ...");

                StringBuffer buffer = new StringBuffer();
                // for each line, 
                String line;
                while ((line = reader.readLine()) != null) {
                    // if it is a blank line,
                    // white space are [ \t\n\x0B\f\r] and matched by \s
                    if (line.matches("^\\s*$")) {
                        // copy the line without any changes.
                        writer.write(line + "\n");
                    // else if it is a comment line, ie, first non blank char is # or !
                    } else if (line.matches("^\\s*[#!].*")) {
                        // copy the line without any changes.
                        writer.write(line + "\n");
                    // else
                    } else {
                        // copy the line adding #EN at the beginning
                        writer.write("#EN " + line + "\n");
                        // add the line to buffer
                        buffer.append(line + "\n");
                        // if the line terminator is not escaped by backslash
                        if (!line.matches(".*\\\\$")) {
                            // output buffer contents
                            writer.write(buffer.toString());
                            // clear buffer
                            buffer.setLength(0);
                        }
                    }
                } // end while readLine 
                writer.close();
                reader.close();
                tmpf.delete();
            } // end for file

            // Finally, generate localization status report.
            reportWriter = new FileWriter(report);
            String reportText = L10nUtil.getStatusReport(enDirectory, l10nDirectory);
            reportWriter.write(reportText);
            getLog().info("Translation status for this project.\n" + reportText);

        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        } finally {
            try {
                if (reader != null) reader.close();
                if (writer != null) writer.close();
                if (reportWriter != null) reportWriter.close();
            } catch (IOException e) { /* ignore */ }
        }
    }
}
