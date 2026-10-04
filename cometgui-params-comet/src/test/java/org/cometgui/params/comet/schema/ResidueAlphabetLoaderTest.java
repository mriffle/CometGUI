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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The residue alphabet as data: {@code versions[].variableModTuple.residueAlphabet}, read by {@link
 * MetadataLoader} for each release, and the bundled releases' own alphabets. Damages are made to
 * CONSTRUCTED metadata ({@link ConstructedMetadata}), one change each.
 */
class ResidueAlphabetLoaderTest {

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final String WHERE = "versions[0]";

    private static final String FIELD = "variableModTuple.residueAlphabet";

    @SuppressWarnings("unchecked")
    private static Map<String, Object> layout(ConstructedMetadata doc) {
        return (Map<String, Object>)
                ((Map<String, Object>) doc.list("versions").get(0)).get("variableModTuple");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> alphabet(ConstructedMetadata doc) {
        return (Map<String, Object>) layout(doc).get("residueAlphabet");
    }

    private static void rejected(
            ConstructedMetadata doc, String where, String field, String message) {
        InvalidMetadataException failure = assertThrows(InvalidMetadataException.class, doc::load);
        assertEquals(where, failure.where(), failure.getMessage());
        assertEquals(field, failure.field(), failure.getMessage());
        assertEquals(
                "invalid Comet parameter metadata: "
                        + where
                        + ", field \""
                        + field
                        + "\": "
                        + message,
                failure.getMessage());
    }

    private static void addTuple(ConstructedMetadata doc, String value) {
        doc.list("parameters")
                .add(
                        new ConstructedMetadata.Builder(
                                        "variable_mod01",
                                        "VARIABLE_MODS",
                                        "VARIABLE_MOD_TUPLE",
                                        value)
                                .serialization("TUPLE")
                                .validators("variable_mod_tuple")
                                .map());
    }

    @Nested
    @DisplayName("the bundled releases' alphabets")
    class Bundled {

        private final CuratedMetadata bundled = MetadataLoader.loadBundled();

        private ResidueAlphabet of(String version) {
            return bundled.version(ToolVersion.parse(version))
                    .orElseThrow()
                    .variableModTuple()
                    .residueAlphabet();
        }

        @Test
        @DisplayName("2026.03.0 accepts ^ and $ beside A-Z, n and c, cited at its tag")
        void comet202603() {
            ResidueAlphabet alphabet = of("2026.03.0");
            assertEquals(LETTERS + "nc^$", alphabet.characters());
            assertEquals(
                    "https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/"
                            + "CometSearchManager.cpp#L1538-L1563",
                    alphabet.source());
            assertEquals(
                    "A-Z, n (N-terminus), c (C-terminus), ^ (protein N-terminus), $ (protein"
                            + " C-terminus)",
                    alphabet.describe());
        }

        @Test
        @DisplayName("2026.02.2 and 2024.01.0 accept A-Z, n and c only, each cited at its tag")
        void olderReleases() {
            for (String version : new String[] {"2026.02.2", "2024.01.0"}) {
                ResidueAlphabet alphabet = of(version);
                assertEquals(LETTERS + "nc", alphabet.characters(), version);
                assertEquals("A-Z, n (N-terminus), c (C-terminus)", alphabet.describe(), version);
                assertTrue(
                        alphabet.source()
                                .startsWith(
                                        "https://github.com/UWPR/Comet/blob/v"
                                                + version
                                                + "/CometSearch/CometSearchManager.cpp#L"),
                        alphabet.source());
            }
            assertEquals(
                    "https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/"
                            + "CometSearchManager.cpp#L1380-L1396",
                    of("2026.02.2").source());
        }
    }

    @Nested
    @DisplayName("what the loader refuses, naming the release and the field")
    class Refusals {

        @Test
        void aConstructedAlphabetLoadsAsWritten() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            alphabet(doc).put("characters", "KST^");
            ResidueAlphabet loaded =
                    doc.load().versions().get(0).variableModTuple().residueAlphabet();
            assertEquals("KST^", loaded.characters());
            assertEquals("KST, ^ (protein N-terminus)", loaded.describe());
            assertEquals("https://example.org/source/CometSearchManager.cpp", loaded.source());
        }

        @Test
        void aLayoutWithoutAnAlphabet() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            layout(doc).remove("residueAlphabet");
            rejected(
                    doc,
                    WHERE,
                    "residueAlphabet",
                    "is missing; every field is required, null where absent");
        }

