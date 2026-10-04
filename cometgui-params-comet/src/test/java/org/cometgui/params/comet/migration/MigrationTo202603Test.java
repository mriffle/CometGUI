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

package org.cometgui.params.comet.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.fixtures.DeclarationLines;
import org.cometgui.params.comet.fixtures.MigrationFixtures;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.parser.ParseResult;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;
import org.cometgui.params.comet.validation.CometValidator;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.Rule;
import org.cometgui.params.comet.validation.ValidationReport;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Schema migration to Comet 2026.03.0 from the REAL fixtures of the two older curated releases:
 * Comet 2026.02.2's {@code -q} and {@code -p} files and Comet 2024.01.0's, each through {@link
 * SchemaMigration#migrateFile}. The expected outcomes are derived from the real dumps here
 * <strong>and</strong> typed by hand, so each checks the other.
 *
 * <p>The value-migration entries of 2026.03.0's version record (Comet 2026.02.2's {@code
 * index_search_type = 1} written {@code -1}; {@code 0} carried with a notice; a terminal distance
 * below -2 written {@code -1}; a terminus Comet never applied flagged) are proved on the real files
 * where the real files hold them, and otherwise on CONSTRUCTED one-line edits of the real {@code
 * -q} files (test input, not Comet's output), each labelled here.
 *
 * <p>{@link Written} pins the canonical files of the four migrated sets, byte for byte, to the
 * files under {@code fixtures/comet-migrated/2026.03.0/}: the bytes {@code
 * MigratedFileRealBinaryTest} gives the real 2026.03.0 binary. That test runs no production class
 * (PIT would re-run its searches for every mutant it covered); this one proves the bytes it runs
 * are what migration and the writer produce.
 */
class MigrationTo202603Test {

    static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    static final ToolVersion V2026_03 = ToolVersion.parse(CometFixtures.COMET_2026_03_0);

    static final ToolVersion V2026_02 = ToolVersion.parse(CometFixtures.COMET_2026_02_2);

    static final ToolVersion V2024_01 = ToolVersion.parse(MigrationFixtures.VERSION);

    /** Where the migrated canonical files are, on the test class path. */
    static final String WRITTEN = "/fixtures/comet-migrated/2026.03.0/";

    /** One real fixture migrated to 2026.03.0. */
    record Source(ToolVersion version, CometFixtures.Mode mode) {

        String text() {
            try {
                return version.equals(V2024_01)
                        ? MigrationFixtures.text(mode)
                        : new String(
                                CometFixtures.bytes(
                                        version.text(), CometFixtures.LINUX_X86_64, mode),
                                StandardCharsets.UTF_8);
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }

        /** The migrated canonical file's name under {@link #WRITTEN}. */
        String writtenName() {
            return "from-" + version.text() + "-" + mode.fileName().substring("comet-".length());
        }

        MigrationResult migrate() {
            return SchemaMigration.migrateFile(METADATA, text(), V2026_03);
        }

        @Override
        public String toString() {
            return "Comet " + version.text() + " " + mode.fileName();
        }
    }

    static final List<Source> SOURCES =
            List.of(
                    new Source(V2026_02, CometFixtures.Mode.COMPLETE),
                    new Source(V2026_02, CometFixtures.Mode.DEFAULTS),
                    new Source(V2024_01, CometFixtures.Mode.COMPLETE),
                    new Source(V2024_01, CometFixtures.Mode.DEFAULTS));

    static Stream<Source> sources() {
        return SOURCES.stream();
    }

    /** The canonical file of a migrated set, as the module's own tests write one. */
    static byte[] written(MigrationResult result) {
        return new CanonicalParamsWriter(ParamsFiles.build()).bytes(result.model());
    }

    /** The parameters a release's real {@code -q} dump declares, in its order. */
    private static List<String> declared(ToolVersion version) {
        try {
            List<String> lines =
                    version.equals(V2024_01)
                            ? MigrationFixtures.lines(CometFixtures.Mode.COMPLETE)
                            : CometFixtures.lines(
                                    version.text(),
                                    CometFixtures.LINUX_X86_64,
                                    CometFixtures.Mode.COMPLETE);
            return DeclarationLines.names(lines);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** Each declared parameter's value text in a release's real {@code -q} dump. */
    private static Map<String, String> dumpValues(ToolVersion version) {
        Map<String, String> values = new LinkedHashMap<>();
        CometParameters model =
                parse(version, new Source(version, CometFixtures.Mode.COMPLETE).text());
        for (String name : declared(version)) {
            values.put(name, model.text(name));
        }
        return values;
    }

    private static CometParameters parse(ToolVersion version, String text) {
        ParseResult parsed = new CometParamsParser(METADATA, version).parse(text);
        return parsed.model()
                .orElseThrow(() -> new AssertionError("does not parse: " + parsed.errors()));
    }

    /** {@code a} minus {@code b}, in {@code a}'s order. */
    private static List<String> minus(List<String> a, List<String> b) {
        Set<String> without = new LinkedHashSet<>(a);
        without.removeAll(b);
        return new ArrayList<>(without);
    }

    private List<String> findings(ValidationReport report) {
        return report.findings().stream()
                .map(f -> f.severity() + " " + f.rule().id() + " " + f.parameters())
                .toList();
    }

    /** Typed by hand: the one value 2026.02.2's real files hold that 2026.03.0 writes otherwise. */
    static final List<String> CONVERTED_FROM_2026_02 = List.of("index_search_type");

    /** Typed by hand: the nine parameters 2024.01.0 lacks, in 2026.03.0's schema order. */
    static final List<String> ADDED_FROM_2024_01 = Sets.NEW_IN_2026;

    @Nested
    @DisplayName("the real files, migrated")
    class RealFiles {

        @ParameterizedTest
        @MethodSource("org.cometgui.params.comet.migration.MigrationTo202603Test#sources")
        @DisplayName("every parameter has one entry, and the outcomes are the dumps' differences")
        void outcomes(Source source) {
            MigrationResult result = source.migrate();
            MigrationReport report = result.report();
            System.out.println("[migration " + source + "] " + report.describe());
            assertEquals(source.version(), report.from());
            assertEquals(V2026_03, report.to());
            assertEquals(118, report.entries().size());

            List<String> newer = declared(V2026_03);
            List<String> older = declared(source.version());
            List<String> added = minus(newer, older);
            assertEquals(added, report.names(MigrationEntry.Outcome.ADDED), "derived from dumps");
            assertEquals(List.of(), minus(older, newer));
            assertEquals(List.of(), report.names(MigrationEntry.Outcome.REMOVED_KEPT_AS_UNKNOWN));

            // Derived: the parameters both dumps declare whose own -q values differ.
            Map<String, String> before = dumpValues(source.version());
            Map<String, String> after = dumpValues(V2026_03);
            List<String> differing = new ArrayList<>();
            for (String name : older) {
                if (!before.get(name).equals(after.get(name))) {
                    differing.add(name);
                }
            }
            if (source.version().equals(V2026_02)) {
                assertEquals(List.of(), added);
                assertEquals(CONVERTED_FROM_2026_02, differing, "typed by hand against the dumps");
                assertEquals(differing, report.names(MigrationEntry.Outcome.CONVERTED));
                assertEquals(117, report.names(MigrationEntry.Outcome.CARRIED).size());
            } else {
                assertEquals(ADDED_FROM_2024_01, added, "typed by hand against the dumps");
                assertEquals(List.of(), report.names(MigrationEntry.Outcome.CONVERTED));
                assertEquals(109, report.names(MigrationEntry.Outcome.CARRIED).size());
            }
            for (MigrationEntry.Outcome outcome :
                    List.of(
                            MigrationEntry.Outcome.RESHAPED,
                            MigrationEntry.Outcome.NOTED,
                            MigrationEntry.Outcome.NEEDS_ATTENTION,
                            MigrationEntry.Outcome.UNKNOWN_ADOPTED,
                            MigrationEntry.Outcome.UNKNOWN_CARRIED)) {
                assertEquals(List.of(), report.names(outcome), outcome.toString());
            }
            for (MigrationEntry entry : report.entries()) {
                assertFalse(entry.explanation().isBlank(), entry.parameter());
                assertEquals(result.model().text(entry.parameter()), entry.targetText());
                if (entry.outcome() == MigrationEntry.Outcome.CARRIED) {
                    assertEquals(
                            Optional.of(result.source().text(entry.parameter())),
                            entry.sourceText());
                    assertEquals(
                            result.source().origin(entry.parameter()),
                            result.model().origin(entry.parameter()));
                }
            }
        }

        @Test
        @DisplayName("2026.02.2's index_search_type = 1 is converted to -1, with its reason")
        void indexSearchTypeIsConverted() {
            for (CometFixtures.Mode mode : CometFixtures.Mode.values()) {
                MigrationResult result = new Source(V2026_02, mode).migrate();
                assertEquals("1", result.source().text("index_search_type"), mode.toString());
                MigrationEntry entry = result.report().entry("index_search_type").orElseThrow();
                assertEquals(MigrationEntry.Outcome.CONVERTED, entry.outcome());
                assertEquals(Optional.of("1"), entry.sourceText());
                assertEquals("-1", entry.targetText());
                assertEquals("-1", result.model().text("index_search_type"));
                assertEquals(ValueOrigin.IMPORTED, result.model().origin("index_search_type"));
                assertTrue(
                        entry.explanation()
                                .startsWith(
                                        "index_search_type = 1 (Comet 2026.02.2) is written -1 for"
                                                + " Comet 2026.03.0, which means the same there:"
                                                + " Comet 2026.02.2 reads index_search_type only"),
                        entry.explanation());
                assertTrue(
                        entry.explanation()
                                .endsWith(
                                        "(https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/"
                                                + "CometSearchManager.cpp#L1524)"),
                        entry.explanation());
                assertTrue(
                        result.report()
                                .describe()
                                .contains("\nCONVERTED index_search_type: index_search_type = 1"),
                        result.report().describe());
                assertEquals(List.of(entry), result.report().changes());
            }
        }

        @Test
        @DisplayName("2024.01.0 has no index_search_type: it is added at 2026.03.0's -1")
        void indexSearchTypeIsAddedFrom2024() {
            MigrationResult result = new Source(V2024_01, CometFixtures.Mode.COMPLETE).migrate();
            MigrationEntry entry = result.report().entry("index_search_type").orElseThrow();
            assertEquals(MigrationEntry.Outcome.ADDED, entry.outcome());
            assertEquals("-1", entry.targetText());
            assertEquals(ValueOrigin.COMET_DEFAULT, result.model().origin("index_search_type"));
            assertEquals(
                    "index_search_type is new in Comet 2026.03.0 (Comet 2024.01.0 has no such"
                            + " parameter) and takes its default",
                    entry.explanation());
        }

        @Test
        @DisplayName("migrated 2026.02.2 -q holds exactly 2026.03.0's own -q values")
        void theMigratedQFileIsTheNewReleasesOwn() {
            CometParameters migrated =
                    new Source(V2026_02, CometFixtures.Mode.COMPLETE).migrate().model();
            CometParameters own =
                    parse(V2026_03, new Source(V2026_03, CometFixtures.Mode.COMPLETE).text());
            for (ParameterEntry entry : own.entries()) {
                assertEquals(own.value(entry.name()), migrated.value(entry.name()), entry.name());
            }
            assertEquals(own.enzymeTable(), migrated.enzymeTable());
            CanonicalParamsWriter writer = new CanonicalParamsWriter(ParamsFiles.build());
            assertEquals(writer.write(own), writer.write(migrated));
        }

        @ParameterizedTest
        @MethodSource("org.cometgui.params.comet.migration.MigrationTo202603Test#sources")
        @DisplayName("each migrated set writes, re-parses as 2026.03.0, and warns about nothing")
        void writesParsesValidates(Source source) {
            CometParameters migrated = source.migrate().model();
            String text = new CanonicalParamsWriter(ParamsFiles.build()).write(migrated);
            assertTrue(text.startsWith("# comet_version 2026.03 rev. 0 (fa08489)\n"), text);
            ParseResult again = new CometParamsParser(METADATA, V2026_03).parse(text);
            assertEquals(List.of(), again.diagnostics());
            CometParameters reparsed = again.model().orElseThrow();
            for (ParameterEntry entry : migrated.entries()) {
                assertEquals(migrated.value(entry.name()), reparsed.value(entry.name()));
            }
            ValidationReport report = CometValidator.standard().validate(migrated);
            assertEquals(
                    List.of("ERROR workflow_enforced.output_off [output_percolatorfile]"),
                    findings(report),
                    "the -q and -p files switch the PIN off, which the workflow forces on");
            assertEquals(
                    List.of(),
                    CometValidator.standard()
                            .validate(migrated.withWorkflowEnforcedOutputs())
                            .findings());
        }

        @Test
        @DisplayName("carried unmigrated, 1 would draw 2026.03.0's warning; migrated, it does not")
        void theWarningIsGone() {
            CometParameters migrated =
                    new Source(V2026_02, CometFixtures.Mode.COMPLETE)
                            .migrate()
                            .model()
                            .withWorkflowEnforcedOutputs()
                            .withText("database_name", "/data/human.fasta", ValueOrigin.USER);
            assertEquals(List.of(), CometValidator.standard().validate(migrated).findings());
            CometParameters carried =
                    migrated.withText("index_search_type", "1", ValueOrigin.IMPORTED);
            assertEquals(
                    List.of(
                            "WARNING index_search_type.ignored_without_idx [index_search_type,"
                                    + " database_name]"),
                    findings(CometValidator.standard().validate(carried)));
        }
    }

    @Nested
    @DisplayName("CONSTRUCTED one-line edits of the real -q files")
    class Edits {

        private MigrationResult migrated(ToolVersion version, String name, String value) {
            String text = new Source(version, CometFixtures.Mode.COMPLETE).text();
            return SchemaMigration.migrateFile(
                    METADATA, Sets.replacing(text, name + " =", name + " = " + value), V2026_03);
        }

        @Test
        @DisplayName("index_search_type = 0 is carried with a notice; 2026.03.0 warns on FASTA")
        void zeroIsNoted() {
            MigrationResult result = migrated(V2026_02, "index_search_type", "0");
            MigrationEntry entry = result.report().entry("index_search_type").orElseThrow();
            assertEquals(MigrationEntry.Outcome.NOTED, entry.outcome());
            assertEquals(Optional.of("0"), entry.sourceText());
            assertEquals("0", entry.targetText());
            assertEquals("0", result.model().text("index_search_type"));
            assertTrue(
                    entry.explanation()
                            .startsWith(
                                    "index_search_type = 0 means the same in Comet 2026.02.2 and"
                                            + " Comet 2026.03.0, but: 0 (build a peptide index"),
                    entry.explanation());
            assertTrue(entry.explanation().contains("#L1764-L1775)"), entry.explanation());
            assertEquals(List.of(entry), result.report().changes());
            assertTrue(result.report().describe().contains("\nNOTED index_search_type: "));
            ValidationReport report =
                    CometValidator.standard()
                            .validate(
                                    result.model()
                                            .withWorkflowEnforcedOutputs()
                                            .withText(
                                                    "database_name",
                                                    "/data/human.fasta",
                                                    ValueOrigin.USER));
            assertEquals(
                    List.of(
                            "WARNING index_search_type.ignored_without_idx [index_search_type,"
                                    + " database_name]"),
                    findings(report));
        }

        @Test
        @DisplayName("an active slot's distance -3 is converted to -1, from either older release")
        void distanceBelowMinusTwo() {
            for (ToolVersion version : List.of(V2026_02, V2024_01)) {
                MigrationResult result =
                        migrated(version, "variable_mod01", "15.9949 M 0 3 -3 0 0 0.0");
                MigrationEntry entry = result.report().entry("variable_mod01").orElseThrow();
                assertEquals(MigrationEntry.Outcome.CONVERTED, entry.outcome(), version.text());
                assertEquals(Optional.of("15.9949 M 0 3 -3 0 0 0.0"), entry.sourceText());
                assertEquals("15.9949 M 0 3 -1 0 0 0.0", entry.targetText());
                assertEquals("15.9949 M 0 3 -1 0 0 0.0", result.model().text("variable_mod01"));
                assertTrue(
                        entry.explanation()
                                .startsWith(
                                        "variable_mod01 = 15.9949 M 0 3 -3 0 0 0.0 (Comet "
                                                + version.text()
                                                + ") is written 15.9949 M 0 3 -1 0 0 0.0 for Comet"
                                                + " 2026.03.0, which means the same there: Comet "
                                                + version.text()
                                                + " reads a terminal distance below -2 as -1"),
                        entry.explanation());
                assertTrue(
                        entry.explanation().contains("one-exclusive-modification-per-peptide"),
                        entry.explanation());
                assertTrue(
                        entry.explanation().contains("/blob/v" + version.text() + "/"),
                        entry.explanation());
                assertEquals(
                        List.of(),
                        CometValidator.standard()
                                .validate(result.model())
                                .of(Rule.VARMOD_DISTANCE_UNDOCUMENTED));
            }
        }

        @Test
        @DisplayName("an unused slot's distance -3 is carried: 2026.03.0 ignores the slot")
        void anUnusedSlotIsCarried() {
            MigrationResult result = migrated(V2026_02, "variable_mod02", "0.0 X 0 3 -3 0 0 0.0");
            MigrationEntry entry = result.report().entry("variable_mod02").orElseThrow();
            assertEquals(MigrationEntry.Outcome.CARRIED, entry.outcome());
            assertEquals("0.0 X 0 3 -3 0 0 0.0", result.model().text("variable_mod02"));
        }

        @Test
        @DisplayName("a terminus Comet never applied needs attention, from either older release")
        void terminusOutsideZeroToThree() {
            for (ToolVersion version : List.of(V2026_02, V2024_01)) {
                MigrationResult result =
                        migrated(version, "variable_mod02", "15.9949 M 0 3 2 4 0 0.0");
                MigrationEntry entry = result.report().entry("variable_mod02").orElseThrow();
                assertEquals(MigrationEntry.Outcome.NEEDS_ATTENTION, entry.outcome());
                assertEquals(Optional.of("15.9949 M 0 3 2 4 0 0.0"), entry.sourceText());
                assertEquals("0.0 X 0 3 -1 0 0 0.0", entry.targetText());
                assertEquals("0.0 X 0 3 -1 0 0 0.0", result.model().text("variable_mod02"));
                assertEquals(ValueOrigin.COMET_DEFAULT, result.model().origin("variable_mod02"));
                assertTrue(
                        entry.explanation()
                                .startsWith(
                                        "variable_mod02 = 15.9949 M 0 3 2 4 0 0.0 (Comet "
                                                + version.text()
                                                + ") has no equivalent in Comet 2026.03.0: With a"
                                                + " terminal distance of 0 or more"),
                        entry.explanation());
                assertTrue(
                        entry.explanation()
                                .endsWith(
                                        "; the migrated set holds Comet 2026.03.0's default"
                                                + " instead, and the source value is kept only in"
                                                + " this report"),
                        entry.explanation());
                assertEquals(List.of(entry), result.report().needingAttention());
            }
        }

        @Test
        @DisplayName("a terminus outside 0-3 with distance -1 is carried: no release refuses it")
        void terminusWithoutDistanceIsCarried() {
            MigrationResult result =
                    migrated(V2026_02, "variable_mod01", "15.9949 M 0 3 -1 4 0 0.0");
            assertEquals(
                    MigrationEntry.Outcome.CARRIED,
                    result.report().entry("variable_mod01").orElseThrow().outcome());
        }

        @Test
        @DisplayName("AScorePro with slot 10 active is carried, and validation still reports it")
        void ascoreProIsNotHidden() {
            MigrationResult result =
                    migrated(V2026_02, "variable_mod10", "79.966331 STY 0 3 -1 0 0 0.0");
            assertEquals(
                    MigrationEntry.Outcome.CARRIED,
                    result.report().entry("variable_mod10").orElseThrow().outcome());
            assertEquals("1", result.model().text("print_ascorepro_score"));
            List<Finding> errors =
                    CometValidator.standard()
                            .validate(result.model().withWorkflowEnforcedOutputs())
                            .errors();
            assertEquals(
                    List.of("variable_mods.ascorepro_slot_unsupported"),
                    errors.stream().map(f -> f.rule().id()).toList());
        }
    }

    @Nested
    @DisplayName("the entries are keyed by the source release")
    class Keyed {

        @Test
        @DisplayName("2026.03.0 to 2026.02.2: -1 is written 1, and the round trip restores 1")
        void backToOlder() {
            CometParameters own =
                    parse(V2026_03, new Source(V2026_03, CometFixtures.Mode.COMPLETE).text());
            MigrationResult back = SchemaMigration.migrate(own, V2026_02);
            MigrationEntry entry = back.report().entry("index_search_type").orElseThrow();
            assertEquals(MigrationEntry.Outcome.CONVERTED, entry.outcome());
            assertEquals("1", back.model().text("index_search_type"));
            assertEquals(
                    List.of("index_search_type"),
                    back.report().names(MigrationEntry.Outcome.CONVERTED));

            CometParameters older =
                    parse(V2026_02, new Source(V2026_02, CometFixtures.Mode.COMPLETE).text());
            CometParameters there = SchemaMigration.migrate(older, V2026_03).model();
            CometParameters again = SchemaMigration.migrate(there, V2026_02).model();
            for (ParameterEntry each : older.entries()) {
                assertEquals(older.value(each.name()), again.value(each.name()), each.name());
            }
        }

        @Test
        @DisplayName(
                "a 2026.03.0 set migrated to 2026.03.0 keeps 1 and -3: no entry is from itself")
        void sameRelease() {
            CometParameters own =
                    parse(V2026_03, new Source(V2026_03, CometFixtures.Mode.COMPLETE).text())
                            .withText("index_search_type", "1", ValueOrigin.USER)
                            .withText(
                                    "variable_mod01", "15.9949 M 0 3 -3 0 0 0.0", ValueOrigin.USER);
            MigrationReport report = SchemaMigration.migrate(own, V2026_03).report();
            assertEquals(118, report.names(MigrationEntry.Outcome.CARRIED).size());
            assertEquals(List.of(), report.changes());
        }

        @Test
        @DisplayName("2024.01.0 to 2026.02.2: a distance of -3 is carried; 2026.02.2 states none")
        void otherTarget() {
            String text = new Source(V2024_01, CometFixtures.Mode.COMPLETE).text();
            MigrationResult result =
                    SchemaMigration.migrateFile(
                            METADATA,
                            Sets.replacing(
                                    text,
                                    "variable_mod01 =",
                                    "variable_mod01 = 15.9949 M 0 3 -3 0 0 0.0"),
                            V2026_02);
            assertEquals(
                    MigrationEntry.Outcome.CARRIED,
                    result.report().entry("variable_mod01").orElseThrow().outcome());
            assertEquals("15.9949 M 0 3 -3 0 0 0.0", result.model().text("variable_mod01"));
        }

        @Test
        @DisplayName("a value migration naming a rule that does not exist stops the migration")
        void anUnknownRuleIsRefused() {
            String json = bundledJson();
            String anchor = "\"rule\": \"variable_mod_tuple.terminus_undocumented\"";
            int at = json.indexOf(anchor);
            assertTrue(at > 0);
            CuratedMetadata damaged =
                    MetadataLoader.load(
                            json.substring(0, at)
                                    + "\"rule\": \"variable_mod_tuple.no_such_rule\""
                                    + json.substring(at + anchor.length()));
            CometParameters older =
                    parse(V2026_02, new Source(V2026_02, CometFixtures.Mode.COMPLETE).text());
            CometParameters source =
                    new CometParamsParser(damaged, V2026_02)
                            .parse(new Source(V2026_02, CometFixtures.Mode.COMPLETE).text())
                            .model()
                            .orElseThrow();
            assertNotEquals(older.metadata(), source.metadata());
            IllegalStateException refused =
                    assertThrows(
                            IllegalStateException.class,
                            () -> SchemaMigration.migrate(source, V2026_03));
            assertEquals(
                    "Comet 2026.03.0's version record migrates values by the rule"
                            + " \"variable_mod_tuple.no_such_rule\", which is not a rule",
                    refused.getMessage());
            // The other direction consults 2026.02.2's record, which names no rule.
            assertEquals(
                    V2026_02,
                    SchemaMigration.migrate(
                                    new CometParamsParser(damaged, V2026_03)
                                            .parse(
                                                    new Source(
                                                                    V2026_03,
                                                                    CometFixtures.Mode.COMPLETE)
                                                            .text())
                                            .model()
                                            .orElseThrow(),
                                    V2026_02)
                            .model()
                            .version());
        }

        private static String bundledJson() {
            try (var in = MetadataLoader.class.getResourceAsStream(MetadataLoader.RESOURCE)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }
    }

    @Nested
    @DisplayName("the migrated canonical files the real binary is given")
    class Written {

        @ParameterizedTest
        @MethodSource("org.cometgui.params.comet.migration.MigrationTo202603Test#sources")
        @DisplayName("migration and the writer produce exactly the checked-in bytes")
        void bytesAreTheCheckedInFile(Source source) throws IOException {
            byte[] produced = written(source.migrate());
            String resource = WRITTEN + source.writtenName();
            byte[] checkedIn = null;
            try (var in = MigrationTo202603Test.class.getResourceAsStream(resource)) {
                if (in != null) {
                    checkedIn = in.readAllBytes();
                }
            }
            if (!java.util.Arrays.equals(checkedIn, produced)) {
                java.nio.file.Path out =
                        java.nio.file.Path.of("target", "migrated-" + source.writtenName());
                java.nio.file.Files.write(out, produced);
                throw new AssertionError(
                        (checkedIn == null ? "the class path holds no " : "")
                                + resource
                                + (checkedIn == null ? "" : " is not what migration writes now")
                                + "; the produced bytes are in "
                                + out.toAbsolutePath()
                                + " -- review them before checking them in");
            }
            String text = new String(produced, StandardCharsets.UTF_8);
            assertEquals(
                    1,
                    text.lines().filter(l -> l.startsWith("index_search_type = -1 ")).count(),
                    "every migrated file writes index_search_type = -1");
            assertTrue(
                    java.util.Arrays.equals(
                            produced,
                            written(
                                    new Source(source.version(), CometFixtures.Mode.COMPLETE)
                                            .migrate())),
                    "a release's -p dump omits only parameters at its defaults, so its migrated"
                            + " file is its -q file's");
            assertFalse(text.contains("\nindex_search_type = 1"), text);
        }

        @Test
        @DisplayName("the four files are named for their source release and dump")
        void names() {
            assertEquals(
                    List.of(
                            "from-2026.02.2-q.params",
                            "from-2026.02.2-p.params",
                            "from-2024.01.0-q.params",
                            "from-2024.01.0-p.params"),
                    SOURCES.stream().map(Source::writtenName).toList());
        }
    }
}
