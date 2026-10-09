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

package org.cometgui.ui.viewmodel.percolator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.AdvisoryRendering;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.resolution.PercolatorResolution;
import org.cometgui.params.percolator.resolution.PercolatorResolver;
import org.cometgui.params.percolator.resolution.ResolutionChange;
import org.cometgui.params.percolator.resolution.SkippedVersion;
import org.cometgui.params.percolator.resolution.StageAvailability;
import org.cometgui.params.percolator.resolution.StageRemedy;
import org.cometgui.params.percolator.schema.PercolatorSetting;
import org.cometgui.params.percolator.schema.SettingsApplicability;
import org.cometgui.params.percolator.validation.TestFdr;
import org.cometgui.params.percolator.validation.TrainFdr;
import org.cometgui.results.filtering.DisplayFilters;
import org.cometgui.ui.viewmodel.NonNullProperty;
import org.cometgui.ui.viewmodel.params.FileChooserPort;
import org.cometgui.ui.viewmodel.params.PercolatorRequest;
import org.cometgui.ui.viewmodel.results.DisplayFiltersViewModel;

/**
 * The Percolator section (decision P9-12; {@code R-PERC-01}, {@code R-PERC-03}, {@code R-PERC-04},
 * {@code R-PERC-10}, {@code R-PERC-11}, {@code R-RES-01}): which Percolator runs, why, what it can
 * do and what it cannot, and the settings and filters around it.
 *
 * <h2>Which Percolator runs</h2>
 *
 * <p>The builds are the Tool Manager's ({@link PercolatorPort#offers()}); the default is {@link
 * PercolatorResolver}'s, over those builds and the enabled downstream stages -- here, whether
 * Limelight conversion is switched on. The version selector lists the builds that can run now (an
 * installed managed build, a registered local binary) with the default marked; a managed build that
 * is not installed is named as installable from the Tool Manager, never offered for running. The
 * build that runs is the scientist's choice when they made one, and the default otherwise; choosing
 * the default again, or {@link #useDefault()}, follows the default from then on.
 *
 * <p>Switching Limelight conversion re-resolves and states the {@link ResolutionChange}: from which
 * version to which, and the capability that made the difference ({@code R-PERC-02}). Every reason
 * and every skipped newer version is the resolution's own sentence -- the words provenance records
 * ({@code R-PERC-10}).
 *
 * <h2>Never a version number</h2>
 *
 * <p>Nothing here compares versions or decides by one (P9-2): which build is the default, what it
 * lacks, which settings it accepts and whether Limelight can run are read from the model over the
 * builds' observed capabilities. A version is only shown.
 *
 * <h2>Two threads</h2>
 *
 * <p>The port reads checksums and runs binaries, so it is called only on the {@code background}
 * executor; every answer is applied on the {@code ui} executor, and an answer to an older read is
 * dropped. The file chooser is modal and is asked on the calling (interface) thread, as every other
 * chooser in the editor is. Everything else is computed on the interface thread from immutable
 * model values.
 *
 * <h2>What Run is given</h2>
 *
 * <p>{@link #requestProperty()} is the Percolator half the Run section runs with, and its {@link
 * PercolatorRequest#problems()} are reasons in the engine's half of the one Run readiness: no build
 * has been read yet, none can run, the default is not installed, or a setting holds text the model
 * refused. The display filters never block a run: they change nothing a tool computes.
 *
 * <h2>The display filters are shared</h2>
 *
 * <p>Since Phase 10 the display filters are not this section's own state: they are the one {@link
 * DisplayFiltersViewModel} the Results section holds too (design decisions P10-1 and P10-9), and
 * every filter method and property here delegates to it, so a filter set in either section is the
 * filter both show.
 */
public final class PercolatorViewModel {

    /** The builds' status before the first read. */
    public static final String NOT_READ =
            "The Percolator builds on this computer have not been read yet.";

    /** The builds' status while they are being read. */
    public static final String READING =
            "Reading the Percolator builds on this computer from the Tool Manager.";

