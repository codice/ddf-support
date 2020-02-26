/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.plugin.bundle;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

@Mojo(
    name = "validate-import-versions",
    defaultPhase = LifecyclePhase.INITIALIZE,
    threadSafe = true)
public class BundleImportValidationPlugin extends AbstractMojo {

  private static final String OUTPUT_FILENAME = "bundleImportValidationResults.txt";

  private static final String FAILURE_SUMMARY_FILENAME = "importVersionValidationFailures.txt";

  private static final String CONFIG_OPTION_TO_VALIDATE = "Import-Package";

  private static final Predicate<String> IS_VALID_IMPORT =
      importString -> importString.contains("version=");

  private static final Predicate<String> IS_NOT_IGNORED_IMPORT = value -> !value.startsWith("!");

  private static final String NO_PACKAGE_IMPORTS_FOUND =
      "No package imports specified; this defaults to a * import";

  private static final Pattern IMPORT_VALUE_PATTERN = Pattern.compile("[^,\"]*\"[^\"]*\"|[^,]+");

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${}")
  private List<String> excludedModules;

  @Parameter(defaultValue = "${}")
  private List<String> warnOnlyModules;

  @Parameter(defaultValue = "true")
  private boolean writeModuleResultsToTarget;

  @Parameter private String failureSummaryDirectory;

  @Override
  public void execute() throws MojoFailureException {
    if (excludedModules.contains(project.getArtifactId())) {
      getLog().info("Skipping import validation for excluded module " + project.getName());
      return;
    }
    Plugin mavenBundlePlugin = getMavenBundlePlugin();
    if (mavenBundlePlugin == null) {
      getLog()
          .info(
              "Skipping import validation; bundle plugin not configured for " + project.getName());
      return;
    }

    List<ValidationResult> validationResults = validateConfiguredImports(mavenBundlePlugin);
    if (writeModuleResultsToTarget) {
      writeTargetOutput(validationResults);
    }

    List<ValidationResult> failures =
        validationResults.stream().filter(result -> !result.isValid).collect(Collectors.toList());

    if (failures.isEmpty() && !validationResults.isEmpty()) {
      getLog().info("All import versions appear valid in " + project.getName());
      return;
    }

    getLog().warn("Bundle version validation failed for " + project.getName());
    if (validationResults.isEmpty()) {
      getLog().warn(NO_PACKAGE_IMPORTS_FOUND);
    }

    if (StringUtils.isNotBlank(failureSummaryDirectory)) {
      writeFailureSummary(failures);
    }

    if (!warnOnlyModules.contains(project.getArtifactId()) && !warnOnlyModules.contains("*")) {
      throw new MojoFailureException("Invalid bundle version configured in " + project.getName());
    }
  }

