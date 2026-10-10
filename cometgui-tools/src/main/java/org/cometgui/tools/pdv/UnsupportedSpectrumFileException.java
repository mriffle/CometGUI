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

package org.cometgui.tools.pdv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;

/**
 * A run's spectrum source cannot be visualised by the selected PDV version.
 *
 * <p>This is the specified error ({@code specification.rst}, <em>Baseline integration</em>: "a
 * clear error when the source spectrum format cannot be visualised by the selected PDV version"),
 * raised by {@link PdvSpectrumSupport#check} <strong>before</strong> anything is launched. Its
 * message names the file, its format, the PDV version and what that version can be given; the
 * accessors carry the same facts for a caller that wants them separately.
 */
public final class UnsupportedSpectrumFileException extends IOException {

    private static final long serialVersionUID = 1L;

    private final transient Path file;
    private final SpectrumFileFormat format;
    private final transient ToolVersion pdvVersion;

    /**
     * Creates the error.
     *
     * @param file the spectrum source
     * @param format the format it was found to be, or {@code null} if it is none Comet reads
     * @param pdvVersion the selected PDV version
     * @param message the whole message
     * @throws NullPointerException if {@code file}, {@code pdvVersion} or {@code message} is {@code
     *     null}
     */
    UnsupportedSpectrumFileException(
            Path file, SpectrumFileFormat format, ToolVersion pdvVersion, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.file = Objects.requireNonNull(file, "file");
        this.format = format;
        this.pdvVersion = Objects.requireNonNull(pdvVersion, "pdvVersion");
    }

    /**
     * The spectrum source that was refused.
     *
     * @return the file
     */
    public Path file() {
        return file;
    }

    /**
     * The format the file was found to be.
     *
     * @return the format, or empty when its name ends in no spectrum extension Comet reads
     */
    public Optional<SpectrumFileFormat> format() {
        return Optional.ofNullable(format);
    }

    /**
     * The PDV version the file was checked against.
     *
     * @return the version
     */
    public ToolVersion pdvVersion() {
        return pdvVersion;
    }
}