    /** What the chooser's title names when a local binary is registered. */
    public static final String REGISTER_WHAT = "a Percolator executable to register";

    /** The registration status before anything was registered. */
    public static final String REGISTER_IDLE =
            "Register a Percolator executable already on this computer: CometGUI runs it to read"
                    + " its version, checks that it is Percolator 3.05 or newer, records its"
                    + " checksums and probes what it can do. This is the documented remedy when no"
                    + " managed build has a capability you need, such as the Percolator XML that"
                    + " Limelight conversion reads.";

    /**
     * What the display filters are, and are not ({@code R-RES-01}, {@code R-PERC-04}): the shared
     * filters' own words, {@link DisplayFiltersViewModel#EXPLANATION}.
     */
    public static final String FILTERS_EXPLANATION = DisplayFiltersViewModel.EXPLANATION;

    private static final Set<DownstreamStage> NO_STAGES = EnumSet.noneOf(DownstreamStage.class);

    private final PercolatorPort port;

    private final FileChooserPort chooser;

    private final Executor background;

    private final Executor ui;

    private final Runnable afterRegistration;

    private final DisplayFiltersViewModel filters;

    // ---- state, on the interface thread ----

    private long generation;

    private boolean read;

    private List<ToolOffer> offers = List.of();

    private Optional<String> unavailable = Optional.empty();

    private PercolatorResolution resolution;

    private String userChoice;

    private PercolatorSettings settings = PercolatorSettings.defaults();

    private final Map<PercolatorSetting, String> settingTexts =
            new EnumMap<>(PercolatorSetting.class);

    private final Map<PercolatorSetting, String> settingRefusals =
            new EnumMap<>(PercolatorSetting.class);

    private boolean registering;

    // ---- published ----

    private final NonNullProperty<String> offersStatus;

    private final NonNullProperty<List<VersionChoice>> choices;

    private final NonNullProperty<Optional<VersionChoice>> selectedChoice;

    private final NonNullProperty<String> selection;

    private final NonNullProperty<String> badge;

    private final NonNullProperty<String> installable;

    private final ReadOnlyBooleanWrapper limelightEnabled;

    private final NonNullProperty<String> limelightStatus;

    private final NonNullProperty<String> notice;

    private final NonNullProperty<String> reason;

    private final NonNullProperty<String> skipped;

    private final NonNullProperty<String> advisories;

    private final NonNullProperty<String> registrationStatus;

    private final ReadOnlyBooleanWrapper registerEnabled;

    private final NonNullProperty<List<SettingState>> settingStates;

    private final NonNullProperty<PercolatorRequest> request;

    /**
     * The section over the Tool Manager's Percolator builds, with display filters of its own.
     * Nothing is read until {@link #refresh()} is called.
     *
     * @param port the Tool Manager's Percolator builds and registration
     * @param chooser the file chooser a local binary is registered through
     * @param background where the port is called: never the interface thread
     * @param ui where every answer is applied: the interface thread
     * @param afterRegistration run on the interface thread after a binary was registered, so the
     *     rest of the interface -- the Tool Manager's list -- reads the builds again too
     */
    public PercolatorViewModel(
            PercolatorPort port,
            FileChooserPort chooser,
            Executor background,
            Executor ui,
            Runnable afterRegistration) {
        this(port, chooser, background, ui, afterRegistration, new DisplayFiltersViewModel());
    }

