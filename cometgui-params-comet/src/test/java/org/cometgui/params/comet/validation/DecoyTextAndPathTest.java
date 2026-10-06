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

package org.cometgui.params.comet.validation;

import static org.cometgui.params.comet.validation.Models.assertAttached;
import static org.cometgui.params.comet.validation.Models.only;
import static org.cometgui.params.comet.validation.Models.validate;
import static org.cometgui.params.comet.validation.Models.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.SerializationRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@code R-DEC-01} at model level -- the decoy source as one value, and the decoy prefix -- plus
 * the form of every text Comet reads as one token and of every path. Values are CONSTRUCTED edits
 * of the real {@code -q} model.
 */
class DecoyTextAndPathTest {

    @Nested
    @DisplayName("decoys")
    class Decoys {

        @Test
        @DisplayName("each decoy source is one decoy_search value, and back")
        void mapping() {
            assertEquals(0, DecoySource.FASTA_CONTAINS_DECOYS.decoySearch());
            assertEquals(1, DecoySource.COMET_INTERNAL_CONCATENATED.decoySearch());
            assertEquals(2, DecoySource.COMET_INTERNAL_SEPARATE.decoySearch());
            for (DecoySource source : DecoySource.values()) {
                assertEquals(
                        Optional.of(source), DecoySource.fromDecoySearch(source.decoySearch()));
            }
            assertEquals(Optional.empty(), DecoySource.fromDecoySearch(3));
            assertEquals(Optional.empty(), DecoySource.fromDecoySearch(-1));
        }

        @Test
        @DisplayName("each decoy source says what it means, in the words every decoy message uses")
        void meanings() {
            assertEquals("no internal decoys", DecoySource.FASTA_CONTAINS_DECOYS.meaning());
            assertEquals(
                    "Comet's internal decoys, concatenated",
                    DecoySource.COMET_INTERNAL_CONCATENATED.meaning());
            assertEquals(
                    "Comet's internal decoys, reported separately",
                    DecoySource.COMET_INTERNAL_SEPARATE.meaning());
        }

        @Test
        @DisplayName("the model reads and sets the source through decoy_search")
        void onTheModel() {
            CometParameters model = Models.real();
            assertEquals(Optional.of(DecoySource.FASTA_CONTAINS_DECOYS), model.decoySource());
            for (DecoySource source : DecoySource.values()) {
                CometParameters set = model.withDecoySource(source, ValueOrigin.USER);
                assertEquals(Optional.of(source), set.decoySource());
                assertEquals(Integer.toString(source.decoySearch()), set.text("decoy_search"));
                assertEquals(ValueOrigin.USER, set.origin("decoy_search"));
            }
            assertEquals(Optional.of(DecoySource.FASTA_CONTAINS_DECOYS), model.decoySource());
        }

        @Test
        @DisplayName("an undocumented decoy_search has no source and is the choice rule's error")
        void undocumented() {
            CometParameters model = with("decoy_search", "3");
            assertEquals(Optional.empty(), model.decoySource());
            assertAttached(
                    only(validate(model)),
                    Rule.CHOICE_NOT_LISTED,
                    ParameterCategory.DATABASE_PEFF,
                    "decoy_search");
        }

        @Test
        @DisplayName("an empty decoy_prefix is an error, whatever the source")
        void emptyPrefix() {
            for (DecoySource source : DecoySource.values()) {
                Finding finding =
                        only(
                                validate(
                                        with("decoy_prefix", "")
                                                .withDecoySource(source, ValueOrigin.USER)));
                assertAttached(
                        finding,
                        Rule.DECOY_PREFIX_EMPTY,
                        ParameterCategory.DATABASE_PEFF,
                        "decoy_prefix");
                assertTrue(finding.message().startsWith("decoy_prefix is empty"));
            }
        }

        @Test
        @DisplayName("an imported 'decoy_prefix =' is an empty value, and an error")
        void importedEmptyPrefix() {
            CometParameters model =
                    Models.parse(
                                    Models.METADATA,
                                    ParamsFiles.completeReplacing(
                                            "decoy_prefix =", "decoy_prefix ="))
                            .withWorkflowEnforcedOutputs();
            assertEquals("", model.text("decoy_prefix"));
            assertEquals(
                    List.of(Rule.DECOY_PREFIX_EMPTY),
                    validate(model).findings().stream().map(Finding::rule).toList());
        }

        @Test
        @DisplayName("a decoy_prefix with a space is an error: Comet reads the first word only")
        void prefixWithSpace() {
            Finding finding = only(validate(with("decoy_prefix", "DECOY X")));
            assertAttached(
                    finding,
                    Rule.TEXT_NOT_ONE_TOKEN,
                    ParameterCategory.DATABASE_PEFF,
                    "decoy_prefix");
            assertEquals(
                    "decoy_prefix = DECOY X holds white space; Comet reads only up to the first"
                            + " blank (\"DECOY\"), so use one word with no spaces",
                    finding.message());
            assertEquals(
                    List.of(Rule.TEXT_NOT_ONE_TOKEN),
                    validate(with("decoy_prefix", "rev\t_")).findings().stream()
                            .map(Finding::rule)
                            .toList());
            assertEquals(List.of(), validate(with("decoy_prefix", "rev_")).findings());
        }
    }

    @Nested
    @DisplayName("texts read as one token")
    class Tokens {

