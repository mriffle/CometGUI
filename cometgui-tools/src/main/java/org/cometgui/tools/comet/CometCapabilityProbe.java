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

package org.cometgui.tools.comet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.api.CompanionGate;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;

/**
 * What a Comet build can do, established by making it write its own parameter file.
 *
 * <p>The signature is {@code org.cometgui.install.probe.CapabilityProber}'s, satisfied from the
 * composition root by a method reference; see {@code
 * org.cometgui.tools.percolator.PercolatorCapabilityProbe} for why this module cannot name that
 * interface.
 *
 * <h2>Two runs, and the difference between them is the capability</h2>
 *
 * <p>{@code comet -p} writes the default parameter file and {@code comet -q} writes the
 * <em>complete</em> one. On this project's host, executed 2026-09-03, {@code -p} declares
 * <strong>96</strong> parameters and {@code -q} declares <strong>118</strong> -- the difference
 * {@code R-PARAM-01} records, and the reason {@code COMPLETE_PARAMS_QUERY} is a capability at all.
 * So the probe runs both and grants it only when {@code -q}'s file declares strictly more than
 * {@code -p}'s. A probe that ran {@code -q} alone would grant the capability to a build whose
 * {@code -q} silently behaved like {@code -p}, which is the whole thing the capability is about.
 *
 * <p>{@code PEPXML_OUTPUT} and {@code PIN_OUTPUT} are then read out of the file the binary itself
 * wrote: it declares {@code output_pepxmlfile} and {@code output_percolatorfile} or it does not.
 * That is the binary's own statement about its own options rather than help text about a family of
 * builds, and it is what {@code R-TOOL-06} means by capability probing where a smoke run is cheap.
 * It is <em>not</em> the Percolator situation: there, two builds that differ in capability print
 * byte-identical text, and no reading of any text could separate them.
 *
 * <h2>The Thermo companion rule is a lookup</h2>
 *
 * <p>{@code THERMO_RAW_WINDOWS} is granted only by a {@link CompanionGate}, and the gates are
 * supplied to this probe rather than invented by it -- the artefact manifest already carries the
 * rule as data, with {@code "gatesCapability": "THERMO_RAW_WINDOWS"} on each of Comet's three
 * Windows companions. {@link CometCompanionGates#thermoRawWindows()} is the same three names for a
 * caller that has no manifest to hand.
 *
 * <h2>What is not probed is absent</h2>
 *
 * <p>{@code FRAGMENT_ION_INDEX}, {@code PEPTIDE_INDEX}, {@code SCAN_RANGE} and {@code
 * OUTPUT_BASENAME} are command-line options rather than parameters, so the file Comet writes says
 * nothing about them and this probe does not claim them ({@code R-TOOL-08}). The manifest does not
 * claim them either.
 */
public final class CometCapabilityProbe {

    /** Comet's option for the default parameter file. */
    public static final String DEFAULT_PARAMETERS_ARGUMENT = "-p";

    /** Comet's option for the complete parameter file. */
    public static final String COMPLETE_PARAMETERS_ARGUMENT = "-q";

    /** The file Comet writes into its working directory for either option. */
    public static final String WRITTEN_PARAMETERS_FILE = "comet.params.new";

    /** The parameter whose presence means Comet writes pepXML. */
    public static final String PEPXML_PARAMETER = "output_pepxmlfile";

    /** The parameter whose presence means Comet writes the Percolator PIN. */
    public static final String PIN_PARAMETER = "output_percolatorfile";

    private static final String WORKSPACE_PREFIX = "cometgui-comet-probe-";

    private final ToolRunner runner;
    private final List<CompanionGate> gates;

    /**
     * Creates the probe.
     *
     * @param runner how one invocation is run and collected
     * @param gates the companion rules read from the manifest row this install came from; empty
     *     when the row declares no companions, which is every row but the Windows one
     * @throws NullPointerException if either argument, or any gate, is {@code null}
     */
    public CometCapabilityProbe(ToolRunner runner, List<CompanionGate> gates) {
        this.runner = Objects.requireNonNull(runner, "runner");
        List<CompanionGate> copy = new ArrayList<>(Objects.requireNonNull(gates, "gates"));
        for (int index = 0; index < copy.size(); index++) {
            Objects.requireNonNull(copy.get(index), "gates[" + index + "]");
        }
        this.gates = List.copyOf(copy);
    }

