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

import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.parser.ParamsLineReader;
import org.cometgui.workflow.state.InputValue;

/**
 * The key of one entry of a project's Comet index cache ({@code R-CMT-07}, design decision P8-8): a
 * SHA-256 over the FASTA, the index mode, the Comet release and the search options an index is
 * built with.
 *
 * <p>Pure: it reads no file. The FASTA contributes the SHA-256 the one hasher computed and its file
 * name -- the name, because Comet names the index after the database it was given ({@code
 * <name>.idx}) and records it as {@code InputDB:}.
 *
 * <h2>The encoding</h2>
 *
 * <p>ASCII lines, each ending in {@code \n}, of which the key is the SHA-256 in lower-case
 * hexadecimal:
 *
 * <pre>
 *     cometgui-index-key 1
 *     fasta &lt;sha256&gt; &lt;file name&gt;
 *     mode &lt;fragment-ion | peptide&gt;
 *     release &lt;release&gt;
 *     param &lt;name&gt; = &lt;value text&gt;      one per keyed parameter, in the model's order
 *     enzyme &lt;row&gt;                     one per row of the canonical [COMET_ENZYME_INFO] table
 * </pre>
 *
 * <h2>Which parameters are keyed</h2>
 *
 * <p>Every option the {@code .idx} header records -- {@code digest_mass_range}, {@code
 * peptide_length_range}, the two mass types, {@code decoy_search} and {@code decoy_prefix}, both
 * search enzymes (with the whole enzyme table, since the header records the rows they select),
 * {@code num_enzyme_termini}, {@code allowed_missed_cleavage}, {@code clip_nterm_methionine}, every
 * static modification ({@code add_*}), every variable modification ({@code variable_mod*}), {@code
 * require_variable_mod}, {@code max_variable_mods_in_peptide} and {@code protein_modslist_file} --
 * and, conservatively, two kinds the header does not record but an index build reads: the
 * fragment-ion index's own options ({@code fragindex_*}) and {@code equal_I_and_L}. A parameter
 * keyed needlessly costs a rebuild; one missing would cost a wrong reuse, so the list errs towards
 * keying. A reused entry is in any case read and judged by the validator's index rules before it is
 * used, whatever its key.
 */
public final class IndexCacheKey {

    /** The first line of the encoding: its name and version. */
    public static final String ENCODING = "cometgui-index-key 1";

    /** The keyed parameters named one by one; see the class documentation. */
    static final Set<String> NAMED =
            Set.of(
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
                    "equal_I_and_L");

    /** The keyed parameters named by prefix; see the class documentation. */
    static final List<String> PREFIXES = List.of("add_", "variable_mod", "fragindex_");

    private IndexCacheKey() {}

    /**
     * Whether a parameter is part of the key.
     *
     * @param name a parameter name
     * @return {@code true} if it is one of {@link #NAMED} or begins with one of {@link #PREFIXES}
     */
    static boolean keyed(String name) {
        if (NAMED.contains(name)) {
            return true;
        }
        for (String prefix : PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The text the key is the SHA-256 of.
     *
     * @param fastaSha256 the FASTA's SHA-256, from the one hasher
     * @param fastaName the FASTA's file name, without a directory
     * @param mode the index mode; not {@link IndexMode#NONE}
     * @param release the Comet release that builds and searches the index
     * @param model the search's parameters
     * @param canonicalParams the canonical text of {@code model}, as {@code CanonicalParamsWriter}
     *     writes it; its enzyme table is keyed
     * @return the encoding
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if the mode is {@link IndexMode#NONE}, the name holds a
     *     directory separator or a line break, or the canonical text has no enzyme table
     */
    public static String encoding(
            String fastaSha256,
            String fastaName,
            IndexMode mode,
            ToolVersion release,
            CometParameters model,
            String canonicalParams) {
        Objects.requireNonNull(fastaSha256, "fastaSha256");
        Objects.requireNonNull(fastaName, "fastaName");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(canonicalParams, "canonicalParams");
        if (mode == IndexMode.NONE) {
            throw new IllegalArgumentException("index mode none builds no index, so it has no key");
        }
        if (fastaName.isEmpty()
                || fastaName.contains("/")
                || fastaName.contains("\\")
                || fastaName.contains("\n")
                || fastaName.contains("\r")) {
            throw new IllegalArgumentException(
                    "a FASTA is keyed by its file name alone, not \"" + fastaName + "\"");
        }
        StringBuilder text = new StringBuilder(ENCODING).append('\n');
        text.append("fasta ").append(fastaSha256).append(' ').append(fastaName).append('\n');
        text.append("mode ").append(mode.wireName()).append('\n');
        text.append("release ").append(release.text()).append('\n');
        for (ParameterEntry entry : model.entries()) {
            if (keyed(entry.name())) {
                text.append("param ")
                        .append(entry.name())
                        .append(" = ")
                        .append(model.text(entry.name()))
                        .append('\n');
            }
        }
        int table = canonicalParams.indexOf(ParamsLineReader.ENZYME_HEADER + "\n");
        if (table < 0) {
            throw new IllegalArgumentException(
                    "the canonical parameter text has no "
                            + ParamsLineReader.ENZYME_HEADER
                            + " table to key");
        }
        String rows =
                canonicalParams.substring(table + ParamsLineReader.ENZYME_HEADER.length() + 1);
        for (String row : rows.split("\n", -1)) {
            if (!row.isEmpty()) {
                text.append("enzyme ").append(row).append('\n');
            }
        }
        return text.toString();
    }

    /**
     * The key: the SHA-256 of {@link #encoding}.
     *
     * @param fastaSha256 the FASTA's SHA-256
     * @param fastaName the FASTA's file name
     * @param mode the index mode; not {@link IndexMode#NONE}
     * @param release the Comet release
     * @param model the search's parameters
     * @param canonicalParams the canonical text of {@code model}
     * @return 64 lower-case hexadecimal characters
     * @throws IllegalArgumentException as {@link #encoding}
     */
    public static String of(
            String fastaSha256,
            String fastaName,
            IndexMode mode,
            ToolVersion release,
            CometParameters model,
            String canonicalParams) {
        return InputValue.text(
                        encoding(fastaSha256, fastaName, mode, release, model, canonicalParams))
                .digest();
    }
}
