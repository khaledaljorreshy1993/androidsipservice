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

/**
 * @author Sebastian Dehne
 * @goal deploy
 */
@SuppressWarnings({"UnusedDeclaration", "JavaDoc"})
public class AndroidDeployMojo extends AbstractAndroidMojo {

    /**
     * @parameter default-value=""
     */
    private String deployOn;

    public void execute() throws MojoExecutionException, MojoFailureException {

        final String dstOption;
        if ("device".equalsIgnoreCase(deployOn)) {
            dstOption = "-d";
        } else {
            dstOption = "-e";
        }

        String[] command = new String[]{
                adb_command,
                dstOption,
                "install",
                "-r",
                appPackageSigned
        };

        try {
            executeAndroidTool(command);
        } catch (MojoExecutionException e) {
            getLog().error("Could not install android app onto device ");
        }

        getLog().info("Done");
    }
}
