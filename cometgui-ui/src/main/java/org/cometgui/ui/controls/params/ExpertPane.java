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

package org.cometgui.ui.controls.params;

import static org.cometgui.ui.controls.AccessibleControls.named;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.StringConverter;
import org.cometgui.params.comet.model.Diagnostic;
import org.cometgui.params.comet.model.UnknownParameter;
import org.cometgui.params.comet.parser.ParamsHighlighting;
import org.cometgui.params.comet.presets.Preset;
import org.cometgui.ui.controls.UiIds;
import org.cometgui.ui.viewmodel.params.Comparison;
import org.cometgui.ui.viewmodel.params.DiffRowView;
import org.cometgui.ui.viewmodel.params.EditOutcome;
import org.cometgui.ui.viewmodel.params.ExpertLine;
import org.cometgui.ui.viewmodel.params.ExpertViewModel;
import org.cometgui.ui.viewmodel.params.ParameterFilesViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSession;
import org.cometgui.ui.viewmodel.params.RawApplyProposal;

/**
 * The Expert level (<em>Expert</em>, {@code AC-PAR-07}, {@code R-PARAM-08}, exit gate item 4): the
 * configuration's canonical raw text, an editable draft, the draft's lines with their syntax
 * colours <em>and</em> their kind and diagnostics in words, the diagnostics as focusable entries
 * that move the caret to their line, comparisons with the defaults or a preset and with the last
 * saved configuration, the unknown parameters with a remove action each, and an apply that checks
 * the draft and asks for explicit confirmation before the typed configuration changes.
 *
 * <p>Nothing here reads a {@code comet.params} line: the kinds and spans are the model's ({@link
 * ParamsHighlighting} through {@link ExpertLine}), the diagnostics the parser's, and the apply, the
 * confirmation and the comparisons the {@link ExpertViewModel}'s. A failed apply leaves the typed
 * configuration as it was, and the offending lines are listed with their text.
 *
 * <p>The draft's lines and diagnostics are rebuilt only while the level is shown ({@link
 * #setShown(boolean)}): they are re-read on every keystroke, and a hidden level has no reader.
 */
public final class ExpertPane extends VBox {

    /** One thing the configuration can be compared with: the defaults, or a preset. */
    private record CompareChoice(String words, Optional<Preset> preset) {}

    private final ExpertViewModel expert;

    private final TextArea canonical = new TextArea();

    private final Label canonicalStatus;

    private final TextArea draft = new TextArea();

    private final Label diagnosticsHeadline;

    private final VBox diagnostics = new VBox(2);

    private final VBox lines = new VBox(0);

    private final Label applyStatus;

    private final Label offending;

    private final VBox confirmation = new VBox(4);

    private final Label changes;

    private final ComboBox<CompareChoice> compare = new ComboBox<>();

    private final Label compareRows;

    private final Label savedRows;

    private final Label unknownHeadline;

    private final VBox unknowns = new VBox(4);

    private boolean shown;

    private boolean updating;

