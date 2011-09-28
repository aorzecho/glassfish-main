/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.glassfish.admingui.console.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 *
 * @author anilam
 */
public class FileUtil {

    static public File inputStreamToFile(InputStream inputStream, String origFileName) throws IOException {

          /* We don't want to use a random tmpfile name, just use the same file name as uploaded.
           * Otherwise, OE will use this random filename to create the services, adding all the random number.
           */
//        int index = origFileName.indexOf(".");
//        String suffix = null;
//        if (index > 0) {
//            suffix = origFileName.substring(index);
//        }
//        String prefix = origFileName.substring(0, index);
//        File tmpFile = File.createTempFile("gf-" + prefix, suffix);
//        tmpFile.deleteOnExit();


        String tmpdir = System.getProperty("java.io.tmpdir");
        File tmpFile = new File(tmpdir, origFileName);
        if (tmpFile.exists()){
            tmpFile.delete();
            tmpFile = new File(tmpdir, origFileName);
        }
        tmpFile.deleteOnExit();
          
        OutputStream out = new FileOutputStream(tmpFile);
        byte buf[] = new byte[4096];
        int len;
        while ((len = inputStream.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.close();
        inputStream.close();
        System.out.println("\ntmp is created." + tmpFile.getAbsolutePath());
        return tmpFile;
    }


}
