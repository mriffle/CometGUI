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

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One MS2 spectrum of a run's spectrum source, as the file states it.
 *
 * <p>Values that are text in the file stay text: the precursor m/z is the file's own decimal
 * string, never a {@code double} printed back, so nothing downstream can round it by accident.
 *
 * @param nativeId the spectrum's identifier in its file: an mzML {@code id} attribute (which Comet
 *     copies into the pepXML as {@code spectrumNativeID}), or an MGF entry's {@code TITLE}
 * @param index the spectrum's 0-based index: an mzML spectrum's {@code index} attribute, or an MGF
 *     entry's ordinal
 * @param position the spectrum's 1-based position among every spectrum of the file, MS1 included --
 *     the number PDV's mzML library ({@code msftbx}) calls a scan number
 * @param scanNumber the instrument scan number, when the native id carries one as {@code scan=<n>}
 * @param precursorMz the selected ion's m/z as the file's text: an mzML {@code selected ion m/z}
 *     ({@code MS:1000744}) value, or the first field of an MGF {@code PEPMASS}
 * @param charge the precursor charge, signed, when the file gives one
 * @param retentionTime the retention time as the file states it, when it does
 * @param peaks the peaks
 */
public record SourceSpectrum(
        String nativeId,
        int index,
        int position,
        OptionalLong scanNumber,
        Optional<String> precursorMz,
        OptionalInt charge,
        Optional<RetentionTime> retentionTime,
        PeakList peaks) {

    private static final Pattern SCAN = Pattern.compile("(?:^|\\s)scan=(\\d{1,18})(?:\\s|$)");

    /**
     * Validates the spectrum.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if the native id is empty, the index is negative or the
     *     position is not at least 1
     */
    public SourceSpectrum {
        Objects.requireNonNull(nativeId, "nativeId");
        Objects.requireNonNull(scanNumber, "scanNumber");
        Objects.requireNonNull(precursorMz, "precursorMz");
        Objects.requireNonNull(charge, "charge");
        Objects.requireNonNull(retentionTime, "retentionTime");
        Objects.requireNonNull(peaks, "peaks");
        if (nativeId.isEmpty()) {
            throw new IllegalArgumentException("a spectrum's native id must not be empty");
        }
        if (index < 0) {
            throw new IllegalArgumentException("a spectrum's index must not be negative: " + index);
        }
        if (position < 1) {
            throw new IllegalArgumentException(
                    "a spectrum's 1-based position must be at least 1: " + position);
        }
    }

    /**
     * The instrument scan number a native id carries, as {@code scan=<n>} delimited by whitespace
     * or the ends of the id -- the form of a Thermo native id such as {@code controllerType=0
     * controllerNumber=1 scan=11367}.
     *
     * @param nativeId the native id
     * @return the scan number, or empty if the id carries none
     */
    public static OptionalLong scanNumberOf(String nativeId) {
        Matcher matcher = SCAN.matcher(Objects.requireNonNull(nativeId, "nativeId"));
        return matcher.find()
                ? OptionalLong.of(Long.parseLong(matcher.group(1)))
                : OptionalLong.empty();
    }
}
