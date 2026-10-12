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

package org.cometgui.ui.viewmodel.limelight;

import java.util.Objects;

/**
 * A run's Limelight converter settings as the section shows and edits them: text exactly as typed,
 * and two switches. Nothing here checks a value -- the port hands the text to the workflow, whose
 * rules accept or refuse it ({@code LimelightSettings.parseCutoff}, the conversion request's prefix
 * rule, and the run's own decoy rule, design decision P12-7).
 *
 * <p>The Limelight q-value cutoff is the converter's own value: it is never one of the display
 * filters, and nothing in this record names one (design decision P12-6).
 *
 * @param qCutoff the Limelight q-value cutoff as text, for example {@code 0.01}
 * @param importDecoys whether to import decoys
 * @param decoyPrefix the independent-decoy prefix as text; blank for none
 * @param openMod whether to convert in open-modification mode
 */
public record LimelightOptions(
        String qCutoff, boolean importDecoys, String decoyPrefix, boolean openMod) {

    /**
     * Requires the texts.
     *
     * @throws NullPointerException if a text is {@code null}
     */
    public LimelightOptions {
        Objects.requireNonNull(qCutoff, "qCutoff");
        Objects.requireNonNull(decoyPrefix, "decoyPrefix");
    }

    /**
     * The same options with another cutoff text.
     *
     * @param text the cutoff as typed
     * @return the options
     */
    public LimelightOptions withQCutoff(String text) {
        return new LimelightOptions(text, importDecoys, decoyPrefix, openMod);
    }

    /**
     * The same options with decoy import on or off.
     *
     * @param on whether to import decoys
     * @return the options
     */
    public LimelightOptions withImportDecoys(boolean on) {
        return new LimelightOptions(qCutoff, on, decoyPrefix, openMod);
    }

    /**
     * The same options with another independent-decoy prefix text.
     *
     * @param text the prefix as typed; blank for none
     * @return the options
     */
    public LimelightOptions withDecoyPrefix(String text) {
        return new LimelightOptions(qCutoff, importDecoys, text, openMod);
    }

    /**
     * The same options with open-modification mode on or off.
     *
     * @param on whether to convert in open-modification mode
     * @return the options
     */
    public LimelightOptions withOpenMod(boolean on) {
        return new LimelightOptions(qCutoff, importDecoys, decoyPrefix, on);
    }
}
