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

package org.cometgui.tools.pdv.fidelity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cometgui.tools.pdv.PdvMzTabException;
import org.cometgui.tools.pdv.PdvMzTabExporter;
import org.cometgui.tools.pdv.PdvMzTabRequest;
import org.cometgui.tools.pdv.PdvMzTabSource;
import org.cometgui.tools.pdv.SpectrumFileFormat;
import org.cometgui.tools.testing.K562Run;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Phase 11 exit gate item 8: a PSM that cannot be represented faithfully, injected into a COPY of
 * the real K562 sources, makes the export fail loudly -- the message naming that PSM and the reason
 * -- and leaves no mzTab or MGF at the final path, nor anything else beside it.
 *
 * <p>Each injection is checked to have landed in the copy, and the same copies without it are shown
 * to export (the K562 run itself does, in {@link K562FidelityTest}), so the refusal is the
 * injection's.
 */
class UnrepresentableTest {

    private static final String BASE_3 = K562FidelityTest.BASE_3;
    private static final String BASE_4 = K562FidelityTest.BASE_4;

    /** The first target row of the real table: Percolator's best PSM, scan 11367 of {@code _3}. */
    private static final String FIRST = BASE_3 + "_11367_2_1";

    private static final String FIRST_ROW =
            FIRST + "\t2.37114\t0.00112905\t9.27998e-07\tR.FNLTYVSHDGDDK.K\tsp|P26639|SYTC_HUMAN";

    private Path work;
    private Path targets;
    private Path decoys;
    private Path pepXml3;
    private Path mzMl3;
    private Path out;

    @BeforeEach
    void copyTheSources() throws IOException {
        work = FidelityFiles.workDirectory("fidelity-refused-");
        Path sources = Files.createDirectories(work.resolve("sources"));
        targets = Files.copy(K562Run.percolatorTargets(), sources.resolve("psms.target.txt"));
        decoys = Files.copy(K562Run.percolatorDecoys(), sources.resolve("psms.decoy.txt"));
        pepXml3 = Files.copy(K562Run.pepXml(), sources.resolve(BASE_3 + ".pep.xml"));
        mzMl3 = Files.copy(K562Run.mzml(), sources.resolve(BASE_3 + ".mzML"));
        out = Files.createDirectories(work.resolve("out"));
    }

    @AfterEach
    void deleteTheCopies() throws IOException {
        FidelityFiles.delete(work);
    }

    private PdvMzTabRequest request() throws IOException {
        return new PdvMzTabRequest(
                targets,
                Optional.of(decoys),
                List.of(
                        new PdvMzTabSource(
                                BASE_3, mzMl3, SpectrumFileFormat.MZML, pepXml3, Optional.empty()),
                        new PdvMzTabSource(
                                BASE_4,
                                K562Run.mzml4(),
                                SpectrumFileFormat.MZML,
                                K562Run.pepXml4(),
                                Optional.empty())),
                out.resolve("mztab"));
    }

