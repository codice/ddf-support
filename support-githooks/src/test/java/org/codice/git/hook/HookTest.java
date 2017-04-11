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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.codice.git.ConfigureLogging;
import org.codice.git.MockRepoHandler;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HookTest extends RepositoryTestCase {
    // Configure the logging for this test
    static {
        ConfigureLogging cfg = new ConfigureLogging();
    }

    protected static final String DIRTY_WORD_LIST = "Bill,WHAT,march madness,DOB-11-1-4,REGEX:System\\.ouch\\.print(f|ln)?";

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
        repHandler.setDirtyWords(DIRTY_WORD_LIST);
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteQuietly(rootdir);
    }

    @Test
    public void testContainsDirtyWords() throws Exception {
        final Hook hook = new Hook(repHandler);
        final Set<String> found = new HashSet<String>();

        assertTrue(hook.containsDirtyWords("Bill decided to do whatever he wanted during March Madness.", found));
        assertFoundAndClear(found, "Bill", "march madness");
        assertTrue(hook.containsDirtyWords("Bill and Tom are both born on DOB-11-1-4 ... what!!!!.", found));
        assertFoundAndClear(found, "Bill", "DOB-11-1-4", "WHAT");
        assertTrue(hook.containsDirtyWords("{ int i = 3; System.ouch.printf(\"there are %d items%n\", i); }", found));
        assertFoundAndClear(found, "REGEX:System\\.ouch\\.print(f|ln)?");
    }

    @Test
    public void testHasDirtyWords() throws Exception {
        final Hook hook = new Hook(repHandler);

        assertTrue(hook.hasDirtyWords());
    }

    @Test
    public void testHasDirtyWordsEmpty() throws Exception {
        repHandler.setDirtyWords("");
        final Hook hook = new Hook(repHandler);

        assertFalse(hook.hasDirtyWords());
    }

    private static void assertFoundAndClear(Set<String> found, String... expected) {
        assertEquals(expected.length, found.size());
        for (final String e: expected) {
            assertTrue(found.contains(e));
        }
        found.clear();
    }
}
