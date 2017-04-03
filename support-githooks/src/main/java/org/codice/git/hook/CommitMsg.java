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
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.codice.git.RepositoryHandler;

public class CommitMsg extends Hook {
    protected static final String ERR_MSG =
            "------------------------COMMIT-MSG HOOK ABORTED OPERATION----------------------%n"
                    + "%sTo commit anyway, use --no-verify (which you should never do!)%n"
                    + "-------------------------------------------------------------------------------%n";

    protected static final String ERR_TICKET_MSG =
            "------------------------COMMIT-MSG HOOK ABORTED OPERATION----------------------%n"
                    + "Commit message must start with a ticket number followed by a space then message%n"
                    + "For Example \"%s-1234 \" or if you're special it can be \"[maven-release-plugin]\"%n"
                    + "To commit anyway, use --no-verify (which you should never do!)%n"
                    + "-------------------------------------------------------------------------------%n";

    protected static final String RELEASE_MSG = "[maven-release-plugin]";

    private static final Logger LOGGER = Logger.getLogger(CommitMsg.class.getName());

    private static final PrintStream OUT = System.out; // to avoid the git hooks

    public CommitMsg(RepositoryHandler handler) throws IOException {
        super(handler);
    }

    /**
     * Performs the functionality of the commit-msg hook. Currently checks that the commit
     * message:
     * - starts with the ticket number
     * - does not contain any words from the dirty list
     * <p/>
     * This hook expects a single argument from git specifying the name of the file where the
     * commit message has been saved.
     *
     * @param args arguments passed from git to the current git hook
     * @return true if the commit should be aborted, false if not
     * @throws Exception if any errors occur reading the commit message or the dirty list
     */
    @Override
    public boolean executeHook(String[] args) throws Exception {
        try {
            if ((args == null) || (args.length == 0)) {
                return false;
            }
            LOGGER.log(Level.FINE, "Reading commit message from: {0}", args[0]);
            String commitMsg = repoHandler.getFileAsString(args[0]);

            LOGGER.log(Level.FINE, "Commit message: {0}", commitMsg);
            //CommitMsg.OUT.println("Commit message: " + commitMsg);
            if (isTicketNumberMissing(commitMsg)) {
                return true;
            }
            //CommitMsg.OUT.println("Ticket number has been validated.");
            if (containsDirtyWords(commitMsg)) {
                return true;
            }
            CommitMsg.OUT.println("Commit message is clean.");
            return false;
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Scans the commit message to see if it contains words from the "dirty list."
     *
     * @param commitMsg The commit message to be scanned
     * @return true if dirty words are found, false otherwise
     * @throws IOException if errors are encoutered reading the dirty word file
     */
    protected boolean containsDirtyWords(String commitMsg) throws IOException {
        final Set<String> foundWords = new HashSet<String>();

        if (containsDirtyWords(commitMsg, foundWords)) {
            final StringBuilder sb = new StringBuilder();

            Hook.appendDirtyWords(sb, foundWords)
                    .append("in the commit message.%n");
            // the double formatting is to properly support %n in the string builder too!
            CommitMsg.OUT.printf(String.format(ERR_MSG, sb));
            LOGGER.log(Level.INFO, "Dirty words found: {0} in the commit message", foundWords);
            return true;
        }
        return false;
    }

    /**
     * Checks the commit message to ensure that it starts with a valid ticket number.
     *
     * @param commitMsg the commit message to be scanned
     * @return true if the ticket number is missing, false otherwise
     */
    protected boolean isTicketNumberMissing(String commitMsg) {
        String prefix = "";

        try {
            prefix = repoHandler.getCommitPrefix();
            if (StringUtils.isEmpty(prefix)) { // no commit prefix so bail
                return false;
            }
            if (StringUtils.isEmpty(commitMsg)) {
                LOGGER.warning("Commit message is empty - aborting commit.");
                CommitMsg.OUT.printf(ERR_TICKET_MSG, prefix);
                return true;
            }
            if (!commitMsg.startsWith(RELEASE_MSG)) {
                String[] values = commitMsg.split("\\s");
                boolean badMessage = true;

                if (values.length > 1) {
                    values = values[0].split("-");
                    if (values.length == 2) {
                        if (values[0].startsWith("\"")) {
                            if (values[0].length() > 1) {
                                values[0] = values[0].substring(1);
                            }
                        }

                        LOGGER.log(Level.FINE,
                                "Validating commit message with prefix: {0} and number: {1} against expected prefix: {2}",
                                new Object[] {values[0], values[1], prefix});
                        if (values[0].equals(prefix)) {
                            badMessage = !values[1].matches("[0-9]+");
                            //int x = Integer.parseInt(values[1]);
                        }
                    }
                }
                if (badMessage) {
                    LOGGER.log(Level.WARNING,
                            "Invalid commit message: {0} aborting commit.",
                            commitMsg);
                    CommitMsg.OUT.printf(ERR_TICKET_MSG, prefix);
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Invalid commit message ticket number: " + e.getMessage() + " Commit message: "
                            + commitMsg,
                    e);
            LOGGER.log(Level.WARNING, "Invalid commit message: {0} aborting commit.", commitMsg);
            CommitMsg.OUT.printf(ERR_TICKET_MSG, prefix);
            return true;
        }
        LOGGER.info("Commit message is valid.");
        return false;
    }
}
