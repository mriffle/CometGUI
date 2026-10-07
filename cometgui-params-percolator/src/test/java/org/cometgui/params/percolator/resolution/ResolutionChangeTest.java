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

package org.cometgui.params.percolator.resolution;

import static org.cometgui.params.percolator.resolution.Offers.ALL_ELEVEN;
import static org.cometgui.params.percolator.resolution.Offers.linux3065NotInstalled;
import static org.cometgui.params.percolator.resolution.Offers.linux3071Installed;
import static org.cometgui.params.percolator.resolution.Offers.local;
import static org.cometgui.params.percolator.resolution.Offers.local309;
import static org.cometgui.params.percolator.resolution.Offers.macos3071NotInstalled;
import static org.cometgui.params.percolator.resolution.Offers.macos309NotInstalled;
import static org.cometgui.params.percolator.resolution.Offers.managed;
import static org.cometgui.params.percolator.resolution.Offers.observed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolOffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Gate item 3: toggling a stage re-evaluates the default and tells the user, from and to. */
class ResolutionChangeTest {

    private static final Set<DownstreamStage> LIMELIGHT =
            EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION);

    private static final Set<DownstreamStage> NONE = EnumSet.noneOf(DownstreamStage.class);

    private static final List<ToolOffer> LINUX =
            List.of(linux3071Installed(), linux3065NotInstalled(), local309());

    @Test
    @DisplayName("switching Limelight on moves the default from 3.09 to 3.07.1, naming XML_OUTPUT")
    void switchedOn() {
        ResolutionChange change =
                ResolutionChange.between(
                        PercolatorResolver.resolve(LINUX, NONE),
                        PercolatorResolver.resolve(LINUX, LIMELIGHT));
        assertTrue(change.changed());
        assertEquals("3.09", change.from().orElseThrow().version().text());
        assertEquals("3.07.1", change.to().orElseThrow().version().text());
        assertEquals(
                "The default Percolator changed from 3.09 (registered local binary) to 3.07.1"
                        + " because Limelight conversion was switched on and needs XML_OUTPUT.",
                change.message());
    }

    @Test
    @DisplayName("switching Limelight off moves it back, saying XML_OUTPUT is no longer needed")
    void switchedOff() {
        ResolutionChange change =
                ResolutionChange.between(
                        PercolatorResolver.resolve(LINUX, LIMELIGHT),
                        PercolatorResolver.resolve(LINUX, NONE));
        assertTrue(change.changed());
        assertEquals(
                "The default Percolator changed from 3.07.1 to 3.09 (registered local binary)"
                        + " because Limelight conversion was switched off, so XML_OUTPUT is no"
                        + " longer needed.",
                change.message());
    }

    @Test
    @DisplayName("switching Limelight on with no XML build: unchanged, and unavailable is said")
    void switchedOnButUnavailable() {
        List<ToolOffer> macos = List.of(macos3071NotInstalled(), macos309NotInstalled());
        ResolutionChange change =
                ResolutionChange.between(
                        PercolatorResolver.resolve(macos, NONE),
                        PercolatorResolver.resolve(macos, LIMELIGHT));
        assertFalse(change.changed());
        assertEquals(change.from(), change.to());
        assertEquals(
                "The default Percolator is still 3.09 (Limelight conversion was switched on and"
                        + " needs XML_OUTPUT, but no Percolator here has been observed to have it,"
                        + " so Limelight conversion is unavailable).",
                change.message());
    }

    @Test
    @DisplayName("switching Limelight on when the newest already has XML: unchanged")
    void switchedOnNoChange() {
        List<ToolOffer> offers = List.of(linux3071Installed(), linux3065NotInstalled());
        ResolutionChange change =
                ResolutionChange.between(
                        PercolatorResolver.resolve(offers, NONE),
                        PercolatorResolver.resolve(offers, LIMELIGHT));
        assertFalse(change.changed());
        assertEquals(
                "The default Percolator is still 3.07.1 (Limelight conversion was switched on and"
                        + " needs XML_OUTPUT).",
                change.message());
    }

    @Test
    @DisplayName("an install finishing is the same build: unchanged, builds changed")
    void installFinished() {
        ToolOffer before =
                managed(
                        "3.07.1",
                        ToolInstallState.NOT_INSTALLED,
                        observed(ALL_ELEVEN.subList(0, 2)),
                        List.of());
        ResolutionChange change =
                ResolutionChange.between(
                        PercolatorResolver.resolve(List.of(before), LIMELIGHT),
                        PercolatorResolver.resolve(List.of(linux3071Installed()), LIMELIGHT));
        assertFalse(change.changed());
        assertEquals(
                "The default Percolator is still 3.07.1 (the Percolator builds available on this"
                        + " computer changed).",
                change.message());
    }

    @Test
    @DisplayName("same version, other origin: a different build")
    void originMatters() {
        ToolOffer localSame = local("3.07.1", observed(ALL_ELEVEN));
        ResolutionChange change =
                ResolutionChange.between(
                        PercolatorResolver.resolve(List.of(localSame), NONE),
                        PercolatorResolver.resolve(List.of(linux3071Installed()), NONE));
        assertTrue(change.changed());
        assertEquals(
                "The default Percolator changed from 3.07.1 (registered local binary) to 3.07.1"
                        + " because the Percolator builds available on this computer changed.",
                change.message());
    }

    @Test
    @DisplayName("from nothing to something, and from nothing to nothing")
    void fromNothing() {
        PercolatorResolution nothing = PercolatorResolver.resolve(List.of(), NONE);
        ResolutionChange appeared =
                ResolutionChange.between(
                        nothing, PercolatorResolver.resolve(List.of(local309()), NONE));
        assertTrue(appeared.changed());
        assertEquals(Optional.empty(), appeared.from());
        assertEquals(
                "The default Percolator changed from no Percolator to 3.09 (registered local"
                        + " binary) because the Percolator builds available on this computer"
                        + " changed.",
                appeared.message());
        ResolutionChange disappeared =
                ResolutionChange.between(
                        PercolatorResolver.resolve(List.of(local309()), NONE), nothing);
        assertTrue(disappeared.changed());
        assertEquals(
                "The default Percolator changed from 3.09 (registered local binary) to no"
                        + " Percolator because the Percolator builds available on this computer"
                        + " changed.",
                disappeared.message());
        ResolutionChange still = ResolutionChange.between(nothing, nothing);
        assertFalse(still.changed());
        assertEquals(
                "The default Percolator is still no Percolator (the Percolator builds available"
                        + " on this computer changed).",
                still.message());
    }

    @Test
    @DisplayName("refuses nulls and a blank message")
    void refusals() {
        PercolatorResolution any = PercolatorResolver.resolve(List.of(), NONE);
        assertThrows(NullPointerException.class, () -> ResolutionChange.between(null, any));
        assertThrows(NullPointerException.class, () -> ResolutionChange.between(any, null));
        assertThrows(
                NullPointerException.class,
                () -> new ResolutionChange(false, null, Optional.empty(), "m"));
        assertThrows(
                NullPointerException.class,
                () -> new ResolutionChange(false, Optional.empty(), null, "m"));
        assertThrows(
                NullPointerException.class,
                () -> new ResolutionChange(false, Optional.empty(), Optional.empty(), null));
        IllegalArgumentException blank =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ResolutionChange(false, Optional.empty(), Optional.empty(), " "));
        assertEquals("message must not be blank", blank.getMessage());
    }
}