        @Test
        void anAlphabetThatIsNotAnObject() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            layout(doc).put("residueAlphabet", LETTERS + "nc");
            rejected(doc, WHERE, FIELD, "must be a JSON object");
        }

        @Test
        void anUnknownMember() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            alphabet(doc).put("since", "2026.03.0");
            rejected(
                    doc,
                    WHERE,
                    "since",
                    "is not a field this format has; expected only [characters, source]");
        }

        @Test
        void noCharacters() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            alphabet(doc).remove("characters");
            rejected(
                    doc,
                    WHERE,
                    "characters",
                    "is missing; every field is required, null where absent");
        }

        @Test
        void charactersThatAreNotAString() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            alphabet(doc).put("characters", 26L);
            rejected(doc, WHERE, "characters", "must be a string");
        }

        @Test
        void anEmptyAlphabet() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            alphabet(doc).put("characters", "");
            rejected(doc, WHERE, FIELD, "the residue alphabet is empty");
        }

        @Test
        void aCharacterNoReleaseCanMean() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            alphabet(doc).put("characters", LETTERS + "nc#");
            rejected(
                    doc,
                    WHERE,
                    FIELD,
                    "the residue alphabet holds '#', which is neither a residue letter A-Z nor a"
                            + " terminal code (n, c, ^, $)");
        }

        @Test
        void aLowerCaseLetter() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            alphabet(doc).put("characters", LETTERS + "ncm");
            rejected(
                    doc,
                    WHERE,
                    FIELD,
                    "the residue alphabet holds 'm', which is neither a residue letter A-Z nor a"
                            + " terminal code (n, c, ^, $)");
        }

        @Test
        void aCharacterListedTwice() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            alphabet(doc).put("characters", LETTERS + "nc^^");
            rejected(doc, WHERE, FIELD, "the residue alphabet lists '^' twice");
        }

        @Test
        void aSourceThatIsNotHttps() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            alphabet(doc).put("source", "CometSearchManager.cpp line 1540");
            rejected(
                    doc,
                    WHERE,
                    "source",
                    "\"CometSearchManager.cpp line 1540\" is not an https:// reference");
        }

        @Test
        void aTupleDefaultWhoseResiduesTheReleaseDoesNotAccept() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            addTuple(doc, "42.010565 ^ 0 1 -1 0 0 0.0");
            rejected(
                    doc,
                    "parameter \"variable_mod01\"",
                    "default",
                    "\"42.010565 ^ 0 1 -1 0 0 0.0\" has the residues \"^\", and '^' is not in"
                            + " the residue alphabet of Comet 2026.02.2: A-Z, n (N-terminus), c"
                            + " (C-terminus)");
        }

        @Test
        void theSameDefaultLoadsWhereTheAlphabetHasTheCode() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            alphabet(doc).put("characters", LETTERS + "nc^$");
            addTuple(doc, "42.010565 ^ 0 1 -1 0 0 0.0");
            assertEquals(
                    "42.010565 ^ 0 1 -1 0 0 0.0",
                    doc.load().parameter("variable_mod01").orElseThrow().defaultValue());
        }

        @Test
        void theDefaultIsReadAtTheLayoutsResiduePosition() {
            ConstructedMetadata doc = ConstructedMetadata.valid();
            alphabet(doc).put("characters", "KM");
            addTuple(doc, "15.9949 M 0 3 -1 0 0 0.0");
            assertEquals(
                    "15.9949 M 0 3 -1 0 0 0.0",
                    doc.load().parameter("variable_mod01").orElseThrow().defaultValue());
            ConstructedMetadata refusing = ConstructedMetadata.valid();
            alphabet(refusing).put("characters", "KM");
            addTuple(refusing, "15.9949 X 0 3 -1 0 0 0.0");
            rejected(
                    refusing,
                    "parameter \"variable_mod01\"",
                    "default",
                    "\"15.9949 X 0 3 -1 0 0 0.0\" has the residues \"X\", and 'X' is not in the"
                            + " residue alphabet of Comet 2026.02.2: KM");
        }
    }
}
