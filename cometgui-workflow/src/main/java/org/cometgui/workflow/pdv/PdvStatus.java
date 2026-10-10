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

package org.cometgui.workflow.pdv;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import org.cometgui.domain.tools.ToolVersion;

/**
 * What the Tool Manager says about PDV on this computer, and the sentence a scientist is shown.
 *
 * @param state where PDV stands
 * @param version the PDV build this is about; empty only when {@link State#UNAVAILABLE}
 * @param jar the installed JAR; present exactly when {@link State#INSTALLED}
 * @param downloadBytes the size of the download, when {@link State#NOT_INSTALLED} and the manifest
 *     pins it
 * @param explanation the sentence the Visualisation section shows
 */
public record PdvStatus(
        State state,
        Optional<ToolVersion> version,
        Optional<Path> jar,
        OptionalLong downloadBytes,
        String explanation) {

    /** Where PDV stands. */
    public enum State {
        /** Installed and usable: {@link PdvStatus#installation()} is present. */
        INSTALLED,
        /** Installable here and not installed -- including after an attempt that failed. */
        NOT_INSTALLED,
        /** An install is running, from whichever section started it. */
        INSTALLING,
        /** Nothing can be installed or used here; the explanation says why. */
        UNAVAILABLE
    }

    /**
     * Validates the status.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if the components contradict the state
     */
    public PdvStatus {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(jar, "jar");
        Objects.requireNonNull(downloadBytes, "downloadBytes");
        Objects.requireNonNull(explanation, "explanation");
        if (explanation.isBlank()) {
            throw new IllegalArgumentException("a PDV status must explain itself");
        }
        if (version.isEmpty() != (state == State.UNAVAILABLE)) {
            throw new IllegalArgumentException(
                    "a PDV status names a version exactly when PDV is not unavailable: " + state);
        }
        if (jar.isPresent() != (state == State.INSTALLED)) {
            throw new IllegalArgumentException(
                    "a PDV status names a JAR exactly when PDV is installed: " + state);
        }
        if (downloadBytes.isPresent() && state != State.NOT_INSTALLED) {
            throw new IllegalArgumentException(
                    "a PDV status names a download size only when PDV is not installed: " + state);
        }
    }

    /**
     * PDV installed at a JAR.
     *
     * @param version the version the Tool Manager offers it as
     * @param jar the installed JAR
     * @return the status
     */
    public static PdvStatus installed(ToolVersion version, Path jar) {
        return new PdvStatus(
                State.INSTALLED,
                Optional.of(version),
                Optional.of(jar),
                OptionalLong.empty(),
                "PDV " + version.text() + " is installed: " + jar + ".");
    }

    /**
     * PDV installable and not installed.
     *
     * @param version the version that would be installed
     * @param downloadBytes the download's size, if the manifest pins it
     * @param lastAttemptFailed whether an install of it was tried and did not succeed
     * @return the status
     */
    public static PdvStatus notInstalled(
            ToolVersion version, OptionalLong downloadBytes, boolean lastAttemptFailed) {
        String size =
                downloadBytes.isPresent()
                        ? ": a download of "
                                + String.format(Locale.ROOT, "%,d", downloadBytes.getAsLong())
                                + " bytes"
                        : "";
        return new PdvStatus(
                State.NOT_INSTALLED,
                Optional.of(version),
                Optional.empty(),
                downloadBytes,
                "PDV "
                        + version.text()
                        + " is not installed"
                        + (lastAttemptFailed ? " (the last install attempt did not succeed)" : "")
                        + ". It is installed the first time a spectrum is drawn"
                        + size
                        + ", which no search waits for.");
    }

    /**
     * PDV being installed.
     *
     * @param version the version being installed
     * @return the status
     */
    public static PdvStatus installing(ToolVersion version) {
        return new PdvStatus(
                State.INSTALLING,
                Optional.of(version),
                Optional.empty(),
                OptionalLong.empty(),
                "PDV "
                        + version.text()
                        + " is being installed. A spectrum can be drawn when the install has"
                        + " finished.");
    }

    /**
     * No PDV here.
     *
     * @param reason why, as a sentence
     * @return the status
     */
    public static PdvStatus unavailable(String reason) {
        return new PdvStatus(
                State.UNAVAILABLE,
                Optional.empty(),
                Optional.empty(),
                OptionalLong.empty(),
                reason);
    }

    /**
     * The installed PDV.
     *
     * @return it, when {@link State#INSTALLED}
     */
    public Optional<PdvInstallation> installation() {
        return jar.map(path -> new PdvInstallation(version.orElseThrow(), path));
    }
}
