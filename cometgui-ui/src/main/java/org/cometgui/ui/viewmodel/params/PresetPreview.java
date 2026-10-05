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

package org.cometgui.ui.viewmodel.params;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.cometgui.params.comet.migration.VersionConversion;
import org.cometgui.params.comet.presets.DiffRow;
import org.cometgui.params.comet.presets.Preset;
import org.cometgui.params.comet.presets.PresetDiff;

/**
 * What a preset would change, shown before anything changes ({@code AC-PAR-08}): the model's {@link
 * PresetDiff} as rows with check boxes, and its compatibility check in words.
 *
 * <p>Building a preview changes nothing; {@link PresetsViewModel#applyAll()} and {@link
 * PresetsViewModel#applySelected()} apply it, and {@link PresetsViewModel#cancel()} drops it.
 */
public final class PresetPreview {

    private final PresetDiff diff;

    private final List<PresetRowViewModel> rows;

    PresetPreview(PresetDiff diff, ParameterSession session) {
        this.diff = Objects.requireNonNull(diff, "diff");
        List<PresetRowViewModel> built = new ArrayList<>();
        for (DiffRow row : diff.rows()) {
            built.add(
                    new PresetRowViewModel(
                            DiffRowView.of(row, session), session.lockOf(row.key())));
        }
        this.rows = List.copyOf(built);
    }

    /**
     * The preset.
     *
     * @return the preset
     */
    public Preset preset() {
        return diff.preset();
    }

    /**
     * The model's diff, taken from the configuration as it was when the preview was made.
     *
     * @return the diff
     */
    public PresetDiff diff() {
        return diff;
    }

    /**
     * One row per parameter the preset would change, in schema order.
     *
     * @return the rows
     */
    public List<PresetRowViewModel> rows() {
        return rows;
    }

    /**
     * The parameters whose rows are selected, in row order.
     *
     * @return the names
     */
    public List<String> selected() {
        return rows.stream()
                .filter(PresetRowViewModel::isSelected)
                .map(PresetRowViewModel::parameter)
                .toList();
    }

    /**
     * The parameters whose rows can be applied: every row not locked, in row order.
     *
     * @return the names
     */
    public List<String> applicable() {
        return rows.stream()
                .filter(row -> row.lockReason().isEmpty())
                .map(PresetRowViewModel::parameter)
                .toList();
    }

    /**
     * Which release the preset was made for, in words.
     *
     * @return for example {@code Made for Comet 2026.02.2; this configuration is for Comet
     *     2026.03.0}
     */
    public String madeFor() {
        String made = "Made for Comet " + preset().cometVersion().text();
        if (preset().cometVersion().equals(diff.base().version())) {
            return made;
        }
        return made + "; this configuration is for Comet " + diff.base().version().text();
    }

    /**
     * The deltas the configuration's release cannot take, in words: never a row, never applied.
     *
     * @return one line per problem, in the preset's order
     */
    public List<String> problems() {
        return diff.compatibility().problems().stream().map(PresetPreview::problemWords).toList();
    }

    /**
     * The deltas whose text was rewritten for the configuration's release, in words.
     *
     * @return one line per conversion, in the preset's order
     */
    public List<String> conversions() {
        return diff.compatibility().converted().stream()
                .map(result -> "Converted -- " + result.explanation())
                .toList();
    }

    /**
     * One compatibility problem in words.
     *
     * @param problem a result that is not usable
     * @return its status in words, then the model's explanation
     */
    static String problemWords(VersionConversion.Result problem) {
        String status =
                problem.status() == VersionConversion.Status.NOT_IN_TARGET
                        ? "Not in this release"
                        : "This release cannot hold the value";
        return status + " -- " + problem.explanation();
    }
}
