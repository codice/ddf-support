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

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.codice.git.hook.ChangeOnlyDiffFormatter;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class GitHandler extends RepositoryHandler {
    private static final Logger LOGGER = Logger.getLogger(GitHandler.class.getName());

    private final Repository repo;

    public GitHandler(File basedir) throws IOException {
        super(basedir);
        this.repo = new FileRepositoryBuilder().findGitDir()
                .readEnvironment()
                .findGitDir()
                .build();
    }

    public GitHandler(File cwd, File basedir) throws IOException {
        super(basedir);
        this.repo = new FileRepositoryBuilder().findGitDir(cwd)
                .readEnvironment()
                .findGitDir()
                .build();
    }

    @Override
    public File getMetadir() {
        return repo.getDirectory();
    }

    @Override
    public String getFileAsString(String filename) throws Exception {
        File fileToRead = new File(filename);

        if (!fileToRead.exists()) {
            LOGGER.finest(
                    "Absolute file " + filename + " does not exist - checking relative to git dir");
            fileToRead = repo.getWorkTree();
            fileToRead = new File(fileToRead, filename);
        }
        LOGGER.log(Level.FINER, "Reading commit message from {0}", fileToRead.getPath());
        return Files.toString(fileToRead, Charsets.UTF_8);
    }

    @Override
    public String getDiff() throws Exception {
        final ObjectId head = repo.resolve(Constants.HEAD + "^{tree}");

        if (head == null) {
            LOGGER.warning("Unable to resolve the HEAD of this git tree");
            throw new NoHeadException(JGitText.get().cannotReadTree);
        }
        final CanonicalTreeParser p = new CanonicalTreeParser();
        final ObjectReader reader = repo.newObjectReader();

        try {
            p.reset(reader, head);
        } finally {
            reader.release();
        }
        final AbstractTreeIterator oldTree = p;
        final AbstractTreeIterator newTree = new DirCacheIterator(repo.readDirCache());
        final OutputStream out = new ByteArrayOutputStream();
        final ChangeOnlyDiffFormatter diffFmt =
                new ChangeOnlyDiffFormatter(new BufferedOutputStream(out));

        diffFmt.setRepository(repo);
        diffFmt.setPathFilter(TreeFilter.ALL);
        diffFmt.setProgressMonitor(NullProgressMonitor.INSTANCE);

        LOGGER.finer("Scanning the git tree for diffs");
        final List<DiffEntry> result = diffFmt.scan(oldTree, newTree);

        diffFmt.format(result);
        diffFmt.flush();
        diffFmt.release();

        return out.toString();
    }

    @Override
    public String getConfigString(String section, String subsection, String key) {
        final StoredConfig config = repo.getConfig();
        final String value = config.getString(section, subsection, key);

        LOGGER.log(Level.FINE,
                "Value for [{0}, {1}, {2}]: {3}\n",
                new Object[] {section, subsection, key, value});
        return value;
    }

    public void setConfigString(String section, String subsection, String key, String value)
            throws IOException {
        final StoredConfig config = repo.getConfig();

        config.setString(section, subsection, key, value);
        config.save();
        LOGGER.log(Level.FINE,
                "Value for [{0}, {1}, {2}] set to: {3}\n",
                new Object[] {section, subsection, key, value});
    }
}