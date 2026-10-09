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

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * A deliberately minimal counter of one Percolator table at one q-value cutoff, independent of the
 * code under test (design decision P10-3): each line split on tabs, the q-value column found by its
 * header name, {@link BigDecimal} compared with the cutoff, the boundary inclusive. A field that is
 * not a decimal within {@code [0, 1]} -- empty, {@code NaN}, a comma, out of range -- is unknown.
 * It uses no {@code org.cometgui.results} class.
 *
 * @param total the data rows
 * @param passing known and at or below the cutoff
 * @param failing known and above it
 * @param unknown not a decimal within {@code [0, 1]}
 */
record IndependentTableCount(long total, long passing, long failing, long unknown) {

    /** Counts a table. */
    static IndependentTableCount at(Path table, BigDecimal cutoff) throws IOException {
        List<String> lines = Files.readAllLines(table, StandardCharsets.UTF_8);
        int column = Arrays.asList(lines.get(0).split("\t", -1)).indexOf("q-value");
        if (column < 0) {
            throw new IllegalStateException(table + " has no q-value column");
        }
        long passing = 0;
        long failing = 0;
        long unknown = 0;
        for (String line : lines.subList(1, lines.size())) {
            BigDecimal q;
            try {
                q = new BigDecimal(line.split("\t", -1)[column]);
            } catch (NumberFormatException notADecimal) {
                unknown++;
                continue;
            }
            if (q.signum() < 0 || q.compareTo(BigDecimal.ONE) > 0) {
                unknown++;
            } else if (q.compareTo(cutoff) <= 0) {
                passing++;
            } else {
                failing++;
            }
        }
        return new IndependentTableCount(lines.size() - 1L, passing, failing, unknown);
    }
}
