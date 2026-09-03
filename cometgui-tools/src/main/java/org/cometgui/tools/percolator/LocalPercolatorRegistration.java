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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;

/**
 * Registering a Percolator binary the user already has: {@code R-TOOL-08}'s "unknown local binary".
 *
 * <p>This is the documented remedy wherever no managed XML-capable build exists for a platform
 * ({@code R-PERC-03}), so it is a first-class path rather than a fallback -- and it is the one
 * place in this product where a file nobody vouched for is executed. Everything about it is
 * therefore conservative:
 *
 * <ul>
 *   <li><strong>The version is read from the binary, never from the file name.</strong> A file
 *       called {@code percolator-3.09} that prints {@code Percolator version 3.04} is 3.04.
 *   <li><strong>The floor is a numeric comparison.</strong> {@code ToolVersion} requires two to
 *       four numeric components, so {@code 3.05} is a version and not a string, {@code 3.05} equals
 *       {@code 3.5}, and {@code 3.04 < 3.05 < 3.06.5} however the release was spelled. A string
 *       test would put {@code 3.10} below {@code 3.05}.
 *   <li><strong>Absent positive evidence, a capability is absent.</strong> The capability set is
 *       whatever the functional probe watched this binary do and nothing else -- no manifest row
 *       exists for a local binary, so there is nothing to inherit and nothing to assume.
 *   <li><strong>A probe that could not run is not an empty capability set.</strong> {@code
 *       R-TOOL-08} makes an empty set positive evidence of absence, so a binary that starts far
 *       enough to print a version but cannot then be exercised is <em>refused</em>, not registered
 *       with nothing.
 * </ul>
 *
 * <p><strong>Every refusal names its own cause, and they are four different sentences.</strong> A
 * file that is not there, a file that is not Percolator, a Percolator that is too old and a
 * Percolator that could not be exercised are four different things for the user to do something
 * about, and a single "could not register that" would tell them none of it.
 */
public final class LocalPercolatorRegistration {

    /**
     * The oldest Percolator this product will register.
     *
     * <p>{@code specification.rst}'s <em>Percolator installation modes</em>: "CometGUI probes it,
     * verifies it is Percolator &gt;= 3.05". Written as a parsed version rather than a string
     * because that is what makes the check a comparison of releases.
     */
    public static final ToolVersion MINIMUM_VERSION = ToolVersion.parse("3.05");

    /** The advisory every local registration carries, because nothing verified these bytes. */
    public static final ToolAdvisory UNMANAGED_ADVISORY =
            new ToolAdvisory(
                    "percolator.local-binary-is-unverified",
                    "This Percolator was registered from a file on this machine. CometGUI did not"
                            + " download it and cannot check it against a pinned checksum, so its"
                            + " provenance is whatever you know about where it came from. Its"
                            + " capabilities below were probed by running it here.");

    private final ToolRunner runner;
    private final PercolatorCapabilityProbe capabilities;
    private final HashService hashes;
    private final HostPlatform host;

