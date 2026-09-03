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

package org.cometgui.app.tools;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.probe.CapabilityProber;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.probe.LoadabilityProbe;
import org.cometgui.install.probe.LoaderOutputClassifier;
import org.cometgui.install.probe.ManifestAlternatives;
import org.cometgui.install.probe.StagedToolProbe;
import org.cometgui.install.probe.VersionBanner;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.tools.api.JavaToolIdentities;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The two JAR tools probed <strong>end to end</strong>, through the composition that will wire
 * them.
 *
 * <p>This test lives here because it is the only place both halves are visible. {@code
 * org.cometgui.install.probe.StagedToolProbe} declares the seams and {@code org.cometgui.tools}
 * implements them, and neither module depends on the other -- which is deliberate, and is why the
 * seams are stated entirely in domain vocabulary and satisfied by a method reference from the
 * composition root. Until phase 05 unit 7 the probe refused PDV and the Limelight converter by
 * name, and unit 10 could not install two of gate item 1's four tools.
 *
 * <p>Everything below runs against the <strong>real published artefacts</strong> in the gitignored
 * mirror, staged at the paths the shipped manifest names, with the JAR digest checked against the
 * manifest's own before anything is read or run.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "the artefacts are read from this phase's Linux artefact mirror; the JARs"
                        + " themselves are platform-independent and their rows say so")
class StagedJavaToolProbeTest {

    private static final String MIRROR = "scratch/phase05/artefacts";
    private static final String PDV_ZIP = "v2.7.0__PDV-2.7.0.zip";
    private static final String CONVERTER_JAR = "v2.8.1__cometPercolator2LimelightXML.jar";

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    /** Records what the capability stage was handed, so the identity's answer is observable. */
    private static final class RecordingProber implements CapabilityProber {

        private final List<String> calls = new ArrayList<>();

        @Override
        public Set<ToolCapability> probe(
                ToolName tool, ToolVersion version, HostPlatform platform, Path executable) {
            calls.add(tool.id() + " " + version.text() + " " + platform.id());
            return Set.of();
        }
    }

