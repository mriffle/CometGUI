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

package org.cometgui.workflow.limelight;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import org.cometgui.domain.tools.ToolVersion;

/**
 * What the Tool Manager says about the Limelight converter on this computer, and the sentence a
 * scientist is shown.
 *
 * @param state where the converter stands
 * @param version the build this is about; empty only when {@link State#UNAVAILABLE}
 * @param jar the installed JAR; present exactly when {@link State#INSTALLED}
 * @param downloadBytes the size of the download, when {@link State#NOT_INSTALLED}
 * @param explanation the sentence the Limelight section shows
 */
public record LimelightConverterStatus(
        State state,
        Optional<ToolVersion> version,
        Optional<Path> jar,
        OptionalLong downloadBytes,
        String explanation) {

    /** Where the converter stands. */
    public enum State {
        /**
         * Installed, and its files verified against the checksums recorded when it was installed:
         * {@link LimelightConverterStatus#installation()} is present.
         */
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
    public LimelightConverterStatus {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(jar, "jar");
        Objects.requireNonNull(downloadBytes, "downloadBytes");
        Objects.requireNonNull(explanation, "explanation");
        if (explanation.isBlank()) {
            throw new IllegalArgumentException("a converter status must explain itself");
        }
        if (version.isEmpty() != (state == State.UNAVAILABLE)) {
            throw new IllegalArgumentException(
                    "a converter status names a version exactly when the converter is not"
                            + " unavailable: "
                            + state);
        }
        if (jar.isPresent() != (state == State.INSTALLED)) {
            throw new IllegalArgumentException(
                    "a converter status names a JAR exactly when the converter is installed: "
                            + state);
        }
        if (downloadBytes.isPresent() && state != State.NOT_INSTALLED) {
            throw new IllegalArgumentException(
                    "a converter status names a download size only when it is not installed: "
                            + state);
        }
    }

    /**
     * The converter installed and verified at a JAR.
     *
     * @param version the version the Tool Manager offers it as
     * @param jar the installed JAR
     * @return the status
     */
    public static LimelightConverterStatus installed(ToolVersion version, Path jar) {
        return new LimelightConverterStatus(
                State.INSTALLED,
                Optional.of(version),
                Optional.of(jar),
                OptionalLong.empty(),
                "The Limelight converter " + version.text() + " is installed: " + jar + ".");
    }

    /**
     * The converter installable and not installed.
     *
     * @param version the version that would be installed
     * @param downloadBytes the download's size, if the manifest pins it
     * @param lastAttemptFailed whether an install of it was tried and did not succeed
     * @return the status
     */
    public static LimelightConverterStatus notInstalled(
            ToolVersion version, OptionalLong downloadBytes, boolean lastAttemptFailed) {
        String size =
                downloadBytes.isPresent()
                        ? " (a download of "
                                + String.format(Locale.ROOT, "%,d", downloadBytes.getAsLong())
                                + " bytes)"
                        : "";
        return new LimelightConverterStatus(
                State.NOT_INSTALLED,
                Optional.of(version),
                Optional.empty(),
                downloadBytes,
                "The Limelight converter "
                        + version.text()
                        + " is not installed"
                        + (lastAttemptFailed
                                ? " (the last install attempt did not succeed, or the installed"
                                        + " files no longer matched their recorded checksums)"
                                : "")
                        + ". It is installed the first time a run is converted"
                        + size
                        + ", which no search waits for.");
    }

    /**
     * The converter being installed.
     *
     * @param version the version being installed
     * @return the status
     */
    public static LimelightConverterStatus installing(ToolVersion version) {
        return new LimelightConverterStatus(
                State.INSTALLING,
                Optional.of(version),
                Optional.empty(),
                OptionalLong.empty(),
                "The Limelight converter "
                        + version.text()
                        + " is being installed. A run can be converted when the install has"
                        + " finished.");
    }

    /**
     * No converter here.
     *
     * @param reason why, as a sentence
     * @return the status
     */
    public static LimelightConverterStatus unavailable(String reason) {
        return new LimelightConverterStatus(
                State.UNAVAILABLE,
                Optional.empty(),
                Optional.empty(),
                OptionalLong.empty(),
                reason);
    }

    /**
     * The installed converter.
     *
     * @return it, when {@link State#INSTALLED}
     */
    public Optional<LimelightConverterInstallation> installation() {
        return jar.map(path -> new LimelightConverterInstallation(version.orElseThrow(), path));
    }
}
