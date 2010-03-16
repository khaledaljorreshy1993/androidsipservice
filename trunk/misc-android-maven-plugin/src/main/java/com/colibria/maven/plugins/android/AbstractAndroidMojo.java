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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author Sebastian Dehne
 */
@SuppressWarnings({"JavaDoc"})
public abstract class AbstractAndroidMojo extends AbstractMojo {

    /**
     * @parameter default-value="aapt"
     */
    protected String aapt_command;

    /**
     * @parameter default-value="aidl"
     */
    protected String aidl_command;

    /**
     * @parameter default-value="dx"
     */
    protected String dex_command;

    /**
     * @parameter default-value="apkbuilder"
     */
    protected String apkbuilder_command;

    /**
     * @parameter default-value="adb"
     */
    protected String adb_command;

    /**
     * @parameter default-value="src/main/java"
     */
    protected String srcDir;

    /**
     * @parameter default-value="src/main/aidl"
     */
    protected String aIdlDir;

    /**
     * @parameter default-value="src/main/resources/"
     */
    protected String resourcesDir;

    /**
     * @parameter default-value="src/main/resources/AndroidManifest.xml"
     */
    protected String androidManifest;

    /**
     * @parameter default-value="src/main/resources/android_res"
     */
    protected String androidResources;

    /**
     * @parameter expression="target/${project.artifactId}.ap_"
     */
    protected String resourcesPackage;

    /**
     * @parameter expression="target/${project.artifactId}-unsigned.apk"
     */
    protected String appPackageUnsigned;

    /**
     * @parameter expression="target/${project.artifactId}-signed.apk"
     */
    protected String appPackageSigned;

    /**
     * @parameter default-value="target/classes.dex"
     */
    protected String dexfile;

    /**
     * @parameter default-value="debug.keystore"
     */
    protected String keyStore;

    /**
     * POM
     *
     * @parameter expression="${project}"
     * @readonly
     * @required
     */
    protected MavenProject project;

    protected String getAndroidJar() throws MojoExecutionException {
        @SuppressWarnings({"unchecked"})
        Set<Artifact> depends = project.getDependencyArtifacts();
        String androidFile = null;
        for (Artifact o : depends) {
            if (o.getGroupId().equals("com.android") && o.getArtifactId().equals("android") && o.getFile() != null) {
                androidFile = o.getFile().getAbsolutePath();
                break;
            }
        }

        if (androidFile == null) {
            throw new MojoExecutionException("Could not find the android.jar in the list of dependencies");
        }

        return androidFile;
    }

    protected Collection<String> getExternalJars() {
        List<String> result = new LinkedList<String>();
        @SuppressWarnings({"unchecked"})
        Set<Artifact> depends = project.getDependencyArtifacts();
        for (Artifact o : depends) {
            if (!o.getFile().getAbsolutePath().matches(".*android[\\-\\d\\.]*\\.jar$")) {
                result.add(o.getFile().getAbsolutePath());
            }
        }
        return result;
    }

    protected void executeAndroidTool(String[] command) throws MojoExecutionException {
        StringBuffer sb = new StringBuffer();
        for (String tmp : command) {
            sb.append(tmp).append(" ");
        }
        getLog().info("About to execute command: \"" + sb.toString() + "\"");

        try {
            Process child = Runtime.getRuntime().exec(command);

            // print the stdout
            BufferedReader stdout = new BufferedReader(new InputStreamReader(child.getInputStream()));
            String line;
            while ((line = stdout.readLine()) != null) {
                getLog().debug(line);
            }

            // print the stderr
            BufferedReader stderr = new BufferedReader(new InputStreamReader(child.getErrorStream()));
            while ((line = stderr.readLine()) != null) {
                getLog().error(line);
            }

            int retVal;
            if ((retVal = child.waitFor()) != 0) {
                throw new MojoExecutionException("Command returned unexpected result: " + retVal);
            }

        } catch (IOException e) {
            getLog().error("Could not execute command successfully", e);
            throw new MojoExecutionException("error", e);
        } catch (InterruptedException e) {
            //void
        }
    }


}
