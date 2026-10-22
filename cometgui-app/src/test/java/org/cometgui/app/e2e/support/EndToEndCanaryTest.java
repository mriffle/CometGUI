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

package org.cometgui.app.e2e.support;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.cometgui.app.config.ApplicationServices;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The canary of the end-to-end tier: the smallest class that carries {@link EndToEnd}, so that the
 * opt-in and the locale assertion can be seen working on a real surefire run before any scenario
 * depends on them. Run three ways (Phase 14 unit 1):
 *
 * <ul>
 *   <li>without {@code -Dcometgui.e2e=true}: skipped, with {@link EndToEndExtension}'s reason in
 *       the surefire report;
 *   <li>with it, under {@code LC_ALL=C.UTF-8}: runs, and its two tests pass;
 *   <li>with it and no locale exported (this build host's default): fails before either test, on
 *       the locale assertion, naming {@code sun.jnu.encoding=ANSI_X3.4-1968} and the remedy.
 * </ul>
 *
 * <p>What it asserts is real work, not a placeholder: a fresh home under a path with a space and an
 * accented letter, a production composition root that puts the application data under it, a file
 * written and read back by that non-ASCII name, and no tool present.
 */
@EndToEnd
class EndToEndCanaryTest {

    @TempDir private Path parent;

    @Test
    @DisplayName("this JVM can represent a non-ASCII path, as the locale assertion promised")
    void theLocaleHolds() {
        RunnerLocale here = RunnerLocale.ofThisJvm();
        Path accented = Path.of(parent.toString(), FreshHome.NON_ASCII_SEGMENT, "x.mzML");
        assertAll(
                () -> assertTrue(here.isUtf8(), here::describe),
                () -> assertTrue(accented.toString().contains("protéomique run")));
    }

    @Test
    @DisplayName(
            "a fresh home under \"protéomique run\": the application data goes under it, a file"
                    + " round trips by its name, and no tool is present")
    void aFreshNonAsciiHome() throws IOException {
        FreshHome fresh = FreshHome.create(parent, FreshHome.Naming.SPACE_AND_NON_ASCII);
        ApplicationServices services = fresh.services();
        Path data = services.fileSystem().applicationDataDirectory();
        Files.createDirectories(data);
        Path written = Files.writeString(data.resolve("café.txt"), "été", StandardCharsets.UTF_8);
        List<String> names;
        try (Stream<Path> listed = Files.list(parent)) {
            names = listed.map(path -> path.getFileName().toString()).toList();
        }
        assertAll(
                () -> assertEquals(fresh.applicationDataDirectory(), data),
                () -> assertTrue(data.startsWith(fresh.home()), data::toString),
                () -> assertEquals(List.of("protéomique run"), names),
                () -> assertEquals("été", Files.readString(written, StandardCharsets.UTF_8)),
                () -> assertEquals(6, fresh.assertNoToolPresent().size()));
    }
}
