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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.NoManagedBuild;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.ui.testing.Editors;
import org.cometgui.ui.testing.Nulls;
import org.cometgui.ui.testing.ScriptedToolManager;
import org.cometgui.ui.testing.ToolOffers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code D-011}: what the Tool Manager says and offers on a computer for which no managed build of
 * a tool exists -- an Intel Mac and Comet -- driven over a scripted port with no toolkit started.
 *
 * <p>The sentences are pinned whole and hand-typed, because they are what a scientist reads. The
 * scripted port answers with what {@code ManagedToolManager} answers on an Intel Mac over the
 * shipped manifest; that half is graded in {@code ToolManagerWiringTest} against the real manifest.
 */
class NoManagedBuildViewModelTest {

    private static final HostPlatform INTEL_MAC =
            new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.X86_64);

    private static final Path OWN_COMET =
            Path.of("Users", "scientist", "comet", "comet.exe").toAbsolutePath();

    private static final String INTEL_MAC_COMET =
            "No CometGUI-managed Comet exists for an Intel Mac (macos-x86-64): the Comet developers"
                    + " publish no build of it for this kind of computer, so there is nothing for"
                    + " CometGUI to download and install. You can register a Comet you have built"
                    + " or obtained yourself: CometGUI runs it to read its version and check what"
                    + " it can do, and then lists it below as your own binary.";

    /** The port as an Intel Mac sees it: both Comet releases unavailable, Comet missing. */
    private static ScriptedToolManager intelMac() {
        ScriptedToolManager manager =
                new ScriptedToolManager(
                        ToolOffers.cometUnavailableHere("2026.03.0"),
                        ToolOffers.cometUnavailableHere("2026.02.2"),
                        ToolOffers.percolatorAvailable());
        manager.missing(new NoManagedBuild(ToolName.COMET, INTEL_MAC, true));
        return manager;
    }

    private static ToolManagerViewModel over(
            ScriptedToolManager manager, Editors.ScriptedChooser chooser) {
        ToolManagerViewModel viewModel =
                new ToolManagerViewModel(manager, Runnable::run, Runnable::run, chooser);
        viewModel.refresh();
        return viewModel;
    }

    @Test
    @DisplayName("an Intel Mac is told plainly that no managed Comet exists, and offered its own")
    void anIntelMacIsToldAndOfferedRegistration() {
        ToolManagerViewModel viewModel = over(intelMac(), new Editors.ScriptedChooser());

        NoManagedBuildViewModel gap = viewModel.noManagedBuild().get(0);

        assertAll(
                () -> assertEquals(1, viewModel.noManagedBuild().size()),
                () -> assertEquals(ToolName.COMET, gap.tool()),
                () -> assertEquals(INTEL_MAC_COMET, gap.explanationText()),
                () -> assertEquals("Register your own Comet...", gap.registerActionText()),
                () -> assertTrue(gap.canRegister()),
                () -> assertEquals("", gap.status()),
                () ->
                        assertTrue(
                                viewModel.rows().stream()
                                        .filter(row -> row.offer().tool() == ToolName.COMET)
                                        .noneMatch(ToolRowViewModel::canInstall),
                                "and no Comet row offers an install that cannot work"));
    }

    @Test
    @DisplayName("registering the user's own Comet runs through the port and lists it as theirs")
    void registeringTheUsersOwnComet() {
        ScriptedToolManager manager = intelMac();
        manager.registering((tool, executable) -> ToolOffers.localComet(executable));
        Editors.ScriptedChooser chooser = new Editors.ScriptedChooser().file(OWN_COMET);
        ToolManagerViewModel viewModel = over(manager, chooser);
        NoManagedBuildViewModel gap = viewModel.noManagedBuild().get(0);

        boolean started = viewModel.register(gap);

        assertAll(
                () -> assertTrue(started),
                () -> assertEquals(List.of("file:a Comet executable to register"), chooser.asked()),
                () -> assertEquals(List.of("comet " + OWN_COMET), manager.registrationsAsked()),
                () ->
                        assertEquals(
                                "Registered Comet 2026.03.0 from "
                                        + OWN_COMET
                                        + ". It is listed below as your own binary.",
                                gap.status()),
                () -> assertFalse(gap.registeringProperty().get()),
                () ->
                        assertSame(
                                gap,
                                viewModel.noManagedBuild().get(0),
                                "the entry survives the refresh, so its status stays on screen"),
                () ->
                        assertEquals(
                                List.of(
                                        "comet 2026.03.0 MANAGED",
                                        "comet 2026.02.2 MANAGED",
                                        "percolator 3.07.1 MANAGED",
                                        "comet 2026.03.0 LOCAL"),
                                rowsOf(viewModel),
                                "the registered Comet is a row after the refresh"));
    }

    @Test
    @DisplayName("a refused registration says why, in the registrar's own words, and adds no row")
    void aRefusedRegistrationSaysWhy() {
        ScriptedToolManager manager = intelMac();
        manager.registering(
                (tool, executable) -> {
                    throw new ToolRegistrationException(
                            "The file at " + executable + " is not Comet: it printed nothing.");
                });
        ToolManagerViewModel viewModel =
                over(manager, new Editors.ScriptedChooser().file(OWN_COMET));
        NoManagedBuildViewModel gap = viewModel.noManagedBuild().get(0);

        viewModel.register(gap);

        assertAll(
                () ->
                        assertEquals(
                                "Comet was not registered: The file at "
                                        + OWN_COMET
                                        + " is not Comet: it printed nothing.",
                                gap.status()),
                () -> assertTrue(gap.canRegister(), "and the user can try another file"),
                () -> assertEquals(3, viewModel.rows().size()));
    }

    @Test
    @DisplayName("a registration that fails unexpectedly is reported, not swallowed")
    void anUnexpectedFailureIsReported() {
        ScriptedToolManager manager = intelMac();
        manager.registering(
                (tool, executable) -> {
                    throw new IllegalStateException("boom");
                });
        ToolManagerViewModel viewModel =
                over(manager, new Editors.ScriptedChooser().file(OWN_COMET));
        NoManagedBuildViewModel gap = viewModel.noManagedBuild().get(0);

        viewModel.register(gap);

        assertEquals(
                "Comet could not be registered: java.lang.IllegalStateException: boom",
                gap.status());
    }

    @Test
    @DisplayName("no file chosen registers nothing, and says so")
    void noFileChosenRegistersNothing() {
        ScriptedToolManager manager = intelMac();
        ToolManagerViewModel viewModel = over(manager, new Editors.ScriptedChooser());
        NoManagedBuildViewModel gap = viewModel.noManagedBuild().get(0);

        boolean started = viewModel.register(gap);

        assertAll(
                () -> assertFalse(started),
                () -> assertEquals(List.of(), manager.registrationsAsked()),
                () -> assertEquals("No file was chosen, so nothing was registered.", gap.status()));
    }

    @Test
    @DisplayName("a registration already running cannot be started twice")
    void aRunningRegistrationIsNotStartedTwice() {
        ScriptedToolManager manager = intelMac();
        manager.registering((tool, executable) -> ToolOffers.localComet(executable));
        List<Runnable> queued = new ArrayList<>();
        Editors.ScriptedChooser chooser =
                new Editors.ScriptedChooser().file(OWN_COMET).file(OWN_COMET);
        ToolManagerViewModel viewModel =
                new ToolManagerViewModel(manager, Runnable::run, queued::add, chooser);
        viewModel.refresh();
        NoManagedBuildViewModel gap = viewModel.noManagedBuild().get(0);

        boolean first = viewModel.register(gap);
        boolean second = viewModel.register(gap);

        assertAll(
                () -> assertTrue(first),
                () -> assertFalse(second),
                () -> assertTrue(gap.registeringProperty().get()),
                () -> assertFalse(gap.canRegister()),
                () ->
                        assertEquals(
                                "Registering "
                                        + OWN_COMET
                                        + ": CometGUI is running it to read its version and check"
                                        + " what it can do.",
                                gap.status()),
                () -> assertEquals(1, queued.size()),
                () -> assertEquals(List.of(), manager.registrationsAsked()));
    }

    @Test
    @DisplayName("a tool the product cannot register is explained, and no chooser is opened")
    void aToolThatCannotBeRegisteredIsExplained() {
        ScriptedToolManager manager =
                new ScriptedToolManager(ToolOffers.percolatorUnavailableHere());
        HostPlatform linuxArm =
                new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.AARCH64);
        manager.missing(new NoManagedBuild(ToolName.PDV, linuxArm, false));
        Editors.ScriptedChooser chooser = new Editors.ScriptedChooser().file(OWN_COMET);
        ToolManagerViewModel viewModel = over(manager, chooser);
        NoManagedBuildViewModel gap = viewModel.noManagedBuild().get(0);

        assertAll(
                () ->
                        assertEquals(
                                "No CometGUI-managed PDV exists for Linux on a 64-bit ARM"
                                        + " processor (linux-aarch64): the PDV developers publish"
                                        + " no build of it for this kind of computer, so there is"
                                        + " nothing for CometGUI to download and install. CometGUI"
                                        + " cannot register a PDV of your own either, so PDV cannot"
                                        + " be used on this computer.",
                                gap.explanationText()),
                () -> assertFalse(gap.canRegister()),
                () -> assertFalse(viewModel.register(gap)),
                () -> assertEquals(List.of(), chooser.asked()));
    }

    @Test
    @DisplayName("Apple silicon, Linux and Windows have a managed Comet, and nothing is said")
    void otherHostsSayNothing() {
        ToolManagerViewModel viewModel =
                over(
                        new ScriptedToolManager(
                                ToolOffers.oneReleaseOfferedTwice().get(0),
                                ToolOffers.percolatorAvailable()),
                        new Editors.ScriptedChooser());

        assertEquals(List.of(), viewModel.noManagedBuild());
    }

    @Test
    @DisplayName("an entry whose fact changed is replaced, and one no longer reported is dropped")
    void entriesFollowThePort() {
        ScriptedToolManager manager = intelMac();
        ToolManagerViewModel viewModel = over(manager, new Editors.ScriptedChooser());
        NoManagedBuildViewModel before = viewModel.noManagedBuild().get(0);

        manager.missing(new NoManagedBuild(ToolName.COMET, INTEL_MAC, false));
        viewModel.refresh();
        NoManagedBuildViewModel changed = viewModel.noManagedBuild().get(0);
        manager.missing();
        viewModel.refresh();

        assertAll(
                () -> assertFalse(before == changed, "a different fact is a different entry"),
                () -> assertFalse(changed.canRegister()),
                () -> assertEquals(List.of(), viewModel.noManagedBuild()));
    }

    @Test
    @DisplayName("an entry this Tool Manager is not showing cannot be acted on")
    void aForeignEntryIsRefused() {
        NoManagedBuildViewModel foreign =
                over(intelMac(), new Editors.ScriptedChooser()).noManagedBuild().get(0);
        ToolManagerViewModel viewModel = over(intelMac(), new Editors.ScriptedChooser());

        assertAll(
                () ->
                        assertThrows(
                                IllegalArgumentException.class, () -> viewModel.register(foreign)),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> viewModel.register(Nulls.of(NoManagedBuildViewModel.class))),
                () ->
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        ToolManagerViewModel.unavailable("no tools", Runnable::run)
                                                .register(foreign)));
    }

    @Test
    @DisplayName("a host with no Tool Manager reports no tool as missing")
    void noToolManagerReportsNothing() {
        ToolManagerViewModel viewModel =
                ToolManagerViewModel.unavailable("no tools", Runnable::run);

        viewModel.refresh();

        assertEquals(List.of(), viewModel.noManagedBuild());
    }

    @Test
    @DisplayName("every platform and every tool has its own plain words")
    void plainWords() {
        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "Linux on a 64-bit Intel or AMD processor",
                                        "Linux on a 64-bit ARM processor",
                                        "an Intel Mac",
                                        "a Mac with Apple silicon",
                                        "Windows on a 64-bit Intel or AMD processor",
                                        "Windows on a 64-bit ARM processor"),
                                List.of(
                                                platform(
                                                        HostOperatingSystem.LINUX,
                                                        HostArchitecture.X86_64),
                                                platform(
                                                        HostOperatingSystem.LINUX,
                                                        HostArchitecture.AARCH64),
                                                platform(
                                                        HostOperatingSystem.MACOS,
                                                        HostArchitecture.X86_64),
                                                platform(
                                                        HostOperatingSystem.MACOS,
                                                        HostArchitecture.AARCH64),
                                                platform(
                                                        HostOperatingSystem.WINDOWS,
                                                        HostArchitecture.X86_64),
                                                platform(
                                                        HostOperatingSystem.WINDOWS,
                                                        HostArchitecture.AARCH64))
                                        .stream()
                                        .map(NoManagedBuildViewModel::platformWords)
                                        .toList()),
                () ->
                        assertEquals(
                                List.of("Comet", "Percolator", "PDV", "Limelight converter"),
                                List.of(ToolName.values()).stream()
                                        .map(NoManagedBuildViewModel::toolWords)
                                        .toList()),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        NoManagedBuildViewModel.platformWords(
                                                Nulls.of(HostPlatform.class))),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> NoManagedBuildViewModel.toolWords(Nulls.of(ToolName.class))),
                () ->
                        assertEquals(
                                "NoManagedBuildViewModel[comet on macos-x86-64]",
                                new NoManagedBuildViewModel(
                                                new NoManagedBuild(ToolName.COMET, INTEL_MAC, true))
                                        .toString()),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> new NoManagedBuildViewModel(Nulls.of(NoManagedBuild.class))));
    }

    private static HostPlatform platform(HostOperatingSystem os, HostArchitecture arch) {
        return new HostPlatform(os, arch);
    }

    private static List<String> rowsOf(ToolManagerViewModel viewModel) {
        return viewModel.rows().stream()
                .map(
                        row ->
                                row.nameText()
                                        + " "
                                        + (row.offer().origin() == ToolOrigin.LOCAL
                                                ? "LOCAL"
                                                : "MANAGED"))
                .toList();
    }
}
