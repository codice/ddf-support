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
package org.codice.git.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.codice.git.ConfigureLogging;
import org.codice.git.GitIntegrationTest;
import org.codice.git.MockRepoHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GitHooksTest {
    // Configure the logging for this test
    static {
        ConfigureLogging cfg = new ConfigureLogging();
    }

    private File rootdir = null;

    private File basedir = null;

    private File metadir = null;

    private File hookdir = null;

    private MockRepoHandler repHandler = null;

    @Before
    public void setUp() throws Exception {
        this.rootdir = File.createTempFile("githook_test_", "_tmp");
        this.basedir = new File(rootdir, "gitsetup");
        this.metadir = new File(rootdir, ".git");
        this.hookdir = new File(metadir, "hooks");
        if (!rootdir.delete() || !rootdir.mkdir()) {
            throw new IOException("Cannot create " + rootdir);
        }
        if (!basedir.mkdir()) {
            throw new IOException("Cannot create " + basedir);
        }
        if (!metadir.mkdir()) {
            throw new IOException("Cannot create " + metadir);
        }
        if (!hookdir.mkdir()) {
            throw new IOException("Cannot create " + hookdir);
        }
        this.repHandler = new MockRepoHandler(basedir);
        repHandler.setMetadir(metadir);
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteQuietly(rootdir);
    }

    @Test
    public void testClean() throws Exception {
        // create hooks
        for (final String name : GitHooks.HOOKS) {
            GitIntegrationTest.writeToFile(new File(hookdir, name), name);
        }
        // create classpath.txt, blacklist-words.txt, blacklist-words.mvn files
        GitIntegrationTest.writeToFile(new File(basedir, "classpath.txt"), "classpath.txt");
        GitIntegrationTest.writeToFile(new File(basedir, "commit-prefix.txt"), "commit-prefix.txt");
        GitIntegrationTest.writeToFile(new File(basedir, "whitelist-words.txt"),
                "whitelist-words.txt");
        GitIntegrationTest.writeToFile(new File(basedir, "blacklist-words.txt"),
                "blacklist-words.txt");
        GitIntegrationTest.writeToFile(new File(basedir, "blacklist-words.mvn"),
                "blacklist-words.mvn");

        GitHooks.clean(repHandler);

        assertEquals(0, hookdir.listFiles().length);
        final Set<String> files = new HashSet<String>();

        for (final File f : basedir.listFiles()) {
            files.add(f.getName());
        }
        assertEquals(4, files.size());
        assertTrue(files.contains("commit-prefix.txt"));
        assertTrue(files.contains("whitelist-words.txt"));
        assertTrue(files.contains("blacklist-words.txt"));
        assertTrue(files.contains("blacklist-words.mvn"));
    }

    @Test
    public void testInstall() throws Exception {
        GitHooks.install(repHandler, "");

        final Set<String> files = new HashSet<String>();

        for (final File f : hookdir.listFiles()) {
            files.add(f.getName());
        }
        assertEquals(GitHooks.HOOKS.length, files.size());
        for (final String name : GitHooks.HOOKS) {
            assertTrue(files.contains(name));
        }
    }
}