    /**
     * The section over the Tool Manager's Percolator builds, showing and editing the interface's
     * one display-filter state. Nothing is read until {@link #refresh()} is called.
     *
     * @param port the Tool Manager's Percolator builds and registration
     * @param chooser the file chooser a local binary is registered through
     * @param background where the port is called: never the interface thread
     * @param ui where every answer is applied: the interface thread
     * @param afterRegistration run on the interface thread after a binary was registered, so the
     *     rest of the interface -- the Tool Manager's list -- reads the builds again too
     * @param filters the display filters the Results section holds too
     */
    public PercolatorViewModel(
            PercolatorPort port,
            FileChooserPort chooser,
            Executor background,
            Executor ui,
            Runnable afterRegistration,
            DisplayFiltersViewModel filters) {
        this.filters = Objects.requireNonNull(filters, "filters");
        this.port = Objects.requireNonNull(port, "port");
        this.chooser = Objects.requireNonNull(chooser, "chooser");
        this.background = Objects.requireNonNull(background, "background");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.afterRegistration = Objects.requireNonNull(afterRegistration, "afterRegistration");
        for (PercolatorSetting setting : PercolatorSetting.values()) {
            settingTexts.put(setting, settings.valueText(setting));
        }
        offersStatus = new NonNullProperty<>(this, "offersStatus", NOT_READ);
        choices = new NonNullProperty<>(this, "choices", List.of());
        selectedChoice = new NonNullProperty<>(this, "selectedChoice", Optional.empty());
        selection = new NonNullProperty<>(this, "selection", NOT_READ);
        badge = new NonNullProperty<>(this, "badge", NOT_READ);
        installable = new NonNullProperty<>(this, "installable", "");
        limelightEnabled = new ReadOnlyBooleanWrapper(this, "limelightEnabled", false);
        limelightStatus = new NonNullProperty<>(this, "limelightStatus", "");
        notice = new NonNullProperty<>(this, "notice", "");
        reason = new NonNullProperty<>(this, "reason", "");
        skipped = new NonNullProperty<>(this, "skipped", "");
        advisories = new NonNullProperty<>(this, "advisories", "");
        registrationStatus = new NonNullProperty<>(this, "registrationStatus", REGISTER_IDLE);
        registerEnabled = new ReadOnlyBooleanWrapper(this, "registerEnabled", false);
        settingStates = new NonNullProperty<>(this, "settingStates", List.of());
        request = new NonNullProperty<>(this, "request", PercolatorRequest.pending(NOT_READ));
        publish();
    }

    // =========================================================================== actions ====

    /**
     * Reads the Tool Manager's Percolator builds again, on the background executor, and re-resolves
     * the default when they arrive. Called by the composition root at start and whenever the Tool
     * Manager's list changes, and after a registration.
     */
    public void refresh() {
        generation++;
        long mine = generation;
        offersStatus.set(READING);
        background.execute(
                () -> {
                    PercolatorOffers answer;
                    try {
                        answer = port.offers();
                    } catch (RuntimeException failed) {
                        answer =
                                PercolatorOffers.unavailable(
                                        "the Percolator builds could not be read from the Tool"
                                                + " Manager: "
                                                + failed);
                    }
                    PercolatorOffers offered = answer;
                    ui.execute(() -> applyOffers(mine, offered));
                });
    }

    /**
     * Switches Limelight conversion -- the downstream stage whose need for Percolator XML is the
     * input to resolution -- on or off, re-resolves, and states what that did to the default.
     *
     * @param on whether Limelight conversion is enabled
     */
    public void setLimelightEnabled(boolean on) {
        if (limelightEnabled.get() == on) {
            return;
        }
        limelightEnabled.set(on);
        if (read) {
            PercolatorResolution before = resolution;
            resolution = resolve();
            notice.set(ResolutionChange.between(before, resolution).message());
        }
        publish();
    }

    /**
     * Chooses the build that runs. Choosing the resolved default follows the default from then on.
     *
     * @param choice one of {@link #choicesProperty()}'s entries
     * @throws IllegalArgumentException if it is not one of them
     */
    public void choose(VersionChoice choice) {
        Objects.requireNonNull(choice, "choice");
        VersionChoice own = null;
        for (VersionChoice offered : choices.get()) {
            if (offered.key().equals(choice.key())) {
                own = offered;
            }
        }
        if (own == null) {
            throw new IllegalArgumentException(
                    "the version selector does not offer " + choice.label());
        }
        userChoice = own.resolvedDefault() ? null : own.key();
        publish();
    }

