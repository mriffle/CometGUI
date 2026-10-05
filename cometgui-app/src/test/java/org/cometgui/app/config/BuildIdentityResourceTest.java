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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Properties;
import org.cometgui.domain.build.BuildIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The build identity read from the file Maven filters into this module's classes. */
class BuildIdentityResourceTest {

    @Test
    @DisplayName("the built resource holds this build's version, no placeholder, and a timestamp")
    void theBuiltResourceIsFiltered() {
        BuildIdentity build = BuildIdentityResource.load();
        assertAll(
                () ->
                        assertTrue(
                                build.version().matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?"),
                                "a Maven project version, not a placeholder: " + build.version()),
                () -> assertEquals(BuildIdentity.UNKNOWN_COMMIT, build.commitId()),
                () -> assertFalse(build.isCommitKnown()),
                () ->
                        assertTrue(
                                build.buildTimestamp()
                                        .isAfter(Instant.parse("2026-01-01T00:00:00Z")),
                                "the build's own time: " + build.buildTimestamp()));
    }

    @Test
    @DisplayName("an unfiltered value is refused, naming the key and the value")
    void anUnfilteredValueIsRefused() {
        Properties unfiltered = new Properties();
        unfiltered.setProperty("cometgui.version", "${project.version}");
        unfiltered.setProperty("cometgui.commit", "unknown");
        unfiltered.setProperty("cometgui.buildTimestamp", "2026-10-05T00:00:00Z");
        IllegalStateException refused =
                assertThrows(
                        IllegalStateException.class, () -> BuildIdentityResource.read(unfiltered));
        assertEquals(
                "/org/cometgui/app/config/build-identity.properties was not filtered by the build:"
                        + " cometgui.version = ${project.version}. A parameter file must not name"
                        + " a build as a placeholder.",
                refused.getMessage());
    }

    @Test
    @DisplayName("filtered values are read as the build identity")
    void filteredValuesAreRead() {
        Properties filtered = new Properties();
        filtered.setProperty("cometgui.version", "1.2.3");
        filtered.setProperty("cometgui.commit", "unknown");
        filtered.setProperty("cometgui.buildTimestamp", "2026-10-05T12:00:00Z");
        assertEquals(
                BuildIdentity.of("1.2.3", "unknown", Instant.parse("2026-10-05T12:00:00Z")),
                BuildIdentityResource.read(filtered));
    }
}
