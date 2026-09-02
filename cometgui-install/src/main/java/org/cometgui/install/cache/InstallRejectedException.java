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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * An install was refused, with the step it was refused at and what was refused.
 *
 * <p>Every message names the artefact, the step and the path or value that failed, because a
 * diagnostic is the part a scientist reads months later to find out why -- and a message that says
 * only "install failed" sends them to the wrong place with the authority of the system behind it.
 */
public final class InstallRejectedException extends IOException {

    private static final long serialVersionUID = 1L;

    /** Why it stopped. Enums are serializable, so this class stays so. */
    private final InstallFailure failure;

    /** Where it stopped. */
    private final InstallStep step;

    /** The file the failure is about, or {@code null} when it is about the artefact as a whole. */
    private final String path;

    /**
     * Creates the rejection.
     *
     * @param failure why the install stopped
     * @param step the step it stopped at
     * @param artefact how the artefact is named in a diagnostic, from {@code
     *     org.cometgui.install.registry.ArtefactRecord#describe()}
     * @param path the file the failure is about, or {@code null} for the artefact as a whole
     * @param detail one sentence naming what was rejected and what was expected
     */
    InstallRejectedException(
            InstallFailure failure, InstallStep step, String artefact, String path, String detail) {
        super(
                Objects.requireNonNull(artefact, "artefact")
                        + " was not installed: "
                        + Objects.requireNonNull(detail, "detail")
                        + " (install step "
                        + Objects.requireNonNull(step, "step").number()
                        + ", "
                        + step
                        + ")");
        this.failure = Objects.requireNonNull(failure, "failure");
        this.step = step;
        this.path = path;
    }

    /**
     * Creates the rejection from an underlying failure.
     *
     * @param failure why the install stopped
     * @param step the step it stopped at
     * @param artefact how the artefact is named in a diagnostic
     * @param path the file the failure is about, or {@code null} for the artefact as a whole
     * @param detail one sentence naming what was rejected
     * @param cause the failure that produced it
     */
    InstallRejectedException(
            InstallFailure failure,
            InstallStep step,
            String artefact,
            String path,
            String detail,
            Throwable cause) {
        this(failure, step, artefact, path, detail);
        initCause(cause);
    }

    /**
     * Why the install stopped.
     *
     * @return the failure kind
     */
    public InstallFailure failure() {
        return failure;
    }

    /**
     * Which of the specification's eight steps refused it.
     *
     * @return the step
     */
    public InstallStep step() {
        return step;
    }

    /**
     * The file the failure is about.
     *
     * @return the path, relative to the install directory, or empty when the failure is about the
     *     artefact as a whole
     */
    public Optional<String> path() {
        return Optional.ofNullable(path);
    }

    /**
     * Convenience for a caller holding an absolute path.
     *
     * @param directory the install directory
     * @return the absolute path of the file the failure is about, or empty
     */
    public Optional<Path> resolvedAgainst(Path directory) {
        Objects.requireNonNull(directory, "directory");
        return path().map(directory::resolve);
    }
}
