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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.exec.PumpStreamHandler;
import org.codice.git.hook.GitHooks;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

@Category(IntegrationTest.class)
public class GitIntegrationTest extends RepositoryTestCase {
  // Configure the logging for this test
    static {
        ConfigureLogging cfg = new ConfigureLogging();
    }

    Logger LOGGER = LoggerFactory.getLogger(GitIntegrationTest.class);

    private File sourceFile;

    private Git source;

   public static void writeToFile(File actFile, String string) throws IOException {
        Files.createParentDirs(actFile);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(actFile);
            fos.write(string.getBytes("UTF-8"));
            fos.close();
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        // flush out the sample git repository here
        // add a file with a single line of text - add and commit it
        sourceFile = new File(db.getWorkTree(), "SomeFile.txt");
        writeToFile(sourceFile, "Hello World.\n");
        source = new Git(db);
        source.add().addFilepattern("SomeFile.txt").call();
        source.commit().setMessage("Initial commit for source.").call();
        // install the hooks over to the newly created git directory
        final MockRepoHandler repHandler = new MockRepoHandler(new File(db.getWorkTree(), "gitsetup"));

        repHandler.setMetadir(db.getDirectory());
        GitHooks.install(repHandler, "");
        // initialize a classpath.txt file since we are unit testing
        writeTrashFile("gitsetup", "classpath.txt", System.getProperty("java.class.path"));
        // initialize a commit-prefix.txt since we are unit testings
        writeTrashFile("gitsetup", "commit-prefix.txt", "DDF");
        // initialize a blacklist-words.txt.txt since we are unit testings
        writeTrashFile("gitsetup", "blacklist-words.txt", String.format("bill%nWHAT%n"));
        LOGGER.debug("Installing hook files into {}", db.getDirectory());
    }

    @Test
    public void testPreCommitAndCommitHooks() throws Exception {
        // add another file to the directory
        File f = writeTrashFile("BadFile.txt", "Line with bad words: BILL, what");

        source.add().addFilepattern("BadFile.txt").call();

        //source.commit().setMessage("Bad commit message - BILL").call();
        String[] args = new String[] {"commit", "-m",
                "Bad commit message - no ticket number and dirty words: BILL, what"};
        int retValue = executeGitCommand(args); // should fail with dirty words in file (pre-commit)
        assert (retValue == 1);

        writeTrashFile("BadFile.txt", "Line without any bad words.");
        source.add().addFilepattern("BadFile.txt").call();

        retValue = executeGitCommand(
                args); // should fail with a bad commit message (commit-msg - no ticket number)
        assert (retValue == 1);

        args[2] = "DDF-123 Bad commit message - ticket number but still dirty words: BILL, what.";
        retValue = executeGitCommand(
                args); // should fail with a bad commit message (commit-msg - dirty words in message)
        assert (retValue == 1);

        args[2] = "DDF-123 Good commit message - ticket number and no dirty words.";
        retValue = executeGitCommand(
                args); // should pass with both good data and good commit message
        assert (retValue == 0);
    }

    @Test
    public void testGitStatus() throws Exception {
        String[] args = new String[] {"status"};
        executeGitCommand(args);

        args = new String[] {"remote", "-v"};
        executeGitCommand(args);

        args = new String[] {"lg"};
        executeGitCommand(args);

        args = new String[] {"remote", "-v"};
        executeGitCommand(args);

    }

    private int executeGitCommand(String[] args) throws IOException {
        int exitValue = 0;
        List<String> outputLines = null;
        CommandLine cmdLine = new CommandLine("git");
        for (String arg : args) {
            cmdLine.addArgument(arg);
        }
        DefaultExecutor executor = new DefaultExecutor();
        executor.setExitValue(0);
        executor.setWorkingDirectory(trash);
        CollectingLogOutputStream output = new CollectingLogOutputStream();
        PumpStreamHandler pump = new PumpStreamHandler(output, output);
        executor.setStreamHandler(pump);
        try {
            exitValue = executor.execute(cmdLine);
            outputLines = output.getLines();
        } catch (IOException e) {
            boolean hookRan = false;
            String errorMessage = "";
            // Check if we got the aborted message from the hook - implies it ran successfully
            outputLines = output.getLines();
            if ((outputLines != null) && (outputLines.size() > 0)) {
                errorMessage = outputLines.get(0);
                for (String line : outputLines) {
                    if (line.contains("HOOK ABORTED")) {
                        hookRan = true;
                        break;
                    }
                }
            }
            if (hookRan) {
                LOGGER.debug("Hook ran successfully - returning an error to abort the git command");
            } else {
                LOGGER.warn("Unexpected error during hook processing - first line of output: {}",
                        errorMessage, e);
                throw e;
            }
            exitValue = 1;
        }

        for (String line : outputLines) {
            System.err.println(line);
        }
        return exitValue;
    }

    private class CollectingLogOutputStream extends LogOutputStream {
        private final List<String> lines = new ArrayList<String>();

        @Override
        protected void processLine(String line, int level) {
            lines.add(line);
        }

        public List<String> getLines() {
            return lines;
        }
    }
}
