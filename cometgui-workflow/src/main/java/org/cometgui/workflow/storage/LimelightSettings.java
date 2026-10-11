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

package org.cometgui.workflow.storage;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.cometgui.tools.limelight.LimelightQValueCutoff;

/**
 * A run's Limelight conversion settings: what the next conversion of that run is given (phase 12,
 * design decisions P12-6 and P12-7).
 *
 * <p>The Limelight q-value cutoff is its own value. It is never read from, or written to, the run's
 * display filters ({@code results/view-state.json}, {@link ViewStateStore}): the two are kept in
 * two files, by two stores, and nothing here names a display filter. Its default is 0.01 ({@link
 * LimelightQValueCutoff#DEFAULT}).
 *
 * <p>Whether decoy import can be asked for is not decided here, because it is a property of the
 * run's Percolator output, not of a setting: {@code org.cometgui.workflow.limelight} refuses to
 * save, and refuses to convert with, a decoy import its run cannot honour.
 *
 * @param qCutoff the Limelight q-value cutoff ({@code -q})
 * @param importDecoys whether to import decoys ({@code --import-decoys})
 * @param independentDecoyPrefix the independent-decoy protein prefix, if any ({@code
 *     --independent-decoy-prefix}); checked by the conversion request, which owns the rule
 * @param openMod whether to convert in open-modification mode ({@code --open-mod})
 */
public record LimelightSettings(
        LimelightQValueCutoff qCutoff,
        boolean importDecoys,
        Optional<String> independentDecoyPrefix,
        boolean openMod) {

    /** What a run's conversion uses when nothing has been saved: q 0.01, every option off. */
    public static final LimelightSettings DEFAULTS =
            new LimelightSettings(LimelightQValueCutoff.DEFAULT, false, Optional.empty(), false);

    /** A cutoff as the scientist types it and the file stores it: digits, an optional '.' part. */
    private static final Pattern DECIMAL = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");

    /**
     * Validates the settings.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public LimelightSettings {
        Objects.requireNonNull(qCutoff, "qCutoff");
        Objects.requireNonNull(independentDecoyPrefix, "independentDecoyPrefix");
    }

    /**
     * Reads a Limelight q-value cutoff as written: decimal digits with an optional {@code .}
     * fraction, from 0 to 1 inclusive, whatever the default locale.
     *
     * @param text the text, for example {@code 0.05}
     * @return the cutoff
     * @throws IllegalArgumentException if the text is not such a number, naming the rule and not
     *     the text
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public static LimelightQValueCutoff parseCutoff(String text) {
        Objects.requireNonNull(text, "text");
        if (!DECIMAL.matcher(text).matches()) {
            throw new IllegalArgumentException(
                    "the Limelight q-value cutoff must be a decimal number from 0 to 1, written"
                            + " with digits and a '.' decimal point");
        }
        BigDecimal value = new BigDecimal(text);
        if (value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "the Limelight q-value cutoff must be a decimal number from 0 to 1");
        }
        return new LimelightQValueCutoff(value.doubleValue());
    }

    /**
     * The same settings with another cutoff.
     *
     * @param cutoff the cutoff
     * @return the settings
     */
    public LimelightSettings withQCutoff(LimelightQValueCutoff cutoff) {
        return new LimelightSettings(cutoff, importDecoys, independentDecoyPrefix, openMod);
    }

    /**
     * The same settings with decoy import on or off.
     *
     * @param decoys whether to import decoys
     * @return the settings
     */
    public LimelightSettings withImportDecoys(boolean decoys) {
        return new LimelightSettings(qCutoff, decoys, independentDecoyPrefix, openMod);
    }

    /**
     * The same settings with another independent-decoy prefix, or none.
     *
     * @param prefix the prefix, or empty
     * @return the settings
     */
    public LimelightSettings withIndependentDecoyPrefix(Optional<String> prefix) {
        return new LimelightSettings(qCutoff, importDecoys, prefix, openMod);
    }

    /**
     * The same settings with open-modification mode on or off.
     *
     * @param open whether to convert in open-modification mode
     * @return the settings
     */
    public LimelightSettings withOpenMod(boolean open) {
        return new LimelightSettings(qCutoff, importDecoys, independentDecoyPrefix, open);
    }
}
