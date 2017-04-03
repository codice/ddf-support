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

public class PreCommitTest {
    protected static final String COMMIT_PREFIX = "PREFIX";

    protected static final String COMMIT_MSG_FILENAME = "COMMIT_EDITMSG";

    protected static final String DIRTY_WORD_LIST = "Bill,WHAT,march madness,DOB-11-1-4,REGEX:System\\.ouch\\.print(f|ln)?,.printMyTrace";

    protected static final String DIFF_OUTPUT =  // commented out lines would be removed by ChangeOnlyDiffFormatter
            //"diff --git a/dirty-file b/dirty-file\n" +
            //"new file mode 100644\n" +
            //"index 0000000..4a80eaf\n" +
            //"--- /dev/null\n" +
            "+++ b/dirty-file\n" +
                    //"@@ -0,0 +1,5 @@\n" +
                    "+1 this has a dirty word: bill\n" +
                    "+2 another dirty word: what\n" +
                    "+3 another dirty word: DOB-11-1-4, BILL, March Madness\n" +
                    "+4 e.printMyTrace()\n" +
                    "+\n" +
                    "+++ b/src/main/java/org/codice/sample/App.java\n" +
                    "+ * Hello world! for BILL\n" +
                    "+ * modified line with what you want in it!\n" +
                    "+        System.ouch.println( \"Hello World!!!!!\" );\n"; //+
    //"     }\n" +
    //" }\n";

    protected static final String DIFF_OUTPUT_CLEAN = "+++ b/dirty-file\n" +
            "+1 this has a clean word: bi11\n" +
            "+2 another clean word: Ray-theon\n" +
            "+3 another clean word: whatever, bO, march\n" +
            "+4 e.printMy()\n" +
            "+\n" +
            "+++ b/src/main/java/org/codice/sample/App.java\n" +
            "+ * Hello world! for who\n" +
            "+ * modified line with the word madness\n" +
            "+        System.ouch.printing( \"Hello World!!!!!\" );\n";

    // Configure the logging for this test
    static {
        ConfigureLogging cfg = new ConfigureLogging();
    }

    @Test
    public void testExecuteHook() throws Exception {
        final MockRepoHandler repHandler = new MockRepoHandler();

        repHandler.setDirtyWords(DIRTY_WORD_LIST);
        repHandler.setMockFile(COMMIT_MSG_FILENAME, COMMIT_PREFIX + "-1234 Sample valid commit msg.");
        repHandler.setCommitPrefix(COMMIT_PREFIX);

        final PreCommit preCommit = new PreCommit(repHandler);

        repHandler.setDiffString(DIFF_OUTPUT);
        assertTrue(preCommit.executeHook(null));

        repHandler.setDiffString(DIFF_OUTPUT_CLEAN);
        assertFalse(preCommit.executeHook(null));

    }
}
