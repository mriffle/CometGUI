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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import org.cometgui.domain.build.BuildIdentity;

/**
 * The running build's identity, read from the properties file Maven filters when this module is
 * built ({@code org/cometgui/app/config/build-identity.properties}).
 *
 * <p>The canonical {@code comet.params} header names the CometGUI version that wrote the file
 * ({@code R-PARAM-12}'s generated header), so the version is the build's, never a string typed into
 * a view. A file that was not filtered -- still reading {@code ${project.version}} -- is refused
 * rather than written into a parameter file.
 */
public final class BuildIdentityResource {

    /** Where the filtered file is, on the class path. */
    public static final String RESOURCE = "/org/cometgui/app/config/build-identity.properties";

    private BuildIdentityResource() {}

    /**
     * The identity of this build.
     *
     * @return the build identity
     * @throws IllegalStateException if the file is missing or was not filtered, naming it
     * @throws IllegalArgumentException from {@link BuildIdentity#fromProperties}, if a value is not
     *     usable
     */
    public static BuildIdentity load() {
        try (InputStream in = BuildIdentityResource.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        RESOURCE + " is not on the class path; cometgui-app was built without it");
            }
            Properties properties = new Properties();
            properties.load(in);
            return read(properties);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("cannot read " + RESOURCE, unreadable);
        }
    }

    /**
     * The identity the properties hold, refused while any value is still a Maven placeholder.
     *
     * @param properties the loaded properties
     * @return the build identity
     * @throws IllegalStateException if a value still reads {@code ${...}}, naming it
     */
    static BuildIdentity read(Properties properties) {
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            if (value.contains("${")) {
                throw new IllegalStateException(
                        RESOURCE
                                + " was not filtered by the build: "
                                + key
                                + " = "
                                + value
                                + ". A parameter file must not name a build as a placeholder.");
            }
        }
        return BuildIdentity.fromProperties(properties);
    }
}
