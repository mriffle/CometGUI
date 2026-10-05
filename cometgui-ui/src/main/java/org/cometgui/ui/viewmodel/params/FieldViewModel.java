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
import java.util.Optional;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.ValueKind;
import org.cometgui.params.comet.schema.VisibilityLevel;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.ValidationReport;
import org.cometgui.ui.viewmodel.NonNullProperty;

/**
 * One parameter of the selected Comet release, as a control shows it.
 *
 * <h2>The release's own facts</h2>
 *
 * <p>Everything a field says about its parameter -- display name, help, help page, inline comment,
 * category, visibility, kind, default and choices -- is the definition {@code
 * CuratedMetadata.parameter(name, version)} gives for the session's release, with that release's
 * override applied (decision P7-2). A field belongs to one release: switching release builds new
 * fields.
 *
 * <h2>State, and where it comes from</h2>
 *
 * <p>The text, origin, findings and lock are the session's to set, from the one model and the one
 * validation report it holds (decision P7-1); a field computes nothing about a value. Its findings
 * are exactly {@link ValidationReport#forParameter(String)}. An edit goes to the session, which
 * hands the text to the model ({@code withText(name, text, USER)}); if the model refuses the text,
 * the configuration is unchanged and this field shows the model's own message as a field-level
 * error, with the text that was refused, until it is replaced by an accepted edit, a reset or a new
 * configuration.
 *
 * <p>State is published in words as well as in kind ({@link #stateText()}, {@link #originText()},
 * {@link #lockReason()}): exit gate item 7 requires validation state to be conveyed in text.
 */
public final class FieldViewModel {

    private final ParameterSession session;

    private final ParameterDefinition definition;

    private final ToolVersion release;

    private final NonNullProperty<String> text;

    private final NonNullProperty<ValueOrigin> origin;

    private final NonNullProperty<List<Finding>> findings;

    private final NonNullProperty<Optional<String>> refusal;

    private final NonNullProperty<FieldState> state;

    private final NonNullProperty<String> stateText;

    private final ReadOnlyBooleanWrapper locked;

    private final NonNullProperty<Optional<String>> lockReason;

    /** The model's text while no refusal is pending; what {@link #text} shows otherwise. */
    private String modelText;

    FieldViewModel(ParameterSession session, ParameterDefinition definition, ToolVersion release) {
        this.session = Objects.requireNonNull(session, "session");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.release = Objects.requireNonNull(release, "release");
        this.modelText = definition.defaultValue();
        this.text = new NonNullProperty<>(this, "text", modelText);
        this.origin = new NonNullProperty<>(this, "origin", ValueOrigin.COMET_DEFAULT);
        this.findings = new NonNullProperty<>(this, "findings", List.of());
        this.refusal = new NonNullProperty<>(this, "refusal", Optional.empty());
        this.state = new NonNullProperty<>(this, "state", FieldState.VALID);
        this.stateText = new NonNullProperty<>(this, "stateText", stateTextFor(List.of()));
        this.locked = new ReadOnlyBooleanWrapper(this, "locked", false);
        this.lockReason = new NonNullProperty<>(this, "lockReason", Optional.empty());
    }

    /**
     * The parameter's name, exactly as Comet spells it.
     *
     * @return for example {@code peptide_mass_tolerance_lower}
     */
    public String name() {
        return definition.name();
    }

    /**
     * The label a control shows, and its accessible name.
     *
     * @return for example {@code Precursor tolerance, lower bound}
     */
    public String displayName() {
        return definition.displayName();
    }

    /**
     * Concise help: the scientific meaning, in the selected release's words.
     *
     * @return the help
     */
    public String shortHelp() {
        return definition.shortHelp();
    }

    /**
     * The release-matched documentation the help was written from.
     *
     * @return an {@code https://} reference
     */
    public String helpUrl() {
        return definition.detailedHelpRef();
    }

    /**
     * The comment the canonical file writes after the value, for the selected release.
     *
     * @return the comment, or empty for none
     */
    public Optional<String> inlineComment() {
        return definition.inlineComment();
    }

    /**
     * The Advanced-mode group.
     *
     * @return the category
     */
    public ParameterCategory category() {
        return definition.category();
    }

    /**
     * The lowest editor level that shows the parameter.
     *
     * @return the visibility
     */
    public VisibilityLevel visibility() {
        return definition.visibility();
    }

    /**
     * Whether the parameter is one few searches need ({@link VisibilityLevel#EXPERT}), which
     * Advanced mode marks.
     *
     * @return {@code true} for an expert parameter
     */
    public boolean isExpert() {
        return definition.visibility() == VisibilityLevel.EXPERT;
    }

    /**
     * The structural kind, which decides the control.
     *
     * @return the kind
     */
    public ValueKind kind() {
        return definition.kind();
    }

    /**
     * The selected release's default, as text.
     *
     * @return the text {@code comet -q} writes for this release
     */
    public String defaultText() {
        return definition.defaultValue();
    }

    /**
     * The selected release's choices, in its order; empty for a parameter that is not enumerated.
     *
     * @return the choices
     */
    public List<ChoiceOption> choices() {
        List<ChoiceOption> options = new ArrayList<>();
        for (var choice : definition.choices()) {
            options.add(new ChoiceOption(choice.value(), choice.label()));
        }
        return List.copyOf(options);
    }

    /**
     * The definition this field was built from, as the selected release has it.
     *
     * @return the definition
     */
    public ParameterDefinition definition() {
        return definition;
    }

    /**
     * The release this field belongs to.
     *
     * @return the Comet release
     */
    public ToolVersion release() {
        return release;
    }

    /**
     * The text the control shows: the configuration's value, or the refused text while a refusal is
     * pending.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> textProperty() {
        return text.getReadOnlyProperty();
    }

    /**
     * The text the control shows.
     *
     * @return the text
     */
    public String text() {
        return text.get();
    }

