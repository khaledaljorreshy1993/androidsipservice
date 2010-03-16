/*
 *
 * Copyright (C) 2010 Colibria AS
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.colibria.maven.plugins.android;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.*;

/**
 * @author Sebastian
 * @goal sign
 */
public class AndroidSignAppMojo extends AbstractAndroidMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {
        File keyStore = new File(this.keyStore);
        if (!keyStore.exists()) {
            keyStore = createDebugKeyStore(this.keyStore);
        }

        // copy the orig file to the dst file
        File dst = new File(project.getBasedir().getAbsolutePath() + File.separatorChar + appPackageSigned);
        File orig = new File(project.getBasedir().getAbsolutePath() + File.separatorChar + appPackageUnsigned);
        try {
            dst.delete();
            dst.createNewFile();
            InputStream is = new FileInputStream(orig);
            OutputStream os = new FileOutputStream(dst);
            int i;
            while ((i = is.read()) != -1) {
                os.write(i);
            }
            is.close();
            os.close();
        } catch (Exception e) {
            throw new MojoExecutionException("Could not copy file", e);
        }

        String[] command = new String[]{
                "jarsigner",
                "-verbose",
                "-storepass",
                "android",
                "-keypass",
                "android",
                "-keystore",
                this.keyStore,
                project.getBasedir().getAbsolutePath() + File.separatorChar + appPackageSigned,
                "androiddebugkey"
        };

        try {
            executeAndroidTool(command);
        } catch (MojoExecutionException e) {
            dst.delete();
            throw e;
        }

        getLog().info("Done");
    }

    private File createDebugKeyStore(String keyStore) throws MojoExecutionException {
        getLog().warn("Could not find the keystore (" + keyStore + "), trying to generate it for you using the debug settings");

        String[] command = {
                "keytool",
                "-genkey",
                "-alias",
                "androiddebugkey",
                "-keystore",
                keyStore,
                "-dname",
                "CN=Android Debug, O=Android, C=US",
                "-storepass",
                "android",
                "-keypass",
                "android"
        };
        executeAndroidTool(command);
        return new File(keyStore);
    }

}
