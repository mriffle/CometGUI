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

package org.cometgui.domain.run;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.cometgui.domain.project.ProjectId;

/**
 * What a run <em>is</em>: every member of {@code run.json} that is written once and never changes
 * ({@code R-RUN-06}).
 *
 * <p>The identity is the run's id and project, when it was created, which Comet release ran it,
 * every input as recorded when the run started ({@code R-RUN-03}), the archived parameter file and
 * its hashes, the index mode, and how the database reached Comet ({@code R-CMT-04}). Everything
 * that changes while a run executes -- its attempts -- lives beside it in {@link RunDescriptor},
 * never in here, so "the identity did not change" is one {@code equals}.
 *
 * <h2>What the constructor holds true</h2>
 *
 * <ul>
 *   <li>There is at least one spectrum file, the positions are {@code 1..n} in order, and every
 *       base name is the one {@link OutputBaseNames#derive} gives the recorded paths in that order.
 *       A {@code run.json} whose base names disagree with its own inputs is refused when read: it
 *       would send Comet's outputs to names the rule did not choose.
 *   <li>The parameter file is under {@code parameters/}, the directory whose files are written
 *       once.
 *   <li>The Comet release is a non-blank string without control characters.
 *   <li>{@code created} is truncated to milliseconds, the precision {@code run.json} records.
 * </ul>
 *
 * @param runId the run's identifier
 * @param projectId the project the run belongs to
 * @param created when the run was created; also the timestamp in the run directory's name
 * @param cometRelease the Comet release that executes the run, as its version text
 * @param spectra the spectrum files, in input order
 * @param fasta the protein database
 * @param parameters the canonical Comet parameter file, archived in the run
 * @param indexMode whether and how the search uses a prebuilt index
 * @param databaseDelivery how the database reached Comet
 */
public record RunIdentity(
        RunId runId,
        ProjectId projectId,
        Instant created,
        String cometRelease,
        List<SpectrumInput> spectra,
        RecordedInput fasta,
        ArchivedFile parameters,
        IndexMode indexMode,
        DatabaseDelivery databaseDelivery) {

    /**
     * Validates and copies the identity.
     *
     * @throws NullPointerException naming a component or element that is {@code null}
     * @throws IllegalArgumentException naming the member that breaks one of the rules above
     */
    public RunIdentity {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(projectId, "projectId");
        created = Objects.requireNonNull(created, "created").truncatedTo(ChronoUnit.MILLIS);
        Objects.requireNonNull(cometRelease, "cometRelease");
        if (cometRelease.isBlank()) {
            throw new IllegalArgumentException("cometRelease must not be blank");
        }
        for (int index = 0; index < cometRelease.length(); index++) {
            if (Character.isISOControl(cometRelease.charAt(index))) {
                throw new IllegalArgumentException(
                        "cometRelease must not contain a control character");
            }
        }
        spectra = List.copyOf(Objects.requireNonNull(spectra, "spectra"));
        requireConsistentSpectra(spectra);
        Objects.requireNonNull(fasta, "fasta");
        Objects.requireNonNull(parameters, "parameters");
        if (!parameters.path().startsWith(RunLayout.PARAMETERS_DIRECTORY_NAME + "/")) {
            throw new IllegalArgumentException(
                    "the parameter file must be archived under "
                            + RunLayout.PARAMETERS_DIRECTORY_NAME
                            + "/, but was recorded at \""
                            + parameters.path()
                            + "\"");
        }
        Objects.requireNonNull(indexMode, "indexMode");
        Objects.requireNonNull(databaseDelivery, "databaseDelivery");
    }

    private static void requireConsistentSpectra(List<SpectrumInput> spectra) {
        if (spectra.isEmpty()) {
            throw new IllegalArgumentException("a run needs at least one spectrum file");
        }
        List<Path> paths = new ArrayList<>(spectra.size());
        for (int index = 0; index < spectra.size(); index++) {
            SpectrumInput spectrum = spectra.get(index);
            if (spectrum.position() != index + 1) {
                throw new IllegalArgumentException(
                        "spectra["
                                + index
                                + "] has position "
                                + spectrum.position()
                                + ", but positions must run 1, 2, 3 ... in input order");
            }
            paths.add(spectrum.file().path());
        }
        List<OutputBase> expected = OutputBaseNames.derive(paths);
        for (int index = 0; index < spectra.size(); index++) {
            String recorded = spectra.get(index).base();
            String derived = expected.get(index).base();
            if (!recorded.equals(derived)) {
                throw new IllegalArgumentException(
                        "spectra["
                                + index
                                + "] has base name \""
                                + recorded
                                + "\", but the output naming rule gives \""
                                + derived
                                + "\" for these inputs");
            }
        }
    }

    /**
     * The spectrum files, in input order, unmodifiable.
     *
     * @return the spectrum files
     */
    @Override
    public List<SpectrumInput> spectra() {
        return List.copyOf(spectra);
    }

    /**
     * Builds the spectrum entries for a list of recorded files, naming each by {@link
     * OutputBaseNames}.
     *
     * @param files the spectrum files' records, in input order
     * @return one entry per file, position 1 first
     * @throws IllegalArgumentException as {@link OutputBaseNames#derive}
     */
    public static List<SpectrumInput> spectraOf(List<RecordedInput> files) {
        Objects.requireNonNull(files, "files");
        List<Path> paths = new ArrayList<>(files.size());
        for (RecordedInput file : files) {
            paths.add(Objects.requireNonNull(file, "files contains null").path());
        }
        List<OutputBase> bases = OutputBaseNames.derive(paths);
        List<SpectrumInput> spectra = new ArrayList<>(files.size());
        for (int index = 0; index < files.size(); index++) {
            spectra.add(new SpectrumInput(index + 1, files.get(index), bases.get(index).base()));
        }
        return List.copyOf(spectra);
    }

    /**
     * The name of the first identity member in which {@code other} differs from this one, or empty
     * if none does.
     *
     * @param other the identity to compare with
     * @return the member's name, for example {@code fasta} or {@code spectra[1]}
     */
    String firstDifference(RunIdentity other) {
        if (!runId.equals(other.runId)) {
            return "runId";
        }
        if (!projectId.equals(other.projectId)) {
            return "projectId";
        }
        if (!created.equals(other.created)) {
            return "created";
        }
        if (!cometRelease.equals(other.cometRelease)) {
            return "cometRelease";
        }
        if (spectra.size() != other.spectra.size()) {
            return "spectra";
        }
        for (int index = 0; index < spectra.size(); index++) {
            if (!spectra.get(index).equals(other.spectra.get(index))) {
                return "spectra[" + index + "]";
            }
        }
        if (!fasta.equals(other.fasta)) {
            return "fasta";
        }
        if (!parameters.equals(other.parameters)) {
            return "parameters";
        }
        if (indexMode != other.indexMode) {
            return "indexMode";
        }
        if (databaseDelivery != other.databaseDelivery) {
            return "databaseDelivery";
        }
        return "";
    }
}
