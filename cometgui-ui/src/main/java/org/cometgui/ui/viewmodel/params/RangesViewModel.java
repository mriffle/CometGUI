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
import org.cometgui.params.comet.schema.ValueKind;

/** Every two-value range parameter of the selected release, each as a {@link RangeViewModel}. */
public final class RangesViewModel {

    private final ParameterSession session;

    /**
     * The ranges of a session.
     *
     * @param session the session
     */
    public RangesViewModel(ParameterSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    /**
     * The release's {@code INTEGER_RANGE} and {@code DECIMAL_RANGE} parameters, in its order.
     *
     * @return the ranges
     */
    public List<RangeViewModel> ranges() {
        return session.fields().stream()
                .filter(
                        field ->
                                field.kind() == ValueKind.INTEGER_RANGE
                                        || field.kind() == ValueKind.DECIMAL_RANGE)
                .map(field -> new RangeViewModel(session, field.name()))
                .toList();
    }

    /**
     * One range.
     *
     * @param name a range parameter, such as {@code peptide_length_range}
     * @return its view-model
     * @throws IllegalArgumentException if the release has no such range parameter
     */
    public RangeViewModel range(String name) {
        FieldViewModel field = session.field(name);
        if (field.kind() != ValueKind.INTEGER_RANGE && field.kind() != ValueKind.DECIMAL_RANGE) {
            throw new IllegalArgumentException(name + " is not a two-value range");
        }
        return new RangeViewModel(session, name);
    }
}
