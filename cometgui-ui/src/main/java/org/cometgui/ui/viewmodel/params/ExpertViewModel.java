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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.Diagnostic;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.UnknownParameter;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.parser.ParamsHighlighting;
import org.cometgui.params.comet.parser.ParseResult;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.params.comet.presets.DiffRow;
import org.cometgui.params.comet.presets.ParameterDiff;
import org.cometgui.params.comet.presets.Preset;
import org.cometgui.params.comet.presets.PresetDiff;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.cometgui.params.comet.writer.ParamsWriteException;
import org.cometgui.ui.viewmodel.NonNullProperty;

/**
 * Expert mode (<em>Expert</em>, {@code AC-PAR-07}, {@code R-PARAM-08}, exit gate item 4): the
 * configuration's canonical raw text, an editable draft of it with its lines classified and
 * diagnosed, diffs, the unknown parameters, and a validating apply that changes the typed
 * configuration only on explicit confirmation.
 *
 * <h2>The raw text is never a second source of truth</h2>
 *
 * <p>The canonical text is {@link CanonicalParamsWriter#write} of the session's model, rewritten
 * whenever the model changes. The draft follows it until the scientist edits it. Applying the draft
 * parses it for the selected release with {@link CometParamsParser}, which is all or nothing: a
 * draft with any error leaves the session's model the same object, and every error is reported with
 * its line numbers and the lines' text. A draft that parses is not adopted either: it is proposed
 * ({@link RawApplyProposal}) with the changes confirming would make, the parse's warnings (a
 * version mismatch among them, {@code R-PARAM-06}) and the draft values the workflow will not take;
 * only {@link #confirm()} adopts it, as {@link Adoption#RAW_APPLIED}, so outputs stay enforced and
 * a migration under review stays under review.
 *
 * <h2>Origins</h2>
 *
 * <p>The parser marks every value it read {@code IMPORTED}. A raw apply is the scientist editing
 * this configuration, not importing a file, so the proposal gives every value the draft changed
 * origin {@code USER} -- the scientist set it -- and every value the draft left as it was keeps the
 * origin it had. A migration entry is therefore resolved by a raw edit of that very parameter, as
 * by any edit of it, and by nothing else.
 *
 * <h2>Highlighting</h2>
 *
 * <p>Each line's kind and spans come from the model ({@link ParamsHighlighting}); nothing here
 * reads a line.
 */
public final class ExpertViewModel {

    private final ParameterSession session;

    private final ParameterFilesViewModel files;

    private final CanonicalParamsWriter writer;

    private final NonNullProperty<String> canonical;

    private final NonNullProperty<Optional<String>> canonicalRefusal;

    private final NonNullProperty<String> draft;

    private final NonNullProperty<Optional<RawApplyProposal>> proposal;

    private final NonNullProperty<List<Diagnostic>> applyErrors;

    /** The last canonical text the writer produced: what an unedited draft holds. */
    private String followed;

    /**
     * Expert mode for a session.
     *
     * @param session the session
     * @param build the running build, whose version the canonical header names
     * @param files saving, whose last saved configuration the draft is compared with
     */
    public ExpertViewModel(
            ParameterSession session, BuildIdentity build, ParameterFilesViewModel files) {
        this.session = Objects.requireNonNull(session, "session");
        this.writer = new CanonicalParamsWriter(Objects.requireNonNull(build, "build"));
        this.files = Objects.requireNonNull(files, "files");
        this.canonical = new NonNullProperty<>(this, "canonical", "");
        this.canonicalRefusal = new NonNullProperty<>(this, "canonicalRefusal", Optional.empty());
        this.proposal = new NonNullProperty<>(this, "proposal", Optional.empty());
        this.applyErrors = new NonNullProperty<>(this, "applyErrors", List.of());
        writeCanonical(session.model());
        this.draft = new NonNullProperty<>(this, "draft", canonical.get());
        this.followed = canonical.get();
        session.modelProperty().addListener((observable, before, after) -> follow(after));
    }

