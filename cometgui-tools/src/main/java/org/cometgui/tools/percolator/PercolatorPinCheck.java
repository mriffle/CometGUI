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

package org.cometgui.tools.percolator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import org.cometgui.tools.comet.CometOutputException;
import org.cometgui.tools.comet.CometPinValidator;
import org.cometgui.tools.comet.PinDecoyConfiguration;
import org.cometgui.tools.comet.PinSummary;

/**
 * The check of the merged PIN before Percolator is launched: the specification's <em>Input
 * validation before Percolator</em> and {@code R-DEC-04} (design decision P9-8).
 *
 * <p><strong>Not a second PIN parser.</strong> The file is read by the one PIN reader, through
 * {@link CometPinValidator#validateBeforePercolator}, whose documentation states every rule: the
 * header has the required columns, every feature parses as a finite decimal, both target and decoy
 * rows are present, and the labels do not obviously contradict the configured decoy prefix. This
 * class adds only what is particular to the moment before launch:
 *
 * <ul>
 *   <li>the file exists, is a regular file and is not empty, each refused with a message of its own
 *       -- the merged PIN is the merge's output, so the reader's "Comet did not write it" would
 *       point at the wrong stage;
 *   <li>every refusal says Percolator was not started and names the file ({@link
 *       PercolatorRefusedException#file()}).
 * </ul>
 *
 * <p><strong>Why this is not left to Comet validation.</strong> The per-file PINs are validated as
 * Comet writes them, and a zero-decoy search -- Comet 2026.02.2 searching a fragment-ion index
 * built with {@code decoy_search = 1}, Phase 08's finding -- already fails there. But a
 * compatible-version rerun reuses the preserved merged PIN without running Comet at all, so the
 * Percolator stage checks the file it is about to hand over, every time.
 */
public final class PercolatorPinCheck {

    private PercolatorPinCheck() {}

    /**
     * Checks the merged PIN.
     *
     * @param mergedPin the merged PIN
     * @param decoys the decoy configuration the search ran with
     * @return its feature columns and row counts
     * @throws PercolatorRefusedException if the file is missing, not a regular file, empty, or
     *     fails any rule of {@link CometPinValidator#validateBeforePercolator}, naming the file
     * @throws IOException if the file's size cannot be read or the file cannot be closed
     * @throws NullPointerException if an argument is {@code null}
     */
    public static PinSummary check(Path mergedPin, PinDecoyConfiguration decoys)
            throws PercolatorRefusedException, IOException {
        Objects.requireNonNull(mergedPin, "mergedPin");
        Objects.requireNonNull(decoys, "decoys");
        if (!Files.exists(mergedPin, LinkOption.NOFOLLOW_LINKS)) {
            throw refused(mergedPin, "the merged PIN file " + mergedPin + " does not exist", null);
        }
        if (!Files.isRegularFile(mergedPin)) {
            throw refused(
                    mergedPin, "the merged PIN file " + mergedPin + " is not a regular file", null);
        }
        if (Files.size(mergedPin) == 0) {
            throw refused(
                    mergedPin,
                    "the merged PIN file "
                            + mergedPin
                            + " is empty (0 bytes): it has not even a header",
                    null);
        }
        try {
            return CometPinValidator.validateBeforePercolator(mergedPin, decoys);
        } catch (CometOutputException invalid) {
            throw refused(mergedPin, invalid.getMessage(), invalid);
        }
    }

    private static PercolatorRefusedException refused(Path pin, String why, Throwable cause) {
        return new PercolatorRefusedException("Percolator was not started: " + why, pin, cause);
    }
}
