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

import static org.cometgui.workflow.testing.TestPaths.absolute;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.RunId;
import org.cometgui.provenance.manifest.ApplicationRecord;
import org.cometgui.provenance.manifest.ExecutionRecord;
import org.cometgui.provenance.manifest.FileDirection;
import org.cometgui.provenance.manifest.FileRecord;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ProvenanceStatus;
import org.cometgui.provenance.manifest.RunRecord;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.state.EngineStep;
import org.junit.jupiter.api.Test;

/** The manifest's order is the plan's, whatever order steps reported in. */
class ProvenanceLedgerTest {

    private static final FileHashes HASHES = new FileHashes("0".repeat(32), "1".repeat(64));

    private static final Instant T = Instant.parse("2026-10-06T00:00:00Z");

    @Test
    void stepsAreRecordedInPlanOrderAndAFileOncePerDirection() {
        ProvenanceLedger ledger = new ProvenanceLedger();
        ledger.put(
                EngineStep.MERGE_PIN,
                List.of(),
                List.of(
                        file(FileDirection.INPUT, "r/a.pin"),
                        file(FileDirection.OUTPUT, "r/m.pin")));
        ledger.put(
                EngineStep.RUN_COMET,
                List.of(tool("comet-01"), tool("comet-02")),
                List.of(
                        file(FileDirection.OUTPUT, "r/a.pin"),
                        file(FileDirection.INPUT, "r/a.mzML")));
        ledger.put(
                EngineStep.HASH_INPUTS, List.of(), List.of(file(FileDirection.INPUT, "r/a.mzML")));
        ProvenanceManifest manifest =
                ledger.manifest(
                        EngineFixture.PLAN,
                        new RunRecord(
                                new RunId("r"), "p", ProvenanceStatus.COMPLETED, T, Optional.of(T)),
                        ApplicationRecord.capture("v", "b"),
                        Map.of("a.b", "c"));
        List<String> files = new ArrayList<>();
        for (FileRecord file : manifest.files()) {
            files.add(file.direction().wireName() + " " + file.path());
        }
        assertEquals(
                List.of("input /r/a.mzML", "output /r/a.pin", "input /r/a.pin", "output /r/m.pin"),
                files);
        List<Optional<String>> tools = new ArrayList<>();
        for (ToolRecord tool : manifest.tools()) {
            tools.add(tool.stageId());
        }
        assertEquals(List.of(Optional.of("comet-01"), Optional.of("comet-02")), tools);
        assertEquals(Map.of("a.b", "c"), manifest.settings());
    }

    private static FileRecord file(FileDirection direction, String path) {
        return new FileRecord(
                direction, "role", absolute(path), 1L, T, HASHES, ProvenanceStatus.COMPLETED);
    }

    private static ToolRecord tool(String stageId) {
        return new ToolRecord(
                "comet",
                "1",
                Optional.empty(),
                absolute("t/comet"),
                HASHES,
                true,
                Optional.empty(),
                Set.of(),
                Optional.of(stageId),
                new ExecutionRecord(
                        new ToolCommand(List.of("/t/comet"), absolute("w"), Map.of()),
                        T,
                        T,
                        0,
                        Optional.empty(),
                        Optional.empty(),
                        ProvenanceStatus.COMPLETED),
                List.of());
    }
}
