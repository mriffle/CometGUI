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

package org.cometgui.app.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.application.Application;
import org.cometgui.app.config.derived.AtlantaFxThemes;
import org.cometgui.app.testing.FxToolkit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AtlantaFxThemes#applyAsUserAgentStylesheet()}, driven for real.
 *
 * <p>This is the one method of the derived theme catalogue that {@code AtlantaFxThemesTest} could
 * not exercise: it needs a started JavaFX toolkit, and cometgui-app had no headless surefire
 * configuration until this work unit added one. The class is separate rather than merged into that
 * test so that the rest of the catalogue's assertions keep costing no toolkit at all.
 *
 * <p>What is asserted is the exact stylesheet JavaFX ends up holding, read back through {@link
 * Application#getUserAgentStylesheet()} -- the round trip, not the return value of the call under
 * test. A method that returned {@code true} and set nothing would pass an assertion on its own
 * result and fail this one.
 */
class AtlantaFxThemeApplicationTest {

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        FxToolkit.start();
    }

    @Test
    @DisplayName("applying a theme sets exactly that theme's stylesheet as the user agent's")
    void applyingSetsTheStylesheet() throws InterruptedException {
        for (AtlantaFxThemes theme : AtlantaFxThemes.values()) {
            String expected = theme.stylesheet().orElseThrow();
            boolean applied = FxToolkit.callOnFxThread(theme::applyAsUserAgentStylesheet);
            String inForce = FxToolkit.callOnFxThread(Application::getUserAgentStylesheet);

            assertAll(
                    theme.name(),
                    () ->
                            assertTrue(
                                    applied,
                                    "AtlantaFX is on the class path; apply returned false"),
                    () ->
                            assertEquals(
                                    expected,
                                    inForce,
                                    "the stylesheet in force is not this theme's"),
                    () ->
                            assertTrue(
                                    inForce.endsWith(".css"),
                                    "the stylesheet in force is not a stylesheet: " + inForce));
        }
    }

    @Test
    @DisplayName("the default theme's stylesheet is AtlantaFX's Primer Light, by exact path")
    void theDefaultThemeIsPrimerLight() throws InterruptedException {
        FxToolkit.onFxThread(() -> AtlantaFxThemes.defaultTheme().applyAsUserAgentStylesheet());

        assertAll(
                () -> assertEquals(AtlantaFxThemes.PRIMER_LIGHT, AtlantaFxThemes.defaultTheme()),
                () ->
                        assertEquals(
                                "/atlantafx/base/theme/primer-light.css",
                                FxToolkit.callOnFxThread(Application::getUserAgentStylesheet),
                                "this exact string is what the startup smoke test asserts"),
                () -> assertTrue(AtlantaFxThemes.isThemingAvailable()));
    }

    @Test
    @DisplayName("switching themes replaces the stylesheet rather than accumulating one")
    void switchingReplaces() throws InterruptedException {
        FxToolkit.onFxThread(() -> AtlantaFxThemes.NORD_DARK.applyAsUserAgentStylesheet());
        String dark = FxToolkit.callOnFxThread(Application::getUserAgentStylesheet);
        FxToolkit.onFxThread(() -> AtlantaFxThemes.CUPERTINO_LIGHT.applyAsUserAgentStylesheet());
        String light = FxToolkit.callOnFxThread(Application::getUserAgentStylesheet);

        assertAll(
                () -> assertEquals("/atlantafx/base/theme/nord-dark.css", dark),
                () -> assertEquals("/atlantafx/base/theme/cupertino-light.css", light));
    }
}
