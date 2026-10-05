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
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.params.comet.presets.AppliedPreset;
import org.cometgui.params.comet.presets.DiffRow;
import org.cometgui.params.comet.presets.Preset;
import org.cometgui.params.comet.presets.PresetDiff;
import org.cometgui.params.comet.presets.PresetLoader;
import org.cometgui.ui.viewmodel.NonNullProperty;

/**
 * Presets: the built-in instrument-resolution presets and any user presets, previewed as a
 * reviewable diff before anything changes, then applied whole, in part, or not at all ({@code
 * AC-PAR-08}, exit gate item 3).
 *
 * <p>The model does the work. {@link PresetDiff#of} makes the rows and runs the compatibility check
 * against the configuration's release; {@link PresetDiff#applySelected} makes the new set, in which
 * exactly the applied rows hold the preset's text with origin {@code PRESET}; the session adopts it
 * as {@link Adoption#PRESET_APPLIED}, so the workflow's outputs stay enforced and a migration under
 * review stays under review. The applied set's findings are the session's one report.
 *
 * <p>Cancelling, applying nothing, and a preview the configuration has moved on from all leave the
 * configuration exactly as it was: the same model object, every origin unchanged.
 */
public final class PresetsViewModel {

    private final ParameterSession session;

    private final List<Preset> presets;

    private final NonNullProperty<Optional<PresetPreview>> preview;

    private final NonNullProperty<Optional<AppliedPreset>> lastApplied;

    /**
     * The presets of a session.
     *
     * @param session the session
     * @param userPresets the scientist's own presets, offered after the built-in ones; each records
     *     the release it was made for, and its preview runs the compatibility check
     */
    public PresetsViewModel(ParameterSession session, List<Preset> userPresets) {
        this.session = Objects.requireNonNull(session, "session");
        List<Preset> all = new ArrayList<>(PresetLoader.loadBundled(session.metadata()));
        all.addAll(userPresets);
        this.presets = List.copyOf(all);
        this.preview = new NonNullProperty<>(this, "preview", Optional.empty());
        this.lastApplied = new NonNullProperty<>(this, "lastApplied", Optional.empty());
        session.releaseProperty().addListener((observable, before, after) -> cancel());
    }

    /**
     * The presets offered: the built-in ones in their file's order, then the user's.
     *
     * @return the presets
     */
    public List<Preset> presets() {
        return presets;
    }

    /**
     * The preview being reviewed.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<Optional<PresetPreview>> previewProperty() {
        return preview.getReadOnlyProperty();
    }

    /**
     * The preview being reviewed.
     *
     * @return the preview, or empty
     */
    public Optional<PresetPreview> preview() {
        return preview.get();
    }

    /**
     * The last preset applied: the model's result, whose rows say what changed.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<Optional<AppliedPreset>> lastAppliedProperty() {
        return lastApplied.getReadOnlyProperty();
    }

    /**
     * The last preset applied.
     *
     * @return the result, or empty
     */
    public Optional<AppliedPreset> lastApplied() {
        return lastApplied.get();
    }

    /**
     * Previews a preset against the configuration as it is now. Nothing changes.
     *
     * @param preset one of {@link #presets()}
     * @return the preview, also held by {@link #previewProperty()}
     * @throws IllegalArgumentException if the preset is not one offered here
     */
    public PresetPreview preview(Preset preset) {
        Objects.requireNonNull(preset, "preset");
        if (!presets.contains(preset)) {
            throw new IllegalArgumentException(
                    "preset " + preset.id() + " is not one of the presets offered here");
        }
        PresetPreview made = new PresetPreview(PresetDiff.of(session.model(), preset), session);
        preview.set(Optional.of(made));
        return made;
    }

    /**
     * Applies every row of the preview that can be applied (a locked row cannot).
     *
     * @return accepted when the configuration now holds them, or why nothing changed
     */
    public EditOutcome applyAll() {
        return current().map(p -> apply(p, p.applicable())).orElseGet(PresetsViewModel::nothing);
    }

    /**
     * Applies exactly the selected rows of the preview.
     *
     * @return accepted when the configuration now holds them, or why nothing changed
     */
    public EditOutcome applySelected() {
        return current().map(p -> apply(p, p.selected())).orElseGet(PresetsViewModel::nothing);
    }

    /** Drops the preview. The configuration is not touched. */
    public void cancel() {
        preview.set(Optional.empty());
    }

    private Optional<PresetPreview> current() {
        return preview.get();
    }

    private static EditOutcome nothing() {
        return EditOutcome.refused("No preset is being previewed, so there is nothing to apply.");
    }

    private EditOutcome apply(PresetPreview shown, Collection<String> names) {
        if (shown.diff().base() != session.model()) {
            return EditOutcome.refused(
                    "The configuration changed after this preview was made, so it no longer shows"
                            + " what applying would change. Preview the preset again.");
        }
        if (names.isEmpty()) {
            return EditOutcome.refused(
                    shown.rows().isEmpty()
                            ? "The configuration already holds every value of this preset that"
                                    + " its release can take; nothing was applied."
                            : "No change is selected, so nothing was applied.");
        }
        AppliedPreset applied = shown.diff().applySelected(names);
        session.adopt(applied.model(), Adoption.PRESET_APPLIED);
        lastApplied.set(Optional.of(applied));
        preview.set(Optional.empty());
        return EditOutcome.applied();
    }

    /**
     * The rows of the last application as the editor shows them.
     *
     * @return the rows, empty when nothing has been applied
     */
    public List<DiffRowView> lastAppliedRows() {
        List<DiffRowView> rows = new ArrayList<>();
        for (DiffRow row : lastApplied.get().map(AppliedPreset::applied).orElse(List.of())) {
            rows.add(DiffRowView.of(row, session));
        }
        return List.copyOf(rows);
    }
}
