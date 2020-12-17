/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General private License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General private License for more details. A copy of the GNU Lesser General private
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.bundle.auto.version;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

@Mojo(
    name = "bundle-auto-version",
    defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
    threadSafe = true)
public class BundleAutoVersionPlugin extends AbstractMojo {

  private static final String COPYRIGHT_NOTICE =
      "<!--\n"
          + "/**\n"
          + " * Copyright (c) Codice Foundation\n"
          + " *\n"
          + " * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either\n"
          + " * version 3 of the License, or any later version.\n"
          + " *\n"
          + " * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.\n"
          + " * See the GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public License is distributed along with this program and can be found at\n"
          + " * <http://www.gnu.org/licenses/lgpl.html>.\n"
          + " *\n"
          + " **/\n"
          + " -->";

  private static final Pattern IMPORT_VALUE_PATTERN =
      Pattern.compile("(?:[^,\\\"]+|(?:\\\"[^\\\"]*\\\"))+|[^,]+");

  private static final String IMPORT_PACKAGE_PROP = "Import-Package";
  private static final String MAVEN_BUNDLE_PLUGIN_ARTIFACT_ID = "maven-bundle-plugin";
  private static final String MAVEN_CONFIG_INSTRUCTIONS = "instructions";

  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  private MavenProject mavenProject;

  @Parameter(property = "excludeModules", defaultValue = "${}")
  private List<String> excludeModules;

  @Override
  public void execute() {
    updateAndSaveModulePom(mavenProject.getModel(), mavenProject.getModel().getProjectDirectory());
  }

  private void updateAndSaveModulePom(Model model, File basePath) {
    if (excludeModules.contains(model.getArtifactId())) {
      getLog().info("Skipping bundle version update for excluded module " + model.getArtifactId());
      return;
    }

    // Does this model have the maven-bundle-plugin
    Plugin mavenBundlePlugin = getMavenBundlePlugin(model);
    Consumer<Model> updateSubFunction = subModel -> updateAndSaveModulePom(subModel, basePath);

    if (null == mavenBundlePlugin) {
      getLog()
          .warn(
              "No "
                  + MAVEN_BUNDLE_PLUGIN_ARTIFACT_ID
                  + " configuration found for "
                  + model.getName());
      getSubModuleModels(model, basePath).stream().forEach(updateSubFunction);
      return;
    }

    List<String> manifestImportsList = importStringToList(getManifestPackageImports(model));

    if (null == manifestImportsList || manifestImportsList.isEmpty()) {
      getLog()
          .warn("No " + IMPORT_PACKAGE_PROP + " directive was found in 'MANIFEST.MF', skipping");

      return;
    }

    try (FileReader pomFileReader = new FileReader(model.getPomFile())) {
      model = new MavenXpp3Reader().read(pomFileReader);
    } catch (IOException | XmlPullParserException e) {
      getLog().error("Error parsing model for Maven project", e);
    }

    // The model loaded from pom is the one we're manipulating that's why we're re-getting the
    // plugin configuration...
    // to get a reference to the correct one
    Xpp3Dom configInstructions = getPluginConfiguration(getMavenBundlePlugin(model));

    if (configInstructions == null)
      throw new RuntimeException("Unable to locate configuration for " + mavenBundlePlugin);

    // Is there an `Import-Package` directive? If not, skip
    if (null == configInstructions.getChild(IMPORT_PACKAGE_PROP)) {
      getLog()
          .info(
              "No "
                  + IMPORT_PACKAGE_PROP
                  + " found in "
                  + MAVEN_BUNDLE_PLUGIN_ARTIFACT_ID
                  + " configuration, skipping");
      return;
    }

    List<String> pomImportsList =
        importStringToList(configInstructions.getChild(IMPORT_PACKAGE_PROP).getValue());

    if (pomImportsList.equals(manifestImportsList)) {
      getLog().info("Package imports between pom.xml and MANIFEST.MF match, skipping");
      return;
    }

    String manifestImportsString = manifestImportsList.stream().collect(Collectors.joining(",\n"));
    configInstructions.getChild(IMPORT_PACKAGE_PROP).setValue(manifestImportsString);

    saveProjectModel(model, mavenProject.getModel().getPomFile());

    getSubModuleModels(model, basePath).stream().forEach(updateSubFunction);
  }

  private List<Model> getSubModuleModels(Model parentModel, File parentDirectory) {
    return parentModel.getModules().stream()
        .map(submodule -> new SubModel(parentDirectory.getAbsolutePath(), submodule, getLog()))
        .map(SubModel::getModel)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private List<String> importStringToList(String packageImport) {
    return StreamSupport.stream(
            new MatchIterator(IMPORT_VALUE_PATTERN.matcher(packageImport)), false)
        .sorted()
        .collect(Collectors.toList());
  }

  private void saveProjectModel(Model model, File pomFile) {
    try (FileWriter pomFileWriter = new FileWriter(pomFile)) {
      new MavenXpp3Writer().write(pomFileWriter, model);
      addCopyrightNotice(pomFile);
    } catch (IOException e) {
      getLog().error("Error saving project model to pom file", e);
    }
  }

  private void addCopyrightNotice(File pomFile) throws IOException {
    List<String> pomFileLines = Files.lines(pomFile.toPath()).collect(Collectors.toList());

    pomFileLines.set(0, appendCopyrightNotice(pomFileLines.get(0)));

    Files.write(pomFile.toPath(), pomFileLines, Charset.forName("UTF-8"));
  }

  private String appendCopyrightNotice(String line) {
    return line + "\n" + COPYRIGHT_NOTICE;
  }

  private Model readProjectPom(File pomFile) {
    try (FileReader pomFileReader = new FileReader(pomFile)) {
      return new MavenXpp3Reader().read(pomFileReader);
    } catch (IOException | XmlPullParserException e) {
      getLog().error("Error reading project model from pom file", e);
    }

    return null;
  }

  private String getManifestPackageImports(Model model) {
    Path manifestFilePath =
        Paths.get(model.getBuild().getOutputDirectory() + "/META-INF/MANIFEST.MF");

    if (!manifestFilePath.toFile().exists()) return "";

    Manifest manifest = null;

    try (FileInputStream manifestInputStream = new FileInputStream(manifestFilePath.toString())) {
      manifest = new Manifest(manifestInputStream);
    } catch (IOException e) {
      getLog().error("Error reading 'MANIFEST.MF' for the project", e);
    }

    if (manifest == null) throw new RuntimeException("Unable to locate generated 'MANIFEST.MF'");

    return Optional.ofNullable(manifest)
        .map(Manifest::getMainAttributes)
        .map(attributes -> attributes.getValue(IMPORT_PACKAGE_PROP))
        .orElse("");
  }

  private Plugin getMavenBundlePlugin(Model model) {
    return Optional.ofNullable(model.getBuild()).map(Build::getPlugins)
        .orElse(Collections.emptyList()).stream()
        .filter(plugin -> MAVEN_BUNDLE_PLUGIN_ARTIFACT_ID.equals(plugin.getArtifactId()))
        .findFirst()
        .orElse(null);
  }

  private Xpp3Dom getPluginConfiguration(Plugin plugin) {
    Xpp3Dom configuration =
        Optional.ofNullable(plugin.getConfiguration()).map(Xpp3Dom.class::cast).orElse(null);

    return Arrays.stream(configuration.getChildren())
        .filter(entry -> MAVEN_CONFIG_INSTRUCTIONS.equals(entry.getName()))
        .findFirst()
        .orElse(null);
  }
}
