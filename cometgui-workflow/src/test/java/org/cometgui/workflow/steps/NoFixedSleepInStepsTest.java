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

package org.cometgui.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No step and no step test waits a fixed time: a real run is waited on through its handle with a
 * bound that fails, and a running Comet through the line it prints (the engine's rule, applied to
 * this package).
 */
class NoFixedSleepInStepsTest {

    static final Pattern SLEEP = Pattern.compile("Thread\\s*\\.\\s*sleep\\s*\\(|\\.sleep\\s*\\(");

    @Test
    @DisplayName("the scan sees a sleep when there is one")
    void theScanSeesASleep() {
        assertTrue(SLEEP.matcher("        Thread.sleep(100);").find());
        assertTrue(SLEEP.matcher("TimeUnit.SECONDS.sleep(1);").find());
    }

    @Test
    @DisplayName("no source of the steps package or its tests sleeps")
    void noStepSourceSleeps() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (String root :
                List.of(
                        "src/main/java/org/cometgui/workflow/steps",
                        "src/test/java/org/cometgui/workflow/steps")) {
            try (Stream<Path> files = Files.list(Path.of(root))) {
                files.filter(file -> file.toString().endsWith(".java")).forEach(sources::add);
            }
        }
        assertTrue(sources.size() >= 30, () -> "too few sources scanned: " + sources);
        List<String> offenders = new ArrayList<>();
        for (Path source : sources) {
            if (source.endsWith("NoFixedSleepInStepsTest.java")) {
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
