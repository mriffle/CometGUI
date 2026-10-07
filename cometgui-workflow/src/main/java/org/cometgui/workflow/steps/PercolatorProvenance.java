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

package org.cometgui.workflow.steps;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.params.percolator.EffectiveSeed;
import org.cometgui.params.percolator.resolution.AdvisoryRendering;
import org.cometgui.params.percolator.resolution.DownstreamStage;
import org.cometgui.params.percolator.resolution.MissingCapability;
import org.cometgui.params.percolator.resolution.SkippedVersion;
import org.cometgui.params.percolator.schema.PercolatorSetting;
import org.cometgui.provenance.manifest.ProvenanceSchema;
import org.cometgui.tools.percolator.NotEmitted;
import org.cometgui.tools.percolator.PercolatorArtefact;
import org.cometgui.tools.percolator.PercolatorCommand;
import org.cometgui.tools.percolator.PercolatorOption;

/**
 * The provenance settings a run that plans Percolator records (design decision P9-10): every key in
 * one place, beside {@link CometWorkflow}'s {@code comet.*} keys, each of the shape {@link
 * ProvenanceSchema#SETTINGS_KEY_PATTERN}.
 *
 * <p><strong>Every one is known before anything runs</strong> -- the effective seed, the build, why
 * it was chosen, what it lacks and what its command leaves out are decided by the selection, the
 * settings and the command built from the probed capabilities when the run is prepared. So they are
 * the run's settings from the start, and are written into {@code provenance.json} however the
 * attempt ends: a run whose Percolator step failed, or was refused before launch, records its seed
 * like any other (gate item 7, {@code AC-PRV-10}).
 *
 * <p>The keys and what each value is are listed in {@code docs/reference/provenance_format.rst}.
 */
public final class PercolatorProvenance {

    /** The effective seed: the seed passed, or {@code not-passed} ({@code R-PERC-05}). */
    public static final String SEED = ProvenanceSchema.PERCOLATOR_SEED_SETTING;

    /** Why the effective seed is what it is. */
    public static final String SEED_EXPLANATION = "percolator.seed-explanation";

    /** The selected build's version. */
    public static final String VERSION = "percolator.version";

    /** Whether the selected build is {@code managed} or {@code local}. */
    public static final String ORIGIN = "percolator.origin";

    /** The selected executable's SHA-256 when it was selected. */
    public static final String BINARY_SHA256 = "percolator.binary-sha256";

    /** The selected build's observed capabilities, sorted, space-separated. */
    public static final String CAPABILITIES = "percolator.capabilities";

    /** The enabled downstream stages, space-separated, or {@code none}. */
    public static final String DOWNSTREAM_STAGES = "percolator.downstream-stages";

    /** {@code resolved-default} or {@code user-choice}. */
    public static final String SELECTION = "percolator.selection";

    /** What resolution chose: {@code <version> <origin>}, or {@code none}. */
    public static final String RESOLVED_DEFAULT = "percolator.resolved-default";

    /** Resolution's reason for its default, naming every newer version passed over. */
    public static final String SELECTION_REASON = "percolator.selection-reason";

    /** The prefix of each newer version resolution passed over: {@code .<nn>.version} ... */
    public static final String SKIPPED_PREFIX = "percolator.skipped.";

    /** The prefix of each advisory shown at selection: {@code percolator.advisory.<id>}. */
    public static final String ADVISORY_PREFIX = "percolator.advisory.";

    /**
     * The prefix of each requested option not passed: {@code .<nn>.option}, {@code .<nn>.reason}.
     */
    public static final String NOT_EMITTED_PREFIX = "percolator.not-emitted.";

    /** Whether pout XML was requested, and why or why not. */
    public static final String POUT_XML = "percolator.pout-xml";

    /** {@code R-PERC-08}'s warning, present only when no weights file was requested. */
    public static final String WEIGHTS_WARNING = "percolator.weights-warning";

    /** The archived {@code percolator-settings.json}'s SHA-256. */
    public static final String SETTINGS_SHA256 = "percolator.settings-sha256";

    /** The decoy prefix the merged PIN was checked with ({@code R-DEC-03}). */
    public static final String DECOY_PREFIX = "percolator.decoy-prefix";

    /**
     * The prefix of each configured setting's value: {@code percolator.<setting id>}, for example
     * {@code percolator.test-fdr}. The configured seed is {@code percolator.random-seed}; the one
     * that ran is {@link #SEED}.
     */
    public static final String SETTING_PREFIX = "percolator.";

    /** The value of {@link #SELECTION} when the build that runs is resolution's default. */
    public static final String RESOLVED = "resolved-default";

    /** The value of {@link #SELECTION} when the scientist chose another build. */
    public static final String USER_CHOICE = "user-choice";

    private PercolatorProvenance() {}

