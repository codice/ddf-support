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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.codice.git.RepositoryHandler;

import com.google.common.base.Charsets;

/**
 * Handles the installation and updating of the git hooks in a current repository.
 */
public class GitHooks {
    public static final String[] HOOKS =
            new String[] {"applypatch-msg", "commit-msg", "pre-applypatch", "pre-commit"};

    public static final String HOOK_DIR = "hooks";

    public static final String HOOK_BASE_DIR = "/" + HOOK_DIR + "/";

    private static final Logger LOGGER = Logger.getLogger(GitHooks.class.getName());

    public static void install(RepositoryHandler handler, String settings) throws IOException {
        if (handler == null) {
            LOGGER.warning("No Repository handler specified; git hooks not installed");
            return;
        }
        final File hdir = new File(handler.getMetadir(), HOOK_DIR);
        final Map<String, String> vars = new HashMap<String, String>(8);
        final StrSubstitutor substitutor = new StrSubstitutor(vars, "@{", "}", '@');
        boolean filesCopiedOK = true;

        // because the paths are being expanded in the bash scripts for the hooks, we will be safe
        // on Windows as long as we replace all \ for / since some of those paths will be concatenated
        // with others inside the scripts
        vars.put("BASEDIR",
                handler.getBasedir()
                        .getAbsolutePath()
                        .replace('\\', '/'));
        vars.put("POMPATH", new File(handler.getBasedir(), "pom.xml").getAbsolutePath()
                .replace('\\', '/'));
        vars.put("SETTINGS", settings.replace('\\', '/'));
        LOGGER.log(Level.INFO, "Installing hooks into directory: {0}", hdir);
        System.out.println("[INFO] Installing git hooks.");
        for (String hookName : HOOKS) {
            filesCopiedOK = filesCopiedOK && copyFromJar(HOOK_BASE_DIR + hookName,
                    new File(hdir, hookName),
                    substitutor,
                    true);
        }
        LOGGER.log(Level.INFO, "Hook files copied successfully: {0}", filesCopiedOK);
    }

    public static void clean(RepositoryHandler handler) throws IOException {
        if (handler == null) {
            LOGGER.warning("No Repository handler specified; git hooks not cleaned.");
            return;
        }
        final File bdir = handler.getBasedir();
        final File hdir = new File(handler.getMetadir(), HOOK_DIR);
        final File cpath = new File(bdir, "classpath.txt");

        LOGGER.log(Level.INFO, "Cleaning cached classpath from directory: {0}", bdir);
        System.out.println("[INFO] Removing git hooks cached classpath.");
        boolean filesDeletedOK = !cpath.exists() || cpath.delete();

        LOGGER.log(Level.INFO, "Cleaning hooks from directory: {0}", hdir);
        System.out.println("[INFO] Removing git hooks.");
        for (String hookName : HOOKS) {
            final File hfile = new File(hdir, hookName);

            filesDeletedOK = filesDeletedOK && (!hfile.exists() || hfile.delete());
        }
        LOGGER.log(Level.INFO, "Hook files deleted successfully: {0}", filesDeletedOK);
    }

    /**
     * Downloads the blacklist either dynamically (if already done in the past) or by prompting for
     * the maven artifact info (if at install time).
     *
     * @param handler  the repository handler
     * @param settings the maven settings file or "" if using the default one
     * @param install  <code>true</code> if we are at install time; <code>false</code> if this is from a hook
     * @throws IOException if an error occurs
     */
    public static void downloadBlacklist(RepositoryHandler handler, String settings,
            boolean install) throws IOException {
        // use stderr instead of stdout to allow the stdout to be piped without impacting the querying
        new Artifact(handler,
                "blacklist words",
                handler.getBlacklistFile(),
                new File(handler.getBasedir(), "blacklist-words.mvn"),
                install).download(settings, install ? System.err : System.out);
    }

    /**
     * Copied/expanded from org.apache.commons.io.IOUtils
     * Takes the name of an input file and the name of an output file and copies the input file to the
     * output file.
     *
     * @param fromName       the name of the input file to read from
     * @param output         the name of the output file to write to
     * @param substitutor    the optional string substitutor to use when processing files (can be <code>null</code>)
     * @param makeExecutable boolean indicating whether the output file should be made executable when done copying
     * @return true indicates the file was successfully copied, false if an error occurs
     * @throws java.lang.NullPointerException if the inputu or output is null
     * @throws IOException                    if an I/O error occurs
     * @throws java.lang.ArithmeticException  if the byte count is too large
     */
    private static boolean copyFromJar(String fromName, File output, StrSubstitutor substitutor,
            boolean makeExecutable) throws IOException {
        boolean successful = true;
        InputStream is = null;

        try {
            LOGGER.log(Level.FINE, "Copying file {0} to {1}", new Object[] {fromName, output});
            is = GitHooks.class.getResourceAsStream(fromName);
            if (is == null) {
                LOGGER.log(Level.WARNING, "Unable to locate file {0} for copying.", fromName);
                throw new IOException("Unable to locate file " + fromName + " for copying");
            }
            final PrintWriter pw = new PrintWriter(new FileWriter(output));

            for (final String line : IOUtils.readLines(is, Charsets.UTF_8)) {
                if (substitutor != null) {
                    pw.println(substitutor.replace(line));
                } else {
                    pw.println(line);
                }
            }
            pw.close();
            if (makeExecutable) {
                output.setExecutable(true);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    "Exception copying to file " + output + " - file may be incompletely copied.",
                    e);
            successful = false;
        } finally {
            IOUtils.closeQuietly(is);
        }
        return successful;
    }
}
