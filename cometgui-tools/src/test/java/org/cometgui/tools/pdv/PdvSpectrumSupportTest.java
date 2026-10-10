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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.run.OutputBaseNames;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Gate item 3's decision: which spectrum sources PDV can be given, and the specified error. */
class PdvSpectrumSupportTest {

    static final ToolVersion PDV = ToolVersion.parse("2.7.0");

    static final String OFFER =
            " PDV 2.7.0 can be given mzML and MGF spectrum files, which CometGUI re-encodes as MGF"
                    + " for it.";

    @TempDir private Path directory;

    private static PdvSpectrumSupport support() {
        return PdvSpectrumSupport.forVersion(PDV);
    }

    private UnsupportedSpectrumFileException refused(
            PdvSpectrumSupport support, String name, String content) throws IOException {
        Path file = Files.writeString(directory.resolve(name), content);
        UnsupportedSpectrumFileException thrown =
                assertThrows(UnsupportedSpectrumFileException.class, () -> support.check(file));
        assertEquals(file, thrown.file());
        return thrown;
    }

    private String refusal(String name, String content) throws IOException {
        return refused(support(), name, content).getMessage();
    }

    private String start(String name) {
        return "The spectrum file "
                + directory.resolve(name)
                + " cannot be visualised by PDV 2.7.0: ";
    }

    @Test
    @DisplayName("the formats are exactly the spectrum extensions the workflow accepts")
    void formatsAreCometsInputs() {
        List<String> extensions = new ArrayList<>();
        for (SpectrumFileFormat format : SpectrumFileFormat.values()) {
            extensions.add(format.extension());
        }
        assertEquals(OutputBaseNames.SPECTRUM_EXTENSIONS, extensions);
    }

