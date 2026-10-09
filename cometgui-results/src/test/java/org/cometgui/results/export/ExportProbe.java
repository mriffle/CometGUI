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

package org.cometgui.results.export;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.file.Path;
import java.time.Clock;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.results.filtering.store.Category;
import org.cometgui.results.filtering.store.TableKind;

/**
 * Runs one export of a PSM table in a child JVM whose heap {@link LargeExportTest} fixes, and
 * prints one line, {@value #RESULT} followed by {@code key=value} fields: the time, the rows
 * written, the counts and the peak heap.
 *
 * <p>Arguments: the table, a scratch directory for the run, the cutoff, the category's name.
 */
public final class ExportProbe {

    /** The first word of the result line. */
    static final String RESULT = "EXPORT-RESULT";

    private ExportProbe() {}

    /**
     * Runs the export.
     *
     * @param arguments the table, a scratch directory, the cutoff, the category's name
     * @throws IOException if the export fails; the child then exits non-zero
     */
    public static void main(String[] arguments) throws IOException {
        Path table = Path.of(arguments[0]);
        RunLayout run = ExportTables.newRun(Path.of(arguments[1]));
        ResultExporter exporter = ExportTables.exporter(run, Clock.systemUTC());
        long start = System.nanoTime();
        TableExport export =
                exporter.exportTable(
                        table,
                        TableKind.TARGET_PSMS,
                        ExportTables.filterFor(TableKind.TARGET_PSMS, arguments[2]),
                        Category.valueOf(arguments[3]));
        long millis = (System.nanoTime() - start) / 1_000_000;
        long peak = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                peak += pool.getPeakUsage().getUsed();
            }
        }
        System.out.println(
                RESULT
                        + " ms="
                        + millis
                        + " rows="
                        + export.rowsWritten()
                        + " total="
                        + export.before().total()
                        + " passing="
                        + export.before().passing()
                        + " failing="
                        + export.before().failing()
                        + " unknown="
                        + export.before().unknownQValue()
                        + " peakHeapBytes="
                        + peak
                        + " maxHeapBytes="
                        + Runtime.getRuntime().maxMemory()
                        + " sha256="
                        + export.exportHashes().sha256()
                        + " file="
                        + export.file());
    }
}
