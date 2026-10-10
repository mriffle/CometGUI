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

package org.cometgui.tools.pdv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The one way the mzTab export fails: a PSM, or a source file, that cannot be represented
 * faithfully (design decision P11-8, {@code R-PDV-03} "no silent loss"). It names the PSM where
 * there is one, the source files involved and the reason; when it is thrown nothing is left at the
 * export's final path ({@link PdvMzTabExporter}).
 */
public final class PdvMzTabException extends IOException {

    private static final long serialVersionUID = 1L;

    /** Why the export was refused. */
    public enum Reason {
        /** The Percolator tables hold no PSM, and PDV cannot open an empty result set. */
        NO_PSMS,
        /** A {@code PSMId} is not Comet's {@code <base>_<scan>_<charge>_<rank>}. */
        NO_SPECTRUM_REFERENCE,
        /** A {@code PSMId}'s base names none of the run's Comet outputs. */
        UNKNOWN_BASE,
        /** Two Percolator rows name the same pepXML hit. */
        DUPLICATE_PSM,
        /** The pepXML holds no hit at the {@code PSMId}'s scan, charge and position. */
        NO_PEPXML_HIT,
        /** Two pepXML spectrum queries share the {@code PSMId}'s scan and charge. */
        AMBIGUOUS_QUERY,
        /** The Percolator peptide is not the pepXML hit's: residues, flanks or modifications. */
        PEPTIDE_MISMATCH,
        /**
         * A modification has no faithful form for PDV: C-terminal, or a terminal mass no one
         * declaration states.
         */
        UNREPRESENTABLE_MODIFICATION,
        /** The hit carries a PEFF sequence variant, which the mzTab cannot state. */
        SEQUENCE_VARIANT,
        /** The pepXML query carries no {@code spectrumNativeID}. */
        NO_NATIVE_ID,
        /** No MS2 spectrum of the spectrum file has the hit's native id. */
        NO_SPECTRUM,
        /** More than one spectrum of the spectrum file has the hit's native id. */
        AMBIGUOUS_SPECTRUM,
        /** The spectrum's native id carries a scan number other than the PSM's. */
        SPECTRUM_MISMATCH,
        /** The spectrum cannot be written to an MGF exactly. */
        UNREPRESENTABLE_SPECTRUM,
        /** A score, q-value or PEP is not a decimal number as Percolator wrote it. */
        NOT_A_DECIMAL,
        /**
         * A value would be empty, would hold a tab or line break, or would read as mzTab's null.
         */
        UNREPRESENTABLE_TEXT,
        /** A source file is missing, unreadable, or refused by its reader. */
        UNREADABLE_SOURCE,
        /** A source changed while it was being exported. */
        SOURCE_CHANGED,
        /** The written document does not hold exactly the PSMs read: the export's own check. */
        INCOMPLETE
    }

    private final Reason reason;

    private final String psmId;

    private final String[] files;

    /**
     * A refusal.
     *
     * @param reason why
     * @param psmId the PSM it concerns, or {@code null} when it concerns a whole file
     * @param files the source files involved
     * @param detail what was found, completing the message
     * @param cause the reader's refusal behind it, or {@code null}
     */
    PdvMzTabException(
            Reason reason, String psmId, List<Path> files, String detail, Throwable cause) {
        super(message(reason, psmId, files, detail), cause);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.psmId = psmId;
        this.files = new String[files.size()];
        for (int index = 0; index < files.size(); index++) {
            this.files[index] = files.get(index).toString();
        }
    }

    private static String message(Reason reason, String psmId, List<Path> files, String detail) {
        StringBuilder text = new StringBuilder("The mzTab for PDV was not written: ");
        if (psmId != null) {
            text.append("PSM ").append(psmId).append(' ');
        }
        text.append(detail);
        if (!files.isEmpty()) {
            text.append(" (source");
            text.append(files.size() == 1 ? " " : "s ");
            for (int index = 0; index < files.size(); index++) {
                text.append(index == 0 ? "" : ", ").append(files.get(index));
            }
            text.append(')');
        }
        return text.append(" [").append(reason).append(']').toString();
    }

    /**
     * Why the export was refused.
     *
     * @return the reason
     */
    public Reason reason() {
        return reason;
    }

    /**
     * The PSM the refusal concerns.
     *
     * @return its {@code PSMId}, or empty when a whole file was refused
     */
    public Optional<String> psmId() {
        return Optional.ofNullable(psmId);
    }

    /**
     * The source files involved.
     *
     * @return the files, in the order the message names them
     */
    public List<Path> files() {
        List<Path> paths = new ArrayList<>(files.length);
        for (String file : files) {
            paths.add(Path.of(file));
        }
        return List.copyOf(paths);
    }
}