    @Test
    @DisplayName("a file name's extension decides its format, in any case")
    void byExtension() {
        assertAll(
                () ->
                        assertEquals(
                                Optional.of(SpectrumFileFormat.MZML),
                                SpectrumFileFormat.ofFileName("a.mzML")),
                () ->
                        assertEquals(
                                Optional.of(SpectrumFileFormat.MZML),
                                SpectrumFileFormat.ofFileName("A.MZML")),
                () ->
                        assertEquals(
                                Optional.of(SpectrumFileFormat.MZXML),
                                SpectrumFileFormat.ofFileName("a.mzxml")),
                () ->
                        assertEquals(
                                Optional.of(SpectrumFileFormat.MGF),
                                SpectrumFileFormat.ofFileName("a.MGF")),
                () ->
                        assertEquals(
                                Optional.of(SpectrumFileFormat.MS2),
                                SpectrumFileFormat.ofFileName("a.ms2")),
                () ->
                        assertEquals(
                                Optional.of(SpectrumFileFormat.CMS2),
                                SpectrumFileFormat.ofFileName("a.cms2")),
                () ->
                        assertEquals(
                                Optional.of(SpectrumFileFormat.BMS2),
                                SpectrumFileFormat.ofFileName("a.bms2")),
                () ->
                        assertEquals(
                                Optional.of(SpectrumFileFormat.THERMO_RAW),
                                SpectrumFileFormat.ofFileName("a.RAW")),
                () -> assertEquals(Optional.empty(), SpectrumFileFormat.ofFileName("a.mzML.gz")),
                () -> assertEquals(Optional.empty(), SpectrumFileFormat.ofFileName("mzML")),
                () -> assertEquals("Thermo RAW", SpectrumFileFormat.THERMO_RAW.displayName()),
                () ->
                        assertEquals(
                                "fileName",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        SpectrumFileFormat.ofFileName(
                                                                Nulls.of(String.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("PDV 2.7.0 can be given mzML and MGF; an unverified version nothing")
    void supported() {
        assertAll(
                () ->
                        assertEquals(
                                Set.of(SpectrumFileFormat.MZML, SpectrumFileFormat.MGF),
                                support().supported()),
                () -> assertEquals(PDV, support().pdvVersion()),
                () ->
                        assertEquals(
                                Set.of(),
                                PdvSpectrumSupport.forVersion(ToolVersion.parse("2.6.0"))
                                        .supported()),
                () ->
                        assertEquals(
                                "pdvVersion",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        PdvSpectrumSupport.forVersion(
                                                                Nulls.of(ToolVersion.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("mzML, indexed mzML and MGF sources are accepted, their content confirmed")
    void accepted() throws IOException {
        Path plain = MzMlText.document().write(directory, "a.mzML");
        Path indexed = MzMlText.document().indexed().write(directory, "b.MZML");
        Path mgf = Files.writeString(directory.resolve("c.mgf"), "\n# made\nBEGIN IONS\n");
        Path parameter = Files.writeString(directory.resolve("d.mgf"), "; x\nCOM=hello\n");
        assertAll(
                () -> assertEquals(SpectrumFileFormat.MZML, support().check(plain)),
                () -> assertEquals(SpectrumFileFormat.MZML, support().check(indexed)),
                () -> assertEquals(SpectrumFileFormat.MGF, support().check(mgf)),
                () -> assertEquals(SpectrumFileFormat.MGF, support().check(parameter)));
    }

    @Test
    @DisplayName(
            "every format PDV cannot be given is the specified error, naming file, format,"
                    + " version and what is supported")
    void unsupportedFormats() throws IOException {
        String readsOnly = " file, and PDV 2.7.0 reads only MGF, mzML and mzXML spectrum files.";
        assertAll(
                () ->
                        assertEquals(
                                start("a.mzXML")
                                        + "it is an mzXML file, which PDV 2.7.0's "
                                        + "denovo-gui mode cannot read and which "
                                        + "CometGUI does not yet re-encode for it: the "
                                        + "MGF title must repeat the native id Comet "
                                        + "records for each spectrum, and what Comet "
                                        + "records for an mzXML spectrum has not been "
                                        + "established."
                                        + OFFER,
                                refusal("a.mzXML", "<mzXML/>")),
                () ->
                        assertEquals(
                                start("a.ms2") + "it is an MS2" + readsOnly + OFFER,
                                refusal("a.ms2", "S 1")),
                () ->
                        assertEquals(
                                start("a.cms2") + "it is a CMS2" + readsOnly + OFFER,
                                refusal("a.cms2", "")),
                () ->
                        assertEquals(
                                start("a.bms2") + "it is a BMS2" + readsOnly + OFFER,
                                refusal("a.bms2", "")),
                () ->
                        assertEquals(
                                start("a.raw") + "it is a Thermo RAW" + readsOnly + OFFER,
                                refusal("a.raw", "")),
                () ->
                        assertEquals(
                                start("a.mzML.gz")
                                        + "its name ends in none of the extensions "
                                        + "Comet reads (.mzML, .mzXML, .mgf, .ms2, "
                                        + ".cms2, .bms2, .raw), so it is not a spectrum "
                                        + "file CometGUI knows."
                                        + OFFER,
                                refusal("a.mzML.gz", "")));
    }

    @Test
    @DisplayName("the exception carries the file, the format and the version")
    void exceptionFacts() throws IOException {
        UnsupportedSpectrumFileException raw = refused(support(), "x.raw", "");
        UnsupportedSpectrumFileException unknown = refused(support(), "x.txt", "");
        assertAll(
                () -> assertEquals(Optional.of(SpectrumFileFormat.THERMO_RAW), raw.format()),
                () -> assertEquals(PDV, raw.pdvVersion()),
                () -> assertEquals(Optional.empty(), unknown.format()));
    }

    @Test
    @DisplayName("an unverified PDV version is given nothing, and says which version is verified")
    void unverifiedVersion() throws IOException {
        PdvSpectrumSupport old = PdvSpectrumSupport.forVersion(ToolVersion.parse("2.6.0"));
        Path file = MzMlText.document().write(directory, "a.mzML");
        UnsupportedSpectrumFileException thrown =
                assertThrows(UnsupportedSpectrumFileException.class, () -> old.check(file));
        assertAll(
                () ->
                        assertEquals(
                                "The spectrum file "
                                        + file
                                        + " cannot be visualised by PDV 2.6.0: it is an "
                                        + "mzML file, and CometGUI has verified which "
                                        + "spectrum formats PDV can be given only for "
                                        + "PDV 2.7.0. PDV 2.6.0 can be given no "
                                        + "spectrum format until it is verified; PDV "
                                        + "2.7.0 can be given mzML and MGF.",
                                thrown.getMessage()),
                () -> assertEquals(ToolVersion.parse("2.6.0"), thrown.pdvVersion()));
    }

    @Test
    @DisplayName(
            "a file whose content is not the format its "
                    + "name declares is refused, saying what it is")
    void mislabelled() throws IOException {
        StringBuilder longLine = new StringBuilder();
        for (int i = 0; i < 70; i++) {
            longLine.append((char) ('a' + i % 26));
        }
        assertAll(
                () ->
                        assertTrue(
                                refusal("a.mzML", "BEGIN IONS\n")
                                        .startsWith(
                                                start("a.mzML")
                                                        + "it is named as an mzML file but its"
                                                        + " content is not mzML: it does not"
                                                        + " begin as XML ("),
                                refusal("a.mzML", "BEGIN IONS\n")),
                () ->
                        assertEquals(
                                start("b.mzML")
                                        + "it is named as an mzML file but its content "
                                        + "is not mzML: its first element is <mzXML>, "
                                        + "not <mzML>."
                                        + OFFER,
                                refusal("b.mzML", "<?xml version=\"1.0\"?>\n<!-- x -->\n<mzXML/>")),
                () ->
                        assertEquals(
                                start("c.mgf")
                                        + "it is named as an MGF file but its content "
                                        + "is not MGF: its first line of content is "
                                        + "\"<?xml version=\"1.0\"?>\", which is "
                                        + "neither BEGIN IONS nor a KEY=value parameter."
                                        + OFFER,
                                refusal("c.mgf", "<?xml version=\"1.0\"?>\n<mzML/>")),
                () ->
                        assertEquals(
                                start("d.mgf")
                                        + "it is named as an MGF file but its content "
                                        + "is not MGF: it is empty."
                                        + OFFER,
                                refusal("d.mgf", "")),
                () ->
                        assertEquals(
                                start("e.mgf")
                                        + "it is named as an MGF file but its content "
                                        + "is not MGF: it holds nothing but blank and "
                                        + "comment lines."
                                        + OFFER,
                                refusal("e.mgf", "\n# only\n  \n")),
                () ->
                        assertEquals(
                                start("f.mgf")
                                        + "it is named as an MGF file but its content "
                                        + "is not MGF: its first line of content is \""
                                        + longLine.substring(0, 60)
                                        + "...\", which is neither BEGIN IONS nor a "
                                        + "KEY=value parameter."
                                        + OFFER,
                                refusal("f.mgf", longLine + "\n")),
                () ->
                        assertEquals(
                                start("g.mgf")
                                        + "it is named as an MGF file but its content "
                                        + "is not MGF: its first line of content is \""
                                        + longLine.substring(0, 60)
                                        + "\", which is neither BEGIN IONS nor a "
                                        + "KEY=value parameter."
                                        + OFFER,
                                refusal("g.mgf", longLine.substring(0, 60) + "\n")));
    }

    @Test
    @DisplayName("a missing file is an I/O error naming it, not a format refusal")
    void missing() {
        Path file = directory.resolve("missing.mzML");
        NoSuchFileException thrown =
                assertThrows(NoSuchFileException.class, () -> support().check(file));
        assertEquals(file.toString(), thrown.getFile());
    }

    @Test
    @DisplayName("a path with no file name is refused as no spectrum file")
    void root() {
        Path root = directory.getRoot();
        assertEquals(
                "The spectrum file "
                        + root
                        + " cannot be visualised by PDV 2.7.0: its name "
                        + "ends in none of the extensions Comet reads "
                        + "(.mzML, .mzXML, .mgf, .ms2, .cms2, .bms2, "
                        + ".raw), so it is not a spectrum file CometGUI "
                        + "knows."
                        + OFFER,
                assertThrows(UnsupportedSpectrumFileException.class, () -> support().check(root))
                        .getMessage());
    }

    @Test
    @DisplayName("only the head of a file is read to confirm it")
    void readsTheHeadOnly() throws IOException {
        StringBuilder big = new StringBuilder("BEGIN IONS\n");
        while (big.length() < PdvSpectrumSupport.SNIFF_BYTES * 2) {
            big.append("100.0 1.0\n");
        }
        Path file = Files.writeString(directory.resolve("big.mgf"), big);
        assertEquals(SpectrumFileFormat.MGF, support().check(file));
    }

    @Test
    @DisplayName("the file is required")
    void nulls() {
        assertEquals(
                "spectrumFile",
                assertThrows(
                                NullPointerException.class,
                                () -> support().check(Nulls.of(Path.class)))
                        .getMessage());
    }
}