  List<ValidationResult> validateConfiguredImports(Plugin mavenBundlePlugin) {
    getLog().info("Validating import versions in " + project.getName());

    Xpp3Dom bundlePluginConfig = (Xpp3Dom) mavenBundlePlugin.getConfiguration();
    return Arrays.stream(bundlePluginConfig.getChildren())
        .map(this::validateConfig)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  void setProject(MavenProject project) {
    this.project = project;
  }

  void setExcludedModules(List<String> excludedModules) {
    this.excludedModules = excludedModules;
  }

  void setWarnOnlyModules(List<String> warnOnlyModules) {
    this.warnOnlyModules = warnOnlyModules;
  }

  private Plugin getMavenBundlePlugin() {
    return project.getBuildPlugins().stream()
        .filter(plugin -> "maven-bundle-plugin".equals(plugin.getArtifactId()))
        .findFirst()
        .orElse(null);
  }

  private List<ValidationResult> validateConfig(Xpp3Dom current) {
    if (current.getChildCount() > 0) {
      return Arrays.stream(current.getChildren())
          .filter(child -> CONFIG_OPTION_TO_VALIDATE.equals(child.getName()))
          .map(this::validateConfig)
          .flatMap(Collection::stream)
          .collect(Collectors.toList());
    }

    if (CONFIG_OPTION_TO_VALIDATE.equals(current.getName())) {
      List<ValidationResult> results = validateImports(current.getValue());
      if (results.isEmpty()) {
        return Collections.singletonList(new ValidationResult("No Packages Imported", true));
      }
      return results;
    } else {
      return Collections.emptyList();
    }
  }

  private List<ValidationResult> validateImports(String importString) {
    if (StringUtils.isBlank(importString)) {
      return Collections.emptyList();
    }
    return extractImports(importString).stream()
        .map(String::trim)
        .filter(IS_NOT_IGNORED_IMPORT)
        .map(this::validate)
        .collect(Collectors.toList());
  }

  private List<String> extractImports(String line) {
    List<String> imports = new ArrayList<>();
    Matcher m = IMPORT_VALUE_PATTERN.matcher(line);
    while (m.find()) {
      imports.add(m.group());
    }
    return imports;
  }

  private ValidationResult validate(String line) {
    getLog().info("Testing import: " + line);

    ValidationResult validationResult = new ValidationResult(line, IS_VALID_IMPORT.test(line));

    if (!validationResult.isValid()) {
      if (warnOnlyModules.contains(project.getArtifactId()) || warnOnlyModules.contains("*")) {
        getLog().warn("Import with missing version found: " + line);
      } else {
        getLog().error("Import with missing version found: " + line);
      }
    }

    return validationResult;
  }

  private void writeTargetOutput(List<ValidationResult> validationResults) {
    String targetDir = project.getBuild().getDirectory();
    StringBuilder validationResultSummary = buildValidationResultSummary(validationResults);
    writeToFile(targetDir, OUTPUT_FILENAME, validationResultSummary);
  }

  StringBuilder buildValidationResultSummary(List<ValidationResult> validationResults) {
    StringBuilder validationResultSummary = new StringBuilder();
    validationResultSummary
        .append("Import validation results for ")
        .append(project.getName())
        .append("\n\n");

    if (validationResults.isEmpty()) {
      validationResultSummary.append("Invalid  -  ").append(NO_PACKAGE_IMPORTS_FOUND).append("\n");
    } else {
      for (ValidationResult result : validationResults) {
        validationResultSummary.append(result).append("\n");
      }
    }
    return validationResultSummary;
  }

  private void writeFailureSummary(List<ValidationResult> failures) {
    StringBuilder failureSummary = new StringBuilder();
    failureSummary.append("Invalid imports found in ").append(project.getName()).append("\n");

    if (failures.isEmpty()) {
      failureSummary.append(NO_PACKAGE_IMPORTS_FOUND).append("\n\n");
    } else {
      for (ValidationResult failure : failures) {
        failureSummary.append(failure.importString).append("\n");
      }
      failureSummary.append(failures.size()).append(" import(s) with no version.\n\n");
    }

    synchronized (BundleImportValidationPlugin.class) {
      writeToFile(failureSummaryDirectory, FAILURE_SUMMARY_FILENAME, failureSummary);
    }
  }

  private void writeToFile(String path, String fileName, CharSequence output) {
    boolean created = new File(path).mkdirs();
    if (created) {
      getLog().info("Created directory for: " + path);
    }

    File file = new File(path, fileName);
    getLog().info("Writing output to: " + file.getAbsolutePath());

    try (FileWriter fileWriter = new FileWriter(file, true)) {
      fileWriter.append(output);
    } catch (IOException e) {
      getLog().warn("Failed to write output to: " + file.getAbsolutePath(), e);
    }
  }

  static class ValidationResult {
    private final String importString;
    private final boolean isValid;

    ValidationResult(String value, boolean isValid) {
      this.importString = value;
      this.isValid = isValid;
    }

    public String toString() {
      String valid = isValid ? "Valid" : "Invalid";
      return String.format("%-9s-  %s", valid, importString);
    }

    public boolean isValid() {
      return this.isValid;
    }
  }
}
