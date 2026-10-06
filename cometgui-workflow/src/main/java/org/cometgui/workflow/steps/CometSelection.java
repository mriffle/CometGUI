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

package org.cometgui.workflow.steps;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.cometgui.domain.tools.ToolVersion;

/**
 * The Comet a run is to use, as the user (or the Tool Manager) selected it: the executable, the
 * release it is, the SHA-256 it was selected at, and whether CometGUI installed it.
 *
 * <p>The SHA-256 is the selection's promise about the bytes. The pre-run check and the run's {@code
 * resolve-comet} step both re-hash the executable and refuse a mismatch naming both digests, so a
 * binary replaced after it was selected never runs under the selected identity.
 *
 * @param executable the Comet executable; absolute, normalised by this constructor
 * @param release the release the executable is, which must be the release the parameter model is
 *     for
 * @param sha256 the executable's SHA-256 when it was selected, 64 hexadecimal characters; kept in
 *     lower case
 * @param managed whether CometGUI installed it, rather than the user registering a local copy
 * @param artefactIdentity the upstream or managed artefact identity, when known (for a managed
 *     install, the manifest artefact it came from)
 */
public record CometSelection(
        Path executable,
        ToolVersion release,
        String sha256,
        boolean managed,
        Optional<String> artefactIdentity) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    /**
     * Validates and normalises.
     *
     * @throws NullPointerException naming a component that is {@code null}
     * @throws IllegalArgumentException if the executable is not absolute or the digest is not 64
     *     hexadecimal characters, quoting it
     */
    public CometSelection {
        Objects.requireNonNull(executable, "executable");
        if (!executable.isAbsolute()) {
            throw new IllegalArgumentException(
                    "the Comet executable must be an absolute path, not \"" + executable + "\"");
        }
        executable = executable.normalize();
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(sha256, "sha256");
        String lower = sha256.toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(lower).matches()) {
            throw new IllegalArgumentException(
                    "a SHA-256 is 64 hexadecimal characters, not \"" + sha256 + "\"");
        }
        sha256 = lower;
        Objects.requireNonNull(artefactIdentity, "artefactIdentity");
    }
}