    /**
     * Rewrites the canonical text for a new configuration. A draft the scientist has not edited
     * follows it; a configuration the writer refuses leaves the draft as it was, so the text does
     * not vanish while the refusal is shown. A proposal or a failed apply was a check against the
     * old configuration (and perhaps another release), so both are dropped.
     *
     * <p>A proposal or a failure otherwise lasts until the draft changes ({@link #setDraft}) or the
     * draft is applied again, which replaces it: the same draft parses the same way against the
     * same configuration.
     */
    private void follow(CometParameters model) {
        boolean following = draft.get().equals(followed);
        writeCanonical(model);
        if (canonicalRefusal.get().isEmpty()) {
            if (following) {
                draft.set(canonical.get());
            }
            followed = canonical.get();
        }
        proposal.set(Optional.empty());
        applyErrors.set(List.of());
    }

    /**
     * The canonical text of the configuration.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> canonicalTextProperty() {
        return canonical.getReadOnlyProperty();
    }

    /**
     * The canonical text of the configuration, as the file saved would hold it.
     *
     * @return the text; empty when the writer refuses the configuration
     */
    public String canonicalText() {
        return canonical.get();
    }

    /**
     * Why the writer refuses the configuration (an enzyme number absent from the table).
     *
     * @return the writer's message, or empty when it writes
     */
    public Optional<String> canonicalRefusal() {
        return canonicalRefusal.get();
    }

    /**
     * The draft.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> draftProperty() {
        return draft.getReadOnlyProperty();
    }

    /**
     * The draft.
     *
     * @return the text
     */
    public String draft() {
        return draft.get();
    }

    /**
     * Replaces the draft, as the scientist edited it. The configuration does not change.
     *
     * @param text the whole draft
     */
    public void setDraft(String text) {
        draft.set(Objects.requireNonNull(text, "text"));
        proposal.set(Optional.empty());
        applyErrors.set(List.of());
    }

    /**
     * Throws the draft away: it is the canonical text again (the last one the writer produced, if
     * it now refuses the configuration).
     */
    public void revertDraft() {
        setDraft(followed);
    }

    /**
     * Whether the draft differs from the last canonical text the writer produced.
     *
     * @return {@code true} when the scientist has edited it
     */
    public boolean isDraftEdited() {
        return !draft.get().equals(followed);
    }

    /**
     * Every line of the draft: its kind and spans from the model, and the draft's diagnostics that
     * name it.
     *
     * @return the lines, in order
     */
    public List<ExpertLine> lines() {
        List<Diagnostic> diagnostics = diagnostics();
        List<ExpertLine> lines = new ArrayList<>();
        for (ParamsHighlighting.Line line : ParamsHighlighting.of(draft.get())) {
            lines.add(
                    new ExpertLine(
                            line,
                            diagnostics.stream()
                                    .filter(d -> d.lines().contains(line.number()))
                                    .toList()));
        }
        return List.copyOf(lines);
    }

    /**
     * Where a line of the draft starts, as an offset into the draft: where a diagnostic moves the
     * caret. The lines are the model's ({@link ParamsHighlighting}), each followed by the {@code
     * \n} it was read without.
     *
     * @param number a 1-based line number of the draft
     * @return the offset of the line's first character
     * @throws IllegalArgumentException if the draft has no such line
     */
    public int lineStart(int number) {
        int offset = 0;
        for (ParamsHighlighting.Line line : ParamsHighlighting.of(draft.get())) {
            if (line.number() == number) {
                return offset;
            }
            offset += line.text().length() + 1;
        }
        throw new IllegalArgumentException("the draft has no line " + number);
    }

    /**
     * Every diagnostic of the draft parsed for the selected release, ordered by line.
     *
     * @return the diagnostics
     */
    public List<Diagnostic> diagnostics() {
        return parse().diagnostics();
    }

    /**
     * Every diagnostic of the draft in words.
     *
     * @return one line per diagnostic
     */
    public List<String> diagnosticTexts() {
        return diagnostics().stream().map(ExpertViewModel::describe).toList();
    }

