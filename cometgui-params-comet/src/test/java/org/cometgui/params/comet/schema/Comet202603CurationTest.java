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

package org.cometgui.params.comet.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.parser.ParseResult;
import org.cometgui.params.comet.validation.CometValidator;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.Rule;
import org.cometgui.params.comet.validation.ValidationReport;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the bundled metadata says about Comet 2026.03.0, and that each version-scoped fact reaches
 * the code that uses it -- the parser's defaults, the choice rule, the canonical writer -- for that
 * version only (COMET-2026-03 unit 1, decision C-2: version facts are data, never an {@code if
 * (version ...)}).
 *
 * <p>Every fact asserted here was established by running the real binaries
 * (docs/developer/comet_parameter_schema.rst, <em>Comet 2026.03.0</em>) and is typed in, not read
 * back from the metadata under test.
 */
class Comet202603CurationTest {

    private static final CuratedMetadata BUNDLED = MetadataLoader.loadBundled();

    private static final ToolVersion V2026_03_0 = ToolVersion.parse("2026.03.0");

    private static final ToolVersion V2026_02_2 = ToolVersion.parse("2026.02.2");

    private static String dump(String version) throws IOException {
        return Files.readString(
                CometFixtures.file(
                        version, CometFixtures.LINUX_X86_64, CometFixtures.Mode.COMPLETE),
                StandardCharsets.UTF_8);
    }

    private static List<String> values(ParameterDefinition definition) {
        return definition.choices().stream().map(Choice::value).toList();
    }

    private static ParameterDefinition at(String name, ToolVersion version) {
        return BUNDLED.parameter(name, version).orElseThrow();
    }

    private static CometParameters parse(ToolVersion version, String text) {
        ParseResult result = new CometParamsParser(BUNDLED, version).parse(text);
        assertTrue(result.succeeded(), () -> "must parse: " + result.errors());
        return result.model().orElseThrow();
    }

    private static List<Finding> choiceFindings(CometParameters model, String name) {
        ValidationReport report = CometValidator.standard().validate(model);
        return report.findings().stream()
                .filter(f -> f.rule() == Rule.CHOICE_NOT_LISTED)
                .filter(f -> f.parameters().contains(name))
                .toList();
    }

    @Test
    @DisplayName("the 2026.03.0 record: marker, pages, source, and 2026.02.2's tuple layout")
    void theVersionRecord() {
        CometVersionRecord record = BUNDLED.version(V2026_03_0).orElseThrow();
        assertEquals("2026.03 rev. 0 (fa08489)", record.marker().text());
        assertEquals(V2026_03_0, record.marker().toolVersion());
        assertEquals(
                "https://uwpr.github.io/Comet/parameters/parameters_202603/",
                record.parameterPages());
        assertEquals("https://github.com/UWPR/Comet/tree/v2026.03.0", record.source());
        assertEquals(
                "https://github.com/UWPR/Comet/blob/v2026.03.0/Comet.cpp#L556-L625",
                record.variableModTuple().source());
        assertEquals(
                BUNDLED.version(V2026_02_2).orElseThrow().variableModTuple().fields(),
                record.variableModTuple().fields(),
                "the 2026.03.0 reader takes the same eight fields with the same two pairs");
        Set<String> overridden =
                new TreeSet<>(
                        Set.of(
                                "add_U_selenocysteine",
                                "decoy_search",
                                "index_search_type",
                                "output_txtfile",
                                "spectral_library_ms_level"));
        for (int slot = 1; slot <= 15; slot++) {
            overridden.add(String.format(java.util.Locale.ROOT, "variable_mod%02d", slot));
        }
        assertEquals(overridden, record.overrides().keySet());
        assertEquals(Map.of("index_search_type", "-1"), record.defaults());
        for (ParameterOverride override : record.overrides().values()) {
            assertTrue(
                    override.source().startsWith("https://github.com/UWPR/Comet/blob/v2026.03.0/"),
                    override::toString);
        }
    }

    @Test
    @DisplayName("variable_modNN's help names ^ and $ for 2026.03.0 only")
    void variableModHelpIsVersionScoped() {
        for (int slot = 1; slot <= 15; slot++) {
            String name = String.format(java.util.Locale.ROOT, "variable_mod%02d", slot);
            ParameterDefinition newer = at(name, V2026_03_0);
            ParameterDefinition older = at(name, V2026_02_2);
            assertTrue(
                    newer.shortHelp()
                            .contains("^ and $, new in Comet 2026.03.0, for the protein N-"),
                    newer.shortHelp());
            assertTrue(newer.shortHelp().contains("Comet 2026.03.0 refuses any other value"));
            assertTrue(older.shortHelp().contains("(n and c for termini)"), older.shortHelp());
            assertTrue(!older.shortHelp().contains("^"), older.shortHelp());
            assertEquals(
                    "https://uwpr.github.io/Comet/parameters/parameters_202603/variable_modXX.html",
                    newer.detailedHelpRef());
            assertEquals(
                    "https://uwpr.github.io/Comet/parameters/parameters_202602/variable_modXX.html",
                    older.detailedHelpRef());
            assertEquals(older.defaultValue(), newer.defaultValue());
        }
    }