    /** Follows the resolved default again. */
    public void useDefault() {
        userChoice = null;
        publish();
    }

    /**
     * Edits one Advanced setting from the text its field holds. A value the model accepts replaces
     * the setting; text it refuses is kept in the field, the refusal is stated beside it, and Run
     * is blocked until it is corrected.
     *
     * @param setting the setting
     * @param text the text, for example {@code 0.05}
     */
    public void editSetting(PercolatorSetting setting, String text) {
        Objects.requireNonNull(setting, "setting");
        Objects.requireNonNull(text, "text");
        settingTexts.put(setting, text);
        try {
            settings = withSetting(settings, setting, text);
            settingRefusals.remove(setting);
            settingTexts.put(setting, settings.valueText(setting));
        } catch (IllegalArgumentException refused) {
            settingRefusals.put(setting, refused.getMessage());
        }
        publish();
    }

    /**
     * Sets the PSM display filter from typed text, in the shared display filters.
     *
     * @param text for example {@code 0.05}
     */
    public void editPsmFilter(String text) {
        filters.editPsmFilter(text);
    }

    /**
     * Sets the peptide display filter from typed text, in the shared display filters.
     *
     * @param text for example {@code 0.05}
     */
    public void editPeptideFilter(String text) {
        filters.editPeptideFilter(text);
    }

    /**
     * Registers a local Percolator binary: asks the chooser for the file (on this thread, as a
     * modal chooser is), then registers it on the background executor and reads the builds again.
     *
     * @return {@code true} if a registration started; {@code false} if none can (no Tool Manager,
     *     one already under way) or no file was chosen
     */
    public boolean register() {
        if (!registerEnabled.get()) {
            return false;
        }
        Optional<Path> chosen = chooser.chooseFile(REGISTER_WHAT);
        if (chosen.isEmpty()) {
            registrationStatus.set("No file was chosen, so nothing was registered.");
            return false;
        }
        Path executable = chosen.get().toAbsolutePath();
        registering = true;
        registrationStatus.set(
                "Registering "
                        + executable
                        + ": CometGUI is running it to read its version and probe what it can"
                        + " do.");
        publish();
        background.execute(
                () -> {
                    String outcome;
                    boolean registered = false;
                    try {
                        ToolOffer offer = port.register(executable);
                        outcome =
                                "Registered "
                                        + name(offer)
                                        + " from "
                                        + executable
                                        + ". Observed capabilities: "
                                        + observedText(offer)
                                        + ".";
                        registered = true;
                    } catch (ToolRegistrationException refused) {
                        outcome = "Percolator was not registered: " + refused.getMessage();
                    } catch (RuntimeException failed) {
                        outcome = "Percolator could not be registered: " + failed;
                    }
                    String said = outcome;
                    boolean then = registered;
                    ui.execute(() -> registered(said, then));
                });
        return true;
    }

    // ========================================================================= published ====

    /**
     * What is known about the builds: not read, being read, how many, or why none can be.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> offersStatusProperty() {
        return offersStatus.getReadOnlyProperty();
    }

    /**
     * The version selector's entries: the builds that can run now, the default marked.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<List<VersionChoice>> choicesProperty() {
        return choices.getReadOnlyProperty();
    }

    /**
     * The selector's entry for the build that runs; empty when that build cannot run (none, or the
     * default is not installed).
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<Optional<VersionChoice>> selectedChoiceProperty() {
        return selectedChoice.getReadOnlyProperty();
    }

    /**
     * Which build runs, and whether it is the default or the scientist's choice.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> selectionProperty() {
        return selection.getReadOnlyProperty();
    }

    /**
     * The build's capability and status badge: managed or local, installed or not, where, and its
     * observed capabilities.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> badgeProperty() {
        return badge.getReadOnlyProperty();
    }

    /**
     * The managed builds that are not installed and so not offered for running: install them from
     * the Tool Manager.
     *
     * @return the read-only property; empty text when there are none
     */
    public ReadOnlyObjectProperty<String> installableProperty() {
        return installable.getReadOnlyProperty();
    }

