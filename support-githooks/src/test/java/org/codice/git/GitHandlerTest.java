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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.codice.git.hook.GitHooks;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class GitHandlerTest extends RepositoryTestCase {
    public static final String TEST_FILE_RELPATH =
            "target" + File.separator + "gittest" + File.separator + "TEST_FILE";

    public static final String TEST_FILE_DATA = "Line1\nLine2\nLine3\n";

    private static File hooksDir;

    private static Git hooksGit;

    // Configure the logging for this test
    static {
        ConfigureLogging cfg = new ConfigureLogging();
    }

    Logger LOGGER = LoggerFactory.getLogger(GitIntegrationTest.class);

    private File sourceFile;

    private Git source;

    private GitHandler gh;

    // uncomment to have base class not delete the temporary git repo created; useful when debugging
    // a single test
    //@Override
    //@After
    //public void tearDown() throws Exception {}

    private static Set<String> readDefaultBlacklist() {
        InputStream is = null;
        final Set<String> dflt = new HashSet<String>();

        // now add the default blacklist-words.txt stored in the associated jar
        try {
            is = GitHandler.class.getResourceAsStream("/blacklist-words.txt");
            if (is != null) {
                for (final String l: IOUtils.readLines(is, Charsets.UTF_8)) {
                    if (!l.isEmpty() && !l.startsWith("#")) {
                        dflt.add(l);
                    }
                }
            }
        } catch (IOException e) { // ignore and continue without defaults
        } finally {
            IOUtils.closeQuietly(is);
        }
        return dflt;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        // set fake user home directory
        System.setProperty("user.home", getTemporaryDirectory().getAbsolutePath());
        // create gitsetup dir and initialize it with an empty blacklist
        final File gsdir = new File(db.getWorkTree(), "gitsetup");

        gsdir.mkdirs();
        final MockRepoHandler repHandler = new MockRepoHandler(gsdir);

        repHandler.setMetadir(db.getDirectory());
        GitIntegrationTest.writeToFile(repHandler.getBlacklistFile(), "");
        // flush out the sample git repository here
        // add a file with a single line of text - add and commit it
        this.sourceFile = new File(db.getWorkTree(), "SomeFile.txt");
        GitIntegrationTest.writeToFile(sourceFile, "Hello World.\n");
        this.source = new Git(db);
        source.add()
                .addFilepattern("SomeFile.txt")
                .call();
        source.commit()
                .setMessage("Initial commit for source.")
                .call();
        final File f = new File(db.getDirectory(), TEST_FILE_RELPATH);

        Files.createParentDirs(f);
        final File testFile = new File(db.getDirectory(), TEST_FILE_RELPATH);

        GitIntegrationTest.writeToFile(testFile, TEST_FILE_DATA);
        // copyFromJar the hooks over to the newly created git directory
        final File hooksDir = new File(db.getDirectory(), "hooks");

        LOGGER.debug("Installing hook files into {}", hooksDir);
        GitHooks.install(repHandler, "");
        this.gh = new GitHandler(sourceFile, gsdir);
    }

    @Test
    public void testGetDirtyWordsWithEmptyLocalAndNoPersonalBlacklistAndNoWhitelist()
            throws Exception {
        final Set<String> expected = readDefaultBlacklist();

        assertEquals(expected,
                gh.getDirtyWords()
                        .keySet());
    }

    @Test
    public void testGetDirtyWordsWithNoPersonalBlacklistAndNoWhitelist() throws Exception {
        final Set<String> expected = readDefaultBlacklist();

        expected.add("a1");
        expected.add("b2");
        GitIntegrationTest.writeToFile(gh.getBlacklistFile(), "a1\nb2");

        assertEquals(expected,
                gh.getDirtyWords()
                        .keySet());
    }

    @Test
    public void testGetDirtyWordsWithEmptyLocalBlacklistAndNoWhitelist() throws Exception {
        final Set<String> expected = readDefaultBlacklist();

        expected.add("a1");
        expected.add("b2");
        GitIntegrationTest.writeToFile(new File(new File(getTemporaryDirectory(), ".gitsetup"),
                "blacklist-words.txt"), "a1\nb2");

        assertEquals(expected,
                gh.getDirtyWords()
                        .keySet());
    }

    @Test
    public void testGetDirtyWordsWithEmptyLocalAndNoPersonalBlacklistAndWhitelist()
            throws Exception {
        final Set<String> expected = readDefaultBlacklist();

        GitIntegrationTest.writeToFile(gh.getWhitelistFile(), "a1\nb2");
        assertEquals(expected,
                gh.getDirtyWords()
                        .keySet());
    }

    @Test
    public void testGetDirtyWordsWithMixtureOfBlacklistAndWhitelist() throws Exception {
        final Set<String> expected = readDefaultBlacklist();

        expected.add("c3");
        expected.add("e5");
        expected.add("f6");
        GitIntegrationTest.writeToFile(gh.getBlacklistFile(), "a1\nb2\nc3\ne5");
        GitIntegrationTest.writeToFile(new File(new File(getTemporaryDirectory(), ".gitsetup"),
                "blacklist-words.txt"), "a1\nc3\nd4\nf6");
        GitIntegrationTest.writeToFile(gh.getWhitelistFile(), "b2\nd4\na1");
        assertEquals(expected,
                gh.getDirtyWords()
                        .keySet());
    }

    @Test
    public void testGetFileAsString() throws Exception {
        File test = new File(TEST_FILE_RELPATH);
        GitIntegrationTest.writeToFile(test, TEST_FILE_DATA);

        String baseWorkingDir = db.getWorkTree()
                .getAbsolutePath();

        String contents = gh.getFileAsString(TEST_FILE_RELPATH);
        assert (contents.equals(TEST_FILE_DATA));

        String path = test.getAbsolutePath();
        contents = gh.getFileAsString(path);
        assert (contents.equals(TEST_FILE_DATA));
    }

    @Test
    public void testGetPatternForLiteral() throws Exception {
        assertEquals("(?:\\b|_)\\Qabc\\E(?:\\b|_)",
                gh.getPatternFor("abc")
                        .pattern());
    }

    @Test
    public void testGetPatternForRegex() throws Exception {
        assertEquals("(?:\\b|_)abc(?:\\b|_)",
                gh.getPatternFor("REGEX:abc")
                        .pattern());
    }
}