    /**
     * Creates the registrar.
     *
     * @param runner how the identifying invocation is run
     * @param capabilities the functional capability probe
     * @param hashes the one hashing service in this product
     * @param host the machine the binary would run on
     * @throws NullPointerException if any argument is {@code null}
     */
    public LocalPercolatorRegistration(
            ToolRunner runner,
            PercolatorCapabilityProbe capabilities,
            HashService hashes,
            HostPlatform host) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.hashes = Objects.requireNonNull(hashes, "hashes");
        this.host = Objects.requireNonNull(host, "host");
    }

    /**
     * Probes a local file and registers it if it is a Percolator this product supports.
     *
     * @param binary the file the user chose
     * @return the registration, carrying the offer the Tool Manager renders and the checksums the
     *     provenance record needs
     * @throws ToolRegistrationException if the file is not there, is not readable, does not start,
     *     is not Percolator, is older than {@link #MINIMUM_VERSION}, or cannot be exercised -- each
     *     with its own sentence
     * @throws NullPointerException if {@code binary} is {@code null}
     */
    public RegisteredLocalBinary register(Path binary) throws ToolRegistrationException {
        Objects.requireNonNull(binary, "binary");
        Path absolute = binary.toAbsolutePath();
        requireReadableFile(absolute);
        ToolVersion version = identify(absolute);
        requireAtLeastMinimum(absolute, version);
        FileHashes checksums = checksumsOf(absolute);
        Set<ToolCapability> probed = probeCapabilities(absolute, version);
        return new RegisteredLocalBinary(offer(absolute, version, probed), checksums, absolute);
    }

    private static void requireReadableFile(Path binary) throws ToolRegistrationException {
        if (!Files.isRegularFile(binary)) {
            throw new ToolRegistrationException(
                    "There is no file at " + binary + " to register as Percolator.");
        }
        if (!Files.isReadable(binary)) {
            throw new ToolRegistrationException(
                    "The file at "
                            + binary
                            + " cannot be read, so CometGUI cannot check what it is. Check its"
                            + " permissions and try again.");
        }
    }

    private ToolVersion identify(Path binary) throws ToolRegistrationException {
        ToolRunOutcome outcome = runVersionQuery(binary);
        if (outcome.timedOut()) {
            throw new ToolRegistrationException(
                    "The file at "
                            + binary
                            + " was started but had not answered "
                            + String.join(" ", PercolatorBanner.VERSION_ARGUMENTS)
                            + " after "
                            + runner.timeout()
                            + ", so CometGUI cannot tell what it is.");
        }
        Optional<ToolVersion> version = PercolatorBanner.readFrom(outcome.errorFirst());
        return version.orElseThrow(
                () ->
                        new ToolRegistrationException(
                                "The file at "
                                        + binary
                                        + " is not Percolator: it printed no \"Percolator version\""
                                        + " line in answer to "
                                        + String.join(" ", PercolatorBanner.VERSION_ARGUMENTS)
                                        + ". It exited "
                                        + outcome.exitCode().orElse(-1)
                                        + " saying: "
                                        + outcome.joinedOutput()));
    }

    private ToolRunOutcome runVersionQuery(Path binary) throws ToolRegistrationException {
        Path directory = binary.getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            throw new ToolRegistrationException(
                    "The file at " + binary + " has no directory to run in.");
        }
        List<String> argv = new ArrayList<>(PercolatorBanner.VERSION_ARGUMENTS.size() + 1);
        argv.add(binary.toString());
        argv.addAll(PercolatorBanner.VERSION_ARGUMENTS);
        try {
            return runner.run(new ToolCommand(argv, directory, Map.of()));
        } catch (IOException didNotStart) {
            throw new ToolRegistrationException(
                    "The file at " + binary + " could not be started: " + didNotStart.getMessage(),
                    didNotStart);
        }
    }

    /*
     * The message names BOTH numbers, hand-typed at the call site rather than recomputed, because
     * a guard that fires correctly while its message misstates the value it rejected is this
     * project's eleventh catalogued failure shape -- and this particular sentence is the one a
     * scientist reads before deciding whether to go and find a different build.
     */
    private static void requireAtLeastMinimum(Path binary, ToolVersion version)
            throws ToolRegistrationException {
        if (version.isAtLeast(MINIMUM_VERSION)) {
            return;
        }
        throw new ToolRegistrationException(
                "The file at "
                        + binary
                        + " is Percolator "
                        + version.text()
                        + ", and CometGUI requires Percolator "
                        + MINIMUM_VERSION.text()
                        + " or newer.");
    }

    private FileHashes checksumsOf(Path binary) throws ToolRegistrationException {
        try {
            return hashes.hash(binary);
        } catch (IOException notHashed) {
            throw new ToolRegistrationException(
                    "The file at "
                            + binary
                            + " could not be checksummed, and a tool with no recorded checksum"
                            + " cannot appear in a provenance record: "
                            + notHashed.getMessage(),
                    notHashed);
        }
    }

    private Set<ToolCapability> probeCapabilities(Path binary, ToolVersion version)
            throws ToolRegistrationException {
        try {
            return capabilities.probe(ToolName.PERCOLATOR, version, host, binary);
        } catch (IOException notExercised) {
            throw new ToolRegistrationException(
                    "The file at "
                            + binary
                            + " reports itself as Percolator "
                            + version.text()
                            + " but could not be exercised, so CometGUI does not know what it can"
                            + " do. Registering it with no capabilities would say it can do"
                            + " nothing, which is not what was observed: "
                            + notExercised.getMessage(),
                    notExercised);
        }
    }

    private ToolOffer offer(Path binary, ToolVersion version, Set<ToolCapability> probed) {
        List<DeclaredCapability> declared = new ArrayList<>(probed.size());
        for (ToolCapability capability : probed) {
            declared.add(
                    new DeclaredCapability(
                            capability,
                            CapabilityEvidence.OBSERVED_BY_EXECUTION,
                            "probed by execution on "
                                    + host.id()
                                    + " when "
                                    + binary
                                    + " was registered as a local binary: the functional probe ran"
                                    + " this build over a 64 target plus 64 decoy synthetic PIN and"
                                    + " read the document it wrote"));
        }
        return new ToolOffer(
                ToolName.PERCOLATOR,
                version,
                ToolOrigin.LOCAL,
                ToolInstallState.INSTALLED,
                declared,
                List.of(UNMANAGED_ADVISORY),
                Optional.empty(),
                Optional.of(binary));
    }
}