    /**
     * The Expert level.
     *
     * @param expert the Expert view-model
     * @param files saving, whose last saved configuration one comparison uses
     * @param presets the presets the configuration can be compared with
     * @param session the session, whose configuration the level follows
     */
    public ExpertPane(
            ExpertViewModel expert,
            ParameterFilesViewModel files,
            List<Preset> presets,
            ParameterSession session) {
        this.expert = Objects.requireNonNull(expert, "expert");
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(presets, "presets");
        Objects.requireNonNull(session, "session");
        setSpacing(6);

        Label canonicalTitle = heading("Canonical comet.params (read-only)");
        canonical.setId(UiIds.EXPERT_CANONICAL);
        named(canonical, "Canonical comet.params text of the configuration, read-only");
        canonical.setEditable(false);
        canonical.setWrapText(false);
        canonical.setPrefRowCount(8);
        canonicalStatus = Texts.label(UiIds.EXPERT_CANONICAL_STATUS, "", "canonical text status");

        Label draftTitle = heading("Draft: edit the raw text, then apply it");
        draft.setId(UiIds.EXPERT_DRAFT);
        named(draft, "Raw comet.params draft");
        draft.setAccessibleHelp(
                "Editing the draft changes nothing. Apply checks it; a draft that reads is applied"
                        + " only after you confirm what it changes.");
        draft.setWrapText(false);
        draft.setPrefRowCount(14);
        draft.textProperty()
                .addListener(
                        (observable, before, after) -> {
                            if (!updating) {
                                expert.setDraft(after);
                            }
                        });
        applyStatus =
                Texts.label(
                        UiIds.EXPERT_APPLY_STATUS,
                        "The draft has not been applied.",
                        "the draft's apply status");
        Button apply =
                Texts.button(
                        UiIds.EXPERT_APPLY,
                        "Apply draft...",
                        "Check the draft and show what applying it would change");
        apply.setOnAction(event -> applied(expert.apply()));
        Button revert =
                Texts.button(
                        UiIds.EXPERT_REVERT,
                        "Discard draft",
                        "Discard the draft's edits: it becomes the canonical text again");
        revert.setOnAction(
                event -> {
                    expert.revertDraft();
                    applyStatus.setText("Draft discarded: it is the canonical text again.");
                });
        HBox actions = new HBox(8, apply, revert);
        actions.setAlignment(Pos.CENTER_LEFT);
        offending = Texts.label(UiIds.EXPERT_OFFENDING, "", "the offending lines");

        confirmation.setId(UiIds.EXPERT_CONFIRMATION);
        confirmation.setPadding(new Insets(0, 0, 0, 16));
        changes = Texts.label(UiIds.EXPERT_CHANGES, "", "what applying the draft would change");
        Button confirm =
                Texts.button(
                        UiIds.EXPERT_CONFIRM,
                        "Confirm: change the configuration",
                        "Confirm: apply the checked draft to the configuration");
        confirm.setOnAction(event -> confirmed(expert.confirm()));
        Button cancel =
                Texts.button(
                        UiIds.EXPERT_CANCEL,
                        "Do not apply",
                        "Do not apply the draft; the configuration stays as it is");
        cancel.setOnAction(
                event -> {
                    expert.cancelApply();
                    applyStatus.setText("Not applied: the configuration is unchanged.");
                    apply.requestFocus();
                });
        confirmation.getChildren().addAll(changes, new HBox(8, confirm, cancel));

        diagnosticsHeadline =
                Texts.label(UiIds.EXPERT_DIAGNOSTICS_HEADLINE, "", "the draft's diagnostics");
        Label linesTitle = heading("The draft, line by line: colours, and the same in words");
        lines.setId(UiIds.EXPERT_LINES);

        Label compareTitle = heading("Differences");
        compare.setId(UiIds.EXPERT_COMPARE);
        named(compare, "Compare the configuration with");
        List<CompareChoice> choices = new ArrayList<>();
        choices.add(new CompareChoice("the release's defaults", Optional.empty()));
        for (Preset preset : presets) {
            choices.add(
                    new CompareChoice(
                            "the defaults with preset " + preset.displayName(),
                            Optional.of(preset)));
        }
        compare.getItems().setAll(choices);
        compare.setValue(choices.get(0));
        compare.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(CompareChoice choice) {
                        return choice == null ? "" : choice.words();
                    }

                    @Override
                    public CompareChoice fromString(String text) {
                        return null;
                    }
                });
        compare.valueProperty().addListener((observable, before, after) -> refreshCompare());
        Label compareLabel = new Label("Compare with");
        named(compareLabel, "Compare with");
        compareLabel.setLabelFor(compare);
        HBox compareLine = new HBox(8, compareLabel, compare);
        compareLine.setAlignment(Pos.CENTER_LEFT);
        compareRows = Texts.label(UiIds.EXPERT_COMPARE_ROWS, "", "comparison");
        savedRows = Texts.label(UiIds.EXPERT_SAVED_ROWS, "", "comparison with the last save");

        Label unknownTitle = heading("Unknown parameters");
        unknownHeadline = Texts.label(UiIds.EXPERT_UNKNOWN_HEADLINE, "", "unknown parameters");

        getChildren()
                .addAll(
                        draftTitle,
                        draft,
                        actions,
                        applyStatus,
                        offending,
                        confirmation,
                        diagnosticsHeadline,
                        diagnostics,
                        compareTitle,
                        compareLine,
                        compareRows,
                        savedRows,
                        unknownTitle,
                        unknownHeadline,
                        unknowns,
                        canonicalTitle,
                        canonical,
                        canonicalStatus,
                        linesTitle,
                        lines);

        expert.draftProperty().addListener((observable, before, after) -> draftChanged());
        expert.proposalProperty().addListener((observable, before, after) -> draftChanged());
        expert.applyErrorsProperty().addListener((observable, before, after) -> draftChanged());
        expert.canonicalTextProperty()
                .addListener((observable, before, after) -> configurationChanged());
        files.lastSavedProperty()
                .addListener((observable, before, after) -> configurationChanged());
        session.modelProperty().addListener((observable, before, after) -> configurationChanged());
        confirmation.setVisible(false);
        confirmation.setManaged(false);
    }

    /**
     * Says whether the level is shown; showing it brings everything up to date.
     *
     * @param visible whether the Expert level is the one shown
     */
    public void setShown(boolean visible) {
        shown = visible;
        if (visible) {
            configurationChanged();
            draftChanged();
        }
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        named(label, text);
        return label;
    }

    /** The configuration, its canonical text or the last save changed: the comparisons too. */
    private void configurationChanged() {
        if (!shown) {
            return;
        }
        if (!canonical.getText().equals(expert.canonicalText())) {
            canonical.setText(expert.canonicalText());
        }
        canonicalStatus.setText(
                expert.canonicalRefusal()
                        .map(reason -> "The configuration cannot be written: " + reason)
                        .orElse("The canonical text is what a saved file holds."));
        refreshCompare();
        refreshUnknowns();
    }

    /** The draft, its proposal or its errors changed: everything read from the draft too. */
    private void draftChanged() {
        if (!shown) {
            return;
        }
        updating = true;
        try {
            if (!draft.getText().equals(expert.draft())) {
                draft.setText(expert.draft());
            }
        } finally {
            updating = false;
        }
        refreshDiagnostics();
        refreshLines();
        refreshProposal();
    }

    private void refreshDiagnostics() {
        diagnostics.getChildren().clear();
        List<Diagnostic> found = expert.diagnostics();
        List<String> texts = expert.diagnosticTexts();
        diagnosticsHeadline.setText(
                found.isEmpty()
                        ? "Diagnostics: the draft reads without an error or a warning."
                        : "Diagnostics: "
                                + found.size()
                                + (found.size() == 1 ? " finding" : " findings")
                                + " in the draft. Activate one to move the caret to its line.");
        for (int index = 0; index < found.size(); index++) {
            Diagnostic diagnostic = found.get(index);
            Button entry =
                    Texts.button(UiIds.expertDiagnostic(index), texts.get(index), texts.get(index));
            entry.setWrapText(true);
            entry.setMaxWidth(Double.MAX_VALUE);
            if (diagnostic.lines().isEmpty()) {
                entry.setAccessibleHelp("This finding is about the draft as a whole.");
            } else {
                int line = diagnostic.lines().get(0);
                entry.setAccessibleHelp("Activate to move the caret to line " + line + ".");
                entry.setOnAction(event -> moveCaretTo(line));
                entry.setOnKeyPressed(
                        event -> {
                            if (event.getCode() == KeyCode.ENTER) {
                                event.consume();
                                entry.fire();
                            }
                        });
            }
            diagnostics.getChildren().add(entry);
        }
    }

    private void moveCaretTo(int line) {
        draft.requestFocus();
        draft.positionCaret(expert.lineStart(line));
    }

    private void refreshLines() {
        lines.getChildren().clear();
        for (ExpertLine line : expert.lines()) {
            String words = line.description();
            Label description = new Label(words);
            description.setId(UiIds.expertLine(line.line().number()));
            description.setMinWidth(220);
            description.setPrefWidth(220);
            description.setWrapText(true);
            named(description, words);
            if (!line.stateText().isEmpty()) {
                description.setStyle("-fx-font-weight: bold;");
            }
            lines.getChildren().add(new HBox(8, description, highlighted(line.line())));
        }
    }

    /** The line's text in its syntax colours, from the model's spans. */
    private static TextFlow highlighted(ParamsHighlighting.Line line) {
        TextFlow flow = new TextFlow();
        int at = 0;
        for (ParamsHighlighting.Token token : line.tokens()) {
            if (token.start() > at) {
                flow.getChildren().add(plain(line.text().substring(at, token.start())));
            }
            Text part = new Text(line.textOf(token));
            part.setStyle(style(token.kind()));
            flow.getChildren().add(part);
            at = token.end();
        }
        if (at < line.text().length()) {
            flow.getChildren().add(plain(line.text().substring(at)));
        }
        return flow;
    }

    private static Text plain(String text) {
        Text part = new Text(text);
        part.setStyle("-fx-font-family: monospace;");
        return part;
    }

    private static String style(ParamsHighlighting.TokenKind kind) {
        String colour =
                switch (kind) {
                    case VERSION_MARKER, ENZYME_HEADER ->
                            "-fx-fill: #6a3d9a; -fx-font-weight: bold;";
                    case COMMENT, INLINE_COMMENT -> "-fx-fill: #5f6b5f;";
                    case NAME -> "-fx-fill: #1f4e9e;";
                    case VALUE -> "-fx-fill: #8a4b00;";
                    case ENZYME_ROW -> "-fx-fill: #00695c;";
                    case MALFORMED -> "-fx-fill: #b00020; -fx-underline: true;";
                };
        return "-fx-font-family: monospace; " + colour;
    }

    private void applied(EditOutcome outcome) {
        if (outcome.accepted()) {
            applyStatus.setText(
                    "The draft reads. Nothing has changed yet: confirm below to apply it to the"
                            + " configuration.");
        } else {
            applyStatus.setText(outcome.refusal().orElse(""));
        }
    }

    private void confirmed(EditOutcome outcome) {
        applyStatus.setText(
                outcome.accepted()
                        ? "Applied: the configuration now holds the draft."
                        : "Not applied: " + outcome.refusal().orElse(""));
    }

    private void refreshProposal() {
        Optional<RawApplyProposal> waiting = expert.proposal();
        confirmation.setVisible(waiting.isPresent());
        confirmation.setManaged(waiting.isPresent());
        List<String> problemLines = new ArrayList<>();
        List<ExpertLine> named =
                expert.applyErrors().isEmpty() ? List.of() : expert.offendingLines();
        for (ExpertLine line : named) {
            problemLines.add("Line " + line.line().number() + ": " + line.line().text());
        }
        offending.setText(
                problemLines.isEmpty()
                        ? ""
                        : "Offending lines:\n" + String.join("\n", problemLines));
        offending.setVisible(!problemLines.isEmpty());
        offending.setManaged(!problemLines.isEmpty());
        if (waiting.isEmpty()) {
            changes.setText("");
            return;
        }
        RawApplyProposal proposal = waiting.get();
        List<String> words = new ArrayList<>();
        words.add(
                proposal.changes().isEmpty()
                        ? "Applying changes nothing: the draft holds what the configuration"
                                + " holds."
                        : "Applying changes "
                                + proposal.changes().size()
                                + (proposal.changes().size() == 1 ? " value:" : " values:"));
        for (DiffRowView row : proposal.changes()) {
            words.add(row.label() + ": " + row.current() + " -> " + row.other());
        }
        for (String warning : proposal.warnings()) {
            words.add(warning);
        }
        words.addAll(proposal.enforced());
        changes.setText(String.join("\n", words));
    }

    private void refreshCompare() {
        CompareChoice choice = compare.getValue();
        Comparison against =
                choice == null || choice.preset().isEmpty()
                        ? expert.againstDefaults()
                        : expert.againstPreset(choice.preset().get());
        compareRows.setText(describe(against));
        savedRows.setText(describe(expert.againstLastSaved()));
    }

    private static String describe(Comparison comparison) {
        String head = "Compared with " + comparison.against();
        if (comparison.unavailable().isPresent()) {
            return head + ": " + comparison.unavailable().get();
        }
        if (comparison.rows().isEmpty()) {
            return head + ": no differences.";
        }
        List<String> words = new ArrayList<>();
        words.add(
                head
                        + ": "
                        + comparison.rows().size()
                        + (comparison.rows().size() == 1 ? " difference" : " differences")
                        + " (this configuration, then the other).");
        for (DiffRowView row : comparison.rows()) {
            words.add(row.label() + ": " + row.current() + " / " + row.other());
        }
        return String.join("\n", words);
    }

    private void refreshUnknowns() {
        unknowns.getChildren().clear();
        List<UnknownParameter> kept = expert.unknownParameters();
        unknownHeadline.setText(
                kept.isEmpty()
                        ? "The configuration has no unknown parameters."
                        : kept.size()
                                + (kept.size() == 1
                                        ? " unknown parameter is kept"
                                        : " unknown parameters are kept")
                                + " as imported and written back unless removed.");
        for (int index = 0; index < kept.size(); index++) {
            UnknownParameter unknown = kept.get(index);
            List<String> findings =
                    expert.findingsOf(unknown.name()).stream()
                            .map(f -> f.severity().name() + ": " + f.message())
                            .toList();
            String text =
                    unknown.name()
                            + " = "
                            + unknown.value()
                            + (findings.isEmpty() ? "" : " -- " + String.join("; ", findings));
            Label label = new Label(text);
            label.setId(UiIds.expertUnknown(index));
            label.setWrapText(true);
            named(label, "Unknown parameter " + text);
            Button remove =
                    Texts.button(
                            UiIds.expertUnknownRemove(index),
                            "Remove",
                            "Remove the unknown parameter " + unknown.name());
            remove.setOnAction(
                    event -> {
                        EditOutcome outcome = expert.removeUnknown(unknown.name());
                        unknownHeadline.setText(
                                outcome.refusal().orElse("Removed " + unknown.name() + "."));
                    });
            HBox row = new HBox(8, label, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            unknowns.getChildren().add(row);
        }
    }
}