    /**
     * Whether Limelight conversion is switched on.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty limelightEnabledProperty() {
        return limelightEnabled.getReadOnlyProperty();
    }

    /**
     * Whether Limelight conversion is switched on.
     *
     * @return {@code true} if it is
     */
    public boolean limelightEnabled() {
        return limelightEnabled.get();
    }

    /**
     * Whether Limelight conversion can run with a Percolator here, and when it cannot, why and the
     * remedies ({@code R-PERC-03}).
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> limelightStatusProperty() {
        return limelightStatus.getReadOnlyProperty();
    }

    /**
     * What the last switch of Limelight conversion, or the last change of the builds, did to the
     * default.
     *
     * @return the read-only property; empty text before anything changed it
     */
    public ReadOnlyObjectProperty<String> noticeProperty() {
        return notice.getReadOnlyProperty();
    }

    /**
     * Why the default is the default: the resolution's own sentence.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> reasonProperty() {
        return reason.getReadOnlyProperty();
    }

    /**
     * Every newer version resolution passed over, each with the capability it lacks.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> skippedProperty() {
        return skipped.getReadOnlyProperty();
    }

    /**
     * The advisories of the build that runs ({@code R-PERC-11}).
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> advisoriesProperty() {
        return advisories.getReadOnlyProperty();
    }

    /**
     * What local-binary registration is doing or did.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> registrationStatusProperty() {
        return registrationStatus.getReadOnlyProperty();
    }

    /**
     * Whether a local binary can be registered now: there is a Tool Manager and no registration is
     * under way.
     *
     * @return the read-only property
     */
    public ReadOnlyBooleanProperty registerEnabledProperty() {
        return registerEnabled.getReadOnlyProperty();
    }

    /**
     * The Advanced settings, in {@link PercolatorSetting} order.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<List<SettingState>> settingStatesProperty() {
        return settingStates.getReadOnlyProperty();
    }

    /**
     * The text of the PSM filter's field.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> psmFilterTextProperty() {
        return filters.psmFilterTextProperty();
    }

    /**
     * The text of the peptide filter's field.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> peptideFilterTextProperty() {
        return filters.peptideFilterTextProperty();
    }

    /**
     * What the display filters do, and any refusal of their text.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<String> filtersStatusProperty() {
        return filters.filtersStatusProperty();
    }

    /**
     * The display filters' values: the shared filters' own property, which the Results section
     * observes too.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<DisplayFilters> displayFiltersProperty() {
        return filters.displayFiltersProperty();
    }

    /**
     * The Percolator half the Run section runs with.
     *
     * @return the read-only property
     */
    public ReadOnlyObjectProperty<PercolatorRequest> requestProperty() {
        return request.getReadOnlyProperty();
    }

    /**
     * The Percolator half the Run section runs with.
     *
     * @return the request
     */
    public PercolatorRequest request() {
        return request.get();
    }

    // ========================================================================= internals ====

    private void applyOffers(long answered, PercolatorOffers answer) {
        if (answered != generation) {
            return;
        }
        PercolatorResolution before = resolution;
        read = true;
        offers = answer.offers();
        unavailable = answer.unavailable();
        resolution = resolve();
        if (before != null) {
            ResolutionChange change = ResolutionChange.between(before, resolution);
            if (change.changed()) {
                notice.set(change.message());
            }
        }
        if (userChoice != null && runnableByKey(userChoice).isEmpty()) {
            notice.set(
                    "Your choice of Percolator is no longer installed on this computer, so the"
                            + " resolved default is used.");
            userChoice = null;
        }
        publish();
    }

    private void registered(String outcome, boolean registered) {
        registering = false;
        registrationStatus.set(outcome);
        publish();
        if (registered) {
            afterRegistration.run();
            refresh();
        }
    }

