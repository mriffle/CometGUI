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

package org.cometgui.app.e2e.oracle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A Percolator input (PIN) file, read by the test's own code (P14-7): its header, how many data
 * rows it has, how many of them came from each spectrum file, and how many are targets and decoys
 * -- what canonical scenario step 19, "the merged PIN has one header and the summed row count of
 * its sources", needs from each source PIN and from the merged one.
 *
 * <h2>The format, as Comet writes it</h2>
 *
 * <p>Tab-separated text. The first line is the header and begins {@code SpecId}, {@code Label};
 * every later non-blank line is a data row, unless it begins {@code DefaultDirection} (a weights
 * line Percolator accepts as the second line, which Comet does not write and which is counted
 * apart, never as a row). A row's {@code SpecId} is {@code <base>_<scan>_<charge>_<rank>}, where
 * {@code <base>} is the {@code -N} base name Comet was given -- the spectrum file's name without
 * its extension -- and may itself contain underscores; so the source of a row is its {@code SpecId}
 * with its last three underscore-separated fields removed. Its {@code Label} is {@code 1} for a
 * target and {@code -1} for a decoy.
 *
 * <p>A later line that repeats the header exactly is counted in {@link #headerLines()} and not as a
 * row: a concatenating merge that copied every source's header would have more than one, which is
 * the defect step 19 exists to catch.
 *
 * @param header the first line's fields
 * @param headerLines how many lines are exactly the header line, the first included
 * @param rows how many data rows there are
 * @param rowsBySource how many data rows each {@code -N} base name contributed, in first-seen order
 * @param labels how many data rows carry each {@code Label} value, in first-seen order
 * @param defaultDirectionLines how many lines begin {@code DefaultDirection}
 * @param malformed data rows whose {@code SpecId} has fewer than four underscore-separated fields,
 *     or which have fewer fields than the header, each prefixed with its line number
 */
public record PinFile(
        List<String> header,
        int headerLines,
        long rows,
        Map<String, Long> rowsBySource,
        Map<String, Long> labels,
        int defaultDirectionLines,
        List<String> malformed) {

    /** The parts, copied; map iteration keeps first-seen order. */
    public PinFile {
        header = List.copyOf(header);
        rowsBySource = Collections.unmodifiableMap(new LinkedHashMap<>(rowsBySource));
        labels = Collections.unmodifiableMap(new LinkedHashMap<>(labels));
        malformed = List.copyOf(malformed);
    }

    /**
     * Reads a PIN file as UTF-8.
     *
     * @param file the file
     * @return what it holds
     * @throws IOException if it cannot be read
     * @throws AssertionError if it is empty or its first line is not a PIN header
     */
    public static PinFile read(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8), file.toString());
    }

    /**
     * Reads a PIN file's text.
     *
     * @param text the whole file
     * @param name what to call it in a failure message
     * @return what it holds
     * @throws AssertionError if it is empty or its first line is not a PIN header
     */
    public static PinFile parse(String text, String name) {
        Objects.requireNonNull(text, "text");
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\n", -1)) {
            lines.add(raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw);
        }
        String headerLine = lines.get(0);
        List<String> header = List.of(headerLine.split("\t", -1));
        if (header.size() < 2
                || !header.get(0).equals("SpecId")
                || !header.get(1).equals("Label")) {
            throw new AssertionError(
                    name + " does not begin with a PIN header (SpecId, Label, ...): " + headerLine);
        }
        int headers = 1;
        long rows = 0;
        int directions = 0;
        Map<String, Long> bySource = new LinkedHashMap<>();
        Map<String, Long> labels = new LinkedHashMap<>();
        List<String> malformed = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }
            if (line.equals(headerLine)) {
                headers++;
                continue;
            }
            if (line.startsWith("DefaultDirection")) {
                directions++;
                continue;
            }
            rows++;
            String[] fields = line.split("\t", -1);
            if (fields.length < header.size()) {
                malformed.add((index + 1) + ": " + fields.length + " fields, not " + header.size());
            }
            labels.merge(fields.length > 1 ? fields[1] : "", 1L, Long::sum);
            String specId = fields[0];
            String base = specId;
            boolean whole = true;
            for (int strip = 0; strip < 3 && whole; strip++) {
                int underscore = base.lastIndexOf('_');
                whole = underscore > 0;
                base = whole ? base.substring(0, underscore) : base;
            }
            if (!whole) {
                malformed.add((index + 1) + ": SpecId " + specId + " is not base_scan_charge_rank");
                continue;
            }
            bySource.merge(base, 1L, Long::sum);
        }
        return new PinFile(header, headers, rows, bySource, labels, directions, malformed);
    }

    /**
     * How many data rows are targets.
     *
     * @return the rows labelled {@code 1}
     */
    public long targets() {
        return labels.getOrDefault("1", 0L);
    }

    /**
     * How many data rows are decoys.
     *
     * @return the rows labelled {@code -1}
     */
    public long decoys() {
        return labels.getOrDefault("-1", 0L);
    }
}
