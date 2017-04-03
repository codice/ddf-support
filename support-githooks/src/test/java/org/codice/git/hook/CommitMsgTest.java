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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.codice.git.ConfigureLogging;
import org.codice.git.MockRepoHandler;
import org.junit.Test;

public class CommitMsgTest {
    protected static final String COMMIT_PREFIX = "PREFIX";

    protected static final String COMMIT_MSG_FILENAME = "COMMIT_EDITMSG";

    protected static final String DIRTY_WORD_LIST = "Bill,WHAT,march madness,DOB-11-1-4,REGEX:System\\.ouch\\.print(f|ln)?";

    // Configure the logging for this test
    static {
        ConfigureLogging cfg = new ConfigureLogging();
    }

    @Test
    public void testContainsDirtyWords() throws Exception {
        final MockRepoHandler repHandler = new MockRepoHandler();

        repHandler.setDirtyWords(DIRTY_WORD_LIST);
        repHandler.setMockFile(COMMIT_MSG_FILENAME, COMMIT_PREFIX + "-1234 Sample valid commit msg.");
        repHandler.setCommitPrefix(COMMIT_PREFIX);

        final CommitMsg hook = new CommitMsg(repHandler);

        assertFalse(hook.containsDirtyWords(""));
        assertFalse(hook.containsDirtyWords(null));
        assertFalse(hook.containsDirtyWords("Initial commit - no dirty words."));
        assertTrue(hook.containsDirtyWords("Commit with dirty word: bill and others"));
        assertTrue(hook.containsDirtyWords("Commit with dirty word and punctuation: bill."));
        assertTrue(hook.containsDirtyWords("Commit with mixed case dirty words: BILL"));
        assertTrue(hook.containsDirtyWords("Commit with mixed case dirty words: BiLL"));
        assertFalse(hook.containsDirtyWords("Commit with prefixed dirty word: abiLL"));
        assertFalse(hook.containsDirtyWords("Commit with dirty word which is part of another: billions"));
        assertTrue(hook.containsDirtyWords("Commit with dirty words separated with _: bill_"));
        assertTrue(hook.containsDirtyWords("Commit with dirty words separated with _: _bill"));
        assertTrue(hook.containsDirtyWords("Commit with dirty words separated with _: _bill_"));
        assertFalse(hook.containsDirtyWords("Commit with dirty words suffixed with a number: 2 * bill4"));
        assertTrue(hook.containsDirtyWords("Commit with dirty words and operators: 2|bill"));
        assertTrue(hook.containsDirtyWords("Commit with dirty words and operators: 2-bill"));
        assertTrue(hook.containsDirtyWords("Commit with dirty words and slashes: /bill/"));
        assertTrue(hook.containsDirtyWords("Commit with dirty words and double quotes: \"bill\""));
        assertTrue(hook.containsDirtyWords("Commit with dirty words and single quotes: 'bill'"));
        assertTrue(hook.containsDirtyWords("Commit with dirty words and parathesis: (bill)"));
        assertTrue(hook.containsDirtyWords("Commit with dirty words and square brackets: [bill]"));
        assertTrue(hook.containsDirtyWords("Commit with dirty words and angle brackets: <bill>"));
        assertTrue(hook.containsDirtyWords("Commit with dirty words and braces: {bill}"));
        assertTrue(hook.containsDirtyWords("Commit with dirty words and $: $bill"));
        assertTrue(hook.containsDirtyWords("Commit with dirty words and %: %bill"));
        assertTrue(hook.containsDirtyWords("Commit with dirty words and colons: :bill:"));
        assertTrue(hook.containsDirtyWords(
                "Commit with mixed case dirty words: mArch Madness"));
        assertTrue(hook.containsDirtyWords("Check for special chars: DOB-11-1-4"));
        assertFalse(hook.containsDirtyWords("Dirty word as part of another word: whatever"));
        assertTrue(hook.containsDirtyWords("Surrounded by punctuation: ...WHAT!!"));
        assertTrue(hook.containsDirtyWords("regex System.ouch.println(\"this\");"));
        assertTrue(hook.containsDirtyWords("regex System.ouch.print(\"this\");"));
        assertTrue(hook.containsDirtyWords("regex System.ouch.printf(\"this\");"));
        assertFalse(hook.containsDirtyWords("regex System_ouch_print(\"this\");"));
    }