    private PercolatorResolution resolve() {
        Set<DownstreamStage> stages =
                limelightEnabled.get()
                        ? EnumSet.of(DownstreamStage.LIMELIGHT_CONVERSION)
                        : NO_STAGES;
        return PercolatorResolver.resolve(offers, stages);
    }

    /** Puts every published value in step with the state. */
    private void publish() {
        registerEnabled.set(read && unavailable.isEmpty() && !registering);
        List<VersionChoice> runnable = runnableChoices();
        choices.set(runnable);
        Optional<ToolOffer> build = build();
        Optional<VersionChoice> chosen = Optional.empty();
        if (build.isPresent()) {
            String key = key(build.get());
            for (VersionChoice choice : runnable) {
                if (choice.key().equals(key)) {
                    chosen = Optional.of(choice);
                }
            }
        }
        selectedChoice.set(chosen);
        offersStatus.set(offersText(runnable.size()));
        selection.set(selectionText(build));
        badge.set(build.map(PercolatorViewModel::badgeOf).orElse(selection.get()));
        installable.set(installableText());
        limelightStatus.set(limelightText());
        reason.set(resolution == null ? "" : resolution.selectionReason());
        skipped.set(skippedText());
        advisories.set(build.map(PercolatorViewModel::advisoriesOf).orElse(""));
        settingStates.set(settingStatesFor(build));
        request.set(requestFor(build));
    }

    /** The build that runs: the scientist's choice if it is still runnable, else the default. */
    private Optional<ToolOffer> build() {
        if (resolution == null) {
            return Optional.empty();
        }
        if (userChoice != null) {
            Optional<ToolOffer> chosen = runnableByKey(userChoice);
            if (chosen.isPresent()) {
                return chosen;
            }
        }
        return resolution.selected();
    }

    private Optional<ToolOffer> runnableByKey(String key) {
        for (ToolOffer offer : offers) {
            if (runnable(offer) && key(offer).equals(key)) {
                return Optional.of(offer);
            }
        }
        return Optional.empty();
    }

    private List<VersionChoice> runnableChoices() {
        List<VersionChoice> list = new ArrayList<>();
        String defaultKey =
                resolution == null
                        ? ""
                        : resolution.selected().map(PercolatorViewModel::key).orElse("");
        for (ToolOffer offer : offers) {
            if (runnable(offer)) {
                boolean isDefault = key(offer).equals(defaultKey);
                list.add(
                        new VersionChoice(
                                key(offer),
                                offer,
                                name(offer) + (isDefault ? " -- the resolved default" : ""),
                                isDefault));
            }
        }
        return List.copyOf(list);
    }

    private String offersText(int runnable) {
        if (!read) {
            return offersStatus.get().equals(READING) ? READING : NOT_READ;
        }
        if (unavailable.isPresent()) {
            return "No Percolator can be selected, because this computer has no Tool Manager: "
                    + unavailable.get();
        }
        return offers.size()
                + (offers.size() == 1 ? " Percolator build is" : " Percolator builds are")
                + " known on this computer, and "
                + runnable
                + (runnable == 1 ? " of them is" : " of them are")
                + " installed and can run.";
    }

    private String selectionText(Optional<ToolOffer> build) {
        if (!read) {
            return NOT_READ;
        }
        if (unavailable.isPresent()) {
            return offersText(0);
        }
        if (build.isEmpty()) {
            return "No Percolator can run. " + resolution.selectionReason();
        }
        ToolOffer offer = build.get();
        if (!runnable(offer)) {
            return notInstalled(offer);
        }
        if (userChoice != null) {
            return name(offer)
                    + " runs in the next search: your choice. The resolved default is "
                    + resolution.selected().map(PercolatorViewModel::name).orElse("no Percolator")
                    + "; choose it, or Use the default, to follow the default again.";
        }
        return name(offer) + " runs in the next search: the resolved default.";
    }

    private static String notInstalled(ToolOffer offer) {
        return "The resolved default, "
                + name(offer)
                + ", is not installed, so no Percolator can run yet: install it in the Tool"
                + " Manager section, register a local Percolator binary below, or choose an"
                + " installed build above.";
    }

