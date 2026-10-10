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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
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
import org.cometgui.install.probe.VersionBanner;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.comet.CometCapabilityProbe;

/**
 * Registering a Comet binary the user already has: {@code R-TOOL-08}'s "unknown local binary" for
 * Comet, and what an Intel Mac is offered instead of a managed install ({@code D-011}).
 *
 * <p>Upstream has never published an x86-64 macOS Comet, so the artefact manifest carries no
 * managed Comet for an Intel Mac; a Comet the user built is the remedy, and this is where it is
 * accepted. It is composed here, in the application, because its two halves live in two modules
 * that do not depend on each other: the identity reading is {@link VersionBanner#comet()}'s, the
 * one translation of Comet's banner into the manifest's version, and the capability probe is {@link
 * CometCapabilityProbe}'s. Neither is duplicated.
 *
 * <ul>
 *   <li><strong>The version is read from the binary</strong>, never from the file name, through the
 *       same banner rule the installer's identity stage uses.
 *   <li><strong>Absent positive evidence, a capability is absent.</strong> The capabilities are
 *       what the probe watched this binary do. The probe is given <em>no</em> companion gates,
 *       because those come from a manifest row and a local binary has none -- so Thermo RAW reading
 *       is never claimed for a binary CometGUI did not install.
 *   <li><strong>A probe that could not run is a refusal</strong>, not an empty capability set,
 *       because an empty set is positive evidence of absence.
 *   <li><strong>No version floor.</strong> The specification sets none for Comet. A registered
 *       Comet whose release has no parameter set in this build is shown and simply never chosen for
 *       a run, whose parameters always name a release.
 * </ul>
 */
public final class LocalCometRegistration {

    /** The advisory every local Comet carries, because nothing verified these bytes. */
    public static final ToolAdvisory UNMANAGED_ADVISORY =
            new ToolAdvisory(
                    "comet.local-binary-is-unverified",
                    "This Comet was registered from a file on this machine. CometGUI did not"
                            + " download it and cannot check it against a pinned checksum, so its"
                            + " provenance is whatever you know about where it came from. Its"
                            + " capabilities below were probed by running it here.");

    private final ToolRunner runner;
    private final CometCapabilityProbe capabilities;
    private final HashService hashes;
    private final HostPlatform host;
    private final VersionBanner banner = VersionBanner.comet();

    /**
     * Creates the registrar.
     *
     * @param runner how the identifying invocation is run
     * @param hashes the one hashing service in this product
     * @param host the machine the binary would run on
     * @throws NullPointerException if any argument is {@code null}
     */
    public LocalCometRegistration(ToolRunner runner, HashService hashes, HostPlatform host) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.capabilities = new CometCapabilityProbe(runner, List.of());
        this.hashes = Objects.requireNonNull(hashes, "hashes");
        this.host = Objects.requireNonNull(host, "host");
    }

    /**
     * Probes a local file and registers it if it is Comet.
     *
     * @param binary the file the user chose
     * @return the offer the Tool Manager renders, {@link ToolOrigin#LOCAL} and installed at {@code
     *     binary}
     * @throws ToolRegistrationException if the file is not there, is not readable, does not start,
     *     is not Comet, could not be checksummed or could not be exercised -- each with its own
     *     sentence
     * @throws NullPointerException if {@code binary} is {@code null}
     */
    public ToolOffer register(Path binary) throws ToolRegistrationException {
        Objects.requireNonNull(binary, "binary");
        Path absolute = binary.toAbsolutePath();
        requireReadableFile(absolute);
        ToolVersion version = identify(absolute);
        checksum(absolute);
        Set<ToolCapability> probed = probeCapabilities(absolute, version);
        return offer(absolute, version, probed);
    }

    private static void requireReadableFile(Path binary) throws ToolRegistrationException {
        if (!Files.isRegularFile(binary)) {
            throw new ToolRegistrationException(
                    "There is no file at " + binary + " to register as Comet.");
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
        Path directory = binary.getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            throw new ToolRegistrationException(
                    "The file at " + binary + " has no directory to run in.");
        }
        List<String> argv = new ArrayList<>();
        argv.add(binary.toString());
        argv.addAll(banner.arguments());
        ToolRunOutcome outcome;
        try {
            outcome = runner.run(new ToolCommand(argv, directory, Map.of()));
        } catch (IOException didNotStart) {
            throw new ToolRegistrationException(
                    "The file at " + binary + " could not be started: " + didNotStart.getMessage(),
                    didNotStart);
        }
        if (outcome.timedOut()) {
            throw new ToolRegistrationException(
                    "The file at "
                            + binary
                            + " was started but had not answered "
                            + String.join(" ", banner.arguments())
                            + " after "
                            + runner.timeout()
                            + ", so CometGUI cannot tell what it is.");
        }
        Optional<ToolVersion> version = banner.readFrom(outcome.errorFirst());
        return version.orElseThrow(
                () ->
                        new ToolRegistrationException(
                                "The file at "
                                        + binary
                                        + " is not Comet: it printed no \"Comet version\" line in"
                                        + " answer to "
                                        + String.join(" ", banner.arguments())
                                        + ". It exited "
                                        + outcome.exitCode().orElse(-1)
                                        + " saying: "
                                        + outcome.joinedOutput()));
    }

    /*
     * Checksummed at registration even though the offer does not carry the digest: a binary that
     * cannot be read through cannot be recorded in a provenance record either, and a run re-hashes
     * the executable it is given (WorkflowRunPort), so failing here is failing early rather than
     * failing differently.
     */
    private void checksum(Path binary) throws ToolRegistrationException {
        try {
            hashes.hash(binary);
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
            return capabilities.probe(ToolName.COMET, version, host, binary);
        } catch (IOException notExercised) {
            throw new ToolRegistrationException(
                    "The file at "
                            + binary
                            + " reports itself as Comet "
                            + version.text()
                            + " but could not be exercised, so CometGUI does not know what it can"
                            + " do. Registering it with no capabilities would say it can do"
                            + " nothing, which is not what was observed: "
                            + notExercised.getMessage(),
                    notExercised);
        }
    }

    private ToolOffer offer(Path binary, ToolVersion version, Set<ToolCapability> probed) {
        List<DeclaredCapability> declared = new ArrayList<>();
        for (ToolCapability capability : probed) {
            declared.add(
                    new DeclaredCapability(
                            capability,
                            CapabilityEvidence.OBSERVED_BY_EXECUTION,
                            "probed by execution on "
                                    + host.id()
                                    + " when "
                                    + binary
                                    + " was registered as a local binary: Comet was asked for its"
                                    + " default and its complete parameter files, and what each"
                                    + " declared was read"));
        }
        return new ToolOffer(
                ToolName.COMET,
                version,
                ToolOrigin.LOCAL,
                ToolInstallState.INSTALLED,
                declared,
                List.of(UNMANAGED_ADVISORY),
                Optional.empty(),
                Optional.of(binary),
                OptionalLong.empty());
    }
}
