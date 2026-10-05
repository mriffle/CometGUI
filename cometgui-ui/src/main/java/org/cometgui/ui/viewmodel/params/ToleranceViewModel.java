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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.params.comet.presets.Preset;
import org.cometgui.params.comet.presets.PresetDelta;
import org.cometgui.params.comet.presets.PresetLoader;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.value.TolerancePair;

/**
 * The precursor mass tolerance as one compound control -- the signed pair, its units, what it is
 * applied to, and the isotope offsets -- and the fragment bins in instrument terms.
 *
 * <p>The pair is {@code R-PARAM-04}'s: its lower bound is normally negative, and its findings are
 * the model's own pair rule ({@code signed_tolerance_pair}: an error for a reversed window, a
 * warning for an asymmetric or same-signed one). Nothing here compares the two bounds.
 *
 * <p>The fragment settings are offered as the built-in presets give them: each distinct set of
 * fragment-scoring values among Comet's example parameter files, named after the files that use it.
 * No value is invented here.
 */
public final class ToleranceViewModel {

    /** The precursor tolerance units. */
    static final String UNITS = "peptide_mass_units";

    /** What an amu or mmu tolerance is applied to. */
    static final String TYPE = "precursor_tolerance_type";

    /** The isotope offsets searched. */
    static final String ISOTOPE = "isotope_error";

    private final ParameterSession session;

    private final List<Preset> builtIns;

    /**
     * The tolerance controls of a session.
     *
     * @param session the session
     */
    public ToleranceViewModel(ParameterSession session) {
        this.session = Objects.requireNonNull(session, "session");
        this.builtIns = PresetLoader.loadBundled(session.metadata());
    }

    /**
     * The lower bound's field.
     *
     * @return the field of {@code peptide_mass_tolerance_lower}
     */
    public FieldViewModel lower() {
        return session.field(TolerancePair.LOWER);
    }

    /**
     * The upper bound's field.
     *
     * @return the field of {@code peptide_mass_tolerance_upper}
     */
    public FieldViewModel upper() {
        return session.field(TolerancePair.UPPER);
    }

    /**
     * The units' field, whose choices name amu, mmu and ppm.
     *
     * @return the field of {@code peptide_mass_units}
     */
    public FieldViewModel units() {
        return session.field(UNITS);
    }

    /**
     * What an amu or mmu tolerance is applied to.
     *
     * @return the field of {@code precursor_tolerance_type}
     */
    public FieldViewModel type() {
        return session.field(TYPE);
    }

    /**
     * The isotope offsets, whose choices name each code's offsets.
     *
     * @return the field of {@code isotope_error}
     */
    public FieldViewModel isotope() {
        return session.field(ISOTOPE);
    }

    /**
     * Sets both bounds, as the compound control's two fields hold them. Each bound is its own
     * parameter, so each is read, and refused at its own field, on its own.
     *
     * @param lowerText the lower bound
     * @param upperText the upper bound
     * @return accepted when both are, else the first refusal
     */
    public EditOutcome setWindow(String lowerText, String upperText) {
        EditOutcome lowerOutcome = lower().setText(lowerText);
        EditOutcome upperOutcome = upper().setText(upperText);
        return lowerOutcome.accepted() ? upperOutcome : lowerOutcome;
    }

    /**
     * The findings at either bound, in report order: the pair rule's, and any bound's.
     *
     * @return the findings
     */
    public List<Finding> pairFindings() {
        return session.report().findings().stream()
                .filter(
                        finding ->
                                finding.concerns(TolerancePair.LOWER)
                                        || finding.concerns(TolerancePair.UPPER))
                .toList();
    }

    /**
     * The whole precursor setting in words, every word the release's.
     *
     * @return for example {@code -20.0 to 20.0 ppm; applied to: Precursor m/z; isotope offsets: 0,
     *     +1, +2}
     */
    public String summary() {
        return session.model().text(TolerancePair.LOWER)
                + " to "
                + session.model().text(TolerancePair.UPPER)
                + " "
                + chosen(units())
                + "; applied to: "
                + chosen(type())
                + "; isotope offsets: "
                + chosen(isotope());
    }

    /**
     * The label of an enumerated parameter's choice in the configuration.
     *
     * @param field an enumerated field
     * @return the label, or the text itself in quotes when it is not one of the release's choices
     *     (the field then carries the model's {@code choice} error)
     */
    private String chosen(FieldViewModel field) {
        String held = session.model().text(field.name());
        for (ChoiceOption choice : field.choices()) {
            if (choice.token().equals(held)) {
                return choice.label();
            }
        }
        return "\"" + held + "\"";
    }

    /**
     * The fragment-bin width's field.
     *
     * @return the field of {@code fragment_bin_tol}
     */
    public FieldViewModel fragmentBinTol() {
        return session.field("fragment_bin_tol");
    }

    /**
     * The fragment-bin offset's field.
     *
     * @return the field of {@code fragment_bin_offset}
     */
    public FieldViewModel fragmentBinOffset() {
        return session.field("fragment_bin_offset");
    }

    /**
     * The instrument settings of the fragment parameters: each distinct set of fragment-scoring
     * values among the built-in presets that the selected release models, in preset order, named
     * after the presets that use it.
     *
     * @return the options
     */
    public List<FragmentOption> fragmentOptions() {
        Map<Map<String, String>, List<String>> grouped = new LinkedHashMap<>();
        for (Preset preset : builtIns) {
            Map<String, String> values = new LinkedHashMap<>();
            for (PresetDelta delta : preset.deltas()) {
                Optional<FieldViewModel> field = fieldOf(delta.parameter());
                if (field.isPresent()
                        && field.get().category() == ParameterCategory.FRAGMENT_SCORING) {
                    values.put(delta.parameter(), delta.value());
                }
            }
            if (!values.isEmpty()) {
                grouped.computeIfAbsent(values, key -> new ArrayList<>()).add(preset.displayName());
            }
        }
        List<FragmentOption> options = new ArrayList<>();
        grouped.forEach((values, names) -> options.add(new FragmentOption(names, values)));
        return List.copyOf(options);
    }

    /**
     * The instrument setting the configuration's fragment values are, if any: every value of the
     * option is the text the configuration holds.
     *
     * @return the option, or empty for values none of the presets gives
     */
    public Optional<FragmentOption> fragmentMatch() {
        return fragmentOptions().stream()
                .filter(
                        option ->
                                option.values().entrySet().stream()
                                        .allMatch(
                                                e ->
                                                        session.model()
                                                                .text(e.getKey())
                                                                .equals(e.getValue())))
                .findFirst();
    }

    /**
     * The fragment setting in words.
     *
     * @return the matching option's words, or that it is none of them
     */
    public String fragmentWords() {
        return fragmentMatch()
                .map(option -> "As in: " + option.words())
                .orElse("Not one of the built-in instrument settings");
    }

    /**
     * Sets the fragment parameters to an instrument setting, each through the model.
     *
     * @param option one of {@link #fragmentOptions()}
     * @return accepted when every value is, else the first refusal
     */
    public EditOutcome chooseFragment(FragmentOption option) {
        Objects.requireNonNull(option, "option");
        EditOutcome outcome = EditOutcome.applied();
        for (Map.Entry<String, String> value : option.values().entrySet()) {
            EditOutcome each = session.edit(value.getKey(), value.getValue());
            if (outcome.accepted() && !each.accepted()) {
                outcome = each;
            }
        }
        return outcome;
    }

    private Optional<FieldViewModel> fieldOf(String name) {
        return session.fields().stream().filter(field -> field.name().equals(name)).findFirst();
    }
}