    /**
     * Where the configuration's value came from.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<ValueOrigin> originProperty() {
        return origin.getReadOnlyProperty();
    }

    /**
     * Where the configuration's value came from.
     *
     * @return the origin
     */
    public ValueOrigin origin() {
        return origin.get();
    }

    /**
     * Where the value came from, in words.
     *
     * @return for example {@code Comet 2026.03.0 default} or {@code Required by CometGUI workflow}
     */
    public String originText() {
        return switch (origin.get()) {
            case COMET_DEFAULT -> "Comet " + release.text() + " default";
            case PRESET -> "Set by a preset";
            case USER -> "Set by you";
            case IMPORTED -> "Imported from a parameter file";
            case WORKFLOW_ENFORCED -> "Required by CometGUI workflow";
        };
    }

    /**
     * The findings of the session's report at this parameter, in the report's order.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<List<Finding>> findingsProperty() {
        return findings.getReadOnlyProperty();
    }

    /**
     * The findings at this parameter.
     *
     * @return the findings, immutable
     */
    public List<Finding> findings() {
        return findings.get();
    }

    /**
     * The model's message for the last refused edit, while it is pending.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<Optional<String>> refusalProperty() {
        return refusal.getReadOnlyProperty();
    }

    /**
     * The pending refusal.
     *
     * @return the model's message, or empty
     */
    public Optional<String> refusal() {
        return refusal.get();
    }

    /**
     * The validation state.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<FieldState> stateProperty() {
        return state.getReadOnlyProperty();
    }

    /**
     * The validation state.
     *
     * @return the state
     */
    public FieldState state() {
        return state.get();
    }

    /**
     * The validation state in words: one line per problem, the refused edit first.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> stateTextProperty() {
        return stateText.getReadOnlyProperty();
    }

    /**
     * The validation state in words.
     *
     * @return for example {@code No problems.}, or {@code Error: ...} and {@code Warning: ...}
     *     lines
     */
    public String stateText() {
        return stateText.get();
    }

    /**
     * Whether the value is locked: an output the workflow requires while a stage that reads it is
     * enabled ({@code AC-PAR-09}).
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty lockedProperty() {
        return locked.getReadOnlyProperty();
    }

    /**
     * Whether the value is locked.
     *
     * @return {@code true} if no edit or reset is accepted
     */
    public boolean isLocked() {
        return locked.get();
    }

    /**
     * Why the value is locked, in words.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<Optional<String>> lockReasonProperty() {
        return lockReason.getReadOnlyProperty();
    }

    /**
     * Why the value is locked.
     *
     * @return for example {@code Required by CometGUI workflow: Percolator rescoring reads the .pin
     *     file}; empty when it is not locked
     */
    public Optional<String> lockReason() {
        return lockReason.get();
    }

    /**
     * Sets the value from text, as the scientist typed or chose it. See {@link
     * ParameterSession#edit(String, String)}.
     *
     * @param newText the value text, as it would appear after {@code name = }
     * @return whether the configuration now holds it, or why not
     */
    public EditOutcome setText(String newText) {
        return session.edit(name(), newText);
    }

    /**
     * Sets an enumerated value by its token. The same as {@link #setText(String)}.
     *
     * @param choice one of {@link #choices()}
     * @return whether the configuration now holds it, or why not
     */
    public EditOutcome choose(ChoiceOption choice) {
        return setText(Objects.requireNonNull(choice, "choice").token());
    }

    /**
     * Puts the value back to the release's default. See {@link
     * ParameterSession#resetField(String)}.
     *
     * @return whether it was reset, or why not
     */
    public EditOutcome reset() {
        return session.resetField(name());
    }

    /**
     * Shows the configuration's state: called by the session after every change.
     *
     * @param model the configuration
     * @param report its one validation report
     * @param lock why the value is locked, or empty
     */
    void show(CometParameters model, ValidationReport report, Optional<String> lock) {
        modelText = model.text(name());
        origin.set(model.origin(name()));
        findings.set(report.forParameter(name()));
        locked.set(lock.isPresent());
        lockReason.set(lock);
        refresh();
    }

    /**
     * Shows a refused edit: the text that was refused and the model's message.
     *
     * @param refusedText what the scientist entered
     * @param message the model's own message
     */
    void refuse(String refusedText, String message) {
        refusal.set(Optional.of(message));
        text.set(refusedText);
        refresh();
    }

    /** Drops a pending refusal, so the field shows the configuration's value again. */
    void clearRefusal() {
        refusal.set(Optional.empty());
        refresh();
    }

    private void refresh() {
        if (refusal.get().isEmpty()) {
            text.set(modelText);
        }
        List<Finding> current = findings.get();
        FieldState next = FieldState.VALID;
        if (refusal.get().isPresent() || current.stream().anyMatch(Finding::isError)) {
            next = FieldState.ERROR;
        } else if (!current.isEmpty()) {
            next = FieldState.WARNING;
        }
        state.set(next);
        stateText.set(stateTextFor(current));
    }

    private String stateTextFor(List<Finding> current) {
        List<String> lines = new ArrayList<>();
        refusal.get()
                .ifPresent(
                        message ->
                                lines.add(
                                        FieldState.ERROR.words()
                                                + ": not applied, the configuration still holds \""
                                                + modelText
                                                + "\". "
                                                + message));
        for (Finding finding : current) {
            FieldState kind = finding.isError() ? FieldState.ERROR : FieldState.WARNING;
            lines.add(kind.words() + ": " + finding.message());
        }
        if (lines.isEmpty()) {
            return FieldState.VALID.words() + ".";
        }
        return String.join("\n", lines);
    }
}
