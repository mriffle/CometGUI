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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.ToolCapability;

/**
 * Builds the command of one Percolator run over a merged PIN from the build's <em>probed</em>
 * capabilities ({@code R-PERC-06}, design decisions P9-7 and P9-9).
 *
 * <p>Pure: nothing here touches the disk or starts a process. The workflow checks the merged PIN
 * ({@link PercolatorPinCheck}) and runs the command through the process service.
 *
 * <h2>Capability, never version</h2>
 *
 * <p>Every option comes from {@link PercolatorOption}, and every one goes through a single test --
 * is its {@link PercolatorOption#capability()} in the request's probed set? -- so no option can be
 * passed without the capability whose probe run watched the build accept it, and no decision here
 * reads a version number: this class never sees one (P9-2). 3.09 gets no {@code -X} because its
 * probe did not observe {@code XML_OUTPUT}, not because it is 3.09.
 *
 * <h2>The argument array</h2>
 *
 * <pre>
 *   percolator
 *     --results-psms           {out}/psms.tsv             always (PSM_TSV_OUTPUT required)
 *     --results-peptides       {out}/peptides.tsv         always (PEPTIDE_TSV_OUTPUT required)
 *     --decoy-results-psms     {out}/decoy-psms.tsv       with DECOY_OUTPUT
 *     --decoy-results-peptides {out}/decoy-peptides.tsv   with DECOY_OUTPUT
 *     --weights                {out}/weights.txt          with WEIGHTS_OUTPUT (R-PERC-08)
 *     -X                       {out}/pout.xml             with XML_OUTPUT, and only when an
 *                                                         enabled stage needs XML
 *     --seed v  --num-threads v  --testFDR v  --trainFDR v  --maxiter v
 *                                                         each with its own capability
 *     --no-analytics                                      with NO_ANALYTICS_OPTION, always
 *     {merged PIN}
 * </pre>
 *
 * <p>Each option and its value are two elements; the merged PIN is last, the shape of Percolator's
 * usage line. The file names are {@link PercolatorArtefact}'s.
 *
 * <ul>
 *   <li><strong>The target tables are the run's purpose.</strong> A build without {@code
 *       PSM_TSV_OUTPUT} or {@code PEPTIDE_TSV_OUTPUT} cannot produce the results the rest of the
 *       product reads, so the command is <em>refused</em> with the missing capabilities named
 *       (P9-6), rather than built to fail later.
 *   <li><strong>Everything else requested and not supported is left out and said so</strong>
 *       ({@link PercolatorCommand#notEmitted()}): a valued option whose capability is absent; the
 *       decoy tables without {@code DECOY_OUTPUT}; the weights file without {@code WEIGHTS_OUTPUT},
 *       which {@code R-PERC-08} requires to be observable so that the run can record its fallback
 *       warning; and pout XML that an enabled stage needs from a build without {@code XML_OUTPUT}.
 *   <li><strong>pout XML is asked for only when it is needed</strong>: an XML-capable build with no
 *       enabled stage needing XML gets no {@code -X} and is not said to lack anything.
 *   <li><strong>No {@code -Z}.</strong> Decoys in the pout XML are never requested. Phase 00 ran
 *       every combination through the Limelight converter ({@code
 *       docs/feasibility/scientific-path.rst}, {@code R-LL-05}): {@code -X} alone converts; {@code
 *       -X -Z} without the converter's {@code --import-decoys} is a hard failure; and {@code
 *       --import-decoys} cannot work with Comet's internal decoys, which are the product's
 *       configuration. The decoy results are kept in the decoy tables instead. If Phase 12 finds a
 *       configuration that needs decoys in the XML, that is one more request flag and one more
 *       capability test here, against {@code XML_DECOY_OUTPUT}.
 * </ul>
 *
 * <h2>The working directory and the environment ({@code R-PROC-04})</h2>
 *
 * <p>The working directory is the output directory, so anything Percolator might write by a
 * relative name lands among the run's Percolator outputs rather than anywhere else; every path in
 * the argument array is absolute anyway. The environment is constructed, never inherited: exactly
 * {@link #ENVIRONMENT}, {@code LANG=C.UTF-8}, as Comet's is. Percolator 3.06.5, 3.07.1 and 3.09 all
 * run correctly with an <em>empty</em> environment -- the capability probe runs every build that
 * way -- so {@code LANG} is not there because Percolator fails without it. It is there to pin the C
 * library's locale to one whose decimal separator is a point, whatever the user's shell has, since
 * Percolator both parses {@code --testFDR 0.01} and prints the result tables' numbers through it;
 * and because Phase 03 measured that a process given no {@code LANG} cannot decode a non-ASCII
 * path. Nothing else is added: no {@code PATH}, {@code HOME} or {@code TMPDIR} has been needed.
 * Windows and macOS have not been run.
 *
 * <h2>{@code --no-analytics} ({@code D-013})</h2>
 *
 * <p>Percolator posts usage analytics to Google on every run unless it is told not to, and the
 * owner decided on 2026-10-08 that CometGUI always tells it not to. So {@code --no-analytics} is
 * passed on <em>every</em> run of a build whose probe observed {@code NO_ANALYTICS_OPTION} -- it is
 * not a setting and no request can turn it off -- as the last option, just before the merged PIN.
 * Like every other option it goes through the one capability test, never a version number. A build
 * whose probe did not observe it is <strong>not refused</strong>: analytics are not part of the
 * scientific result, and refusing would leave such a build unusable for no gain. Instead the
 * omission is recorded ({@link PercolatorCommand#notEmitted()}, and from there provenance) with a
 * sentence saying that this run could not switch analytics off.
 */