    private static Path repositoryRoot() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("manifests"))) {
            cursor = cursor.getParent();
        }
        if (cursor == null) {
            throw new AssertionError("no repository root above " + Path.of("").toAbsolutePath());
        }
        return cursor;
    }

    private static Path artefact(String fileName) {
        Path file = repositoryRoot().resolve(MIRROR).resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError(
                    "the real artefact \""
                            + fileName
                            + "\" is not in the mirror at "
                            + file
                            + ". The mirror is gitignored; refill it by fetching each artefact from"
                            + " the URL in manifests/tools.json and checking its SHA-256 before"
                            + " use. This test fails rather than skips, because a probe suite that"
                            + " stops running the real artefacts stops proving anything.");
        }
        return file;
    }

    private static ArtefactRecord record(ToolName tool) throws IOException {
        ArtefactManifest manifest = ArtefactManifestReader.readFromClasspath();
        return manifest.select(LINUX, tool).get(0).artefact();
    }

    private static StagedToolProbe probe(RecordingProber capabilities) throws IOException {
        ArtefactManifest manifest = ArtefactManifestReader.readFromClasspath();
        ProcessService processes = new ProcessService(Clock.systemUTC());
        return new StagedToolProbe(
                new LoadabilityProbe(
                        processes,
                        new LoaderOutputClassifier(LINUX, DEBIAN_12),
                        LINUX,
                        Duration.ofSeconds(120)),
                DEBIAN_12,
                VersionBanner.observedOnThisProject(),
                capabilities,
                new ManifestAlternatives(manifest, LINUX, DEBIAN_12)::forArtefact,
                Map.of(),
                JavaToolIdentities.usingThisApplicationsRuntime(
                                new ToolRunner(processes, Duration.ofSeconds(120)))
                        ::identify);
    }

    @Test
    @DisplayName("PDV 2.7.0 is probed end to end from the real ZIP, at the manifest's own path")
    void pdvIsProbedEndToEnd(@TempDir Path staged) throws IOException {
        ArtefactRecord record = record(ToolName.PDV);
        Path jar = staged.resolve(record.executablePath());
        Files.createDirectories(jar.getParent());
        try (ZipFile zip = new ZipFile(artefact(PDV_ZIP).toFile())) {
            ZipEntry entry = zip.getEntry("PDV-2.7.0/PDV-2.7.0.jar");
            try (InputStream in = zip.getInputStream(entry)) {
                Files.copy(in, jar, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        RecordingProber capabilities = new RecordingProber();

        Set<ToolCapability> probed = probe(capabilities).probe(record, staged);

        assertAll(
                () -> assertEquals("PDV-2.7.0/PDV-2.7.0.jar", record.executablePath()),
                () ->
                        assertEquals(
                                List.of("pdv 2.7.0 linux-x86-64"),
                                capabilities.calls,
                                "the version the capability stage is handed is the one read out of"
                                        + " the artefact, not the one the manifest claims"),
                () ->
                        assertEquals(
                                Set.of(),
                                probed,
                                "PDV is not capability-probed: ToolCapability declares none for"
                                        + " it, and an empty set here is the honest answer"),
                () ->
                        assertEquals(
                                Optional.empty(),
                                probe(new RecordingProber()).loadabilityOf(record, staged),
                                "and it is not refused for the offered set"));
    }

    @Test
    @DisplayName("the Limelight converter 2.8.1 is probed end to end by starting the real JAR")
    void theConverterIsProbedEndToEnd(@TempDir Path staged) throws IOException {
        ArtefactRecord record = record(ToolName.LIMELIGHT_CONVERTER);
        Path jar = staged.resolve(record.executablePath());
        Files.createDirectories(jar.toAbsolutePath().getParent());
        Files.copy(artefact(CONVERTER_JAR), jar, StandardCopyOption.REPLACE_EXISTING);
        RecordingProber capabilities = new RecordingProber();

        Set<ToolCapability> probed = probe(capabilities).probe(record, staged);

        assertAll(
                () -> assertEquals("cometPercolator2LimelightXML.jar", record.executablePath()),
                () ->
                        assertEquals(
                                List.of("limelight-converter 2.8.1 linux-x86-64"),
                                capabilities.calls),
                () -> assertEquals(Set.of(), probed),
                () ->
                        assertEquals(
                                Optional.empty(),
                                probe(new RecordingProber()).loadabilityOf(record, staged)));
    }

    @Test
    @DisplayName("a JAR that is not the tool it should be is refused, and the stage never advances")
    void aJarThatIsNotTheTool(@TempDir Path staged) throws IOException {
        ArtefactRecord record = record(ToolName.PDV);
        Path jar = staged.resolve(record.executablePath());
        Files.createDirectories(jar.getParent());
        Files.copy(artefact(CONVERTER_JAR), jar, StandardCopyOption.REPLACE_EXISTING);
        RecordingProber capabilities = new RecordingProber();

        IOException refused =
                assertThrows(IOException.class, () -> probe(capabilities).probe(record, staged));

        assertAll(
                () ->
                        assertEquals(
                                jar
                                        + " is not PDV: its manifest declares Implementation-Title"
                                        + " as \"\" and PDV's declares \"PDV\"",
                                refused.getMessage()),
                () ->
                        assertEquals(
                                List.of(),
                                capabilities.calls,
                                "R-TOOL-06's order is the guarantee: a stage is only reached when"
                                        + " every earlier one passed"));
    }

    @Test
    @DisplayName("the JAR the probe read is the byte sequence the manifest pins")
    void theArtefactsArePinned() throws IOException {
        ArtefactRecord converter = record(ToolName.LIMELIGHT_CONVERTER);
        ArtefactRecord pdv = record(ToolName.PDV);

        assertAll(
                () ->
                        assertEquals(
                                converter.hashes().sha256(),
                                sha256(artefact(CONVERTER_JAR)),
                                "the converter in the mirror is not the artefact the manifest"
                                        + " pins"),
                () ->
                        assertEquals(
                                pdv.hashes().sha256(),
                                sha256(artefact(PDV_ZIP)),
                                "the PDV archive in the mirror is not the one the manifest pins"),
                () ->
                        assertTrue(
                                converter
                                        .url()
                                        .toString()
                                        .endsWith("cometPercolator2LimelightXML.jar"),
                                converter.url().toString()));
    }

    private static String sha256(Path file) throws IOException {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            try (InputStream in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError("every Java runtime provides SHA-256", impossible);
        }
    }
}
