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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.cometgui.app.testing.ArtefactMirror;
import org.cometgui.app.testing.LoopbackArtefactServer;
import org.cometgui.app.testing.TestPercolators;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.archive.ArtefactExtractor;
import org.cometgui.install.cache.ArtefactInstaller;
import org.cometgui.install.cache.InstallationMarker;
import org.cometgui.install.cache.PlatformFixups;
import org.cometgui.install.cache.ToolCache;
import org.cometgui.install.cache.ToolProbe;
import org.cometgui.install.manager.ManagedToolManager;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.verify.ArtefactVerifier;
import org.cometgui.install.verify.VerifiedDownloader;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.params.RunNotStartedException;
import org.cometgui.ui.viewmodel.params.RunObserver;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepTransition;
import org.cometgui.workflow.provenance.ProvenanceReader;
import org.cometgui.workflow.provenance.ProvenanceView;
import org.cometgui.workflow.provenance.WarningSource;
import org.cometgui.workflow.state.Plan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code R-TOOL-07} end to end, through the real install path: the pinned Comet 2026.03.0 is
 * installed by the product's own installer from the artefact mirror into an empty cache, step 6
 * runs the real three-stage probe, and the completion marker is written as {@link
 * ManagedToolManager}'s installer writes it -- except that the probe's answer leaves out {@code
 * COMPLETE_PARAMS_QUERY}, standing in for a build whose {@code -q} does not work. The Tool
 * Manager's offer is then read back from that marker, the Run section's port selects it, a real run
 * records it, and its {@code provenance.json} -- read back with {@link ManifestReader} -- and the
 * Provenance section's read model must both carry the manifest/probe discrepancy, in the words
 * {@code ToolWarnings} pins.
 *
 * <p>The search itself is the smallest that reaches Comet: a one-record FASTA and a spectrum file
 * that is not mzML, so Comet runs, both of its records are written, and the run then fails at
 * output validation -- which is beside the point: the tool record is written for every invocation.
 *
 * <p>Files read outside this module: {@code scratch/phase05/artefacts/v2026.03.0__comet.linux.exe},
 * which {@link ArtefactMirror} fails rather than skips without.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason = "the pinned Comet installed and run here is the linux-x86-64 build")
class ManifestProbeDiscrepancyRunTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-test", "unknown", Instant.parse("2026-10-13T00:00:00Z"));

    private static final String MIRRORED = "v2026.03.0__comet.linux.exe";

    /** The discrepancy, word for word as {@code ToolWarnings} writes it. */
    private static final String DISCREPANCY =
            "Comet 2026.03.0: the artefact manifest claims COMPLETE_PARAMS_QUERY"
                    + " (observed-by-execution evidence), but the probe run when this build was"
                    + " installed did not find it, so CometGUI treats the build as lacking"
                    + " COMPLETE_PARAMS_QUERY (R-TOOL-07: where manifest and probe disagree, the"
                    + " probe wins).";

    @TempDir private Path scratch;

    /** The real probe, with one capability withheld from its answer, and what it answered. */
    private static final class Withholding implements ToolProbe {

        private final ToolProbe real;
        private final ToolCapability withheld;
        private final List<Set<ToolCapability>> answers =
                Collections.synchronizedList(new ArrayList<>());

        Withholding(ToolProbe real, ToolCapability withheld) {
            this.real = real;
            this.withheld = withheld;
        }

        @Override
        public Set<ToolCapability> probe(ArtefactRecord record, Path stagedDirectory)
                throws IOException {
            Set<ToolCapability> found = EnumSet.noneOf(ToolCapability.class);
            found.addAll(real.probe(record, stagedDirectory));
            answers.add(Set.copyOf(found));
            found.remove(withheld);
            return found;
        }
    }

    private static ManagedToolManager managed(
            Path cacheRoot, LoopbackArtefactServer served, ToolProbe probe) throws IOException {
        ArtefactManifest manifest = ArtefactManifestReader.readFromClasspath();
        HashService hashes = new StreamingHashService();
        ToolCache cache = new ToolCache(cacheRoot, hashes);
        ProcessService processes = new ProcessService(Clock.systemUTC());
        ArtefactInstaller installer =
                new ArtefactInstaller(
                        cache,
                        new VerifiedDownloader(served, new ArtefactVerifier(hashes))::fetch,
                        new ArtefactExtractor(),
                        new PlatformFixups(LINUX.operatingSystem(), processes),
                        probe,
                        hashes,
                        Clock.systemUTC());
        return new ManagedToolManager(
                manifest,
                LINUX,
                DEBIAN_12,
                installer,
                record -> Optional.empty(),
                Map.of(),
                ToolManagerWiring.installThreads());
    }

    private Path executable(Path root, String name) throws IOException {
        Path file = Files.createDirectories(root.resolve("bin")).resolve(name);
        Files.writeString(file, "#!/bin/sh\nexit 0\n", StandardCharsets.US_ASCII);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwx------"));
        return file;
    }

    @Test
    @DisplayName(
            "a manifest claim the install's probe did not confirm is a warning on every Comet"
                    + " record in provenance.json and a manifest/probe discrepancy in the read"
                    + " model")
    void theDiscrepancyReachesTheRecord()
            throws IOException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    RunNotStartedException {
        Path root = scratch.toRealPath();
        ArtefactRecord record = ArtefactMirror.record(ToolName.COMET, "2026.03.0", LINUX);
        try (LoopbackArtefactServer served =
                new LoopbackArtefactServer()
                        .serve(record.url(), ArtefactMirror.artefact(MIRRORED))) {
            Path cacheRoot = root.resolve("cometgui-data");
            Withholding probe =
                    new Withholding(
                            ToolManagerWiring.probe(
                                    ArtefactManifestReader.readFromClasspath(),
                                    LINUX,
                                    DEBIAN_12,
                                    new ProcessService(Clock.systemUTC())),
                            ToolCapability.COMPLETE_PARAMS_QUERY);
            ManagedToolManager manager = managed(cacheRoot, served, probe);
            CountDownLatch installed = new CountDownLatch(1);
            List<InstallPhase> phases = Collections.synchronizedList(new ArrayList<>());
            manager.install(
                    ToolName.COMET,
                    ToolVersion.parse("2026.03.0"),
                    progress -> {
                        phases.add(progress.phase());
                        if (progress.phase().isTerminal()) {
                            installed.countDown();
                        }
                    });
            assertTrue(installed.await(5, TimeUnit.MINUTES), phases::toString);
            assertEquals(InstallPhase.DONE, phases.get(phases.size() - 1), phases::toString);

            ToolOffer offer =
                    manager.offers().stream()
                            .filter(candidate -> candidate.tool() == ToolName.COMET)
                            .filter(candidate -> candidate.state() == ToolInstallState.INSTALLED)
                            .findFirst()
                            .orElseThrow();
            List<Path> markers;
            try (Stream<Path> walk = Files.walk(cacheRoot)) {
                markers =
                        walk.filter(
                                        file ->
                                                file.endsWith(InstallationMarker.FILE_NAME)
                                                        && offer.installedPath()
                                                                .orElseThrow()
                                                                .startsWith(
                                                                        file.resolveSibling("")))
                                .toList();
            }
            assertEquals(1, markers.size(), markers::toString);
            Path markerFile = markers.get(0);
            InstallationMarker marker =
                    InstallationMarker.parse(Files.readString(markerFile, StandardCharsets.UTF_8));
            assertAll(
                    "the install: the real probe found all three, the marker holds two",
                    () ->
                            assertEquals(
                                    List.of(
                                            Set.of(
                                                    ToolCapability.PEPXML_OUTPUT,
                                                    ToolCapability.PIN_OUTPUT,
                                                    ToolCapability.COMPLETE_PARAMS_QUERY)),
                                    probe.answers),
                    () ->
                            assertEquals(
                                    List.of(
                                            ToolCapability.PEPXML_OUTPUT,
                                            ToolCapability.PIN_OUTPUT),
                                    marker.capabilities()),
                    () -> assertEquals(ToolOrigin.MANAGED, offer.origin()),
                    () ->
                            assertEquals(
                                    List.of(
                                            new DeclaredCapability(
                                                    ToolCapability.COMPLETE_PARAMS_QUERY,
                                                    CapabilityEvidence.OBSERVED_BY_EXECUTION,
                                                    "run on linux-x86-64 by the Comet 2026.03.0"
                                                            + " intake on 2026-10-04: comet -q"
                                                            + " wrote 118 parameters against comet"
                                                            + " -p's 95, the same 118 names as"
                                                            + " 2026.02.2's -q; captured as the"
                                                            + " cometgui-params-comet fixture for"
                                                            + " 2026.03.0")),
                                    offer.unconfirmedClaims()));

            ProvenanceManifest recorded = run(root, manager);
            List<ToolRecord> comets =
                    recorded.tools().stream().filter(tool -> tool.name().equals("comet")).toList();
            Map<String, String> warningSettings = new TreeMap<>();
            for (Map.Entry<String, String> setting : recorded.settings().entrySet()) {
                if (setting.getKey().startsWith("comet.advisory.")
                        || setting.getKey().startsWith("comet.probe-discrepancy.")) {
                    warningSettings.put(setting.getKey(), setting.getValue());
                }
            }
            Set<WarningSource> sources =
                    EnumSet.of(
                            WarningSource.MANIFEST_PROBE_DISCREPANCY, WarningSource.TOOL_WARNING);
            ProvenanceView view =
                    new ProvenanceReader(
                                    SecretRedactor::patternsOnly,
                                    new CachingHashService(new StreamingHashService()))
                            .load(runDirectory(root))
                            .view();
            List<ProvenanceView.Warning> shown =
                    view.warnings().stream()
                            .filter(warning -> sources.contains(warning.source()))
                            .toList();
            assertAll(
                    "the run",
                    () -> assertEquals(1, comets.size(), "one spectrum file, one Comet record"),
                    () -> assertEquals(List.of(DISCREPANCY), comets.get(0).warnings()),
                    () ->
                            assertEquals(
                                    Map.of("comet.probe-discrepancy.01", DISCREPANCY),
                                    warningSettings,
                                    "and no advisory: the Linux Comet row carries none"),
                    () ->
                            assertEquals(
                                    List.of(
                                            new ProvenanceView.Warning(
                                                    WarningSource.MANIFEST_PROBE_DISCREPANCY,
                                                    "comet.probe-discrepancy.01",
                                                    DISCREPANCY)),
                                    shown));
        }
    }

    private Path runDirectory(Path root) throws IOException {
        try (var runs = Files.list(root.resolve("project").resolve("runs"))) {
            List<Path> all = runs.toList();
            assertEquals(1, all.size(), all::toString);
            return all.get(0);
        }
    }

    /** One search through the Run section's port, with the managed Comet; its record. */
    private ProvenanceManifest run(Path root, ManagedToolManager manager)
            throws IOException,
                    InterruptedException,
                    ExecutionException,
                    TimeoutException,
                    RunNotStartedException {
        ProjectSession project =
                new ProjectSession(
                        root.resolve("project"),
                        Clock.systemUTC(),
                        ApplicationServices.forThisHost().runIds());
        Path fasta =
                Files.writeString(
                        Files.createDirectories(root.resolve("inputs")).resolve("targets.fasta"),
                        ">sp|P00001|ONE\nPEPTIDEK\n",
                        StandardCharsets.US_ASCII);
        Path spectrum =
                Files.writeString(
                        root.resolve("inputs").resolve("sample.mzML"),
                        "<mzML/>\n",
                        StandardCharsets.US_ASCII);
        SessionEngine port =
                RunWiring.port(
                        ApplicationServices.forThisHost(),
                        new BoundedMessageLog(),
                        Optional.of(manager),
                        "the artefact manifest cannot be read",
                        project,
                        BUILD);
        PercolatorRequest percolator =
                TestPercolators.ready(
                        TestPercolators.installed(
                                "3.07.1",
                                ToolOrigin.MANAGED,
                                executable(root, "percolator"),
                                TestPercolators.ALL,
                                List.of()));
        CompletableFuture<RunResult> finished = new CompletableFuture<>();
        port.start(
                ParameterEditorWiring.newSession()
                        .model()
                        .withText("database_name", fasta.toString(), ValueOrigin.USER)
                        .withText("decoy_search", "1", ValueOrigin.USER),
                List.of(),
                List.of(spectrum),
                percolator,
                new RunObserver() {
                    @Override
                    public void planned(Plan plan, String description) {}

                    @Override
                    public void onTransition(StepTransition transition) {}

                    @Override
                    public void onRunFinished(RunResult result) {
                        finished.complete(result);
                    }
                });
        finished.get(5, TimeUnit.MINUTES);
        ProvenanceManifest recorded =
                ManifestReader.readFrom(
                        runDirectory(root).resolve("provenance").resolve("provenance.json"));
        project.close();
        return recorded;
    }
}