    /** Replaces exactly one occurrence, and proves it was there and the file changed. */
    private static void inject(Path file, String from, String to) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        int at = text.indexOf(from);
        assertTrue(at >= 0, "the injection's anchor is in " + file + ": " + from);
        assertEquals(-1, text.indexOf(from, at + 1), "the anchor is unique: " + from);
        String injured = text.substring(0, at) + to + text.substring(at + from.length());
        Files.writeString(file, injured, StandardCharsets.UTF_8);
        assertFalse(text.equals(Files.readString(file, StandardCharsets.UTF_8)), "injected");
    }

    /**
     * Exports, expects the refusal, and proves it loud and named, with nothing left behind.
     *
     * @return the message
     */
    private String refused(String psmId, String reason, Path source) throws IOException {
        PdvMzTabException refusal =
                assertThrows(PdvMzTabException.class, () -> PdvMzTabExporter.export(request()));
        String message = refusal.getMessage();
        List<Path> left = FidelityFiles.files(out);
        List<String> entries = new ArrayList<>();
        try (var listing = Files.list(out)) {
            listing.forEach(path -> entries.add(String.valueOf(path.getFileName())));
        }
        assertAll(
                () ->
                        assertTrue(
                                message.contains("PSM " + psmId + " "),
                                "the message names the PSM " + psmId + ": " + message),
                () ->
                        assertTrue(
                                message.endsWith("[" + reason + "]"),
                                "the message names the reason " + reason + ": " + message),
                () ->
                        assertTrue(
                                message.contains(source.toString()),
                                "the message names the source " + source + ": " + message),
                () -> assertFalse(Files.exists(out.resolve("mztab")), "no export directory"),
                () -> assertEquals(List.of(), left, "no mzTab, MGF or staging file is left"),
                () -> assertEquals(List.of(), entries, "nothing at all is left beside it"));
        return message;
    }

    @Test
    @DisplayName("a Percolator row with no pepXML hit (position 9 of a five-hit query) is refused")
    void percolatorRowWithoutHit() throws IOException {
        String orphan = BASE_3 + "_11367_2_9";
        PepXmlFile pepXml = PepXmlFile.read(pepXml3);
        assertEquals(
                5,
                pepXml.queries("11367", "2").get(0).hits().size(),
                "the suite's own reader: scan 11367 charge 2 has five hits, so no ninth");
        Files.writeString(
                targets,
                orphan + "\t-1.5\t1\t1\tK.ORPHANK.L\tsp|P00001|ONE_INVENTED\n",
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
        refused(orphan, "NO_PEPXML_HIT", pepXml3);
    }

    @Test
    @DisplayName(
            "a hit with a C-terminal modification -- Comet's own form, agreeing with Percolator --"
                    + " is refused")
    void cTerminalModification() throws IOException {
        inject(
                pepXml3,
                "  <aminoacid_modification aminoacid=\"M\"",
                "  <terminal_modification terminus=\"C\" massdiff=\"14.015650\" mass=\"31.018390\""
                        + " variable=\"Y\" protein_terminus=\"N\"/>\n"
                        + "  <aminoacid_modification aminoacid=\"M\"");
        String text = Files.readString(pepXml3, StandardCharsets.UTF_8);
        int query = text.indexOf("spectrum=\"" + BASE_3 + ".11367.11367.2\"");
        int hit = text.indexOf("peptide=\"FNLTYVSHDGDDK\" peptide_prev_aa=\"R\"", query);
        assertTrue(query > 0 && hit > query, "the first hit of scan 11367 charge 2");
        int scores = text.indexOf("<search_score", hit);
        Files.writeString(
                pepXml3,
                text.substring(0, scores)
                        + "<modification_info modified_peptide=\"FNLTYVSHDGDDKc[31]\""
                        + " mod_cterm_mass=\"31.018390\">\n    </modification_info>\n    "
                        + text.substring(scores),
                StandardCharsets.UTF_8);
        PepXmlFile.Hit injected =
                PepXmlFile.read(pepXml3).queries("11367", "2").get(0).hits().get(0);
        assertEquals("31.018390", injected.ctermMass(), "the suite's reader sees the injection");
        inject(
                targets,
                FIRST_ROW,
                FIRST_ROW.replace("R.FNLTYVSHDGDDK.K", "R.FNLTYVSHDGDDKc[14.0157].K"));
        refused(FIRST, "UNREPRESENTABLE_MODIFICATION", pepXml3);
    }

    @Test
    @DisplayName("a peptide on which Percolator and the pepXML disagree is refused")
    void peptideDisagreement() throws IOException {
        inject(targets, FIRST_ROW, FIRST_ROW.replace("R.FNLTYVSHDGDDK.K", "R.FNLTYVSHDGEDK.K"));
        refused(FIRST, "PEPTIDE_MISMATCH", targets);
    }

    @Test
    @DisplayName("a modification on which Percolator and the pepXML disagree is refused")
    void modificationDisagreement() throws IOException {
        inject(
                targets,
                FIRST_ROW,
                FIRST_ROW.replace("R.FNLTYVSHDGDDK.K", "R.FNLTYVS[79.9663]HDGDDK.K"));
        refused(FIRST, "PEPTIDE_MISMATCH", targets);
    }

    @Test
    @DisplayName("a spectrum the pepXML names that the spectrum file lacks is refused")
    void spectrumMissing() throws IOException {
        String id = "controllerType=0 controllerNumber=1 scan=11367";
        // Same length, so the file's index offsets still hold: the file simply has no such id.
        inject(mzMl3, "id=\"" + id + "\"", "id=\"controllerType=0 controllerNumber=9 scan=11367\"");
        assertEquals(
                List.of(),
                MzMlFile.read(mzMl3).withId(id),
                "the suite's own reader finds no spectrum " + id);
        String message = refused(FIRST, "NO_SPECTRUM", mzMl3);
        assertTrue(message.contains(id), "the message names the missing spectrum: " + message);
    }
}
