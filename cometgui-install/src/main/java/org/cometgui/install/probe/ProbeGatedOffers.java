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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.cometgui.domain.tools.LoaderDiagnostic;
import org.cometgui.domain.tools.ProbeFailureKind;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.registry.ArtefactSelection;

/**
 * {@code R-TOOL-06}'s last sentence, as a value: <em>a tool that fails loadability shall never be
 * offered for selection.</em>
 *
 * <h2>The invariant this class exists to hold</h2>
 *
 * <p><strong>An offer is withheld only when a specific unmet floor or a specific loader failure can
 * be named. Absence of information is never a refusal, and it is never an approval either.</strong>
 * There are exactly two ways an artefact is refused here, and each carries the {@link
 * LoaderDiagnostic} that says why:
 *
 * <ol>
 *   <li>the advance check named a floor this host does not meet ({@link HostRequirementCheck}),
 *       which saves a download and a failure the user cannot act on;
 *   <li>the binary was run and did not start.
 * </ol>
 *
 * <p>A floor that could not be measured -- a macOS release on a machine nothing reads one from, a
 * Windows DLL on a machine that is not Windows, a {@code GLIBCXX} floor where no {@code libstdc++}
 * was found -- produces {@link HostRequirementVerdict.Status#UNDETERMINED}, and the artefact
 * <strong>is</strong> offered, because {@code R-PLAT-02} says compatibility is established by
 * executing the binary. {@code ProbeGatedOffersTest} grades exactly that: with the host's C++
 * runtime unknown and a {@code GLIBCXX} floor declared, the offer stands and the probe decides.
 */
public final class ProbeGatedOffers {

    /**
     * Whether one artefact's binary starts on this host.
     *
     * <p>The seam is here rather than the whole probe, because deciding the offered set and running
     * a binary are different jobs and the test for one should not need the other.
     */
    @FunctionalInterface
    public interface LoadabilityCheck {

        /**
         * Runs the artefact's binary and says whether it refused to start.
         *
         * @param record the artefact to run
         * @return the diagnostic if it did not start, or empty if it did
         * @throws IOException if the binary cannot be reached at all
         */
        Optional<LoaderDiagnostic> refusalFor(ArtefactRecord record) throws IOException;
    }

    /**
     * One artefact that will not be offered, and the reason a user is shown.
     *
     * @param artefact the artefact
     * @param diagnostic why it is not offered
     */
    public record Refusal(ArtefactRecord artefact, LoaderDiagnostic diagnostic) {

        /**
         * Validates the refusal.
         *
         * @throws NullPointerException if either component is {@code null}
         */
        public Refusal {
            Objects.requireNonNull(artefact, "artefact");
            Objects.requireNonNull(diagnostic, "diagnostic");
        }
    }

    /**
     * What may be offered on this host and what may not.
     *
     * @param offered the artefacts that may be presented for selection, in the order they were
     *     given
     * @param refused the ones that may not, each with its diagnostic
     */
    public record Decision(List<ArtefactRecord> offered, List<Refusal> refused) {

        /**
         * Validates the decision and takes immutable copies of both lists.
         *
         * @throws NullPointerException if either component is {@code null}
         */
        public Decision {
            offered = List.copyOf(Objects.requireNonNull(offered, "offered"));
            refused = List.copyOf(Objects.requireNonNull(refused, "refused"));
        }

        /**
         * The artefacts that may be offered, immutable.
         *
         * @return the offers, possibly empty
         */
        @Override
        public List<ArtefactRecord> offered() {
            return List.copyOf(offered);
        }

        /**
         * The artefacts that may not be offered, immutable.
         *
         * @return the refusals, possibly empty
         */
        @Override
        public List<Refusal> refused() {
            return List.copyOf(refused);
        }
    }

    private final HostRuntimeVersions versions;
    private final Function<ArtefactRecord, List<String>> alternatives;

    /**
     * Creates the gate.
     *
     * @param versions what this host's runtimes were established to be
     * @param alternatives what to offer instead of a refused artefact, usually {@link
     *     ManifestAlternatives#forArtefact}
     * @throws NullPointerException if either argument is {@code null}
     */
    public ProbeGatedOffers(
            HostRuntimeVersions versions, Function<ArtefactRecord, List<String>> alternatives) {
        this.versions = Objects.requireNonNull(versions, "versions");
        this.alternatives = Objects.requireNonNull(alternatives, "alternatives");
    }

    /**
     * Decides which of the manifest's selections for this host may be offered.
     *
     * @param candidates what the manifest selected for this host
     * @param check runs a candidate's binary
     * @return the offered and the refused
     * @throws IOException if a candidate's binary cannot be reached
     * @throws NullPointerException if either argument is {@code null}
     */
    public Decision decide(List<ArtefactSelection> candidates, LoadabilityCheck check)
            throws IOException {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(check, "check");
        List<ArtefactRecord> offered = new ArrayList<>();
        List<Refusal> refused = new ArrayList<>();
        for (ArtefactSelection candidate : candidates) {
            ArtefactRecord record = candidate.artefact();
            Optional<LoaderDiagnostic> refusal = refusalFor(record, check);
            if (refusal.isPresent()) {
                refused.add(new Refusal(record, refusal.get()));
            } else {
                offered.add(record);
            }
        }
        return new Decision(offered, refused);
    }

    private Optional<LoaderDiagnostic> refusalFor(ArtefactRecord record, LoadabilityCheck check)
            throws IOException {
        HostRequirementVerdict verdict =
                HostRequirementCheck.check(record.minimumHostRequirements(), versions);
        if (verdict.isRefusal()) {
            return Optional.of(advanceRefusal(record, verdict));
        }
        return check.refusalFor(record);
    }

    /*
     * MISSING_SYMBOL_VERSION rather than a kind of its own: what the advance check established is
     * precisely what the loader would have said -- this host's libc.so.6 or libstdc++.so.6 does not
     * provide a symbol version this build needs -- and inventing a second kind for the same fact
     * would make the Tool Manager render one failure two ways.
     */
    private LoaderDiagnostic advanceRefusal(ArtefactRecord record, HostRequirementVerdict verdict) {
        return new LoaderDiagnostic(
                ProbeFailureKind.MISSING_SYMBOL_VERSION,
                verdict.objectName().orElseThrow(),
                verdict.requiredVersion(),
                verdict.availableVersion(),
                alternatives.apply(record));
    }
}
