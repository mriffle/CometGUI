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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.migration.MigrationEntry;
import org.cometgui.params.comet.migration.MigrationResult;
import org.cometgui.params.comet.migration.MigrationReview;
import org.cometgui.params.comet.migration.SchemaMigration;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ParameterEntry;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.validation.CometValidator;
import org.cometgui.params.comet.validation.ValidationReport;
import org.cometgui.params.comet.validation.WorkflowOutputs;
import org.cometgui.params.comet.value.EnzymeTable;
import org.cometgui.params.comet.value.ValueSyntaxException;
import org.cometgui.ui.viewmodel.NonNullProperty;

/**
 * The Comet parameter editor's session: the one configuration being edited, the release it is for,
 * a migration under review, and one field per parameter.
 *
 * <h2>The model is the source of truth (decision P7-1)</h2>
 *
 * <p>The configuration is an immutable {@link CometParameters}; every change replaces it, and every
 * change is one of the model's own operations -- {@code withText(name, text, USER)}, {@code
 * resetToDefault}, {@code withDecoySource}, {@code withWorkflowEnforcedOutputs}, {@link
 * SchemaMigration#migrate}. Nothing here reads a number, splits a tuple or judges a value. The one
 * validation report the editor shows is {@link MigrationReview#validate} when a migration is under
 * review and {@code CometValidator.standard().validate} otherwise; it is recomputed after every
 * change and never assembled here.
 *
 * <h2>Every adopted set has the workflow's outputs on (decision P7-7)</h2>
 *
 * <p>A new configuration, an imported file, an Expert raw apply, a preset and a migration are all
 * adopted through {@link #adopt} or {@link #adoptMigration}, which apply {@code
 * withWorkflowEnforcedOutputs()}. While a stage that reads an output is enabled ({@link
 * StageSwitches}), its field is locked and every edit or reset of it is refused with the reason.
 *
 * <h2>Releases (decision P7-2)</h2>
 *
 * <p>The releases offered are given by the caller -- the composition root -- in the order to show
 * them, the first being the default; none is named here. A new configuration starts from the
 * release's own {@code comet -q} output ({@link ReleaseDefaults}). Selecting another release
 * migrates the configuration ({@link SchemaMigration}) and keeps the migration's review, whose
 * unresolved entries are errors in the report and so block a run until the scientist resolves them
 * ({@link #resolve(String)}, or a value set on that parameter).
 *
 * <h2>Origin {@code USER} is earned</h2>
 *
 * <p>A migration review treats a {@code USER} origin as the scientist's decision on that parameter,
 * so an edit that would not change the value -- a control re-committing the text it already shows
 * -- changes nothing, origin included. Only a value the scientist actually changed on that
 * parameter is marked {@code USER}.
 *
 * <p>Toolkit-free and single-threaded: every call is made on the interface thread, and nothing here
 * runs in the background.
 */
public final class ParameterSession {

    private final CuratedMetadata metadata;

    private final List<ToolVersion> offered;

    private StageSwitches stages;

    private final NonNullProperty<CometParameters> model;

    private final NonNullProperty<ValidationReport> report;

    private final NonNullProperty<ToolVersion> release;

    private final NonNullProperty<Optional<MigrationReview>> review;

    private final NonNullProperty<List<FieldViewModel>> fields;

    private final NonNullProperty<List<FieldViewModel>> pendingRefusals;

    private Map<String, FieldViewModel> byName = Map.of();

