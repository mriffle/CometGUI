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

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.schema.PercolatorSetting;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.tools.comet.PinDecoyConfiguration;
import org.cometgui.tools.percolator.PercolatorCommand;
import org.cometgui.tools.percolator.PercolatorOption;
import org.cometgui.tools.percolator.PercolatorRequest;
import org.cometgui.workflow.engine.ToolIdentity;

/**
 * Everything the Percolator steps of one recorded run share: the Comet run whose merged PIN they
 * read, the choice that was made, the tool as provenance records it, the command built from the
 * build's probed capabilities, and the archived settings file.
 *
 * <p>The command is built once, when the run is prepared, from the selection's probed capabilities
 * -- so the files the steps declare, the files Percolator is asked to write and the options
 * provenance says were not passed are one decision, made before anything runs.
 *
 * @param comet the Comet run
 * @param choice the Percolator half of the search
 * @param tool the selected Percolator as provenance records its invocation
 * @param command the command, its artefacts and what it leaves out
 * @param settings the archived {@code percolator-settings.json}
 */
record PercolatorRun(
        CometRun comet,
        PercolatorChoice choice,
        ToolIdentity tool,
        PercolatorCommand command,
        PercolatorSettingsFile.Archived settings) {

    PercolatorRun {
        Objects.requireNonNull(comet, "comet");
        Objects.requireNonNull(choice, "choice");
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(settings, "settings");
    }

    /** The run's directory. */
    RunLayout layout() {
        return comet.layout();
    }

    /** The one hasher. */
    CachingHashService hashes() {
        return comet.hashes();
    }

    /**
     * The decoy configuration the search ran with: one prefix for the whole run ({@code R-DEC-03}).
     */
    PinDecoyConfiguration decoys() {
        return comet.decoys();
    }

    /** The directory of the raw outputs. */
    Path outputDirectory() {
        return PercolatorDeclarations.outputDirectory(layout());
    }

    /**
     * The request the command builder is given: the selection's probed capabilities, whether an
     * enabled stage needs pout XML, and each setting's text.
     *
     * @param executable the Percolator executable
     * @param mergedPin the merged PIN
     * @param outputDirectory where the artefacts go
     * @param choice the Percolator half of the search
     * @return the request
     */
    static PercolatorRequest request(
            Path executable, Path mergedPin, Path outputDirectory, PercolatorChoice choice) {
        return new PercolatorRequest(
                executable,
                mergedPin,
                outputDirectory,
                choice.selection().capabilities(),
                choice.xmlNeeded(),
                values(choice.settings()));
    }

    /**
     * Each setting's text, keyed by the option that carries it.
     *
     * @param settings the settings
     * @return option to text, in option order
     */
    static Map<PercolatorOption, String> values(PercolatorSettings settings) {
        Map<PercolatorOption, String> values = new EnumMap<>(PercolatorOption.class);
        for (PercolatorSetting setting : PercolatorSetting.values()) {
            values.put(optionOf(setting), settings.valueText(setting));
        }
        return values;
    }

    /**
     * The option that carries a setting to Percolator. Each maps to the option whose capability is
     * the setting's own required capability, which a test holds to.
     *
     * @param setting the setting
     * @return its option
     */
    static PercolatorOption optionOf(PercolatorSetting setting) {
        return switch (setting) {
            case TEST_FDR -> PercolatorOption.TEST_FDR;
            case TRAIN_FDR -> PercolatorOption.TRAIN_FDR;
            case RANDOM_SEED -> PercolatorOption.SEED;
            case MAXIMUM_ITERATIONS -> PercolatorOption.MAX_ITERATIONS;
            case THREAD_COUNT -> PercolatorOption.NUM_THREADS;
        };
    }
}
