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
import java.util.LinkedList;
import java.util.List;

/**
 * @author Sebastian Dehne
 * @goal pkg_app
 * @requiresDependencyResolution
 */
public class AndroidPackageAppMojo extends AbstractAndroidMojo {


    public void execute() throws MojoExecutionException, MojoFailureException {
        Collection<String> libs = getExternalJars();

        List<String> command = new LinkedList<String>();
        command.add(apkbuilder_command);
        command.add(project.getBasedir().getAbsolutePath() + File.separatorChar + appPackageUnsigned);
        command.add("-u");
        command.add("-z");
        command.add(project.getBasedir().getAbsolutePath() + File.separatorChar + resourcesPackage);
        command.add("-f");
        command.add(project.getBasedir().getAbsolutePath() + File.separatorChar + dexfile);

        // adding some resoucres, lile log4j
        File resourcesPath = new File(project.getBasedir().getAbsolutePath() + File.separatorChar + resourcesDir);
        File[] files = resourcesPath.listFiles();
        for (File f : files) {
            if (f.isFile() && !f.getPath().contains("AndroidManifest.xml")) {
                command.add("-f");
                command.add(f.getAbsolutePath());
            }
        }

        command.add("-rf");
        command.add(project.getBasedir().getAbsolutePath() + File.separatorChar + srcDir);

        // adding all libs
        for (String s : libs) {
            command.add("-rj");
            command.add(s);
        }

        String[] tmp = command.toArray(new String[command.size()]);
        executeAndroidTool(tmp);

        getLog().info("Done");
    }
}
