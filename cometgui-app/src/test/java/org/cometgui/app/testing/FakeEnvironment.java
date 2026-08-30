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

package org.cometgui.app.testing;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.ports.EnvironmentReader;

/**
 * An {@link EnvironmentReader} whose answers the test chooses.
 *
 * <p>This is what makes the Windows and macOS branches of {@code PlatformFileSystemAccess}, and a
 * 32-bit host, assertable on a 64-bit Linux build machine -- the three platforms the supported
 * matrix calls tier 1, of which this project's environment has exactly one.
 *
 * <p>A name that was never set answers {@link Optional#empty()}, exactly as the real reader does
 * for a variable that is not in the environment.
 */
public final class FakeEnvironment implements EnvironmentReader {

    private final Map<String, String> variables = new HashMap<>();

    private final Map<String, String> properties = new HashMap<>();

    /** Creates an environment in which nothing at all is set. */
    public FakeEnvironment() {
        // Every value is added by the test that needs it.
    }

    /**
     * A 64-bit Linux host, the machine this project builds on.
     *
     * @return an environment reporting {@code os.name=Linux}, {@code os.arch=amd64}, a 64-bit data
     *     model and {@code user.home=/home/tester}
     */
    public static FakeEnvironment linux64() {
        return new FakeEnvironment()
                .withProperty(OS_NAME_PROPERTY, "Linux")
                .withProperty(OS_ARCH_PROPERTY, "amd64")
                .withProperty(DATA_MODEL_PROPERTY, "64")
                .withProperty("user.home", "/home/tester");
    }

    /**
     * Sets an environment variable.
     *
     * @param name the variable name
     * @param value the value
     * @return this environment, for chaining
     * @throws NullPointerException if either argument is {@code null}
     */
    public FakeEnvironment withVariable(String name, String value) {
        variables.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
        return this;
    }

    /**
     * Sets a system property.
     *
     * @param name the property name
     * @param value the value
     * @return this environment, for chaining
     * @throws NullPointerException if either argument is {@code null}
     */
    public FakeEnvironment withProperty(String name, String value) {
        properties.put(
                Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
        return this;
    }

    /**
     * Unsets a system property that a factory method above had set.
     *
     * @param name the property name
     * @return this environment, for chaining
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public FakeEnvironment withoutProperty(String name) {
        properties.remove(Objects.requireNonNull(name, "name"));
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<String> environmentVariable(String name) {
        return Optional.ofNullable(variables.get(Objects.requireNonNull(name, "name")));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<String> systemProperty(String name) {
        return Optional.ofNullable(properties.get(Objects.requireNonNull(name, "name")));
    }
}
