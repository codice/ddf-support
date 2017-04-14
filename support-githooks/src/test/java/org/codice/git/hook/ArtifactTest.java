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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.codice.git.ConfigureLogging;
import org.codice.git.GitIntegrationTest;
import org.codice.git.MockRepoHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;

public class ArtifactTest {
    // Configure the logging for this test
    static {
        ConfigureLogging cfg = new ConfigureLogging();
    }

    private File rootdir = null;

    private File basedir = null;

    private File metadir = null;

    private File hookdir = null;

    private MockRepoHandler repHandler = null;

    private File aifile;

    private Artifact a;

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
        this.aifile = new File(basedir, "blacklist-words.mvn");
        this.a = new Artifact(repHandler,
                "blacklist words",
                repHandler.getBlacklistFile(),
                aifile,
                true);
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteQuietly(rootdir);
    }

    @Test
    public void testReadInfoWhenFileExist() throws Exception {
        GitIntegrationTest.writeToFile(aifile, "a,b");

        assertNull(a.getMavenInfo());
        assertNull(a.getMavenName());

        a.readInfo();

        assertEquals("a", a.getMavenInfo());
        assertEquals("b", a.getMavenName());
    }

    @Test
    public void testReadInfoWhenFileDoesNotExist() throws Exception {
        assertNull(a.getMavenInfo());
        assertNull(a.getMavenName());
        assertFalse(aifile.exists());

        a.readInfo();

        assertNull(a.getMavenInfo());
        assertNull(a.getMavenName());
    }

    @Test
    public void testReadInfoWithInvalidContent() throws Exception {
        GitIntegrationTest.writeToFile(aifile, "a;b");

        assertNull(a.getMavenInfo());
        assertNull(a.getMavenName());

        a.readInfo();

        assertNull(a.getMavenInfo());
        assertNull(a.getMavenName());
    }

    @Test
    public void testWriteInfo() throws Exception {
        a.mvnInfo = "a";
        a.mvnName = "b";

        assertFalse(aifile.exists());

        a.writeInfo();

        assertTrue(aifile.exists());
        assertEquals("a,b", FileUtils.readFileToString(aifile, Charsets.UTF_8));
    }

    @Test
    public void testWriteInfoWithNoMavenInfo() throws Exception {
        assertFalse(aifile.exists());
        assertNull(a.getMavenInfo());
        assertNull(a.getMavenName());

        a.writeInfo();

        assertFalse(aifile.exists());
    }

    @Test
    public void testWriteInfoWhenFileAlreadyExist() throws Exception {
        a.mvnInfo = "c";
        a.mvnName = "d";
        GitIntegrationTest.writeToFile(aifile, "a,b");

        a.writeInfo();

        assertTrue(aifile.exists());
        assertEquals("a,b", FileUtils.readFileToString(aifile, Charsets.UTF_8));
    }

    @Test
    public void testPromptInfoInputYesAndAllDefaults() throws Exception {
        repHandler.setConfigString("user", null, "email", "bill@gmail.com");
        final BufferedReader br = new BufferedReader(new StringReader(String.format(
                "y%n%n%n%n%n%nZ%n")));

        a.promptInfo(br, System.out);

        assertEquals("Z", br.readLine());
        assertEquals("com.gmail:blacklist-words:RELEASE:txt", a.getMavenInfo());
        assertEquals("blacklist-words.txt", a.getMavenName());
    }

    @Test
    public void testPromptInfoInputNo() throws Exception {
        repHandler.setConfigString("user", null, "email", "bill@gmail.com");
        final BufferedReader br = new BufferedReader(new StringReader(String.format("n%nZ%n")));

        a.promptInfo(br, System.out);

        assertEquals("Z", br.readLine());
        assertNull(a.getMavenInfo());
        assertNull(a.getMavenName());
    }

    @Test
    public void testPromptInfoInputYesAndAllDefaultsButInvalidEmail() throws Exception {
        repHandler.setConfigString("user", null, "email", "bill%gmail.com");
        final BufferedReader br = new BufferedReader(new StringReader(String.format(
                "y%n%ngroup%n%n%n%n%nZ%n")));

        a.promptInfo(br, System.out);

        assertEquals("Z", br.readLine());
        assertEquals("group:blacklist-words:RELEASE:txt", a.getMavenInfo());
        assertEquals("blacklist-words.txt", a.getMavenName());
    }

    @Test
    public void testPromptInfoInputYesAndAllDefaultsButInvalidEmail2()
            throws Exception {
        repHandler.setConfigString("user", null, "email", "bill@gmail_com");
        final BufferedReader br = new BufferedReader(new StringReader(String.format(
                "y%n%ngroup%n%n%n%n%nZ%n")));

        a.promptInfo(br, System.out);

        assertEquals("Z", br.readLine());
        assertEquals("group:blacklist-words:RELEASE:txt", a.getMavenInfo());
        assertEquals("blacklist-words.txt", a.getMavenName());
    }

    @Test
    public void testPromptInfoInputYesAndAllDefaultsExceptForClassifier()
            throws Exception {
        repHandler.setConfigString("user", null, "email", "bill@gmail.com");
        final BufferedReader br = new BufferedReader(new StringReader(String.format(
                "y%n%n%n%n%nclassifier%nZ%n")));

        a.promptInfo(br, System.out);

        assertEquals("Z", br.readLine());
        assertEquals("com.gmail:blacklist-words:RELEASE:txt:classifier", a.getMavenInfo());
        assertEquals("blacklist-words.txt", a.getMavenName());
    }

    @Test
    public void testPromptInfoInputYesAndNoDefaults() throws Exception {
        repHandler.setConfigString("user", null, "email", "bill@gmail.com");
        final BufferedReader br = new BufferedReader(new StringReader(String.format(
                "y%ngroup%nartifact%nversion%ntype%nclassifier%nZ%n")));

        a.promptInfo(br, System.out);

        assertEquals("Z", br.readLine());
        assertEquals("group:artifact:version:type:classifier", a.getMavenInfo());
        assertEquals("artifact.type", a.getMavenName());
    }
}
