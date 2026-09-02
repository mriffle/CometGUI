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

package org.cometgui.domain.testing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.cometgui.domain.ports.EnvironmentReader;

/**
 * An {@link EnvironmentReader} whose answers the test chooses.
 *
 * <p>This is the whole point of the port. Every branch of the host baseline check -- a 32-bit
 * machine, an unset {@code os.arch}, an architecture nobody here has -- is reachable from an
 * ordinary unit test on this Linux x86-64 machine because the host is a parameter rather than a
 * fact. A value of {@code null} means "not set", which is what distinguishes an absent property
 * from an empty one.
 */
public final class FakeEnvironmentReader implements EnvironmentReader {

    private final Map<String, String> variables = new LinkedHashMap<>();
    private final Map<String, String> properties = new LinkedHashMap<>();

    /**
     * Sets, or with a {@code null} value unsets, one environment variable.
     *
     * @param name the variable name
     * @param value the value, or {@code null} to leave it unset
     * @return this reader, for chaining
     */
    public FakeEnvironmentReader withVariable(String name, String value) {
        return put(variables, name, value);
    }

    /**
     * Sets, or with a {@code null} value unsets, one system property.
     *
     * @param name the property name
     * @param value the value, or {@code null} to leave it unset
     * @return this reader, for chaining
     */
    public FakeEnvironmentReader withProperty(String name, String value) {
        return put(properties, name, value);
    }

    /**
     * Sets the three properties the host baseline check reads, in one call.
     *
     * @param osName {@code os.name}, or {@code null} to leave it unset
     * @param osArch {@code os.arch}, or {@code null} to leave it unset
     * @param dataModel {@code sun.arch.data.model}, or {@code null} to leave it unset
     * @return this reader, for chaining
     */
    public FakeEnvironmentReader withHost(String osName, String osArch, String dataModel) {
        return withProperty(OS_NAME_PROPERTY, osName)
                .withProperty(OS_ARCH_PROPERTY, osArch)
                .withProperty(DATA_MODEL_PROPERTY, dataModel);
    }

    private FakeEnvironmentReader put(Map<String, String> target, String name, String value) {
        if (value == null) {
            target.remove(name);
        } else {
            target.put(name, value);
        }
        return this;
    }

    @Override
    public Optional<String> environmentVariable(String name) {
        return Optional.ofNullable(variables.get(name));
    }

    @Override
    public Optional<String> systemProperty(String name) {
        return Optional.ofNullable(properties.get(name));
    }
}
