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

package org.cometgui.ui.controls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.ui.viewmodel.SectionId;
import org.cometgui.workflow.state.WorkflowStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The stable identifiers: their exact spelling, and the property the whole scheme rests on -- that
 * no two controls get the same one.
 *
 * <p>A duplicate identifier does not fail: {@code Scene.lookup} returns whichever node it reaches
 * first, so a test would quietly assert against the wrong control. That is the failure this class
 * exists to make impossible, and it is why the identifiers are derived from the model's own
 * identifiers rather than typed out.
 */
class UiIdsTest {

    @Test
    @DisplayName("section identifiers are built from SectionId.id()")
    void sectionIdentifiersComeFromTheModel() {
        assertEquals("section-comet-parameters", UiIds.sectionPane(SectionId.COMET_PARAMETERS));
        assertEquals(
                "section-comet-parameters-heading",
                UiIds.sectionHeading(SectionId.COMET_PARAMETERS));
        assertEquals(
                "section-comet-parameters-description",
                UiIds.sectionDescription(SectionId.COMET_PARAMETERS));
        assertEquals(
                "section-comet-parameters-note", UiIds.sectionNote(SectionId.COMET_PARAMETERS));
        assertEquals("nav-comet-parameters", UiIds.navigationEntry(SectionId.COMET_PARAMETERS));
        assertEquals("section-visualisation", UiIds.sectionPane(SectionId.VISUALISATION));
    }

    @Test
    @DisplayName("stepper identifiers are built from WorkflowStage.id()")
    void stepperIdentifiersComeFromTheModel() {
        assertEquals("stage-limelight-xml", UiIds.stepperStage(WorkflowStage.LIMELIGHT_XML));
        assertEquals(
                "stage-limelight-xml-name", UiIds.stepperStageName(WorkflowStage.LIMELIGHT_XML));
        assertEquals(
                "stage-limelight-xml-state", UiIds.stepperStageState(WorkflowStage.LIMELIGHT_XML));
        assertEquals(
                "stage-arrow-results-limelight-xml",
                UiIds.stepperArrow(WorkflowStage.RESULTS, WorkflowStage.LIMELIGHT_XML));
        assertEquals("stage-branch-pdv", UiIds.stepperBranch(WorkflowStage.PDV));
        assertEquals("stage-branch-pdv-from", UiIds.stepperBranchOrigin(WorkflowStage.PDV));
    }

    @Test
    @DisplayName("console filter identifiers are built from the stage and the severity")
    void consoleFilterIdentifiersComeFromTheModel() {
        assertEquals(
                "console-stage-filter-percolator",
                UiIds.consoleStageFilter(WorkflowStage.PERCOLATOR));
        assertEquals(
                "console-severity-filter-stderr",
                UiIds.consoleSeverityFilter(MessageSeverity.STDERR));
        assertEquals("console-stage-filter-all", UiIds.CONSOLE_STAGE_FILTER_ALL);
    }

    @Test
    @DisplayName("no two identifiers collide")
    void noTwoIdentifiersCollide() {
        Set<String> seen = new HashSet<>();
        List<String> all = allIdentifiers();
        for (String id : all) {
            assertTrue(seen.add(id), "duplicate stable identifier: " + id);
            assertTrue(
                    id.equals(id.toLowerCase(Locale.ROOT)) && !id.contains(" "),
                    "identifiers are lower case and unspaced: " + id);
        }
        assertEquals(
                all.size(), seen.size(), "every identifier the interface sets must be distinct");
        assertTrue(
                all.size() >= 80, "the scheme covers every control, not a handful: " + all.size());
    }

    @Test
    @DisplayName("a null argument is rejected rather than producing the identifier \"null\"")
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> UiIds.sectionPane(null));
        assertThrows(NullPointerException.class, () -> UiIds.navigationEntry(null));
        assertThrows(NullPointerException.class, () -> UiIds.stepperStage(null));
        assertThrows(NullPointerException.class, () -> UiIds.stepperArrow(WorkflowStage.PDV, null));
        assertThrows(NullPointerException.class, () -> UiIds.consoleStageFilter(null));
        assertThrows(NullPointerException.class, () -> UiIds.consoleSeverityFilter(null));
    }

    /** Every identifier the interface can set, constants and derived alike. */
    private static List<String> allIdentifiers() {
        List<String> all = new ArrayList<>();
        all.addAll(
                List.of(
                        UiIds.SHELL_ROOT,
                        UiIds.SHELL_HEADER,
                        UiIds.SHELL_TITLE,
                        UiIds.SHELL_SECTION_TITLE,
                        UiIds.HOST_BASELINE_BANNER,
                        UiIds.NAVIGATION,
                        UiIds.NAVIGATION_SEPARATOR,
                        UiIds.CONTENT,
                        UiIds.STAGE_STEPPER,
                        UiIds.STAGE_STEPPER_CORE,
                        UiIds.STAGE_STEPPER_BRANCHES,
                        UiIds.STAGE_STEPPER_RUN_STATE,
                        UiIds.CONSOLE_PANE,
                        UiIds.CONSOLE_TITLE,
                        UiIds.CONSOLE_OUTPUT,
                        UiIds.CONSOLE_SUMMARY,
                        UiIds.CONSOLE_FILTERS,
                        UiIds.CONSOLE_STAGE_FILTER,
                        UiIds.CONSOLE_STAGE_FILTER_ALL,
                        UiIds.CONSOLE_SEVERITY_FILTER,
                        UiIds.CONSOLE_CLEAR,
                        UiIds.CONSOLE_COPY));
        for (SectionId section : SectionId.displayOrder()) {
            all.add(UiIds.sectionPane(section));
            all.add(UiIds.sectionHeading(section));
            all.add(UiIds.sectionDescription(section));
            all.add(UiIds.sectionNote(section));
            all.add(UiIds.navigationEntry(section));
        }
        for (WorkflowStage stage : WorkflowStage.values()) {
            all.add(UiIds.stepperStage(stage));
            all.add(UiIds.stepperStageName(stage));
            all.add(UiIds.stepperStageState(stage));
            all.add(UiIds.consoleStageFilter(stage));
            for (WorkflowStage predecessor : stage.predecessors()) {
                all.add(UiIds.stepperArrow(predecessor, stage));
            }
        }
        for (List<WorkflowStage> branch : WorkflowStage.downstreamBranches()) {
            all.add(UiIds.stepperBranch(branch.get(0)));
            all.add(UiIds.stepperBranchOrigin(branch.get(0)));
        }
        for (MessageSeverity severity : MessageSeverity.values()) {
            all.add(UiIds.consoleSeverityFilter(severity));
        }
        return List.copyOf(all);
    }
}