    private String installableText() {
        List<String> names = new ArrayList<>();
        for (ToolOffer offer : offers) {
            if (offer.origin() == ToolOrigin.MANAGED
                    && (offer.state() == ToolInstallState.NOT_INSTALLED
                            || offer.state() == ToolInstallState.INSTALLING)) {
                names.add(name(offer));
            }
        }
        if (names.isEmpty()) {
            return "";
        }
        return "Not installed, so not offered for running -- install from the Tool Manager"
                + " section: "
                + String.join(", ", names)
                + ".";
    }

    private String limelightText() {
        if (resolution == null) {
            return "Whether Limelight conversion can run is known once the Percolator builds have"
                    + " been read.";
        }
        StageAvailability stage = resolution.availability(DownstreamStage.LIMELIGHT_CONVERSION);
        if (stage.available()) {
            return "Limelight conversion can run: a Percolator on this computer was observed to"
                    + " write the Percolator XML it reads (XML_OUTPUT).";
        }
        StringBuilder text =
                new StringBuilder("Limelight conversion is unavailable. ")
                        .append(stage.explanation().orElseThrow())
                        .append("\nWhat you can do:");
        for (StageRemedy remedy : stage.remedies()) {
            text.append("\n- ").append(remedy.text());
        }
        return text.toString();
    }

    private String skippedText() {
        if (resolution == null) {
            return "";
        }
        List<SkippedVersion> passed = resolution.skipped();
        if (passed.isEmpty()) {
            return "No newer Percolator was passed over.";
        }
        StringBuilder text = new StringBuilder("Newer Percolator builds passed over:");
        for (SkippedVersion skip : passed) {
            text.append("\n- ").append(skip.reason());
        }
        return text.toString();
    }

    private static String advisoriesOf(ToolOffer offer) {
        List<String> lines = AdvisoryRendering.forSelection(offer);
        if (lines.isEmpty()) {
            return name(offer) + " has no version advisories.";
        }
        return "Advisories for "
                + name(offer)
                + ", shown when it is selected:\n- "
                + String.join("\n- ", lines);
    }

    private List<SettingState> settingStatesFor(Optional<ToolOffer> build) {
        Optional<SettingsApplicability> applicability =
                build.filter(PercolatorViewModel::runnable)
                        .map(
                                offer ->
                                        SettingsApplicability.forCapabilities(
                                                PercolatorResolver.observedCapabilities(offer)));
        List<SettingState> states = new ArrayList<>();
        for (PercolatorSetting setting : PercolatorSetting.values()) {
            boolean supported = applicability.map(a -> a.isSupported(setting)).orElse(true);
            String refusal = settingRefusals.get(setting);
            StringBuilder state = new StringBuilder(setting.description());
            if (refusal != null) {
                state.append("\nNot valid: ").append(refusal);
            }
            if (!supported) {
                state.append("\nNot supported by this build: ")
                        .append(name(build.orElseThrow()))
                        .append(" was not observed to accept ")
                        .append(setting.requiredCapability().id())
                        .append(", so this setting is not passed to it.");
            } else if (applicability.isEmpty()) {
                state.append(
                        "\nWhich settings the build accepts is known once an installed build is"
                                + " selected.");
            }
            states.add(
                    new SettingState(
                            setting,
                            settingTexts.get(setting),
                            supported,
                            state.toString(),
                            refusal == null));
        }
        return List.copyOf(states);
    }

