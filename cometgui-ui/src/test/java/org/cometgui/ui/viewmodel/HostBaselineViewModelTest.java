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

package org.cometgui.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.cometgui.domain.platform.HostBaselineOutcome;
import org.cometgui.domain.platform.HostBaselineReport;
import org.cometgui.ui.testing.Nulls;
import org.cometgui.ui.viewmodel.HostBaselineViewModel.BannerLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The startup banner: which of the domain's five outcomes shows what, and what it says. */
class HostBaselineViewModelTest {

    private static HostBaselineViewModel viewModelFor(HostBaselineOutcome outcome) {
        return new HostBaselineViewModel(new HostBaselineReport(outcome, "message for " + outcome));
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("requires a report, naming the argument")
        void rejectsANullReport() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () -> new HostBaselineViewModel(Nulls.of(HostBaselineReport.class)));
            assertEquals("report", thrown.getMessage());
        }

        @Test
        @DisplayName("keeps the report it was given")
        void keepsTheReport() {
            HostBaselineReport report =
                    new HostBaselineReport(HostBaselineOutcome.SUPPORTED, "64-bit, glibc 2.36");
            assertSame(report, new HostBaselineViewModel(report).report());
        }
    }

    @Nested
    @DisplayName("the banner level")
    class Level {

        @Test
        @DisplayName("is NONE on a supported host, and nothing is shown")
        void supportedShowsNoBanner() {
            HostBaselineViewModel model = viewModelFor(HostBaselineOutcome.SUPPORTED);
            assertEquals(BannerLevel.NONE, model.level());
            assertFalse(model.bannerVisible());
            assertFalse(model.blocking());
        }

        @Test
        @DisplayName("is BLOCKING on a 32-bit host")
        void notSixtyFourBitBlocks() {
            HostBaselineViewModel model = viewModelFor(HostBaselineOutcome.NOT_64_BIT);
            assertEquals(BannerLevel.BLOCKING, model.level());
            assertTrue(model.bannerVisible());
            assertTrue(model.blocking());
        }

        @Test
        @DisplayName("is BLOCKING when glibc is too old")
        void tooOldAGlibcBlocks() {
            HostBaselineViewModel model = viewModelFor(HostBaselineOutcome.GLIBC_TOO_OLD);
            assertEquals(BannerLevel.BLOCKING, model.level());
            assertTrue(model.blocking());
        }

        @Test
        @DisplayName("is WARNING when the architecture could not be established")
        void anUndeterminedArchitectureWarns() {
            HostBaselineViewModel model =
                    viewModelFor(HostBaselineOutcome.ARCHITECTURE_UNDETERMINED);
            assertEquals(BannerLevel.WARNING, model.level());
            assertTrue(model.bannerVisible());
            assertFalse(model.blocking());
        }

        @Test
        @DisplayName("is WARNING when the glibc version could not be read")
        void anUndeterminedGlibcWarns() {
            HostBaselineViewModel model = viewModelFor(HostBaselineOutcome.GLIBC_UNDETERMINED);
            assertEquals(BannerLevel.WARNING, model.level());
            assertTrue(model.bannerVisible());
            assertFalse(model.blocking());
        }

        @ParameterizedTest
        @EnumSource(HostBaselineOutcome.class)
        @DisplayName("agrees with the domain about whether the outcome blocks")
        void neverDisagreesWithTheDomain(HostBaselineOutcome outcome) {
            HostBaselineViewModel model = viewModelFor(outcome);
            assertEquals(outcome.blocking(), model.blocking());
            assertEquals(
                    outcome != HostBaselineOutcome.SUPPORTED,
                    model.bannerVisible(),
                    "a banner is shown for every outcome except SUPPORTED");
        }
    }

    @Nested
    @DisplayName("the banner text")
    class Text {

        @Test
        @DisplayName("is the domain's diagnostic, unchanged")
        void keepsTheDomainsMessage() {
            HostBaselineReport report =
                    new HostBaselineReport(
                            HostBaselineOutcome.GLIBC_TOO_OLD,
                            "this host has glibc 2.17; the selected tools need 2.28 or newer");
            assertEquals(
                    "this host has glibc 2.17; the selected tools need 2.28 or newer",
                    new HostBaselineViewModel(report).message());
        }

        @Test
        @DisplayName("states the severity in words, not by colour alone")
        void statesTheSeverityInWords() {
            HostBaselineReport blocking =
                    new HostBaselineReport(HostBaselineOutcome.NOT_64_BIT, "this host is 32-bit");
            assertEquals(
                    "Cannot continue: this host is 32-bit",
                    new HostBaselineViewModel(blocking).bannerText());
            HostBaselineReport warning =
                    new HostBaselineReport(
                            HostBaselineOutcome.GLIBC_UNDETERMINED, "glibc version unreadable");
            assertEquals(
                    "Warning: glibc version unreadable",
                    new HostBaselineViewModel(warning).bannerText());
        }
    }

    @Nested
    @DisplayName("the banner levels")
    class Levels {

        @ParameterizedTest
        @EnumSource(BannerLevel.class)
        @DisplayName("each carry a heading a screen reader can read")
        void headingsArePresent(BannerLevel level) {
            assertFalse(level.heading().isBlank(), "blank heading on " + level);
        }

        @Test
        @DisplayName("say exactly what they say")
        void headingsAreExact() {
            assertEquals("Host baseline satisfied", BannerLevel.NONE.heading());
            assertEquals("Warning", BannerLevel.WARNING.heading());
            assertEquals("Cannot continue", BannerLevel.BLOCKING.heading());
        }

        @Test
        @DisplayName("are visible except NONE, and blocking only for BLOCKING")
        void visibilityAndBlockingAreExact() {
            assertFalse(BannerLevel.NONE.visible());
            assertFalse(BannerLevel.NONE.blocking());
            assertTrue(BannerLevel.WARNING.visible());
            assertFalse(BannerLevel.WARNING.blocking());
            assertTrue(BannerLevel.BLOCKING.visible());
            assertTrue(BannerLevel.BLOCKING.blocking());
        }
    }
}
