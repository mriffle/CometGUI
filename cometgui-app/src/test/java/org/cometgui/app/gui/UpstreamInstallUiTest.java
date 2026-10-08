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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import javafx.stage.Stage;
import org.cometgui.app.config.ToolManagerWiring;
import org.cometgui.app.testing.ArtefactMirror;
import org.cometgui.app.testing.RecordingProcessRunner;
import org.cometgui.app.testing.ShownToolManager;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.ui.controls.UiIds;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;

/**
 * Gate item 1 against the real world: the same four tools, pressed in the same interface, with the
 * bytes fetched from GitHub over TLS -- and Comet twice, the default 2026.03.0 and the 2026.02.2 it
 * superseded and still offers (D-010).
 *
 * <h2>How to run it</h2>
 *
 * <pre>{@code
 * mvn -o -pl cometgui-domain,cometgui-provenance,cometgui-process,cometgui-tools,\
 * cometgui-install,cometgui-params-comet,cometgui-params-percolator,cometgui-results,\
 * cometgui-workflow,cometgui-ui,cometgui-app -Dcometgui.install.upstream=true \
 *     -Dtest=UpstreamInstallUiTest -Dsurefire.failIfNoSpecifiedTests=false test
 * }</pre>
 *
 * <p>It moves <strong>123 059 863 bytes</strong> from six URLs on three GitHub repositories'
 * release assets. Measured on this project's build host with five URLs, the whole class ran in
 * <strong>11.4 s</strong>; the cost is the connection rather than the work, so a slower one takes
 * proportionally longer and nothing here assumes otherwise.
 *
 * <h2>The network test does not run in the ordinary build</h2>
 *
 * <p>{@code UpstreamArtefactTest} established the rule this follows and the reason: a suite that
 * goes red because a release host is having a bad afternoon teaches people to ignore it. The opt-in
 * half is therefore skipped <em>with a stated reason</em>, which surefire records, rather than
 * passing while doing nothing -- and the other half of this class <strong>always runs</strong>, so
 * the gate is not vacuous when the flag is absent: it pins the six URLs, their sizes and the scheme
 * against the shipped manifest, and goes red if the manifest moves under a stale copy.
 *
 * <h2>What it adds to {@code ToolManagerInstallUiTest}</h2>
 *
 * <p>Three things the loopback server cannot exercise: TLS, GitHub's redirect from the release URL
 * to a signed asset host on a different origin, and the artefact still <em>being there</em>. A
 * vanished or re-tagged upstream artefact is an availability failure naming the URL -- {@code
 * D-008} makes that a product requirement, because the project holds no copy to fall back on -- and
 * this is the only test in the repository that can observe it through the interface.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "the URLs, the installed paths and the probe here are the Linux ones; the same"
                        + " four tools on another platform are different rows and belong to the"
                        + " runner that has them.")
class UpstreamInstallUiTest {

    /** The property that opts in to reaching the real network. */
    private static final String OPT_IN = "cometgui.install.upstream";

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    private static final String COMET_ROW = "comet-2026_03_0-1";
    private static final String OLDER_COMET_ROW = "comet-2026_02_2-1";
    private static final String PERCOLATOR_ROW = "percolator-3_07_1-1";
    private static final String PDV_ROW = "pdv-2_7_0-1";
    private static final String CONVERTER_ROW = "limelight-converter-2_8_1-1";

    /** The four tools, with both Comet releases the manifest offers on this host. */
    private static final List<String> THE_FOUR =
            List.of(COMET_ROW, OLDER_COMET_ROW, PERCOLATOR_ROW, PDV_ROW, CONVERTER_ROW);

    /**
     * Every URL this test fetches and how large upstream says it is, typed out here.
     *
     * <p>Six entries for five rows: Percolator's row fetches its portable zip <em>and</em> the
     * Debian package the two XSD companions are taken out of.
     */
    private static final Map<String, Long> UPSTREAM_TRANSFER = upstreamTransfer();

    /** The sum of the six, typed out rather than added up by the code under test. */
    private static final long UPSTREAM_TOTAL_BYTES = 123_059_863L;

    @TempDir private Path temporary;

    private static Stage window;

    @BeforeAll
    static void startTheToolkit() throws TimeoutException {
        window = FxToolkit.registerPrimaryStage();
    }

    private static Map<String, Long> upstreamTransfer() {
        Map<String, Long> transfer = new LinkedHashMap<>();
        transfer.put(
                "https://github.com/UWPR/Comet/releases/download/v2026.03.0/comet.linux.exe",
                7_077_008L);
        transfer.put(
                "https://github.com/UWPR/Comet/releases/download/v2026.02.2/comet.linux.exe",
                7_014_400L);
        transfer.put(
                "https://github.com/percolator/percolator/releases/download/rel-3-07-01/"
                        + "percolator-noxml-ubuntu-portable.zip",
                946_303L);
        transfer.put(
                "https://github.com/percolator/percolator/releases/download/rel-3-07-01/"
                        + "percolator-noxml-v3-07-linux-amd64.deb",
                1_852_660L);
        transfer.put(
                "https://github.com/wenbostar/PDV/releases/download/v2.7.0/PDV-2.7.0.zip",
                103_407_417L);
        transfer.put(
                "https://github.com/yeastrc/limelight-import-comet-percolator/releases/download/"
                        + "v2.8.1/cometPercolator2LimelightXML.jar",
                2_762_075L);
        return Map.copyOf(transfer);
    }

    @Test
    @DisplayName("the opt-in install targets the six transfers the shipped manifest really pins")
    void theOptInInstallTargetsTheShippedManifest() throws IOException {
        Map<String, Long> shipped = new LinkedHashMap<>();
        ArtefactRecord older = ArtefactMirror.record(ToolName.COMET, "2026.02.2", LINUX);
        shipped.put(older.url().toString(), older.sizeBytes());
        for (ToolName tool :
                List.of(
                        ToolName.COMET,
                        ToolName.PERCOLATOR,
                        ToolName.PDV,
                        ToolName.LIMELIGHT_CONVERTER)) {
            ArtefactRecord record = recordFor(tool);
            shipped.put(record.url().toString(), record.sizeBytes());
            record.companions()
                    .forEach(
                            companion ->
                                    shipped.put(companion.url().toString(), companion.sizeBytes()));
        }
        long sum = 0L;
        for (long size : UPSTREAM_TRANSFER.values()) {
            sum += size;
        }
        long typedSum = sum;
        assertAll(
                () ->
                        assertEquals(
                                UPSTREAM_TRANSFER,
                                shipped,
                                "the URLs and sizes typed into this class are the ones"
                                        + " manifests/tools.json ships for this host; if the"
                                        + " manifest moves, this half goes red rather than the"
                                        + " opt-in half quietly fetching a stale list"),
                () ->
                        assertEquals(
                                UPSTREAM_TOTAL_BYTES,
                                typedSum,
                                "and the cost quoted in this class's documentation is the sum of"
                                        + " them"),
                () ->
                        assertEquals(
                                List.of(),
                                UPSTREAM_TRANSFER.keySet().stream()
                                        .filter(url -> !URI.create(url).getScheme().equals("https"))
                                        .toList(),
                                "R-SEC-02: every managed download is https, so the opt-in run"
                                        + " exercises TLS and the release redirect rather than the"
                                        + " loopback carve-out the other tests use"));
    }

    @Test
    @EnabledIfSystemProperty(
            named = OPT_IN,
            matches = "true",
            disabledReason =
                    "reaches github.com and moves 123 059 863 bytes; run with"
                            + " -Dcometgui.install.upstream=true. The ordinary build must not"
                            + " depend on upstream being reachable.")
    @DisplayName(
            "the four managed tools install from their real upstream URLs, through the"
                    + " Tool Manager")
    void theFourManagedToolsInstallFromUpstream() throws IOException, InterruptedException {
        Path cacheRoot = temporary.resolve("cometgui-data");
        RecordingProcessRunner processes = RecordingProcessRunner.inFrontOf(Clock.systemUTC());
        ToolManager manager =
                ToolManagerWiring.toolManager(
                        LINUX,
                        DEBIAN_12,
                        cacheRoot,
                        processes,
                        Clock.systemUTC(),
                        ToolManagerWiring.installThreads());
        long startedAt = System.nanoTime();
        try (ShownToolManager ui = ShownToolManager.showing(manager, window)) {
            for (String row : THE_FOUR) {
                ui.pressInstall(row);
                InstallProgress terminal = ui.awaitTerminal(row);
                assertEquals(
                        InstallPhase.DONE,
                        terminal.phase(),
                        () ->
                                row
                                        + " did not install from upstream: "
                                        + ui.phases(row)
                                        + ". A vanished or re-tagged artefact is an upstream"
                                        + " availability failure naming the URL, not a corrupt"
                                        + " download (D-008).");
            }
            long elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000L;

            assertAll(
                    () ->
                            assertEquals(
                                    "CometGUI-managed: installed",
                                    ui.textOf(UiIds.toolRowState(COMET_ROW))),
                    () ->
                            assertEquals(
                                    "Capabilities: PEPXML_OUTPUT (observed-by-execution),"
                                            + " PIN_OUTPUT (observed-by-execution),"
                                            + " COMPLETE_PARAMS_QUERY (observed-by-execution)",
                                    ui.textOf(UiIds.toolRowCapabilities(COMET_ROW))),
                    () ->
                            assertEquals(
                                    "Capabilities: PEPXML_OUTPUT (observed-by-execution),"
                                            + " PIN_OUTPUT (observed-by-execution),"
                                            + " COMPLETE_PARAMS_QUERY (observed-by-execution)",
                                    ui.textOf(UiIds.toolRowCapabilities(OLDER_COMET_ROW))),
                    () ->
                            assertTrue(
                                    Files.isExecutable(
                                            cacheRoot.resolve(
                                                    "tools/comet/2026.3/linux-x86-64/bin/comet")),
                                    "the default Comet is installed and executable"),
                    () ->
                            assertTrue(
                                    Files.isExecutable(
                                            cacheRoot.resolve(
                                                    "tools/comet/2026.2.2/linux-x86-64/bin/comet")),
                                    "and so is the release it superseded"),
                    () ->
                            assertEquals(
                                    "Capabilities: XML_OUTPUT (observed-by-execution),"
                                            + " XML_DECOY_OUTPUT (observed-by-execution),"
                                            + " PSM_TSV_OUTPUT (observed-by-execution),"
                                            + " PEPTIDE_TSV_OUTPUT (observed-by-execution),"
                                            + " DECOY_OUTPUT (observed-by-execution),"
                                            + " WEIGHTS_OUTPUT (observed-by-execution),"
                                            + " THREAD_OPTION (observed-by-execution),"
                                            + " SEED_OPTION (observed-by-execution),"
                                            + " TEST_FDR_OPTION (observed-by-execution),"
                                            + " TRAIN_FDR_OPTION (observed-by-execution),"
                                            + " MAX_ITERATIONS_OPTION (observed-by-execution),"
                                            + " NO_ANALYTICS_OPTION (observed-by-execution)",
                                    ui.textOf(UiIds.toolRowCapabilities(PERCOLATOR_ROW)),
                                    "the binary GitHub is serving today is still the XML-capable"
                                            + " one, established by running it"),
                    () ->
                            assertTrue(
                                    Files.isExecutable(
                                            cacheRoot.resolve(
                                                    "tools/percolator/3.7.1/linux-x86-64/"
                                                            + "bin/percolator")),
                                    "and it is installed and executable"),
                    () ->
                            assertTrue(
                                    Files.isRegularFile(
                                            cacheRoot.resolve(
                                                    "tools/pdv/2.7/linux-x86-64/PDV-2.7.0/"
                                                            + "PDV-2.7.0.jar")),
                                    "PDV's jar came out of the 103 407 417-byte archive"),
                    () ->
                            assertTrue(
                                    Files.isRegularFile(
                                            cacheRoot.resolve(
                                                    "tools/limelight-converter/2.8.1/"
                                                            + "linux-x86-64/"
                                                            + "cometPercolator2LimelightXML.jar")),
                                    "and the converter is where the manifest says"),
                    () ->
                            assertTrue(
                                    elapsedSeconds >= 0,
                                    "fetched "
                                            + UPSTREAM_TOTAL_BYTES
                                            + " bytes from six real URLs, installed and probed"
                                            + " five builds, in "
                                            + elapsedSeconds
                                            + " s"));
        }
    }

    private static ArtefactRecord recordFor(ToolName tool) throws IOException {
        return switch (tool) {
            case COMET -> ArtefactMirror.record(tool, "2026.03.0", LINUX);
            case PERCOLATOR -> ArtefactMirror.record(tool, "3.07.1", LINUX);
            case PDV -> ArtefactMirror.record(tool, "2.7.0", LINUX);
            case LIMELIGHT_CONVERTER -> ArtefactMirror.record(tool, "2.8.1", LINUX);
        };
    }
}
