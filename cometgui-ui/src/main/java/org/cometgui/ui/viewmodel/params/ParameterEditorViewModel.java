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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.writer.WrittenParams;
import org.cometgui.ui.viewmodel.NonNullProperty;

/**
 * The Comet parameter editor's own state, as the views see it: which level is shown ({@link
 * EditorMode}), where a summary entry's field is, the outcome of the last release switch and of the
 * last save in words, file choice for a file-path parameter, and the summary, run readiness, files
 * and migration review over the session.
 *
 * <h2>Collaborators are injected, never handed out</h2>
 *
 * <p>The session, the spectrum inputs and the variable-modification editor are made by the
 * composition root and given to this class and to the views alike, through their constructors;
 * nothing here publishes them. A composition root handing out shared mutable state is what SpotBugs
 * reports as {@code EI_EXPOSE_REP}, and this project's answer is the one {@code
 * ApplicationServices} gives for the message log: inject at the call site, where the sharing is
 * stated. The stateless view-models over the session -- enzymes, tolerance, ranges -- are made on
 * each call: they hold nothing but the session, so two of them are the same editor.
 *
 * <p>Toolkit-free and single-threaded, like every view-model in this package.
 */
public final class ParameterEditorViewModel {

    private final ParameterSession session;

    private final FileChooserPort chooser;

    private final SpectrumInputsViewModel inputs;

    private final ValidationSummaryViewModel summary;

    private final RunReadinessViewModel readiness;

    private final ParameterFilesViewModel files;

    private final MigrationReviewViewModel migrationReview;

    private final PresetsViewModel presets;

    private final NonNullProperty<EditorMode> mode;

    private final NonNullProperty<String> releaseStatus;

    private final NonNullProperty<String> saveStatus;

    private final NonNullProperty<String> importStatus;

    /**
     * The editor's state over a session.
     *
     * @param session the configuration being edited
     * @param inputs the spectrum inputs over the same session, whose chooser sets the database
     * @param chooser the file chooser, for file parameters other than the database and for saving
     * @param build the running build, which a saved file's header names
     * @param hashService the hash port a saved file is hashed through
     * @param engineUnavailable why no run can start, until the workflow engine exists (see {@link
     *     RunReadinessViewModel})
     */
    public ParameterEditorViewModel(
            ParameterSession session,
            SpectrumInputsViewModel inputs,
            FileChooserPort chooser,
            BuildIdentity build,
            HashService hashService,
            Optional<String> engineUnavailable) {
        this.session = Objects.requireNonNull(session, "session");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.chooser = Objects.requireNonNull(chooser, "chooser");
        this.summary = new ValidationSummaryViewModel(session);
        this.readiness = new RunReadinessViewModel(session, engineUnavailable);
        this.files = new ParameterFilesViewModel(session, build, hashService);
        this.migrationReview = new MigrationReviewViewModel(session);
        this.presets = new PresetsViewModel(session, List.of());
        this.mode = new NonNullProperty<>(this, "mode", EditorMode.ESSENTIALS);
        this.releaseStatus = new NonNullProperty<>(this, "releaseStatus", releaseWords());
        this.saveStatus = new NonNullProperty<>(this, "saveStatus", "Not saved yet.");
        this.importStatus =
                new NonNullProperty<>(this, "importStatus", "No parameter file imported yet.");
        session.modelProperty()
                .addListener((observable, before, after) -> releaseStatus.set(releaseWords()));
        session.reviewProperty()
                .addListener((observable, before, after) -> releaseStatus.set(releaseWords()));
    }

    /**
     * The validation summary at the top of the editor.
     *
     * @return the summary
     */
    public ValidationSummaryViewModel summary() {
        return summary;
    }

    /**
     * Whether the Run control may be enabled, and why not.
     *
     * @return the run readiness
     */
    public RunReadinessViewModel readiness() {
        return readiness;
    }

    /**
     * The enzyme selectors, over the session; made on each call, as it holds nothing else.
     *
     * @return the enzymes' view-model
     */
    public EnzymesViewModel enzymes() {
        return new EnzymesViewModel(session);
    }

    /**
     * The compound precursor tolerance and the fragment settings.
     *
     * @return the tolerance view-model, made on each call (it holds the session and the built-in
     *     presets, nothing else)
     */
    public ToleranceViewModel tolerance() {
        return new ToleranceViewModel(session);
    }

    /**
     * The two-value ranges.
     *
     * @return the ranges' view-model, made on each call (it holds the session, nothing else)
     */
    public RangesViewModel ranges() {
        return new RangesViewModel(session);
    }

