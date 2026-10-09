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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.cometgui.domain.project.SchemaVerdict;
import org.cometgui.domain.project.UnsupportedSchemaVersionException;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.results.filtering.PeptideQValueFilter;
import org.cometgui.results.filtering.PsmQValueFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A run's view state ({@code R-RES-01}): the {@code results/view-state.json} format against
 * hand-typed documents, {@code R-RUN-04}'s version policy, the one range rule, and what reading and
 * writing do -- and do not do -- to the run's other files.
 */
class ViewStateStoreTest {

    private static final String DEFAULTS_JSON =
            """
            {
              "schemaVersion": 1,
              "psmQValueFilter": "0.01",
              "peptideQValueFilter": "0.01"
            }
            """;

    private static final String CHOSEN_JSON =
            """
            {
              "schemaVersion": 1,
              "psmQValueFilter": "0.050",
              "peptideQValueFilter": "0"
            }
            """;

    private static final DisplayFilters CHOSEN =
            new DisplayFilters(
                    new PsmQValueFilter(new BigDecimal("0.050")),
                    new PeptideQValueFilter(BigDecimal.ZERO));

    @TempDir private Path tmp;

    /** A run directory holding a run.json, a provenance record and a raw output. */
    private RunLayout run() throws IOException {
        RunLayout run = new RunLayout(tmp.toRealPath().resolve("runs/20261008T120000Z-run-0001"));
        Files.createDirectories(run.provenanceDirectory());
        Files.createDirectories(run.outputsDirectory().resolve("percolator"));
        Files.writeString(run.runFile(), "{\"schemaVersion\": 1}\n");
        Files.writeString(run.provenanceJsonFile(), "{\"schemaVersion\": 1}\n");
        Files.writeString(run.eventLogFile(), "");
        Files.writeString(
                run.outputsDirectory().resolve("percolator/psms.tsv"), "PSMId\tscore\tq-value\n");
        return run;
    }

    /** Every path under a directory with its bytes, as text. */
    private static Map<String, String> tree(Path root) throws IOException {
        Map<String, String> tree = new TreeMap<>();
        try (Stream<Path> walked = Files.walk(root)) {
            for (Path path : walked.toList()) {
                String key = root.relativize(path).toString();
                tree.put(
                        key,
                        Files.isDirectory(path)
                                ? "directory"
                                : Files.readString(path, StandardCharsets.ISO_8859_1));
            }
        }
        return tree;
    }

    private static String document(String psm, String peptide) {
        return "{\n  \"schemaVersion\": 1,\n  \"psmQValueFilter\": "
                + psm
                + ",\n  \"peptideQValueFilter\": "
                + peptide
                + "\n}\n";
    }

    @Test
    @DisplayName("the writer produces exactly the hand-typed documents")
    void writer() {
        assertAll(
                () -> assertEquals(DEFAULTS_JSON, ViewStateJson.render(DisplayFilters.DEFAULTS)),
                () -> assertEquals(CHOSEN_JSON, ViewStateJson.render(CHOSEN)));
    }

    @Test
    @DisplayName("the reader reads the hand-typed documents; the typed decimal survives exactly")
    void reader() {
        DisplayFilters read = ViewStateJson.parse(CHOSEN_JSON, "view-state.json");
        assertAll(
                () -> assertEquals("0.050", read.psm().text()),
                () -> assertEquals("0", read.peptide().text()),
                () ->
                        assertEquals(
                                DisplayFilters.DEFAULTS,
                                ViewStateJson.parse(DEFAULTS_JSON, "view-state.json")));
    }

    @Test
    @DisplayName("no file: the defaults 0.01 and 0.01, as nothing saved, and nothing is written")
    void absent() throws IOException {
        RunLayout run = run();
        Map<String, String> before = tree(run.root());
        ViewStateReading reading = ViewStateStore.read(run);
        assertAll(
                () -> assertEquals("0.01", reading.filters().psm().text()),
                () -> assertEquals("0.01", reading.filters().peptide().text()),
                () ->
                        assertEquals(
                                ViewStateReading.Source.DEFAULTS_NOTHING_SAVED, reading.source()),
                () -> assertEquals(Optional.empty(), reading.refusal()),
                () -> assertEquals(before, tree(run.root())));
    }

