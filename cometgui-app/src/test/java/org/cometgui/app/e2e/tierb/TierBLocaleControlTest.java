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

package org.cometgui.app.e2e.tierb;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.cometgui.app.e2e.scenario.Screen;
import org.cometgui.app.e2e.support.EndToEnd;
import org.cometgui.app.e2e.support.FreshHome;
import org.cometgui.app.e2e.support.RunnerLocale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/**
 * The harness's locale control in Tier B (Phase 14 unit 7; design decision P14-9): the installed
 * application launched exactly as the Tier B scenarios launch it, except that its environment names
 * no locale at all -- no {@code LC_ALL}, {@code LC_CTYPE} or {@code LANG}, this build host's own
 * state -- and the assertion every Tier B launch makes, {@link RunnerLocale#assertUtf8()} over what
 * the <em>launched process</em> reports through its bridge, must then fail loudly, naming the
 * encoding. A harness whose locale check could not go red here would let a runner with no UTF-8
 * locale lose the non-ASCII coverage without a word.
 *
 * <p>It then records, without repairing anything (the launcher's locale is Phase 16's, {@code
 * STATUS.rst} <em>Risks currently live</em> item 1), what the packaged application does with a home
 * under a path with an accented letter in that locale: whether its window appears, where its
 * application data directory went, what a first install does, and what it printed. Those
 * observations are printed for the report; they are not asserted, because they describe the defect
 * Phase 16 is to remove.
 *
 * <p><strong>Measured on 2026-10-23</strong>: the window appears and the interface reads normally;
 * the launched JVM sees the home's accented letter as two U+FFFD characters, so {@code
 * PlatformFileSystemAccess} cannot represent {@code XDG_DATA_HOME}, ignores it without a word, and
 * falls back to {@code user.home} -- the account's home from the password database, not {@code
 * HOME} -- where the first install then writes the tool cache and the default project. So that the
 * record cannot write into the real account's home, this one launch is given {@code -Duser.home}
 * naming a directory of the test's own ({@code account-home}); the Tier B scenarios are not.
 *
 * <p>Reads outside the module: as {@link TierBApplication}, minus the {@code D-006} fixture.
 */
