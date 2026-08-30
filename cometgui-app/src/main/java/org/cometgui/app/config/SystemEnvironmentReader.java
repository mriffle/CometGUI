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

package org.cometgui.app.config;

import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.ports.EnvironmentReader;

/**
 * The one {@link EnvironmentReader} that reads the real process environment.
 *
 * <p><strong>This class, and the rest of {@code org.cometgui.app.config}, is the only place in the
 * product that calls {@code System.getenv} or {@code System.getProperty}.</strong> That is the
 * point of {@code R-PROC-01}'s environment seam: a test that cannot choose the host's operating
 * system, architecture or {@code PATH} cannot test the behaviour that depends on them, and {@code
 * R-PLAT-01}'s host-baseline check is exactly such behaviour. Everything else takes an {@link
 * EnvironmentReader} through its constructor and is therefore drivable from a fake.
 *
 * <p><strong>No {@code SecurityException} is caught.</strong> {@link EnvironmentReader} says an
 * implementation that cannot read the environment reports the value as absent, and on a JDK where a
 * security manager could refuse the read that would mean a {@code catch}. This JDK is 25, where JEP
 * 486 removed the security manager permanently: {@code System.getenv} and {@code
 * System.getProperty} can no longer throw {@code SecurityException} at all. A catch block for it
 * would be unreachable code pretending to be defensive, so there is none. A future JDK that
 * reintroduced such a refusal would need one, and a test for it.
 *
 * <p>The class is stateless and safe to share between threads.
 */
public final class SystemEnvironmentReader implements EnvironmentReader {

    /** Creates a reader over the real process environment. */
    public SystemEnvironmentReader() {
        // Stateless. Declared explicitly so that the class documents its own construction.
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads {@code System.getenv(name)}. An unset variable, and a name no variable has, are
     * absent rather than an error.
     */
    @Override
    public Optional<String> environmentVariable(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(System.getenv(name));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads {@code System.getProperty(name)}. An empty name is rejected by the JDK with an
     * {@link IllegalArgumentException}, which is passed through rather than turned into an empty
     * result: an empty property name is a mistake at the call site, not a property that happens not
     * to be set.
     *
     * @throws IllegalArgumentException if {@code name} is empty
     */
    @Override
    public Optional<String> systemProperty(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(System.getProperty(name));
    }
}
