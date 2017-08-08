/**
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General private License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General private License for more details. A copy of the GNU Lesser General private License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 **/
package org.codice.github.dependency.check;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class GithubDependencyCheckPluginTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void failGithubDependencyCheckTest() throws Exception {
        expectedException.expect(MojoExecutionException.class);
        expectedException.expectMessage("GitHub dependency detected.");

        File baseDir = new File(
                new File(".").getCanonicalPath() + "/src/test/resources/failScanDir");

        GithubDependencyCheckPlugin dependencyCheckPlugin = new GithubDependencyCheckPlugin();

        dependencyCheckPlugin.setBaseDir(baseDir);
        dependencyCheckPlugin.execute();
    }

    @Test
    public void passGithubDependencyCheckTest() throws MojoExecutionException, IOException {
        File baseDir = new File(
                new File(".").getCanonicalPath() + "/src/test/resources/passScanDir");
        GithubDependencyCheckPlugin dependencyCheckPlugin = new GithubDependencyCheckPlugin();

        dependencyCheckPlugin.setBaseDir(baseDir);
        dependencyCheckPlugin.execute();
    }

    @Test
    public void oneBadLinkGithubDependencyCheckTest() throws MojoExecutionException, IOException {
        expectedException.expect(MojoExecutionException.class);
        expectedException.expectMessage("GitHub dependency detected.");

        File baseDir = new File(
                new File(".").getCanonicalPath() + "/src/test/resources/oneBadDependency");

        GithubDependencyCheckPlugin dependencyCheckPlugin = new GithubDependencyCheckPlugin();

        dependencyCheckPlugin.setBaseDir(baseDir);
        dependencyCheckPlugin.execute();
    }

    @Test
    public void noBaseDirGithubDependencyCheckTest() throws MojoExecutionException, IOException {
        expectedException.expect(MojoExecutionException.class);
        expectedException.expectMessage("Unable to find base directory.");

        GithubDependencyCheckPlugin dependencyCheckPlugin = new GithubDependencyCheckPlugin();
        dependencyCheckPlugin.execute();
    }
}
