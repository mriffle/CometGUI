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

package org.cometgui.ui.controls.provenance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import org.cometgui.ui.controls.AccessibleControls;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.testing.Editors;
import org.cometgui.ui.testing.FxToolkit;
import org.cometgui.ui.testing.ProvenanceViews;
import org.cometgui.ui.testing.ScriptedProvenance;
import org.cometgui.ui.viewmodel.provenance.ProvenanceViewModel;
import org.cometgui.workflow.provenance.DiskOutcome;
import org.cometgui.workflow.provenance.DiskVerification;
import org.cometgui.workflow.provenance.RunCondition;
import org.cometgui.workflow.provenance.RunListing;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Provenance pane shows its view-model and acts only through it: the run selector chooses
 * through the view-model; the parts follow it; each row action is disabled until a row is selected
 * and then acts on that row -- a copy putting exactly the recorded value on the clipboard; the
 * exports, Replace and Verify follow the view-model's switches; and every identified control has a
 * name of its own.
 */
class ProvenancePaneTest {

    private static final RunListing COMPLETED =
            ProvenanceViews.listing("20261014T080000Z-r1", RunCondition.COMPLETED, true);

    private static final RunListing CANCELLED =
            ProvenanceViews.listing("20261014T090000Z-r2", RunCondition.CANCELLED, true);

    private ScriptedProvenance port;

    private Editors.ScriptedChooser chooser;

    private ProvenanceViewModel section;

    private Scene scene;

    @BeforeAll
    static void toolkit() throws InterruptedException {
        FxToolkit.start();
    }

