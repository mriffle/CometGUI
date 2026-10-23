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

package org.cometgui.app.e2e.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The agent's arguments, and the two decisions it makes before anything else: whether this JVM is
 * the packaged launcher, and the one address it listens on.
 */
class BridgeArgumentsTest {

    @TempDir private Path scratch;

    @Test
    @DisplayName("port-file alone binds 127.0.0.1; bind=127.0.0.1 says the same")
    void accepted() {
        Path file = scratch.resolve("bridge.port");
        BridgeArguments plain = BridgeArguments.parse("port-file=" + file);
        BridgeArguments explicit = BridgeArguments.parse("port-file=" + file + ",bind=127.0.0.1");
        assertEquals(file, plain.portFile());
        assertEquals("127.0.0.1", plain.bind().getHostAddress());
        assertEquals(plain, explicit);
        assertTrue(plain.bind().isLoopbackAddress());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "port-file=/tmp/p,bind=0.0.0.0|bind=0.0.0.0 refused: the bridge listens on"
                        + " 127.0.0.1 only",
                "port-file=/tmp/p,bind=localhost|bind=localhost refused: the bridge listens on"
                        + " 127.0.0.1 only",
                "port-file=/tmp/p,bind=::1|bind=::1 refused: the bridge listens on 127.0.0.1 only",
                "port-file=/tmp/p,bind=192.168.1.10|bind=192.168.1.10 refused: the bridge listens"
                        + " on 127.0.0.1 only",
                "port-file=/tmp/p,bind=127.0.0.2|bind=127.0.0.2 refused: the bridge listens on"
                        + " 127.0.0.1 only",
                "bind=127.0.0.1|no port-file=/absolute/path",
                "port-file=relative/p|port-file must be absolute, but was relative/p",
                "port-file=/tmp/p,port=4|unknown argument \"port\" (known: port-file, bind)",
                "port-file=/tmp/p,port-file=/tmp/q|\"port-file\" given twice",
                "port-file|not key=value: \"port-file\"",
            })
    @DisplayName("refused, naming why: another address, no or a relative port file, an unknown key")
    void refused(String text, String why) {
        assertEquals(
                why,
                assertThrows(IllegalArgumentException.class, () -> BridgeArguments.parse(text))
                        .getMessage());
    }

    @Test
    @DisplayName("no arguments at all are refused")
    void noArguments() {
        assertThrows(IllegalArgumentException.class, () -> BridgeArguments.parse(null));
        assertThrows(IllegalArgumentException.class, () -> BridgeArguments.parse(" "));
    }

    @ParameterizedTest(name = "[{0}] [{1}] -> {2}")
    @CsvSource(
            delimiter = '|',
            nullValues = "NULL",
            value = {
                "org.cometgui.app.bootstrap.CometGuiLauncher|/opt/x/CometGUI/bin/CometGUI|true",
                "org.cometgui.app.bootstrap.CometGuiLauncher --flag|/opt/x/bin/CometGUI|true",
                "org.cometgui.app.bootstrap.CometGuiLauncher|C:\\\\Apps\\\\CometGUI.exe|true",
                "converter.jar --version|NULL|false",
                "converter.jar --version|/opt/x/CometGUI/bin/CometGUI|false",
                "org.cometgui.app.bootstrap.CometGuiLauncher|NULL|false",
                "NULL|/opt/x/CometGUI/bin/CometGUI|false",
                "org.cometgui.app.bootstrap.CometGuiLauncherX|/opt/x/bin/CometGUI|false",
                "org.cometgui.app.bootstrap.CometGuiLauncher|/opt/x/bin/OtherApp|false",
                "org.cometgui.app.bootstrap.CometGuiLauncher|/opt/x/bin/CometGUIx|false",
                "PDVGUI.jar|NULL|false",
            })
    @DisplayName(
            "the bridge starts only in the packaged launcher: its main class and jpackage's"
                    + " app path both say CometGUI")
    void onlyThePackagedLauncher(String command, String appPath, boolean starts) {
        assertEquals(starts, BridgeAgent.isCometGuiLauncher(command, appPath));
    }

    @Test
    @DisplayName("in this test JVM, which is not the packaged launcher, it would not start")
    void notInThisJvm() {
        assertFalse(
                BridgeAgent.isCometGuiLauncher(
                        System.getProperty("sun.java.command"),
                        System.getProperty("jpackage.app-path")));
    }
}
