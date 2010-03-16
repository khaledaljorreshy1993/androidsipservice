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
import java.util.Collection;

/**
 * @author Sebastian Dehne
 * @goal dex
 * @requiresDependencyResolution
 */
@SuppressWarnings({"JavaDoc"})
public class AndroidConvertToDexMojo extends AbstractAndroidMojo {


    public void execute() throws MojoExecutionException, MojoFailureException {

        File classesDir = new File(project.getBasedir().getAbsolutePath() + File.separatorChar + "target/classes");
        if (!classesDir.exists()) {
            throw new MojoExecutionException("Cannot convert classes to dex if the classes aren't there yet. Please run mvn compile first");
        }

        Collection<String> externalLibs = getExternalJars();

        String[] command = new String[4 + externalLibs.size()];
        command[0] = dex_command;
        command[1] = "--dex";
        command[2] = "--output=" + project.getBasedir().getAbsolutePath() + File.separatorChar + dexfile;
        command[3] = classesDir.getAbsolutePath();
        int pos = 3;
        for (String s : externalLibs) {
            command[++pos] = s;
        }

        executeAndroidTool(command);

        getLog().info("Done");
    }
}
