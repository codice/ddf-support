/**
 * Copyright (c) Codice Foundation
 * <p/>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.git;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class MockRepoHandler extends RepositoryHandler {
    private File metadir;

    private Map<String, Pattern> dirtyWords;

    private String commitFilename;

    private String diffString;

    private String prefix;

    private HashMap<String, String> mockFiles = new HashMap<String, String>();

    private Map<String, String> cfg = new HashMap<String, String>();

    public MockRepoHandler() {
        this(null);
    }

    public MockRepoHandler(File basedir) {
        super(basedir);
    }

    public void setMetadir(File dir) {
        this.metadir = dir;
    }

    public void setMockFile(String filename, String contents) {
        mockFiles.put(filename, contents);
    }

    @Override
    public File getMetadir() {
        return metadir;
    }

    @Override
    public Map<String, Pattern> getDirtyWords() throws IOException {
        return dirtyWords;
    }

    public void setDirtyWords(String words) {
        this.dirtyWords = new HashMap<String, Pattern>();
        if (StringUtils.isNotEmpty(words)) {
            for (final String w: words.split(",")) {
                dirtyWords.put(w, getPatternFor(w));
            }
        }
    }

    @Override
    public String getCommitPrefix() {
        return prefix;
    }

    public void setCommitPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getFileAsString(String filename) throws Exception {
        String fileContents = mockFiles.get(filename);
        if (fileContents == null) {
            throw new IOException("File not found - " + filename);
        }
        return fileContents;
    }

    public void setDiffString(String d) {
        diffString = d;
    }

    @Override
    public String getDiff() throws Exception {
        return diffString;
    }

    @Override
    public String getConfigString(String section, String subsection, String key) {
        return cfg.get(section + ':' + subsection + ':' + key);
    }

    @Override
    public void setConfigString(String section, String subsection, String key, String value) {
        cfg.put(section + ':' + subsection + ':' + key, value);
    }
}