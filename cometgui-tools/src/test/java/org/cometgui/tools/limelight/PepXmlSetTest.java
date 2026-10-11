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

package org.cometgui.tools.limelight;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.tools.limelight.LimelightConversionException.Failure;
import org.cometgui.tools.limelight.LimelightFixtures.Psm;
import org.cometgui.tools.limelight.LimelightFixtures.SmallRun;
import org.cometgui.tools.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The pepXML set: the converter's name rule from PSM ids, and the proof that every pepXML it will
 * read is one of the run's recorded Comet outputs.
 */
class PepXmlSetTest {

    @Test
    @DisplayName("the name is the PSM id less its last three numeric fields, plus .pep.xml")
    void theNameRule() {
        assertAll(
                () ->
                        assertEquals(
                                "20100614_Velos1_TaGe_SA_K562_3.pep.xml",
                                PepXmlSet.expectedFileName(
                                        "20100614_Velos1_TaGe_SA_K562_3_11367_2_1")),
                () -> assertEquals("run_10.pep.xml", PepXmlSet.expectedFileName("run_10_1_2_3")),
                () -> assertEquals("a_1_2.pep.xml", PepXmlSet.expectedFileName("a_1_2_3_4_5")),
                () -> assertEquals("x_.pep.xml", PepXmlSet.expectedFileName("x__1_2_3")),
                () -> assertEquals("a.b c.pep.xml", PepXmlSet.expectedFileName("a.b c_1_2_3")),
                () -> assertEquals("..pep.xml", PepXmlSet.expectedFileName("._1_2_3")),
                () ->
                        assertEquals(
                                "f.pep.xml",
                                PepXmlSet.expectedFileName("f_12345678901234567890_99_100")));
    }