    private PercolatorRequest requestFor(Optional<ToolOffer> build) {
        List<String> problems = new ArrayList<>();
        if (!read) {
            problems.add(NOT_READ);
        } else if (unavailable.isPresent()) {
            problems.add(offersText(0));
        } else if (build.isEmpty()) {
            problems.add(resolution.selectionReason());
        } else if (!runnable(build.get())) {
            problems.add(
                    name(build.get())
                            + ", the default Percolator for the enabled downstream stages, is not"
                            + " installed: install it in the Tool Manager section, register a"
                            + " local Percolator binary in the Percolator section, or choose an"
                            + " installed build there.");
        }
        for (PercolatorSetting setting : PercolatorSetting.values()) {
            String refusal = settingRefusals.get(setting);
            if (refusal != null) {
                problems.add(
                        "The Percolator setting " + setting.label() + " is not valid: " + refusal);
            }
        }
        return new PercolatorRequest(
                build,
                userChoice == null,
                Optional.ofNullable(resolution),
                settingRefusals.isEmpty() ? Optional.of(settings) : Optional.empty(),
                problems,
                !read);
    }

    /** One setting's new value from text, through the settings model's own checks. */
    private static PercolatorSettings withSetting(
            PercolatorSettings current, PercolatorSetting setting, String text) {
        return switch (setting) {
            case TEST_FDR -> current.withTestFdr(TestFdr.parse(text));
            case TRAIN_FDR -> current.withTrainFdr(TrainFdr.parse(text));
            case RANDOM_SEED -> current.withRandomSeed(wholeNumber(setting, text));
            case MAXIMUM_ITERATIONS -> current.withMaximumIterations(wholeNumber(setting, text));
            case THREAD_COUNT -> current.withThreadCount(wholeNumber(setting, text));
        };
    }

    /*
     * THE ONE NUMBER READ IN THIS PACKAGE, AND WHY IT IS HERE.  The settings model takes the three
     * whole-number settings as int and checks their ranges itself -- its own message is what the
     * field shows for an out-of-range value -- but offers no reader from text, as it does for the
     * two FDRs.  So the text is turned into an int here and nothing else is decided: no range, no
     * default, no rule.
     */
    private static int wholeNumber(PercolatorSetting setting, String text) {
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(
                    setting.label() + " must be a whole number, but was \"" + text.strip() + "\"",
                    notANumber);
        }
    }

    private static boolean runnable(ToolOffer offer) {
        return offer.state() == ToolInstallState.INSTALLED && offer.installedPath().isPresent();
    }

    private static String key(ToolOffer offer) {
        return offer.version().text()
                + "|"
                + offer.origin()
                + "|"
                + offer.installedPath().map(Path::toString).orElse("");
    }

    /**
     * How a build is named in this section: "Percolator", its version as upstream wrote it, and
     * whether it is a registered local binary -- the resolution's own naming.
     *
     * @param offer the build
     * @return for example {@code Percolator 3.09 (registered local binary)}
     */
    static String name(ToolOffer offer) {
        return "Percolator "
                + offer.version().text()
                + (offer.origin() == ToolOrigin.LOCAL ? " (registered local binary)" : "");
    }

    private static String badgeOf(ToolOffer offer) {
        StringBuilder text = new StringBuilder(name(offer)).append(": ");
        if (offer.origin() == ToolOrigin.LOCAL) {
            text.append("a binary registered from this computer");
        } else {
            text.append("a managed build");
        }
        if (runnable(offer)) {
            text.append(", installed at ").append(offer.installedPath().orElseThrow());
        } else {
            text.append(", not installed -- install it from the Tool Manager section");
        }
        text.append(". Capabilities observed by running it: ")
                .append(observedText(offer))
                .append('.');
        List<String> unobserved = new ArrayList<>();
        for (DeclaredCapability declared : offer.capabilities()) {
            if (!declared.isObserved()) {
                unobserved.add(declared.capability().id() + " (" + declared.evidence().id() + ")");
            }
        }
        if (!unobserved.isEmpty()) {
            text.append(" Claimed but not observed, so not counted: ")
                    .append(String.join(", ", unobserved))
                    .append('.');
        }
        return text.toString();
    }

    private static String observedText(ToolOffer offer) {
        List<String> ids = new ArrayList<>();
        for (ToolCapability capability : PercolatorResolver.observedCapabilities(offer)) {
            ids.add(capability.id());
        }
        return ids.isEmpty() ? "none" : String.join(", ", ids);
    }
}
