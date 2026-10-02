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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.cometgui.params.comet.migration.VersionConversion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.validation.CometValidator;

/**
 * What a preset would change in a parameter set, before anything changes ({@code AC-PAR-08}).
 *
 * <p>{@link #of} runs the compatibility check of the preset against the set's Comet version, then
 * makes one {@link DiffRow} -- parameter, current text, preset text -- for every delta the version
 * can take whose text differs from the set's, in schema order. A delta whose value the set already
 * has makes no row. Nothing is changed by building the diff: the set is immutable, and only {@link
 * #applyAll()} or {@link #applySelected(Collection)} produce a new one, in which exactly the
 * applied rows hold the preset's text with origin {@link ValueOrigin#PRESET}, validated.
 */
public final class PresetDiff {

    private final CometParameters base;

    private final Preset preset;

    private final List<DiffRow> rows;

    private final CompatibilityReport compatibility;

    private PresetDiff(
            CometParameters base,
            Preset preset,
            List<DiffRow> rows,
            CompatibilityReport compatibility) {
        this.base = base;
        this.preset = preset;
        this.rows = List.copyOf(rows);
        this.compatibility = compatibility;
    }

    /**
     * The diff between a parameter set and a preset.
     *
     * @param current the parameter set
     * @param preset the preset
     * @return the diff; the set is not changed
     * @throws IllegalArgumentException if the metadata was not curated against the preset's
     *     version, or the preset names a parameter its own version does not have
     */
    public static PresetDiff of(CometParameters current, Preset preset) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(preset, "preset");
        VersionConversion conversion =
                VersionConversion.between(
                        current.metadata(), preset.cometVersion(), current.version());
        List<VersionConversion.Result> results = new ArrayList<>();
        for (PresetDelta delta : preset.deltas()) {
            results.add(conversion.convert(delta.parameter(), delta.value()));
        }
        CompatibilityReport compatibility =
                new CompatibilityReport(preset.cometVersion(), current.version(), results);
        Map<String, VersionConversion.Result> usable =
                results.stream()
                        .filter(r -> r.status().usable())
                        .collect(
                                Collectors.toMap(
                                        VersionConversion.Result::parameter, Function.identity()));
        List<DiffRow> rows = new ArrayList<>();
        for (ParameterEntry entry : current.entries()) {
            VersionConversion.Result result = usable.get(entry.name());
            if (result == null) {
                continue;
            }
            String now = current.text(entry.name());
            String proposed = result.targetText().orElseThrow();
            if (!now.equals(proposed)) {
                rows.add(
                        new DiffRow(
                                DiffRow.Kind.PARAMETER,
                                entry.name(),
                                Optional.of(now),
                                Optional.of(proposed)));
            }
        }
        return new PresetDiff(current, preset, rows, compatibility);
    }

    /**
     * The parameter set the diff was taken from, unchanged.
     *
     * @return the set
     */
    public CometParameters base() {
        return base;
    }

    /**
     * The preset.
     *
     * @return the preset
     */
    public Preset preset() {
        return preset;
    }

    /**
     * The rows: one per parameter the preset would change, in schema order.
     *
     * @return the rows, immutable
     */
    public List<DiffRow> rows() {
        return rows;
    }

    /**
     * The compatibility check: one entry per delta, the problems among them reported.
     *
     * @return the check
     */
    public CompatibilityReport compatibility() {
        return compatibility;
    }

    /**
     * Applies every row.
     *
     * @return the new set, the rows applied and its validation
     */
    public AppliedPreset applyAll() {
        return applySelected(rows.stream().map(DiffRow::key).toList());
    }

    /**
     * Applies the selected rows only; every other parameter keeps its value and origin.
     *
     * @param parameters the names of the rows to apply; may be empty
     * @return the new set, the rows applied and its validation
     * @throws IllegalArgumentException naming a parameter that is not a row of this diff
     */
    public AppliedPreset applySelected(Collection<String> parameters) {
        Objects.requireNonNull(parameters, "parameters");
        Set<String> selected = new LinkedHashSet<>(parameters);
        Set<String> keys = rows.stream().map(DiffRow::key).collect(Collectors.toSet());
        for (String name : selected) {
            if (!keys.contains(name)) {
                throw new IllegalArgumentException(
                        name
                                + " is not a row of the diff of preset "
                                + preset.id()
                                + " (rows: "
                                + rows.stream().map(DiffRow::key).toList()
                                + "), so it cannot be applied");
            }
        }
        CometParameters model = base;
        List<DiffRow> applied = new ArrayList<>();
        for (DiffRow row : rows) {
            if (selected.contains(row.key())) {
                model = model.withText(row.key(), row.other().orElseThrow(), ValueOrigin.PRESET);
                applied.add(row);
            }
        }
        return new AppliedPreset(
                model, applied, CometValidator.standard().validate(model), compatibility);
    }
}
