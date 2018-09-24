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
package org.codice.maven.artifactsize;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.util.StringUtils;

/**
 * When the ArtifactSizeEnforcerRule is active, it will look up the packaging type and enforce a
 * maximum artifact size. If the packaging type is unknown to the rule, enforcement will be skipped.
 * The only currently supported packaging types are bundle and jar.
 *
 * <p>The following arguments can be specified to the ArtifactSizeEnforcerRule:
 *
 * <ul>
 * <li>{@code maxArtifactSize} - If the specified artifact is larger than this value then an
 * exception will be thrown. The unit of this argument should end with {@code _B} (Bytes),
 * {@code _KB} (Kilobytes), or {@code _MB} (Megabytes). By default, value is set to 1_MB.</li>
 * <li>
 * {@code artifactLocation} - A path to the file to be checked. If not provided, will search for a
 * a file path based on project version, packaging, and artifactId.</li>
 * <li>{@code skip} - If set, this rule will not run.
 * </li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * {@code
 * <plugin>
 *    <groupId>org.apache.maven.plugins</groupId>
 *    <artifactId>maven-enforcer-plugin</artifactId>
 *    <version>1.4.1</version>
 *    <dependencies>
 *        <dependency>
 *            <groupId>ddf.support</groupId>
 *            <artifactId>artifact-size-enforcer</artifactId>
 *        </dependency>
 *    </dependencies>
 *    <executions>
 *        <execution>
 *            <id>check-artifact-size</id>
 *            <phase>verify</phase>
 *            <goals>
 *                <goal>enforce</goal>
 *            </goals>
 *            <configuration>
 *                <rules>
 *                    <ArtifactSizeEnforcerRule implementation="org.codice.maven.artifactsize.ArtifactSizeEnforcerRule" />
 *                </rules>
 *            </configuration>
 *        </execution>
 *    </executions>
 * </plugin>}
 * </pre>
 */
public class ArtifactSizeEnforcerRule implements EnforcerRule {

  @VisibleForTesting
  static final String PROJECT_PACKAGING_PROP = "${project.packaging}";

  @VisibleForTesting
  static final String PROJECT_ARTIFACT_ID_PROP = "${project.artifactId}";

  @VisibleForTesting
  static final String PROJECT_VERSION_PROP = "${project.version}";

  @VisibleForTesting
  static final String PROJECT_BUILD_DIR_PROP = "${project.build.directory}";

  @VisibleForTesting
  static final String JAR = "jar";

  private static final String BUNDLE = "bundle";

  @VisibleForTesting
  static final List<String> SUPPORTED_PACKAGE_TYPES = ImmutableList.of(JAR, BUNDLE);

  private static final String DEFAULT_MAX_ARTIFACT_SIZE = "1_MB";
  private static final String BYTES = "_B";
  private static final String MEGA_BYTES = "_MB";
  private static final String KILO_BYTES = "_KB";


  private static final String MAX_FILE_SIZE_EXCEEDED_MSG =
      "The specified artifact is larger than the set maximum artifact size. %n%n\tArtifact: %s%n\tArtifact Size: %d Bytes%n\tMax Artifact Size: %d Bytes%n."
          + " Either reduce the artifact size (highly recommended) or set the maxArtifactSize property in the enforcer-plugin to a higher value.";

  private static final String DEFAULT_ARTIFACT_INFO_MSG =
      "Using the following parameters to find artifact: %n\tArtifactId: %s%n\tVersion: %s%n\tPackaging: %s%n\tArtifact Directory: %s";

  private static final String UNKNOWN_ARTIFACT_SIZE_UNIT_MSG =
      String.format("Unknown artifact size unit. The artifactSize property must end with one of: "
              + "%n\t%s: Bytes%n\t%s: KiloBytes%n\t%s: MegaBytes",
          BYTES,
          KILO_BYTES,
          MEGA_BYTES);

  // Rule arguments. These properties are set through reflection when running as a rule via the
  // enforcer-plugin, not through setters.
  private String maxArtifactSize;
  private String artifactLocation;
  private boolean skip;

  @Override
  public void execute(EnforcerRuleHelper helper) throws EnforcerRuleException {
    if (skip) {
      return;
    }

    String packaging = getPackaging(helper);
    if (!SUPPORTED_PACKAGE_TYPES.contains(packaging)) {
      helper
          .getLog()
          .info(
              String.format(
                  "Unsupported package type %s. Skipping artifact size enforcement.", packaging));
      return;
    }

    String artifactPath = getArtifactPath(helper);
    long maxArtifactSizeBytes = maxArtifactSizeToBytes(helper);
    long artifactSize = getArtifact(artifactPath).length();

    if (artifactSize > maxArtifactSizeBytes) {
      throw new EnforcerRuleException(
          String.format(
              MAX_FILE_SIZE_EXCEEDED_MSG, artifactPath, artifactSize, maxArtifactSizeBytes));
    }
  }

