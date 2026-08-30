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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import atlantafx.base.theme.Theme;
import java.util.Optional;
import org.cometgui.app.config.derived.AtlantaFxThemes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for the derived theme catalogue.
 *
 * <p>This file is NOT derived: it is new writing, so it lives outside the {@code /derived/} path
 * and carries the ordinary CometGUI header. The convention is the path, and a test of derived code
 * is not itself derived material.
 *
 * <p>Nothing here starts a JavaFX toolkit, and that is now a choice rather than a limitation:
 * cometgui-app has the headless Monocle surefire configuration since phase 02's bootstrap unit, and
 * {@code applyAsUserAgentStylesheet()} -- the one method that needs a display -- is driven for real
 * by {@link AtlantaFxThemeApplicationTest}. Keeping the two apart means the assertions here still
 * cost no toolkit.
 */
class AtlantaFxThemesTest {

    @ParameterizedTest
    @EnumSource(AtlantaFxThemes.class)
    @DisplayName("every theme resolves a stylesheet that is really on the class path")
    void resolvesARealClassPathResource(AtlantaFxThemes theme) {
        Optional<String> stylesheet = theme.stylesheet();
        assertTrue(stylesheet.isPresent(), () -> theme + " resolved no stylesheet at all");
        String path = stylesheet.orElseThrow();
        assertFalse(path.isBlank(), () -> theme + " resolved a blank stylesheet path");
        assertTrue(
                path.endsWith(".css"),
                () -> theme + " resolved '" + path + "', which is not a stylesheet");
        // The point of the test: a non-blank string proves nothing if it names nothing.
        assertNotNull(
                AtlantaFxThemes.class.getResource(path),
                () -> theme + " resolved '" + path + "', which is not a class-path resource");
    }

    @ParameterizedTest
    @EnumSource(AtlantaFxThemes.class)
    @DisplayName("the declared light/dark classification matches AtlantaFX's own")
    void classificationMatchesTheLibrary(AtlantaFxThemes theme)
            throws ReflectiveOperationException {
        Theme upstream =
                (Theme)
                        Class.forName("atlantafx.base.theme." + theme.styleClassName())
                                .getDeclaredConstructor()
                                .newInstance();
        assertEquals(
                upstream.isDarkMode(),
                theme.isDark(),
                () -> theme + " is declared " + (theme.isDark() ? "dark" : "light") + " here");
    }

    @Test
    @DisplayName("the catalogue is the seven AtlantaFX themes, three light and four dark")
    void catalogueIsComplete() {
        assertAll(
                () -> assertEquals(7, AtlantaFxThemes.values().length),
                () ->
                        assertEquals(
                                "PrimerLight, PrimerDark, NordLight, NordDark, CupertinoLight,"
                                        + " CupertinoDark, Dracula",
                                AtlantaFxThemes.knownStyleClassNames()),
                () ->
                        assertEquals(
                                4,
                                java.util.Arrays.stream(AtlantaFxThemes.values())
                                        .filter(AtlantaFxThemes::isDark)
                                        .count()));
    }

    @ParameterizedTest
    @EnumSource(AtlantaFxThemes.class)
    @DisplayName("every theme round-trips through the name a settings file records")
    void roundTripsThroughItsStyleClassName(AtlantaFxThemes theme) {
        assertSame(theme, AtlantaFxThemes.ofStyleClassName(theme.styleClassName()));
    }

    @Test
    @DisplayName("no recorded preference is not an error: it falls back to Primer Light")
    void absentPreferenceFallsBackToTheDefault() {
        assertAll(
                () -> assertSame(AtlantaFxThemes.PRIMER_LIGHT, AtlantaFxThemes.defaultTheme()),
                () -> assertFalse(AtlantaFxThemes.defaultTheme().isDark()),
                () ->
                        assertSame(
                                AtlantaFxThemes.PRIMER_LIGHT,
                                AtlantaFxThemes.ofStyleClassName(null)),
                () ->
                        assertSame(
                                AtlantaFxThemes.PRIMER_LIGHT, AtlantaFxThemes.ofStyleClassName("")),
                () ->
                        assertSame(
                                AtlantaFxThemes.PRIMER_LIGHT,
                                AtlantaFxThemes.ofStyleClassName("   ")));
    }

    @Test
    @DisplayName("a misspelt theme is rejected, and the message says what would have worked")
    void unknownThemeIsRejectedWithAUsefulMessage() {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> AtlantaFxThemes.ofStyleClassName("PrimerDarkk"));
        assertEquals(
                "Unknown AtlantaFX theme 'PrimerDarkk'. Known themes: PrimerLight, PrimerDark,"
                        + " NordLight, NordDark, CupertinoLight, CupertinoDark, Dracula.",
                thrown.getMessage());
    }

    @Test
    @DisplayName("theming reports itself available, because atlantafx-base is a real dependency")
    void themingIsAvailableOnThisClassPath() {
        assertTrue(AtlantaFxThemes.isThemingAvailable());
    }
}
