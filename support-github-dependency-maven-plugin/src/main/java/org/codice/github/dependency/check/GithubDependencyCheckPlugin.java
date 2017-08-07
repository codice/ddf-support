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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;

@Mojo(name= "github_dependency")
public class GithubDependencyCheckPlugin extends AbstractMojo{

    private String baseDir;

    @Parameter(defaultValue = "${project}")
    MavenProject project;

    public GithubDependencyCheckPlugin() {
        baseDir = null;
    }

    /**
     * Constructor with that allows to set the base directory.
     *
     * @param baseDirPath the base directory to scan
     */
    public GithubDependencyCheckPlugin(String baseDirPath) {
        baseDir = baseDirPath;
    }

    /**
     * Scans a project's yarn files to determine whether they contain GitHub dependencies.
     *
     * @throws MojoExecutionException - {@link MojoExecutionException} which creates a build failure
     */
    public void execute() throws MojoExecutionException {
        if (baseDir == null) {
            try {
                baseDir = project.getBasedir()
                        .getCanonicalPath();
            } catch (IOException e) {
                getLog().error("Base directory" + baseDir + "not found.", e);
            }
        }
        if (baseDir != null) {
            String[] yarnFiles = getYarnFiles(baseDir);

            for (String filePath : yarnFiles) {
                Path fullPath = null;
                try {
                    fullPath = Paths.get(baseDir + "/" + filePath)
                            .toRealPath();
                    String fileContent = new String(Files.readAllBytes(fullPath));
                    if (containsGithubDependency(fileContent)) {
                        getLog().error("GitHub dependency detected in yarn file: " + fullPath);
                        throw new MojoExecutionException("GitHub dependency detected.");

                    }
                } catch (IOException e) {
                    getLog().error("File" + fullPath + "not found.", e);
                }
            }
        }
    }

    /**
     * Returns the paths of the yarn files in the base directory.
     *
     * @param baseDir the directory to scan.
     * @return a {@code path} to all the yarn files in the given base directory.
     */
    private String[] getYarnFiles(String baseDir) {
        DirectoryScanner scanner = new DirectoryScanner();
        String[] includes = {"**/*.lock"};

        scanner.setIncludes(includes);
        scanner.setBasedir(baseDir);
        scanner.scan();

        return scanner.getIncludedFiles();
    }

    /**
     * Checks if the given string has a link to GitHub.
     * The presence of a link to GitHub in a yarn file is considered a vulnerable dependency.
     *
     * @param fileContent a string with the contents of the file to scan.
     * @return {@code true} is a link to GitHub exists and {@code false} if there isn't such a dependency.
     */
    private boolean containsGithubDependency(String fileContent) {
        String[] urls = fileContent.split("(?=(https.*))");
        for (String url : urls) {
            if (url.toLowerCase()
                    .contains("/github")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Custom exception that informs users there is a vulnerable (GitHub) dependency present in their yarn files.
     */
    public class VulnerableDependencyException extends Exception {

        public VulnerableDependencyException(String message) {
            super(message);
        }
    }

}


