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

package org.cometgui.install.probe;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.testing.Nulls;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The route a tool with no version banner takes through {@link StagedToolProbe}.
 *
 * <p>The two Java artefacts -- PDV and the Limelight converter -- are not executable files, so the
 * loadability stage cannot be handed one; {@link JavaArtefactIdentity} is the seam that answers for
 * them instead, and {@code org.cometgui.tools} implements it. What is graded here is the routing
 * and the refusals; that the seam's real implementations answer correctly against the real
 * artefacts is {@code org.cometgui.tools}'s own suite, and that the two fit together is proved end
 * to end where both modules are visible.
 *
 * <p><strong>The six-argument constructor is unchanged and must stay unchanged.</strong> A probe
 * built without an identity still fails by name for those tools, with the same sentence, which is
 * what {@code StagedToolProbeTest.aToolWithNoBannerFailsByName} pins.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "shares the shipped-manifest fixtures with the rest of this package, which are"
                        + " read against the Linux host")
class JavaArtefactIdentityTest {

    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    private static final Set<ToolCapability> NO_CAPABILITIES = Set.of();

    /** Records what it was asked, and answers with a fixed version. */
    private static final class RecordingIdentity implements JavaArtefactIdentity {

        private final List<String> calls = new ArrayList<>();
        private final ToolVersion answer;

        RecordingIdentity(String answer) {
            this.answer = ToolVersion.parse(answer);
        }

        @Override
        public ToolVersion identify(ToolName tool, HostPlatform platform, Path artefact) {
            calls.add(tool.id() + " " + platform.id() + " " + artefact);
            return answer;
        }
    }

    private static Path directoryOf(Path file) {
        return Objects.requireNonNull(file.getParent(), "a staged artefact has a directory");
    }

    private static ArtefactRecord pdv() throws IOException {
        return ProbeRecords.shipped()
                .select(ProbeRecords.LINUX_X86_64, ToolName.PDV)
                .get(0)
                .artefact();
    }

    private static ArtefactRecord converter() throws IOException {
        return ProbeRecords.shipped()
                .select(ProbeRecords.LINUX_X86_64, ToolName.LIMELIGHT_CONVERTER)
                .get(0)
                .artefact();
    }

    private static StagedToolProbe probeWith(JavaArtefactIdentity identity, CapabilityProber caps)
            throws IOException {
        return new StagedToolProbe(
                loadability(new ProcessService(Clock.systemUTC())),
                DEBIAN_12,
                VersionBanner.observedOnThisProject(),
                caps,
                new ManifestAlternatives(
                                ProbeRecords.shipped(), ProbeRecords.LINUX_X86_64, DEBIAN_12)
                        ::forArtefact,
                Map.of(),
                identity);
    }

