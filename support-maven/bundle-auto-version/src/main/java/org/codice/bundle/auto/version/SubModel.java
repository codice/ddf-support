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
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public class SubModel {

  private final String name;
  private final Path path;
  private final Log log;

  SubModel(String parentPath, String submoduleName, Log log) {
    this.path = Paths.get(parentPath, submoduleName);
    this.name = submoduleName;
    this.log = log;
  }

  Model getModel() {
    try {
      readProjectModel();
    } catch (IOException | XmlPullParserException e) {
      log.error("Unable to read model for " + name, e);
    }

    return null;
  }

  private Model readProjectModel() throws XmlPullParserException, IOException {
    FileReader pomFileReader = new FileReader(new File(path.toFile(), "pom.xml"));
    return new MavenXpp3Reader().read(pomFileReader);
  }
}