    /**
     * The configuration compared with the selected release's defaults (its own {@code comet -q}).
     *
     * @return the comparison
     */
    public Comparison againstDefaults() {
        return new Comparison(
                "Comet " + session.release().text() + "'s defaults",
                rows(ParameterDiff.between(session.model(), defaults())),
                Optional.empty());
    }

    /**
     * The configuration compared with a preset: the selected release's defaults with every row of
     * the preset applied.
     *
     * @param preset the preset
     * @return the comparison
     */
    public Comparison againstPreset(Preset preset) {
        Objects.requireNonNull(preset, "preset");
        CometParameters withPreset = PresetDiff.of(defaults(), preset).applyAll().model();
        return new Comparison(
                "Comet "
                        + session.release().text()
                        + "'s defaults with preset "
                        + preset.displayName(),
                rows(ParameterDiff.between(session.model(), withPreset)),
                Optional.empty());
    }

    /**
     * The configuration compared with the one last saved.
     *
     * @return the comparison, or why it cannot be made
     */
    public Comparison againstLastSaved() {
        String against = "the last saved configuration";
        Optional<CometParameters> saved = files.lastSaved();
        if (saved.isEmpty()) {
            return Comparison.unavailable(against, "Nothing has been saved yet.");
        }
        CometParameters current = session.model();
        if (!saved.get().version().equals(current.version())) {
            return Comparison.unavailable(
                    against,
                    "The last saved configuration is for Comet "
                            + saved.get().version().text()
                            + " and this one is for Comet "
                            + current.version().text()
                            + "; configurations of two releases are compared by migrating one,"
                            + " not by a diff.");
        }
        return new Comparison(
                against, rows(ParameterDiff.between(current, saved.get())), Optional.empty());
    }

    /**
     * The configuration's unknown parameters ({@code R-PARAM-07}): kept as imported and written
     * back unless removed.
     *
     * @return the parameters, in the configuration's order
     */
    public List<UnknownParameter> unknownParameters() {
        return session.model().unknownParameters();
    }

    /**
     * The findings about one unknown parameter, from the session's report.
     *
     * @param name the parameter's name
     * @return its findings, in report order
     */
    public List<Finding> findingsOf(String name) {
        return session.report().forParameter(Objects.requireNonNull(name, "name"));
    }

    /**
     * Removes one unknown parameter from the configuration.
     *
     * @param name the parameter's name
     * @return accepted, or why not
     */
    public EditOutcome removeUnknown(String name) {
        return session.removeUnknown(name);
    }

    /**
     * The proposal waiting for confirmation.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<Optional<RawApplyProposal>> proposalProperty() {
        return proposal.getReadOnlyProperty();
    }

    /**
     * The proposal waiting for confirmation.
     *
     * @return the proposal, or empty
     */
    public Optional<RawApplyProposal> proposal() {
        return proposal.get();
    }

    /**
     * The errors of the last apply that failed.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<List<Diagnostic>> applyErrorsProperty() {
        return applyErrors.getReadOnlyProperty();
    }

    /**
     * The errors of the last apply that failed; empty after one that parsed.
     *
     * @return the errors, ordered by line
     */
    public List<Diagnostic> applyErrors() {
        return applyErrors.get();
    }

    /**
     * The lines the last failed apply's errors name, in line order: the offending lines.
     *
     * @return the lines with their diagnostics
     */
    public List<ExpertLine> offendingLines() {
        Set<Integer> named = new LinkedHashSet<>();
        for (Diagnostic error : applyErrors.get()) {
            named.addAll(error.lines());
        }
        return lines().stream().filter(l -> named.contains(l.line().number())).toList();
    }

