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

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.codice.git.ConfigureLogging;
import org.codice.git.GitHandler;
import org.codice.git.RepositoryHandler;

public class Hook {
    /*
     * Use a positive number here for the error code - the git bash shell doesn't recognize
     * negative numbers - when those are returned it sees them as a zero.
     */
    public static int ERROR_CODE = 1;

    private static Logger LOGGER = Logger.getLogger(Hook.class.getName());

    // Configure the logging for this test
    static {
        ConfigureLogging cfg = new ConfigureLogging();
    }

    protected final RepositoryHandler repoHandler;

    private final Map<String, Pattern> dirtyWords;

    /**
     * Instantiates a new hook with the specified repository handler.
     *
     * @param handler the repository handler
     * @throws java.io.IOException                    if an error occurs while initializing
     * @throws java.util.regex.PatternSyntaxException if unable to compile regex patters for dirty
     *                                                words
     */
    protected Hook(RepositoryHandler handler) throws IOException {
        this.repoHandler = handler;
        this.dirtyWords = repoHandler.getDirtyWords();
    }

    /**
     * Checks if there are dirty words defined.
     *
     * @return <code>true</code> if there are dirty words to be checked for; <code>false</code> otherwise
     */
    public boolean hasDirtyWords() {
        return !dirtyWords.isEmpty();
    }

    /**
     * Entry point for the git hook processing. Invoked by the individual git scripts
     * in the .git/hooks directory. Each script provides the class name of the (java) hook
     * to execute along with the parameters that git passed as part of the hook call.
     * <p/>
     * The java handler is instantiated, the class name is removed from the arguments,
     * and the executeHook method of the java hook is invoked.
     *
     * @param args class name of java hook handler plus original git arguments
     */
    public static void main(String[] args) {
        try {
            if (ArrayUtils.isEmpty(args)) {
                LOGGER.log(Level.WARNING, "Missing arguments");
                System.exit(ERROR_CODE);
            }
            if (args.length < 1) {
                LOGGER.log(Level.WARNING, "Missing basedir argument");
                System.exit(ERROR_CODE);
            }
            if (args.length < 2) {
                LOGGER.log(Level.WARNING, "Missing maven settings argument");
                System.exit(ERROR_CODE);
            }
            if (args.length < 3) {
                LOGGER.log(Level.WARNING, "Missing hook class argument");
                System.exit(ERROR_CODE);
            }
            final RepositoryHandler handler = new GitHandler(new File(args[0]));
            final String settings = StringUtils.defaultString(args[1]);
            final Hook hook = (Hook) Class.forName(args[2])
                    .getConstructor(RepositoryHandler.class)
                    .newInstance(handler);
            final String[] hargs = new String[args.length - 3];

            System.arraycopy(args, 3, hargs, 0, hargs.length);
            LOGGER.log(Level.FINE, "Hook being called with arguments: {0}", ArrayUtils.toString(args));
            GitHooks.downloadBlacklist(handler, settings, false);
            if (hook.executeHook(hargs)) {
                System.exit(ERROR_CODE);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Exception caught: " + e.getMessage(), e);
            System.exit(ERROR_CODE);
        }
    }

    /**
     * Appends all dirty words in the provided set to the provided string builder.
     *
     * @param sb  the string builder where to append
     * @param dirtyWords the set of dirty words to append
     * @return <code>sb</code> for chaining
     */
    protected static StringBuilder appendDirtyWords(StringBuilder sb, Set<String> dirtyWords) {
        final StringBuilder dwsb = new StringBuilder();
        final StringBuilder drsb = new StringBuilder();

        for (final String dw: dirtyWords) {
            if (dw.startsWith(RepositoryHandler.REGEX_PREFIX)) {
                drsb.append('\t').append(dw.substring(RepositoryHandler.REGEX_PREFIX.length())).append("%n");
            } else {
                dwsb.append('\t').append(dw).append("%n");
            }
        }
        if (dwsb.length() > 0) {
            sb.append("Dirty words found:%n")
                    .append(dwsb);
        }
        if (drsb.length() > 0) {
            sb.append("Dirty regex patterns found:%n")
                    .append(drsb);
        }
        return sb;
    }

    /**
     * Scans the specified string to see if it contains dirty words from the "dirty list".
     *
     * @param s     the string to be scanned
     * @param found a set where to report the words found so far
     * @return <code>true</code> if dirty words are found, <code>false</code> otherwise
     * @throws IOException if errors are encountered reading the dirty word file
     */
    protected boolean containsDirtyWords(String s, Set<String> found) throws IOException {
        if (StringUtils.isEmpty(s) || dirtyWords.isEmpty()) { // nothing to check or no dirty words; all accepted so bail!
            return false;
        }
        boolean dirty = false;

        for (final Map.Entry<String, Pattern> e: dirtyWords.entrySet()) {
            if (e.getValue().matcher(s).find()) {
                dirty = true;
                found.add(e.getKey());
            }
        }
        return dirty;
    }

    /**
     * Each git hook should implement their version of this method. This
     * default method just fails.
     *
     * @param args arguments passed from git to the current git hook
     * @return true if the commit should abort, false otherwise
     * @throws Exception any exception that occurs during processing
     */
    public boolean executeHook(String[] args) throws Exception {
        // Override this to perform hook-specific functionality
        return true;
    }
}
