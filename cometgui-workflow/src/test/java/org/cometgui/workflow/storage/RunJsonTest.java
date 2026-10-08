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

package org.cometgui.workflow.storage;

import static org.cometgui.workflow.storage.StorageFixtures.DERIVED_RUN_JSON;
import static org.cometgui.workflow.storage.StorageFixtures.RUN_JSON;
import static org.cometgui.workflow.testing.TestPaths.absolute;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.project.SchemaVerdict;
import org.cometgui.domain.project.UnsupportedSchemaVersionException;
import org.cometgui.domain.run.ArchivedFile;
import org.cometgui.domain.run.AttemptOutcome;
import org.cometgui.domain.run.DatabaseDelivery;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RecordedFingerprint;
import org.cometgui.domain.run.RunAttempt;
import org.cometgui.domain.run.RunDerivation;
import org.cometgui.domain.run.RunDescriptor;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.SpectrumInput;
import org.cometgui.provenance.json.JsonParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The {@code run.json} format. The writer is held to the hand-typed {@link
 * StorageFixtures#RUN_JSON} byte for byte; the reader parses that same hand-typed text, and
 * hand-made damage to it, and never a document the writer produced.
 */
class RunJsonTest {

    private static final String DOC = "run.json";

    private static final String PREFIX = "run.json is not valid: ";

    @Test
    @DisplayName("the writer produces exactly the hand-typed document")
    void writerPinsTheBytes() {
        assertEquals(RUN_JSON, RunJson.render(StorageFixtures.descriptor()));
    }

    @Test
    @DisplayName("a fresh run is written with an empty attempts array")
    void writerFreshRun() {
        String rendered = RunJson.render(RunDescriptor.of(StorageFixtures.identity()));
        assertTrue(
                rendered.endsWith(
                        "  \"databaseDelivery\": \"parameter-file\",\n  \"attempts\": []\n}\n"),
                rendered);
    }

    @Test
    @DisplayName("the reader reads every value of the hand-typed document")
    void readerReadsTheValues() {
        RunDescriptor read = RunJson.parse(RUN_JSON, DOC);
        List<SpectrumInput> spectra = read.identity().spectra();
        RunAttempt first = read.attempts().get(0);
        RunAttempt second = read.attempts().get(1);
        assertAll(
                () -> assertEquals(new RunId("run-0001"), read.identity().runId()),
                () -> assertEquals(new ProjectId("project-beta"), read.identity().projectId()),
                () ->
                        assertEquals(
                                Instant.parse("2026-08-28T23:15:00.250Z"),
                                read.identity().created()),
                () -> assertEquals("2026.03.0", read.identity().cometRelease()),
                () -> assertEquals(2, spectra.size()),
                () -> assertEquals(1, spectra.get(0).position()),
                () -> assertEquals("k562_3", spectra.get(0).base()),
                () -> assertEquals("comet-01", spectra.get(0).stageId()),
                () -> assertEquals(absolute("data/in/k562_3.mzML"), spectra.get(0).file().path()),
                () -> assertEquals(2602922, spectra.get(0).file().size()),
                () ->
                        assertEquals(
                                Instant.parse("2026-08-01T10:00:00.000Z"),
                                spectra.get(0).file().modified()),
                () ->
                        assertEquals(
                                new FileHashes(StorageFixtures.MD5_1, StorageFixtures.SHA_1),
                                spectra.get(0).file().hashes()),
                () -> assertEquals(2, spectra.get(1).position()),
                () -> assertEquals("k562_4", spectra.get(1).base()),
                () -> assertEquals(absolute("data/in/k562_4.mzML"), spectra.get(1).file().path()),
                () -> assertEquals(1195947, spectra.get(1).file().size()),
                () ->
                        assertEquals(
                                Instant.parse("2026-08-01T10:05:30.500Z"),
                                spectra.get(1).file().modified()),
                () -> assertEquals(StorageFixtures.SHA_2, spectra.get(1).file().hashes().sha256()),
                () -> assertEquals(absolute("data/db/sub.fasta"), read.identity().fasta().path()),
                () -> assertEquals(512000, read.identity().fasta().size()),
                () ->
                        assertEquals(
                                Instant.parse("2026-07-30T08:00:00.000Z"),
                                read.identity().fasta().modified()),
                () -> assertEquals(StorageFixtures.MD5_F, read.identity().fasta().hashes().md5()),
                () ->
                        assertEquals(
                                new ArchivedFile(
                                        "parameters/comet.params",
                                        12345,
                                        new FileHashes(
                                                StorageFixtures.MD5_P, StorageFixtures.SHA_P)),
                                read.identity().parameters()),
                () -> assertEquals(IndexMode.NONE, read.identity().indexMode()),
                () ->
                        assertEquals(
                                DatabaseDelivery.PARAMETER_FILE,
                                read.identity().databaseDelivery()),
                () -> assertEquals(2, read.attempts().size()),
                () -> assertEquals(1, first.number()),
                () -> assertEquals(Instant.parse("2026-08-28T23:15:01.000Z"), first.started()),
                () ->
                        assertEquals(
                                Optional.of(Instant.parse("2026-08-28T23:16:00.000Z")),
                                first.ended()),
                () -> assertEquals(AttemptOutcome.FAILED, first.outcome()),
                () ->
                        assertEquals(
                                Map.of(
                                        "hash-inputs",
                                        new RecordedFingerprint(
                                                StorageFixtures.FP,
                                                Map.of(
                                                        "fasta", StorageFixtures.DG_F,
                                                        "spectrum-files", StorageFixtures.DG_S))),
                                first.succeededSteps()),
                () -> assertEquals(2, second.number()),
                () -> assertEquals(Instant.parse("2026-08-28T23:17:00.000Z"), second.started()),
                () -> assertEquals(Optional.empty(), second.ended()),
                () -> assertEquals(AttemptOutcome.RUNNING, second.outcome()),
                () -> assertEquals(Map.of(), second.succeededSteps()));
    }

    @Test
    @DisplayName("the other wire names are read: fragment-ion, peptide, command-line, cancelled")
    void readerWireNames() {
        String text =
                replaceOnce(
                        replaceOnce(
                                replaceOnce(
                                        RUN_JSON,
                                        "\"indexMode\": \"none\"",
                                        "\"indexMode\": \"fragment-ion\""),
                                "\"databaseDelivery\": \"parameter-file\"",
                                "\"databaseDelivery\": \"command-line\""),
                        "\"outcome\": \"failed\"",
                        "\"outcome\": \"cancelled\"");
        RunDescriptor read = RunJson.parse(text, DOC);
        assertAll(
                () -> assertEquals(IndexMode.FRAGMENT_ION, read.identity().indexMode()),
                () ->
                        assertEquals(
                                DatabaseDelivery.COMMAND_LINE, read.identity().databaseDelivery()),
                () -> assertEquals(AttemptOutcome.CANCELLED, read.attempts().get(0).outcome()),
                () ->
                        assertEquals(
                                IndexMode.PEPTIDE,
                                RunJson.parse(
                                                replaceOnce(
                                                        RUN_JSON,
                                                        "\"indexMode\": \"none\"",
                                                        "\"indexMode\": \"peptide\""),
                                                DOC)
                                        .identity()
                                        .indexMode()));
    }

    @Test
    @DisplayName("a newer schema version is refused before any other member is read")
    void newerRefusedFirst() {
        // Every other member is wrong too; only the version may be reported. Version 2 is a
        // derived run's, which this build reads; 3 is the first it does not.
        String newer =
                "{\"schemaVersion\": 3, \"runId\": 5, \"spectra\": \"x\", \"surprise\": true}\n";
        UnsupportedSchemaVersionException thrown =
                assertThrows(
                        UnsupportedSchemaVersionException.class, () -> RunJson.parse(newer, DOC));
        assertAll(
                () -> assertEquals(SchemaVerdict.NEWER, thrown.verdict()),
                () -> assertEquals(3, thrown.found()),
                () -> assertEquals(2, thrown.current()),
                () -> assertEquals("run.json", thrown.document()),
                () ->
                        assertTrue(
                                thrown.getMessage()
                                        .startsWith(
                                                "run.json declares schema version 3, and this build"
                                                        + " of CometGUI reads version 2."),
                                thrown.getMessage()));
    }

    @Test
    @DisplayName("an older schema version is refused naming both versions")
    void olderRefused() {
        UnsupportedSchemaVersionException thrown =
                assertThrows(
                        UnsupportedSchemaVersionException.class,
                        () ->
                                RunJson.parse(
                                        replaceOnce(
                                                RUN_JSON,
                                                "\"schemaVersion\": 1",
                                                "\"schemaVersion\": 0"),
                                        DOC));
        assertAll(
                () -> assertEquals(SchemaVerdict.OLDER, thrown.verdict()),
                () ->
                        assertEquals(
                                "run.json declares schema version 0, and this build of CometGUI"
                                        + " reads version 2. No migration from version 0 to version"
                                        + " 2 exists, so it is refused; the file has not been"
                                        + " changed.",
                                thrown.getMessage()));
    }

    @Test
    @DisplayName("text that is not JSON is refused with the parser's position as the cause")
    void notJson() {
        InvalidDocumentException thrown =
                assertThrows(
                        InvalidDocumentException.class,
                        () -> RunJson.parse("{\"schemaVersion\": 1,", DOC));
        JsonParseException cause = assertInstanceOf(JsonParseException.class, thrown.getCause());
        assertAll(
                () -> assertEquals("", thrown.member()),
                () -> assertEquals("run.json", thrown.document()),
                () -> assertEquals(1, cause.line()),
                () ->
                        assertTrue(
                                thrown.getMessage()
                                        .startsWith("run.json is not well-formed JSON: "),
                                thrown.getMessage()),
                () ->
                        assertTrue(
                                thrown.getMessage().endsWith(cause.getMessage()),
                                thrown.getMessage()));
    }

    static Stream<Arguments> damage() {
        return Stream.of(
                Arguments.of(
                        "root is an array", null, "[]\n", "", "the document must be a JSON object"),
                Arguments.of(
                        "no schema version",
                        "  \"schemaVersion\": 1,\n",
                        "",
                        "",
                        "the document has no member \"schemaVersion\""),
                Arguments.of(
                        "schema version as text",
                        "\"schemaVersion\": 1",
                        "\"schemaVersion\": \"1\"",
                        "schemaVersion",
                        "\"schemaVersion\" must be a whole number"),
                Arguments.of(
                        "an unknown root member",
                        "\"cometRelease\": \"2026.03.0\",",
                        "\"cometRelease\": \"2026.03.0\", \"note\": \"x\",",
                        "",
                        "the document has 1 member(s) this build does not know; its schema version"
                                + " defines exactly [schemaVersion, runId, projectId, created,"
                                + " cometRelease, spectra, fasta, parameters, indexMode,"
                                + " databaseDelivery, attempts]"),
                Arguments.of(
                        "no index mode",
                        "  \"indexMode\": \"none\",\n",
                        "",
                        "",
                        "the document has no member \"indexMode\""),
                Arguments.of(
                        "a misspelt member",
                        "\"fasta\": {",
                        "\"Fasta\": {",
                        "",
                        "the document has 1 member(s) this build does not know; its schema version"
                                + " defines exactly [schemaVersion, runId, projectId, created,"
                                + " cometRelease, spectra, fasta, parameters, indexMode,"
                                + " databaseDelivery, attempts]"),
                Arguments.of(
                        "a run id that could climb out of runs/",
                        "\"runId\": \"run-0001\"",
                        "\"runId\": \"../run-0001\"",
                        "runId",
                        "\"runId\" was refused by the model (IllegalArgumentException); the model's"
                                + " own message is not repeated, because it quotes the value it"
                                + " refused"),
                Arguments.of(
                        "a bad project id",
                        "\"projectId\": \"project-beta\"",
                        "\"projectId\": \"\"",
                        "projectId",
                        "\"projectId\" was refused by the model (IllegalArgumentException); the"
                                + " model's own message is not repeated, because it quotes the"
                                + " value"
                                + " it refused"),
                Arguments.of(
                        "a creation time without milliseconds",
                        "\"created\": \"2026-08-28T23:15:00.250Z\"",
                        "\"created\": \"2026-08-28T23:15:00Z\"",
                        "created",
                        "\"created\" must be a UTC timestamp of the form"
                                + " uuuu-MM-dd'T'HH:mm:ss.SSS'Z'"),
                Arguments.of(
                        "a blank release",
                        "\"cometRelease\": \"2026.03.0\"",
                        "\"cometRelease\": \" \"",
                        "cometRelease",
                        "\"cometRelease\" must be non-blank and hold no control character"),
                Arguments.of(
                        "a release with a control character",
                        "\"cometRelease\": \"2026.03.0\"",
                        "\"cometRelease\": \"2026\\u0007\"",
                        "cometRelease",
                        "\"cometRelease\" must be non-blank and hold no control character"),
                Arguments.of(
                        "no spectra",
                        null,
                        "SPECTRA:[]",
                        "spectra",
                        "\"spectra\" must name at least one spectrum file"),
                Arguments.of(
                        "spectra as an object",
                        null,
                        "SPECTRA:{}",
                        "spectra",
                        "\"spectra\" must be a JSON array"),
                Arguments.of(
                        "a position out of order",
                        "\"position\": 2",
                        "\"position\": 3",
                        "spectra[1].position",
                        "\"spectra[1].position\" must be 2: positions run 1, 2, 3 ... in input"
                                + " order"),
                Arguments.of(
                        "a position beyond an int",
                        "\"position\": 2",
                        "\"position\": 4294967298",
                        "spectra[1].position",
                        "\"spectra[1].position\" must fit in a signed 32-bit integer"),
                Arguments.of(
                        "the largest int position is read, then refused for its order",
                        "\"position\": 2",
                        "\"position\": 2147483647",
                        "spectra[1].position",
                        "\"spectra[1].position\" must be 2: positions run 1, 2, 3 ... in input"
                                + " order"),
                Arguments.of(
                        "the smallest int position is read, then refused for its order",
                        "\"position\": 2",
                        "\"position\": -2147483648",
                        "spectra[1].position",
                        "\"spectra[1].position\" must be 2: positions run 1, 2, 3 ... in input"
                                + " order"),
                Arguments.of(
                        "one past the largest int",
                        "\"position\": 2",
                        "\"position\": 2147483648",
                        "spectra[1].position",
                        "\"spectra[1].position\" must fit in a signed 32-bit integer"),
                Arguments.of(
                        "one below the smallest int",
                        "\"position\": 2",
                        "\"position\": -2147483649",
                        "spectra[1].position",
                        "\"spectra[1].position\" must fit in a signed 32-bit integer"),
                Arguments.of(
                        "an unknown member in the FASTA",
                        "\"path\": \"/data/db/sub.fasta\",",
                        "\"path\": \"/data/db/sub.fasta\", \"copy\": true,",
                        "fasta",
                        "\"fasta\" has 1 member(s) this build does not know; its schema version"
                                + " defines exactly [path, size, modified, md5, sha256]"),
                Arguments.of(
                        "an unknown member in an attempt",
                        "\"number\": 2,",
                        "\"number\": 2, \"host\": \"x\",",
                        "attempts[1]",
                        "\"attempts[1]\" has 1 member(s) this build does not know; its schema"
                                + " version defines exactly [number, started, ended, outcome,"
                                + " succeededSteps]"),
                Arguments.of(
                        "a stage id that is not derived from the position",
                        "\"stageId\": \"comet-01\"",
                        "\"stageId\": \"comet-1\"",
                        "spectra[0].stageId",
                        "\"spectra[0].stageId\" must be comet-01, derived from the position"),
                Arguments.of(
                        "a base the naming rule would not give",
                        "\"base\": \"k562_4\"",
                        "\"base\": \"k562_3\"",
                        "spectra[1].base",
                        "\"spectra[1].base\" is not the name the output naming rule gives these"
                                + " inputs"),
                Arguments.of(
                        "a relative input path",
                        "\"path\": \"/data/in/k562_3.mzML\"",
                        "\"path\": \"in/k562_3.mzML\"",
                        "spectra[0]",
                        "\"spectra[0]\" was refused by the model (IllegalArgumentException); the"
                                + " model's own message is not repeated, because it quotes the"
                                + " value"
                                + " it refused"),
                Arguments.of(
                        "an input path that is no path at all",
                        "\"path\": \"/data/in/k562_3.mzML\"",
                        "\"path\": \"/data/in/k562\\u0000.mzML\"",
                        "spectra[0].path",
                        "\"spectra[0].path\" was refused by the model (InvalidPathException); the"
                                + " model's own message is not repeated, because it quotes the"
                                + " value"
                                + " it refused"),
                Arguments.of(
                        "an input whose name escapes outputs/comet",
                        "\"path\": \"/data/in/k562_4.mzML\"",
                        "\"path\": \"/data/in/..mzML\"",
                        "spectra",
                        "\"spectra\" was refused by the model (IllegalArgumentException); the"
                                + " model's own message is not repeated, because it quotes the"
                                + " value"
                                + " it refused"),
                Arguments.of(
                        "a negative size",
                        "\"size\": 2602922",
                        "\"size\": -1",
                        "spectra[0]",
                        "\"spectra[0]\" was refused by the model (IllegalArgumentException); the"
                                + " model's own message is not repeated, because it quotes the"
                                + " value"
                                + " it refused"),
                Arguments.of(
                        "a size given as text",
                        "\"size\": 2602922",
                        "\"size\": \"2602922\"",
                        "spectra[0].size",
                        "\"spectra[0].size\" must be a whole number"),
                Arguments.of(
                        "an upper-case MD5",
                        "\"md5\": \"0123456789abcdef0123456789abcdef\"",
                        "\"md5\": \"0123456789ABCDEF0123456789ABCDEF\"",
                        "spectra[0]",
                        "\"spectra[0]\" must record its digests in lower-case hexadecimal"),
                Arguments.of(
                        "an upper-case SHA-256",
                        "a562f6e6b4c1d0e9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5",
                        "A562F6E6B4C1D0E9F8A7B6C5D4E3F2A1B0C9D8E7F6A5B4C3D2E1F0A9B8C7D6E5",
                        "spectra[0]",
                        "\"spectra[0]\" must record its digests in lower-case hexadecimal"),
                Arguments.of(
                        "a short SHA-256",
                        "a562f6e6b4c1d0e9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5",
                        "a562f6e6",
                        "spectra[0]",
                        "\"spectra[0]\" was refused by the model (IllegalArgumentException); the"
                                + " model's own message is not repeated, because it quotes the"
                                + " value"
                                + " it refused"),
                Arguments.of(
                        "an unknown member in a spectrum entry",
                        "\"base\": \"k562_3\",",
                        "\"base\": \"k562_3\", \"copy\": true,",
                        "spectra[0]",
                        "\"spectra[0]\" has 1 member(s) this build does not know; its schema"
                                + " version"
                                + " defines exactly [position, stageId, base, path, size, modified,"
                                + " md5, sha256]"),
                Arguments.of(
                        "no modification time on the FASTA",
                        "\"modified\": \"2026-07-30T08:00:00.000Z\"",
                        "\"modified\": null",
                        "fasta.modified",
                        "\"fasta.modified\" must be a string"),
                Arguments.of(
                        "a parameter file outside parameters/",
                        "\"path\": \"parameters/comet.params\"",
                        "\"path\": \"outputs/comet.params\"",
                        "parameters.path",
                        "\"parameters.path\" must name a file under parameters/"),
                Arguments.of(
                        "a parameter file that climbs out",
                        "\"path\": \"parameters/comet.params\"",
                        "\"path\": \"parameters/../../x\"",
                        "parameters",
                        "\"parameters\" was refused by the model (IllegalArgumentException); the"
                                + " model's own message is not repeated, because it quotes the"
                                + " value"
                                + " it refused"),
                Arguments.of(
                        "a parameter file with a modification time",
                        "\"path\": \"parameters/comet.params\",",
                        "\"path\": \"parameters/comet.params\", \"modified\": \"x\",",
                        "parameters",
                        "\"parameters\" has 1 member(s) this build does not know; its schema"
                                + " version"
                                + " defines exactly [path, size, md5, sha256]"),
                Arguments.of(
                        "an unknown index mode",
                        "\"indexMode\": \"none\"",
                        "\"indexMode\": \"Peptide\"",
                        "indexMode",
                        "\"indexMode\" must be one of [none, fragment-ion, peptide]"),
                Arguments.of(
                        "an unknown database delivery",
                        "\"databaseDelivery\": \"parameter-file\"",
                        "\"databaseDelivery\": \"-D\"",
                        "databaseDelivery",
                        "\"databaseDelivery\" must be one of [parameter-file, command-line]"),
                Arguments.of(
                        "attempts as an object",
                        null,
                        "ATTEMPTS:{}",
                        "attempts",
                        "\"attempts\" must be a JSON array"),
                Arguments.of(
                        "an attempt that is not an object",
                        null,
                        "ATTEMPTS:[1]",
                        "attempts[0]",
                        "\"attempts[0]\" must be a JSON object"),
                Arguments.of(
                        "an unknown outcome",
                        "\"outcome\": \"failed\"",
                        "\"outcome\": \"canceled\"",
                        "attempts[0].outcome",
                        "\"attempts[0].outcome\" must be one of [running, succeeded, failed,"
                                + " cancelled]"),
                Arguments.of(
                        "a running attempt with an end",
                        "\"ended\": null",
                        "\"ended\": \"2026-08-28T23:18:00.000Z\"",
                        "attempts[1]",
                        "\"attempts[1]\" was refused by the model (IllegalArgumentException); the"
                                + " model's own message is not repeated, because it quotes the"
                                + " value"
                                + " it refused"),
                Arguments.of(
                        "an end time that is not a timestamp",
                        "\"ended\": null",
                        "\"ended\": 5",
                        "attempts[1].ended",
                        "\"attempts[1].ended\" must be a string"),
                Arguments.of(
                        "attempts misnumbered",
                        "\"number\": 2",
                        "\"number\": 3",
                        "attempts",
                        "\"attempts\" was refused by the model (IllegalArgumentException); the"
                                + " model's own message is not repeated, because it quotes the"
                                + " value"
                                + " it refused"),
                Arguments.of(
                        "a step id that is not an identifier",
                        "\"hash-inputs\": {",
                        "\"Hash_Inputs\": {",
                        "attempts[0].succeededSteps",
                        "\"attempts[0].succeededSteps\" has a step id that is not lower-case"
                                + " letters"
                                + " and digits in hyphen-separated words"),
                Arguments.of(
                        "an input kind that is not an identifier",
                        "\"fasta\": \"6666",
                        "\"FASTA\": \"6666",
                        "attempts[0].succeededSteps.hash-inputs.inputDigests",
                        "\"attempts[0].succeededSteps.hash-inputs.inputDigests\" has an input kind"
                                + " that is not lower-case letters and digits in hyphen-separated"
                                + " words"),
                Arguments.of(
                        "an input digest that is not text",
                        "\"fasta\": \"" + StorageFixtures.DG_F + "\"",
                        "\"fasta\": 6",
                        "attempts[0].succeededSteps.hash-inputs.inputDigests.fasta",
                        "\"attempts[0].succeededSteps.hash-inputs.inputDigests.fasta\" must be a"
                                + " string"),
                Arguments.of(
                        "an upper-case fingerprint",
                        "\"fingerprint\": \"5555",
                        "\"fingerprint\": \"A555",
                        "attempts[0].succeededSteps.hash-inputs",
                        "\"attempts[0].succeededSteps.hash-inputs\" was refused by the model"
                                + " (IllegalArgumentException); the model's own message is not"
                                + " repeated, because it quotes the value it refused"),
                Arguments.of(
                        "a fingerprint with an extra member",
                        "\"fingerprint\": \"5555",
                        "\"why\": 1, \"fingerprint\": \"5555",
                        "attempts[0].succeededSteps.hash-inputs",
                        "\"attempts[0].succeededSteps.hash-inputs\" has 1 member(s) this build does"
                                + " not know; its schema version defines exactly [fingerprint,"
                                + " inputDigests]"),
                Arguments.of(
                        "succeeded steps as an array",
                        "\"succeededSteps\": {}",
                        "\"succeededSteps\": []",
                        "attempts[1].succeededSteps",
                        "\"attempts[1].succeededSteps\" must be a JSON object"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("damage")
    @DisplayName("damaged documents are refused naming the member, quoting no value")
    void damaged(String what, String from, String to, String member, String problem) {
        String damaged = damage(from, to);
        assertNotEquals(RUN_JSON, damaged, "the damage must change the document");
        InvalidDocumentException thrown =
                assertThrows(
                        InvalidDocumentException.class, () -> RunJson.parse(damaged, DOC), what);
        assertAll(
                () -> assertEquals(PREFIX + problem, thrown.getMessage()),
                () -> assertEquals(member, thrown.member()),
                () -> assertEquals(DOC, thrown.document()));
    }

    private static String damage(String from, String to) {
        if (from != null) {
            return replaceOnce(RUN_JSON, from, to);
        }
        if (to.startsWith("SPECTRA:")) {
            int start = RUN_JSON.indexOf("\"spectra\": [");
            int end = RUN_JSON.indexOf("  \"fasta\"");
            return RUN_JSON.substring(0, start)
                    + "\"spectra\": "
                    + to.substring("SPECTRA:".length())
                    + ",\n"
                    + RUN_JSON.substring(end);
        }
        if (to.startsWith("ATTEMPTS:")) {
            int start = RUN_JSON.indexOf("\"attempts\": [");
            return RUN_JSON.substring(0, start)
                    + "\"attempts\": "
                    + to.substring("ATTEMPTS:".length())
                    + "\n}\n";
        }
        return to;
    }

    @Test
    @DisplayName("a refused value never reaches the message")
    void valuesAreNotQuoted() {
        String secret = "ghp_" + "Zx9".repeat(12);
        String damaged =
                replaceOnce(RUN_JSON, "\"runId\": \"run-0001\"", "\"runId\": \"/" + secret + "\"");
        InvalidDocumentException thrown =
                assertThrows(InvalidDocumentException.class, () -> RunJson.parse(damaged, DOC));
        assertAll(
                () -> assertFalse(thrown.getMessage().contains(secret), thrown.getMessage()),
                () -> assertEquals(null, thrown.getCause()));
    }

    @Test
    @DisplayName("a document nested beyond the reader's bound is refused as malformed, not a crash")
    void hostileNesting() {
        String hostile = "[".repeat(100_000);
        InvalidDocumentException thrown =
                assertThrows(InvalidDocumentException.class, () -> RunJson.parse(hostile, DOC));
        assertInstanceOf(JsonParseException.class, thrown.getCause());
    }

    @Test
    @DisplayName("a duplicated member is refused as malformed")
    void duplicateMember() {
        String twice =
                replaceOnce(
                        RUN_JSON,
                        "\"runId\": \"run-0001\",",
                        "\"runId\": \"run-0001\", \"runId\": \"run-0002\",");
        InvalidDocumentException thrown =
                assertThrows(InvalidDocumentException.class, () -> RunJson.parse(twice, DOC));
        assertInstanceOf(JsonParseException.class, thrown.getCause());
    }

    @Test
    @DisplayName("a run that executes its own search is still written at version 1")
    void ownSearchStaysVersionOne() {
        assertTrue(RUN_JSON.startsWith("{\n  \"schemaVersion\": 1,\n"));
        assertEquals(1, RunJson.parse(RUN_JSON, DOC).schemaVersion());
        assertFalse(RunJson.render(StorageFixtures.descriptor()).contains("derivedFrom"));
    }

    @Test
    @DisplayName("a derived run is written exactly as the hand-typed version-2 document")
    void writerPinsTheDerivedBytes() {
        assertEquals(DERIVED_RUN_JSON, RunJson.render(StorageFixtures.derivedDescriptor()));
    }

    @Test
    @DisplayName("the reader reads every value of the hand-typed version-2 document")
    void readerReadsTheDerivation() {
        RunDescriptor read = RunJson.parse(DERIVED_RUN_JSON, DOC);
        RunDerivation from = read.identity().derivedFrom().orElseThrow();
        assertAll(
                () -> assertEquals(2, read.schemaVersion()),
                () -> assertEquals(new RunId("run-0002"), read.identity().runId()),
                () -> assertEquals(new RunId("run-0001"), from.runId()),
                () -> assertEquals(Instant.parse("2026-08-28T22:00:00.125Z"), from.created()),
                () -> assertEquals("20260828T220000Z-run-0001", from.directoryName()),
                () -> assertEquals("provenance/provenance.json", from.provenance().path()),
                () -> assertEquals(40960, from.provenance().size()),
                () ->
                        assertEquals(
                                new FileHashes(
                                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                                                + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
                                from.provenance().hashes()),
                () -> assertEquals("inputs/pin/merged.pin", from.mergedPin().path()),
                () -> assertEquals(1234567, from.mergedPin().size()),
                () ->
                        assertEquals(
                                new FileHashes(
                                        "88888888888888888888888888888888",
                                        "99999999999999999999999999999999"
                                                + "99999999999999999999999999999999"),
                                from.mergedPin().hashes()),
                () -> assertEquals(StorageFixtures.derivedDescriptor(), read));
    }

    static Stream<Arguments> derivedDamage() {
        return Stream.of(
                Arguments.of(
                        "a version-1 document carrying derivedFrom",
                        "\"schemaVersion\": 2",
                        "\"schemaVersion\": 1",
                        "",
                        "the document has 1 member(s) this build does not know; its schema version"
                                + " defines exactly [schemaVersion, runId, projectId, created,"
                                + " cometRelease, spectra, fasta, parameters, indexMode,"
                                + " databaseDelivery, attempts]"),
                Arguments.of(
                        "an unknown member beside derivedFrom",
                        "\"derivedFrom\": {",
                        "\"derivedFrom\": null, \"x\": {",
                        "",
                        "the document has 1 member(s) this build does not know; its schema version"
                                + " defines exactly [schemaVersion, runId, projectId, created,"
                                + " cometRelease, spectra, fasta, parameters, indexMode,"
                                + " databaseDelivery, derivedFrom, attempts]"),
                Arguments.of(
                        "derivedFrom renamed away",
                        "\"derivedFrom\": {",
                        "\"derivedFrm\": {",
                        "",
                        "the document has 1 member(s) this build does not know; its schema version"
                                + " defines exactly [schemaVersion, runId, projectId, created,"
                                + " cometRelease, spectra, fasta, parameters, indexMode,"
                                + " databaseDelivery, derivedFrom, attempts]"),
                Arguments.of(
                        "a derivation with an extra member",
                        "\"runId\": \"run-0001\",\n    \"created\"",
                        "\"runId\": \"run-0001\", \"why\": 1,\n    \"created\"",
                        "derivedFrom",
                        "\"derivedFrom\" has 1 member(s) this build does not know; its schema"
                                + " version defines exactly [runId, created, provenance,"
                                + " mergedPin]"),
                Arguments.of(
                        "a derivation without its merged PIN",
                        "\"mergedPin\": {",
                        "\"mergedPim\": {",
                        "derivedFrom",
                        "\"derivedFrom\" has 1 member(s) this build does not know; its schema"
                                + " version defines exactly [runId, created, provenance,"
                                + " mergedPin]"),
                Arguments.of(
                        "a source run id that is no run id",
                        "\"runId\": \"run-0001\",\n    \"created\"",
                        "\"runId\": \"../run-0001\",\n    \"created\"",
                        "derivedFrom.runId",
                        "\"derivedFrom.runId\" was refused by the model"
                                + " (IllegalArgumentException); the model's own message is not"
                                + " repeated, because it quotes the value it refused"),
                Arguments.of(
                        "a source creation time that is no timestamp",
                        "\"created\": \"2026-08-28T22:00:00.125Z\"",
                        "\"created\": \"yesterday\"",
                        "derivedFrom.created",
                        "\"derivedFrom.created\" must be a UTC timestamp of the form"
                                + " uuuu-MM-dd'T'HH:mm:ss.SSS'Z'"),
                Arguments.of(
                        "the merged PIN recorded at another path",
                        "\"path\": \"inputs/pin/merged.pin\"",
                        "\"path\": \"inputs/pin/other.pin\"",
                        "derivedFrom",
                        "\"derivedFrom\" was refused by the model (IllegalArgumentException);"
                                + " the model's own message is not repeated, because it quotes the"
                                + " value it refused"),
                Arguments.of(
                        "the manifest with an extra member",
                        "\"path\": \"provenance/provenance.json\",",
                        "\"path\": \"provenance/provenance.json\", \"why\": 1,",
                        "derivedFrom.provenance",
                        "\"derivedFrom.provenance\" has 1 member(s) this build does not know;"
                                + " its schema version defines exactly [path, size, md5, sha256]"),
                Arguments.of(
                        "the merged PIN's digest in upper case",
                        "\"sha256\": \"99999",
                        "\"sha256\": \"A9999",
                        "derivedFrom.mergedPin",
                        "\"derivedFrom.mergedPin\" must record its digests in lower-case"
                                + " hexadecimal"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("derivedDamage")
    @DisplayName("damaged version-2 documents are refused naming the member, quoting no value")
    void derivedDamaged(String what, String from, String to, String member, String problem) {
        String damaged = replaceOnce(DERIVED_RUN_JSON, from, to);
        assertNotEquals(DERIVED_RUN_JSON, damaged, "the damage must change the document");
        InvalidDocumentException thrown =
                assertThrows(
                        InvalidDocumentException.class, () -> RunJson.parse(damaged, DOC), what);
        assertAll(
                () -> assertEquals(PREFIX + problem, thrown.getMessage()),
                () -> assertEquals(member, thrown.member()));
    }

    @Test
    @DisplayName("derivedFrom as null in a version-2 document is refused: it must be an object")
    void derivationNotAnObject() {
        String damaged =
                DERIVED_RUN_JSON.substring(0, DERIVED_RUN_JSON.indexOf("  \"derivedFrom\""))
                        + "  \"derivedFrom\": null,\n"
                        + DERIVED_RUN_JSON.substring(DERIVED_RUN_JSON.indexOf("  \"attempts\""));
        InvalidDocumentException thrown =
                assertThrows(InvalidDocumentException.class, () -> RunJson.parse(damaged, DOC));
        assertAll(
                () ->
                        assertEquals(
                                PREFIX + "\"derivedFrom\" must be a JSON object",
                                thrown.getMessage()),
                () -> assertEquals("derivedFrom", thrown.member()));
    }

    @Test
    @DisplayName("a version-2 document with no derivedFrom is refused naming the member")
    void derivationMissing() {
        String damaged =
                DERIVED_RUN_JSON.substring(0, DERIVED_RUN_JSON.indexOf("  \"derivedFrom\""))
                        + DERIVED_RUN_JSON.substring(DERIVED_RUN_JSON.indexOf("  \"attempts\""));
        InvalidDocumentException thrown =
                assertThrows(InvalidDocumentException.class, () -> RunJson.parse(damaged, DOC));
        assertAll(
                () ->
                        assertEquals(
                                PREFIX + "the document has no member \"derivedFrom\"",
                                thrown.getMessage()),
                () -> assertEquals("", thrown.member()));
    }

    static String replaceOnce(String text, String from, String to) {
        int at = text.indexOf(from);
        if (at < 0 || text.indexOf(from, at + 1) >= 0) {
            throw new AssertionError("the fixture must contain \"" + from + "\" exactly once");
        }
        return text.substring(0, at) + to + text.substring(at + from.length());
    }
}