    /**
     * The settings a run records.
     *
     * @param choice the Percolator half of the search
     * @param command the built command
     * @param settingsSha256 the archived settings file's SHA-256
     * @param decoyPrefix the run's one decoy prefix
     * @return key to value, sorted
     */
    static Map<String, String> settings(
            PercolatorChoice choice,
            PercolatorCommand command,
            String settingsSha256,
            String decoyPrefix) {
        PercolatorSelection selection = choice.selection();
        Map<String, String> settings = new TreeMap<>();
        EffectiveSeed seed = choice.settings().effectiveSeed(selection.capabilities());
        settings.put(SEED, seed.recordedValue());
        settings.put(SEED_EXPLANATION, seed.explanation());
        settings.put(VERSION, selection.version().text());
        settings.put(ORIGIN, selection.originId());
        settings.put(BINARY_SHA256, selection.sha256());
        settings.put(CAPABILITIES, capabilities(selection));
        settings.put(DOWNSTREAM_STAGES, stages(choice));
        settings.put(SELECTION, choice.isResolvedDefault() ? RESOLVED : USER_CHOICE);
        settings.put(
                RESOLVED_DEFAULT,
                choice.resolution().selected().map(PercolatorProvenance::label).orElse("none"));
        settings.put(SELECTION_REASON, choice.resolution().selectionReason());
        List<SkippedVersion> skipped = choice.resolution().skipped();
        for (int index = 0; index < skipped.size(); index++) {
            SkippedVersion skip = skipped.get(index);
            String prefix = SKIPPED_PREFIX + number(index) + ".";
            settings.put(prefix + "version", label(skip.offer()));
            settings.put(prefix + "missing", missing(skip.missing()));
            settings.put(prefix + "reason", skip.reason());
        }
        for (Map.Entry<String, String> advisory :
                AdvisoryRendering.forProvenance(selection.offer()).entrySet()) {
            settings.put(ADVISORY_PREFIX + advisory.getKey(), advisory.getValue());
        }
        List<NotEmitted> left = command.notEmitted();
        for (int index = 0; index < left.size(); index++) {
            String prefix = NOT_EMITTED_PREFIX + number(index) + ".";
            settings.put(prefix + "option", left.get(index).option().spelling());
            settings.put(prefix + "reason", left.get(index).reason());
        }
        settings.put(POUT_XML, poutXml(choice, command));
        weightsWarning(command).ifPresent(warning -> settings.put(WEIGHTS_WARNING, warning));
        settings.put(SETTINGS_SHA256, settingsSha256);
        settings.put(DECOY_PREFIX, decoyPrefix);
        for (PercolatorSetting setting : PercolatorSetting.values()) {
            settings.put(SETTING_PREFIX + setting.id(), choice.settings().valueText(setting));
        }
        return settings;
    }

    /**
     * {@code R-PERC-08}'s provenance warning, when the command requests no weights file.
     *
     * @param command the built command
     * @return the warning, or empty when the weights file is requested
     */
    static Optional<String> weightsWarning(PercolatorCommand command) {
        return command.omission(PercolatorOption.WEIGHTS)
                .map(
                        left ->
                                "R-PERC-08: no learned weights file was written, so the weights"
                                        + " are not available from a file: "
                                        + left.reason());
    }

    /**
     * Whether pout XML was requested, and why or why not.
     *
     * @param choice the Percolator half of the search
     * @param command the built command
     * @return the sentence
     */
    static String poutXml(PercolatorChoice choice, PercolatorCommand command) {
        if (command.writesXml()) {
            return "requested ("
                    + PercolatorOption.XML_OUTPUT.spelling()
                    + " "
                    + command.artefacts().get(PercolatorArtefact.POUT_XML)
                    + "), because an enabled downstream stage needs it: "
                    + stages(choice);
        }
        return command.omission(PercolatorOption.XML_OUTPUT)
                .map(left -> "not requested: " + left.reason())
                .orElse(
                        "not requested: no enabled downstream stage needs pout XML, so none is"
                                + " written and none is expected");
    }

    private static String capabilities(PercolatorSelection selection) {
        TreeSet<String> ids = new TreeSet<>();
        for (ToolCapability capability : selection.capabilities()) {
            ids.add(capability.id());
        }
        return String.join(" ", ids);
    }

    private static String stages(PercolatorChoice choice) {
        List<String> ids = new ArrayList<>();
        for (DownstreamStage stage : choice.enabledStages()) {
            ids.add(stage.id());
        }
        return ids.isEmpty() ? "none" : String.join(" ", ids);
    }

    private static String missing(List<MissingCapability> missing) {
        List<String> ids = new ArrayList<>();
        for (MissingCapability capability : missing) {
            ids.add(capability.capability().id());
        }
        return String.join(" ", ids);
    }

    private static String label(ToolOffer offer) {
        return offer.version().text() + " " + (offer.origin().isManaged() ? "managed" : "local");
    }

    private static String number(int index) {
        return String.format(Locale.ROOT, "%02d", index + 1);
    }
}
