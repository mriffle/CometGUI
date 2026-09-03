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

package org.cometgui.tools.percolator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.testing.Nulls;
import org.cometgui.tools.testing.ScriptedRunner;
import org.cometgui.tools.testing.UpstreamArtefacts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code R-TOOL-08}: registering a Percolator binary the user already has.
 *
 * <p>The accepting case runs the <strong>real</strong> 3.07.1 portable binary and requires the
 * recorded SHA-256 to be the one the artefact manifest pins for that member, hand-typed here. Every
 * refusal is a different sentence and each is pinned whole, because "there is no file", "that is
 * not Percolator", "that is Percolator 3.04" and "that could not be exercised" are four different
 * things for a scientist to do something about.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "the accepting case runs the real Linux Percolator binary from this phase's"
                        + " artefact mirror")
class LocalPercolatorRegistrationTest {

    private static final String ZIP_3071 = "rel-3-07-01__percolator-noxml-ubuntu-portable.zip";

    /** The manifest's {@code memberSha256} for the 3.07.1 Linux portable zip, hand-typed. */
    private static final String SHA256_3071 =
            "1ba38acf09520cc89d5ed907ed0382c4d23876a7e20ec3e91cbbaa2ed431237c";

    /** The manifest's {@code memberMd5} for the same member, hand-typed. */
    private static final String MD5_3071 = "0b77b68fd859639d7421f1c5e006ade5";

    private static final HostPlatform HOST =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final String BANNER_3071 =
            "Percolator version 3.07.1, Build Date Jun 20 2024 13:20:18";

    /**
     * The one hashing service this module can reach.
     *
     * <p>{@code org.cometgui.provenance.hashing.StreamingHashService} is the product's, and {@code
     * cometgui-tools} does not depend on {@code cometgui-provenance}; the composition root supplies
     * it through the {@link HashService} port. What matters for this test is that the digest it
     * produces is compared against a value hand-typed from the shipped manifest, so the check
     * cannot pass by both sides being computed the same way.
     */
    private static HashService hashes() {
        return file -> new FileHashes(digest(file, "MD5"), digest(file, "SHA-256"));
    }

