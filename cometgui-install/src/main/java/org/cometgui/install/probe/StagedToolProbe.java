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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.cometgui.domain.tools.LoaderDiagnostic;
import org.cometgui.domain.tools.ProbeFailureKind;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.cache.ToolProbe;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * {@code R-TOOL-06}'s three ordered stages, composed: does it start, what is it, what can it do.
 *
 * <p>This is the implementation of {@code org.cometgui.install.cache.ToolProbe} that install step 6
 * calls. The pipeline runs it on a <strong>staged</strong> directory after the SHA-256 has been
 * verified, the layout checked and the executable bit set, so {@code R-SEC-02}'s "verify before you
 * execute" is satisfied by the order of the steps and is not re-implemented here.
 *
 * <h2>The order is the guarantee</h2>
 *
 * <p>A stage is only reached when every earlier one passed, and a failure is reported as the stage
 * it happened at. That is not bookkeeping: reporting a missing Visual C++ runtime as "not
 * XML-capable" is the specific defect {@code phases/PHASE-05-tool-registry.rst} names, and it is
 * produced by a probe that never got as far as looking. Refusing is throwing, because {@code
 * R-TOOL-06} says a tool that fails loadability is never offered.
 *
 * <ol>
 *   <li><strong>Advance check.</strong> {@link HostRequirementCheck} against the manifest's
 *       declared floors. Refuses only when it can name the floor; an unmeasurable floor lets the
 *       run proceed.
 *   <li><strong>Loadability.</strong> {@link LoadabilityProbe} runs the binary through the process
 *       service.
 *   <li><strong>Identity.</strong> {@link IdentityProbe} parses the banner, standard error first. A
 *       version that does not parse is {@link ProbeFailureKind#UNPARSEABLE_VERSION}, and a version
 *       that parses but is not the one the manifest pinned is refused too -- {@code R-TOOL-09} pins
 *       exact versions, and a cache entry recorded under a version the binary disagrees with is a
 *       provenance record that cannot be reproduced.
 *   <li><strong>Capability.</strong> Delegated to {@link CapabilityProber}, which phase 05 unit 7
 *       implements in {@code org.cometgui.tools}.
 * </ol>
 *
 * <h2>What this unit does not know</h2>
 *
 * <p>It has banners for the two tools this project has watched print one -- Comet and Percolator.
 * PDV and the Limelight converter are Java artefacts whose identity needs a JVM launch, which
 * belongs with the other tool adapters, so asking this probe for one <strong>fails by name</strong>
 * rather than guessing or silently skipping the stage. Supplying those banners is what widens it.
 */
public final class StagedToolProbe implements ToolProbe {

    private final LoadabilityProbe loadability;
    private final HostRuntimeVersions versions;
    private final Map<ToolName, VersionBanner> banners;
    private final CapabilityProber capabilities;
    private final Function<ArtefactRecord, List<String>> alternatives;
    private final Map<String, String> environment;

    /**
     * Composes the three stages.
     *
     * @param loadability stage 1
     * @param versions what this host's runtimes were established to be, for the advance check
     * @param banners how each tool spells its version; a tool absent from this map cannot be probed
     * @param capabilities stage 3, implemented in {@code org.cometgui.tools}
     * @param alternatives what to offer instead of a build that fails, usually {@link
     *     ManifestAlternatives#forArtefact}
     * @param environment the environment every probe run gets, which is constructed rather than
     *     inherited ({@code R-PROC-04}); usually empty
     * @throws NullPointerException if any argument is {@code null}
     */
    public StagedToolProbe(
            LoadabilityProbe loadability,
            HostRuntimeVersions versions,
            Map<ToolName, VersionBanner> banners,
            CapabilityProber capabilities,
            Function<ArtefactRecord, List<String>> alternatives,
            Map<String, String> environment) {
        this.loadability = Objects.requireNonNull(loadability, "loadability");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.banners = Map.copyOf(Objects.requireNonNull(banners, "banners"));
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.alternatives = Objects.requireNonNull(alternatives, "alternatives");
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
    }

    /**
     * {@inheritDoc}
     *
     * @throws ProbeFailedException if any of the three stages refuses the build; the message is the
     *     whole {@code R-PLAT-03} diagnostic where the failure has one
     * @throws IOException if this unit has no version banner for the record's tool
     */
    @Override
    public Set<ToolCapability> probe(ArtefactRecord record, Path stagedDirectory)
            throws IOException {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(stagedDirectory, "stagedDirectory");
        Path executable = stagedDirectory.resolve(record.executablePath());
        VersionBanner banner = bannerFor(record);
        ProbeContext context = contextFor(record);
        refuseIfHostFallsShort(record, context);
        LoadabilityResult started =
                loadability.probe(executable, banner.arguments(), environment, context);
        if (started.failure().isPresent()) {
            throw refusal(started.failure().get());
        }
        ToolVersion identified = identify(record, banner, started);
        return capabilities.probe(record.tool(), identified, loadability.host(), executable);
    }

    /**
     * Whether one artefact's staged binary refuses to start, for the offered-set gate.
     *
     * <p>The loadability stage on its own: {@link ProbeGatedOffers} needs "does it start" without
     * the identity and capability stages, because a build that will not start is not offered
     * whatever else might have been true of it.
     *
     * @param record the artefact
     * @param stagedDirectory the directory its files were laid out in
     * @return the diagnostic if it did not start, or empty if it did
     * @throws IOException if the executable cannot be reached
     * @throws NullPointerException if either argument is {@code null}
     */
    public Optional<LoaderDiagnostic> loadabilityOf(ArtefactRecord record, Path stagedDirectory)
            throws IOException {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(stagedDirectory, "stagedDirectory");
        Path executable = stagedDirectory.resolve(record.executablePath());
        return loadability
                .probe(executable, bannerFor(record).arguments(), environment, contextFor(record))
                .failure();
    }

    private ProbeContext contextFor(ArtefactRecord record) {
        return new ProbeContext(
                ProbeContext.subjectOf(record.executablePath()),
                record.minimumHostRequirements().requiredHostLibraries(),
                alternatives.apply(record));
    }

    private VersionBanner bannerFor(ArtefactRecord record) throws IOException {
        VersionBanner banner = banners.get(record.tool());
        if (banner == null) {
            throw new IOException(
                    record.describe()
                            + " cannot be probed: no version banner is configured for "
                            + record.tool().id()
                            + ", and this unit ships only the two it has watched a tool print."
                            + " A Java artefact's identity needs a JVM launch, which belongs with"
                            + " the tool adapters; supply a banner for it rather than letting the"
                            + " identity stage be skipped.");
        }
        return banner;
    }

    private void refuseIfHostFallsShort(ArtefactRecord record, ProbeContext context)
            throws ProbeFailedException {
        HostRequirementVerdict verdict =
                HostRequirementCheck.check(record.minimumHostRequirements(), versions);
        if (!verdict.isRefusal()) {
            return;
        }
        throw refusal(
                new LoaderDiagnostic(
                        ProbeFailureKind.MISSING_SYMBOL_VERSION,
                        verdict.objectName().orElseThrow(),
                        verdict.requiredVersion(),
                        verdict.availableVersion(),
                        context.alternatives()));
    }

    private ToolVersion identify(
            ArtefactRecord record, VersionBanner banner, LoadabilityResult started)
            throws ProbeFailedException {
        List<String> searched = started.output();
        ToolVersion identified =
                IdentityProbe.identify(banner, started.standardError(), started.standardOutput())
                        .orElseThrow(
                                () ->
                                        new ProbeFailedException(
                                                ProbeFailureKind.UNPARSEABLE_VERSION,
                                                record.describe()
                                                        + " printed no version this build"
                                                        + " recognises: "
                                                        + searched.size()
                                                        + " line(s) of output were searched,"
                                                        + " standard error first"));
        if (!identified.equals(record.version())) {
            throw new ProbeFailedException(
                    ProbeFailureKind.UNPARSEABLE_VERSION,
                    record.describe()
                            + " reports itself as version "
                            + identified.text()
                            + ", and the manifest pins "
                            + record.version().text()
                            + "; a cache entry recorded under a version the binary disagrees with"
                            + " cannot be reproduced");
        }
        return identified;
    }

    private static ProbeFailedException refusal(LoaderDiagnostic diagnostic) {
        return new ProbeFailedException(diagnostic.kind(), diagnostic.message());
    }
}
