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
package org.codice.git.hook.noops;

import java.io.IOException;
import java.util.logging.Logger;

import org.codice.git.RepositoryHandler;
import org.codice.git.hook.Hook;

public class CommitMsg extends Hook {
    private static final Logger LOGGER = Logger.getLogger(CommitMsg.class.getName());

    public CommitMsg(RepositoryHandler handler) throws IOException {
        super(handler);
    }

    /**
     * Dumps out the command arguments
     *
     * @param args arguments passed from git to the current git hook
     * @return true if the commit should be aborted, false if not
     * @throws Exception if any errors occur reading the commit message or the dirty list
     */
    @Override
    public boolean executeHook(String[] args) throws Exception {
        System.out.println("commit-msg hook invoked with " + args.length + " arguments");
        for (int i = 0; i < args.length; i++) {
            System.out.printf("  arg[%d]: %s\n", i, args[i]);
        }
        return false;
    }

}