  private String getArtifactPath(EnforcerRuleHelper helper) throws EnforcerRuleException {
    String convertedArtifactLocation = artifactLocation;

    if (StringUtils.isNotEmpty(convertedArtifactLocation)) {
      helper
          .getLog()
          .info(String.format("Using specified artifactLocation %s", convertedArtifactLocation));
    } else {
      try {
        helper
            .getLog()
            .info(
                "artifactLocation property not specified. Looking up artifact using maven properties.");

        String artifactId = (String) helper.evaluate(PROJECT_ARTIFACT_ID_PROP);
        String version = (String) helper.evaluate(PROJECT_VERSION_PROP);
        String packaging = getPackaging(helper);
        String buildDir = (String) helper.evaluate(PROJECT_BUILD_DIR_PROP);
        helper
            .getLog()
            .debug(
                String.format(DEFAULT_ARTIFACT_INFO_MSG, artifactId, version, packaging, buildDir));

        convertedArtifactLocation =
            String.format("%s%s%s-%s.%s",
                buildDir,
                File.separator,
                artifactId,
                version,
                packaging);
        helper
            .getLog()
            .debug(
                String.format("Complete generated artifact path: %s", convertedArtifactLocation));
      } catch (ExpressionEvaluationException e) {
        throw new EnforcerRuleException(e.getMessage());
      }
    }

    return convertedArtifactLocation;
  }

  private File getArtifact(String artifactPath) throws EnforcerRuleException {
    File artifact = new File(artifactPath);
    if (!artifact.exists()) {
      throw new EnforcerRuleException(
          String.format("No file found for artifact location: %s", artifactPath));
    }

    return artifact;
  }

  String getPackaging(EnforcerRuleHelper helper) throws EnforcerRuleException {
    String packaging;
    try {
      packaging = (String) helper.evaluate(PROJECT_PACKAGING_PROP);
    } catch (ExpressionEvaluationException e) {
      throw new EnforcerRuleException(e.getMessage());
    }

    // Bundle and jar packaging both produce jar outputs
    switch (packaging) {
      case JAR:
      case BUNDLE:
        return JAR;
      default:
        return packaging;
    }
  }

  long maxArtifactSizeToBytes(EnforcerRuleHelper helper) throws EnforcerRuleException {
    String convertArtifactSize = maxArtifactSize;
    if (StringUtils.isEmpty(convertArtifactSize)) {
      helper
          .getLog()
          .info(
              String.format(
                  "maxArtifactSize property not specified. Using default maxArtifactSize size: %s",
                  DEFAULT_MAX_ARTIFACT_SIZE));
      convertArtifactSize = DEFAULT_MAX_ARTIFACT_SIZE;
    }

    try {
      return convertInputSize(convertArtifactSize);
    } catch (NumberFormatException e) {
      throw new EnforcerRuleException(
          String.format("Unparsable input size of %s", convertArtifactSize));
    }
  }

  private long convertInputSize(String convertArtifactSize) throws EnforcerRuleException {
    if (convertArtifactSize.endsWith(BYTES)) {
      return Long.parseLong(convertArtifactSize.split(BYTES)[0]);
    } else if (convertArtifactSize.endsWith(KILO_BYTES)) {
      return (long) (Double.parseDouble(convertArtifactSize.split(KILO_BYTES)[0]) * 1024);
    } else if (convertArtifactSize.endsWith(MEGA_BYTES)) {
      return (long) (Double.parseDouble(convertArtifactSize.split(MEGA_BYTES)[0]) * 1024 * 1024);
    } else {
      throw new EnforcerRuleException(UNKNOWN_ARTIFACT_SIZE_UNIT_MSG);
    }
  }

  @Override
  public boolean isCacheable() {
    return false;
  }

  @Override
  public boolean isResultValid(EnforcerRule enforcerRule) {
    return false;
  }

  @Override
  public String getCacheId() {
    return null;
  }

  public ArtifactSizeEnforcerRule setMaxArtifactSize(String maxArtifactSize) {
    this.maxArtifactSize = maxArtifactSize;
    return this;
  }

  public ArtifactSizeEnforcerRule setArtifactLocation(String artifactLocation) {
    this.artifactLocation = artifactLocation;
    return this;
  }

  public ArtifactSizeEnforcerRule setSkip(boolean skip) {
    this.skip = skip;
    return this;
  }
}