    @Test
    public void testIsTicketNumberMissing() throws Exception {
        final MockRepoHandler repHandler = new MockRepoHandler();

        repHandler.setCommitPrefix(COMMIT_PREFIX);
        final CommitMsg hook = new CommitMsg(repHandler);

        assertTrue(hook.isTicketNumberMissing(null));
        assertTrue(hook.isTicketNumberMissing(""));
        assertTrue(hook.isTicketNumberMissing("\""));
        assertTrue(hook.isTicketNumberMissing("xyz abc"));
        assertTrue(hook.isTicketNumberMissing(COMMIT_PREFIX));
        assertTrue(hook.isTicketNumberMissing(COMMIT_PREFIX + "-"));
        assertTrue(hook.isTicketNumberMissing(COMMIT_PREFIX + "-abc"));
        assertTrue(hook.isTicketNumberMissing(COMMIT_PREFIX + "-123abc other"));
        assertTrue(hook.isTicketNumberMissing(COMMIT_PREFIX + "--123 other"));
        assertTrue(hook.isTicketNumberMissing(COMMIT_PREFIX + "-123.00 other"));
        assertTrue(hook.isTicketNumberMissing(COMMIT_PREFIX + "-123-2 other"));
        assertTrue(hook.isTicketNumberMissing(COMMIT_PREFIX + "-1"));
        assertFalse(hook.isTicketNumberMissing(COMMIT_PREFIX + "-1 rest of commit msg"));
        assertFalse(hook.isTicketNumberMissing(COMMIT_PREFIX + "-001 rest of commit msg"));
        assertFalse(hook.isTicketNumberMissing(COMMIT_PREFIX + "-123 rest of commit msg"));
        assertFalse(hook.isTicketNumberMissing(COMMIT_PREFIX + "-000 rest of commit msg"));
        assertFalse(hook.isTicketNumberMissing(COMMIT_PREFIX + "-123 rest of commit msg"));
        assertFalse(hook.isTicketNumberMissing("\"" + COMMIT_PREFIX + "-123 rest of commit msg\""));
        assertFalse(hook.isTicketNumberMissing(CommitMsg.RELEASE_MSG));
        assertFalse(hook.isTicketNumberMissing(CommitMsg.RELEASE_MSG + " rest of commit msg"));
        assertFalse(hook.isTicketNumberMissing(CommitMsg.RELEASE_MSG + "no spaces is ok here"));
        assertTrue(hook.isTicketNumberMissing(COMMIT_PREFIX + "-123: message with colon"));
    }

    @Test
    public void testExecuteHook() throws Exception {
        final MockRepoHandler repHandler = new MockRepoHandler();

        repHandler.setDirtyWords("MAN,what,bill");
        repHandler.setMockFile(COMMIT_MSG_FILENAME, COMMIT_PREFIX + "-1234 Sample valid commit msg.");
        repHandler.setCommitPrefix(COMMIT_PREFIX);
        final CommitMsg hook = new CommitMsg(repHandler);

        assertFalse(hook.executeHook(new String[] {COMMIT_MSG_FILENAME}));
        // change the commit message to not have a ticket
        repHandler.setMockFile(COMMIT_MSG_FILENAME, COMMIT_PREFIX.substring(1) + "-1234 Misspelled the " + COMMIT_PREFIX + " ticket.");
        assertTrue(hook.executeHook(new String[] {COMMIT_MSG_FILENAME}));

        // change the commit message to have valid ticket - but dirty word
        repHandler.setMockFile(COMMIT_MSG_FILENAME, COMMIT_PREFIX + "-1234 Bad commit message - contains bill");
    }
}
