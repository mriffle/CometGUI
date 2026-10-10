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

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The spectrum file formats Comet reads, as a run's spectrum source can be one of them.
 *
 * <p>The extensions are exactly {@code
 * org.cometgui.domain.run.OutputBaseNames#SPECTRUM_EXTENSIONS}, the list the workflow's pre-run
 * check accepts, in the case Comet's documentation writes them; a test holds the two lists equal so
 * that a format Comet gains cannot reach PDV unclassified. A format is decided by the file's
 * extension, matched case-insensitively, as Comet decides it -- and {@link PdvSpectrumSupport} then
 * confirms the content of any format it accepts.
 */
public enum SpectrumFileFormat {

    /** HUPO-PSI mzML. */
    MZML("mzML", ".mzML"),

    /** The ISB mzXML format. */
    MZXML("mzXML", ".mzXML"),

    /** Mascot Generic Format. */
    MGF("MGF", ".mgf"),

    /** The MS2 text format. */
    MS2("MS2", ".ms2"),

    /** The compressed MS2 format. */
    CMS2("CMS2", ".cms2"),

    /** The binary MS2 format. */
    BMS2("BMS2", ".bms2"),

    /** A Thermo RAW file, which Comet reads only on Windows. */
    THERMO_RAW("Thermo RAW", ".raw");

    private final String displayName;
    private final String extension;

    SpectrumFileFormat(String displayName, String extension) {
        this.displayName = displayName;
        this.extension = extension;
    }

    /**
     * The format's name as a scientist knows it.
     *
     * @return the name, such as {@code mzML}
     */
    public String displayName() {
        return displayName;
    }

    /**
     * The extension, in the case Comet's documentation writes it.
     *
     * @return the extension with its leading dot, such as {@code .mzML}
     */
    public String extension() {
        return extension;
    }

    /**
     * The format a file name declares by its extension.
     *
     * @param fileName a file name, without directories
     * @return the format, or empty if the name ends in no spectrum extension Comet reads
     * @throws NullPointerException if {@code fileName} is {@code null}
     */
    public static Optional<SpectrumFileFormat> ofFileName(String fileName) {
        String folded = Objects.requireNonNull(fileName, "fileName").toLowerCase(Locale.ROOT);
        for (SpectrumFileFormat format : values()) {
            if (folded.endsWith(format.extension.toLowerCase(Locale.ROOT))) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }
}
