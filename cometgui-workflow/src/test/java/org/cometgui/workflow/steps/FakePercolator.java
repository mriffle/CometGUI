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

package org.cometgui.workflow.steps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.resolution.PercolatorResolver;

/**
 * A stand-in Percolator for the tests that need no real binary: a POSIX shell script, run through
 * the real process service, that writes a fixed small document to each file its command line names
 * -- a two-row result table, a two-split weights file, a two-PSM pout XML -- and can be told to
 * misbehave. It uses shell built-ins only, because the command's environment is exactly {@code
 * LANG=C.UTF-8}, with no {@code PATH}.
 *
 * <p>Its capabilities are whatever the offer declares as observed, hand-typed per test: that is
 * what lets a test take one capability away and watch the command and provenance follow.
 */
final class FakePercolator {

    /** Every Percolator capability, hand-typed. */
    static final Set<ToolCapability> EVERY =
            Set.of(
                    ToolCapability.XML_OUTPUT,
                    ToolCapability.XML_DECOY_OUTPUT,
                    ToolCapability.PSM_TSV_OUTPUT,
                    ToolCapability.PEPTIDE_TSV_OUTPUT,
                    ToolCapability.DECOY_OUTPUT,
                    ToolCapability.WEIGHTS_OUTPUT,
                    ToolCapability.THREAD_OPTION,
                    ToolCapability.SEED_OPTION,
                    ToolCapability.TEST_FDR_OPTION,
                    ToolCapability.TRAIN_FDR_OPTION,
                    ToolCapability.MAX_ITERATIONS_OPTION);

    /** The table every table file gets: two rows, one known q-value and one unparsable. */
    static final String TABLE =
            "PSMId\\tscore\\tq-value\\tposterior_error_prob\\tpeptide\\tproteinIds\\n"
                    + "p1\\t2.5\\t0.001\\t0.0001\\tK.PEPTIDEK.R\\tsp|P1|A\\n"
                    + "p2\\t1.0\\tNaN\\t0.5\\tK.KEDITPEP.R\\tsp|P2|B\\n";

    /** The weights file: one comment line, two splits of three features. */
    static final String WEIGHTS =
            "# a stand-in weights file\\n"
                    + "feat1\\tfeat2\\tm0\\n0.1\\t0.2\\t-1.0\\n0.3\\t0.4\\t-2.0\\n"
                    + "feat1\\tfeat2\\tm0\\n0.15\\t0.25\\t-1.5\\n0.35\\t0.45\\t-2.5\\n";

    /** The pout XML: two PSMs and one peptide, in Percolator's namespace. */
    static final String XML =
            "<?xml version=\"1.0\"?>\\n<percolator_output"
                    + " xmlns=\"http://per-colator.com/percolator_out/15\"><psms><psm/><psm/></psms>"
                    + "<peptides><peptide/></peptides></percolator_output>\\n";

    private FakePercolator() {}

    /**
     * How the stand-in behaves.
     *
     * @param exitCode what it exits with
     * @param skipOption an option whose file it does not write, or empty
     * @param emptyOption an option whose file it writes empty, or empty
     * @param extraFile a file it also writes in its working directory, or empty
     * @param table the table text it writes, a printf format
     */
    record Behaviour(
            int exitCode, String skipOption, String emptyOption, String extraFile, String table) {

        static Behaviour normal() {
            return new Behaviour(0, "", "", "", TABLE);
        }

        Behaviour exiting(int code) {
            return new Behaviour(code, skipOption, emptyOption, extraFile, table);
        }

        Behaviour skipping(String option) {
            return new Behaviour(exitCode, option, emptyOption, extraFile, table);
        }

        Behaviour emptying(String option) {
            return new Behaviour(exitCode, skipOption, option, extraFile, table);
        }

        Behaviour alsoWriting(String name) {
            return new Behaviour(exitCode, skipOption, emptyOption, name, table);
        }

        Behaviour withTable(String text) {
            return new Behaviour(exitCode, skipOption, emptyOption, extraFile, text);
        }
    }

    /**
     * Writes the stand-in.
     *
     * @param file where it goes
     * @param behaviour how it behaves
     * @return the executable
     */
    static Path write(Path file, Behaviour behaviour) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("#!/bin/sh");
        lines.add("table='" + behaviour.table() + "'");
        lines.add("weights='" + WEIGHTS + "'");
        lines.add("xml='" + XML + "'");
        lines.add("while [ $# -gt 1 ]; do");
        lines.add("  case \"$1\" in");
        if (!behaviour.skipOption().isEmpty()) {
            lines.add("    " + behaviour.skipOption() + ") : ;;");
        }
        if (!behaviour.emptyOption().isEmpty()) {
            lines.add("    " + behaviour.emptyOption() + ") : > \"$2\" ;;");
        }
        lines.add(
                "    --results-psms|--results-peptides|--decoy-results-psms"
                        + "|--decoy-results-peptides) printf \"$table\" > \"$2\" ;;");
        lines.add("    --weights) printf \"$weights\" > \"$2\" ;;");
        lines.add("    -X) printf \"$xml\" > \"$2\" ;;");
        lines.add("  esac");
        lines.add("  shift 2");
        lines.add("done");
        if (!behaviour.extraFile().isEmpty()) {
            lines.add("printf 'extra\\n' > '" + behaviour.extraFile() + "'");
        }
        lines.add("exit " + behaviour.exitCode());
        Files.createDirectories(RealComet.parentOf(file));
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.US_ASCII);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwx------"));
        return file;
    }

    /**
     * An installed offer for the stand-in, with these capabilities observed.
     *
     * @param executable the stand-in
     * @param version the version it claims
     * @param origin managed or local
     * @param capabilities its observed capabilities
     * @param advisories its advisories
     * @return the offer
     */
    static ToolOffer offer(
            Path executable,
            String version,
            ToolOrigin origin,
            Set<ToolCapability> capabilities,
            List<ToolAdvisory> advisories) {
        List<DeclaredCapability> declared = new ArrayList<>();
        for (ToolCapability capability : ToolCapability.values()) {
            if (!capabilities.contains(capability)) {
                continue;
            }
            declared.add(
                    new DeclaredCapability(
                            capability,
                            CapabilityEvidence.OBSERVED_BY_EXECUTION,
                            "observed by a test's stand-in"));
        }
        return new ToolOffer(
                ToolName.PERCOLATOR,
                ToolVersion.parse(version),
                origin,
                ToolInstallState.INSTALLED,
                declared,
                advisories,
                Optional.empty(),
                Optional.of(executable),
                origin == ToolOrigin.MANAGED ? OptionalLong.of(1000) : OptionalLong.empty());
    }

    /**
     * The choice of one offer, resolved among the given ones for the given stages.
     *
     * @param selected the offer that runs
     * @param offers every offer resolution sees
     * @param stages the enabled stages
     * @param settings the settings
     * @return the choice
     */
    static PercolatorChoice choice(
            ToolOffer selected,
            List<ToolOffer> offers,
            Set<DownstreamStage> stages,
            PercolatorSettings settings)
            throws IOException {
        Path executable = selected.installedPath().orElseThrow();
        return new PercolatorChoice(
                new PercolatorSelection(selected, executable, RealComet.sha256(executable)),
                settings,
                stages,
                PercolatorResolver.resolve(offers, stages));
    }
}
