/*
 * CometGUI -- Comet to Percolator proteomics search workflow with provenance.
 * Copyright (C) 2026 The CometGUI authors.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License, version 3, as published
 * by the Free Software Foundation. It is distributed WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for details.
 *
 * The full licence is the LICENSE file at the root of this repository. If it
 * is missing, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.cometgui.tools.process;

import org.cometgui.domain.log.MessageSeverity;

/**
 * Which of a tool's two output streams a line arrived on.
 *
 * <p>It exists because the stage log file merges the two into one file, and a merged file that
 * cannot say which stream a line came from has thrown away the one fact the reader needs when a
 * tool dies without explaining itself. The tag and the console severity are the two renderings of
 * that fact, and they are here together so that they cannot disagree.
 *
 * <p>Both tags are six characters long on purpose: every stage log line then has its text starting
 * at the same column, so the file can be read by eye and split by a parser at a fixed offset.
 */
enum ToolStream {

    /** The tool's standard output: ordinary progress, tagged {@code stdout}. */
    STANDARD_OUTPUT("stdout", MessageSeverity.INFO),

    /**
     * The tool's standard error, tagged {@code stderr}.
     *
     * <p>{@link MessageSeverity#STDERR} rather than {@link MessageSeverity#ERROR}: Comet and
     * Percolator both write ordinary progress to standard error, so treating the stream as a
     * failure would make every successful run look broken.
     */
    STANDARD_ERROR("stderr", MessageSeverity.STDERR);

    private final String tag;
    private final MessageSeverity severity;

    ToolStream(String tag, MessageSeverity severity) {
        this.tag = tag;
        this.severity = severity;
    }

    /**
     * How this stream is named in a stage log file.
     *
     * @return the tag, six characters, never null
     */
    String tag() {
        return tag;
    }

    /**
     * How a line from this stream is ranked in the console.
     *
     * @return the severity, never null
     */
    MessageSeverity severity() {
        return severity;
    }
}