    private static String digest(Path file, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[65536];
            try (InputStream in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static Path realBinary(Path directory) throws IOException {
        return UpstreamArtefacts.executableMember(
                ZIP_3071, "percolator", directory.resolve("bin").resolve("percolator"));
    }

    private static LocalPercolatorRegistration realRegistrar() {
        ToolRunner runner =
                new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofSeconds(120));
        return new LocalPercolatorRegistration(
                runner, new PercolatorCapabilityProbe(runner), hashes(), HOST);
    }

    private static LocalPercolatorRegistration scriptedRegistrar(ScriptedRunner runner) {
        ToolRunner tool = new ToolRunner(runner, Duration.ofSeconds(5));
        return new LocalPercolatorRegistration(
                tool, new PercolatorCapabilityProbe(tool), hashes(), HOST);
    }

    @Test
    @DisplayName(
            "the real 3.07.1 binary registers, with the manifest's checksums and its probed set")
    void theRealBinaryRegisters(@TempDir Path directory)
            throws IOException, ToolRegistrationException {
        Path binary = realBinary(directory);

        RegisteredLocalBinary registered = realRegistrar().register(binary);

        ToolOffer offer = registered.offer();
        assertAll(
                () -> assertEquals(SHA256_3071, registered.checksums().sha256()),
                () -> assertEquals(MD5_3071, registered.checksums().md5()),
                () -> assertEquals(binary, registered.binary()),
                () -> assertEquals(ToolName.PERCOLATOR, offer.tool()),
                () -> assertEquals(ToolVersion.parse("3.07.1"), offer.version()),
                () -> assertEquals("3.07.1", offer.version().text()),
                () -> assertEquals(ToolOrigin.LOCAL, offer.origin()),
                () -> assertEquals(ToolInstallState.INSTALLED, offer.state()),
                () -> assertEquals(Optional.of(binary), offer.installedPath()),
                () -> assertEquals(Optional.empty(), offer.loaderDiagnostic()),
                () ->
                        assertEquals(
                                List.of(ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT),
                                offer.capabilities().stream()
                                        .map(DeclaredCapability::capability)
                                        .toList()),
                () ->
                        assertTrue(
                                offer.capabilities().stream()
                                        .allMatch(
                                                declared ->
                                                        declared.evidence()
                                                                == CapabilityEvidence
                                                                        .OBSERVED_BY_EXECUTION),
                                "a local binary's capabilities are only ever observed, because"
                                        + " there is no manifest row to infer anything from"),
                () ->
                        assertEquals(
                                List.of(LocalPercolatorRegistration.UNMANAGED_ADVISORY),
                                offer.advisories()));
    }

    @Test
    @DisplayName("the evidence note names the host and the file, so a reader can act on it")
    void theEvidenceNote(@TempDir Path directory) throws IOException, ToolRegistrationException {
        Path binary = realBinary(directory);

        RegisteredLocalBinary registered = realRegistrar().register(binary);

        assertEquals(
                "probed by execution on linux-x86-64 when "
                        + binary
                        + " was registered as a local binary: the functional probe ran this build"
                        + " over a 64 target plus 64 decoy synthetic PIN and read the document it"
                        + " wrote",
                registered.offer().capabilities().get(0).note());
    }

    @Test
    @DisplayName("a binary reporting 3.04 is refused, naming the version found AND the minimum")
    void tooOld(@TempDir Path directory) throws IOException {
        Path binary = Files.writeString(directory.resolve("percolator"), "not really");
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenPrints(
                                0,
                                List.of("Percolator version 3.04, Build Date Jan  1 2020 00:00:00"),
                                List.of());

        assertEquals(
                "The file at "
                        + binary
                        + " is Percolator 3.04, and CometGUI requires Percolator 3.05 or newer.",
                assertThrows(
                                ToolRegistrationException.class,
                                () -> scriptedRegistrar(runner).register(binary))
                        .getMessage());
    }

    @Test
    @DisplayName("the floor is a numeric comparison, so 3.10 is above 3.05 and 3.4 is below it")
    void theFloorIsNumeric(@TempDir Path directory) throws IOException {
        assertAll(
                () ->
                        assertEquals(
                                ToolVersion.parse("3.05"),
                                LocalPercolatorRegistration.MINIMUM_VERSION),
                () -> assertEquals("3.05", LocalPercolatorRegistration.MINIMUM_VERSION.text()),
                () ->
                        assertTrue(
                                ToolVersion.parse("3.10")
                                        .isAtLeast(LocalPercolatorRegistration.MINIMUM_VERSION),
                                "read as text, \"3.10\" sorts below \"3.05\""),
                () ->
                        assertTrue(
                                ToolVersion.parse("3.5")
                                        .isAtLeast(LocalPercolatorRegistration.MINIMUM_VERSION),
                                "3.5 and 3.05 are one version"),
                () ->
                        assertTrue(
                                !ToolVersion.parse("3.4")
                                        .isAtLeast(LocalPercolatorRegistration.MINIMUM_VERSION)),
                () ->
                        assertTrue(
                                ToolVersion.parse("3.05")
                                        .isAtLeast(LocalPercolatorRegistration.MINIMUM_VERSION),
                                "the floor is inclusive: exactly 3.05 is accepted"));
    }

    @Test
    @DisplayName("exactly 3.05 registers, which is the boundary the floor is stated at")
    void exactlyTheMinimum(@TempDir Path directory) throws IOException, ToolRegistrationException {
        Path binary = Files.writeString(directory.resolve("percolator"), "not really");
        Path targets = directory.resolve("t.xml");
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenPrints(
                                0,
                                List.of("Percolator version 3.05, Build Date Jan  1 2020 00:00:00"),
                                List.of())
                        .thenPrints(
                                0,
                                List.of("Percolator version 3.05, Build Date Jan  1 2020 00:00:00"),
                                List.of())
                        .thenPrints(
                                0,
                                List.of("Percolator version 3.05, Build Date Jan  1 2020 00:00:00"),
                                List.of());

        RegisteredLocalBinary registered = scriptedRegistrar(runner).register(binary);

        assertAll(
                () -> assertEquals(ToolVersion.parse("3.05"), registered.offer().version()),
                () ->
                        assertEquals(
                                List.of(),
                                registered.offer().capabilities(),
                                "it printed its banner and wrote no document, so the absence of"
                                        + " every capability really was observed"),
                () -> assertTrue(targets.getFileName() != null));
    }

    @Test
    @DisplayName("a file that is not there is refused differently from one that is not Percolator")
    void noSuchFile(@TempDir Path directory) {
        Path missing = directory.resolve("nowhere").resolve("percolator");

        assertEquals(
                "There is no file at " + missing.toAbsolutePath() + " to register as Percolator.",
                assertThrows(
                                ToolRegistrationException.class,
                                () -> scriptedRegistrar(new ScriptedRunner()).register(missing))
                        .getMessage());
    }

    @Test
    @DisplayName("a directory is not a binary")
    void aDirectory(@TempDir Path directory) throws IOException {
        Path notAFile = Files.createDirectory(directory.resolve("percolator"));

        assertEquals(
                "There is no file at " + notAFile + " to register as Percolator.",
                assertThrows(
                                ToolRegistrationException.class,
                                () -> scriptedRegistrar(new ScriptedRunner()).register(notAFile))
                        .getMessage());
    }

    @Test
    @DisplayName(
            "an UNREADABLE file is refused with its own sentence, telling the user what to fix")
    void unreadable(@TempDir Path directory) throws IOException {
        Path binary = Files.writeString(directory.resolve("percolator"), "bytes");
        Files.setPosixFilePermissions(binary, Set.of());

        try {
            assertEquals(
                    "The file at "
                            + binary
                            + " cannot be read, so CometGUI cannot check what it is. Check its"
                            + " permissions and try again.",
                    assertThrows(
                                    ToolRegistrationException.class,
                                    () -> scriptedRegistrar(new ScriptedRunner()).register(binary))
                            .getMessage());
        } finally {
            Files.setPosixFilePermissions(
                    binary,
                    Set.of(
                            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
        }
    }

    @Test
    @DisplayName(
            "the REAL Comet binary is refused as not Percolator, which is the honest confusion")
    void aRealBinaryThatIsNotPercolator(@TempDir Path directory) throws IOException {
        Path comet =
                UpstreamArtefacts.executableCopy(
                        "v2026.02.2__comet.linux.exe",
                        directory.resolve("bin").resolve("percolator"));

        ToolRegistrationException refused =
                assertThrows(
                        ToolRegistrationException.class, () -> realRegistrar().register(comet));

        assertAll(
                () ->
                        assertTrue(
                                refused.getMessage()
                                        .startsWith(
                                                "The file at "
                                                        + comet
                                                        + " is not Percolator: it printed no"
                                                        + " \"Percolator version\" line in answer"
                                                        + " to --help."),
                                refused.getMessage()),
                () ->
                        assertTrue(
                                refused.getMessage().contains("Comet version"),
                                "what it did say has to reach the message, or the user cannot see"
                                        + " that they picked the wrong tool: "
                                        + refused.getMessage()));
    }

    @Test
    @DisplayName("a file that starts and says nothing recognisable is refused, quoting the silence")
    void notPercolatorAtAll(@TempDir Path directory) throws IOException {
        Path binary = Files.writeString(directory.resolve("percolator"), "bytes");
        ScriptedRunner runner = new ScriptedRunner().thenPrints(2, List.of(), List.of());

        assertEquals(
                "The file at "
                        + binary
                        + " is not Percolator: it printed no \"Percolator version\" line in answer"
                        + " to --help. It exited 2 saying: ",
                assertThrows(
                                ToolRegistrationException.class,
                                () -> scriptedRegistrar(runner).register(binary))
                        .getMessage());
    }

    @Test
    @DisplayName("a file that cannot be started is refused, quoting the reason it could not be")
    void cannotBeStarted(@TempDir Path directory) throws IOException {
        Path binary = Files.writeString(directory.resolve("percolator"), "bytes");
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenFailsToStart("Cannot run program: error=13, Permission denied");

        assertEquals(
                "The file at "
                        + binary
                        + " could not be started: Cannot run program: error=13, Permission denied",
                assertThrows(
                                ToolRegistrationException.class,
                                () -> scriptedRegistrar(runner).register(binary))
                        .getMessage());
    }

    @Test
    @DisplayName("a run that never answers is refused, and is not the same as an unrecognised one")
    void neverAnswers(@TempDir Path directory) throws IOException {
        Path binary = Files.writeString(directory.resolve("percolator"), "bytes");
        ToolRunner runner =
                new ToolRunner(new ScriptedRunner().thenNeverFinishes(), Duration.ofMillis(50));

        assertEquals(
                "The file at "
                        + binary
                        + " was started but had not answered --help after PT0.05S, so CometGUI"
                        + " cannot tell what it is.",
                assertThrows(
                                ToolRegistrationException.class,
                                () ->
                                        new LocalPercolatorRegistration(
                                                        runner,
                                                        new PercolatorCapabilityProbe(runner),
                                                        hashes(),
                                                        HOST)
                                                .register(binary))
                        .getMessage());
    }

    @Test
    @DisplayName(
            "a probe that could not be exercised is refused, not registered with no capability")
    void aProbeThatCouldNotRun(@TempDir Path directory) throws IOException {
        Path binary = Files.writeString(directory.resolve("percolator"), "bytes");
        ScriptedRunner runner =
                new ScriptedRunner()
                        .thenPrints(0, List.of(BANNER_3071), List.of())
                        .thenPrints(
                                127, List.of("error while loading shared libraries"), List.of());

        ToolRegistrationException refused =
                assertThrows(
                        ToolRegistrationException.class,
                        () -> scriptedRegistrar(runner).register(binary));

        assertAll(
                () ->
                        assertTrue(
                                refused.getMessage()
                                        .startsWith(
                                                "The file at "
                                                        + binary
                                                        + " reports itself as Percolator 3.07.1"
                                                        + " but could not be exercised, so CometGUI"
                                                        + " does not know what it can do."
                                                        + " Registering it with no capabilities"
                                                        + " would say it can do nothing, which is"
                                                        + " not what was observed:"),
                                refused.getMessage()),
                () ->
                        assertTrue(
                                refused.getCause() instanceof IOException,
                                "the probe's own refusal is kept as the cause"));
    }

    @Test
    @DisplayName(
            "a file that cannot be checksummed is refused, because provenance needs the digest")
    void cannotBeChecksummed(@TempDir Path directory) throws IOException {
        Path binary = Files.writeString(directory.resolve("percolator"), "bytes");
        ToolRunner runner =
                new ToolRunner(
                        new ScriptedRunner().thenPrints(0, List.of(BANNER_3071), List.of()),
                        Duration.ofSeconds(5));
        HashService broken =
                file -> {
                    throw new IOException("the disc went away");
                };

        assertEquals(
                "The file at "
                        + binary
                        + " could not be checksummed, and a tool with no recorded checksum cannot"
                        + " appear in a provenance record: the disc went away",
                assertThrows(
                                ToolRegistrationException.class,
                                () ->
                                        new LocalPercolatorRegistration(
                                                        runner,
                                                        new PercolatorCapabilityProbe(runner),
                                                        broken,
                                                        HOST)
                                                .register(binary))
                        .getMessage());
    }

    @Test
    @DisplayName("every constructor argument and the path itself are required")
    void everyArgumentIsRequired() {
        ToolRunner runner = new ToolRunner(new ScriptedRunner(), Duration.ofSeconds(1));
        PercolatorCapabilityProbe probe = new PercolatorCapabilityProbe(runner);

        assertAll(
                () ->
                        assertEquals(
                                "runner",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LocalPercolatorRegistration(
                                                                Nulls.of(ToolRunner.class),
                                                                probe,
                                                                hashes(),
                                                                HOST))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "capabilities",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LocalPercolatorRegistration(
                                                                runner,
                                                                Nulls.of(
                                                                        PercolatorCapabilityProbe
                                                                                .class),
                                                                hashes(),
                                                                HOST))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "hashes",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LocalPercolatorRegistration(
                                                                runner,
                                                                probe,
                                                                Nulls.of(HashService.class),
                                                                HOST))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "host",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LocalPercolatorRegistration(
                                                                runner,
                                                                probe,
                                                                hashes(),
                                                                Nulls.of(HostPlatform.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "binary",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LocalPercolatorRegistration(
                                                                        runner, probe, hashes(),
                                                                        HOST)
                                                                .register(Nulls.of(Path.class)))
                                        .getMessage()));
    }
}
