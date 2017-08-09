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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.DirectoryScanner;

@Mojo(name = "github-dependency-check", defaultPhase = LifecyclePhase.VERIFY)
public class GithubDependencyCheckPlugin extends AbstractMojo {

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private File baseDir;

    private static Pattern PATTERN = Pattern.compile(
            "^.*resolved\\s*[\"](.*://.*github[.]com.*)[\"]$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Scans a project's yarn files to determine whether they contain GitHub dependencies.
     *
     * @throws MojoExecutionException - {@link MojoExecutionException} which creates a build failure
     */
    public void execute() throws MojoExecutionException {
        if (baseDir == null) {
            throw new MojoExecutionException("Unable to find base directory.");
        } else {
            String[] yarnFiles = getYarnFiles(baseDir);

            for (String filePath : yarnFiles) {
                scanFile(filePath);
            }
        }
    }

    private void scanFile(String filePath) throws MojoExecutionException {
        Path fullPath = null;
        try {
            fullPath = Paths.get(baseDir.getCanonicalPath(), filePath)
                    .toRealPath();

            boolean containsGithubDependency = Files.lines(fullPath, StandardCharsets.UTF_8)
                    .anyMatch(line -> PATTERN.matcher(line)
                            .matches());
            if (containsGithubDependency) {
                getLog().error("GitHub dependency detected in yarn file: " + fullPath);
                throw new MojoExecutionException("GitHub dependency detected.");

            }
        } catch (IOException e) {
            getLog().error("File" + fullPath + "not found.", e);
        }
    }

    /**
     * Returns the paths of the yarn files in the base directory.
     *
     * @param baseDir the directory to scan.
     * @return a {@code path} to all the yarn files in the given base directory.
     */
    private String[] getYarnFiles(File baseDir) {
        DirectoryScanner scanner = new DirectoryScanner();
        String[] includes = {"yarn.lock"};

        scanner.setIncludes(includes);
        scanner.setBasedir(baseDir);
        scanner.scan();

        return scanner.getIncludedFiles();
    }

    public void setBaseDir(File baseDir) {
        this.baseDir = baseDir;
    }
}


