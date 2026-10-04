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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import org.cometgui.app.config.ToolManagerWiring;
import org.cometgui.app.testing.FxToolkit;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.platform.HostBaselineOutcome;
import org.cometgui.domain.platform.HostBaselineReport;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.view.ShellView;
import org.cometgui.ui.viewmodel.ConsoleViewModel;
import org.cometgui.ui.viewmodel.HostBaselineViewModel;
import org.cometgui.ui.viewmodel.NavigationViewModel;
import org.cometgui.ui.viewmodel.SectionId;
import org.cometgui.ui.viewmodel.StageStepperViewModel;
import org.cometgui.ui.viewmodel.ToolManagerViewModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Tool Manager section, rendered headless over the <strong>real</strong> runtime.
 *
 * <p>Nothing is scripted here: the shell is the product's own {@code ShellView}, the port behind it
 * is {@code ManagedToolManager} as {@code ToolManagerWiring} composes it, the manifest is the
 * shipped one and the cache is an empty temporary directory. What the section shows is therefore
 * what a scientist on this machine would see with nothing installed yet.
 *
 * <p><strong>The expected set of builds is read out of {@code manifests/tools.json} by this test,
 * with a scan of its own.</strong> Not from {@code ArtefactManifestReader} -- the product's reader
 * is on the other side of the thing being checked, and an expectation produced by it would agree
 * with the interface however wrong both were -- and not from a list typed in here, which would stop
 * being the manifest's answer the day a version is added to it.
 */
