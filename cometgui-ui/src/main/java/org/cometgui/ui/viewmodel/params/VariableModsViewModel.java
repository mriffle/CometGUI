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
import java.util.Set;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.migration.MigrationEntry;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.presets.ModificationPreset;
import org.cometgui.params.comet.presets.ModificationPresets;
import org.cometgui.params.comet.schema.TerminalCode;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.value.ValueSyntaxException;
import org.cometgui.params.comet.value.VariableModChoice;
import org.cometgui.params.comet.value.VariableModPart;
import org.cometgui.params.comet.value.VariableModSlots;
import org.cometgui.params.comet.value.VariableModification;
import org.cometgui.ui.viewmodel.NonNullProperty;

/**
 * The variable-modification editor (R-PARAM-09, R-PARAM-10, AC-PAR-05): every slot of the selected
 * release, each with its summary, its serialised value and a control per part of the release's
 * tuple layout; add, edit, remove and move; common-modification presets; and the two parameters
 * that change the meaning of every slot, with their cross-validation findings.
 *
 * <h2>Nothing is parsed here (decision P7-1)</h2>
 *
 * <p>Slots, parts, the residue alphabet, the unused value, choices and explanations are the model's
 * {@link VariableModSlots} for the session's release; a part's text goes to {@code
 * VariableModSlots.withPart}, a residue or terminus to {@code withResidue}, and the value it
 * returns is put into the slot with the session's {@code setValue} (origin {@code USER}). A refused
 * text is shown at the slot's field with the model's message, so it blocks a run and is listed in
 * the summary as any refused edit is. Every finding is the session's one report.
 *
 * <h2>Moving a slot and the migration review (unit 1's constraint)</h2>
 *
 * <p>A move changes two slots' values at once, both origin {@code USER}: the scientist reassigned
 * both. A slot whose migration entry still needs the scientist's decision is therefore never moved
 * -- from or to -- and is never the slot an added modification goes into: either would put a value
 * the scientist did not set on that parameter under origin {@code USER} and silently resolve the
 * entry. Such a move is refused with the reason. Editing a part of the slot, or removing its
 * modification, is a decision on that very slot and resolves it, as the review intends.
 */
public final class VariableModsViewModel {

    /** The per-peptide limit shown with the slots (R-PARAM-10). */
    static final String LIMIT = "max_variable_mods_in_peptide";

    /** The requirement switch shown with the slots (R-PARAM-10). */
    static final String REQUIRE = "require_variable_mod";

    private final ParameterSession session;

    private final ModificationPresets presets;

    private VariableModSlots slots;

    private ToolVersion slotsRelease;

    private final NonNullProperty<List<VariableModSlotView>> views;

    /**
     * The editor of a session's slots, following the session's release.
     *
     * @param session the session
     */
    public VariableModsViewModel(ParameterSession session) {
        this.session = Objects.requireNonNull(session, "session");
        this.presets = ModificationPresets.loadBundled(session.metadata());
        this.slotsRelease = session.model().version();
        this.slots = VariableModSlots.forRelease(session.metadata(), slotsRelease);
        this.views = new NonNullProperty<>(this, "slots", build());
        session.modelProperty().addListener((observable, before, after) -> refresh());
        session.reportProperty().addListener((observable, before, after) -> refresh());
    }

    /**
     * Every slot of the selected release, in order; replaced after every change.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<List<VariableModSlotView>> slotsProperty() {
        return views.getReadOnlyProperty();
    }

    /**
     * Every slot of the selected release, in order.
     *
     * @return the slots
     */
    public List<VariableModSlotView> slots() {
        return views.get();
    }