    @Test
    @DisplayName("an id without three trailing numeric fields gives no name, as in the converter")
    void underivableIds() {
        for (String id :
                List.of(
                        "a_1_2",
                        "_1_2_3",
                        "a_1_2_3x",
                        "a_1_2_",
                        "a_1__3",
                        "a-1-2-3",
                        "a_1_2_٣",
                        "",
                        "a_1_2_3\n")) {
            assertEquals(
                    "the PSM id \""
                            + id
                            + "\" is not <file>_<scan>_<charge>_<rank>, so the converter could not"
                            + " derive a pepXML file name from it",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> PepXmlSet.expectedFileName(id))
                            .getMessage(),
                    id);
        }
    }

    @Test
    @DisplayName("a file part holding a path separator is refused: it would leave the directory")
    void aPathIsNotAName() {
        for (String id :
                List.of(
                        "../../etc/x_1_2_3",
                        "a/b_1_2_3",
                        "a\\b_1_2_3",
                        "a\0b_1_2_3",
                        "/x_1_2_3",
                        "\\x_1_2_3",
                        "\0x_1_2_3")) {
            assertTrue(
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> PepXmlSet.expectedFileName(id))
                            .getMessage()
                            .endsWith(
                                    "\", which is a path rather than a file name in the pepXML"
                                            + " directory"),
                    id);
        }
    }

    @Test
    @DisplayName("a run whose pepXML is recorded and unchanged is verified, with its checksums")
    void theRunsOwnFilesAreVerified(@TempDir Path directory) throws IOException {
        SmallRun run =
                SmallRun.in(
                        directory,
                        List.of(
                                Psm.target("b_run_2_1_2_1"),
                                Psm.target("a_run_1_5_3_1"),
                                Psm.target("b_run_2_9_2_1")));
        AtomicInteger hashed = new AtomicInteger();
        HashService counting =
                file -> {
                    hashed.incrementAndGet();
                    return LimelightFixtures.hashes().hash(file);
                };

        PepXmlSet set =
                PepXmlSet.verify(run.read(), run.pepXmlDirectory(), run.recorded(), counting);

        Path a = run.pepXmlDirectory().resolve("a_run_1.pep.xml");
        Path b = run.pepXmlDirectory().resolve("b_run_2.pep.xml");
        assertAll(
                () ->
                        assertEquals(
                                List.of("a_run_1.pep.xml", "b_run_2.pep.xml"),
                                List.copyOf(set.files().keySet())),
                () -> assertEquals(List.of(a, b), set.paths()),
                () -> assertEquals(run.pepXmlDirectory(), set.directory()),
                () -> assertEquals(run.pout(), set.percolatorXml()),
                () ->
                        assertEquals(
                                new FileHashes(
                                        LimelightFixtures.digest(a, "MD5"),
                                        LimelightFixtures.digest(a, "SHA-256")),
                                set.files().get("a_run_1.pep.xml")),
                () -> assertEquals(2, hashed.get(), "each pepXML is hashed once"));
    }

    @Test
    @DisplayName("a recorded checksum is compared without regard to case")
    void recordedChecksumsAreCaseInsensitive(@TempDir Path directory) throws IOException {
        SmallRun run = SmallRun.in(directory);
        Map<String, String> upper = new HashMap<>();
        run.recorded().forEach((name, sha) -> upper.put(name, sha.toUpperCase(Locale.ROOT)));

        assertEquals(
                1,
                PepXmlSet.verify(
                                run.read(),
                                run.pepXmlDirectory(),
                                upper,
                                LimelightFixtures.hashes())
                        .files()
                        .size());
    }

    @Test
    @DisplayName("a pepXML the converter will look for and that is not there is refused by name")
    void aMissingPepXmlIsRefused(@TempDir Path directory) throws IOException {
        SmallRun run = SmallRun.in(directory);
        Path pepXml = run.pepXmlDirectory().resolve("run_10_7.pep.xml");
        Files.delete(pepXml);

        LimelightConversionException refused =
                assertThrows(LimelightConversionException.class, run::verified);

        assertAll(
                () -> assertEquals(Failure.PEPXML_MISSING, refused.failure()),
                () -> assertEquals(Optional.of(pepXml), refused.file()),
                () ->
                        assertEquals(
                                "the Limelight converter will read "
                                        + pepXml
                                        + ", derived from the Percolator PSM ids, and there is no"
                                        + " such file",
                                refused.getMessage()));
    }

    @Test
    @DisplayName("a pepXML that is not one of the run's recorded outputs is refused by name")
    void anUnrecordedPepXmlIsRefused(@TempDir Path directory) throws IOException {
        SmallRun run = SmallRun.in(directory);
        Path pepXml = run.pepXmlDirectory().resolve("run_10_7.pep.xml");
        Map<String, String> other =
                Map.of("z.pep.xml", run.recorded().get("run_10_7.pep.xml"), "y.pep.xml", "00");

        LimelightConversionException refused =
                assertThrows(
                        LimelightConversionException.class,
                        () ->
                                PepXmlSet.verify(
                                        run.read(),
                                        run.pepXmlDirectory(),
                                        other,
                                        LimelightFixtures.hashes()));

        assertAll(
                () -> assertEquals(Failure.PEPXML_NOT_OF_THIS_RUN, refused.failure()),
                () -> assertEquals(Optional.of(pepXml), refused.file()),
                () ->
                        assertEquals(
                                "the Limelight converter will read "
                                        + pepXml
                                        + ", and it is not one of this run's recorded Comet"
                                        + " outputs (y.pep.xml, z.pep.xml)",
                                refused.getMessage()));
    }

    @Test
    @DisplayName("a pepXML changed since the run recorded it is refused, both checksums named")
    void aChangedPepXmlIsRefused(@TempDir Path directory) throws IOException {
        SmallRun run = SmallRun.in(directory);
        Path pepXml = run.pepXmlDirectory().resolve("run_10_7.pep.xml");
        String recorded = run.recorded().get("run_10_7.pep.xml");
        Files.writeString(
                pepXml, "<msms_pipeline_analysis>another search</msms_pipeline_analysis>\n");
        String now = LimelightFixtures.digest(pepXml, "SHA-256");

        LimelightConversionException refused =
                assertThrows(LimelightConversionException.class, run::verified);

        assertAll(
                () -> assertEquals(Failure.PEPXML_CHECKSUM_MISMATCH, refused.failure()),
                () -> assertEquals(Optional.of(pepXml), refused.file()),
                () ->
                        assertEquals(
                                "the pepXML "
                                        + pepXml
                                        + " has SHA-256 "
                                        + now
                                        + ", not the "
                                        + recorded
                                        + " this run recorded when Comet wrote it, so it is not"
                                        + " this run's output any more",
                                refused.getMessage()));
    }

    @Test
    @DisplayName("every derived pepXML is checked, not only the first")
    void everyPepXmlIsChecked(@TempDir Path directory) throws IOException {
        SmallRun run =
                SmallRun.in(directory, List.of(Psm.target("a_1_2_1"), Psm.target("b_1_2_1")));
        Path second = run.pepXmlDirectory().resolve("b.pep.xml");
        Files.writeString(second, "changed");

        LimelightConversionException refused =
                assertThrows(LimelightConversionException.class, run::verified);

        assertAll(
                () -> assertEquals(Failure.PEPXML_CHECKSUM_MISMATCH, refused.failure()),
                () -> assertEquals(Optional.of(second), refused.file()));
    }

    @Test
    @DisplayName("not Percolator XML, no PSM, and an underivable id are each refused by name")
    void theSourceIsChecked(@TempDir Path directory) throws IOException {
        Path other = Files.writeString(directory.resolve("other.xml"), "<other/>");
        Path empty = LimelightFixtures.pout(directory.resolve("empty.pout.xml"), List.of());
        Path odd =
                LimelightFixtures.pout(
                        directory.resolve("odd.pout.xml"),
                        List.of(Psm.target("a_1_2_1"), Psm.target("scan7")));

        LimelightConversionException notPercolator =
                assertThrows(LimelightConversionException.class, () -> verify(other, directory));
        LimelightConversionException noPsms =
                assertThrows(LimelightConversionException.class, () -> verify(empty, directory));
        LimelightConversionException underivable =
                assertThrows(LimelightConversionException.class, () -> verify(odd, directory));

        assertAll(
                () -> assertEquals(Failure.NOT_PERCOLATOR_XML, notPercolator.failure()),
                () -> assertEquals(Optional.of(other), notPercolator.file()),
                () ->
                        assertEquals(
                                other
                                        + " is not Percolator XML, so the Limelight"
                                        + " converter cannot read it",
                                notPercolator.getMessage()),
                () -> assertEquals(Failure.NO_PSMS, noPsms.failure()),
                () ->
                        assertEquals(
                                "the Percolator XML "
                                        + empty
                                        + " holds no PSM, so there is nothing to convert and no"
                                        + " pepXML the converter would read",
                                noPsms.getMessage()),
                () -> assertEquals(Failure.PSM_ID_UNDERIVABLE, underivable.failure()),
                () -> assertEquals(Optional.of(odd), underivable.file()),
                () ->
                        assertEquals(
                                "in "
                                        + odd
                                        + ": the PSM id \"scan7\" is not"
                                        + " <file>_<scan>_<charge>_<rank>, so the converter could"
                                        + " not derive a pepXML file name from it",
                                underivable.getMessage()));
    }

    private static PepXmlSet verify(Path pout, Path directory) throws IOException {
        return PepXmlSet.verify(
                PercolatorXmlPsms.read(pout), directory, Map.of(), LimelightFixtures.hashes());
    }

    @Test
    @DisplayName("the set's map is copied, kept in order and unmodifiable; an empty set is refused")
    void theRecord(@TempDir Path directory) {
        FileHashes hashes = new FileHashes("0".repeat(32), "0".repeat(64));
        Map<String, FileHashes> files = new LinkedHashMap<>();
        files.put("b.pep.xml", hashes);
        files.put("a.pep.xml", hashes);
        PepXmlSet set = new PepXmlSet(directory.resolve("p.xml"), directory, files);
        files.clear();

        assertAll(
                () ->
                        assertEquals(
                                List.of("b.pep.xml", "a.pep.xml"),
                                new ArrayList<>(set.files().keySet())),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> set.files().put("c.pep.xml", hashes)),
                () ->
                        assertEquals(
                                "a conversion reads at least one pepXML file",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () -> new PepXmlSet(directory, directory, Map.of()))
                                        .getMessage()));
    }

    @Test
    @DisplayName("every argument is required")
    void nulls(@TempDir Path directory) throws IOException {
        SmallRun run = SmallRun.in(directory);
        PercolatorXmlPsms read = run.read();
        HashService hashes = LimelightFixtures.hashes();
        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("run_10_7.pep.xml", null);
        Map<String, String> nullKey = new HashMap<>();
        nullKey.put(null, "00");
        Path dir = run.pepXmlDirectory();

        assertAll(
                () ->
                        assertNpe(
                                "percolatorXml",
                                () ->
                                        PepXmlSet.verify(
                                                Nulls.of(PercolatorXmlPsms.class),
                                                dir,
                                                Map.of(),
                                                hashes)),
                () ->
                        assertNpe(
                                "directory",
                                () ->
                                        PepXmlSet.verify(
                                                read, Nulls.of(Path.class), Map.of(), hashes)),
                () ->
                        assertNpe(
                                "recordedSha256",
                                () -> PepXmlSet.verify(read, dir, Nulls.of(Map.class), hashes)),
                () ->
                        assertNpe(
                                "hashes",
                                () ->
                                        PepXmlSet.verify(
                                                read, dir, Map.of(), Nulls.of(HashService.class))),
                () ->
                        assertNpe(
                                "recorded SHA-256",
                                () -> PepXmlSet.verify(read, dir, nullValue, hashes)),
                () ->
                        assertNpe(
                                "recorded file name",
                                () -> PepXmlSet.verify(read, dir, nullKey, hashes)),
                () -> assertNpe("psmId", () -> PepXmlSet.expectedFileName(Nulls.of(String.class))),
                () ->
                        assertNpe(
                                "percolatorXml",
                                () -> new PepXmlSet(Nulls.of(Path.class), dir, Map.of())),
                () ->
                        assertNpe(
                                "directory",
                                () -> new PepXmlSet(dir, Nulls.of(Path.class), Map.of())),
                () -> assertNpe("files", () -> new PepXmlSet(dir, dir, Nulls.of(Map.class))));
    }

    private static void assertNpe(String message, org.junit.jupiter.api.function.Executable call) {
        assertEquals(message, assertThrows(NullPointerException.class, call).getMessage());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("REAL: the K562 Percolator XML names exactly the two pepXML files Comet wrote")
    void realRun() throws IOException {
        Map<String, String> anotherSearch =
                Map.of(
                        LimelightFixtures.PEPXML_3, LimelightFixtures.CONCAT_PEPXML_3_SHA256,
                        LimelightFixtures.PEPXML_4, LimelightFixtures.PEPXML_4_SHA256);
        Path pout = LimelightFixtures.real(LimelightFixtures.POUT, LimelightFixtures.POUT_SHA256);
        Path comet = LimelightFixtures.realDirectory(LimelightFixtures.COMET_DIRECTORY);
        Map<String, String> recorded =
                Map.of(
                        LimelightFixtures.PEPXML_3, LimelightFixtures.PEPXML_3_SHA256,
                        LimelightFixtures.PEPXML_4, LimelightFixtures.PEPXML_4_SHA256);

        PepXmlSet set =
                PepXmlSet.verify(
                        PercolatorXmlPsms.read(pout), comet, recorded, LimelightFixtures.hashes());

        assertAll(
                () ->
                        assertEquals(
                                List.of(LimelightFixtures.PEPXML_3, LimelightFixtures.PEPXML_4),
                                List.copyOf(set.files().keySet())),
                () ->
                        assertEquals(
                                LimelightFixtures.PEPXML_4_SHA256,
                                set.files().get(LimelightFixtures.PEPXML_4).sha256()),
                () ->
                        assertEquals(
                                Failure.PEPXML_CHECKSUM_MISMATCH,
                                assertThrows(
                                                LimelightConversionException.class,
                                                () ->
                                                        PepXmlSet.verify(
                                                                PercolatorXmlPsms.read(pout),
                                                                comet,
                                                                anotherSearch,
                                                                LimelightFixtures.hashes()))
                                        .failure(),
                                "the decoy-chain search's _3 pepXML has the same name and is"
                                        + " another search's output"));
    }
}
