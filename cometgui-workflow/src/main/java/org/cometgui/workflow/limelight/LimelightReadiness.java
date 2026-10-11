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

package org.cometgui.workflow.limelight;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.run.RunId;
import org.cometgui.workflow.storage.LimelightSettings;

/**
 * Whether a run can be converted to Limelight XML now, and if not, every reason why -- the one
 * answer the Limelight section (unit 6) and the compatibility view (unit 4) read, from {@link
 * LimelightConversions#check}.
 *
 * <p>Besides the refusals it carries what the section shows read-only: the resolved files the
 * converter would be given, where the output would go, and whether decoy import can be offered at
 * all (design decision P12-7: decided from the run's own Percolator output, before anyone asks).
 *
 * @param runId the run
 * @param refusals every prerequisite that is not met, in the order they are checked -- which is
 *     {@link LimelightPrerequisite} order; empty when the run can be converted
 * @param settings the settings the answer was given for
 * @param cometParams the run's canonical {@code comet.params} ({@code -c})
 * @param percolatorXml the run's Percolator XML ({@code -p} is it, or a copy of it whose PSM ids
 *     name the pepXML relative to the pepXML directory)
 * @param pepXmlDirectory the pepXML directory ({@code -d}): the {@code outputs/comet/} of the run
 *     whose search produced the results -- the run itself, or for a Percolator rerun the run it was
 *     derived from
 * @param fasta the FASTA the run recorded ({@code -f})
 * @param output where the Limelight XML would be written, when that is a usable new file
 * @param decoyImportUnavailable why decoy import cannot be offered for this run, or empty when it
 *     can
 * @param converter the installed, verified converter, when there is one
 * @param compatibility whether the run's own Percolator wrote the XML the converter reads, and what
 *     is offered when it did not -- the {@link LimelightPrerequisite#PERCOLATOR_XML} refusal is its
 *     explanation (P12-9)
 */
public record LimelightReadiness(
        RunId runId,
        List<LimelightRefusal> refusals,
        LimelightSettings settings,
        Path cometParams,
        Path percolatorXml,
        Path pepXmlDirectory,
        Path fasta,
        Optional<Path> output,
        Optional<String> decoyImportUnavailable,
        Optional<LimelightConverterInstallation> converter,
        LimelightCompatibility compatibility) {

    /**
     * Validates the answer and copies its refusals.
     *
     * @throws NullPointerException if a component or a refusal is {@code null}
     */
    public LimelightReadiness {
        Objects.requireNonNull(runId, "runId");
        refusals = List.copyOf(Objects.requireNonNull(refusals, "refusals"));
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(cometParams, "cometParams");
        Objects.requireNonNull(percolatorXml, "percolatorXml");
        Objects.requireNonNull(pepXmlDirectory, "pepXmlDirectory");
        Objects.requireNonNull(fasta, "fasta");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(decoyImportUnavailable, "decoyImportUnavailable");
        Objects.requireNonNull(converter, "converter");
        Objects.requireNonNull(compatibility, "compatibility");
    }

    /**
     * Every prerequisite that is not met.
     *
     * @return the refusals, immutable
     */
    @Override
    public List<LimelightRefusal> refusals() {
        return List.copyOf(refusals);
    }

    /**
     * Whether the run can be converted now.
     *
     * @return {@code true} exactly when nothing is refused
     */
    public boolean ready() {
        return refusals.isEmpty();
    }

    /**
     * The refusal of one prerequisite, if it is not met.
     *
     * @param prerequisite the prerequisite
     * @return its refusal, or empty when it is met
     */
    public Optional<LimelightRefusal> refusal(LimelightPrerequisite prerequisite) {
        Objects.requireNonNull(prerequisite, "prerequisite");
        return refusals.stream()
                .filter(refusal -> refusal.prerequisite() == prerequisite)
                .findFirst();
    }
}