    /**
     * Saving and importing parameter files.
     *
     * @return the files' view-model
     */
    public ParameterFilesViewModel files() {
        return files;
    }

    /**
     * The migration under review, if any.
     *
     * @return the migration review's view-model
     */
    public MigrationReviewViewModel migrationReview() {
        return migrationReview;
    }

    /**
     * The presets: the built-in instrument-resolution presets, previewed as a reviewable diff.
     *
     * @return the presets' view-model
     */
    public PresetsViewModel presets() {
        return presets;
    }

    /**
     * The editor level shown.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<EditorMode> modeProperty() {
        return mode.getReadOnlyProperty();
    }

    /**
     * The editor level shown.
     *
     * @return the mode
     */
    public EditorMode mode() {
        return mode.get();
    }

    /**
     * Shows another editor level. The configuration is not touched: every level edits the one
     * session.
     *
     * @param next the level to show
     */
    public void setMode(EditorMode next) {
        mode.set(Objects.requireNonNull(next, "next"));
    }

    /**
     * The surface a parameter's field is shown on when a summary entry asks for it, and the mode
     * switched to show it: Essentials while Essentials is shown and has the parameter, otherwise
     * Advanced, which has every parameter of the release.
     *
     * @param name the parameter
     * @return the mode now shown
     * @throws IllegalArgumentException if the selected release does not model the parameter
     */
    public EditorMode showParameter(String name) {
        session.field(name);
        EditorMode target =
                mode.get() == EditorMode.ESSENTIALS && onEssentials(name)
                        ? EditorMode.ESSENTIALS
                        : EditorMode.ADVANCED;
        mode.set(target);
        return target;
    }

    /**
     * Shows a parameter on the Advanced level, which has every parameter of the release: where a
     * search result leads, whichever level is shown.
     *
     * @param name the parameter
     * @return {@link EditorMode#ADVANCED}, the mode now shown
     * @throws IllegalArgumentException if the selected release does not model the parameter
     */
    public EditorMode showInAdvanced(String name) {
        session.field(name);
        mode.set(EditorMode.ADVANCED);
        return EditorMode.ADVANCED;
    }