    @Test
    @DisplayName(
            "write then read: the filters come back as saved; results/ is made; nothing else in the"
                    + " run changes -- run.json, provenance/ and outputs/ untouched")
    void roundTrip() throws IOException {
        RunLayout run = run();
        Map<String, String> before = tree(run.root());

        ViewStateStore.write(run, CHOSEN);

        assertEquals(CHOSEN_JSON, Files.readString(run.viewStateFile(), StandardCharsets.UTF_8));
        ViewStateReading reading = ViewStateStore.read(run);
        assertEquals(ViewStateReading.Source.SAVED, reading.source());
        assertEquals("0.050", reading.filters().psm().text());
        assertEquals("0", reading.filters().peptide().text());
        assertEquals(Optional.empty(), reading.refusal());
        Map<String, String> after = tree(run.root());
        Map<String, String> expected = new TreeMap<>(before);
        expected.put("results", "directory");
        expected.put("results/view-state.json", CHOSEN_JSON);
        assertEquals(expected, after, "only results/view-state.json was added");

        ViewStateStore.write(run, DisplayFilters.DEFAULTS.withPeptide(PeptideQValueFilter.of(1)));
        assertEquals(
                document("\"0.01\"", "\"1.0\""),
                Files.readString(run.viewStateFile(), StandardCharsets.UTF_8));
        assertEquals(List.of("view-state.json"), listing(run.resultsDirectory()));
    }

    private static List<String> listing(Path directory) throws IOException {
        try (Stream<Path> listed = Files.list(directory)) {
            return listed.map(path -> String.valueOf(path.getFileName())).sorted().toList();
        }
    }

    @Test
    @DisplayName(
            "a NEWER view state: read falls back to the defaults with the refusal naming both"
                    + " versions; write refuses to replace it; the file is byte-identical")
    void newer() throws IOException {
        RunLayout run = run();
        Files.createDirectories(run.resultsDirectory());
        byte[] newer =
                "{\"schemaVersion\": 2, \"psmQValueFilter\": 7, \"sort\": []}\n"
                        .getBytes(StandardCharsets.UTF_8);
        Files.write(run.viewStateFile(), newer);
        Map<String, String> before = tree(run.root());

        ViewStateReading reading = ViewStateStore.read(run);
        assertEquals(ViewStateReading.Source.DEFAULTS_FILE_REFUSED, reading.source());
        assertEquals(DisplayFilters.DEFAULTS, reading.filters());
        assertEquals(
                Optional.of(
                        run.viewStateFile()
                                + " declares schema version 2, and this build of CometGUI reads"
                                + " version 1. It was written by a newer CometGUI, which may have"
                                + " changed what a member means, so it is refused before anything"
                                + " else in it is read; the file has not been changed."),
                reading.refusal());

        UnsupportedSchemaVersionException refused =
                assertThrows(
                        UnsupportedSchemaVersionException.class,
                        () -> ViewStateStore.write(run, CHOSEN));
        assertEquals(SchemaVerdict.NEWER, refused.verdict());
        assertArrayEquals(newer, Files.readAllBytes(run.viewStateFile()));
        assertEquals(before, tree(run.root()), "nothing written, no temporary file left");
    }

    @Test
    @DisplayName("an OLDER view state (version 0) is refused: no migration exists")
    void older() {
        UnsupportedSchemaVersionException refused =
                assertThrows(
                        UnsupportedSchemaVersionException.class,
                        () ->
                                ViewStateJson.parse(
                                        DEFAULTS_JSON.replace(
                                                "\"schemaVersion\": 1", "\"schemaVersion\": 0"),
                                        "v.json"));
        assertEquals(SchemaVerdict.OLDER, refused.verdict());
    }

    @ParameterizedTest(name = "[{index}] psmQValueFilter {0}")
    @ValueSource(strings = {"\"1.5\"", "\"-0.01\"", "\"NaN\"", "\"0,01\"", "\"\"", "\"1.0000001\""})
    @DisplayName(
            "a cutoff the one range rule refuses is refused by the reader, naming the member and"
                    + " quoting no value")
    void outOfRange(String psm) {
        InvalidDocumentException refused =
                assertThrows(
                        InvalidDocumentException.class,
                        () -> ViewStateJson.parse(document(psm, "\"0.01\""), "r/view-state.json"));
        assertEquals("psmQValueFilter", refused.member());
        assertEquals(
                "r/view-state.json is not valid: \"psmQValueFilter\" must be a decimal number"
                        + " between 0 and 1 inclusive, written with a '.' decimal point",
                refused.getMessage());
    }

