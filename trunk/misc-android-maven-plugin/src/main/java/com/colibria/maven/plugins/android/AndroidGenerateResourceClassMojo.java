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

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * Generate the aidl interface code plus the R.java files for this project's resources.
 *
 * @author Sebastian Dehne
 * @goal gen_res
 * @requiresDependencyResolution
 */
@SuppressWarnings({"JavaDoc"})
public class AndroidGenerateResourceClassMojo extends AbstractAndroidMojo {

    private static void findAidlFiles(File dir, List<File> aidlFiles) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                if (!f.isDirectory() && f.getName().endsWith(".aidl")) {
                    aidlFiles.add(f);
                } else if (f.isDirectory()) {
                    findAidlFiles(f, aidlFiles);
                }
            }
        }
    }

    public void execute() throws MojoExecutionException, MojoFailureException {

        String[] command;

        /*
         * Generate R.java
         */
        if (new File(project.getBasedir().getAbsolutePath() + File.separatorChar + androidResources).exists()) {
            command = new String[]{
                    aapt_command,
                    "package",
                    "-m",
                    "-J",
                    project.getBasedir().getAbsolutePath() + File.separatorChar + srcDir,
                    "-M",
                    project.getBasedir().getAbsolutePath() + File.separatorChar + androidManifest,
                    "-S",
                    project.getBasedir().getAbsolutePath() + File.separatorChar + androidResources,
                    "-I",
                    getAndroidJar()
            };
            executeAndroidTool(command);
        } else {
            getLog().warn("Skipping generating R.java since no resources could be found");
        }


        /*
         * Generate AIDL code
         */
        // A) find all aidl files
        File f = new File(project.getBasedir().getAbsolutePath() + File.separatorChar + aIdlDir);
        List<File> aidlFiles = new LinkedList<File>();
        findAidlFiles(f, aidlFiles);

        if (aidlFiles.size() > 0) {
            for (File tmp : aidlFiles) {
                command = new String[]{
                        aidl_command,
                        "-I" + project.getBasedir().getAbsolutePath() + File.separatorChar + aIdlDir,
                        "-I" + project.getBasedir().getAbsolutePath() + File.separatorChar + srcDir,
                        "-o" + project.getBasedir().getAbsolutePath() + File.separatorChar + srcDir,
                        tmp.getAbsolutePath()
                };
                executeAndroidTool(command);
            }
        } else {
            getLog().info("Skipping generating of aidl interfaces since aidl files could be found");
        }

        getLog().info("Done");
    }
}
