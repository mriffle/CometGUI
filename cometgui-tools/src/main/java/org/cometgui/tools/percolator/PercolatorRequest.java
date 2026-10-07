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

package org.cometgui.tools.percolator;

import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;

/**
 * Everything {@link PercolatorCommands} needs to build one Percolator run over a merged PIN, in
 * this module's vocabulary: no settings model, no version number.
 *
 * <p>The workflow fills it from the Percolator settings and the resolved build: {@code
 * capabilities} is the build's <em>probed</em> set (an installed or registered build's, never a
 * manifest claim), {@code xmlNeeded} is whether an <em>enabled</em> downstream stage needs pout XML
 * (today: Limelight conversion), and {@code values} holds each valued setting's text exactly as it
 * goes on the command line -- {@code PercolatorSettings.valueText} gives it, plain decimal in every
 * locale -- keyed by the option that carries it. A value whose option the build lacks is not an
 * error: it is left out, and the command says so ({@link PercolatorCommand#notEmitted()}).
 *
 * @param executable the Percolator executable, absolute
 * @param mergedPin the merged PIN Percolator reads, absolute
 * @param outputDirectory the run's Percolator output directory, absolute; every artefact is written
 *     into it and Percolator runs in it
 * @param capabilities the build's probed capabilities; Percolator's only
 * @param xmlNeeded whether an enabled downstream stage needs pout XML
 * @param values the valued options' text: keys among {@link #VALUED_OPTIONS}
 */
public record PercolatorRequest(
        Path executable,
        Path mergedPin,
        Path outputDirectory,
        Set<ToolCapability> capabilities,
        boolean xmlNeeded,
        Map<PercolatorOption, String> values) {

    /** The options that carry a setting's value rather than a file name. */
    public static final Set<PercolatorOption> VALUED_OPTIONS =
            Collections.unmodifiableSet(
                    EnumSet.of(
                            PercolatorOption.SEED,
                            PercolatorOption.NUM_THREADS,
                            PercolatorOption.TEST_FDR,
                            PercolatorOption.TRAIN_FDR,
                            PercolatorOption.MAX_ITERATIONS));

    /**
     * A value as it may go on the command line: unsigned plain decimal digits, with an optional
     * fraction. No sign, exponent, grouping or locale's decimal comma, and so never anything
     * Percolator could read as an option.
     */
    static final Pattern VALUE = Pattern.compile("[0-9]+(\\.[0-9]+)?");

    /**
     * Validates the request and copies both collections.
     *
     * @throws NullPointerException if a component, a capability, a key or a value is {@code null}
     * @throws IllegalArgumentException if a path is not absolute, a capability is not Percolator's,
     *     a key is not a valued option, or a value is not plain decimal
     */
    public PercolatorRequest {
        executable = absolute(executable, "the Percolator executable");
        mergedPin = absolute(mergedPin, "the merged PIN");
        outputDirectory = absolute(outputDirectory, "the Percolator output directory");
        Objects.requireNonNull(capabilities, "capabilities");
        Set<ToolCapability> probed = EnumSet.noneOf(ToolCapability.class);
        for (ToolCapability capability : capabilities) {
            probed.add(
                    Objects.requireNonNull(capability, "a capability")
                            .requireBelongsTo(ToolName.PERCOLATOR));
        }
        capabilities = Collections.unmodifiableSet(probed);
        Objects.requireNonNull(values, "values");
        Map<PercolatorOption, String> valued = new EnumMap<>(PercolatorOption.class);
        for (Map.Entry<PercolatorOption, String> entry : values.entrySet()) {
            PercolatorOption option = Objects.requireNonNull(entry.getKey(), "an option");
            String value = Objects.requireNonNull(entry.getValue(), option.spelling());
            if (!VALUED_OPTIONS.contains(option)) {
                throw new IllegalArgumentException(
                        option.spelling()
                                + " does not take a setting's value; the valued options are "
                                + spellings(VALUED_OPTIONS));
            }
            if (!VALUE.matcher(value).matches()) {
                throw new IllegalArgumentException(
                        "the value \""
                                + value
                                + "\" for "
                                + option.spelling()
                                + " is not plain decimal digits with an optional fraction");
            }
            valued.put(option, value);
        }
        values = Collections.unmodifiableMap(valued);
    }

    /**
     * The build's probed capabilities.
     *
     * @return the capabilities, immutable
     */
    @Override
    public Set<ToolCapability> capabilities() {
        Set<ToolCapability> copy = EnumSet.noneOf(ToolCapability.class);
        copy.addAll(capabilities);
        return Collections.unmodifiableSet(copy);
    }

    /**
     * The valued options' text.
     *
     * @return option to value, in option order, immutable
     */
    @Override
    public Map<PercolatorOption, String> values() {
        Map<PercolatorOption, String> copy = new EnumMap<>(PercolatorOption.class);
        copy.putAll(values);
        return Collections.unmodifiableMap(copy);
    }

    private static String spellings(Set<PercolatorOption> options) {
        return options.stream().map(PercolatorOption::spelling).toList().toString();
    }

    private static Path absolute(Path path, String what) {
        Objects.requireNonNull(path, what);
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(
                    what + " must be an absolute path, not \"" + path + "\"");
        }
        return path;
    }
}
