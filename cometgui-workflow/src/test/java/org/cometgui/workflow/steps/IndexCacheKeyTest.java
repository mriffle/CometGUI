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

package org.cometgui.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.cometgui.workflow.testing.TestPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The index cache key, without a Comet process. */
class IndexCacheKeyTest {

    static final String FASTA_SHA = "a".repeat(64);

    static final ToolVersion RELEASE = ToolVersion.parse(RealComet.NEWER);

    static CometParameters model() {
        return RealComet.model(
                RealComet.NEWER,
                TestPaths.absolute("data/db.fasta"),
                DecoySource.COMET_INTERNAL_CONCATENATED,
                4);
    }

    static String key(CometParameters model) {
        return key(FASTA_SHA, "db.fasta", IndexMode.FRAGMENT_ION, RELEASE, model);
    }

    static String key(
            String sha, String name, IndexMode mode, ToolVersion release, CometParameters model) {
        return IndexCacheKey.of(
                sha,
                name,
                mode,
                release,
                model,
                new CanonicalParamsWriter(RealComet.BUILD).write(model));
    }

    static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    @Test
    @DisplayName("names the parameters the header records and the index options, and no others")
    void keyed() {
        for (String name :
                List.of(
                        "digest_mass_range",
                        "peptide_length_range",
                        "mass_type_parent",
                        "mass_type_fragment",
                        "decoy_search",
                        "decoy_prefix",
                        "search_enzyme_number",
                        "search_enzyme2_number",
                        "num_enzyme_termini",
                        "allowed_missed_cleavage",
                        "clip_nterm_methionine",
                        "require_variable_mod",
                        "max_variable_mods_in_peptide",
                        "protein_modslist_file",
                        "equal_I_and_L",
                        "add_C_cysteine",
                        "add_Nterm_protein",
                        "variable_mod01",
                        "variable_mod15",
                        "fragindex_min_fragmentmass")) {
            assertTrue(IndexCacheKey.keyed(name), name);
        }
        for (String name :
                List.of(
                        "num_threads",
                        "fragment_bin_tol",
                        "peptide_mass_tolerance_upper",
                        "output_pepxmlfile",
                        "database_name",
                        "index_search_type",
                        "spectral_library_name",
                        "dd_C_cysteine")) {
            assertFalse(IndexCacheKey.keyed(name), name);
        }
    }

    @Test
    @DisplayName("the encoding: four fixed lines, one per keyed parameter, one per enzyme row")
    void encoding() {
        CometParameters model = model();
        String canonical = new CanonicalParamsWriter(RealComet.BUILD).write(model);
        String text =
                IndexCacheKey.encoding(
                        FASTA_SHA, "db.fasta", IndexMode.PEPTIDE, RELEASE, model, canonical);
        assertTrue(
                text.startsWith(
                        "cometgui-index-key 1\nfasta "
                                + FASTA_SHA
                                + " db.fasta\nmode peptide\nrelease 2026.03.0\nparam "),
                text);
        assertTrue(text.contains("\nparam decoy_search = 1\n"), text);
        assertTrue(text.contains("\nparam decoy_prefix = DECOY_\n"), text);
        assertTrue(text.contains("\nparam add_C_cysteine = 57.021464\n"), text);
        assertTrue(
                text.contains("\nenzyme 1.  Trypsin                1      KR          P\n"), text);
        assertFalse(text.contains("num_threads"), text);
        assertFalse(text.contains("[COMET_ENZYME_INFO]"), text);
        assertTrue(text.endsWith("\n"));
        assertFalse(text.contains("\n\n"));
        long params = text.lines().filter(line -> line.startsWith("param ")).count();
        long enzymes = text.lines().filter(line -> line.startsWith("enzyme ")).count();
        assertEquals(66, params, text);
        assertEquals(12, enzymes, text);
        assertEquals(
                sha256(text),
                IndexCacheKey.of(
                        FASTA_SHA, "db.fasta", IndexMode.PEPTIDE, RELEASE, model, canonical));
    }

    @Test
    @DisplayName("changes with the FASTA, its name, the mode, the release and a keyed option")
    void changesWithWhatTheIndexDependsOn() {
        CometParameters model = model();
        String base = key(model);
        assertNotEquals(
                base, key("b".repeat(64), "db.fasta", IndexMode.FRAGMENT_ION, RELEASE, model));
        assertNotEquals(
                base, key(FASTA_SHA, "other.fasta", IndexMode.FRAGMENT_ION, RELEASE, model));
        assertNotEquals(base, key(FASTA_SHA, "db.fasta", IndexMode.PEPTIDE, RELEASE, model));
        assertNotEquals(
                base,
                key(
                        FASTA_SHA,
                        "db.fasta",
                        IndexMode.FRAGMENT_ION,
                        ToolVersion.parse("2026.02.2"),
                        model));
        assertNotEquals(
                base, key(model.withText("digest_mass_range", "700.0 5000.0", ValueOrigin.USER)));
        assertNotEquals(
                base, key(model.withText("allowed_missed_cleavage", "1", ValueOrigin.USER)));
        assertNotEquals(base, key(model.withText("search_enzyme_number", "2", ValueOrigin.USER)));
        assertEquals(base, key(model.withText("num_threads", "1", ValueOrigin.USER)));
        assertEquals(base, key(model.withText("fragment_bin_tol", "1.0005", ValueOrigin.USER)));
        assertEquals(64, base.length());
    }

    @Test
    @DisplayName(
            "refuses index mode none, a name with a separator or a line break, a text without"
                    + " enzymes")
    void refusals() {
        CometParameters model = model();
        String canonical = new CanonicalParamsWriter(RealComet.BUILD).write(model);
        IllegalArgumentException none =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                IndexCacheKey.of(
                                        FASTA_SHA,
                                        "db.fasta",
                                        IndexMode.NONE,
                                        RELEASE,
                                        model,
                                        canonical));
        assertEquals("index mode none builds no index, so it has no key", none.getMessage());
        for (String name : List.of("", "a/db.fasta", "a\\db.fasta", "db\n.fasta", "db\r.fasta")) {
            IllegalArgumentException bad =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    IndexCacheKey.of(
                                            FASTA_SHA,
                                            name,
                                            IndexMode.PEPTIDE,
                                            RELEASE,
                                            model,
                                            canonical));
            assertEquals(
                    "a FASTA is keyed by its file name alone, not \"" + name + "\"",
                    bad.getMessage());
        }
        IllegalArgumentException noTable =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                IndexCacheKey.of(
                                        FASTA_SHA,
                                        "db.fasta",
                                        IndexMode.PEPTIDE,
                                        RELEASE,
                                        model,
                                        "num_threads = 4\n"));
        assertEquals(
                "the canonical parameter text has no [COMET_ENZYME_INFO] table to key",
                noTable.getMessage());
        assertTrue(
                IndexCacheKey.encoding(
                                FASTA_SHA,
                                "db.fasta",
                                IndexMode.PEPTIDE,
                                RELEASE,
                                model,
                                "[COMET_ENZYME_INFO]\n0.  No_enzyme 0 - -\n")
                        .endsWith("\nenzyme 0.  No_enzyme 0 - -\n"),
                "a table at the very start of the text is still the table");
    }
}
