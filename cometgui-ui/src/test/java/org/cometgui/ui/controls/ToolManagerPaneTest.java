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

package org.cometgui.ui.controls;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.NoManagedBuild;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.ui.testing.Editors;
import org.cometgui.ui.testing.FxToolkit;
import org.cometgui.ui.testing.ScriptedToolManager;
import org.cometgui.ui.testing.ToolOffers;
import org.cometgui.ui.viewmodel.ToolManagerViewModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Tool Manager on screen, headless, over a scripted port.
 *
 * <p>This is the half the real runtime cannot show on this machine: a build whose install is
 * running, a build whose last attempt failed, a build this host does not meet the requirements of,
 * and a binary the user registered. Every assertion below is on a <strong>rendered node</strong>
 * looked up by its stable identifier, because a view-model field that is right and a label that
 * shows something else is precisely the failure this test exists to catch.
 */
class ToolManagerPaneTest {

    /*
     * Absolute, and not a literal: SpotBugs rejects a hard-coded absolute path
     * (DMI_HARDCODED_ABSOLUTE_FILENAME) and ToolOffer requires an installed path to be absolute.
     * The assertions below therefore pin the wording the view contributes and take the path as an
     * input, which is what it is.
     */
    private static final Path COMET_AT =
            Path.of("opt", "cometgui", "tools", "comet", "2026.2.2", "bin", "comet")
                    .toAbsolutePath();

    private static final Path LOCAL_PERCOLATOR =
            Path.of("usr", "local", "bin", "percolator").toAbsolutePath();