    /**
     * One slot.
     *
     * @param name the slot, such as {@code variable_mod03}
     * @return its view
     * @throws IllegalArgumentException if the selected release has no such slot
     */
    public VariableModSlotView slot(String name) {
        Objects.requireNonNull(name, "name");
        return views.get().stream()
                .filter(view -> view.name().equals(name))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Comet "
                                                + session.release().text()
                                                + " has no variable-modification slot "
                                                + name));
    }

    /**
     * A slot's field: its origin, findings, pending refusal and state in words.
     *
     * @param slot the slot
     * @return the field
     * @throws IllegalArgumentException if the selected release has no such slot
     */
    public FieldViewModel field(String slot) {
        return session.field(slot(slot).name());
    }

    /**
     * The parts of the selected release's tuple, in its layout's order.
     *
     * @return the parts
     */
    public List<VariableModPart> parts() {
        return slots.parts();
    }

    /**
     * The residue letters the multi-select offers: the selected release's alphabet.
     *
     * @return the letters, in alphabetical order
     */
    public List<Character> residueLetters() {
        return slots.alphabet().letters().chars().mapToObj(c -> (char) c).toList();
    }

    /**
     * The terminus choices of the residue token the selected release offers: {@code n} and {@code
     * c} in every release, {@code ^} and {@code $} where the release's alphabet has them.
     *
     * @return the codes, each with its words
     */
    public List<TerminalCode> terminusCodes() {
        return slots.alphabet().terminalCodes();
    }

    /**
     * The common-modification presets the selected release can hold, in the bundled order.
     *
     * @return the presets
     */
    public List<ModificationPreset> presets() {
        return presets.offeredIn(slotsRelease);
    }

    /**
     * The per-peptide limit, shown next to the slots because it changes the meaning of every slot
     * (R-PARAM-10).
     *
     * @return the field of {@code max_variable_mods_in_peptide}
     */
    public FieldViewModel limitField() {
        return session.field(LIMIT);
    }

    /**
     * The requirement switch, shown next to the slots (R-PARAM-10).
     *
     * @return the field of {@code require_variable_mod}
     */
    public FieldViewModel requireField() {
        return session.field(REQUIRE);
    }

    /**
     * The findings of the report that judge the slots against the two parameters above: every
     * finding at {@code max_variable_mods_in_peptide} or {@code require_variable_mod}, in report
     * order.
     *
     * @return the findings
     */
    public List<Finding> crossFindings() {
        return session.report().findings().stream()
                .filter(finding -> finding.concerns(LIMIT) || finding.concerns(REQUIRE))
                .toList();
    }

    /**
     * The first slot an added modification would go into: the first unused slot whose migration
     * entry, if any, is resolved.
     *
     * @return the slot, or empty when none is free
     */
    public Optional<String> firstFreeSlot() {
        Set<String> open = unresolvedSlots();
        return views.get().stream()
                .filter(view -> !view.active() && !open.contains(view.name()))
                .map(VariableModSlotView::name)
                .findFirst();
    }

    /**
     * Adds a common modification into the first free slot.
     *
     * @param preset one of {@link #presets()}
     * @return whether it was added, or why not: the release cannot hold it, or no slot is free
     */
    public EditOutcome add(ModificationPreset preset) {
        Objects.requireNonNull(preset, "preset");
        Optional<String> unholdable = slots.unwritable(preset.modification());
        if (unholdable.isPresent()) {
            return EditOutcome.refused(
                    preset.name()
                            + " cannot be added to a Comet "
                            + session.release().text()
                            + " configuration: "
                            + unholdable.get());
        }
        Optional<String> free = firstFreeSlot();
        if (free.isEmpty()) {
            return EditOutcome.refused(noFreeSlot(preset));
        }
        return session.setValue(free.get(), new ParameterValue.Tuple(preset.modification()));
    }

    private String noFreeSlot(ModificationPreset preset) {
        List<String> waiting =
                views.get().stream()
                        .filter(view -> !view.active() && view.needsAttention())
                        .map(VariableModSlotView::name)
                        .toList();
        String message =
                preset.name()
                        + " cannot be added: all "
                        + views.get().size()
                        + " variable-modification slots hold a modification; remove one first";
        if (!waiting.isEmpty()) {
            message +=
                    ". Unused but waiting for your decision in the migration review: "
                            + String.join(", ", waiting);
        }
        return message;
    }

    /**
     * Sets one part of a slot from the text the scientist entered.
     *
     * @param slot the slot
     * @param part one of {@link #parts()}
     * @param text the text
     * @return whether the slot now holds it, or the model's refusal, which the slot's field shows
     */
    public EditOutcome setPart(String slot, VariableModPart part, String text) {
        VariableModification current = slot(slot).value();
        VariableModification changed;
        try {
            changed = slots.withPart(slot, current, part, text);
        } catch (ValueSyntaxException refused) {
            return session.refuse(slot, text, refused.getMessage());
        }
        return session.setValue(slot, new ParameterValue.Tuple(changed));
    }

    /**
     * Sets a coded part from one of its documented choices.
     *
     * @param slot the slot
     * @param part {@link VariableModPart#TERMINUS} or {@link VariableModPart#REQUIRED}
     * @param choice one of the part's choices
     * @return whether the slot now holds it, or why not
     */
    public EditOutcome choose(String slot, VariableModPart part, VariableModChoice choice) {
        return setPart(slot, part, Objects.requireNonNull(choice, "choice").token());
    }

    /**
     * Selects or clears one residue letter or terminal code of a slot's residue token.
     *
     * @param slot the slot
     * @param character a letter of {@link #residueLetters()} or a code of {@link #terminusCodes()}
     * @param selected whether the token should hold it
     * @return whether the slot now holds it, or the model's refusal, which the slot's field shows
     */
    public EditOutcome setResidue(String slot, char character, boolean selected) {
        VariableModification current = slot(slot).value();
        VariableModification changed;
        try {
            changed = slots.withResidue(slot, current, character, selected);
        } catch (ValueSyntaxException refused) {
            return session.refuse(slot, String.valueOf(character), refused.getMessage());
        }
        return session.setValue(slot, new ParameterValue.Tuple(changed));
    }

    /**
     * Removes a slot's modification: the slot goes back to the release's unused value. This is the
     * scientist's decision on that slot, so it resolves a migration entry for it.
     *
     * @param slot the slot
     * @return the outcome
     */
    public EditOutcome remove(String slot) {
        slot(slot);
        return session.setValue(slot, new ParameterValue.Tuple(slots.unused()));
    }

    /**
     * Swaps a slot's value with the slot before it.
     *
     * @param slot the slot
     * @return the outcome; refused for the first slot
     */
    public EditOutcome moveUp(String slot) {
        int index = slot(slot).number() - 1;
        if (index == 0) {
            return EditOutcome.refused(slot + " is the first slot; it cannot move up");
        }
        return moveTo(slot, views.get().get(index - 1).name());
    }

    /**
     * Swaps a slot's value with the slot after it.
     *
     * @param slot the slot
     * @return the outcome; refused for the last slot
     */
    public EditOutcome moveDown(String slot) {
        int index = slot(slot).number() - 1;
        if (index == views.get().size() - 1) {
            return EditOutcome.refused(slot + " is the last slot; it cannot move down");
        }
        return moveTo(slot, views.get().get(index + 1).name());
    }

    /**
     * Assigns a slot's value to another slot, which takes the first slot's place: the two values
     * are swapped, both origin {@code USER}. Refused when either slot's migration entry still needs
     * the scientist's decision.
     *
     * @param slot the slot whose value moves
     * @param target the slot it moves to
     * @return the outcome
     */
    public EditOutcome moveTo(String slot, String target) {
        VariableModSlotView from = slot(slot);
        VariableModSlotView to = slot(target);
        if (from.name().equals(to.name())) {
            return EditOutcome.applied();
        }
        for (VariableModSlotView view : List.of(from, to)) {
            if (view.needsAttention()) {
                return EditOutcome.refused(
                        view.name()
                                + " needs your decision in the migration review before a"
                                + " modification can be moved to or from it: set its value or"
                                + " accept it first");
            }
        }
        Map<String, ParameterValue> swap = new LinkedHashMap<>();
        swap.put(from.name(), new ParameterValue.Tuple(to.value()));
        swap.put(to.name(), new ParameterValue.Tuple(from.value()));
        return session.setValues(swap);
    }

    private Set<String> unresolvedSlots() {
        return session.unresolved().stream()
                .map(MigrationEntry::parameter)
                .collect(Collectors.toSet());
    }

    /**
     * Rebuilds every slot's view. The slots follow the configuration's own release, which is the
     * session's once a release change has finished.
     */
    private void refresh() {
        ToolVersion release = session.model().version();
        if (!release.equals(slotsRelease)) {
            slots = VariableModSlots.forRelease(session.metadata(), release);
            slotsRelease = release;
        }
        views.set(build());
    }

    private List<VariableModSlotView> build() {
        CometParameters model = session.model();
        Set<String> open = unresolvedSlots();
        List<ModificationPreset> offered = presets.offeredIn(slotsRelease);
        List<VariableModSlotView> built = new ArrayList<>();
        List<String> names = slots.slots();
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index);
            VariableModification value = ((ParameterValue.Tuple) model.value(name)).modification();
            Optional<ModificationPreset> preset =
                    offered.stream()
                            .filter(candidate -> candidate.modification().equals(value))
                            .findFirst();
            List<VariableModPartView> parts = new ArrayList<>();
            for (VariableModPart part : slots.parts()) {
                parts.add(
                        new VariableModPartView(
                                part,
                                part.label(),
                                slots.partText(value, part),
                                VariableModSlots.explanation(part),
                                slots.choices(part)));
            }
            built.add(
                    new VariableModSlotView(
                            name,
                            index + 1,
                            value,
                            !value.isUnused(),
                            preset.map(ModificationPreset::summary).orElse(value.summary()),
                            model.text(name),
                            open.contains(name),
                            preset,
                            parts,
                            session.field(name).displayName()));
        }
        return List.copyOf(built);
    }
}
