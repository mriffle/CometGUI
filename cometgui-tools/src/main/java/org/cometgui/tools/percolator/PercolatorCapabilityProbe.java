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
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
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
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;

/**
 * {@code R-PERC-02}'s functional capability probe: run the binary and read what it wrote.
 *
 * <p>This class has the shape of {@code org.cometgui.install.probe.CapabilityProber} without naming
 * it. {@code cometgui-tools} depends on {@code cometgui-domain} and {@code cometgui-process} and
 * not on {@code cometgui-install}, so it cannot implement an interface declared there; the port is
 * stated entirely in domain vocabulary precisely so that a method reference -- {@code
 * percolatorProbe::probe} -- satisfies it from the composition root, which is the only place that
 * sees both modules.
 *
 * <h2>Why a text probe would prove nothing</h2>
 *
 * <p>The {@code noxml} and {@code XML_SUPPORT=ON} builds of Percolator 3.07.1 print
 * <strong>byte-identical</strong> help text -- 17928 characters each, both listing {@code
 * --xmloutput} and {@code --decoy-xml-output} -- so a probe that read the help text would call both
 * builds capable and would have no way to be wrong about either. {@code
 * scripts/feasibility/probe_xml_capability.py} is wrong for exactly that reason and is not the
 * ancestor of this class.
 *
 * <h2>The two capabilities are established by two runs</h2>
 *
 * <p>{@code XML_OUTPUT} by {@code -X}, {@code XML_DECOY_OUTPUT} by {@code -X -Z}, each judged on
 * the document it produced. They are <em>separate runs</em> rather than one run read two ways
 * because 3.09 has already shown that a release can drop one XML feature and keep others, and one
 * combined flag would make such a release indistinguishable from a fully capable one -- which is
 * the argument {@code ToolCapability.XML_DECOY_OUTPUT} carries.
 *
 * <h2>What "no answer" means, and why it is not an empty set</h2>
 *
 * <p>{@code R-TOOL-08} makes an empty capability set positive evidence of absence, so a probe that
 * could not exercise the binary at all must <strong>throw</strong>, not return nothing. The test
 * for "it ran" is functional: every Percolator run prints its version banner, and the Percolator
 * 3.09 Debian payload on this host prints {@code error while loading shared libraries:
 * libboost_filesystem.so.1.83.0} and exits 127 without one. Reporting that as "not XML-capable" is
 * the exact defect {@code phases/PHASE-05-tool-registry.rst} names, produced by a probe that never
 * got as far as looking.
 *
 * <h2>What this probe deliberately does not establish</h2>
 *
 * <p>Only the two XML capabilities. {@code PSM_TSV_OUTPUT}, {@code PEPTIDE_TSV_OUTPUT}, {@code
 * DECOY_OUTPUT}, {@code WEIGHTS_OUTPUT}, {@code THREAD_OPTION} and {@code SEED_OPTION} are
 * <strong>not probed and are therefore absent</strong>, which is {@code R-TOOL-08} applied rather
 * than an oversight: the manifest claims only the two XML capabilities for Percolator, and a
 * capability this project has not watched a binary demonstrate is not one it may advertise. Adding
 * one is a probe run plus a manifest row, and the phase 05 report records it as residue for the
 * phases that need the tab-separated results.
 */
public final class PercolatorCapabilityProbe {

    /** Where the probe writes its fixture and the binary's output. */
    private static final String WORKSPACE_PREFIX = "cometgui-percolator-probe-";

    private static final String TARGETS_FILE = "targets.pout.xml";
    private static final String DECOYS_FILE = "decoys.pout.xml";

    private final ToolRunner runner;
    private final int targetRows;

    /**
     * Creates the probe, with the fixture size {@code R-PERC-02} fixes.
     *
     * @param runner how one invocation is run and collected; every process in this product goes
     *     through the process service ({@code R-PROC-02})
     * @throws NullPointerException if {@code runner} is {@code null}
     */
    public PercolatorCapabilityProbe(ToolRunner runner) {
        this(runner, SyntheticPin.PROBE_TARGET_ROWS);
    }

    /**
     * Creates the probe with a chosen fixture size.
     *
     * <p><strong>This exists so that the fixture size can be shown to matter.</strong> {@code
     * R-PERC-02} says 64 target and 64 decoy rows is sufficient and 8 and 8 is not, and the only
     * way to demonstrate that claim rather than repeat it is to run this same probe, against the
     * same binary, at both sizes and watch the verdict change -- {@code
     * PercolatorRealBinaryTest.theNegativeControl} does exactly that. The size is a number from the
     * requirement, not a knob for a caller: {@link #PercolatorCapabilityProbe(ToolRunner)} is what
     * the product uses, and a test pins it to {@value SyntheticPin#PROBE_TARGET_ROWS}.
     *
     * @param runner how one invocation is run and collected
     * @param targetRows how many target rows the fixture gets; the same number of decoy rows
     *     follows
     * @throws NullPointerException if {@code runner} is {@code null}
     * @throws IllegalArgumentException if {@code targetRows} is not positive
     */
    public PercolatorCapabilityProbe(ToolRunner runner, int targetRows) {
        this.runner = Objects.requireNonNull(runner, "runner");
        if (targetRows < 1) {
            throw new IllegalArgumentException(
                    "a synthetic PIN needs at least one target row, but was asked for "
                            + targetRows);
        }
        this.targetRows = targetRows;
    }

