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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codice.plugin.bundle.BundleImportValidationPlugin.ValidationResult;
import org.junit.Before;
import org.junit.Test;

public class BundleImportValidationPluginTest {

  private static final String MAVEN_BUNDLE_PLUGIN = "maven-bundle-plugin";

  private static final String ARTIFACT_ID = "artifact";

  private static final String VALID_CONFIG = "validConfig.txt";

  private static final String INVALID_CONFIG = "invalidConfig.txt";

  private static final String NO_IMPORT_CONFIG = "noImportConfig.txt";

  private MavenProject project;

  private BundleImportValidationPlugin validationPlugin;

  @Before
  public void setUp() {
    project = mock(MavenProject.class);
    validationPlugin = new BundleImportValidationPlugin();
    validationPlugin.setProject(project);
    validationPlugin.setExcludedModules(Collections.emptyList());
    validationPlugin.setWarnOnlyModules(Collections.emptyList());
    when(project.getName()).thenReturn("project");
    when(project.getArtifactId()).thenReturn(ARTIFACT_ID);
  }

  @Test
  public void testNoBuildPluginSucceeds() throws Exception {
    when(project.getBuildPlugins()).thenReturn(Collections.emptyList());
    validationPlugin.execute();
  }

  @Test(expected = MojoFailureException.class)
  public void testInvalidConfigFails() throws Exception {
    InputStream configStream = getClass().getClassLoader().getResourceAsStream(INVALID_CONFIG);

    Plugin buildPlugin = mock(Plugin.class);
    when(buildPlugin.getArtifactId()).thenReturn(MAVEN_BUNDLE_PLUGIN);
    when(buildPlugin.getConfiguration())
        .thenReturn(Xpp3DomBuilder.build(configStream, UTF_8.name()));
    when(project.getBuildPlugins()).thenReturn(Collections.singletonList(buildPlugin));
    validationPlugin.execute();
  }

  @Test(expected = MojoFailureException.class)
  public void testConfigWithoutImportFails() throws Exception {
    InputStream configStream = getClass().getClassLoader().getResourceAsStream(NO_IMPORT_CONFIG);

    Plugin buildPlugin = mock(Plugin.class);
    when(buildPlugin.getArtifactId()).thenReturn(MAVEN_BUNDLE_PLUGIN);
    when(buildPlugin.getConfiguration())
        .thenReturn(Xpp3DomBuilder.build(configStream, UTF_8.name()));
    when(project.getBuildPlugins()).thenReturn(Collections.singletonList(buildPlugin));
    validationPlugin.execute();
  }

  @Test
  public void testValidConfigSucceeds() throws Exception {
    InputStream configStream = getClass().getClassLoader().getResourceAsStream(VALID_CONFIG);

    Plugin buildPlugin = mock(Plugin.class);
    when(buildPlugin.getArtifactId()).thenReturn(MAVEN_BUNDLE_PLUGIN);
    when(buildPlugin.getConfiguration())
        .thenReturn(Xpp3DomBuilder.build(configStream, UTF_8.name()));
    when(project.getBuildPlugins()).thenReturn(Collections.singletonList(buildPlugin));
    validationPlugin.execute();

    List<ValidationResult> results = validationPlugin.validateConfiguredImports(buildPlugin);
    assertThat(results.size(), is(4));
  }

  @Test
  public void testInvalidConfigSucceedsIfWarnOnly() throws Exception {
    validationPlugin.setWarnOnlyModules(Collections.singletonList(ARTIFACT_ID));
    InputStream configStream = getClass().getClassLoader().getResourceAsStream(INVALID_CONFIG);

    Plugin buildPlugin = mock(Plugin.class);
    when(buildPlugin.getArtifactId()).thenReturn(MAVEN_BUNDLE_PLUGIN);
    when(buildPlugin.getConfiguration())
        .thenReturn(Xpp3DomBuilder.build(configStream, UTF_8.name()));
    when(project.getBuildPlugins()).thenReturn(Collections.singletonList(buildPlugin));
    validationPlugin.execute();

    List<ValidationResult> results = validationPlugin.validateConfiguredImports(buildPlugin);
    List<ValidationResult> failures =
        results.stream().filter(result -> !result.isValid()).collect(Collectors.toList());
    assertThat(results.size(), is(3));
    assertThat(failures.size(), is(2));
  }

  @Test
  public void testInvalidConfigSucceedsIfExcluded() throws Exception {
    validationPlugin.setExcludedModules(Collections.singletonList(ARTIFACT_ID));
    InputStream configStream = getClass().getClassLoader().getResourceAsStream(INVALID_CONFIG);

    Plugin buildPlugin = mock(Plugin.class);
    when(buildPlugin.getArtifactId()).thenReturn(MAVEN_BUNDLE_PLUGIN);
    when(buildPlugin.getConfiguration())
        .thenReturn(Xpp3DomBuilder.build(configStream, UTF_8.name()));
    when(project.getBuildPlugins()).thenReturn(Collections.singletonList(buildPlugin));
    validationPlugin.execute();

    verify(project, times(0)).getBuildPlugins();
  }

  @Test
  public void testBuildValidationSummary() throws Exception {
    InputStream configStream = getClass().getClassLoader().getResourceAsStream(INVALID_CONFIG);

    Plugin buildPlugin = mock(Plugin.class);
    when(buildPlugin.getArtifactId()).thenReturn(MAVEN_BUNDLE_PLUGIN);
    when(buildPlugin.getConfiguration())
        .thenReturn(Xpp3DomBuilder.build(configStream, UTF_8.name()));
    when(project.getBuildPlugins()).thenReturn(Collections.singletonList(buildPlugin));

    List<ValidationResult> results = validationPlugin.validateConfiguredImports(buildPlugin);
    StringBuilder summary = validationPlugin.buildValidationResultSummary(results);
    assertThat(summary.toString().contains("Valid"), is(true));
    assertThat(summary.toString().contains("Invalid"), is(true));
  }

  @Test
  public void testBuildValidationSummaryNoImports() throws Exception {
    InputStream configStream = getClass().getClassLoader().getResourceAsStream(NO_IMPORT_CONFIG);

    Plugin buildPlugin = mock(Plugin.class);
    when(buildPlugin.getArtifactId()).thenReturn(MAVEN_BUNDLE_PLUGIN);
    when(buildPlugin.getConfiguration())
        .thenReturn(Xpp3DomBuilder.build(configStream, UTF_8.name()));
    when(project.getBuildPlugins()).thenReturn(Collections.singletonList(buildPlugin));

    List<ValidationResult> results = validationPlugin.validateConfiguredImports(buildPlugin);
    StringBuilder summary = validationPlugin.buildValidationResultSummary(results);
    assertThat(summary.toString().contains("No package imports specified"), is(true));
    assertThat(summary.toString().contains("Valid"), is(false));
  }
}
