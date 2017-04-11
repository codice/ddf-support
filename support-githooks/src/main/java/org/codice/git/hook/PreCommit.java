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

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.codice.git.RepositoryHandler;

public class PreCommit extends Hook {
    protected static final String ERR_MSG =
            "------------------------PRE-COMMIT HOOK ABORTED OPERATION----------------------%n"
                    + "%sTo commit anyway, use --no-verify (which you should never do!)%n"
                    + "-------------------------------------------------------------------------------%n";

    private static final Logger LOGGER = Logger.getLogger(PreCommit.class.getName());

    public PreCommit(RepositoryHandler handler) throws IOException {
        super(handler);
    }

    public boolean executeHook(String[] args) throws Exception {
        if (!hasDirtyWords()) { // no dirty words; all accepted so bail!
            return false;
        }
        LOGGER.finer("Executing the git diff to determine files with changes.");
        final String diff = repoHandler.getDiff();
        final Set<String> foundWords = new HashSet<String>();
        final Set<String> foundInFiles = new HashSet<String>();
        final StringBuilder sb = new StringBuilder();
        String currentFile = "???";
        boolean dirty = false;

        LOGGER.log(Level.FINEST, "Diff for this commit: {0}", diff);
        for (final String line: StringUtils.split(diff, '\n')) {
            if (line.startsWith("+++ b/")) {
                if ((sb.length() > 0) && containsDirtyWords(sb.toString(), foundWords)) {
                    dirty = true;
                    foundInFiles.add(currentFile);
                }
                sb.setLength(0);
                currentFile = StringUtils.substringAfter(line, "+++ b/");
                // continue which will allow us to validate the filename as well
            }
            sb.append(line).append('\n'); // accumulate all lines to check in one shot
        }
        if ((sb.length() > 0) && containsDirtyWords(sb.toString(), foundWords)) {
            dirty = true;
            foundInFiles.add(currentFile);
        }
        if (dirty) {
            LOGGER.log(Level.FINE, "Dirty words found: {0}", foundWords);
            LOGGER.log(Level.FINE, "Files with dirty words: {0}", foundInFiles);
            sb.setLength(0);
            Hook.appendDirtyWords(sb, foundWords)
                    .append("In files:%n");
            for (final String f: foundInFiles) {
                sb.append('\t').append(f).append("%n");
            }
            // the double formatting is to properly support %n in the string builder too!
            System.out.printf(String.format(ERR_MSG, sb));
            return true;
        } else {
            LOGGER.info("Commit is clean.");
            System.out.println("Commit is clean.");
            return false;
        }
    }
}
