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

package org.cometgui.install.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.registry.ArchiveMember;
import org.cometgui.install.registry.ArtefactCompanion;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole pipeline over the bytes upstream really publishes, from the shipped manifest.
 *
 * <p>Everything here is real except the transport: the record comes from {@code
 * manifests/tools.json}, the archive and the Debian payload are the artefacts fetched by pinned URL
 * and verified by SHA-256 into the gitignored mirror, and the binary that comes out is
 * <strong>executed</strong>. That is what closes {@code R-PLAT-05} -- <em>"downloaded executables
 * shall be made executable ... since archive-preserved modes cannot be relied on"</em> -- with a
 * real binary rather than a shell script.
 *
 * <p>It is also where {@code ArchiveMember.hashes()} stops being a value nobody compares: the two
 * schemas the manifest records inside a 1.8 MB {@code .deb} are hashed after extraction and
 * compared with the digests the manifest pins for them.
 *
 * <p><strong>This test fails rather than skips when the mirror is absent.</strong> An installer
 * suite that quietly stops reading the real artefacts stops proving anything, and the message says
 * how to refill it.
 */
class RealArtefactInstallTest {

    /** Where the gitignored mirror lives, relative to the repository root. */
    private static final String MIRROR = "scratch/phase05/artefacts";

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    @TempDir private Path temporary;

    @Test
    @DisplayName("the real Percolator 3.07.1 artefact installs, verifies and runs on this host")
    void theRealPercolatorArtefactInstallsAndRuns() throws IOException, InterruptedException {
        ArtefactRecord record = shippedRecord();
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        harness.fetcher()
                .serve(
                        record.url(),
                        Files.readAllBytes(
                                artefact("rel-3-07-01__percolator-noxml-ubuntu-portable.zip")));
        ArtefactCompanion companion = record.companions().get(0);
        harness.fetcher()
                .serve(
                        companion.url(),
                        Files.readAllBytes(
                                artefact("rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb")));

        Installation installation = harness.install(record);

        Path binary = installation.executable();
        assertEquals(
                record.member().orElseThrow().hashes().sha256(),
                CacheFixtures.sha256Of(binary),
                "the installed binary is the member the manifest pinned, byte for byte");
        assertEquals(
                record.member().orElseThrow().sizeBytes(),
                Files.size(binary),
                "and the length the manifest pinned");
        for (ArchiveMember member : companion.members()) {
            Path schema = installation.directory().resolve(member.installedPath());
            assertEquals(
                    member.hashes().sha256(),
                    CacheFixtures.sha256Of(schema),
                    () ->
                            "the companion member \""
                                    + member.path()
                                    + "\" must come out of the .deb payload with the digest the"
                                    + " manifest records for it");
        }
        assertTrue(
                Files.isExecutable(binary),
                () -> binary + " must carry the R-PLAT-05 executable bit");
        assertTrue(harness.verify(record).installed(), "and the entry verifies by R-TOOL-04");

        ChildProcesses.Result run =
                ChildProcesses.run(
                        List.of(binary.toString(), "--help"), temporary, Duration.ofSeconds(60));

        assertEquals(0, run.exitCode(), run::describe);
        assertTrue(
                String.join("\n", run.standardError()).contains("--xmloutput"),
                () ->
                        "the installed binary was launched and printed its own help, on stderr as"
                                + " this project measured it doing: "
                                + run.describe());
    }

    @Test
    @DisplayName("corrupting the installed binary afterwards makes the entry not installed")
    void corruptingTheInstalledBinaryInvalidatesTheEntry()
            throws IOException, InterruptedException {
        ArtefactRecord record = shippedRecord();
        InstallHarness harness = InstallHarness.at(temporary.resolve("cache"));
        harness.fetcher()
                .serve(
                        record.url(),
                        Files.readAllBytes(
                                artefact("rel-3-07-01__percolator-noxml-ubuntu-portable.zip")));
        harness.fetcher()
                .serve(
                        record.companions().get(0).url(),
                        Files.readAllBytes(
                                artefact("rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb")));
        Installation installation = harness.install(record);
        Path binary = installation.executable();

        byte[] bytes = Files.readAllBytes(binary);
        bytes[bytes.length / 2] ^= 0x5a;
        Files.write(binary, bytes);

        InstallationCheck check = harness.verify(record);
        assertEquals(
                InstallationState.CHECKSUM_MISMATCH,
                check.state(),
                "R-TOOL-04: a marker whose recorded checksum no longer matches the file makes the"
                        + " entry not installed, even though the marker is still there");
        assertEquals(
                bytes.length,
                Files.size(binary),
                "and the swap kept the length, so only the digest could have caught it");
        assertTrue(
                check.detail().contains(CacheFixtures.INSTALLED_BINARY_OF_PERCOLATOR),
                check::detail);
        assertTrue(check.marker().isPresent(), "the marker is still present and still readable");
    }

    private static ArtefactRecord shippedRecord() throws IOException {
        ArtefactManifest manifest = ArtefactManifestReader.readFromClasspath();
        return manifest.artefacts().stream()
                .filter(record -> record.tool() == ToolName.PERCOLATOR)
                .filter(record -> record.version().equals(ToolVersion.parse("3.07.1")))
                .filter(record -> record.platform().equals(LINUX))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "manifests/tools.json no longer holds percolator 3.07.1 for"
                                                + " linux-x86-64; this test installs the"
                                                + " artefact the"
                                                + " product really ships"));
    }

    private static Path artefact(String fileName) {
        Path file = repositoryRoot().resolve(MIRROR).resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError(
                    "the real artefact \""
                            + fileName
                            + "\" is not in the mirror at "
                            + file
                            + ". The mirror is gitignored and holds the bytes upstream publishes;"
                            + " refill it by fetching each artefact from the URL in"
                            + " manifests/tools.json and checking its SHA-256 before use. This test"
                            + " fails rather than skips, because an installer suite that stops"
                            + " reading the real artefacts stops proving anything.");
        }
        return file;
    }

    private static Path repositoryRoot() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("manifests"))) {
            cursor = cursor.getParent();
        }
        if (cursor == null) {
            throw new AssertionError(
                    "no repository root above "
                            + Path.of("").toAbsolutePath()
                            + " holds a manifests directory");
        }
        return cursor;
    }
}