    /**
     * Checks the draft: parses it for the selected release. Nothing changes either way. With an
     * error, the errors are kept ({@link #applyErrors()}) and the refusal names each with its
     * lines; without one, the proposal is kept for {@link #confirm()}.
     *
     * @return accepted when the draft parsed and awaits confirmation, else why not
     */
    public EditOutcome apply() {
        ParseResult parsed = parse();
        if (!parsed.succeeded()) {
            applyErrors.set(parsed.errors());
            List<String> lines = new ArrayList<>();
            lines.add("The draft was not applied; the configuration is unchanged.");
            for (Diagnostic error : parsed.errors()) {
                lines.add(describe(error));
            }
            return EditOutcome.refused(String.join("\n", lines));
        }
        CometParameters current = session.model();
        CometParameters read = parsed.model().orElseThrow();
        CometParameters merged = read;
        for (ParameterEntry entry : read.entries()) {
            String name = entry.name();
            boolean unchanged = entry.value().equals(current.value(name));
            merged = merged.withOrigin(name, unchanged ? current.origin(name) : ValueOrigin.USER);
        }
        CometParameters proposed = merged.withWorkflowEnforcedOutputs();
        List<String> enforced = new ArrayList<>();
        for (ParameterEntry entry : merged.entries()) {
            String name = entry.name();
            if (!entry.value().equals(proposed.value(name))) {
                enforced.add(
                        name
                                + " = "
                                + merged.text(name)
                                + " in the draft is applied as "
                                + proposed.text(name)
                                + ". "
                                + session.lockOf(name).orElse("Required by CometGUI workflow")
                                + ".");
            }
        }
        proposal.set(
                Optional.of(
                        new RawApplyProposal(
                                current,
                                proposed,
                                rows(ParameterDiff.between(current, proposed)),
                                parsed.warnings().stream().map(ExpertViewModel::describe).toList(),
                                enforced)));
        return EditOutcome.applied();
    }

    /**
     * Adopts the proposal: the explicit confirmation a raw edit needs before it changes the typed
     * configuration. The draft becomes the new canonical text.
     *
     * @return accepted, or refused when nothing awaits confirmation or the configuration changed
     *     after the draft was checked
     */
    public EditOutcome confirm() {
        Optional<RawApplyProposal> waiting = proposal.get();
        if (waiting.isEmpty()) {
            return EditOutcome.refused(
                    "Nothing is waiting for confirmation; apply the draft first.");
        }
        proposal.set(Optional.empty());
        if (waiting.get().base() != session.model()) {
            return EditOutcome.refused(
                    "The configuration changed after the draft was checked; apply it again.");
        }
        session.adopt(waiting.get().proposed(), Adoption.RAW_APPLIED);
        draft.set(followed);
        return EditOutcome.applied();
    }

    /** Drops the proposal; the configuration is not touched. */
    public void cancelApply() {
        proposal.set(Optional.empty());
    }

    /**
     * One diagnostic in words: its severity, the lines it names, and the parser's message.
     *
     * @param diagnostic the diagnostic
     * @return for example {@code Error, line 7: line 7 is not ...}; {@code Warning: ...} for one
     *     about the file as a whole
     */
    static String describe(Diagnostic diagnostic) {
        String severity = diagnostic.isError() ? "Error" : "Warning";
        List<Integer> lines = diagnostic.lines();
        String where;
        if (lines.isEmpty()) {
            where = "";
        } else if (lines.size() == 1) {
            where = ", line " + lines.get(0);
        } else {
            where =
                    ", lines "
                            + String.join(
                                    ", ", lines.stream().map(n -> Integer.toString(n)).toList());
        }
        return severity + where + ": " + diagnostic.message();
    }

    private ParseResult parse() {
        return new CometParamsParser(session.metadata(), session.release()).parse(draft.get());
    }

    private CometParameters defaults() {
        return ReleaseDefaults.load(session.metadata(), session.release());
    }

    private List<DiffRowView> rows(List<DiffRow> diff) {
        return diff.stream().map(row -> DiffRowView.of(row, session)).toList();
    }

    private void writeCanonical(CometParameters model) {
        try {
            canonical.set(writer.write(model));
            canonicalRefusal.set(Optional.empty());
        } catch (ParamsWriteException refused) {
            canonical.set("");
            canonicalRefusal.set(Optional.of(refused.getMessage()));
        }
    }
}
