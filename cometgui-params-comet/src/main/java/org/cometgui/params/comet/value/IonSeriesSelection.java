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

package org.cometgui.params.comet.value;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Which fragment-ion series a search scores, and whether it also scores the water and ammonia
 * losses of b and y ions: the eight parameters {@code use_A_ions} .. {@code use_Z1_ions} and {@code
 * use_NL_ions} as one value.
 *
 * <p>Each parameter is {@code 0} or {@code 1}, which is all Comet's documentation for the 2026.02
 * release allows; any other text is refused as unreadable. {@code use_NL_ions} is a plain boolean
 * in the metadata, not a series -- it modifies how b and y are scored -- so it is a separate
 * component here.
 *
 * @param series the series switched on
 * @param neutralLossPeaks whether {@code use_NL_ions} is on
 */
public record IonSeriesSelection(Set<IonSeries> series, boolean neutralLossPeaks) {

    /** The parameter for water and ammonia losses. */
    public static final String NEUTRAL_LOSS_PARAMETER = "use_NL_ions";

    private static final String ON = "1";

    private static final String OFF = "0";

    /** Takes an immutable copy. */
    public IonSeriesSelection {
        EnumSet<IonSeries> copy = EnumSet.noneOf(IonSeries.class);
        copy.addAll(series);
        series = Collections.unmodifiableSet(copy);
    }

    /**
     * The series switched on, immutable.
     *
     * @return the series
     */
    @Override
    public Set<IonSeries> series() {
        EnumSet<IonSeries> copy = EnumSet.noneOf(IonSeries.class);
        copy.addAll(series);
        return Collections.unmodifiableSet(copy);
    }

    /**
     * Reads the family from the parameters' value texts.
     *
     * @param values value text by parameter name; must hold all eight parameters, and may hold
     *     others, which are ignored
     * @return the selection
     * @throws ValueSyntaxException naming the parameter, if one is missing or is not {@code 0} or
     *     {@code 1}
     */
    public static IonSeriesSelection parse(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        EnumSet<IonSeries> on = EnumSet.noneOf(IonSeries.class);
        for (IonSeries series : IonSeries.values()) {
            if (flag(values, series.parameter())) {
                on.add(series);
            }
        }
        return new IonSeriesSelection(on, flag(values, NEUTRAL_LOSS_PARAMETER));
    }

    private static boolean flag(Map<String, String> values, String name) {
        String text = values.get(name);
        if (text == null) {
            throw new ValueSyntaxException(
                    name, "value", "is missing; the ion-series family is read as a whole");
        }
        String stripped = text.strip();
        if (!ON.equals(stripped) && !OFF.equals(stripped)) {
            throw new ValueSyntaxException(name, "value", "\"" + stripped + "\" is not 0 or 1");
        }
        return ON.equals(stripped);
    }

    /**
     * The value text of each of the eight parameters, in the order {@code comet -q} writes them.
     *
     * @return {@code 0} or {@code 1} by parameter name
     */
    public Map<String, String> format() {
        Map<String, String> texts = new LinkedHashMap<>();
        for (IonSeries each : IonSeries.values()) {
            texts.put(each.parameter(), series.contains(each) ? ON : OFF);
        }
        texts.put(NEUTRAL_LOSS_PARAMETER, neutralLossPeaks ? ON : OFF);
        return Collections.unmodifiableMap(texts);
    }
}
