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

package org.cometgui.app.gui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code R-PARAM-13} through the launched application: "A configuration migrated between releases
 * is shown as a reviewable report, and an entry needing the scientist's attention blocks a run
 * until resolved" (decision P7-3).
 *
 * <h2>The fixture</h2>
 *
 * <p>{@code variable-mod01-terminus-4-distance-2-2026.02.2.params}, beside this class, is
 * CONSTRUCTED: it is Comet 2026.02.2's own {@code comet -q} file (the checked-in fixture {@code
 * cometgui-params-comet/src/test/resources/fixtures/comet/2026.02.2/linux-x86-64/comet-q.params},
 * SHA-256 {@code d15048709f485c09a840dcb2a384dc301b4ba4da4566c2ea053e9c17eae58f51}) with exactly
 * one line edited: {@code variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0} became {@code variable_mod01 =
 * 15.9949 M 0 3 2 4 0 0.0} -- terminus 4 at a distance of 2, which 2026.02.2 accepts and never
 * applies and 2026.03.0 refuses. {@link #theFixtureIsTheRealFileWithOneLineEdited()} holds it to
 * that, against the copy the parameter model bundles.
 *
 * <h2>The path</h2>
 *
 * <p>Imported through the injected chooser into an editor set to Comet 2026.03.0, the file is
 * offered for migration with both releases named; migrated, the review lists its two changes and
 * marks {@code variable_mod01} as blocking. Run is disabled with that reason, the validation
 * summary lists it at {@code variable_mod01}, and the keyboard reaches it there; accepting the
 * value lifts the block. Every expected text is typed out.
 */
class MigrationReviewBlocksRunUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-05T00:00:00Z"));

    private static final String FIXTURE = "variable-mod01-terminus-4-distance-2-2026.02.2.params";

    /** The model's reason, typed out: why 2026.03.0 cannot hold the source value. */
    private static final String TERMINUS_REASON =
            "With a terminal distance of 0 or more, Comet 2026.02.2 matches no terminus outside"
                    + " 0-3, so this modification was never applied (searches gave the same"
                    + " results as with the slot switched off), and Comet 2026.03.0 refuses the"
                    + " setting. Choose the terminus that was meant (0 protein N, 1 protein C, 2"
                    + " peptide N, 3 peptide C), or switch the slot off."
                    + " (https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearch.cpp"
                    + "#L5373-L5390)";

    /** The finding the review adds at variable_mod01, typed out. */
    private static final String NEEDS_DECISION =
            "variable_mod01 needs your decision: migrating from Comet 2026.02.2 to Comet"
                    + " 2026.03.0 could not keep its value 15.9949 M 0 3 2 4 0 0.0 and put"
                    + " Comet 2026.03.0's default 15.9949 M 0 3 -1 0 0 0.0 in its place; the"
                    + " set now holds 15.9949 M 0 3 -1 0 0 0.0. Set a value, or accept this"
                    + " one, before running. Why: variable_mod01 = 15.9949 M 0 3 2 4 0 0.0"
                    + " (Comet 2026.02.2) has no equivalent in Comet 2026.03.0: "
                    + TERMINUS_REASON
                    + "; the migrated set holds Comet 2026.03.0's default instead, and the"
                    + " source value is kept only in this report";

    private static final String SUMMARY_ENTRY =
            "Error -- Variable modification 1 (variable_mod01), Variable modifications: "
                    + NEEDS_DECISION;

    private static ParameterEditorApp app;

    private static FxUiDriver driver;

    @TempDir private static Path directory;

    @BeforeAll
    static void launch() {
        app = ParameterEditorApp.launch(BUILD);
        driver = new TestFxUiDriver(app.application());
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    @DisplayName("the fixture is the real 2026.02.2 -q file with exactly line 53 edited")
    void theFixtureIsTheRealFileWithOneLineEdited() throws IOException {
        List<String> real =
                new String(
                                ReleaseDefaults.bundledFile(ToolVersion.parse("2026.02.2")),
                                StandardCharsets.UTF_8)
                        .lines()
                        .toList();
        List<String> fixture = new String(fixture(), StandardCharsets.UTF_8).lines().toList();
        assertEquals(real.size(), fixture.size());
        List<String> differing = new ArrayList<>();
        for (int index = 0; index < real.size(); index++) {
            if (!real.get(index).equals(fixture.get(index))) {
                differing.add((index + 1) + ": " + real.get(index) + " => " + fixture.get(index));
            }
        }
        assertEquals(
                List.of(
                        "53: variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0"
                                + " => variable_mod01 = 15.9949 M 0 3 2 4 0 0.0"),
                differing);
    }

    @Test
    @DisplayName("a migrated NEEDS_ATTENTION entry blocks Run until it is accepted")
    void aMigratedEntryNeedingAttentionBlocksRun() throws IOException {
        Path file = directory.resolve("older-search.params");
        Files.write(file, fixture());
        ParameterEditorApp.openEditor(driver);
        driver.clickOn("param-mode-essentials");
        assertFalse(driver.isVisible("param-migration"), "nothing is under review yet");
        // A value set by hand, so that the waiting offer can be seen to change nothing.
        ParameterEditorApp.enter(driver, "ess-num_threads", "4");

        // Import: the file names another release, so the choice is offered, both releases named.
        app.chooser().parameterFile(file);
        driver.clickOn("param-import");
        String question =
                file
                        + " was written for Comet 2026.02.2; the editor is set to Comet 2026.03.0."
                        + " Migrate it to Comet 2026.03.0 with a reviewable report, switch the"
                        + " editor to Comet 2026.02.2, or read it as Comet 2026.03.0 as it is.";
        assertAll(
                "the offer",
                () -> assertTrue(driver.isVisible("param-import-offer")),
                () -> assertEquals(question, driver.textOf("param-import-question")),
                () ->
                        assertEquals(
                                "Not imported yet: choose how to read it.\n" + question,
                                driver.textOf("param-import-status")),
                () -> assertEquals(List.of("parameter file"), app.chooser().asked()),
                () -> assertEquals("4", driver.textOf("ess-num_threads"), "nothing imported yet"),
                () ->
                        assertEquals(
                                "Value from: Set by you", driver.textOf("adv-num_threads-origin")),
                () ->
                        assertEquals(
                                "Comet 2026.03.0 (default)",
                                ParameterEditorApp.comboText(driver, "param-release")));

        driver.clickOn("param-import-migrate");
        assertAll(
                "migrated, under review",
                () -> assertFalse(driver.isVisible("param-import-offer")),
                () ->
                        assertEquals(
                                "Migrated to Comet 2026.03.0; review the changes below before"
                                        + " running:\nComet 2026.02.2 -> 2026.03.0: 118"
                                        + " parameters, 2 changes, 1 needing attention",
                                driver.textOf("param-import-status")),
                () -> assertTrue(driver.isVisible("param-migration")),
                () ->
                        assertEquals(
                                "Migration review: Comet 2026.02.2 -> 2026.03.0: 118 parameters,"
                                        + " 2 changed, 116 unchanged; 1 needs your decision.",
                                driver.textOf("param-migration-headline")),
                () ->
                        assertTrue(
                                driver.textOf("param-migration-row-0")
                                        .startsWith(
                                                "Converted -- Index type for an index built on"
                                                        + " demand (index_search_type): was "),
                                driver.textOf("param-migration-row-0")),
                () ->
                        assertEquals(
                                "No decision needed.",
                                driver.textOf("param-migration-row-0-state")),
                () ->
                        assertTrue(
                                driver.textOf("param-migration-row-1")
                                        .startsWith(
                                                "Needs your decision -- Variable modification 1"
                                                        + " (variable_mod01): was 15.9949 M 0 3 2"
                                                        + " 4 0 0.0, now 15.9949 M 0 3 -1 0 0"
                                                        + " 0.0. "),
                                driver.textOf("param-migration-row-1")),
                () ->
                        assertEquals(
                                "Blocks the run until you decide.",
                                driver.textOf("param-migration-row-1-state")),
                () -> assertTrue(driver.isVisible("param-migration-row-1-accept")),
                () -> assertTrue(driver.isVisible("param-migration-row-1-goto")),
                () ->
                        assertFalse(
                                ParameterEditorApp.exists(driver, "param-migration-row-0-accept")));

        // The summary lists it at variable_mod01, and Run is blocked with that reason.
        assertEquals(
                "Validation: 1 error and 0 warnings.", driver.textOf("param-summary-headline"));
        assertEquals(SUMMARY_ENTRY, driver.textOf("param-summary-entry-0"));
        driver.clickOn("nav-run");
        assertAll(
                "the Run section",
                () -> assertTrue(isDisabled("run-start"), "Run is disabled"),
                () ->
                        assertEquals(
                                "The parameters block a run:\n" + SUMMARY_ENTRY,
                                driver.textOf("run-parameters")));

        // The keyboard reaches the entry, and Enter moves to the slot's field.
        reachByTab("param-summary-entry-0");
        driver.press(KeyCode.ENTER);
        assertEquals("ess-variable_mod01-part-mass", driver.focusedNodeId());
        // So does the review's own "go to the field".
        driver.clickOn("param-migration-row-1-goto");
        assertEquals("ess-variable_mod01-part-mass", driver.focusedNodeId());

        // Accepting the value lifts the block.
        driver.clickOn("param-migration-row-1-accept");
        assertAll(
                "accepted",
                () ->
                        assertEquals(
                                "Resolved: you accepted the value it holds.",
                                driver.textOf("param-migration-row-1-state")),
                () ->
                        assertFalse(
                                ParameterEditorApp.exists(driver, "param-migration-row-1-accept")),
                () ->
                        assertEquals(
                                "Accepted the value of Variable modification 1 (variable_mod01);"
                                        + " it no longer blocks the run.",
                                driver.textOf("param-migration-status")),
                () ->
                        assertEquals(
                                "Migration review: Comet 2026.02.2 -> 2026.03.0: 118 parameters,"
                                        + " 2 changed, 116 unchanged; 0 need your decision.",
                                driver.textOf("param-migration-headline")),
                () ->
                        assertEquals(
                                "Validation: No errors or warnings.",
                                driver.textOf("param-summary-headline")),
                () -> assertTrue(driver.isVisible("param-migration"), "the review stays"));
        driver.clickOn("nav-run");
        assertEquals("The parameters do not block a run.", driver.textOf("run-parameters"));
    }

    private static byte[] fixture() throws IOException {
        try (InputStream in = MigrationReviewBlocksRunUiTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(in, FIXTURE + " is checked in beside this test");
            return in.readAllBytes();
        }
    }

    private static void reachByTab(String entryId) {
        driver.clickOn("nav-comet-parameters");
        List<String> visited = new ArrayList<>();
        for (int press = 0; press < 40 && !entryId.equals(driver.focusedNodeId()); press++) {
            driver.tab();
            visited.add(driver.focusedNodeId());
        }
        assertEquals(
                entryId,
                driver.focusedNodeId(),
                () -> "Tab never reached #" + entryId + "; it visited " + visited);
    }

    private static boolean isDisabled(String id) {
        Node node = driver.node(id);
        return driver.callOnFxThread(node::isDisabled);
    }
}