    /**
     * A session holding a new configuration of the first offered release.
     *
     * @param metadata the curated metadata
     * @param offeredReleases the releases to offer, in the order to show them, the default first;
     *     each must have curated metadata and a bundled starting set
     * @param stages which downstream stages are enabled
     * @throws IllegalArgumentException if no release is offered, one is offered twice, or one has
     *     no metadata or no starting set, naming it
     */
    public ParameterSession(
            CuratedMetadata metadata, List<ToolVersion> offeredReleases, StageSwitches stages) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.offered = List.copyOf(offeredReleases);
        this.stages = Objects.requireNonNull(stages, "stages");
        if (offered.isEmpty()) {
            throw new IllegalArgumentException(
                    "the parameter editor needs at least one Comet release to offer");
        }
        Set<ToolVersion> seen = new HashSet<>();
        for (ToolVersion candidate : offered) {
            if (!seen.add(candidate)) {
                throw new IllegalArgumentException(
                        "Comet " + candidate.text() + " is offered twice");
            }
            if (metadata.version(candidate).isEmpty()) {
                throw new IllegalArgumentException(
                        "Comet "
                                + candidate.text()
                                + " cannot be offered: the parameter metadata was not curated"
                                + " against it");
            }
            if (!ReleaseDefaults.isBundled(candidate)) {
                throw new IllegalArgumentException(
                        "Comet "
                                + candidate.text()
                                + " cannot be offered: it has no bundled starting set");
            }
        }
        ToolVersion first = offered.get(0);
        CometParameters start = ReleaseDefaults.load(metadata, first).withWorkflowEnforcedOutputs();
        this.model = new NonNullProperty<>(this, "model", start);
        this.release = new NonNullProperty<>(this, "release", first);
        this.review = new NonNullProperty<>(this, "review", Optional.empty());
        this.report = new NonNullProperty<>(this, "report", validate(start, Optional.empty()));
        this.fields = new NonNullProperty<>(this, "fields", List.of());
        this.pendingRefusals = new NonNullProperty<>(this, "pendingRefusals", List.of());
        rebuildFields(start);
        commit(start);
    }

    /**
     * The releases offered, in the order given; the first is the default.
     *
     * @return the releases
     */
    public List<ToolVersion> offeredReleases() {
        return offered;
    }

    /**
     * The curated metadata the session was built with.
     *
     * @return the metadata
     */
    public CuratedMetadata metadata() {
        return metadata;
    }

    /**
     * The configuration being edited.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<CometParameters> modelProperty() {
        return model.getReadOnlyProperty();
    }

    /**
     * The configuration being edited.
     *
     * @return the model
     */
    public CometParameters model() {
        return model.get();
    }

    /**
     * The one validation report of the configuration.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<ValidationReport> reportProperty() {
        return report.getReadOnlyProperty();
    }

    /**
     * The one validation report of the configuration.
     *
     * @return the report
     */
    public ValidationReport report() {
        return report.get();
    }

    /**
     * The selected release: the configuration's Comet version.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<ToolVersion> releaseProperty() {
        return release.getReadOnlyProperty();
    }

    /**
     * The selected release.
     *
     * @return the Comet version
     */
    public ToolVersion release() {
        return release.get();
    }

    /**
     * The migration under review, present after a release switch or an adopted migration until a
     * new configuration starts.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<Optional<MigrationReview>> reviewProperty() {
        return review.getReadOnlyProperty();
    }

    /**
     * The migration under review.
     *
     * @return the review, or empty
     */
    public Optional<MigrationReview> review() {
        return review.get();
    }

    /**
     * The entries of the migration under review that still need the scientist.
     *
     * @return the unresolved entries in report order; empty when nothing is under review
     */
    public List<MigrationEntry> unresolved() {
        return review.get().map(r -> r.unresolved(model.get())).orElse(List.of());
    }

    /**
     * One field per parameter of the selected release, in the release's order. Replaced, with new
     * fields, when the release changes.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<List<FieldViewModel>> fieldsProperty() {
        return fields.getReadOnlyProperty();
    }

    /**
     * The fields of the selected release.
     *
     * @return the fields, immutable
     */
    public List<FieldViewModel> fields() {
        return fields.get();
    }

    /**
     * The fields holding an edit the model refused, in field order: each shows text the
     * configuration does not hold, so each blocks a run until the refusal is cleared (by an
     * accepted edit, a reset, an adopted set or a release change).
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<List<FieldViewModel>> pendingRefusalsProperty() {
        return pendingRefusals.getReadOnlyProperty();
    }

    /**
     * The fields holding an edit the model refused, in field order.
     *
     * @return the fields, immutable
     */
    public List<FieldViewModel> pendingRefusals() {
        return pendingRefusals.get();
    }

    /**
     * One parameter's field.
     *
     * @param name the parameter name
     * @return the field
     * @throws IllegalArgumentException if the selected release does not model the parameter
     */
    public FieldViewModel field(String name) {
        FieldViewModel field = byName.get(Objects.requireNonNull(name, "name"));
        if (field == null) {
            throw new IllegalArgumentException(
                    "Comet " + release.get().text() + " has no parameter named " + name);
        }
        return field;
    }

    /**
     * The display name of a parameter of the selected release.
     *
     * @param name the parameter name
     * @return the field's display name, or empty if the release does not model it
     */
    public Optional<String> displayNameOf(String name) {
        return Optional.ofNullable(byName.get(name)).map(FieldViewModel::displayName);
    }

    /**
     * The Essentials surface for the selected release: every {@link EssentialsSection}, in order,
     * with its fields.
     *
     * @return the groups
     */
    public List<EssentialsGroup> essentials() {
        List<ParameterDefinition> definitions = definitions();
        List<EssentialsGroup> groups = new ArrayList<>();
        for (EssentialsSection section : EssentialsSection.values()) {
            groups.add(
                    new EssentialsGroup(
                            section,
                            section.parameters(definitions).stream().map(this::field).toList()));
        }
        return List.copyOf(groups);
    }

    /**
     * Advanced mode for the selected release: the fourteen categories in the specification's order,
     * each with every field of that category.
     *
     * @return the groups
     */
    public List<AdvancedCategory> advanced() {
        List<AdvancedCategory> groups = new ArrayList<>();
        for (ParameterCategory category : ParameterCategory.values()) {
            groups.add(new AdvancedCategory(category, fieldsOf(category)));
        }
        return List.copyOf(groups);
    }

    /**
     * Sets one parameter from text, as the scientist entered it, with origin {@code USER}.
     *
     * <p>Refused, and the configuration unchanged, when the field is locked (the lock's reason) or
     * when the model cannot read the text as the parameter's kind (the model's own message); the
     * field then shows the refusal and the refused text. Text that reads as the value the
     * configuration already holds changes nothing, origin included.
     *
     * @param name the parameter name
     * @param text the value text, as it would appear after {@code name = }
     * @return whether the configuration now holds it, or why not
     * @throws IllegalArgumentException if the selected release does not model the parameter
     */
    public EditOutcome edit(String name, String text) {
        Objects.requireNonNull(text, "text");
        FieldViewModel field = field(name);
        Optional<String> lock = lockOf(name);
        if (lock.isPresent()) {
            return refuseLocked(field, lock.get());
        }
        CometParameters current = model.get();
        CometParameters candidate;
        try {
            candidate = current.withText(name, text, ValueOrigin.USER);
        } catch (ValueSyntaxException unreadable) {
            field.refuse(text, unreadable.getMessage());
            return EditOutcome.refused(unreadable.getMessage());
        }
        field.clearRefusal();
        if (!candidate.value(name).equals(current.value(name))) {
            commit(candidate);
        }
        return EditOutcome.applied();
    }

    /**
     * Sets one parameter to a typed value the model made, as the scientist chose it, with origin
     * {@code USER}: what a structured editor calls after the model has read its texts ({@code
     * VariableModSlots.withPart}, {@code CometParameters.rangeValue}) or for a choice that is a
     * value already ({@code Flag}, an enzyme row's number). The same rules as {@link #edit}: a
     * locked field is refused with its reason, and a value equal to the one held changes nothing,
     * origin included.
     *
     * @param name the parameter name
     * @param value the new value, of the variant the parameter's kind holds
     * @return whether the configuration now holds it, or why not
     * @throws IllegalArgumentException if the selected release does not model the parameter, or the
     *     value is of the wrong variant
     */
    EditOutcome setValue(String name, ParameterValue value) {
        Map<String, ParameterValue> one = new LinkedHashMap<>();
        one.put(name, Objects.requireNonNull(value, "value"));
        return setValues(one);
    }

    /**
     * Sets several parameters at once, as one change of the configuration: every value origin
     * {@code USER}, or nothing at all if any field is locked. A value equal to the one held changes
     * nothing for that parameter, origin included. Used where one action of the scientist changes
     * two parameters, such as moving a variable modification to another slot.
     *
     * @param values the new values by parameter name, in the order to check them
     * @return whether the configuration now holds them all, or why not (the first lock met)
     * @throws IllegalArgumentException if the selected release does not model a parameter, or a
     *     value is of the wrong variant
     */
    EditOutcome setValues(Map<String, ParameterValue> values) {
        List<FieldViewModel> touched = new ArrayList<>();
        for (String name : values.keySet()) {
            FieldViewModel field = field(name);
            Optional<String> lock = lockOf(name);
            if (lock.isPresent()) {
                return refuseLocked(field, lock.get());
            }
            touched.add(field);
        }
        CometParameters current = model.get();
        CometParameters next = current;
        for (Map.Entry<String, ParameterValue> change : values.entrySet()) {
            if (!change.getValue().equals(current.value(change.getKey()))) {
                next = next.withValue(change.getKey(), change.getValue(), ValueOrigin.USER);
            }
        }
        for (FieldViewModel field : touched) {
            field.clearRefusal();
        }
        if (next != current) {
            commit(next);
        }
        return EditOutcome.applied();
    }

    /**
     * Replaces the enzyme table, as the scientist edited it: a custom row added, or a row removed.
     * The model's {@code enzyme_in_table} rule reports any selected number the new table lacks.
     *
     * @param table the new table
     */
    void setEnzymeTable(EnzymeTable table) {
        Objects.requireNonNull(table, "table");
        if (!table.equals(model.get().enzymeTable())) {
            commit(model.get().withEnzymeTable(table));
        }
    }

    /**
     * Removes an unknown parameter the configuration carries, as the scientist decided in Expert
     * mode ({@code R-PARAM-07}: kept and written back unless the user explicitly removes it),
     * through the model's {@code withoutUnknown}.
     *
     * @param name the unknown parameter's name
     * @return accepted, or refused when the configuration carries no unknown parameter of that name
     */
    public EditOutcome removeUnknown(String name) {
        Objects.requireNonNull(name, "name");
        CometParameters current = model.get();
        if (current.unknownParameters().stream().noneMatch(u -> u.name().equals(name))) {
            return EditOutcome.refused(
                    name
                            + " is not an unknown parameter of this configuration, so there is"
                            + " nothing to remove");
        }
        commit(current.withoutUnknown(name));
        return EditOutcome.applied();
    }

    /**
     * Shows a structured editor's refused input at a parameter's field, so that it blocks a run and
     * is listed in the summary as any refused edit is ({@link #pendingRefusals()}).
     *
     * @param name the parameter name
     * @param refusedText what the scientist entered, as the editor shows it
     * @param message the model's own message
     * @return the refused outcome carrying the message
     */
    EditOutcome refuse(String name, String refusedText, String message) {
        field(name).refuse(refusedText, message);
        return EditOutcome.refused(message);
    }

    /**
     * Puts one parameter back to the selected release's default, origin {@code COMET_DEFAULT}.
     *
     * @param name the parameter name
     * @return whether it was reset, or why not (a locked field is refused with its reason)
     * @throws IllegalArgumentException if the selected release does not model the parameter
     */
    public EditOutcome resetField(String name) {
        FieldViewModel field = field(name);
        Optional<String> lock = lockOf(name);
        if (lock.isPresent()) {
            return refuseLocked(field, lock.get());
        }
        field.clearRefusal();
        commit(model.get().resetToDefault(name));
        return EditOutcome.applied();
    }

    /**
     * Puts every parameter of one category back to the selected release's default. A locked field
     * keeps its enforced value.
     *
     * @param category the category
     * @return the parameters reset, in the release's order
     */
    public List<String> resetCategory(ParameterCategory category) {
        Objects.requireNonNull(category, "category");
        CometParameters next = model.get();
        List<String> reset = new ArrayList<>();
        for (FieldViewModel field : fieldsOf(category)) {
            if (lockOf(field.name()).isEmpty()) {
                next = next.resetToDefault(field.name());
                field.clearRefusal();
                reset.add(field.name());
            }
        }
        commit(next);
        return List.copyOf(reset);
    }

    /**
     * Starts again from the selected release's starting set, outputs enforced. The migration under
     * review, if any, is dropped with the configuration it was about.
     */
    public void resetAll() {
        adopt(ReleaseDefaults.load(metadata, release.get()), Adoption.NEW);
    }

    /**
     * A new configuration of an offered release: its starting set, outputs enforced.
     *
     * @param target the release
     * @throws IllegalArgumentException if the release is not offered
     */
    public void newConfiguration(ToolVersion target) {
        requireOffered(target);
        adopt(ReleaseDefaults.load(metadata, target), Adoption.NEW);
    }

    /**
     * Adopts a whole parameter set, with the workflow's outputs enforced (decision P7-7).
     *
     * @param adopted the set
     * @param source where it came from: a set that {@linkplain Adoption#startsAfresh() starts
     *     afresh} may be of any offered release and drops a migration under review; an edit of the
     *     configuration must be of the selected release and keeps it
     * @throws IllegalArgumentException if the set's release is not offered, or, for an edit, is not
     *     the selected release
     */
    public void adopt(CometParameters adopted, Adoption source) {
        Objects.requireNonNull(adopted, "adopted");
        Objects.requireNonNull(source, "source");
        ToolVersion version = adopted.version();
        if (source.startsAfresh()) {
            requireOffered(version);
            review.set(Optional.empty());
        } else if (!version.equals(release.get())) {
            throw new IllegalArgumentException(
                    "a set of Comet "
                            + version.text()
                            + " cannot be applied to a configuration of Comet "
                            + release.get().text());
        }
        replace(adopted.withWorkflowEnforcedOutputs());
    }

    /**
     * Adopts a migration: its migrated set, outputs enforced, under review.
     *
     * @param migration the migration, to an offered release
     * @throws IllegalArgumentException if the migration's target is not offered
     */
    public void adoptMigration(MigrationResult migration) {
        Objects.requireNonNull(migration, "migration");
        requireOffered(migration.model().version());
        review.set(Optional.of(MigrationReview.of(migration)));
        replace(migration.model().withWorkflowEnforcedOutputs());
    }

    /**
     * Selects a release: migrates the configuration to it and puts the migration under review.
     *
     * <p>Refused while the migration already under review has unresolved entries: migrating again
     * would replace that review, and with it the record of the values the scientist has not yet
     * decided about.
     *
     * @param target an offered release
     * @return whether the release is now selected, or why not
     * @throws IllegalArgumentException if the release is not offered
     */
    public EditOutcome selectRelease(ToolVersion target) {
        requireOffered(target);
        if (target.equals(release.get())) {
            return EditOutcome.applied();
        }
        List<MigrationEntry> open = unresolved();
        if (!open.isEmpty()) {
            MigrationReview current = review.get().orElseThrow();
            return EditOutcome.refused(
                    "Resolve the migration from Comet "
                            + current.report().from().text()
                            + " to Comet "
                            + current.report().to().text()
                            + " before selecting another release; "
                            + open.size()
                            + " still need your decision: "
                            + open.stream().map(MigrationEntry::parameter).toList());
        }
        adoptMigration(SchemaMigration.migrate(model.get(), target));
        return EditOutcome.applied();
    }

    /**
     * The scientist accepts the value the configuration holds for one entry of the migration under
     * review.
     *
     * @param name the entry's parameter
     * @throws IllegalStateException if no migration is under review
     * @throws IllegalArgumentException from the review, naming the parameter, if it is not an entry
     *     that needs attention or was already acknowledged
     */
    public void resolve(String name) {
        MigrationReview current =
                review.get()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "no migration is under review, so there is nothing"
                                                        + " to resolve"));
        review.set(Optional.of(current.resolve(name)));
        commit(model.get());
    }

    /**
     * The decoy source ({@code R-DEC-01}) the configuration's {@code decoy_search} means.
     *
     * @return the source, or empty for a value Comet does not document
     */
    public Optional<DecoySource> decoySource() {
        return model.get().decoySource();
    }

    /**
     * The choices of the single decoy control, in the order of {@link DecoySource}, each with the
     * selected release's words for its {@code decoy_search} value.
     *
     * @return the options
     */
    public List<DecoyOption> decoyOptions() {
        List<ChoiceOption> choices = field(DecoySource.PARAMETER).choices();
        List<DecoyOption> options = new ArrayList<>();
        for (DecoySource source : DecoySource.values()) {
            String token = Integer.toString(source.decoySearch());
            for (ChoiceOption choice : choices) {
                if (choice.token().equals(token)) {
                    options.add(new DecoyOption(source, token, choice.label()));
                }
            }
        }
        return List.copyOf(options);
    }

    /**
     * Sets the decoy source, as the scientist chose it: {@code decoy_search} through the model's
     * {@code withDecoySource}, origin {@code USER}. Choosing the source already set changes
     * nothing.
     *
     * @param source the decoy source
     * @return the outcome, always accepted
     */
    public EditOutcome setDecoySource(DecoySource source) {
        Objects.requireNonNull(source, "source");
        field(DecoySource.PARAMETER).clearRefusal();
        if (!model.get().decoySource().equals(Optional.of(source))) {
            commit(model.get().withDecoySource(source, ValueOrigin.USER));
        }
        return EditOutcome.applied();
    }

    /**
     * Changes which downstream stages are enabled, and so which outputs are locked. An output that
     * becomes locked is switched back on by the model's {@code withWorkflowEnforcedOutputs()}, so a
     * stage is never enabled with its input off; an output that becomes unlocked keeps its value.
     *
     * <p>Model-level validation still assumes every stage is enabled ({@code
     * workflow_enforced.output_off} is an error whatever the switches say): no stage can be
     * disabled before Phases 11 and 12, and teaching the validator otherwise is theirs.
     *
     * @param switches the stages now enabled
     */
    public void setStageSwitches(StageSwitches switches) {
        this.stages = Objects.requireNonNull(switches, "switches");
        CometParameters current = model.get();
        CometParameters enforced = current.withWorkflowEnforcedOutputs();
        CometParameters next = current;
        for (FieldViewModel field : fields.get()) {
            String name = field.name();
            if (lockOf(name).isPresent()) {
                next = next.withValue(name, enforced.value(name), enforced.origin(name));
                field.clearRefusal();
            }
        }
        commit(next);
    }

    /**
     * Why a parameter is locked: an output the workflow requires while a stage that reads it is
     * enabled.
     *
     * @param name the parameter name
     * @return the reason in words, or empty when it may be changed
     */
    public Optional<String> lockOf(String name) {
        Optional<String> stage = WorkflowOutputs.stageNeeding(name);
        if (stage.isEmpty() || !stages.dependentStageEnabled(name)) {
            return Optional.empty();
        }
        return Optional.of("Required by CometGUI workflow: " + stage.get());
    }

    private EditOutcome refuseLocked(FieldViewModel field, String lock) {
        String message =
                field.displayName()
                        + " cannot be changed while the stage that needs it is enabled. "
                        + lock
                        + ".";
        return EditOutcome.refused(message);
    }

    private void requireOffered(ToolVersion candidate) {
        if (!offered.contains(Objects.requireNonNull(candidate, "release"))) {
            throw new IllegalArgumentException(
                    "Comet "
                            + candidate.text()
                            + " is not offered; the offered releases are "
                            + offered.stream().map(ToolVersion::text).toList());
        }
    }

    private List<ParameterDefinition> definitions() {
        List<ParameterDefinition> definitions = new ArrayList<>();
        for (FieldViewModel field : fields.get()) {
            definitions.add(field.definition());
        }
        return definitions;
    }

    private List<FieldViewModel> fieldsOf(ParameterCategory category) {
        return fields.get().stream().filter(f -> f.category() == category).toList();
    }

    /** Replaces the whole configuration: new fields if the release changed, no refusal pending. */
    private void replace(CometParameters next) {
        boolean newRelease = !next.version().equals(release.get());
        release.set(next.version());
        if (newRelease) {
            rebuildFields(next);
        } else {
            for (FieldViewModel field : fields.get()) {
                field.clearRefusal();
            }
        }
        commit(next);
    }

    private void rebuildFields(CometParameters next) {
        ToolVersion version = next.version();
        Map<String, FieldViewModel> built = new LinkedHashMap<>();
        for (ParameterEntry entry : next.entries()) {
            ParameterDefinition definition =
                    metadata.parameter(entry.name(), version).orElseThrow();
            built.put(entry.name(), new FieldViewModel(this, definition, version));
        }
        byName = built;
        fields.set(List.copyOf(built.values()));
        for (FieldViewModel field : built.values()) {
            field.refusalProperty().addListener((observable, before, after) -> collectRefusals());
        }
        collectRefusals();
    }

    private void collectRefusals() {
        pendingRefusals.set(
                fields.get().stream().filter(field -> field.refusal().isPresent()).toList());
    }

    /**
     * Makes a configuration current: its report, then every field, then the model property, so a
     * listener on the model sees fields that already show it.
     */
    private void commit(CometParameters next) {
        ValidationReport nextReport = validate(next, review.get());
        for (FieldViewModel field : fields.get()) {
            field.show(next, nextReport, lockOf(field.name()));
        }
        report.set(nextReport);
        model.set(next);
    }

    private static ValidationReport validate(
            CometParameters configuration, Optional<MigrationReview> underReview) {
        return underReview
                .map(r -> r.validate(configuration))
                .orElseGet(() -> CometValidator.standard().validate(configuration));
    }
}
