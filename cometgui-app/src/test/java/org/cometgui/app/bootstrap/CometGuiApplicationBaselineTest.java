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

package org.cometgui.app.bootstrap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.ClockRunIdSource;
import org.cometgui.app.config.PlatformFileSystemAccess;
import org.cometgui.app.testing.FakeEnvironment;
import org.cometgui.app.testing.FxToolkit;
import org.cometgui.app.testing.Nulls;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.platform.GlibcVersionSource;
import org.cometgui.ui.controls.UiIds;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the bootstrap does with the host-baseline report: {@code R-PLAT-01}'s "verified at startup
 * and reported to the user".
 *
 * <p>Three hosts are started here, and two of them cannot exist in this project's environment: a
 * 32-bit machine and a Linux machine whose C library cannot be identified. That is exactly why
 * {@link CometGuiApplication} takes an {@link ApplicationServices} -- the composition root is the
 * thing that varies, and a bootstrap that read {@code System.getProperty} inline would leave the
 * blocking path untestable on every machine this project owns.
 *
 * <p><strong>"Before the stage is shown" is asserted, not assumed.</strong> Each test registers a
 * listener on the stage's {@code showing} property <em>before</em> {@link
 * CometGuiApplication#start(Stage)} is called, and reads the banner from inside it. What that
 * listener sees is the state of the scene at the instant the window became visible; a bootstrap
 * that filled the banner in afterwards would be caught by it.
 */
class CometGuiApplicationBaselineTest {