@EndToEnd
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TierBLocaleControlTest {

    /** How long the bridge, and then the window, may take to appear. */
    private static final Duration START = Duration.ofMinutes(3);

    /** How long the application is given to show a window in a locale it cannot represent. */
    private static final int WINDOW_SECONDS = 60;

    /** The Comet the record tries to install. */
    private static final String COMET = "2026.03.0";

    private Path work;

    private InstalledCometGui installed;

    private PackagedDisplay display;

    private FreshHome home;

    private Path accountHome;

    private LaunchedCometGui launched;

    private Map<String, String> reported;

    @BeforeAll
    void installAndLaunchWithoutALocale(@TempDir Path scratch) throws IOException {
        work = scratch.toRealPath();
        Path bridgeJar = BridgeJar.build(work.resolve("bridge").resolve("cometgui-e2e-bridge.jar"));
        installed =
                InstalledCometGui.install(
                        InstalledCometGui.builtArchive(), work.resolve("install"));
        display = PackagedDisplay.start(work.resolve("display"));
        home =
                FreshHome.create(
                        Files.createDirectories(work.resolve("homes")),
                        FreshHome.Naming.SPACE_AND_NON_ASCII);
        accountHome = Files.createDirectories(work.resolve("account-home"));
        Path launch = work.resolve("launch");
        launched =
                LaunchedCometGui.start(
                        installed,
                        display,
                        home.home(),
                        launch.resolve("cwd"),
                        bridgeJar,
                        launch.resolve("bridge.port"),
                        List.of("-Duser.home=" + accountHome),
                        Set.of("LC_ALL"));
    }

    @AfterAll
    void stopAndUninstall() throws IOException {
        try {
            if (launched != null) {
                launched.close();
                System.out.println(
                        "[locale control] the application printed: " + launched.output());
            }
            if (display != null) {
                display.close();
            }
        } finally {
            if (installed != null) {
                installed.uninstall();
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName(
            "with no locale in its environment, the launched process reports a non-UTF-8"
                    + " sun.jnu.encoding, and the harness's assertion fails naming it")
    void theLocaleAssertionFailsLoudly() {
        launched.connect(START);
        reported = launched.environment();
        RunnerLocale locale = launched.locale();
        AssertionError refused = assertThrows(AssertionError.class, locale::assertUtf8);
        System.out.println("[locale control] the assertion's message: " + refused.getMessage());
        String encoding = reported.get(RunnerLocale.SUN_JNU_ENCODING);
        assertAll(
                "the launched process, with no locale: " + reported,
                () -> assertFalse(reported.containsKey("LC_ALL"), "LC_ALL is unset"),
                () -> assertFalse(reported.containsKey("LC_CTYPE"), "LC_CTYPE is unset"),
                () -> assertFalse(reported.containsKey("LANG"), "LANG is unset"),
                () ->
                        assertEquals(
                                installed.runtime().toString(),
                                reported.get("java.home"),
                                "it is the installed runtime"),
                () -> assertFalse(locale.isUtf8(), "sun.jnu.encoding=" + encoding),
                () ->
                        assertTrue(
                                refused.getMessage()
                                        .startsWith(
                                                "The end-to-end harness needs a UTF-8 runner, and"
                                                        + " the packaged CometGUI (pid "
                                                        + launched.endpoint().pid()
                                                        + ") has sun.jnu.encoding="
                                                        + encoding
                                                        + ", not UTF-8"),
                                refused.getMessage()),
                () ->
                        assertTrue(
                                refused.getMessage()
                                        .contains("LC_ALL unset, LC_CTYPE unset, LANG unset"),
                                refused.getMessage()));
    }

    @Test
    @Order(2)
    @DisplayName(
            "recorded, not asserted (Phase 16's repair): what the application does with a"
                    + " non-ASCII home in that locale")
    void whatHappensToTheNonAsciiHome() throws IOException {
        assertEquals(
                accountHome.toString(),
                reported.get("user.home"),
                "the record's writes fall back to the test's own directory, never the account's"
                        + " real home");
        List<String> observed = new ArrayList<>();
        observed.add("reported HOME=" + reported.get("HOME"));
        observed.add("reported XDG_DATA_HOME=" + reported.get("XDG_DATA_HOME"));
        observed.add("reported user.home=" + reported.get("user.home"));
        String window;
        try {
            window =
                    "shown, titled "
                            + launched.bridge().text("bridge.awaitApplication", WINDOW_SECONDS);
        } catch (AssertionError | RuntimeException failed) {
            window = "not shown within " + WINDOW_SECONDS + " s: " + failed.getMessage();
        }
        observed.add("window: " + window);
        if (window.startsWith("shown")) {
            Screen screen = new Screen(launched.driver());
            try {
                screen.show("tool-manager");
                observed.add(
                        "Tool Manager: "
                                + screen.await(
                                        "tool-manager-summary",
                                        "the Tool Manager to read its list",
                                        said ->
                                                !"The tool list has not been read yet."
                                                        .equals(said),
                                        Duration.ofMinutes(2)));
                String row = "tool-row-comet-" + COMET.replace('.', '_') + "-1";
                screen.ui().clickOn(row + "-install");
                String state =
                        screen.await(
                                row + "-state",
                                "the Comet install to end",
                                said ->
                                        said.equals("CometGUI-managed: installed")
                                                || said.contains("did not succeed"),
                                Duration.ofMinutes(5));
                observed.add("Install Comet " + COMET + ": " + state);
                for (String part : List.of("diagnostic", "path")) {
                    if (screen.ui().exists(row + "-" + part)) {
                        observed.add("  " + part + ": " + screen.ui().textOf(row + "-" + part));
                    }
                }
            } catch (AssertionError | RuntimeException failed) {
                observed.add("the interface could not be read: " + failed.getMessage());
            }
        }
        observed.add("process alive: " + !launched.hasExited());
        observed.add(
                "the fresh home's application data directory "
                        + home.applicationDataDirectory()
                        + (Files.exists(home.applicationDataDirectory())
                                ? " exists"
                                : " does not exist"));
        for (Path tree : List.of(work.resolve("homes"), accountHome)) {
            try (Stream<Path> walk = Files.walk(tree)) {
                observed.add(
                        "under "
                                + tree
                                + ": "
                                + walk.map(path -> tree.relativize(path).toString())
                                        .filter(path -> !path.isEmpty())
                                        .sorted()
                                        .toList());
            }
        }
        for (String line : observed) {
            System.out.println("[locale control] " + line);
        }
    }
}
