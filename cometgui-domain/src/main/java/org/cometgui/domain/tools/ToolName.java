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

package org.cometgui.domain.tools;

import java.util.Objects;

/**
 * The four scientific tools CometGUI installs, probes and runs.
 *
 * <p>This is the closed set the product manages. A user may still register a local binary that the
 * application did not install ({@code R-TOOL-08}), but it is a local binary <em>of one of these
 * tools</em>: there is no fifth name, and a manifest entry naming one is rejected rather than
 * quietly carried.
 *
 * <p><strong>Why the identifier is a field and not {@code name().toLowerCase()}.</strong> The same
 * argument {@code org.cometgui.provenance.events.ProvenanceEventType} records: {@code
 * "PDV".toLowerCase()} spells itself differently under a Turkish default locale, and deriving the
 * on-disk token from the Java constant would let an ordinary rename silently change every manifest
 * key, every cache directory name and every provenance record ever written. {@code
 * LIMELIGHT_CONVERTER} would also need a rule for turning an underscore into a hyphen, and a rule
 * is one more thing that can differ between the writer and the reader. Here the two names are
 * independent and every identifier is pinned by a hand-typed test.
 */
public enum ToolName {

    /** Comet, the database search engine that turns spectra into peptide-spectrum matches. */
    COMET("comet"),

    /** Percolator, which rescores those matches and estimates their error rates. */
    PERCOLATOR("percolator"),

    /** PDV, the spectrum viewer, distributed as a JAR. */
    PDV("pdv"),

    /** The Limelight converter, which turns a Comet plus Percolator run into Limelight XML. */
    LIMELIGHT_CONVERTER("limelight-converter");

    private final String id;

    ToolName(String id) {
        this.id = id;
    }

    /**
     * The stable identifier used in the artefact manifest, in the tool cache path and in the
     * provenance record.
     *
     * <p>Lower case, hyphenated, and identical on every machine whatever the JVM default locale is;
     * see the type documentation for why it is stored rather than derived.
     *
     * @return the identifier, never {@code null} or blank
     */
    public String id() {
        return id;
    }

    /**
     * Resolves an identifier read from a manifest, a cache path or a provenance record back to its
     * constant.
     *
     * <p>Exact match, deliberately: no trimming, no case folding, no "close enough". A manifest
     * whose tool is {@code "Percolator"} was not written by this application, and guessing what it
     * meant is how a manifest and a reader drift apart.
     *
     * @param id the identifier to resolve
     * @return the matching tool
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if no tool has that identifier, with a message naming the
     *     rejected value and listing what is accepted
     */
    public static ToolName fromId(String id) {
        Objects.requireNonNull(id, "id");
        for (ToolName tool : values()) {
            if (tool.id.equals(id)) {
                return tool;
            }
        }
        throw new IllegalArgumentException(
                "no tool has the id \""
                        + id
                        + "\"; expected one of [comet, percolator, pdv, limelight-converter]");
    }
}