    @Test
    @DisplayName("output_txtfile: each release's comment names that release, which treats 2 as 1")
    void outputTxtfileCommentNamesItsOwnRelease() {
        ParameterDefinition newer = at("output_txtfile", V2026_03_0);
        ParameterDefinition older = at("output_txtfile", V2026_02_2);
        assertEquals(
                Optional.of("0=no, 1=yes  write tab-delimited txt file (2026.03.0 treats 2 as 1)"),
                newer.inlineComment());
        assertEquals(
                Optional.of("0=no, 1=yes  write tab-delimited txt file (2026.02.2 treats 2 as 1)"),
                older.inlineComment());
        assertTrue(newer.shortHelp().contains("the 2026.03.0 parameter code"), newer.shortHelp());
        assertTrue(!newer.shortHelp().contains("2026.02.2"), newer.shortHelp());
        assertEquals(values(older), values(newer));
    }

    @Test
    @DisplayName("index_search_type: -1 is the default and a choice for 2026.03.0 only")
    void indexSearchTypeIsVersionScoped() {
        ParameterDefinition newer = at("index_search_type", V2026_03_0);
        ParameterDefinition older = at("index_search_type", V2026_02_2);
        assertEquals("-1", newer.defaultValue());
        assertEquals(List.of("-1", "0", "1"), values(newer));
        assertEquals(
                Optional.of(
                        "0=create peptide index, 1=create fragment ion index; if .idx specified"
                                + " but not present"),
                newer.inlineComment());
        assertEquals(
                "https://uwpr.github.io/Comet/parameters/parameters_202603/index_search_type.html",
                newer.detailedHelpRef());
        assertTrue(newer.shortHelp().contains("-1, the default, means not set"), newer.shortHelp());

        assertEquals("1", older.defaultValue());
        assertEquals(List.of("0", "1"), values(older));
        assertEquals(
                Optional.of("0=peptide index (PI_DB), 1=fragment ion index (FI_DB, default)"),
                older.inlineComment());
        assertEquals(
                "https://uwpr.github.io/Comet/parameters/parameters_202602/index_search_type.html",
                older.detailedHelpRef());
        assertEquals(older, BUNDLED.parameter("index_search_type").orElseThrow());
    }

    @Test
    @DisplayName("spectral_library_ms_level and add_U_selenocysteine say what each release does")
    void theTwoFixedNamesAreDescribedPerVersion() {
        ParameterDefinition levelNewer = at("spectral_library_ms_level", V2026_03_0);
        ParameterDefinition levelOlder = at("spectral_library_ms_level", V2026_02_2);
        assertTrue(levelOlder.shortHelp().contains("ignored by that release"));
        assertTrue(
                levelNewer.shortHelp().contains("Comet 2026.03.0 reads this name")
                        && levelNewer.shortHelp().contains("no effect on a search"),
                levelNewer.shortHelp());
        assertEquals(
                Optional.of(
                        "read by Comet 2026.03.0 but used only for a .raw spectral library, which"
                                + " it refuses"),
                levelNewer.inlineComment());
        assertEquals(List.of("1", "2", "3"), values(levelNewer));

        ParameterDefinition seleniumNewer = at("add_U_selenocysteine", V2026_03_0);
        ParameterDefinition seleniumOlder = at("add_U_selenocysteine", V2026_02_2);
        assertTrue(seleniumOlder.shortHelp().contains("has no effect in that release"));
        assertEquals(
                "Mass added to every selenocysteine (U) residue; 0.0 means no modification.",
                seleniumNewer.shortHelp());
        assertEquals(
                Optional.of("added to U - avg. 150.0379, mono. 150.95363"),
                seleniumNewer.inlineComment());
        assertEquals(
                Optional.of(
                        "added to U - avg. 150.0379, mono. 150.95363; ignored by Comet 2026.02.2"),
                seleniumOlder.inlineComment());
    }

