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

package org.cometgui.app.performance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import org.cometgui.app.config.ParameterEditorWiring;
import org.cometgui.app.testing.ArtefactMirror;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.ui.viewmodel.params.ImportOutcome;
import org.cometgui.ui.viewmodel.params.ParameterFilesViewModel;
import org.cometgui.ui.viewmodel.params.ParameterSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Performance (b): parsing and validating a large Comet parameter file, through the product's own
 * import -- {@link ParameterFilesViewModel#importFile}, the Comet Parameters section's import
 * action: the file read as UTF-8, parsed by the release's parser, adopted into a fresh session and
 * validated by the standard validator, as a scientist's import is.
 *
 * <p><strong>The file is generated here, never committed</strong>: Comet 2026.03.0's own complete
 * {@code comet -q} output (the committed fixture under {@code cometgui-params-comet}) -- every
 * parameter the release knows -- with all fifteen variable-modification slots set, the enzyme table
 * extended to {@code params.enzymes} rows, and {@code params.unknowns} parameters the release does
 * not know, each with a comment, plus a comment line between every ten of them. The checks prove
 * the import read all of it: the outcome is {@code IMPORTED}, the session's model holds every
 * unknown parameter, every enzyme row and fifteen set modifications, and the validation report was
 * produced.
 *
 * <p>Each sample is one import into a session made before the clock starts. Opt-in (P15-5).
 */
@EnabledIfSystemProperty(
        named = PerfRecorder.ENABLE,
        matches = "true",
        disabledReason = PerfRecorder.DISABLED)
class PerformanceParameters {

    /** The measurement's identifier in the data. */
    static final String ID = "params-import";

    /** The release whose complete parameter file is the base. */
    static final String RELEASE = "2026.03.0";

    private static final String BASE =
            "cometgui-params-comet/src/test/resources/fixtures/comet/"
                    + RELEASE
                    + "/linux-x86-64/comet-q.params";

    private static final BuildIdentity BUILD =
            BuildIdentity.of("0.0.0-performance", "unknown", Instant.parse("2026-11-06T00:00:00Z"));

    /** Fifteen distinct, valid variable modifications: mass and residues. */
    private static final String[][] MODIFICATIONS = {
        {"15.9949", "M"},
        {"79.966331", "STY"},
        {"0.984016", "NQ"},
        {"42.010565", "K"},
        {"14.01565", "KR"},
        {"28.0313", "K"},
        {"-17.026549", "Q"},
        {"-18.010565", "E"},
        {"57.021464", "C"},
        {"114.042927", "K"},
        {"31.989829", "W"},
        {"15.994915", "P"},
        {"21.981943", "DE"},
        {"203.079373", "ST"},
        {"-0.984016", "G"},
    };

    private Path work;

    @AfterEach
    void cleanUp() throws IOException {
        PerfRecorder.delete(work);
    }

    @Test
    @DisplayName("a large parameter file imported, parsed and validated through the product")
    void importALargeParameterFile() throws IOException {
        OptionalInt repeats = PerfRecorder.repeats(ID);
        if (repeats.isEmpty()) {
            return;
        }
        int unknowns = (int) PerfRecorder.longParameter("params.unknowns");
        int enzymes = (int) PerfRecorder.longParameter("params.enzymes");
        work = PerfRecorder.work("parameters");
        Path file = work.resolve("large-comet.params");
        Files.writeString(file, generate(unknowns, enzymes), StandardCharsets.UTF_8);

        PerfRecorder.Measurement measurement = PerfRecorder.start(ID, "ms");
        measurement.evidence("file", "Comet " + RELEASE + " comet -q, enlarged");
        measurement.evidence("bytes", Files.size(file));
        measurement.evidence("lines", Files.readAllLines(file, StandardCharsets.UTF_8).size());
        measurement.evidence("unknownParameters", unknowns);
        measurement.evidence("enzymeRows", enzymes);
        int findings = -1;
        ParameterSession last = null;
        ImportOutcome lastOutcome = null;
        for (int sample = 0; sample < repeats.getAsInt(); sample++) {
            ParameterSession session = ParameterEditorWiring.newSession();
            ParameterFilesViewModel files =
                    new ParameterFilesViewModel(session, BUILD, new StreamingHashService());
            long start = System.nanoTime();
            ImportOutcome outcome = files.importFile(file);
            findings = session.report().findings().size();
            measurement.sample(PerfRecorder.millisSince(start));
            last = session;
            lastOutcome = outcome;
        }
        measurement.checkEquals(
                "the import's outcome", ImportOutcome.Kind.IMPORTED, lastOutcome.kind());
        CometParameters model = last.model();
        measurement.checkEquals("the session's release", RELEASE, last.release().text());
        measurement.checkEquals(
                "every unknown parameter is held", unknowns, model.unknownParameters().size());
        measurement.checkEquals(
                "every enzyme row is held", enzymes, model.enzymeTable().rows().size());
        List<String> set = new ArrayList<>();
        for (int slot = 1; slot <= MODIFICATIONS.length; slot++) {
            String text = model.text(String.format(Locale.ROOT, "variable_mod%02d", slot));
            if (text.startsWith(MODIFICATIONS[slot - 1][0] + " ")) {
                set.add(text);
            }
        }
        measurement.checkEquals("fifteen variable modifications are set", 15, set.size());
        measurement.evidence("validationFindings", findings);
        measurement.evidence(
                "validationErrors",
                last.report().errors().stream().map(String::valueOf).limit(10).toList());
        measurement.evidence("importMessages", lastOutcome.messages().size());
        measurement.finish();
    }

    /**
     * The large file: the release's complete parameter file with every slot set, the unknown
     * parameters before the enzyme section and the enzyme table extended.
     */
    static String generate(int unknowns, int enzymes) throws IOException {
        Path base = ArtefactMirror.repositoryRoot().resolve(BASE);
        List<String> lines = Files.readAllLines(base, StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder();
        int enzymeSection = -1;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.startsWith("[COMET_ENZYME_INFO]")) {
                enzymeSection = index;
                break;
            }
            if (line.startsWith("variable_mod") && !line.startsWith("variable_mod_")) {
                int slot = Integer.parseInt(line.substring(12, 14));
                String[] modification = MODIFICATIONS[slot - 1];
                out.append(
                        String.format(
                                Locale.ROOT,
                                "variable_mod%02d = %s %s 0 3 -1 0 0 0.0%n",
                                slot,
                                modification[0],
                                modification[1]));
                continue;
            }
            out.append(line).append('\n');
        }
        assertTrue(enzymeSection > 0, "the base file has an enzyme section: " + base);
        out.append("#\n# parameters this release does not know, kept as written\n#\n");
        for (int unknown = 0; unknown < unknowns; unknown++) {
            if (unknown % 10 == 0) {
                out.append("# a comment between unknown parameters, block ")
                        .append(unknown / 10)
                        .append('\n');
            }
            out.append(
                    String.format(
                            Locale.ROOT,
                            "cometgui_performance_unknown_%06d = %d   # unknown parameter %d%n",
                            unknown,
                            unknown * 7,
                            unknown));
        }
        int kept = 0;
        for (int index = enzymeSection; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }
            out.append(line).append('\n');
            if (!line.startsWith("[")) {
                kept++;
            }
        }
        for (int number = kept; number < enzymes; number++) {
            out.append(
                    String.format(
                            Locale.ROOT,
                            "%d.  Performance_enzyme_%06d   1   %s   P%n",
                            number,
                            number,
                            number % 2 == 0 ? "KR" : "DE"));
        }
        return out.toString();
    }
}
