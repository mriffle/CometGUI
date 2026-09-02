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

package org.cometgui.install.cache;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * What one look at a tool directory found, with enough detail to say why.
 *
 * <p>Returned rather than thrown, because "not installed" is the ordinary answer to an ordinary
 * question: the Tool Manager asks it about every build it lists. The detail exists so that the
 * reason survives to a log line or a diagnostic -- <em>which</em> file went missing, <em>which</em>
 * digest stopped matching -- rather than being flattened into a boolean at the point it is known
 * and reconstructed by guesswork later.
 *
 * @param state what was found
 * @param directory the tool directory that was looked at
 * @param detail one sentence naming what was wrong, or what was found for an installed entry
 * @param marker the marker, when there was a readable one
 */
public record InstallationCheck(
        InstallationState state,
        Path directory,
        String detail,
        Optional<InstallationMarker> marker) {

    /**
     * Validates the verdict and its agreement with the state.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if the detail is blank, if a state that requires a readable
     *     marker carries none, or if a state reached before the marker was read carries one
     */
    public InstallationCheck {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(marker, "marker");
        if (detail.isBlank()) {
            throw new IllegalArgumentException(
                    "an installation check says why, and this one's detail is blank");
        }
        boolean markerWasRead =
                state != InstallationState.NOT_PRESENT
                        && state != InstallationState.NO_MARKER
                        && state != InstallationState.MARKER_UNREADABLE;
        if (markerWasRead && marker.isEmpty()) {
            throw new IllegalArgumentException(
                    state + " is only reached by reading the marker, so one must be supplied");
        }
        if (!markerWasRead && marker.isPresent()) {
            throw new IllegalArgumentException(
                    state + " is reached before a marker has been read, but one was supplied");
        }
    }

    /**
     * Whether the tool may be offered and launched.
     *
     * @return {@code true} only when the marker was present and every recorded checksum matched
     */
    public boolean installed() {
        return state.installed();
    }

    /**
     * The marker, for a state that has one.
     *
     * @return the marker
     * @throws IllegalStateException if this verdict was reached before a marker was read
     */
    public InstallationMarker requireMarker() {
        return marker.orElseThrow(
                () -> new IllegalStateException(state + " carries no marker: " + detail));
    }
}