    private static final Instant WHEN = Instant.parse("2026-08-30T09:00:00Z");

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        FxToolkit.start();
    }

    /** What one started application left behind, read at the instant the window became visible. */
    private record Startup(Stage stage, String bannerAtShowTime, BoundedMessageLog log) {}

    private static Startup start(FakeEnvironment environment, GlibcVersionSource glibc)
            throws InterruptedException {
        BoundedMessageLog log = new BoundedMessageLog(16);
        ApplicationServices services =
                new ApplicationServices(
                        Clock.fixed(WHEN, ZoneOffset.UTC),
                        environment,
                        new PlatformFileSystemAccess(environment),
                        new ClockRunIdSource(Clock.fixed(WHEN, ZoneOffset.UTC)),
                        glibc,
                        null,
                        null,
                        null);
        AtomicReference<String> bannerAtShowTime = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        FxToolkit.onFxThread(
                () -> {
                    Stage window = new Stage();
                    window.showingProperty()
                            .addListener(
                                    (property, was, showing) -> {
                                        if (Boolean.TRUE.equals(showing)) {
                                            bannerAtShowTime.set(bannerTextOf(window));
                                        }
                                    });
                    new CometGuiApplication(services, log).start(window);
                    stage.set(window);
                });
        return new Startup(stage.get(), bannerAtShowTime.get(), log);
    }

    private static String bannerTextOf(Stage window) {
        Label banner = (Label) window.getScene().lookup("#" + UiIds.HOST_BASELINE_BANNER);
        return banner == null ? null : banner.getText();
    }

    private static Label banner(Stage window) throws InterruptedException {
        return FxToolkit.callOnFxThread(
                () -> (Label) window.getScene().lookup("#" + UiIds.HOST_BASELINE_BANNER));
    }

    private static void close(Stage window) throws InterruptedException {
        FxToolkit.onFxThread(window::hide);
    }

    @Test
    @DisplayName("a supported host shows no banner, and the window carries the shell and the title")
    void aSupportedHostShowsNoBanner() throws InterruptedException {
        Startup startup =
                start(FakeEnvironment.linux64(), () -> Optional.of(GlibcVersion.of(2, 36, 0)));
        Label banner = banner(startup.stage());

        try {
            assertAll(
                    () -> assertTrue(startup.stage().isShowing()),
                    () -> assertEquals("CometGUI", startup.stage().getTitle()),
                    () ->
                            assertEquals(
                                    UiIds.SHELL_ROOT, startup.stage().getScene().getRoot().getId()),
                    () -> assertNotNull(banner, "the banner slot must exist even when hidden"),
                    () -> assertFalse(banner.isVisible(), "banner: " + banner.getText()),
                    () -> assertFalse(banner.isManaged(), "a hidden banner must not take space"),
                    () ->
                            assertTrue(
                                    banner.getText()
                                            .startsWith(
                                                    "Host baseline satisfied: Host baseline OK:"),
                                    banner.getText()));
        } finally {
            close(startup.stage());
        }
    }

    @Test
    @DisplayName("a 32-bit host gets a BLOCKING banner, shown, and the JVM is NOT exited")
    void aBlockingHostIsReportedAndTheApplicationStillStarts() throws InterruptedException {
        FakeEnvironment thirtyTwoBit =
                FakeEnvironment.linux64()
                        .withProperty("sun.arch.data.model", "32")
                        .withProperty("os.arch", "i386");

        Startup startup = start(thirtyTwoBit, () -> Optional.of(GlibcVersion.of(2, 36, 0)));
        Label banner = banner(startup.stage());

        try {
            assertAll(
                    () ->
                            assertTrue(
                                    startup.stage().isShowing(),
                                    "a blocking baseline must be reported, not enforced by exit"),
                    () -> assertTrue(banner.isVisible(), "the blocking banner must be shown"),
                    () -> assertTrue(banner.isManaged()),
                    () ->
                            assertTrue(
                                    banner.getText().startsWith("Cannot continue: "),
                                    "blocking must be distinguishable in text, not colour alone: "
                                            + banner.getText()),
                    () ->
                            assertTrue(
                                    banner.getText()
                                            .contains(
                                                    "CometGUI requires a 64-bit operating system"),
                                    banner.getText()),
                    () ->
                            assertTrue(
                                    banner.getText().contains("sun.arch.data.model=32"),
                                    "the diagnostic must name the host's own value: "
                                            + banner.getText()),
                    () ->
                            assertEquals(
                                    banner.getText(),
                                    startup.bannerAtShowTime(),
                                    "the banner must be populated BEFORE the window is shown"));
        } finally {
            close(startup.stage());
        }
    }

    @Test
    @DisplayName(
            "a Linux host whose glibc cannot be read gets a WARNING banner, worded differently")
    void anUndeterminedGlibcIsAWarning() throws InterruptedException {
        Startup startup = start(FakeEnvironment.linux64(), Optional::empty);
        Label banner = banner(startup.stage());

        try {
            assertAll(
                    () -> assertTrue(banner.isVisible()),
                    () ->
                            assertTrue(
                                    banner.getText().startsWith("Warning: "),
                                    "a warning must not read like a blocking outcome: "
                                            + banner.getText()),
                    () ->
                            assertFalse(
                                    banner.getText().startsWith("Cannot continue: "),
                                    banner.getText()),
                    () ->
                            assertTrue(
                                    banner.getText().contains("glibc 2.14.0 or newer"),
                                    "the warning must name the floor that was checked: "
                                            + banner.getText()),
                    () ->
                            assertEquals(
                                    banner.getText(),
                                    startup.bannerAtShowTime(),
                                    "the banner must be populated BEFORE the window is shown"));
        } finally {
            close(startup.stage());
        }
    }

    @Test
    @DisplayName("a host below the floor gets the blocking glibc diagnostic, naming both versions")
    void anOldGlibcIsBlocking() throws InterruptedException {
        Startup startup =
                start(FakeEnvironment.linux64(), () -> Optional.of(GlibcVersion.parse("2.12")));
        Label banner = banner(startup.stage());

        try {
            assertAll(
                    () -> assertTrue(banner.isVisible()),
                    () -> assertTrue(banner.getText().startsWith("Cannot continue: ")),
                    () ->
                            assertTrue(
                                    banner.getText().contains("This host has glibc 2.12"),
                                    banner.getText()),
                    () ->
                            assertTrue(
                                    banner.getText().contains("require glibc 2.14.0 or newer"),
                                    banner.getText()));
        } finally {
            close(startup.stage());
        }
    }

    @Test
    @DisplayName("the startup floor is 2.14, the lowest requirement of any managed tool build")
    void theStartupFloorIsTheLowestManagedFloor() {
        assertAll(
                () ->
                        assertEquals(
                                GlibcVersion.of(2, 14, 0), CometGuiApplication.STARTUP_GLIBC_FLOOR),
                () -> assertEquals("2.14.0", CometGuiApplication.STARTUP_GLIBC_FLOOR.toString()),
                () -> assertEquals("CometGUI", CometGuiApplication.WINDOW_TITLE),
                () -> assertEquals(1280.0, CometGuiApplication.INITIAL_WIDTH, 0.0),
                () -> assertEquals(800.0, CometGuiApplication.INITIAL_HEIGHT, 0.0));
    }

    @Test
    @DisplayName("the baseline statement reaches the shared message log at a matching severity")
    void theBaselineIsRecordedInTheMessageLog() throws InterruptedException {
        Startup supported =
                start(FakeEnvironment.linux64(), () -> Optional.of(GlibcVersion.of(2, 36, 0)));
        Startup warning = start(FakeEnvironment.linux64(), Optional::empty);
        Startup blocking =
                start(
                        FakeEnvironment.linux64().withProperty("sun.arch.data.model", "32"),
                        () -> Optional.of(GlibcVersion.of(2, 36, 0)));

        try {
            assertAll(
                    () -> assertEquals(MessageSeverity.INFO, only(supported).severity()),
                    () -> assertEquals(MessageSeverity.WARNING, only(warning).severity()),
                    () -> assertEquals(MessageSeverity.ERROR, only(blocking).severity()),
                    () ->
                            assertEquals(
                                    WHEN,
                                    only(supported).timestamp(),
                                    "the message is timestamped from the injected clock"),
                    () ->
                            assertEquals(
                                    Optional.empty(),
                                    only(supported).stage(),
                                    "startup narration belongs to no workflow stage"),
                    () ->
                            assertEquals(
                                    bannerTextOnFxThread(supported.stage()),
                                    only(supported).text(),
                                    "the console and the banner must say the same thing"));
        } finally {
            close(supported.stage());
            close(warning.stage());
            close(blocking.stage());
        }
    }

    private static String bannerTextOnFxThread(Stage window) throws InterruptedException {
        return FxToolkit.callOnFxThread(() -> bannerTextOf(window));
    }

    private static LogMessage only(Startup startup) {
        List<LogMessage> messages = startup.log().snapshot();
        assertEquals(1, messages.size(), "expected exactly one startup message, got " + messages);
        return messages.get(0);
    }

    @Test
    @DisplayName("the constructors reject what they must")
    void rejectsNulls() {
        assertAll(
                () ->
                        assertEquals(
                                "services",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new CometGuiApplication(
                                                                Nulls.of(ApplicationServices.class),
                                                                new BoundedMessageLog(4)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "messageLog",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new CometGuiApplication(
                                                                ApplicationServices.forThisHost(),
                                                                Nulls.of(BoundedMessageLog.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "primaryStage",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new CometGuiApplication(
                                                                        ApplicationServices
                                                                                .forThisHost(),
                                                                        new BoundedMessageLog(4))
                                                                .start(Nulls.of(Stage.class)))
                                        .getMessage()));
    }
}
