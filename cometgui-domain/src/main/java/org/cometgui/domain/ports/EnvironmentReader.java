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

package org.cometgui.domain.ports;

import java.util.Optional;

/**
 * Reads environment variables and JVM system properties.
 *
 * <p>This is the seam {@code R-PROC-01} requires for the environment. Outside the one system-backed
 * implementation, nothing in this product calls {@code System.getenv} or {@code
 * System.getProperty}: a test that cannot choose the host's operating system, architecture or
 * {@code PATH} cannot test the behaviour that depends on them, and the host-baseline check of
 * {@code R-PLAT-01} is exactly such behaviour.
 *
 * <p>Every method answers {@link Optional#empty()} rather than {@code null} for a name that is not
 * set, and none of them throws for an unset name. An implementation that cannot read the
 * environment at all -- a security manager, a restricted container -- reports it as absent.
 */
public interface EnvironmentReader {

    /** System property naming the operating system, for example {@code Linux}. */
    String OS_NAME_PROPERTY = "os.name";

    /** System property naming the CPU architecture, for example {@code amd64}. */
    String OS_ARCH_PROPERTY = "os.arch";

    /** System property holding the JVM's data model in bits: {@code 64} or {@code 32}. */
    String DATA_MODEL_PROPERTY = "sun.arch.data.model";

    /**
     * Reads an environment variable.
     *
     * @param name the variable name, for example {@code PATH}
     * @return the value, or empty when the variable is not set
     * @throws NullPointerException if {@code name} is {@code null}
     */
    Optional<String> environmentVariable(String name);

    /**
     * Reads a JVM system property.
     *
     * @param name the property name, for example {@code os.name}
     * @return the value, or empty when the property is not set
     * @throws NullPointerException if {@code name} is {@code null}
     */
    Optional<String> systemProperty(String name);

    /**
     * Reads {@value #OS_NAME_PROPERTY}.
     *
     * @return the operating-system name, or empty when the property is not set
     */
    default Optional<String> osName() {
        return systemProperty(OS_NAME_PROPERTY);
    }

    /**
     * Reads {@value #OS_ARCH_PROPERTY}.
     *
     * @return the CPU architecture, or empty when the property is not set
     */
    default Optional<String> osArch() {
        return systemProperty(OS_ARCH_PROPERTY);
    }

    /**
     * Reads {@value #DATA_MODEL_PROPERTY}, the width of the running JVM in bits.
     *
     * <p>This is the JVM's data model, not the kernel's: a 32-bit JVM on a 64-bit kernel reports
     * {@code 32}. That is the honest thing to report, because the managed tools have to run beside
     * this JVM on this installation. It is also not universal -- it is a HotSpot property -- so
     * {@link org.cometgui.domain.platform.HostBaselineVerifier} falls back to {@link #osArch()}
     * when it is absent rather than assuming a width.
     *
     * @return {@code 64}, {@code 32}, or empty when the property is not set
     */
    default Optional<String> dataModel() {
        return systemProperty(DATA_MODEL_PROPERTY);
    }
}