    private static LoadabilityProbe loadability(ProcessRunner processes) {
        return new LoadabilityProbe(
                processes,
                new LoaderOutputClassifier(ProbeRecords.LINUX_X86_64, DEBIAN_12),
                ProbeRecords.LINUX_X86_64,
                Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("a JAR is identified through the seam, and the capability stage gets that version")
    void aJarIsIdentifiedThroughTheSeam(@TempDir Path staged) throws IOException {
        ArtefactRecord record = pdv();
        Path jar = staged.resolve(record.executablePath());
        Files.createDirectories(directoryOf(jar));
        Files.writeString(jar, "PK");
        RecordingIdentity identity = new RecordingIdentity("2.7.0");
        List<String> capabilityCalls = new ArrayList<>();
        CapabilityProber caps =
                (tool, version, platform, executable) -> {
                    capabilityCalls.add(
                            tool.id()
                                    + " "
                                    + version.text()
                                    + " "
                                    + platform.id()
                                    + " "
                                    + executable);
                    return NO_CAPABILITIES;
                };

        Set<ToolCapability> capabilities = probeWith(identity, caps).probe(record, staged);

        assertAll(
                () -> assertEquals(NO_CAPABILITIES, capabilities),
                () -> assertEquals(List.of("pdv linux-x86-64 " + jar), identity.calls),
                () -> assertEquals(List.of("pdv 2.7.0 linux-x86-64 " + jar), capabilityCalls));
    }

    @Test
    @DisplayName("no process is started for a JAR: it is not an executable file")
    void noProcessIsStartedForAJar(@TempDir Path staged) throws IOException {
        ArtefactRecord record = converter();
        Path jar = staged.resolve(record.executablePath());
        Files.createDirectories(directoryOf(jar.toAbsolutePath()));
        Files.writeString(jar, "PK");
        ProcessRunner neverEntered =
                (command, listener) -> {
                    throw new AssertionError(
                            "a .jar is not an executable file; asking the operating system to run"
                                    + " one is what this route exists to avoid, but it was asked:"
                                    + " "
                                    + command.argv());
                };
        StagedToolProbe probe =
                new StagedToolProbe(
                        loadability(neverEntered),
                        DEBIAN_12,
                        VersionBanner.observedOnThisProject(),
                        (tool, version, platform, executable) -> NO_CAPABILITIES,
                        new ManifestAlternatives(
                                        ProbeRecords.shipped(),
                                        ProbeRecords.LINUX_X86_64,
                                        DEBIAN_12)
                                ::forArtefact,
                        Map.of(),
                        new RecordingIdentity("2.8.1"));

        assertEquals(NO_CAPABILITIES, probe.probe(record, staged));
    }

    @Test
    @DisplayName("the offered-set question answers YES for a JAR that identified itself")
    void loadabilityOfAnIdentifiedJar(@TempDir Path staged) throws IOException {
        ArtefactRecord record = pdv();
        Path jar = staged.resolve(record.executablePath());
        Files.createDirectories(directoryOf(jar));
        Files.writeString(jar, "PK");

        assertEquals(
                Optional.empty(),
                probeWith(
                                new RecordingIdentity("2.7.0"),
                                (tool, version, platform, executable) -> NO_CAPABILITIES)
                        .loadabilityOf(record, staged),
                "for a JAR the identity IS the answer to \"does it start\": there is no separate"
                        + " question to ask");
    }

    @Test
    @DisplayName("a JAR that cannot identify itself propagates, and is not silently offered")
    void aJarThatCannotIdentifyItself(@TempDir Path staged) throws IOException {
        ArtefactRecord record = pdv();
        JavaArtefactIdentity broken =
                (tool, platform, artefact) -> {
                    throw new IOException(artefact + " is not PDV");
                };
        StagedToolProbe probe =
                probeWith(broken, (tool, version, platform, executable) -> NO_CAPABILITIES);
        Path jar = staged.resolve(record.executablePath());

        assertAll(
                () ->
                        assertEquals(
                                jar + " is not PDV",
                                assertThrows(IOException.class, () -> probe.probe(record, staged))
                                        .getMessage()),
                () ->
                        assertEquals(
                                jar + " is not PDV",
                                assertThrows(
                                                IOException.class,
                                                () -> probe.loadabilityOf(record, staged))
                                        .getMessage()));
    }

    @Test
    @DisplayName("a tool WITH a banner never reaches the seam, however one is configured")
    void aNativeToolNeverReachesTheSeam(@TempDir Path staged) throws IOException {
        ArtefactRecord record = ProbeRecords.shippedPercolator("3.07.1");
        Path executable =
                StagedBinaries.stage(
                        StagedBinaries.percolator3071(), staged, record.executablePath());
        RecordingIdentity identity = new RecordingIdentity("9.99.9");

        Set<ToolCapability> capabilities =
                probeWith(
                                identity,
                                (tool, version, platform, file) ->
                                        Set.of(ToolCapability.XML_OUTPUT))
                        .probe(record, staged);

        assertAll(
                () -> assertEquals(Set.of(ToolCapability.XML_OUTPUT), capabilities),
                () ->
                        assertEquals(
                                List.of(),
                                identity.calls,
                                "Percolator prints a banner, so it goes through the loadability and"
                                        + " identity stages that run it -- "
                                        + executable),
                () ->
                        assertTrue(
                                probeWith(
                                                identity,
                                                (tool, version, platform, file) -> NO_CAPABILITIES)
                                        .loadabilityOf(record, staged)
                                        .isEmpty()));
    }

    @Test
    @DisplayName(
            "the identity is required when one is asked for, and the old constructor is intact")
    void theSeamIsRequired(@TempDir Path staged) throws IOException {
        ArtefactRecord record = pdv();

        assertAll(
                () ->
                        assertEquals(
                                "javaArtefacts",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        probeWith(
                                                                Nulls.of(
                                                                        JavaArtefactIdentity.class),
                                                                (tool,
                                                                        version,
                                                                        platform,
                                                                        executable) ->
                                                                        NO_CAPABILITIES))
                                        .getMessage()),
                () ->
                        assertTrue(
                                assertThrows(
                                                IOException.class,
                                                () -> withoutSeam().loadabilityOf(record, staged))
                                        .getMessage()
                                        .contains(
                                                "cannot be probed: no version banner is configured"
                                                        + " for pdv"),
                                "a probe built the old way still fails by name, and it does so for"
                                        + " the offered-set question as well as for the probe"));
    }

    private static StagedToolProbe withoutSeam() throws IOException {
        return new StagedToolProbe(
                loadability(new ProcessService(Clock.systemUTC())),
                DEBIAN_12,
                VersionBanner.observedOnThisProject(),
                (tool, version, platform, executable) -> NO_CAPABILITIES,
                new ManifestAlternatives(
                                ProbeRecords.shipped(), ProbeRecords.LINUX_X86_64, DEBIAN_12)
                        ::forArtefact,
                Map.of());
    }
}