        @Test
        @DisplayName("255 bytes are read, 256 are not")
        void length() {
            assertEquals(List.of(), validate(with("decoy_prefix", "D".repeat(255))).findings());
            Finding finding = only(validate(with("decoy_prefix", "D".repeat(256))));
            assertAttached(
                    finding, Rule.TEXT_TOO_LONG, ParameterCategory.DATABASE_PEFF, "decoy_prefix");
            assertEquals(
                    "decoy_prefix is 256 bytes long; Comet reads at most 255, so shorten it",
                    finding.message());
            assertEquals(
                    List.of(Rule.TEXT_TOO_LONG),
                    validate(with("decoy_prefix", "é".repeat(128))).findings().stream()
                            .map(Finding::rule)
                            .toList());
        }

        @Test
        @DisplayName("pinfile_protein_delimiter and protein_modslist_file are held to it too")
        void others() {
            assertAttached(
                    only(validate(with("pinfile_protein_delimiter", "a b"))),
                    Rule.TEXT_NOT_ONE_TOKEN,
                    ParameterCategory.OUTPUT,
                    "pinfile_protein_delimiter");
            assertAttached(
                    only(validate(with("protein_modslist_file", "/data/my mods.txt"))),
                    Rule.TEXT_NOT_ONE_TOKEN,
                    ParameterCategory.VARIABLE_MODS,
                    "protein_modslist_file");
            Finding longPath = only(validate(with("protein_modslist_file", "/" + "m".repeat(300))));
            assertEquals(Rule.TEXT_TOO_LONG, longPath.rule());
            Finding veryLong =
                    only(validate(with("protein_modslist_file", "/" + "m".repeat(5000))));
            assertEquals(Rule.TEXT_TOO_LONG, veryLong.rule());
            assertEquals(List.of(), validate(with("pinfile_protein_delimiter", "")).findings());
        }
    }

    @Nested
    @DisplayName("paths, by form")
    class Paths {

        @Test
        @DisplayName("database_name must not be empty")
        void databaseRequired() {
            Finding finding = only(validate(with("database_name", "")));
            assertAttached(
                    finding, Rule.PATH_EMPTY, ParameterCategory.DATABASE_PEFF, "database_name");
            assertEquals("database_name is empty; it needs a path to a file", finding.message());
        }

        @Test
        @DisplayName("the optional paths may be empty, spectral_library_name included")
        void optionalPaths() {
            for (String name :
                    List.of(
                            "peff_obo",
                            "compoundmods_file",
                            "protein_modslist_file",
                            "spectral_library_name")) {
                assertEquals(
                        SerializationRule.EMPTY_ALLOWED,
                        Models.METADATA.parameter(name).orElseThrow().serialization(),
                        name);
                assertEquals(List.of(), validate(with(name, "")).findings(), name);
            }
        }

        @Test
        @DisplayName(
                "CONSTRUCTED metadata making spectral_library_name required makes empty an error")
        void requiredByMetadata() {
            CuratedMetadata strict =
                    Models.redefine(
                            "spectral_library_name",
                            d ->
                                    new ParameterDefinition(
                                            d.name(),
                                            d.displayName(),
                                            d.category(),
                                            d.kind(),
                                            d.visibility(),
                                            d.defaultValue(),
                                            d.minimum(),
                                            d.maximum(),
                                            d.choices(),
                                            d.shortHelp(),
                                            d.inlineComment(),
                                            d.detailedHelpRef(),
                                            d.supportedVersions(),
                                            SerializationRule.SINGLE_VALUE,
                                            d.validators(),
                                            d.aliases(),
                                            d.related()));
            CometParameters model =
                    Models.parse(strict, ParamsFiles.complete())
                            .withWorkflowEnforcedOutputs()
                            .withText("spectral_library_name", "", ValueOrigin.USER);
            assertEquals(
                    List.of(Rule.PATH_EMPTY),
                    validate(model).findings().stream().map(Finding::rule).toList());
        }

        @Test
        @DisplayName("a NUL character is an error in every path")
        void nul() {
            for (String name :
                    List.of(
                            "database_name",
                            "peff_obo",
                            "compoundmods_file",
                            "protein_modslist_file",
                            "spectral_library_name")) {
                for (String path : List.of("/data/a\u0000b", "\u0000/data/a")) {
                    Finding finding = only(validate(with(name, path)));
                    assertEquals(Rule.PATH_NUL, finding.rule(), name);
                    assertEquals(List.of(name), finding.parameters());
                }
            }
        }

        @Test
        @DisplayName("4095 bytes fit Comet's file-name buffer, 4096 do not; bytes, not characters")
        void length() {
            assertEquals(
                    List.of(), validate(with("database_name", "/" + "d".repeat(4094))).findings());
            Finding finding = only(validate(with("database_name", "/" + "d".repeat(4095))));
            assertAttached(
                    finding, Rule.PATH_TOO_LONG, ParameterCategory.DATABASE_PEFF, "database_name");
            assertEquals(
                    "database_name is 4096 bytes long; Comet holds at most 4095, so use a shorter"
                            + " path",
                    finding.message());
            assertEquals(
                    List.of(Rule.PATH_TOO_LONG),
                    validate(with("peff_obo", "/" + "é".repeat(2048))).findings().stream()
                            .map(Finding::rule)
                            .toList());
        }

        @Test
        @DisplayName("spaces are legal in a path Comet reads whole")
        void spaces() {
            assertEquals(
                    List.of(),
                    validate(with("database_name", "/data/my db/human.fasta")).findings());
        }
    }
}
