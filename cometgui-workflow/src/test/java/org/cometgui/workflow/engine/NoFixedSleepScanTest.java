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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * No engine test and no engine class waits a fixed time. A test waits on something observable -- a
 * transition, a console line, a process's exit, the clock passing a computed instant -- with an
 * explicit bound that is a failure, not a delay (Phase 03's rule, applied to this package).
 *
 * <p>The fake program's own polling ({@code src/test/resources/enginefakes}) is not scanned: it is
 * the stand-in for a tool, and its {@code hold} scenario's duration is the behaviour under test.
 */
class NoFixedSleepScanTest {

    static final Pattern SLEEP = Pattern.compile("Thread\\s*\\.\\s*sleep\\s*\\(|\\.sleep\\s*\\(");

    @Test
    void theScanSeesASleepWhenThereIsOne() {
        assertTrue(SLEEP.matcher("        Thread.sleep(100);").find());
        assertTrue(SLEEP.matcher("TimeUnit.SECONDS.sleep(1);").find());
        assertFalse(SLEEP.matcher("LockSupport.parkUntil(deadline);").find());
    }

    @Test
    void noEngineSourceSleeps() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (String root :
                List.of(
                        "src/main/java/org/cometgui/workflow/engine",
                        "src/test/java/org/cometgui/workflow/engine")) {
            try (Stream<Path> files = Files.list(Path.of(root))) {
                files.filter(file -> file.toString().endsWith(".java")).forEach(sources::add);
            }
        }
        assertTrue(sources.size() >= 40, () -> "too few sources scanned: " + sources);
        List<String> offenders = new ArrayList<>();
        for (Path source : sources) {
            if (source.endsWith("NoFixedSleepScanTest.java")) {
                continue;
            }
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size(); index++) {
                if (SLEEP.matcher(lines.get(index)).find()) {
                    offenders.add(source + ":" + (index + 1));
                }
            }
        }
        assertEquals(List.of(), offenders);
    }
}