@EnabledOnOs(OS.LINUX)
class ToolManagerSectionUiTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    /**
     * One artefact record's tool and version, as the manifest writes them.
     *
     * <p>The opening quote in {@code "version"} is what keeps this off {@code
     * minimumCometGuiVersion} and {@code schemaVersion}: neither key ends with that spelling.
     */
    private static final Pattern TOOL_AND_VERSION =
            Pattern.compile(
                    "\"tool\"\\s*:\\s*\"([^\"]+)\".*?\"version\"\\s*:\\s*\"([^\"]+)\"",
                    Pattern.DOTALL);

    @TempDir private Path temporary;

    private ToolManagerViewModel toolManager;

    private Scene scene;

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        FxToolkit.start();
    }

    @Test
    @DisplayName("the section shows a row for every release the shipped manifest knows about")
    void theSectionShowsARowForEveryReleaseTheManifestKnowsAbout()
            throws IOException, InterruptedException {
        show();

        assertAll(
                () ->
                        assertEquals(
                                releasesInTheManifestFile(),
                                new TreeSet<>(renderedBuilds()),
                                "every release in manifests/tools.json has a row, and the section"
                                        + " shows no build the manifest does not name (gate item"
                                        + " 8)"),
                () -> assertEquals(7, renderedBuilds().size()),
                () ->
                        assertEquals(
                                "7 tool builds on this host.", textOf(UiIds.TOOL_MANAGER_SUMMARY)));
    }

    @Test
    @DisplayName("percolator 3.09 is present on Linux and says it is not published here")
    void percolator309IsPresentAndSaysItIsNotPublishedHere()
            throws IOException, InterruptedException {
        show();

        assertAll(
                () ->
                        assertNotNull(
                                node(UiIds.toolRow("percolator-3_09-1")),
                                "upstream publishes no Linux artefact of 3.09 and the row must"
                                        + " still be there: R-PERC-01 forbids promising it, not"
                                        + " mentioning it"),
                () ->
                        assertEquals(
                                "CometGUI-managed: not published for this platform",
                                textOf(UiIds.toolRowState("percolator-3_09-1"))),
                () ->
                        assertEquals(
                                "Download: nothing to fetch on this host",
                                textOf(UiIds.toolRowDownload("percolator-3_09-1"))),
                () ->
                        assertEquals(
                                "Capabilities: none declared",
                                textOf(UiIds.toolRowCapabilities("percolator-3_09-1")),
                                "no capability and no advisory of a build that is not published"
                                        + " here: \"runs under Rosetta 2\" is a false statement"
                                        + " about a Linux machine"));
    }

    @Test
    @DisplayName("percolator 3.07.1 shows the whole transfer, its capabilities and its advisories")
    void percolator3071ShowsItsTransferCapabilitiesAndAdvisories()
            throws IOException, InterruptedException {
        show();

        assertAll(
                () ->
                        assertEquals(
                                "percolator 3.07.1",
                                textOf(UiIds.toolRowName("percolator-3_07_1-1")),
                                "ToolVersion.text(), not the normalised cache directory name"
                                        + " percolator/3.7.1"),
                () ->
                        assertEquals(
                                "CometGUI-managed: not installed",
                                textOf(UiIds.toolRowState("percolator-3_07_1-1"))),
                () ->
                        assertEquals(
                                "Download: 2,798,963 bytes",
                                textOf(UiIds.toolRowDownload("percolator-3_07_1-1")),
                                "946,303 for the portable zip and 1,852,660 for the .deb the"
                                        + " two XSD companions come out of: the transfer, not"
                                        + " one file"),
                () ->
                        assertEquals(
                                "Capabilities: XML_OUTPUT (observed-by-execution), XML_DECOY_OUTPUT"
                                        + " (observed-by-execution)",
                                textOf(UiIds.toolRowCapabilities("percolator-3_07_1-1"))),
                () ->
                        assertEquals(
                                "Advisories:\n"
                                        + "Percolator 3.07.1 predates 3.08's change of the default"
                                        + " PEP regressor to I-splines, so its posterior error"
                                        + " probabilities are computed the older way.\n"
                                        + "Percolator 3.07.1 predates the fix for PEP values"
                                        + " exceeding 1.0 (upstream issue #394, fixed in 3.08.1 and"
                                        + " 3.09), so a PEP above 1.0 can appear in its output.",
                                textOf(UiIds.toolRowAdvisories("percolator-3_07_1-1")),
                                "R-PERC-11 at selection time, in the manifest's own words"));
    }

    @Test
    @DisplayName(
            "an empty cache leaves every managed row offering an install and nothing installed")
    void anEmptyCacheLeavesEveryRowOfferingAnInstall() throws IOException, InterruptedException {
        show();

        assertAll(
                () ->
                        assertFalse(
                                isShowing(UiIds.toolRowPath("comet-2026_02_2-1")),
                                "nothing is installed, so no row names a location"),
                () ->
                        assertFalse(
                                isShowing(UiIds.toolRowDiagnostic("comet-2026_02_2-1")),
                                "nothing has been run, so nothing was refused by the loader"),
                () ->
                        assertFalse(
                                isShowing(UiIds.toolRowProgress("comet-2026_02_2-1")),
                                "no install has reported"),
                () ->
                        assertEquals(
                                "Download: 7,014,400 bytes",
                                textOf(UiIds.toolRowDownload("comet-2026_02_2-1"))),
                () ->
                        assertTrue(
                                Files.notExists(temporary.resolve("cache")),
                                "reading the offered list must create no cache directory"));
    }

    /** Builds the real shell over the real runtime and reads the port once. */
    private void show() throws IOException, InterruptedException {
        ToolManager manager =
                ToolManagerWiring.toolManager(
                        LINUX,
                        DEBIAN_12,
                        temporary.resolve("cache"),
                        new ProcessService(Clock.systemUTC()),
                        Clock.systemUTC(),
                        Runnable::run);
        toolManager = new ToolManagerViewModel(manager, Runnable::run);
        FxToolkit.onFxThread(
                () -> {
                    ShellView shell =
                            new ShellView(
                                    new NavigationViewModel(),
                                    new HostBaselineViewModel(
                                            new HostBaselineReport(
                                                    HostBaselineOutcome.SUPPORTED,
                                                    "64-bit host, glibc 2.36.")),
                                    new StageStepperViewModel(),
                                    new ConsoleViewModel(new BoundedMessageLog(64)),
                                    toolManager);
                    scene = new Scene(shell, 1280, 800);
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                });
        FxToolkit.onFxThread(toolManager::refresh);
        assertNotNull(
                scene.lookup("#" + UiIds.sectionPane(SectionId.TOOL_MANAGER)),
                "the Tool Manager section pane is not in the shell's scene");
    }

    /** What each row's name label reads, in the order the rows are drawn. */
    private List<String> renderedBuilds() {
        Parent rows = (Parent) node(UiIds.TOOL_MANAGER_ROWS);
        assertNotNull(rows, "the Tool Manager has no row container");
        List<String> builds = new ArrayList<>();
        for (Node row : rows.getChildrenUnmodifiable()) {
            Label name = (Label) scene.lookup("#" + row.getId() + "-name");
            assertNotNull(name, "the row " + row.getId() + " names no build");
            builds.add(name.getText());
        }
        return builds;
    }

    /**
     * Every {@code "<tool> <version>"} the manifest file names, read from the file itself.
     *
     * @return the releases, de-duplicated, in a set so that the platform rows of one release count
     *     once
     */
    private static TreeSet<String> releasesInTheManifestFile() {
        String json = manifestText();
        Matcher records = TOOL_AND_VERSION.matcher(json);
        TreeSet<String> releases = new TreeSet<>();
        int from = 0;
        while (records.find(from)) {
            releases.add(records.group(1) + " " + records.group(2));
            from = records.end(2);
        }
        assertTrue(
                releases.size() >= 4,
                () ->
                        "the scan of manifests/tools.json found only "
                                + releases
                                + "; a scan that reads nothing would agree with an empty section");
        return releases;
    }

    /** The manifest file's text, found from the module directory or from the repository root. */
    private static String manifestText() {
        Path fromRoot = Path.of("manifests", "tools.json");
        Path fromModule = Path.of("..").resolve(fromRoot);
        Path file = Files.isRegularFile(fromRoot) ? fromRoot : fromModule;
        assertTrue(
                Files.isRegularFile(file),
                () ->
                        "cannot find manifests/tools.json from "
                                + Path.of("").toAbsolutePath()
                                + "; tried "
                                + fromRoot.toAbsolutePath()
                                + " and "
                                + fromModule.toAbsolutePath());
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("cannot read " + file.toAbsolutePath(), unreadable);
        }
    }

    private Node node(String id) {
        return scene.lookup("#" + id);
    }

    private String textOf(String id) {
        Node found = node(id);
        assertNotNull(found, "no control with id " + id);
        return ((Label) found).getText();
    }

    private boolean isShowing(String id) {
        Node found = node(id);
        assertNotNull(found, "no control with id " + id);
        return found.isVisible() && found.isManaged();
    }
}
