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

package org.cometgui.ui.viewmodel.params;

import java.util.List;
import java.util.Objects;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.value.IonSeries;
import org.cometgui.params.comet.value.IonSeriesSelection;

/**
 * The ion-series family as named check boxes rather than numeric flags: a, b, c, x, y, z and z+1
 * ions from the model's {@link IonSeries}, and the water and ammonia losses of b and y ions. What
 * is on is read from the model's {@link IonSeriesSelection}; a box sets its parameter's {@code
 * Flag} through the session.
 */
public final class IonSeriesViewModel {

    private final ParameterSession session;

    /**
     * The ion-series boxes of a session.
     *
     * @param session the session
     */
    public IonSeriesViewModel(ParameterSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    /**
     * The series, in the order {@code comet -q} writes them.
     *
     * @return the series
     */
    public List<IonSeries> series() {
        return List.of(IonSeries.values());
    }

    /**
     * The field of one series' box: its name ({@code Score a ions}), help, origin and findings.
     *
     * @param series the series
     * @return the field
     */
    public FieldViewModel field(IonSeries series) {
        return session.field(Objects.requireNonNull(series, "series").parameter());
    }

    /**
     * The field of the neutral-loss box.
     *
     * @return the field of {@code use_NL_ions}
     */
    public FieldViewModel neutralLossField() {
        return session.field(IonSeriesSelection.NEUTRAL_LOSS_PARAMETER);
    }

    /**
     * Whether a series is scored.
     *
     * @param series the series
     * @return {@code true} if the configuration switches it on
     */
    public boolean isOn(IonSeries series) {
        return session.model().ionSeries().series().contains(series);
    }

    /**
     * Whether the water and ammonia losses of b and y ions are scored.
     *
     * @return {@code true} if {@code use_NL_ions} is on
     */
    public boolean neutralLossOn() {
        return session.model().ionSeries().neutralLossPeaks();
    }

    /**
     * Switches a series on or off.
     *
     * @param series the series
     * @param on whether to score it
     * @return the outcome
     */
    public EditOutcome set(IonSeries series, boolean on) {
        return session.setValue(field(series).name(), new ParameterValue.Flag(on));
    }

    /**
     * Switches the neutral-loss peaks on or off.
     *
     * @param on whether to score them
     * @return the outcome
     */
    public EditOutcome setNeutralLoss(boolean on) {
        return session.setValue(
                IonSeriesSelection.NEUTRAL_LOSS_PARAMETER, new ParameterValue.Flag(on));
    }
}
