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

package org.cometgui.ui.viewmodel.percolator;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;

/**
 * The answer of {@link PercolatorPort#offers()}: the Tool Manager's Percolator builds, or why this
 * computer has no Tool Manager.
 *
 * @param offers the Percolator offers in the Tool Manager's order; empty when there is no Tool
 *     Manager
 * @param unavailable why there is no Tool Manager; empty when there is one
 */
public record PercolatorOffers(List<ToolOffer> offers, Optional<String> unavailable) {

    /**
     * Validates and copies.
     *
     * @throws NullPointerException if a component or an offer is {@code null}
     * @throws IllegalArgumentException if an offer is not of Percolator, if a reason is blank, or
     *     if there are offers and a reason both
     */
    public PercolatorOffers {
        offers = List.copyOf(offers);
        for (ToolOffer offer : offers) {
            if (offer.tool() != ToolName.PERCOLATOR) {
                throw new IllegalArgumentException(
                        "the Percolator section is offered only Percolator builds, not one of "
                                + offer.tool().id());
            }
        }
        Objects.requireNonNull(unavailable, "unavailable");
        if (unavailable.isPresent() && unavailable.get().isBlank()) {
            throw new IllegalArgumentException(
                    "a Tool Manager that is not available has to say why");
        }
        if (unavailable.isPresent() && !offers.isEmpty()) {
            throw new IllegalArgumentException(
                    "a computer with no Tool Manager has no Percolator offers to show");
        }
    }

    /**
     * The Tool Manager's Percolator offers.
     *
     * @param offers the offers, in its order
     * @return the answer
     */
    public static PercolatorOffers of(List<ToolOffer> offers) {
        return new PercolatorOffers(offers, Optional.empty());
    }

    /**
     * There is no Tool Manager.
     *
     * @param reason why, as a sentence
     * @return the answer
     */
    public static PercolatorOffers unavailable(String reason) {
        return new PercolatorOffers(List.of(), Optional.of(reason));
    }

    @Override
    public List<ToolOffer> offers() {
        return List.copyOf(offers);
    }
}
