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
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.ports.ToolCommand;

/**
 * One Percolator run, built: the command to launch, the files it will write, and what was asked for
 * and left out.
 *
 * @param command the argument array, working directory and constructed environment
 * @param artefacts every file the command asks Percolator to write, by role: exactly these, so a
 *     run whose map holds no {@link PercolatorArtefact#POUT_XML} expects no XML
 * @param notEmitted every requested option the build lacks the capability for, with the reason, in
 *     option order
 */
public record PercolatorCommand(
        ToolCommand command, Map<PercolatorArtefact, Path> artefacts, List<NotEmitted> notEmitted) {

    /**
     * Validates the record and copies both collections.
     *
     * @throws NullPointerException if a component, a key or a value is {@code null}
     */
    public PercolatorCommand {
        Objects.requireNonNull(command, "command");
        Map<PercolatorArtefact, Path> written = new EnumMap<>(PercolatorArtefact.class);
        for (Map.Entry<PercolatorArtefact, Path> entry :
                Objects.requireNonNull(artefacts, "artefacts").entrySet()) {
            written.put(
                    Objects.requireNonNull(entry.getKey(), "an artefact"),
                    Objects.requireNonNull(entry.getValue(), "an artefact's path"));
        }
        artefacts = Collections.unmodifiableMap(written);
        notEmitted = List.copyOf(notEmitted);
    }

    /**
     * Every file the command asks Percolator to write.
     *
     * @return role to path, in role order, immutable
     */
    @Override
    public Map<PercolatorArtefact, Path> artefacts() {
        Map<PercolatorArtefact, Path> copy = new EnumMap<>(PercolatorArtefact.class);
        copy.putAll(artefacts);
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Every requested option left out, with its reason.
     *
     * @return the omissions, immutable
     */
    @Override
    public List<NotEmitted> notEmitted() {
        return List.copyOf(notEmitted);
    }

    /**
     * Whether the run writes pout XML.
     *
     * @return {@code true} only when {@link PercolatorArtefact#POUT_XML} is requested
     */
    public boolean writesXml() {
        return artefacts.containsKey(PercolatorArtefact.POUT_XML);
    }

    /**
     * Whether the run writes the weights file. When it does not, {@code R-PERC-08}'s fallback
     * applies and the run records a provenance warning; {@link #omission} gives the reason.
     *
     * @return {@code true} only when {@link PercolatorArtefact#WEIGHTS} is requested
     */
    public boolean writesWeights() {
        return artefacts.containsKey(PercolatorArtefact.WEIGHTS);
    }

    /**
     * Why an option was left out.
     *
     * @param option the option
     * @return the omission, or empty when the option was passed or never requested
     */
    public Optional<NotEmitted> omission(PercolatorOption option) {
        Objects.requireNonNull(option, "option");
        return notEmitted.stream().filter(left -> left.option() == option).findFirst();
    }
}