    /**
     * Probes what a Comet build can do, by running it.
     *
     * @param tool which tool this is a build of; must be {@link ToolName#COMET}
     * @param version the version the identity stage read, quoted in any refusal
     * @param platform the host the probe is running on
     * @param executable the absolute path of the staged executable
     * @return the capabilities the build was observed to have, possibly empty
     * @throws IOException if the build could not be exercised at all
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code tool} is not {@link ToolName#COMET}
     */
    public Set<ToolCapability> probe(
            ToolName tool, ToolVersion version, HostPlatform platform, Path executable)
            throws IOException {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(executable, "executable");
        if (tool != ToolName.COMET) {
            throw new IllegalArgumentException(
                    "this probe runs Comet and was asked to probe "
                            + tool.id()
                            + "; a capability of one tool means nothing said of another");
        }
        Path workspace = Files.createTempDirectory(WORKSPACE_PREFIX);
        try {
            Set<ToolCapability> observed = EnumSet.noneOf(ToolCapability.class);
            observed.addAll(fromParameterFiles(workspace, version, executable));
            observed.addAll(gatedByCompanions(platform, executable));
            return Collections.unmodifiableSet(observed);
        } finally {
            deleteRecursively(workspace);
        }
    }

    /**
     * The capabilities the installed companions unlock on this host.
     *
     * <p>Exposed on its own because it is a rule about files and not about running anything, and
     * because both halves of {@code R-TOOL-02} -- with the companions and without them -- have to
     * be gradeable on a host whose Comet build cannot be executed at all.
     *
     * @param platform the host being probed
     * @param executable the installed executable; companions are looked for beside it
     * @return the gated capabilities whose gates are open, possibly empty
     * @throws NullPointerException if either argument is {@code null}
     */
    public Set<ToolCapability> gatedByCompanions(HostPlatform platform, Path executable) {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(executable, "executable");
        Set<ToolCapability> open = new LinkedHashSet<>();
        for (CompanionGate gate : gates) {
            if (gate.isOpenFor(platform, executable)) {
                open.add(gate.capability());
            }
        }
        return Collections.unmodifiableSet(open);
    }

    private Set<ToolCapability> fromParameterFiles(
            Path workspace, ToolVersion version, Path executable) throws IOException {
        Set<String> defaults =
                declaredBy(workspace, version, executable, DEFAULT_PARAMETERS_ARGUMENT, "defaults");
        Set<String> complete =
                declaredBy(
                        workspace, version, executable, COMPLETE_PARAMETERS_ARGUMENT, "complete");
        Set<ToolCapability> observed = EnumSet.noneOf(ToolCapability.class);
        if (complete.size() > defaults.size()) {
            observed.add(ToolCapability.COMPLETE_PARAMS_QUERY);
        }
        if (complete.contains(PEPXML_PARAMETER)) {
            observed.add(ToolCapability.PEPXML_OUTPUT);
        }
        if (complete.contains(PIN_PARAMETER)) {
            observed.add(ToolCapability.PIN_OUTPUT);
        }
        return observed;
    }

    /*
     * Each option gets its own directory, because Comet writes comet.params.new into the working
     * directory under both and the second run would otherwise be compared against the first run's
     * file -- a comparison that can only ever say "the same", which is a check that cannot fail.
     */
    private Set<String> declaredBy(
            Path workspace, ToolVersion version, Path executable, String argument, String name)
            throws IOException {
        Path directory = Files.createDirectory(workspace.resolve(name));
        ToolRunOutcome outcome =
                runner.run(
                        new ToolCommand(
                                List.of(executable.toString(), argument), directory, Map.of()));
        if (outcome.timedOut()) {
            throw new IOException(
                    "Comet "
                            + version.text()
                            + " at "
                            + executable
                            + " did not answer "
                            + argument
                            + " within "
                            + runner.timeout()
                            + ", so this probe established nothing about it");
        }
        if (!CometBanner.isPresentIn(outcome.errorFirst())) {
            throw new IOException(
                    "Comet "
                            + version.text()
                            + " at "
                            + executable
                            + " never printed its version banner in answer to "
                            + argument
                            + ", so it did not run far enough to be asked what it can do; this is a"
                            + " loadability failure and must not be reported as a missing"
                            + " capability. It exited "
                            + outcome.exitCode().orElse(-1)
                            + " saying: "
                            + outcome.joinedOutput());
        }
        Path written = directory.resolve(WRITTEN_PARAMETERS_FILE);
        if (!Files.isRegularFile(written)) {
            return Set.of();
        }
        return CometParameterDeclarations.readFrom(written);
    }

    /* Best effort and deliberately silent; see PercolatorCapabilityProbe for the reasoning. */
    private static void deleteRecursively(Path directory) {
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path entry : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException tidyingFailed) {
            /* Nothing useful can be done, and nothing about the probe's verdict changes. */
        }
    }
}
