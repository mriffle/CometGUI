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

package org.cometgui.install.archive;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.cometgui.domain.tools.ArtefactKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Extracts the real artefacts this product installs and checks the bytes that came out.
 *
 * <p>Every digest below is unit 0's, hand-typed from the survey and never computed by the code
 * under test. That is the difference between proving an extraction and watching it exit zero: an
 * expected value the extractor produced cannot fail.
 *
 * <p>The pair in {@link #theTraversingUpstreamZipInstallsAndIsRejected()} is the phase's own
 * standing direction made checkable. {@code rel-3-06-05/percolator-noxml-osx-portable.zip} is a
 * genuine upstream artefact whose single member is named {@code
 * ../my_build/percolator-noxml/src/percolator}. It must install -- because the manifest names the
 * destination and the archive's name never places a file -- <strong>and</strong> the same entry
 * must be rejected when the same archive is unpacked whole. Both are here, in one test, so that
 * neither can be quietly lost.
 */
class RealArtefactExtractionTest {

    /** Unit 0's digest for {@code percolator_out.xsd}, identical in all four payloads. */
    private static final String OUT_XSD_SHA256 =
            "21204c89234b3b255fc05009ac6b956195573fce79020863f472ab64fd986865";

    /** Unit 0's digest for {@code percolator_in.xsd}, identical in all four payloads. */
    private static final String IN_XSD_SHA256 =
            "fa50a550ea01c9109197ad2c8c9efdcdad448fddd81c5ddcf54f13f8af280f4f";

    /** The shipped manifest's digest for the Limelight converter JAR. */
    private static final String CONVERTER_JAR_SHA256 =
            "843573396ce0654a0ac81582b378c496923e49dde71f40d750d890947774ece1";

    /** Unit 0's digest for PDV 2.7.0's launcher jar. */
    private static final String PDV_LAUNCHER_SHA256 =
            "fc876310b6f9dd95bd9538f212e28d45ac0226ea1615a9c09f2e904d70d07908";

    /** Where {@code percolator_out.xsd} is installed, in both the Debian and the macOS case. */
    private static final String OUT_XSD_INSTALLED =
            "share/xml/percolator/xml-pout-1-5/percolator_out.xsd";

    /** Where {@code percolator_in.xsd} is installed, in both cases. */
    private static final String IN_XSD_INSTALLED =
            "share/xml/percolator/xml-pin-1-3/percolator_in.xsd";

    @TempDir private Path work;

    private final ArtefactExtractor extractor = new ArtefactExtractor();

    @Test
    @DisplayName("the Linux portable zip yields the binary unit 0 pinned")
    void linuxPortableZip() throws IOException {
        Path destination = work.resolve("percolator-linux");
        ExtractionReport report =
                extractor.extractNamedMembers(
                        ArtefactKind.ZIP,
                        ArtefactMirror.artefact(
                                "rel-3-07-01__percolator-noxml-ubuntu-portable.zip"),
                        destination,
                        List.of(new RequestedMember("percolator", "bin/percolator")));
        assertEquals(List.of("bin/percolator"), report.paths());
        ArtefactMirror.assertContent(
                destination.resolve("bin/percolator"),
                2_538_632L,
                "1ba38acf09520cc89d5ed907ed0382c4d23876a7e20ec3e91cbbaa2ed431237c");
    }

    @Test
    @DisplayName("the deep-path macOS zip yields the binary unit 0 pinned")
    void deepPathMacosZip() throws IOException {
        Path destination = work.resolve("percolator-macos");
        extractor.extractNamedMembers(
                ArtefactKind.ZIP,
                ArtefactMirror.artefact("rel-3-07-01__percolator-noxml-osx-portable.zip"),
                destination,
                List.of(
                        new RequestedMember(
                                "Users/runner/work/percolator/percolator/build/percolator-noxml/"
                                        + "src/percolator",
                                "bin/percolator")));
        ArtefactMirror.assertContent(
                destination.resolve("bin/percolator"),
                1_368_048L,
                "a071eaba560980212750e37a3e4e275875278191d35a87664b9061cdd550c306");
    }

    @Test
    @DisplayName(
            "the 3.06.5 macOS zip, whose only member traverses, installs by manifest name and is"
                    + " rejected by archive name")
    void theTraversingUpstreamZipInstallsAndIsRejected() throws IOException {
        Path artefact = ArtefactMirror.artefact("rel-3-06-05__percolator-noxml-osx-portable.zip");
        Path installed = work.resolve("named-member");
        extractor.extractNamedMembers(
                ArtefactKind.ZIP,
                artefact,
                installed,
                List.of(
                        new RequestedMember(
                                "../my_build/percolator-noxml/src/percolator", "bin/percolator")));
        ArtefactMirror.assertContent(
                installed.resolve("bin/percolator"),
                1_471_048L,
                "f6c627105bc22f90e0ce495ae6d69a319d222b57f39b683279e3023cafe44c27");

        Path whole = work.resolve("whole-artefact");
        Files.createDirectories(whole);
        List<String> before = DestinationSnapshot.of(work);
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () ->
                                extractor.extractWholeArtefact(
                                        ArtefactKind.ZIP, artefact, whole, "percolator"));
        assertAll(
                () ->
                        assertEquals(
                                RejectionReason.ENTRY_NAME_TRAVERSES,
                                rejection.reason(),
                                "the guard must reject the real traversing entry, and the"
                                        + " manifest-names-the-member design must not be the reason"
                                        + " it is never exercised"),
                () ->
                        assertEquals(
                                "the archive entry \"../my_build/percolator-noxml/src/percolator\""
                                        + " was rejected because its name has a \"..\" segment,"
                                        + " which would place it outside the destination directory",
                                rejection.getMessage()),
                () -> DestinationSnapshot.assertUnchanged(work, before, "unpacking it whole"),
                () -> assertEquals(List.of(), DestinationSnapshot.of(whole)));
    }

    @Test
    @DisplayName("the Debian payload yields both XSD companions, byte for byte")
    void debPayloadXsdPair() throws IOException {
        Path destination = work.resolve("percolator-linux-xsd");
        ExtractionReport report =
                extractor.extractNamedMembers(
                        ArtefactKind.DEB_PAYLOAD,
                        ArtefactMirror.artefact(
                                "rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb"),
                        destination,
                        List.of(
                                new RequestedMember(
                                        "usr/share/xml/percolator/xml-pout-1-5/percolator_out.xsd",
                                        "share/xml/percolator/xml-pout-1-5/percolator_out.xsd"),
                                new RequestedMember(
                                        "usr/share/xml/percolator/xml-pin-1-3/percolator_in.xsd",
                                        "share/xml/percolator/xml-pin-1-3/percolator_in.xsd")));
        assertAll(
                () ->
                        assertEquals(
                                12, report.entriesRead(), "the payload tar holds twelve entries"),
                () ->
                        ArtefactMirror.assertContent(
                                destination.resolve(
                                        "share/xml/percolator/xml-pout-1-5/percolator_out.xsd"),
                                10_388L,
                                OUT_XSD_SHA256),
                () ->
                        ArtefactMirror.assertContent(
                                destination.resolve(
                                        "share/xml/percolator/xml-pin-1-3/percolator_in.xsd"),
                                15_457L,
                                IN_XSD_SHA256));
    }

    @Test
    @DisplayName("the macOS package payload yields the same two XSDs from a different prefix")
    void pkgPayloadXsdPair() throws IOException {
        Path destination = work.resolve("percolator-macos-xsd");
        ExtractionReport report =
                extractor.extractNamedMembers(
                        ArtefactKind.PKG_PAYLOAD,
                        ArtefactMirror.artefact(
                                "rel-3-07-01__percolator-noxml-v3-07-osx-x86_64.pkg"),
                        destination,
                        List.of(
                                new RequestedMember(
                                        "usr/local/share/xml/percolator/xml-pout-1-5/"
                                                + "percolator_out.xsd",
                                        "share/xml/percolator/xml-pout-1-5/percolator_out.xsd"),
                                new RequestedMember(
                                        "usr/local/share/xml/percolator/xml-pin-1-3/"
                                                + "percolator_in.xsd",
                                        "share/xml/percolator/xml-pin-1-3/percolator_in.xsd")));
        assertAll(
                () ->
                        assertEquals(
                                14,
                                report.entriesRead(),
                                "the payload cpio holds fourteen entries"),
                () ->
                        ArtefactMirror.assertContent(
                                destination.resolve(
                                        "share/xml/percolator/xml-pout-1-5/percolator_out.xsd"),
                                10_388L,
                                OUT_XSD_SHA256),
                () ->
                        ArtefactMirror.assertContent(
                                destination.resolve(
                                        "share/xml/percolator/xml-pin-1-3/percolator_in.xsd"),
                                15_457L,
                                IN_XSD_SHA256));
    }

    @Test
    @DisplayName("the converter JAR is installed as one file, not unpacked")
    void converterJar() throws IOException {
        Path destination = work.resolve("converter");
        ExtractionReport report =
                extractor.extractWholeArtefact(
                        ArtefactKind.JAR,
                        ArtefactMirror.artefact("v2.8.1__cometPercolator2LimelightXML.jar"),
                        destination,
                        "cometPercolator2LimelightXML.jar");
        assertAll(
                () -> assertEquals(List.of("cometPercolator2LimelightXML.jar"), report.paths()),
                () ->
                        ArtefactMirror.assertContent(
                                destination.resolve("cometPercolator2LimelightXML.jar"),
                                2_762_075L,
                                CONVERTER_JAR_SHA256));
    }

    @Test
    @DisplayName("the Comet bare executable is copied and verified, not extracted")
    void cometBareExecutable() throws IOException {
        Path destination = work.resolve("comet");
        extractor.extractWholeArtefact(
                ArtefactKind.BARE_EXECUTABLE,
                ArtefactMirror.artefact("v2026.02.2__comet.linux.exe"),
                destination,
                "bin/comet");
        ArtefactMirror.assertContent(
                destination.resolve("bin/comet"),
                7_014_400L,
                "af515b6ed5a17efafff7277a6a9c73cee97e26d38f3c9b2a8da16adaa44e6d9e");
    }

    @Test
    @DisplayName("PDV -- 222 entries, 115 MB, ratio 1.11 -- is not rejected by the bomb guard")
    void pdvIsNotRejected() throws IOException {
        Path artefact = ArtefactMirror.artefact("v2.7.0__PDV-2.7.0.zip");
        Path destination = work.resolve("pdv");
        ExtractionReport report =
                extractor.extractWholeArtefact(
                        ArtefactKind.ZIP, artefact, destination, "PDV-2.7.0/PDV-2.7.0.jar");
        assertAll(
                () -> assertEquals(222, report.entriesRead()),
                () -> assertEquals(115_057_606L, report.expandedBytes()),
                () -> assertEquals(103_407_417L, report.artefactBytes()),
                () ->
                        assertTrue(
                                report.paths().contains("PDV-2.7.0/PDV-2.7.0.jar"),
                                "the launcher must be where the manifest expects it"),
                () ->
                        ArtefactMirror.assertContent(
                                destination.resolve("PDV-2.7.0/PDV-2.7.0.jar"),
                                1_343_276L,
                                PDV_LAUNCHER_SHA256));
    }
}
