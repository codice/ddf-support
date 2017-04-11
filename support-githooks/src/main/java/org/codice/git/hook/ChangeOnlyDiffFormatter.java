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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawText;

public class ChangeOnlyDiffFormatter extends DiffFormatter {

    public ChangeOnlyDiffFormatter(OutputStream out) {
        super(out);
    }

    @Override
    protected void formatGitDiffFirstHeaderLine(ByteArrayOutputStream o,
            final DiffEntry.ChangeType type, final String oldPath, final String newPath)
            throws IOException {

    }

    @Override
    protected void writeHunkHeader(int aStartLine, int aEndLine, int bStartLine, int bEndLine)
            throws IOException {

    }

    @Override
    protected void formatIndexLine(OutputStream o, DiffEntry ent) throws IOException {

    }

    @Override
    protected void writeContextLine(final RawText text, final int line) throws IOException {

    }

    @Override
    protected void writeRemovedLine(final RawText text, final int line) throws IOException {

    }

    @Override
    protected void writeLine(char prefix, RawText text, int cur) throws IOException {
        // Only interested in added or modified lines, ignore removed or context lines
        if (prefix == '+') {
            super.writeLine(prefix, text, cur);
        }
    }
}