    private static final HostPlatform INTEL_MAC =
            new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.X86_64);

    private static final String COMET = "comet-2026_02_2-1";

    private static final String PERCOLATOR_3_09 = "percolator-3_09-1";

    private static final String PERCOLATOR_3_07_1 = "percolator-3_07_1-1";

    private static final String PERCOLATOR_3_06_5 = "percolator-3_06_5-1";

    private static final String PDV = "pdv-2_7_0-1";

    private static final String LOCAL = "percolator-3_05-1";

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        FxToolkit.start();
    }

    @Test
    @DisplayName("every offer the port returns gets a row, in the port's order")
    void everyOfferGetsARow() throws InterruptedException {
        Fixture fixture = shown(everyKindOfOffer());

        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        UiIds.toolRow(COMET),
                                        UiIds.toolRow(PERCOLATOR_3_09),
                                        UiIds.toolRow(PERCOLATOR_3_07_1),
                                        UiIds.toolRow(PERCOLATOR_3_06_5),
                                        UiIds.toolRow(PDV),
                                        UiIds.toolRow(LOCAL)),
                                fixture.rowIds()),
                () ->
                        assertEquals(
                                "6 tool builds on this host.",
                                fixture.textOf(UiIds.TOOL_MANAGER_SUMMARY)));
    }

    @Test
    @DisplayName("an installed row shows its state, its probed capabilities and where it lives")
    void anInstalledRowShowsItsStateCapabilitiesAndPath() throws InterruptedException {
        Fixture fixture = shown(everyKindOfOffer());

        assertAll(
                () -> assertEquals("comet 2026.02.2", fixture.textOf(UiIds.toolRowName(COMET))),
                () ->
                        assertEquals(
                                "CometGUI-managed: installed",
                                fixture.textOf(UiIds.toolRowState(COMET))),
                () ->
                        assertEquals(
                                "Capabilities: PIN_OUTPUT (observed-by-execution)",
                                fixture.textOf(UiIds.toolRowCapabilities(COMET))),
                () ->
                        assertEquals(
                                "Installed at: " + COMET_AT,
                                fixture.textOf(UiIds.toolRowPath(COMET))),
                () -> assertTrue(fixture.isShowing(UiIds.toolRowPath(COMET))),
                () ->
                        assertEquals(
                                "Download: 7,014,400 bytes",
                                fixture.textOf(UiIds.toolRowDownload(COMET))),
                () ->
                        assertTrue(
                                fixture.isDisabled(UiIds.toolRowInstall(COMET)),
                                "an installed build needs nothing fetched"));
    }

    @Test
    @DisplayName("an available row shows its advisories and the whole transfer it would start")
    void anAvailableRowShowsItsAdvisoriesAndDownload() throws InterruptedException {
        Fixture fixture = shown(everyKindOfOffer());

        assertAll(
                () ->
                        assertEquals(
                                "percolator 3.07.1",
                                fixture.textOf(UiIds.toolRowName(PERCOLATOR_3_07_1))),
                () ->
                        assertEquals(
                                "CometGUI-managed: not installed",
                                fixture.textOf(UiIds.toolRowState(PERCOLATOR_3_07_1))),
                () ->
                        assertEquals(
                                "Capabilities: XML_OUTPUT (observed-by-execution), XML_DECOY_OUTPUT"
                                        + " (observed-by-execution)",
                                fixture.textOf(UiIds.toolRowCapabilities(PERCOLATOR_3_07_1))),
                () ->
                        assertEquals(
                                "Advisories:\n"
                                        + "Percolator 3.07.1 predates 3.08's change of the default"
                                        + " PEP regressor to I-splines, so its posterior error"
                                        + " probabilities are computed the older way.\n"
                                        + "Percolator 3.07.1 predates the fix for PEP values"
                                        + " exceeding 1.0 (upstream issue #394, fixed in 3.08.1 and"
                                        + " 3.09), so a PEP above 1.0 can appear in its output.",
                                fixture.textOf(UiIds.toolRowAdvisories(PERCOLATOR_3_07_1)),
                                "R-PERC-11 wants these at selection time, in the domain's words"),
                () ->
                        assertEquals(
                                "Download: 2,798,963 bytes",
                                fixture.textOf(UiIds.toolRowDownload(PERCOLATOR_3_07_1))),
                () -> assertFalse(fixture.isDisabled(UiIds.toolRowInstall(PERCOLATOR_3_07_1))),
                () ->
                        assertFalse(
                                fixture.isShowing(UiIds.toolRowDiagnostic(PERCOLATOR_3_07_1)),
                                "a build the loader never refused shows no diagnostic, and the"
                                        + " slot is still in the scene to be asserted empty"),
                () -> assertFalse(fixture.isShowing(UiIds.toolRowPath(PERCOLATOR_3_07_1))),
                () -> assertFalse(fixture.isShowing(UiIds.toolRowProgress(PERCOLATOR_3_07_1))));
    }

    @Test
    @DisplayName("a build upstream does not publish here is shown, not hidden (R-PERC-01)")
    void aBuildNotPublishedHereIsShownNotHidden() throws InterruptedException {
        Fixture fixture = shown(everyKindOfOffer());

        assertAll(
                () ->
                        assertNotNull(
                                fixture.node(UiIds.toolRow(PERCOLATOR_3_09)),
                                "percolator 3.09 publishes no Linux artefact and must still have a"
                                        + " row: absent is a fact the user needs"),
                () ->
                        assertEquals(
                                "CometGUI-managed: not published for this platform",
                                fixture.textOf(UiIds.toolRowState(PERCOLATOR_3_09))),
                () ->
                        assertEquals(
                                "Download: nothing to fetch on this host",
                                fixture.textOf(UiIds.toolRowDownload(PERCOLATOR_3_09))),
                () ->
                        assertEquals(
                                "Capabilities: none declared",
                                fixture.textOf(UiIds.toolRowCapabilities(PERCOLATOR_3_09))),
                () -> assertTrue(fixture.isDisabled(UiIds.toolRowInstall(PERCOLATOR_3_09))));
    }

    @Test
    @DisplayName("the R-PLAT-03 diagnostic reaches the screen word for word")
    void theLoaderDiagnosticReachesTheScreenWordForWord() throws InterruptedException {
        Fixture fixture = shown(everyKindOfOffer());

        assertAll(
                () ->
                        assertEquals(
                                "This build cannot run on this host: libstdc++.so.6 on this host"
                                        + " does not provide a symbol version this build needs."
                                        + " Required: GLIBCXX_3.4.29. Available on this host:"
                                        + " GLIBCXX_3.4.28. Alternatives: percolator 3.07.1;"
                                        + " register a local binary.",
                                fixture.textOf(UiIds.toolRowDiagnostic(PERCOLATOR_3_06_5)),
                                "the required version, the host's version and the alternatives are"
                                        + " what R-PLAT-03 asks for, and none of them is re-worded"
                                        + " on the way to the screen"),
                () -> assertTrue(fixture.isShowing(UiIds.toolRowDiagnostic(PERCOLATOR_3_06_5))),
                () ->
                        assertEquals(
                                "CometGUI-managed: cannot run on this host",
                                fixture.textOf(UiIds.toolRowState(PERCOLATOR_3_06_5))),
                () -> assertTrue(fixture.isDisabled(UiIds.toolRowInstall(PERCOLATOR_3_06_5))));
    }

    @Test
    @DisplayName("a failed row says the attempt failed and claims no reason it does not have")
    void aFailedRowClaimsNoReasonItDoesNotHave() throws InterruptedException {
        Fixture fixture = shown(everyKindOfOffer());

        assertAll(
                () ->
                        assertEquals(
                                "CometGUI-managed: the last install attempt did not succeed",
                                fixture.textOf(UiIds.toolRowState(PDV))),
                () ->
                        assertFalse(
                                fixture.isShowing(UiIds.toolRowDiagnostic(PDV)),
                                "a checksum, extraction or probe failure carries no diagnostic"
                                        + " through the port, and the view does not invent one"),
                () ->
                        assertFalse(
                                fixture.isDisabled(UiIds.toolRowInstall(PDV)),
                                "a failed attempt can be tried again"));
    }

    @Test
    @DisplayName("a binary the user registered is shown as theirs, with nothing to fetch")
    void aRegisteredLocalBinaryIsShownAsTheirs() throws InterruptedException {
        Fixture fixture = shown(everyKindOfOffer());

        assertAll(
                () -> assertEquals("percolator 3.05", fixture.textOf(UiIds.toolRowName(LOCAL))),
                () ->
                        assertEquals(
                                "Your own binary: installed",
                                fixture.textOf(UiIds.toolRowState(LOCAL))),
                () ->
                        assertEquals(
                                "Capabilities: PSM_TSV_OUTPUT (unverified)",
                                fixture.textOf(UiIds.toolRowCapabilities(LOCAL)),
                                "R-TOOL-08: absent positive evidence, and the screen says so"),
                () ->
                        assertEquals(
                                "Installed at: " + LOCAL_PERCOLATOR,
                                fixture.textOf(UiIds.toolRowPath(LOCAL))),
                () ->
                        assertEquals(
                                "Download: nothing to fetch on this host",
                                fixture.textOf(UiIds.toolRowDownload(LOCAL))),
                () -> assertTrue(fixture.isDisabled(UiIds.toolRowInstall(LOCAL))));
    }

    @Test
    @DisplayName("pressing Install starts one, and the row then reports progress and can cancel")
    void pressingInstallStartsOneAndTheRowFollowsIt() throws InterruptedException {
        ScriptedToolManager manager = new ScriptedToolManager(ToolOffers.percolatorAvailable());
        Fixture fixture = shown(manager);

        fixture.press(UiIds.toolRowInstall(PERCOLATOR_3_07_1));
        String afterStarting = fixture.textOf(UiIds.toolRowState(PERCOLATOR_3_07_1));
        boolean cancelEnabled = !fixture.isDisabled(UiIds.toolRowCancel(PERCOLATOR_3_07_1));

        FxToolkit.onFxThread(
                () ->
                        manager.report(
                                new InstallProgress(
                                        ToolName.PERCOLATOR,
                                        ToolVersion.parse("3.07.1"),
                                        InstallPhase.DOWNLOADING,
                                        1_048_576L,
                                        2_798_963L)));

        assertAll(
                () -> assertEquals(List.of("percolator 3.07.1"), manager.installsAsked()),
                () -> assertEquals("CometGUI-managed: installing", afterStarting),
                () -> assertTrue(cancelEnabled),
                () -> assertTrue(fixture.isDisabled(UiIds.toolRowInstall(PERCOLATOR_3_07_1))),
                () ->
                        assertEquals(
                                "Downloading: 1,048,576 of 2,798,963 bytes",
                                fixture.textOf(UiIds.toolRowProgress(PERCOLATOR_3_07_1)),
                                "the row a user is looking at moves while the install runs"),
                () -> assertTrue(fixture.isShowing(UiIds.toolRowProgress(PERCOLATOR_3_07_1))));
    }

    @Test
    @DisplayName("pressing Cancel calls cancel on the handle the install returned")
    void pressingCancelCallsCancelOnTheHandle() throws InterruptedException {
        ScriptedToolManager manager = new ScriptedToolManager(ToolOffers.percolatorAvailable());
        Fixture fixture = shown(manager);
        fixture.press(UiIds.toolRowInstall(PERCOLATOR_3_07_1));
        int beforeTheClick = manager.handle().cancellations();

        fixture.press(UiIds.toolRowCancel(PERCOLATOR_3_07_1));

        assertAll(
                () -> assertEquals(0, beforeTheClick),
                () ->
                        assertEquals(
                                1,
                                manager.handle().cancellations(),
                                "the button reaches InstallHandle.cancel(), not merely a flag of"
                                        + " the view's own"));
    }

    @Test
    @DisplayName("two builds of one release are two rows, and neither lookup finds the other")
    void twoBuildsOfOneReleaseAreTwoRows() throws InterruptedException {
        List<ToolOffer> twice = ToolOffers.oneReleaseOfferedTwice();
        Fixture fixture = shown(new ScriptedToolManager(twice.get(0), twice.get(1)));

        Node first = fixture.node(UiIds.toolRow("comet-2026_02_2-1"));
        Node second = fixture.node(UiIds.toolRow("comet-2026_02_2-2"));

        assertAll(
                () -> assertNotNull(first),
                () -> assertNotNull(second),
                () -> assertNotSame(first, second),
                () -> assertEquals(2, fixture.rowIds().size()),
                () ->
                        assertEquals(
                                "comet 2026.02.2",
                                fixture.textOf(UiIds.toolRowName("comet-2026_02_2-2")),
                                "the second build renders as itself rather than being hidden by"
                                        + " the first"));
    }

    @Test
    @DisplayName("a Tool Manager this host has none of explains itself and lists nothing")
    void anUnavailableToolManagerExplainsItself() throws InterruptedException {
        ToolManagerViewModel viewModel =
                ToolManagerViewModel.unavailable(
                        "CometGUI manages tool installations on 64-bit Linux, macOS and Windows,"
                                + " and this machine reports os.name=\"Plan 9\" os.arch=\"386\".",
                        Runnable::run);
        Fixture fixture = new Fixture(viewModel);
        fixture.build();
        FxToolkit.onFxThread(viewModel::refresh);

        assertAll(
                () ->
                        assertEquals(
                                "CometGUI manages tool installations on 64-bit Linux, macOS and"
                                        + " Windows, and this machine reports os.name=\"Plan 9\""
                                        + " os.arch=\"386\".",
                                fixture.textOf(UiIds.TOOL_MANAGER_SUMMARY)),
                () -> assertEquals(List.of(), fixture.rowIds()));
    }

    @Test
    @DisplayName("every control in the section carries a non-blank accessible name")
    void everyControlCarriesAnAccessibleName() throws InterruptedException {
        Fixture fixture = shown(everyKindOfOffer());

        List<String> unnamed = new ArrayList<>();
        for (Control control : fixture.controls()) {
            String name = control.getAccessibleText();
            if (name == null || name.isBlank()) {
                unnamed.add(control.getClass().getSimpleName() + " with id " + control.getId());
            }
        }

        assertAll(
                () -> assertEquals(List.of(), unnamed),
                () ->
                        assertTrue(
                                fixture.controls().size() >= 6 * 10 + 1,
                                () ->
                                        "six rows of ten controls and a summary line at least, but"
                                                + " the walk found "
                                                + fixture.controls().size()));
    }

    @Test
    @DisplayName(
            "D-011: on an Intel Mac the section says plainly that no managed Comet exists and"
                    + " offers the user's own")
    void anIntelMacIsToldAndOfferedRegistration() throws InterruptedException {
        ScriptedToolManager manager =
                new ScriptedToolManager(
                        ToolOffers.cometUnavailableHere("2026.03.0"),
                        ToolOffers.cometUnavailableHere("2026.02.2"),
                        ToolOffers.percolatorAvailable());
        manager.missing(new NoManagedBuild(ToolName.COMET, INTEL_MAC, true));
        Path own = Path.of("Users", "scientist", "comet.exe").toAbsolutePath();
        manager.registering((tool, executable) -> ToolOffers.localComet(executable));
        Fixture fixture =
                new Fixture(
                        new ToolManagerViewModel(
                                manager,
                                Runnable::run,
                                Runnable::run,
                                new Editors.ScriptedChooser().file(own)));
        fixture.build();
        fixture.refresh();
        String register = UiIds.noManagedBuildRegister("comet");

        boolean shownBefore = fixture.isShowing(UiIds.TOOL_MANAGER_NO_MANAGED_BUILD);
        String explanation = fixture.textOf(UiIds.noManagedBuildText("comet"));
        boolean buttonShown = fixture.isShowing(register);
        boolean buttonEnabled = !fixture.isDisabled(register);
        String buttonText = ((Button) fixture.node(register)).getText();
        boolean installOffered = !fixture.isDisabled(UiIds.toolRowInstall("comet-2026_03_0-1"));
        fixture.press(register);

        assertAll(
                () -> assertTrue(shownBefore),
                () ->
                        assertEquals(
                                "No CometGUI-managed Comet exists for an Intel Mac (macos-x86-64):"
                                        + " the Comet developers publish no build of it for this"
                                        + " kind of computer, so there is nothing for CometGUI to"
                                        + " download and install. You can register a Comet you"
                                        + " have built or obtained yourself: CometGUI runs it to"
                                        + " read its version and check what it can do, and then"
                                        + " lists it below as your own binary.",
                                explanation),
                () -> assertTrue(buttonShown),
                () -> assertTrue(buttonEnabled),
                () -> assertEquals("Register your own Comet...", buttonText),
                () -> assertFalse(installOffered, "no install that cannot work is offered"),
                () -> assertEquals(List.of("comet " + own), manager.registrationsAsked()),
                () ->
                        assertEquals(
                                "Registered Comet 2026.03.0 from "
                                        + own
                                        + ". It is listed below as your own binary.",
                                fixture.textOf(UiIds.noManagedBuildStatus("comet"))),
                () ->
                        assertEquals(
                                "Your own binary: installed",
                                fixture.textOf(UiIds.toolRowState("comet-2026_03_0-2")),
                                "and the registered Comet is a row"));
    }

    @Test
    @DisplayName("where every tool has a managed build, the section says nothing extra")
    void nothingMissingShowsNothing() throws InterruptedException {
        Fixture fixture = shown(everyKindOfOffer());

        assertAll(
                () -> assertFalse(fixture.isShowing(UiIds.TOOL_MANAGER_NO_MANAGED_BUILD)),
                () ->
                        assertEquals(
                                null,
                                fixture.node(UiIds.noManagedBuild("comet")),
                                "no explanation exists to be found"));
    }

    @Test
    @DisplayName("a tool the product cannot register is explained with no register action")
    void aToolThatCannotBeRegisteredHasNoAction() throws InterruptedException {
        ScriptedToolManager manager = new ScriptedToolManager(ToolOffers.percolatorAvailable());
        manager.missing(new NoManagedBuild(ToolName.PDV, INTEL_MAC, false));
        Fixture fixture = shown(manager);

        assertAll(
                () -> assertTrue(fixture.isShowing(UiIds.noManagedBuildText("pdv"))),
                () -> assertFalse(fixture.isShowing(UiIds.noManagedBuildRegister("pdv"))),
                () -> assertFalse(fixture.isShowing(UiIds.noManagedBuildStatus("pdv"))));
    }

    private static ScriptedToolManager everyKindOfOffer() {
        return new ScriptedToolManager(
                ToolOffers.cometInstalled(COMET_AT),
                ToolOffers.percolatorUnavailableHere(),
                ToolOffers.percolatorAvailable(),
                ToolOffers.percolatorBeyondThisHost(),
                ToolOffers.pdvFailed(),
                ToolOffers.localPercolator(LOCAL_PERCOLATOR));
    }

    private static Fixture shown(ScriptedToolManager manager) throws InterruptedException {
        Fixture fixture =
                new Fixture(
                        new ToolManagerViewModel(
                                manager,
                                Runnable::run,
                                Runnable::run,
                                new Editors.ScriptedChooser()));
        fixture.build();
        fixture.refresh();
        return fixture;
    }

    /** A scene holding one Tool Manager pane, and the few questions these tests ask of it. */
    private static final class Fixture {

        private final ToolManagerViewModel viewModel;

        private Scene scene;

        Fixture(ToolManagerViewModel viewModel) {
            this.viewModel = viewModel;
        }

        void build() throws InterruptedException {
            FxToolkit.onFxThread(
                    () -> {
                        scene = new Scene(new ToolManagerPane(viewModel), 1024, 768);
                        scene.getRoot().applyCss();
                        scene.getRoot().layout();
                    });
        }

        void refresh() throws InterruptedException {
            FxToolkit.onFxThread(viewModel::refresh);
        }

        Node node(String id) {
            return scene.lookup("#" + id);
        }

        String textOf(String id) {
            Node found = node(id);
            assertNotNull(found, "no control with id " + id);
            return ((Label) found).getText();
        }

        boolean isShowing(String id) {
            Node found = node(id);
            assertNotNull(found, "no control with id " + id);
            return found.isVisible() && found.isManaged();
        }

        boolean isDisabled(String id) {
            Node found = node(id);
            assertNotNull(found, "no control with id " + id);
            return ((Button) found).isDisable();
        }

        void press(String id) throws InterruptedException {
            Node found = node(id);
            assertNotNull(found, "no control with id " + id);
            FxToolkit.onFxThread(((Button) found)::fire);
        }

        List<String> rowIds() {
            Parent rows = (Parent) node(UiIds.TOOL_MANAGER_ROWS);
            assertNotNull(rows, "the Tool Manager has no row container");
            List<String> ids = new ArrayList<>();
            for (Node child : rows.getChildrenUnmodifiable()) {
                ids.add(child.getId());
            }
            return ids;
        }

        List<Control> controls() {
            List<Control> found = new ArrayList<>();
            collect(scene.getRoot(), found);
            return found;
        }

        private static void collect(Node node, List<Control> found) {
            if (node instanceof Control control) {
                found.add(control);
            }
            if (node instanceof Parent parent) {
                for (Node child : parent.getChildrenUnmodifiable()) {
                    collect(child, found);
                }
            }
        }
    }
}