    /**
     * How many target rows this probe's fixture carries.
     *
     * @return the row count, {@value SyntheticPin#PROBE_TARGET_ROWS} for the product's own probe
     */
    public int targetRows() {
        return targetRows;
    }

    /**
     * Probes what a Percolator build can do, by running it.
     *
     * <p>The signature is {@code org.cometgui.install.probe.CapabilityProber}'s.
     *
     * @param tool which tool this is a build of; must be {@link ToolName#PERCOLATOR}
     * @param version the version the identity stage read from the binary, carried into the
     *     diagnostics so a refusal names the build it was about
     * @param platform the host the probe is running on
     * @param executable the absolute path of the staged executable
     * @return the capabilities the build was <em>observed</em> to have, possibly empty
     * @throws IOException if the build could not be exercised at all -- which is never reported as
     *     an empty capability set
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code tool} is not {@link ToolName#PERCOLATOR}
     */
    public Set<ToolCapability> probe(
            ToolName tool, ToolVersion version, HostPlatform platform, Path executable)
            throws IOException {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(executable, "executable");
        if (tool != ToolName.PERCOLATOR) {
            throw new IllegalArgumentException(
                    "this probe runs Percolator and was asked to probe "
                            + tool.id()
                            + "; a capability of one tool means nothing said of another");
        }
        Path workspace = Files.createTempDirectory(WORKSPACE_PREFIX);
        try {
            return probeIn(workspace, version, executable);
        } finally {
            deleteRecursively(workspace);
        }
    }

    private Set<ToolCapability> probeIn(Path workspace, ToolVersion version, Path executable)
            throws IOException {
        Path pin = SyntheticPin.write(workspace, targetRows, SyntheticPin.PROBE_SEED);
        Set<ToolCapability> observed = EnumSet.noneOf(ToolCapability.class);
        Path targets = workspace.resolve(TARGETS_FILE);
        exercise(executable, workspace, version, List.of("-X", targets.toString(), pin.toString()));
        if (writesDocument(targets, targetRows, false)) {
            observed.add(ToolCapability.XML_OUTPUT);
        }
        Path decoys = workspace.resolve(DECOYS_FILE);
        exercise(
                executable,
                workspace,
                version,
                List.of("-X", decoys.toString(), "-Z", pin.toString()));
        if (writesDocument(decoys, targetRows * 2, true)) {
            observed.add(ToolCapability.XML_DECOY_OUTPUT);
        }
        return Collections.unmodifiableSet(observed);
    }

    /*
     * Runs one invocation and refuses unless the binary demonstrably ran.  The banner is the test:
     * a loader failure prints its own complaint and never reaches Percolator's own code, and
     * treating that as "this build cannot write XML" is the specific defect this phase exists to
     * avoid.
     */
    private void exercise(
            Path executable, Path workspace, ToolVersion version, List<String> arguments)
            throws IOException {
        List<String> argv = new ArrayList<>();
        argv.add(executable.toString());
        argv.addAll(arguments);
        ToolRunOutcome outcome = runner.run(new ToolCommand(argv, workspace, Map.of()));
        if (outcome.timedOut()) {
            throw new IOException(
                    "Percolator "
                            + version.text()
                            + " at "
                            + executable
                            + " did not finish within "
                            + runner.timeout()
                            + ", so this probe established nothing about it; a probe that got no"
                            + " answer has not established that a capability is absent");
        }
        if (!PercolatorBanner.isPresentIn(outcome.errorFirst())) {
            throw new IOException(
                    "Percolator "
                            + version.text()
                            + " at "
                            + executable
                            + " never printed its version banner, so it did not run far enough to"
                            + " be asked what it can do; this is a loadability failure and must not"
                            + " be reported as a missing capability. It exited "
                            + outcome.exitCode().orElse(-1)
                            + " saying: "
                            + outcome.joinedOutput());
        }
    }

    /*
     * THE DOCUMENT IS THE VERDICT, AND THE EXIT CODE IS DELIBERATELY NOT PART OF IT.  An aborted
     * Percolator run exits 1 AND leaves a zero-byte file, so a rule that short-circuited on the
     * exit code would never reach the zero-byte check on the one run this project has ever seen
     * produce a zero-byte file -- a guard that cannot fire is not a guard, which is this project's
     * signature defect.  Reading the file is also strictly the stronger test: 64 psm elements in
     * the percolator_out/15 namespace cannot be produced by a build that cannot write pout XML,
     * whatever it exited with, and comet.linux.exe answering -h with exit 1 and a correct banner is
     * this project's standing reminder that a tool's exit code is not its verdict.
     */
    private static boolean writesDocument(
            Path written, int expectedPsmCount, boolean requireBothDecoys) {
        PoutDocument document;
        try {
            document = PoutDocument.read(written);
        } catch (IOException noUsableDocument) {
            return false;
        }
        if (!document.isPercolatorOutput() || document.psmCount() != expectedPsmCount) {
            return false;
        }
        return !requireBothDecoys || document.hasBothDecoyValues();
    }

    /*
     * Best effort, and deliberately silent: this runs in a finally block, and a failure to tidy a
     * temporary directory must not replace the IOException that says the binary could not be
     * exercised.  A left-behind temporary directory is a nuisance; a lost diagnostic sends somebody
     * to the wrong problem.
     */
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
