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

package org.cometgui.tools.limelight;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.cometgui.tools.percolator.PoutDocument;

/**
 * What the Limelight converter will read from a run's Percolator XML: whether it is Percolator XML
 * at all, every PSM id, and how many PSMs are decoys -- a strict view over {@link PoutDocument}.
 *
 * <p><strong>One reader of pout XML.</strong> The document is walked once, by {@link
 * PoutDocument#read}, the product's only reader of the format (design decision P12-1): streaming,
 * DTD support and external entities forced off. This class reads nothing itself. It adds the
 * refusals the converter adapter needs and the capability probe must not start making (the probe
 * accepted these documents before Phase 12, and still does):
 *
 * <ul>
 *   <li>a {@code psm} without a {@code p:psm_id}, because no pepXML name can be derived from it;
 *   <li>a {@code p:decoy} value other than {@code true} or {@code false}, because a file that says
 *       something else about decoys is not one whose decoy count can be trusted;
 *   <li>a DOCTYPE, which Percolator never writes. It is never resolved either way.
 * </ul>
 *
 * <h2>Decoys, as the real files carry them</h2>
 *
 * <p>Percolator 3.07.1 run with {@code -X} alone writes target PSMs only and <strong>no {@code
 * p:decoy} attribute at all</strong> ({@code percolator-3.07.1/percolator.pout.xml}: 3897 PSMs,
 * none carrying it). With {@code -X -Z} every PSM carries {@code p:decoy="true"} or {@code "false"}
 * ({@code decoy-chain/withZ.pout.xml}: 3897 false, 2773 true). A missing attribute is therefore a
 * target. {@link #decoyPsmCount()} is what design decision P12-7 reads at configuration time to
 * decide whether Limelight decoy import can be offered.
 *
 * <p>A well-formed document whose element is not {@code percolator_output} in Percolator's
 * namespace is reported by {@link #isPercolatorXml()} as {@code false}, with no PSMs.
 */
public final class PercolatorXmlPsms {

    /** The only {@code p:decoy} values Percolator writes. */
    static final Set<String> DECOY_VALUES = Set.of("true", "false");

    private final Path file;
    private final PoutDocument document;

    private PercolatorXmlPsms(Path file, PoutDocument document) {
        this.file = file;
        this.document = document;
    }

    /**
     * Reads a Percolator XML file through {@link PoutDocument}.
     *
     * @param file the file
     * @return what it holds
     * @throws IOException if the file is absent, is empty or is not well-formed XML ({@link
     *     PoutDocument}'s own refusals), or carries a DOCTYPE, a {@code psm} without an id, or a
     *     {@code p:decoy} value other than {@code true} or {@code false} -- each naming the file
     * @throws NullPointerException if {@code file} is {@code null}
     */
    public static PercolatorXmlPsms read(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        PoutDocument document = PoutDocument.read(file);
        if (document.hasDoctype()) {
            throw new IOException(
                    "the Percolator XML file "
                            + file
                            + " carries a DOCTYPE, which Percolator never writes, so it is not"
                            + " read");
        }
        if (document.psmsWithoutId() > 0) {
            throw new IOException(
                    "the Percolator XML file "
                            + file
                            + " has "
                            + document.psmsWithoutId()
                            + " psm element(s) without a p:psm_id");
        }
        Set<String> unknown = new TreeSet<>(document.psmDecoyValues());
        unknown.removeAll(DECOY_VALUES);
        if (!unknown.isEmpty()) {
            throw new IOException(
                    "the Percolator XML file "
                            + file
                            + " says p:decoy="
                            + unknown
                            + "; Percolator writes only \"true\" or \"false\"");
        }
        return new PercolatorXmlPsms(file, document);
    }

    /**
     * The file this was read from.
     *
     * @return the file
     */
    public Path file() {
        return file;
    }

    /**
     * Whether the document element is {@code percolator_output} in Percolator's namespace.
     *
     * @return {@code true} for Percolator XML
     */
    public boolean isPercolatorXml() {
        return document.isPercolatorOutput();
    }

    /**
     * Every {@code psm} element's {@code p:psm_id}, in document order.
     *
     * @return the ids, immutable; empty when the file is not Percolator XML
     */
    public List<String> psmIds() {
        return isPercolatorXml() ? document.psmIds() : List.of();
    }

    /**
     * How many {@code psm} elements say {@code p:decoy="true"}.
     *
     * @return the count; zero for any run made without Percolator's {@code -Z}, and for a file that
     *     is not Percolator XML
     */
    public int decoyPsmCount() {
        return isPercolatorXml() ? document.decoyPsmCount() : 0;
    }
}