public final class PercolatorCommands {

    /** The constructed environment every Percolator run gets: see the class documentation. */
    public static final Map<String, String> ENVIRONMENT = Map.of("LANG", "C.UTF-8");

    /** The rule every omission ends with. */
    private static final String NEVER_PASSED =
            ", and an option the build was not observed to accept is never passed (R-PERC-06)";

    private PercolatorCommands() {}

    /**
     * Builds the command.
     *
     * @param request the run
     * @return the command, the artefacts it will write and what was left out
     * @throws PercolatorRefusedException if the build lacks {@code PSM_TSV_OUTPUT} or {@code
     *     PEPTIDE_TSV_OUTPUT}, naming each missing one
     * @throws NullPointerException if {@code request} is {@code null}
     */
    public static PercolatorCommand build(PercolatorRequest request)
            throws PercolatorRefusedException {
        Objects.requireNonNull(request, "request");
        Builder builder = new Builder(request);
        builder.refuseWithoutTargetTables();
        builder.file(PercolatorArtefact.TARGET_PSMS);
        builder.file(PercolatorArtefact.TARGET_PEPTIDES);
        builder.optionalFile(PercolatorArtefact.DECOY_PSMS, "the decoy PSM table", "");
        builder.optionalFile(PercolatorArtefact.DECOY_PEPTIDES, "the decoy peptide table", "");
        builder.optionalFile(
                PercolatorArtefact.WEIGHTS,
                "the learned weights file",
                "; the weights must come from R-PERC-08's fallback, which the run records as a"
                        + " provenance warning");
        if (request.xmlNeeded()) {
            builder.optionalFile(
                    PercolatorArtefact.POUT_XML,
                    "the pout XML an enabled downstream stage needs",
                    "");
        }
        for (PercolatorOption option : PercolatorRequest.VALUED_OPTIONS) {
            String value = request.values().get(option);
            if (value != null) {
                builder.value(option, value);
            }
        }
        builder.noAnalytics();
        return builder.finish();
    }

    /** One build in progress. */
    private static final class Builder {

        private final PercolatorRequest request;
        private final Set<ToolCapability> probed;
        private final List<String> argv = new ArrayList<>();
        private final Map<PercolatorArtefact, Path> artefacts =
                new EnumMap<>(PercolatorArtefact.class);
        private final List<NotEmitted> notEmitted = new ArrayList<>();

        Builder(PercolatorRequest request) {
            this.request = request;
            this.probed = request.capabilities();
            argv.add(request.executable().toString());
        }

        /* THE ONE TEST every option passes through: its own capability, probed. */
        boolean accepts(PercolatorOption option) {
            return probed.contains(option.capability());
        }

        void refuseWithoutTargetTables() throws PercolatorRefusedException {
            List<String> missing = new ArrayList<>();
            for (PercolatorArtefact table :
                    List.of(PercolatorArtefact.TARGET_PSMS, PercolatorArtefact.TARGET_PEPTIDES)) {
                if (!accepts(table.option())) {
                    missing.add(
                            table.option().capability().id()
                                    + " ("
                                    + table.option().spelling()
                                    + ")");
                }
            }
            if (!missing.isEmpty()) {
                throw new PercolatorRefusedException(
                        "Percolator was not started: the build at "
                                + request.executable()
                                + " cannot write the target results this run reads -- its probed"
                                + " capabilities do not include "
                                + String.join(" or ", missing)
                                + ". Choose a Percolator build whose probe observed both"
                                + " PSM_TSV_OUTPUT and PEPTIDE_TSV_OUTPUT",
                        null,
                        null);
            }
        }

        void file(PercolatorArtefact artefact) {
            Path path = request.outputDirectory().resolve(artefact.fileName());
            argv.add(artefact.option().spelling());
            argv.add(path.toString());
            artefacts.put(artefact, path);
        }

        void optionalFile(PercolatorArtefact artefact, String what, String consequence) {
            if (accepts(artefact.option())) {
                file(artefact);
            } else {
                notEmitted.add(
                        new NotEmitted(
                                artefact.option(),
                                what
                                        + " ("
                                        + artefact.option().spelling()
                                        + ") was not requested: the build's probed capabilities"
                                        + " do not include "
                                        + artefact.option().capability().id()
                                        + NEVER_PASSED
                                        + consequence));
            }
        }

        void value(PercolatorOption option, String value) {
            if (accepts(option)) {
                argv.add(option.spelling());
                argv.add(value);
            } else {
                notEmitted.add(
                        new NotEmitted(
                                option,
                                option.spelling()
                                        + " "
                                        + value
                                        + " was requested and not passed, so Percolator uses its"
                                        + " own default: the build's probed capabilities do not"
                                        + " include "
                                        + option.capability().id()
                                        + NEVER_PASSED));
            }
        }

        /* D-013: always, when the build accepts it; said so when it does not, never refused. */
        void noAnalytics() {
            PercolatorOption option = PercolatorOption.NO_ANALYTICS;
            if (accepts(option)) {
                argv.add(option.spelling());
            } else {
                notEmitted.add(
                        new NotEmitted(
                                option,
                                option.spelling()
                                        + " was not passed, so this Percolator may post usage"
                                        + " analytics while it runs: the build's probed"
                                        + " capabilities do not include "
                                        + option.capability().id()
                                        + NEVER_PASSED));
            }
        }

        PercolatorCommand finish() {
            argv.add(request.mergedPin().toString());
            return new PercolatorCommand(
                    new ToolCommand(argv, request.outputDirectory(), ENVIRONMENT),
                    artefacts,
                    notEmitted);
        }
    }
}