    @Test
    @DisplayName("the peptide cutoff is held to the same rule, by the peptide filter's parser")
    void peptideOutOfRange() {
        InvalidDocumentException refused =
                assertThrows(
                        InvalidDocumentException.class,
                        () -> ViewStateJson.parse(document("\"0.01\"", "\"2\""), "v.json"));
        assertEquals("peptideQValueFilter", refused.member());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"0", "1", "0.0", "1.000", "0.01", "1e-2"})
    @DisplayName("both ends of [0, 1] are accepted, as the filters' parser accepts them")
    void boundaries(String text) {
        DisplayFilters read =
                ViewStateJson.parse(
                        document("\"" + text + "\"", "\"" + text + "\""), "view-state.json");
        assertEquals(PsmQValueFilter.parse(text), read.psm());
        assertEquals(PeptideQValueFilter.parse(text), read.peptide());
    }

    @Test
    @DisplayName(
            "a damaged view state: read falls back to the defaults and says why; write refuses to"
                    + " replace it; the file is byte-identical")
    void damaged() throws IOException {
        RunLayout run = run();
        Files.createDirectories(run.resultsDirectory());
        byte[] damaged = document("\"1.5\"", "\"0.01\"").getBytes(StandardCharsets.UTF_8);
        Files.write(run.viewStateFile(), damaged);
        Map<String, String> before = tree(run.root());

        ViewStateReading reading = ViewStateStore.read(run);
        assertEquals(ViewStateReading.Source.DEFAULTS_FILE_REFUSED, reading.source());
        assertEquals(DisplayFilters.DEFAULTS, reading.filters());
        assertEquals(
                Optional.of(
                        run.viewStateFile()
                                + " is not valid: \"psmQValueFilter\" must be a decimal number"
                                + " between 0 and 1 inclusive, written with a '.' decimal point"),
                reading.refusal());
        assertFalse(reading.refusal().orElseThrow().contains("1.5"), "no value is quoted");

        InvalidDocumentException refused =
                assertThrows(
                        InvalidDocumentException.class, () -> ViewStateStore.write(run, CHOSEN));
        assertEquals(reading.refusal().orElseThrow(), refused.getMessage());
        assertArrayEquals(damaged, Files.readAllBytes(run.viewStateFile()));
        assertEquals(before, tree(run.root()));
    }

    @Test
    @DisplayName(
            "not JSON, not UTF-8, a missing member, an unknown member, a number for a cutoff:"
                    + " each refused naming the member")
    void malformed() throws IOException {
        RunLayout run = run();
        Files.createDirectories(run.resultsDirectory());
        Map<String, String> members = new TreeMap<>();
        members.put("{\"schemaVersion\": 1, \"psmQValueFilter\": \"0.01\"}\n", "");
        members.put(
                "{\"schemaVersion\": 1, \"psmQValueFilter\": \"0.01\","
                        + " \"peptideQValueFilter\": \"0.01\", \"sort\": \"score\"}\n",
                "");
        members.put(document("1", "\"0.01\""), "psmQValueFilter");
        members.put(document("0.01", "\"0.01\""), "");
        members.put("{\"psmQValueFilter\": \"0.01\", \"peptideQValueFilter\": \"0.01\"}\n", "");
        members.put("not json", "");
        members.put("[]\n", "");
        for (Map.Entry<String, String> member : members.entrySet()) {
            Files.writeString(run.viewStateFile(), member.getKey());
            InvalidDocumentException refused =
                    assertThrows(
                            InvalidDocumentException.class,
                            () -> ViewStateStore.write(run, CHOSEN),
                            member.getKey());
            assertEquals(member.getValue(), refused.member(), member.getKey());
            assertEquals(member.getKey(), Files.readString(run.viewStateFile()));
            ViewStateReading reading = ViewStateStore.read(run);
            assertEquals(
                    ViewStateReading.Source.DEFAULTS_FILE_REFUSED,
                    reading.source(),
                    member.getKey());
            assertTrue(
                    reading.refusal().orElseThrow().startsWith(run.viewStateFile().toString()),
                    reading.refusal()::toString);
        }
        Files.write(run.viewStateFile(), new byte[] {'{', (byte) 0xC3, '}'});
        assertEquals(
                Optional.of(run.viewStateFile() + " is not valid: the document is not UTF-8 text"),
                ViewStateStore.read(run).refusal());
    }

    @Test
    @DisplayName("a reading's parts must agree")
    void readingInvariants() {
        assertAll(
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new ViewStateReading(
                                                DisplayFilters.DEFAULTS,
                                                ViewStateReading.Source.DEFAULTS_FILE_REFUSED,
                                                Optional.empty())),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new ViewStateReading(
                                                DisplayFilters.DEFAULTS,
                                                ViewStateReading.Source.SAVED,
                                                Optional.of("why"))),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new ViewStateReading(
                                                CHOSEN,
                                                ViewStateReading.Source.DEFAULTS_NOTHING_SAVED,
                                                Optional.empty())),
                () ->
                        assertEquals(
                                CHOSEN,
                                new ViewStateReading(
                                                CHOSEN,
                                                ViewStateReading.Source.SAVED,
                                                Optional.empty())
                                        .filters()));
    }
}
