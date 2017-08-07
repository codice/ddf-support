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
package org.codeice.github.dependency.check;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.codice.github.dependency.check.GithubDependencyCheckPlugin;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class GithubDependencyCheckPluginTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testFailGithubDependencyCheck() throws Exception {
        expectedException.expect(MojoExecutionException.class);
        expectedException.expectMessage("GitHub dependency detected.");

        String filePath =
                new java.io.File(".").getCanonicalPath() + "/src/test/resources/failScanDir";

        GithubDependencyCheckPlugin dependencyCheckPlugin =
                new GithubDependencyCheckPlugin(filePath);

        dependencyCheckPlugin.execute();
        Assert.fail("Expected exception to be thrown");
    }

    @Test
    public void passGithubDependencyCheckTest() {
        try {
            String filePath =
                    new java.io.File(".").getCanonicalPath() + "/src/test/resources/passScanDir";

            GithubDependencyCheckPlugin dependencyCheckPlugin = new GithubDependencyCheckPlugin(
                    filePath);

            try {
                dependencyCheckPlugin.execute();
            } catch (MojoExecutionException e) {
                Assert.fail("MojoExecutionException thrown.");
            }

        } catch (IOException e) {
            Assert.fail("IOException thrown.");
        }
    }
}
