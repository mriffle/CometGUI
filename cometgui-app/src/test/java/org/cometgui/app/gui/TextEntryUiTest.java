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

package org.cometgui.app.gui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.stream.Stream;
import javafx.scene.input.KeyCode;
import org.cometgui.app.uidriver.FxUiDriver;
import org.cometgui.app.uidriver.RobotFxUiDriver;
import org.cometgui.app.uidriver.TestFxUiDriver;
import org.cometgui.domain.build.BuildIdentity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The drivers' text entry ({@code FxUiDriver.typeInto} and {@code type}) types exactly what it
 * claims, through both implementations: every character class the parameter-editor tests type --
 * lower and upper case letters, digits, and {@code - _ . / , +} and space -- arrives in a real text
 * field as typed, replacing what the field held, and nothing is committed until Enter.
 */
class TextEntryUiTest {

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-guitest", "unknown", Instant.parse("2026-10-05T00:00:00Z"));

    /** Every character class the drivers type. */
    private static final String TYPED = "Rev_Decoy-9.8/x,y+z AZ09";

    private static ParameterEditorApp app;

    @BeforeAll
    static void launch() {
        app = ParameterEditorApp.launch(BUILD);
        ParameterEditorApp.openEditor(new TestFxUiDriver(app.application()));
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.stop();
        }
    }

    static Stream<FxUiDriver> drivers() {
        return Stream.of(
                new TestFxUiDriver(app.application()), new RobotFxUiDriver(app.application()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drivers")
    @DisplayName("typeInto replaces a field's text with exactly the characters typed")
    void typeIntoTypesExactlyTheText(FxUiDriver driver) {
        assertEquals("DECOY_", driver.textOf("ess-decoy_prefix"), "the release's default first");
        driver.typeInto("ess-decoy_prefix", TYPED);
        assertAll(
                "after write, before Enter",
                () -> assertEquals(TYPED, driver.textOf("ess-decoy_prefix")),
                () -> assertEquals("ess-decoy_prefix", driver.focusedNodeId()),
                () ->
                        assertEquals(
                                "Value from: Comet 2026.03.0 default",
                                driver.textOf("ess-decoy_prefix-origin"),
                                "nothing is committed by typing"));

        driver.type("Q");
        assertEquals(TYPED + "Q", driver.textOf("ess-decoy_prefix"), "type appends at the caret");

        driver.typeInto("ess-decoy_prefix", "DECOY_");
        driver.press(KeyCode.ENTER);
        assertEquals("DECOY_", driver.textOf("ess-decoy_prefix"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("drivers")
    @DisplayName("a character a driver cannot type is refused, naming it, before anything is typed")
    void anUntypeableCharacterIsRefused(FxUiDriver driver) {
        AssertionError refused = assertThrows(AssertionError.class, () -> driver.type("ok#"));
        assertEquals("this driver does not type the character '#'", refused.getMessage());
    }
}