    @Test
    @DisplayName("each version's inline comments are its own -q comments, but for typed deviations")
    void inlineCommentsAreEachVersionsOwn() throws IOException {
        assertEquals(
                Set.of(
                        "add_U_selenocysteine",
                        "isotope_error",
                        "output_txtfile",
                        "spectral_library_ms_level"),
                deviations(CometFixtures.COMET_2026_02_2));
        assertEquals(
                Set.of("isotope_error", "output_txtfile", "spectral_library_ms_level"),
                deviations(CometFixtures.COMET_2026_03_0));
    }

    private static Set<String> deviations(String version) throws IOException {
        DiscoveredSchema discovered =
                SchemaDiscovery.discover(dump(version), DiscoveryMode.COMPLETE);
        Set<String> deviating = new TreeSet<>();
        int commented = 0;
        for (DiscoveredParameter declared : discovered.parameters()) {
            ParameterDefinition definition =
                    BUNDLED.parameter(declared.name(), ToolVersion.parse(version)).orElseThrow();
            commented += declared.inlineComment().isPresent() ? 1 : 0;
            if (!definition.inlineComment().equals(declared.inlineComment())) {
                deviating.add(declared.name());
            }
        }
        assertEquals(87, commented, "Comet " + version + " -q comments 87 of its 118 parameters");
        return deviating;
    }

    @Test
    @DisplayName("the real 2026.03.0 -q parses for 2026.03.0, and -1 passes the choice rule there")
    void theReal202603FileValidatesItsOwnDefault() throws IOException {
        CometParameters model = parse(V2026_03_0, dump(CometFixtures.COMET_2026_03_0));
        assertEquals("-1", model.text("index_search_type"));
        assertEquals(List.of(), choiceFindings(model, "index_search_type"));
        assertEquals(
                List.of(),
                CometValidator.standard().validate(model).findings().stream()
                        .filter(f -> f.rule() == Rule.CHOICE_NOT_LISTED)
                        .toList(),
                "no parameter of Comet's own 2026.03.0 file is outside its 2026.03.0 choices");
        List<Finding> two =
                choiceFindings(
                        model.withText("index_search_type", "2", ValueOrigin.USER),
                        "index_search_type");
        assertEquals(1, two.size(), two::toString);
        assertTrue(two.get(0).message().contains("use one of -1 ("), two.get(0).message());
    }

    @Test
    @DisplayName("index_search_type = -1 for 2026.02.2 is refused by the choice rule")
    void minusOneIsNotA202602Choice() throws IOException {
        String real = dump(CometFixtures.COMET_2026_02_2);
        String line = "index_search_type = 1 ";
        assertEquals(1, real.split(Pattern.quote(line), -1).length - 1);
        CometParameters model = parse(V2026_02_2, real.replace(line, "index_search_type = -1"));
        assertEquals("-1", model.text("index_search_type"));
        List<Finding> findings = choiceFindings(model, "index_search_type");
        assertEquals(1, findings.size(), findings::toString);
        assertEquals(
                "index_search_type = -1 is not one of its values; use one of 0 (Peptide index"
                        + " (PI_DB)), 1 (Fragment-ion index (FI_DB))",
                findings.get(0).message());
    }

    @Test
    @DisplayName("the canonical writer writes each version's own default line")
    void theWriterUsesTheVersionsOwnDefinition() throws IOException {
        CometParameters real = parse(V2026_03_0, dump(CometFixtures.COMET_2026_03_0));
        CanonicalParamsWriter writer = new CanonicalParamsWriter(ParamsFiles.build());
        String newer =
                writer.write(CometParameters.defaults(BUNDLED, V2026_03_0, real.enzymeTable()));
        String older =
                writer.write(CometParameters.defaults(BUNDLED, V2026_02_2, real.enzymeTable()));
        assertTrue(
                newer.contains(
                        "\nindex_search_type = -1                 # 0=create peptide index,"
                                + " 1=create fragment ion index; if .idx specified but not"
                                + " present\n"),
                newer);
        assertTrue(
                newer.contains(
                        "\nadd_U_selenocysteine = 0.0000          # added to U - avg. 150.0379,"
                                + " mono. 150.95363\n"),
                newer);
        assertTrue(
                older.contains(
                        "\nindex_search_type = 1                  # 0=peptide index (PI_DB),"
                                + " 1=fragment ion index (FI_DB, default)\n"),
                older);
        assertTrue(
                older.contains(
                        "\nadd_U_selenocysteine = 0.0000          # added to U - avg. 150.0379,"
                                + " mono. 150.95363; ignored by Comet 2026.02.2\n"),
                older);
        assertTrue(newer.startsWith("# comet_version 2026.03 rev. 0 (fa08489)\n"));
    }
}
