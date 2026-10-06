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

/**
 * One kind of input an {@link EngineStep} can read: a file set, a tool identity, a group of
 * settings.
 *
 * <p>Each step declares the kinds it reads ({@link EngineStep#inputs()}); a step's fingerprint
 * contains the digest of exactly those values and no others. That is the whole mechanism by which
 * the specification's <em>Stage reruns</em> paragraph is met: a change to a value re-executes the
 * steps that declare its kind, and the steps downstream of them, and nothing else.
 *
 * <h2>Two kinds that no step reads</h2>
 *
 * <p>{@link #PSM_DISPLAY_FILTER} and {@link #PEPTIDE_DISPLAY_FILTER} are declared and read by no
 * step. That is deliberate and is the point of declaring them: a caller can hand the engine the
 * project's whole configuration without first deciding which parts are scientific, and the
 * specification's "changing only the PSM or peptide display filters requires no scientific rerun"
 * holds because no step's fingerprint can see them, not because some caller remembered to leave
 * them out. A test pins that no step declares either.
 *
 * <h2>Identifiers</h2>
 *
 * <p>{@link #id()} is stable and lower-case-hyphenated. It is part of the fingerprint encoding (so
 * renaming one changes every fingerprint that contains it) and it is what a rerun preview shows as
 * the input that changed. It is deliberately not {@link #name()}.
 */
public enum InputKind {

    /**
     * The spectrum files, in the run's input order. Order matters: it fixes each file's position in
     * the merged PIN and its {@code comet-<nn>} log.
     */
    SPECTRUM_FILES("spectrum-files", ValueType.FILES),

    /** The protein database, as a list of one file. */
    FASTA("fasta", ValueType.FILES),

    /**
     * The canonical Comet parameter file's bytes, exactly as the one canonical writer produces them
     * -- the bytes that will be archived as {@code parameters/comet.params} and passed with {@code
     * -P}.
     */
    COMET_PARAMETERS("comet-parameters", ValueType.BYTES),

    /**
     * The selected Comet index mode, as a canonical token chosen by the caller (for example {@code
     * none}, {@code fragment-ion}, {@code peptide}). It is not part of {@code comet.params}: it
     * selects the {@code -i}/{@code -j} index step and the {@code -D} of the search.
     */
    COMET_INDEX_MODE("comet-index-mode", ValueType.TEXT),

    /** The Comet binary in use: its version and SHA-256. */
    COMET_TOOL("comet-tool", ValueType.TOOL),

    /** Percolator's settings, as the canonical text its owning phase serialises them to. */
    PERCOLATOR_SETTINGS("percolator-settings", ValueType.TEXT),

    /** The Percolator binary in use: its version and SHA-256. */
    PERCOLATOR_TOOL("percolator-tool", ValueType.TOOL),

    /** The PDV JAR in use: its version and SHA-256. */
    PDV_TOOL("pdv-tool", ValueType.TOOL),

    /** The Limelight converter's q-value cutoff, separate from both display filters. */
    LIMELIGHT_Q_CUTOFF("limelight-q-cutoff", ValueType.DECIMAL),

    /**
     * The converter's other options (import decoys, an independent decoy prefix, open-mod mode), as
     * canonical text.
     */
    LIMELIGHT_CONVERTER_OPTIONS("limelight-converter-options", ValueType.TEXT),

    /** The Limelight converter JAR in use: its version and SHA-256. */
    LIMELIGHT_CONVERTER_TOOL("limelight-converter-tool", ValueType.TOOL),

    /**
     * Where an upload goes -- the server and the project on it, as canonical text. Never a
     * credential: a key is a secret (R-SEC) and an upload to the same place with a new key is the
     * same upload.
     */
    LIMELIGHT_UPLOAD_TARGET("limelight-upload-target", ValueType.TEXT),

    /** The PSM result display filter. Read by no step; see the type's documentation. */
    PSM_DISPLAY_FILTER("psm-display-filter", ValueType.DECIMAL),

    /** The peptide result display filter. Read by no step; see the type's documentation. */
    PEPTIDE_DISPLAY_FILTER("peptide-display-filter", ValueType.DECIMAL);

    private final String id;

    private final ValueType valueType;

    InputKind(String id, ValueType valueType) {
        this.id = id;
        this.valueType = valueType;
    }

    /**
     * The stable, lower-case-hyphenated identifier written into fingerprints and previews.
     *
     * @return the identifier, never {@code null}
     */
    public String id() {
        return id;
    }

    /**
     * The one type a value of this kind must have.
     *
     * @return the value type, never {@code null}
     */
    public ValueType valueType() {
        return valueType;
    }
}
