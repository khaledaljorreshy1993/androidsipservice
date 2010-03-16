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
import java.io.IOException;

/**
 * @author Sebastian Dehne
 * @goal pkg_res
 * @requiresDependencyResolution
 */
@SuppressWarnings({"ResultOfMethodCallIgnored"})
public class AndroidPackageResourcesMojo extends AbstractAndroidMojo {


    public void execute() throws MojoExecutionException, MojoFailureException {

        // make sure the last dir in the File exists, but no the actual file
        File f = new File(project.getBasedir().getAbsolutePath() + File.separatorChar + resourcesPackage);
        try {
            f.mkdirs();
            f.delete();
            f.createNewFile();
            f.delete();
        } catch (IOException e) {
            throw new MojoExecutionException("", e);
        }

        String[] command = new String[]{
                aapt_command,
                "package",
                "-f",
                "-M",
                project.getBasedir().getAbsolutePath() + File.separatorChar + androidManifest,
                "-S",
                project.getBasedir().getAbsolutePath() + File.separatorChar + androidResources,
                "-I",
                getAndroidJar(),
                "-F",
                f.getAbsolutePath()
        };

        executeAndroidTool(command);

        getLog().info("Done");
    }
}
