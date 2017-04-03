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
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Charsets;

public abstract class RepositoryHandler {
    private static final Logger LOGGER = Logger.getLogger(RepositoryHandler.class.getName());

    public static final String PREFIX_NONE = "NONE";
    public static final String REGEX_PREFIX = "REGEX:";
    // check for word boundaries with \b and also for underscores, ?: is to not capture
    public static final String REGEX_PATTERN = "(?:\\b|_)%s(?:\\b|_)";

    private final File basedir;

    protected RepositoryHandler(File basedir) {
        this.basedir = basedir;
    }

    /**
     * Gets the base directory for the hooks setup files for this repository.
     *
     * @return the base directory for the hooks setup files
     */
    public File getBasedir() {
        return basedir;
    }

    /**
     * Gets the local meta directory for the repository.
     *
     * @return the local metadata directory
     */
    public abstract File getMetadir();

    /**
     * Gets the local backlist words file for the repository.
     *
     * @return the local blacklist words file
     */
    public File getBlacklistFile() {
        return new File(basedir, "blacklist-words.txt");
    }

    /**
     * Gets the local whitelist words file for the repository.
     *
     * @return the local whitelist words file
     */
    public File getWhitelistFile() {
        return new File(basedir, "whitelist-words.txt");
    }

    /**
     * Combines the blacklist words (a.k.a. dirty) files gitsetup/blacklist-words.txt,
     * ~/.gitsetup/blacklist-words.txt, and the default one in resources/blacklist-words.txt and
     * remove those from the file gitsetup/whitelist-words.txt to return a map of all dirty words
     * along with their corresponding regex patterns.
     *
     * @return map containing each of the dirty words as a string with its corresponding regex pattern
     * @throws java.io.IOException if any error occurs reading the files
     */
    public Map<String, Pattern> getDirtyWords() throws IOException {
        final Set<String> words = new HashSet<String>();
        final String uhome = System.getProperty("user.home");

        // start with ~/.gitsetup/blacklist-words.txt
        if (StringUtils.isNotEmpty(uhome)) {
            final File ubfile = new File(new File(uhome, ".gitsetup"), "blacklist-words.txt");

            if (ubfile.exists()) {
                LOGGER.log(Level.FINE, "Loading user-defined blacklist from: {0}", ubfile);
                for (final String l: FileUtils.readLines(ubfile, Charsets.UTF_8)) {
                    if (!l.isEmpty() && !l.startsWith("#")) {
                        words.add(l);
                    }
                }
            }
        }
        InputStream is = null;

        // now add the default blacklist-words.txt stored in the associated jar
        try {
            is = GitHandler.class.getResourceAsStream("/blacklist-words.txt");
            if (is != null) {
                LOGGER.log(Level.FINE, "Loading default blacklist");
                for (final String l: IOUtils.readLines(is, Charsets.UTF_8)) {
                    if (!l.isEmpty() && !l.startsWith("#")) {
                        words.add(l);
                    }
                }
            }
        } catch (IOException e) { // ignore and continue without defaults
        } finally {
            IOUtils.closeQuietly(is);
        }
        final File bfile = getBlacklistFile();
        final File wfile = getWhitelistFile();

        // now add /git/XXX/gitsetup/blacklist-words.txt
        if (bfile.exists()) {
            LOGGER.log(Level.FINE, "Loading local blacklist from: {0}", bfile);
            for (final String l: FileUtils.readLines(bfile, Charsets.UTF_8)) {
                if (!l.isEmpty() && !l.startsWith("#")) {
                    words.add(l);
                }
            }
        }
        // finally remove /git/XXX/gitsetup/whitelist-words.txt
        if (!words.isEmpty() && wfile.exists()) {
            LOGGER.log(Level.FINE, "Loading local whitelist from: {0}", wfile);
            for (final String l: FileUtils.readLines(wfile, Charsets.UTF_8)) {
                if (!l.isEmpty() && !l.startsWith("#")) {
                    words.remove(l);
                }
            }
        }
        LOGGER.log(Level.FINE, "Dirty Words are: {0}", words);
        // generate all regex patterns for all dirty words
        final Map<String, Pattern> wordmap = new HashMap<String, Pattern>(words.size() * 3 / 2);

        for (final String w: words) {
            // quote the word if it is a literal one (not starting with regex prefix)
            wordmap.put(w, getPatternFor(w));
        }
        return wordmap;
    }

    /**
     * Reads the gitsetup/commit-prefix.txt file and return the prefix value.
     *
     * @return the commit prefix value
     * @throws java.io.IOException if any error occurs reading the file
     */
    public String getCommitPrefix() throws IOException {
        final File pfile = new File(basedir, "commit-prefix.txt");

        if (!pfile.exists()) {
            return RepositoryHandler.PREFIX_NONE;
        }
        return FileUtils.readFileToString(pfile, Charsets.UTF_8).trim();
    }

    /**
     * Reads the file at the given path from the root of the working tree
     *
     * @param filename path to be added to the root of the working directory
     * @return String containing the contents of the file
     * @throws Exception
     */
    public abstract String getFileAsString(String filename) throws Exception;

    /**
     * Uses git to scan the repository and return a list of differences in the
     * files waiting to be committed. This will only look at files that have been
     * added and are waiting to be committed (only those that would be affected
     * by a commit). Uses a custom formatter to only include the lines from the
     * changes that involve new content (deletions, etc. are ignored).
     *s
     * @return String containing lines of the diff that involve new content
     * @throws java.io.IOException                         if any exceptions occur during processing
     * @throws org.eclipse.jgit.api.errors.NoHeadException if no git repository can be found
     */
    public abstract String getDiff() throws Exception;

    public abstract String getConfigString(String section, String subsection, String key);

    public abstract void setConfigString(String section, String subsection, String key, String value) throws IOException;

    protected Pattern getPatternFor(String dirtyWord) {
        // quote the word if it is a literal one (not starting with regex prefix)
        final String rw = dirtyWord.startsWith(RepositoryHandler.REGEX_PREFIX) ? dirtyWord.substring(RepositoryHandler.REGEX_PREFIX.length()) : Pattern.quote(dirtyWord);

        return Pattern.compile(String.format(RepositoryHandler.REGEX_PATTERN, rw),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
