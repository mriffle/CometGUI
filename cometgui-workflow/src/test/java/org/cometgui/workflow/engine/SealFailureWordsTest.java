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

package org.cometgui.workflow.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The words a seal failure's reason ends with ({@link RunExecution#plainly}): the system's own
 * reason and the file, never an exception's class unless there is nothing else to say. The real
 * runs that meet these errors are {@code SealFailureRunTest} and, in a child JVM where mutation
 * testing cannot see it, {@code FileSizeLimitRunTest}; this pins every branch in this JVM.
 */
class SealFailureWordsTest {

    @Test
    @DisplayName("a refused permission names the file")
    void permissionDenied() {
        assertEquals(
                "permission was denied (/r/provenance/provenance.json.tmp-1)",
                RunExecution.plainly(
                        new AccessDeniedException("/r/provenance/provenance.json.tmp-1")));
    }

    @Test
    @DisplayName("a file-system error gives its own reason and the file")
    void fileSystemReason() {
        assertEquals(
                "No space left on device (/r/provenance/provenance.json)",
                RunExecution.plainly(
                        new FileSystemException(
                                "/r/provenance/provenance.json", null, "No space left on device")));
    }

    @Test
    @DisplayName("a file-system error without a reason is given by its message")
    void fileSystemWithoutReason() {
        assertEquals(
                "/r/provenance/provenance.json",
                RunExecution.plainly(new FileSystemException("/r/provenance/provenance.json")));
    }

    @Test
    @DisplayName("any other error is given by its message: File too large")
    void message() {
        assertEquals("File too large", RunExecution.plainly(new IOException("File too large")));
    }

    @Test
    @DisplayName("an error with no message, or a blank one, is named by its class")
    void noMessage() {
        assertEquals("IOException", RunExecution.plainly(new IOException((String) null)));
        assertEquals("IOException", RunExecution.plainly(new IOException("  ")));
        assertEquals("IllegalStateException", RunExecution.plainly(new IllegalStateException("")));
    }
}
