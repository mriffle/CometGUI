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

package org.cometgui.workflow.pdv;

import java.io.IOException;
import java.util.Objects;

/**
 * A PSM's spectrum cannot be drawn, for a reason found before PDV is launched.
 *
 * <p>Nothing is launched and nothing is recorded when this is thrown. The two other refusals a draw
 * can end in keep their own types and their own words: an unsupported spectrum file is {@code
 * org.cometgui.tools.pdv.UnsupportedSpectrumFileException} (also before any launch), and PDV
 * failing is {@code org.cometgui.tools.pdv.PdvFigureException} (after the launch, and recorded).
 */
public final class SpectrumNotDrawnException extends IOException {

    private static final long serialVersionUID = 1L;

    /** Why the spectrum cannot be drawn. */
    public enum Reason {
        /** The PSM's identifier carries no spectrum reference: no base, scan and charge. */
        NO_SPECTRUM_REFERENCE,
        /** No spectrum file of the run produced the PSM's {@code -N} base. */
        NO_SPECTRUM_FILE,
        /** The Comet pepXML PDV reads the PSM from is missing. */
        NO_PEPXML,
        /** No documented mapping from the PSM's scan to exactly one spectrum of the file. */
        NO_DOCUMENTED_MAPPING
    }

    private final Reason reason;

    /**
     * Creates the refusal.
     *
     * @param reason why
     * @param message the whole sentence
     */
    SpectrumNotDrawnException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Why the spectrum cannot be drawn.
     *
     * @return the reason
     */
    public Reason reason() {
        return reason;
    }
}
