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

package org.cometgui.workflow.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.domain.run.StageTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for {@link WorkflowStage}.
 *
 * <p>The point of most of these is that the declared edges really are the diagram in the
 * specification's <em>Information Architecture</em>. That is checked twice over and from two
 * directions: the edges are asserted one at a time as values, and the shape they make -- one path
 * from Inputs to Results, two branches off Results, no cycles, exactly one root -- is recomputed
 * here from {@link WorkflowStage#predecessors()} and compared with the drawing. An edge changed by
 * accident fails the first; an edge changed consistently but wrongly fails the second.
 */
class WorkflowStageTest {

    @Test
    @DisplayName("the eight stages are exactly the stepper's, in the order it draws them")
    void constantsAreTheEightStepperStages() {
        assertEquals(
                List.of(
                        WorkflowStage.INPUTS,
                        WorkflowStage.VALIDATE,
                        WorkflowStage.COMET,
                        WorkflowStage.PERCOLATOR,
                        WorkflowStage.RESULTS,
                        WorkflowStage.PDV,
                        WorkflowStage.LIMELIGHT_XML,
                        WorkflowStage.LIMELIGHT_UPLOAD),
                List.of(WorkflowStage.values()));
    }

    @Test
    @DisplayName("every stage has the stable identifier a provenance record will name")
    void identifiersAreTheStableOnes() {
        assertEquals(
                Map.ofEntries(
                        Map.entry(WorkflowStage.INPUTS, "inputs"),
                        Map.entry(WorkflowStage.VALIDATE, "validate"),
                        Map.entry(WorkflowStage.COMET, "comet"),
                        Map.entry(WorkflowStage.PERCOLATOR, "percolator"),
                        Map.entry(WorkflowStage.RESULTS, "results"),
                        Map.entry(WorkflowStage.PDV, "pdv"),
                        Map.entry(WorkflowStage.LIMELIGHT_XML, "limelight-xml"),
                        Map.entry(WorkflowStage.LIMELIGHT_UPLOAD, "limelight-upload")),
                mapOf(WorkflowStage::id));
    }

    @Test
    @DisplayName("every stage has the display name the stepper shows")
    void displayNamesAreTheOnesTheStepperShows() {
        assertEquals(
                Map.ofEntries(
                        Map.entry(WorkflowStage.INPUTS, "Inputs"),
                        Map.entry(WorkflowStage.VALIDATE, "Validate"),
                        Map.entry(WorkflowStage.COMET, "Comet"),
                        Map.entry(WorkflowStage.PERCOLATOR, "Percolator"),
                        Map.entry(WorkflowStage.RESULTS, "Results"),
                        Map.entry(WorkflowStage.PDV, "PDV"),
                        Map.entry(WorkflowStage.LIMELIGHT_XML, "Limelight XML"),
                        Map.entry(WorkflowStage.LIMELIGHT_UPLOAD, "Limelight Upload")),
                mapOf(WorkflowStage::displayName));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(WorkflowStage.class)
    @DisplayName("an identifier is usable in a file name and a test identifier: [a-z0-9-]+")
    void identifiersAreSafeForFilesAndTestIdentifiers(WorkflowStage stage) {
        assertTrue(
                stage.id().matches("[a-z0-9]+(-[a-z0-9]+)*"),
                () -> stage + " has an identifier that is not file-safe: '" + stage.id() + "'");
    }

    @Test
    @DisplayName("identifiers and display names are unique across the eight stages")
    void identifiersAndDisplayNamesAreUnique() {
        assertEquals(
                List.of(8, 8),
                List.of(
                        Set.copyOf(mapOf(WorkflowStage::id).values()).size(),
                        Set.copyOf(mapOf(WorkflowStage::displayName).values()).size()));
    }

    @Test
    @DisplayName("the declared predecessors are the diagram's edges, one at a time")
    void predecessorsAreTheDiagramsEdges() {
        Map<WorkflowStage, List<WorkflowStage>> edges = new LinkedHashMap<>();
        edges.put(WorkflowStage.INPUTS, List.of());
        edges.put(WorkflowStage.VALIDATE, List.of(WorkflowStage.INPUTS));
        edges.put(WorkflowStage.COMET, List.of(WorkflowStage.VALIDATE));
        edges.put(WorkflowStage.PERCOLATOR, List.of(WorkflowStage.COMET));
        edges.put(WorkflowStage.RESULTS, List.of(WorkflowStage.PERCOLATOR));
        edges.put(WorkflowStage.PDV, List.of(WorkflowStage.RESULTS));
        edges.put(WorkflowStage.LIMELIGHT_XML, List.of(WorkflowStage.RESULTS));
        edges.put(WorkflowStage.LIMELIGHT_UPLOAD, List.of(WorkflowStage.LIMELIGHT_XML));

        assertEquals(edges, mapOf(WorkflowStage::predecessors));
    }

    @Test
    @DisplayName("following predecessors from RESULTS reaches INPUTS through exactly the core path")
    void followingPredecessorsFromResultsWalksTheCorePath() {
        List<WorkflowStage> walked = new ArrayList<>();
        WorkflowStage stage = WorkflowStage.RESULTS;
        walked.add(stage);
        while (!stage.predecessors().isEmpty()) {
            WorkflowStage current = stage;
            assertEquals(
                    1,
                    current.predecessors().size(),
                    () ->
                            "the core path must be a chain, but "
                                    + current
                                    + " has a fork behind it");
            stage = current.predecessors().get(0);
            walked.add(stage);
        }
        assertEquals(
                List.of(
                        WorkflowStage.RESULTS,
                        WorkflowStage.PERCOLATOR,
                        WorkflowStage.COMET,
                        WorkflowStage.VALIDATE,
                        WorkflowStage.INPUTS),
                walked);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(WorkflowStage.class)
    @DisplayName("no stage is its own ancestor: the declared edges are acyclic")
    void noStageIsItsOwnAncestor(WorkflowStage stage) {
        assertFalse(
                ancestorsOf(stage).contains(stage),
                () -> stage + " reaches itself by following predecessors: the edges form a cycle");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(WorkflowStage.class)
    @DisplayName("every stage except INPUTS has at least one predecessor, and INPUTS has none")
    void inputsIsTheOnlyRoot(WorkflowStage stage) {
        assertEquals(
                stage != WorkflowStage.INPUTS,
                !stage.predecessors().isEmpty(),
                () -> stage + " has predecessors " + stage.predecessors());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = WorkflowStage.class,
            names = {"INPUTS"},
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("every other stage is reachable from INPUTS by following predecessors back")
    void everyOtherStageDescendsFromInputs(WorkflowStage stage) {
        assertTrue(
                ancestorsOf(stage).contains(WorkflowStage.INPUTS),
                () ->
                        stage
                                + " does not descend from INPUTS; its ancestors are "
                                + ancestorsOf(stage));
    }

    @Test
    @DisplayName("the core path is the ordered five the specification draws")
    void coreStagesAreTheOrderedFive() {
        assertEquals(
                List.of(
                        WorkflowStage.INPUTS,
                        WorkflowStage.VALIDATE,
                        WorkflowStage.COMET,
                        WorkflowStage.PERCOLATOR,
                        WorkflowStage.RESULTS),
                WorkflowStage.coreStages());
    }

    @Test
    @DisplayName("isCore agrees with the core path, and the other three are downstream")
    void isCoreAgreesWithTheCorePath() {
        assertEquals(
                EnumSet.copyOf(WorkflowStage.coreStages()),
                EnumSet.copyOf(
                        Stream.of(WorkflowStage.values()).filter(WorkflowStage::isCore).toList()));
    }

    @Test
    @DisplayName("the downstream branches are PDV, and Limelight XML then Limelight Upload")
    void downstreamBranchesAreTheTwoOptionalOnes() {
        assertEquals(
                List.of(
                        List.of(WorkflowStage.PDV),
                        List.of(WorkflowStage.LIMELIGHT_XML, WorkflowStage.LIMELIGHT_UPLOAD)),
                WorkflowStage.downstreamBranches());
    }

    @Test
    @DisplayName("each downstream branch hangs off RESULTS and contains no core stage")
    void downstreamBranchesHangOffResults() {
        List<WorkflowStage> heads = new ArrayList<>();
        List<Boolean> anyCore = new ArrayList<>();
        for (List<WorkflowStage> branch : WorkflowStage.downstreamBranches()) {
            heads.addAll(branch.get(0).predecessors());
            anyCore.add(branch.stream().anyMatch(WorkflowStage::isCore));
        }
        assertEquals(
                List.of(
                        List.of(WorkflowStage.RESULTS, WorkflowStage.RESULTS),
                        List.of(false, false)),
                List.of(heads, anyCore));
    }

    @Test
    @DisplayName("the core path and the branches partition the eight stages, with no stage twice")
    void corePathAndBranchesPartitionTheStages() {
        List<WorkflowStage> all = new ArrayList<>(WorkflowStage.coreStages());
        WorkflowStage.downstreamBranches().forEach(all::addAll);
        assertEquals(WorkflowStage.values().length, all.size(), () -> "listed: " + all);
        assertEquals(EnumSet.allOf(WorkflowStage.class), EnumSet.copyOf(all));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(WorkflowStage.class)
    @DisplayName("a stage's predecessor list cannot be modified by a caller")
    void predecessorListsAreImmutable(WorkflowStage stage) {
        assertThrows(UnsupportedOperationException.class, () -> stage.predecessors().clear());
    }

    @Test
    @DisplayName("the core path and the branch lists cannot be modified by a caller")
    void publishedStageListsAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> WorkflowStage.coreStages().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> WorkflowStage.downstreamBranches().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> WorkflowStage.downstreamBranches().get(1).clear());
    }

    @Test
    @DisplayName("a stage is a StageTag, so the domain's console can filter by it")
    void aStageTagsAConsoleMessageWithoutTheDomainKnowingThisModule() {
        BoundedMessageLog log = new BoundedMessageLog(10);
        log.append(message(WorkflowStage.COMET, "Comet is searching"));
        log.append(message(WorkflowStage.PERCOLATOR, "Percolator is rescoring"));
        log.append(message(WorkflowStage.COMET, "Comet wrote a PIN file"));

        List<String> cometLines =
                log.snapshotForStage(WorkflowStage.COMET, MessageSeverity.INFO).stream()
                        .map(LogMessage::text)
                        .toList();

        assertEquals(List.of("Comet is searching", "Comet wrote a PIN file"), cometLines);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(WorkflowStage.class)
    @DisplayName("a stage seen only as a StageTag still reports its own id and display name")
    void aStageIsUsableThroughTheStageTagInterfaceAlone(WorkflowStage stage) {
        StageTag tag = stage;
        assertEquals(
                List.of(stage.id(), stage.displayName()), List.of(tag.id(), tag.displayName()));
    }

    private static LogMessage message(WorkflowStage stage, String text) {
        return LogMessage.at(Instant.EPOCH, stage, MessageSeverity.INFO, text);
    }

    /**
     * Every stage reachable from {@code start} by following predecessors, {@code start} excluded.
     */
    private static Set<WorkflowStage> ancestorsOf(WorkflowStage start) {
        Set<WorkflowStage> seen = EnumSet.noneOf(WorkflowStage.class);
        Deque<WorkflowStage> pending = new ArrayDeque<>(start.predecessors());
        while (!pending.isEmpty()) {
            WorkflowStage stage = pending.removeFirst();
            if (seen.add(stage)) {
                pending.addAll(stage.predecessors());
            }
        }
        return seen;
    }

    private static <T> Map<WorkflowStage, T> mapOf(Function<WorkflowStage, T> property) {
        Map<WorkflowStage, T> values = new LinkedHashMap<>();
        for (WorkflowStage stage : WorkflowStage.values()) {
            values.put(stage, property.apply(stage));
        }
        return values;
    }
}