    @BeforeEach
    void build() throws InterruptedException {
        port =
                new ScriptedProvenance()
                        .runs(() -> List.of(COMPLETED, CANCELLED))
                        .opens(
                                new ScriptedProvenance.Record(
                                        COMPLETED,
                                        ProvenanceViews.failedRun(),
                                        Optional.empty(),
                                        true));
        chooser = new Editors.ScriptedChooser();
        section = new ProvenanceViewModel(port, chooser, Runnable::run, Runnable::run);
        FxToolkit.onFxThread(
                () -> {
                    scene = new Scene(new ProvenancePane(section), 1000, 800);
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                });
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T node(String id) {
        Node found = scene.lookup("#" + id);
        assertNotNull(found, "#" + id + " is in the pane");
        return (T) found;
    }

    private void fire(String id) throws InterruptedException {
        FxToolkit.onFxThread(() -> this.<Button>node(id).fire());
    }

    private void select(String id, int index) throws InterruptedException {
        FxToolkit.onFxThread(
                () -> this.<ListView<String>>node(id).getSelectionModel().select(index));
    }

    private String clipboard() throws InterruptedException {
        return FxToolkit.callOnFxThread(() -> Clipboard.getSystemClipboard().getString());
    }

    @Test
    @DisplayName("nothing is shown before the runs are read; then the selector chooses a run")
    void theRunSelector() throws InterruptedException {
        ComboBox<String> run = node(UiIds.PROVENANCE_RUN);
        assertAll(
                () -> assertEquals(List.of(), run.getItems()),
                () ->
                        assertEquals(
                                ProvenanceViewModel.RUNS_NOT_READ,
                                this.<Label>node(UiIds.PROVENANCE_RUNS_STATUS).getText()),
                () ->
                        assertEquals(
                                ProvenanceViewModel.NO_RUN,
                                this.<Label>node(UiIds.PROVENANCE_RECORD).getText()),
                () -> assertTrue(this.<Button>node(UiIds.PROVENANCE_EXPORT_JSON).isDisabled()),
                () -> assertTrue(this.<Button>node(UiIds.PROVENANCE_VERIFY).isDisabled()),
                () -> assertEquals(List.of(), port.calls()));
        fire(UiIds.PROVENANCE_REFRESH);
        assertEquals(
                List.of(
                        "r1 -- created 2026-10-14T08:00:00Z, completed, sealed record",
                        "r2 -- created 2026-10-14T08:00:00Z, cancelled, sealed record"),
                FxToolkit.callOnFxThread(() -> List.copyOf(run.getItems())));
        FxToolkit.onFxThread(() -> run.getSelectionModel().select(1));
        assertAll(
                () -> assertEquals(1, section.chosenIndexProperty().get()),
                () ->
                        assertEquals(
                                "20261014T090000Z-r2",
                                port.calls().get(port.calls().size() - 1).argument()),
                () ->
                        assertTrue(
                                this.<Label>node(UiIds.PROVENANCE_SUMMARY)
                                        .getText()
                                        .startsWith("Run r1 of project cometgui-default\n")),
                () ->
                        assertEquals(
                                2,
                                this.<ListView<String>>node(UiIds.PROVENANCE_TOOLS)
                                        .getItems()
                                        .size()),
                () ->
                        assertTrue(
                                this.<ListView<String>>node(UiIds.PROVENANCE_FILES)
                                        .getItems()
                                        .get(1)
                                        .startsWith("PARTIAL -- output pepxml\n")),
                () ->
                        assertEquals(
                                "# comet_version 2026.03.0\nnum_threads = 4\n",
                                this.<TextArea>node(UiIds.PROVENANCE_COMET_PARAMS).getText()),
                () -> assertFalse(this.<TextArea>node(UiIds.PROVENANCE_COMET_PARAMS).isEditable()),
                () -> assertFalse(this.<Button>node(UiIds.PROVENANCE_EXPORT_JSON).isDisabled()),
                () -> assertFalse(this.<Button>node(UiIds.PROVENANCE_EXPORT_RST).isDisabled()),
                () -> assertFalse(this.<Button>node(UiIds.PROVENANCE_VERIFY).isDisabled()),
                () -> assertTrue(this.<Button>node(UiIds.PROVENANCE_VERIFY_CANCEL).isDisabled()),
                () -> assertTrue(this.<Button>node(UiIds.PROVENANCE_EXPORT_REPLACE).isDisabled()));

        int calls = port.calls().size();
        FxToolkit.onFxThread(section::refresh);
        assertAll(
                () ->
                        assertEquals(
                                1,
                                FxToolkit.callOnFxThread(
                                        run.getSelectionModel()::getSelectedIndex)),
                () ->
                        assertEquals(
                                calls + 1,
                                port.calls().size(),
                                "moving the selector to the chosen run does not choose it again"));
    }

    @Test
    @DisplayName(
            "each row action waits for a selection, then copies exactly the recorded value or"
                    + " acts on that row")
    void rowActions() throws InterruptedException {
        FxToolkit.onFxThread(
                () -> {
                    section.refresh();
                    section.choose(0);
                });
        for (String id :
                List.of(
                        UiIds.PROVENANCE_TOOL_COPY_MD5,
                        UiIds.PROVENANCE_TOOL_COPY_SHA256,
                        UiIds.PROVENANCE_TOOL_OPEN_LOCATION,
                        UiIds.PROVENANCE_FILE_COPY_MD5,
                        UiIds.PROVENANCE_FILE_COPY_SHA256,
                        UiIds.PROVENANCE_FILE_OPEN_LOCATION,
                        UiIds.PROVENANCE_EXECUTION_COPY_COMMAND,
                        UiIds.PROVENANCE_LOG_OPEN_LOCATION)) {
            assertTrue(
                    this.<Button>node(id).isDisabled(), id + " is disabled with no row selected");
        }
        select(UiIds.PROVENANCE_TOOLS, 0);
        assertFalse(this.<Button>node(UiIds.PROVENANCE_TOOL_COPY_SHA256).isDisabled());
        fire(UiIds.PROVENANCE_TOOL_COPY_SHA256);
        assertEquals(ProvenanceViews.COMET_SHA256, clipboard());
        assertEquals(
                "Copied the SHA-256 of comet 2026.03.0's executable: "
                        + ProvenanceViews.COMET_SHA256,
                this.<Label>node(UiIds.PROVENANCE_ACTION_STATUS).getText());
        select(UiIds.PROVENANCE_TOOLS, 1);
        fire(UiIds.PROVENANCE_TOOL_COPY_MD5);
        assertEquals(ProvenanceViews.PERCOLATOR_MD5, clipboard());
        select(UiIds.PROVENANCE_FILES, 1);
        fire(UiIds.PROVENANCE_FILE_COPY_SHA256);
        assertEquals(ProvenanceViews.PEPXML_SHA256, clipboard());
        select(UiIds.PROVENANCE_FILES, 0);
        fire(UiIds.PROVENANCE_FILE_COPY_MD5);
        assertEquals(ProvenanceViews.SPECTRUM_MD5, clipboard());
        select(UiIds.PROVENANCE_EXECUTIONS, 0);
        fire(UiIds.PROVENANCE_EXECUTION_COPY_COMMAND);
        assertEquals(ProvenanceViews.COMET_COMMAND, clipboard());

        port.locates(() -> "started");
        fire(UiIds.PROVENANCE_TOOL_OPEN_LOCATION);
        fire(UiIds.PROVENANCE_FILE_OPEN_LOCATION);
        select(UiIds.PROVENANCE_LOGS, 0);
        fire(UiIds.PROVENANCE_LOG_OPEN_LOCATION);
        assertEquals(
                List.of(
                        "cache/percolator",
                        ProvenanceViews.SPECTRUM.toString(),
                        ProvenanceViews.COMET_LOG.toString()),
                port.calls().stream()
                        .filter(call -> call.method().equals("openLocation"))
                        .map(ScriptedProvenance.Call::argument)
                        .toList());
        assertEquals("started", this.<Label>node(UiIds.PROVENANCE_ACTION_STATUS).getText());
    }

    @Test
    @DisplayName("an export refused for an existing file offers Replace; Verify and its rows")
    void exportsAndVerify() throws InterruptedException {
        FxToolkit.onFxThread(
                () -> {
                    section.refresh();
                    section.choose(0);
                });
        Path target = Path.of("exports/r1.json");
        chooser.saveTo(target);
        port.exports(
                () -> {
                    throw new java.nio.file.FileAlreadyExistsException(
                            target.toString(), null, "a file already exists here");
                });
        fire(UiIds.PROVENANCE_EXPORT_JSON);
        assertFalse(this.<Button>node(UiIds.PROVENANCE_EXPORT_REPLACE).isDisabled());
        port.exports(() -> target);
        fire(UiIds.PROVENANCE_EXPORT_REPLACE);
        assertAll(
                () -> assertTrue(this.<Button>node(UiIds.PROVENANCE_EXPORT_REPLACE).isDisabled()),
                () ->
                        assertEquals(
                                "Exported provenance JSON to exports/r1.json.",
                                this.<Label>node(UiIds.PROVENANCE_EXPORT_STATUS).getText()));
        chooser.saveTo(Path.of("exports/r1.rst"));
        port.exports(() -> Path.of("exports/r1.rst"));
        fire(UiIds.PROVENANCE_EXPORT_RST);
        assertEquals(
                "Exported provenance RST to exports/r1.rst.",
                this.<Label>node(UiIds.PROVENANCE_EXPORT_STATUS).getText());

        port.verifies(
                () ->
                        new DiskVerification(
                                List.of(
                                        new DiskVerification.Check(
                                                "input",
                                                "spectrum",
                                                "/data/a.mzML",
                                                ProvenanceViews.SPECTRUM,
                                                DiskOutcome.MATCHES,
                                                ProvenanceViews.SPECTRUM_MD5,
                                                ProvenanceViews.SPECTRUM_SHA256,
                                                Optional.of(ProvenanceViews.SPECTRUM_MD5),
                                                Optional.of(ProvenanceViews.SPECTRUM_SHA256),
                                                Optional.empty())),
                                true));
        fire(UiIds.PROVENANCE_VERIFY);
        assertAll(
                () ->
                        assertEquals(
                                List.of("MATCHES -- input spectrum: /data/a.mzML"),
                                this.<ListView<String>>node(UiIds.PROVENANCE_VERIFICATION)
                                        .getItems()),
                () ->
                        assertEquals(
                                "Every file the record lists matches its record on disk: 1 file"
                                        + " checked.",
                                this.<Label>node(UiIds.PROVENANCE_VERIFY_STATUS).getText()));
    }

    @Test
    @DisplayName("every identified control and every shown row has a name of its own")
    void everyControlIsNamed() throws InterruptedException {
        FxToolkit.onFxThread(
                () -> {
                    section.refresh();
                    section.choose(0);
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                });
        List<String> unnamed = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        FxToolkit.onFxThread(() -> walk(scene.getRoot(), unnamed, rows));
        assertEquals(List.of(), unnamed, "identified controls with no name of their own");
        assertTrue(
                rows.contains(
                        "PARTIAL -- output pepxml\n"
                                + "Status: partial (incomplete: the stage that wrote it did not"
                                + " complete)\n"
                                + "Path: /run/outputs/comet/a.pep.xml\n"
                                + "Size: 42 bytes; modified 2026-10-14T08:00:05Z\n"
                                + "MD5: "
                                + ProvenanceViews.PEPXML_MD5
                                + "\nSHA-256: "
                                + ProvenanceViews.PEPXML_SHA256),
                () -> "a row is read out as the text it shows: " + rows);
        long identified =
                FxToolkit.callOnFxThread(
                        () ->
                                scene.getRoot().lookupAll("*").stream()
                                        .filter(node -> node instanceof Control)
                                        .map(Node::getId)
                                        .filter(id -> id != null && id.startsWith("provenance-"))
                                        .count());
        assertEquals(35, identified, "every one of the section's 35 identifiers is in the scene");
    }

    private static void walk(Node node, List<String> unnamed, List<String> rows) {
        if (node instanceof ListCell<?> cell && cell.getItem() != null) {
            rows.add(cell.getAccessibleText());
        }
        // ComboBoxListViewSkin identifies the list it builds for the run selector's choices as
        // "list-view": the one skin-built control that carries an identifier (as
        // AccessibleNameEnumerationUiTest's SKIN_IDENTIFIERS records). It is named by the fallback.
        if (node instanceof Control control
                && control.getId() != null
                && !control.getId().equals("list-view")
                && (control.getAccessibleText() == null
                        || control.getAccessibleText().isBlank()
                        || AccessibleControls.hasGeneratedName(control))) {
            unnamed.add(control.getId());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                walk(child, unnamed, rows);
            }
        }
    }
}
