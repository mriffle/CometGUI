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

package org.cometgui.params.comet.presets;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.migration.VersionConversion;

/**
 * The compatibility check of a preset against the Comet version of the parameter set it is applied
 * to (<em>Presets</em>: "applying them to a different version shall run a compatibility check").
 *
 * <p>There is <strong>one entry per delta</strong> of the preset, in the preset's order, so no
 * delta can go missing between the preset and its diff: a delta the target can take is {@link
 * VersionConversion.Status#SAME} or {@link VersionConversion.Status#CONVERTED} (its syntax changed
 * between the versions and it was rewritten, which is reported); one it cannot is {@link
 * VersionConversion.Status#NOT_IN_TARGET} or {@link VersionConversion.Status#NOT_CONVERTIBLE}, and
 * is listed by {@link #problems()} -- reported, never silently dropped. For a preset made for the
 * target version every entry is {@code SAME}.
 *
 * @param presetVersion the Comet version the preset was made against
 * @param targetVersion the Comet version of the parameter set
 * @param entries one result per delta, in the preset's order
 */
public record CompatibilityReport(
        ToolVersion presetVersion,
        ToolVersion targetVersion,
        List<VersionConversion.Result> entries) {

    /**
     * Validates the components and takes an immutable copy.
     *
     * @throws IllegalArgumentException if a parameter has two entries
     */
    public CompatibilityReport {
        Objects.requireNonNull(presetVersion, "presetVersion");
        Objects.requireNonNull(targetVersion, "targetVersion");
        entries = List.copyOf(entries);
        if (entries.stream().map(VersionConversion.Result::parameter).distinct().count()
                != entries.size()) {
            throw new IllegalArgumentException("a delta has two compatibility entries");
        }
    }

    /**
     * The entries, immutable.
     *
     * @return one result per delta
     */
    @Override
    public List<VersionConversion.Result> entries() {
        return List.copyOf(entries);
    }

    /**
     * The entry for one delta.
     *
     * @param parameter the parameter name
     * @return its result, or empty if the preset does not set the parameter
     */
    public Optional<VersionConversion.Result> entry(String parameter) {
        Objects.requireNonNull(parameter, "parameter");
        return entries.stream().filter(e -> e.parameter().equals(parameter)).findFirst();
    }

    /**
     * The deltas the target cannot take: every one is reported to the user.
     *
     * @return the entries that are not usable, in the preset's order
     */
    public List<VersionConversion.Result> problems() {
        return entries.stream().filter(e -> !e.status().usable()).toList();
    }

    /**
     * The deltas whose syntax changed and were rewritten for the target.
     *
     * @return the converted entries, in the preset's order
     */
    public List<VersionConversion.Result> converted() {
        return entries.stream()
                .filter(e -> e.status() == VersionConversion.Status.CONVERTED)
                .toList();
    }

    /**
     * Whether every delta can be taken as written.
     *
     * @return {@code true} if there is no problem and no conversion
     */
    public boolean isClean() {
        return problems().isEmpty() && converted().isEmpty();
    }
}