    /**
     * Whether the Essentials surface of the selected release shows a parameter.
     *
     * @param name the parameter
     * @return {@code true} if one of the Essentials groups holds it
     */
    public boolean onEssentials(String name) {
        for (EssentialsGroup group : session.essentials()) {
            for (FieldViewModel field : group.fields()) {
                if (field.name().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * What the last release choice did, in words: the selected release and, after a switch, the
     * migration's headline; or why the switch was refused.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> releaseStatusProperty() {
        return releaseStatus.getReadOnlyProperty();
    }

    /**
     * What the last release choice did, in words.
     *
     * @return the text
     */
    public String releaseStatus() {
        return releaseStatus.get();
    }

    /**
     * Selects a release, as the release selector asks: the session migrates the configuration, or
     * refuses with its reason, and the release status says which.
     *
     * @param target an offered release
     * @return the session's outcome
     * @throws IllegalArgumentException if the release is not offered
     */
    public EditOutcome selectRelease(ToolVersion target) {
        EditOutcome outcome = session.selectRelease(target);
        releaseStatus.set(
                outcome.refusal()
                        .map(reason -> "Not changed: " + reason)
                        .orElseGet(this::releaseWords));
        return outcome;
    }

    /**
     * Asks the chooser for a file-path parameter's file and sets it: the database through {@link
     * SpectrumInputsViewModel#chooseDatabase()}, any other through {@link
     * FileChooserPort#chooseFile(String)} and the field's own edit.
     *
     * @param field a field of kind {@code FILE_PATH}
     * @return the edit's outcome, or empty if the chooser was cancelled
     */
    public Optional<EditOutcome> chooseFileFor(FieldViewModel field) {
        Objects.requireNonNull(field, "field");
        if (field.name().equals(SpectrumInputsViewModel.DATABASE_PARAMETER)) {
            return inputs.chooseDatabase();
        }
        return chooser.chooseFile(field.displayName()).map(path -> field.setText(path.toString()));
    }

    /**
     * The outcome of the last save, in words.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> saveStatusProperty() {
        return saveStatus.getReadOnlyProperty();
    }

    /**
     * The outcome of the last save, in words.
     *
     * @return the text
     */
    public String saveStatus() {
        return saveStatus.get();
    }

    /**
     * Saves the configuration where the chooser says, through {@link
     * ParameterFilesViewModel#save(Path)}: written once and hashed, or refused with every reason.
     *
     * @return the save's outcome, or empty if the chooser was cancelled (the status then says so)
     */
    public Optional<SaveOutcome> save() {
        Optional<Path> target = chooser.chooseSaveTarget();
        if (target.isEmpty()) {
            saveStatus.set("Not saved: no file was chosen.");
            return Optional.empty();
        }
        SaveOutcome outcome = files.save(target.get());
        saveStatus.set(describe(outcome));
        return Optional.of(outcome);
    }

    /**
     * The outcome of the last import step, in words.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> importStatusProperty() {
        return importStatus.getReadOnlyProperty();
    }

    /**
     * The outcome of the last import step, in words.
     *
     * @return the text
     */
    public String importStatus() {
        return importStatus.get();
    }

    /**
     * Imports the parameter file the chooser gives, through {@link
     * ParameterFilesViewModel#importFile(Path)}: read for the selected release, refused with every
     * error, or -- for a file naming another release -- offered for the scientist's choice ({@link
     * ParameterFilesViewModel#offer()}).
     *
     * @return the import's outcome, or empty if the chooser was cancelled (the status then says so)
     */
    public Optional<ImportOutcome> importFile() {
        Optional<Path> chosen = chooser.chooseParameterFile();
        if (chosen.isEmpty()) {
            importStatus.set("Nothing imported: no file was chosen.");
            return Optional.empty();
        }
        return Optional.of(reported(files.importFile(chosen.get())));
    }

    /**
     * Migrates the file waiting for a choice to the selected release; its report goes under review.
     *
     * @return the outcome
     */
    public ImportOutcome migrateOffered() {
        return reported(files.migrateOffered());
    }

    /**
     * Reads the file waiting for a choice as its own release, switching the editor to it.
     *
     * @return the outcome
     */
    public ImportOutcome readOfferedAsItsRelease() {
        return reported(files.readOfferedAsItsRelease());
    }

    /**
     * Reads the file waiting for a choice as the selected release, with the parser's mismatch
     * warning naming both versions ({@code R-PARAM-06}).
     *
     * @return the outcome
     */
    public ImportOutcome readOfferedAsSelected() {
        return reported(files.readOfferedAsSelected());
    }

    /** Imports nothing: drops the file waiting for a choice. */
    public void dismissOffer() {
        files.dismissOffer();
        importStatus.set("Nothing imported: the file was put aside.");
    }

    private ImportOutcome reported(ImportOutcome outcome) {
        importStatus.set(describe(outcome, session.release()));
        return outcome;
    }

    /**
     * An import step's outcome in words.
     *
     * @param outcome the outcome
     * @param release the release selected after it
     * @return what happened, then the outcome's messages, one per line
     */
    static String describe(ImportOutcome outcome, ToolVersion release) {
        List<String> lines = new ArrayList<>();
        switch (outcome.kind()) {
            case IMPORTED ->
                    lines.add(
                            "Imported as a Comet "
                                    + release.text()
                                    + " parameter file"
                                    + (outcome.messages().isEmpty() ? "." : ", with warnings:"));
            case MIGRATED ->
                    lines.add(
                            "Migrated to Comet "
                                    + release.text()
                                    + "; review the changes below before running:");
            case OFFERED -> lines.add("Not imported yet: choose how to read it.");
            case REFUSED -> lines.add("Not imported:");
        }
        lines.addAll(outcome.messages());
        return String.join("\n", lines);
    }

    /**
     * A save's outcome in words: the file, its size and both digests, or every refusal.
     *
     * @param outcome the outcome
     * @return the text, one reason per line for a refusal
     */
    static String describe(SaveOutcome outcome) {
        if (outcome.written().isEmpty()) {
            return String.join("\n", outcome.refusals());
        }
        WrittenParams written = outcome.written().get();
        return "Saved "
                + written.path()
                + " ("
                + written.size()
                + " bytes; SHA-256 "
                + written.hashes().sha256()
                + ", MD5 "
                + written.hashes().md5()
                + ").";
    }

    /** The release in words, with the migration under review, if any. */
    private String releaseWords() {
        List<String> words = new ArrayList<>();
        words.add("Comet " + session.release().text() + " is selected.");
        if (migrationReview.underReview()) {
            words.add("Migrated: " + migrationReview.headline() + ".");
        }
        return String.join(" ", words);
    }
}
