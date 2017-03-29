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

package org.codice.plugin.version;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;

@Mojo(name = "check-package-json")
public class MavenVersionValidationPlugin extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;

    Set<Character> rangeSymbols = new HashSet(Arrays.asList('>', '=', '|', '-', '<', '^', '~'));

    private final static String[] EXCLUDED_DIRECTORIES =
            new String[] {"**/node_modules/**", "**/node/**", "*/target/**", "**\\node_modules\\**",
                    "**\\node\\**", "**\\target\\**"};

    private final static String FILE_NAME = "package.json";

    private final static String MOJO_EXCEPTION_MESSAGE =
            "Failed to validate version due to improper range symbol.";

    public void execute() throws MojoExecutionException, MojoFailureException {
        String jsonContents;
        File jsonFile;
        boolean hasRangeChars = false;

        JsonObject jsonObject;

        String baseDir = null;
        try {
            baseDir = project.getBasedir()
                    .getCanonicalPath();
        } catch (IOException e) {
            getLog().error("Could not find base directory", e);
        }

        if (baseDir != null) {
            String[] fileList = getListOfPackageJsonFiles(baseDir, FILE_NAME);

            for (String filepath : fileList) {
                jsonFile = Paths.get(baseDir, File.separator, filepath)
                        .toFile();

                try {
                    jsonContents = FileUtils.readFileToString(jsonFile, (Charset) null);
                    JsonReader jsonReader = Json.createReader(new StringReader(jsonContents));
                    jsonObject = jsonReader.readObject();

                    hasRangeChars |= hasRangeChars(jsonObject.getJsonObject("devDependencies"),
                            jsonFile.toString());
                    hasRangeChars |= hasRangeChars(jsonObject.getJsonObject("dependencies"),
                            jsonFile.toString());
                } catch (IOException e) {
                    getLog().error("Could not find file", e);
                }
            }
            if (hasRangeChars) {
                throw new MojoFailureException(MOJO_EXCEPTION_MESSAGE);
            }
        }
    }

    /**
     * Uses DirectoryScanner to search for given filename in baseDir given, excluding string
     * array of excluded regular expression paths. Returns list of full file paths (minus baseDir)
     *
     * @param baseDir:  Base directory of scanning (depends on project)
     * @param filename: Name of file to search for (regex)
     * @return list of files
     */
    private String[] getListOfPackageJsonFiles(String baseDir, String filename) {

        DirectoryScanner scanner = new DirectoryScanner();

        scanner.setIncludes(new String[] {"**/*" + filename});
        scanner.setExcludes(EXCLUDED_DIRECTORIES);
        scanner.setBasedir(baseDir);
        scanner.scan();

        return scanner.getIncludedFiles();
    }

    /**
     * Extracts key and value from JsonObject and evaluates if contains bad range symbol
     * key: dependency name
     * value: version number
     *
     * @param jsonObject: Incoming Json structure to parse (key-value pairs)
     * @param filename:   File being parsed (used for name in error message)
     * @return true if there are version range characters in the file
     */
    private boolean hasRangeChars(JsonObject jsonObject, String filename)
            throws MojoFailureException {
        // (ex. key = "babel-core", value = "^6.17.0")
        String key;
        String value;
        String rangeChar;
        String errorMessage;

        boolean hasRangeChar = false;

        if (jsonObject == null) {
            return false;
        }

        for (Map.Entry<String, JsonValue> entry : jsonObject.entrySet()) {

            key = entry.getKey();
            value = entry.getValue()
                    .toString();

            rangeChar = scanTokenForRangeSymbol(value);

            if (rangeChar != null) {
                errorMessage = String.format(
                        "In [%s] | Invalid version range symbol: [%s] in [%s] : %s",
                        filename,
                        rangeChar,
                        key,
                        value);
                getLog().error(errorMessage);
                hasRangeChar = true;
            }
        }
        return hasRangeChar;
    }

    /**
     * Scans token for bad symbols which denotes a range being used.
     * Returns character being used as range symbol.
     *
     * @param token Input string which is checked for range symbol
     * @return Range character or null (if valid range)
     */
    String scanTokenForRangeSymbol(String token) {
        if (token.contains("-beta") || token.contains("#")) {
            return null;
        }

        if (!token.matches(".*\\d+[.]\\d+.*")) {
            return null;
        }

        for (char character: token.toCharArray()) {
            if (rangeSymbols.contains(character)) {
                return String.valueOf(character);
            }
        }

        return null;
    }
}
