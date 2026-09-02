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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.install.archive.ArchiveFixtures.Entry;
import org.cometgui.install.registry.ArtefactCompanion;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The extractor's own decisions: which mode a record selects, how a member name is matched, and
 * what happens when an artefact is not what the manifest says it is.
 *
 * <p>The mode tests are driven from <strong>the shipped {@code manifests/tools.json}</strong>
 * rather than from a fixture, and deliberately. A fixture contains what the rule needs; real data
 * contains what the world has -- and the world here contains a Percolator artefact whose only
 * member is named {@code ../my_build/...}, which no fixture author would have thought to write.
 */
class ArtefactExtractorTest {

    /** Unit 0's digest for the member of the traversing 3.06.5 macOS zip. */
    private static final String TRAVERSING_MEMBER_SHA256 =
            "f6c627105bc22f90e0ce495ae6d69a319d222b57f39b683279e3023cafe44c27";

    /** Unit 0's digest for {@code percolator_out.xsd}. */
    private static final String OUT_XSD_SHA256 =
            "21204c89234b3b255fc05009ac6b956195573fce79020863f472ab64fd986865";

    @TempDir private Path work;

    private Path archives;

    private final ArtefactExtractor extractor = new ArtefactExtractor();

    @BeforeEach
    void createTree() throws IOException {
        archives = Files.createDirectories(work.resolve("archives"));
    }

    private static ArtefactRecord shipped(String tool, String version, String platform)
            throws IOException {
        ArtefactManifest manifest = ArtefactManifestReader.readFromClasspath();
        return manifest.artefacts().stream()
                .filter(record -> tool.equals(record.tool().id()))
                .filter(record -> version.equals(record.version().text()))
                .filter(record -> platform.equals(record.platform().id()))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "the shipped manifest has no "
                                                + tool
                                                + " "
                                                + version
                                                + " "
                                                + platform
                                                + " row, so this test checks something the product"
                                                + " does not offer"));
    }

    @Test
    @DisplayName("a record with a member extracts that member to the manifest's own path")
    void aRecordWithAMemberUsesNamedMemberMode() throws IOException {
        ArtefactRecord record = shipped("percolator", "3.06.5", "macos-x86-64");
        Path destination = work.resolve("percolator");
        ExtractionReport report =
                extractor.extract(
                        record,
                        ArtefactMirror.artefact("rel-3-06-05__percolator-noxml-osx-portable.zip"),
                        destination);
        assertAll(
                () ->
                        assertEquals(
                                "../my_build/percolator-noxml/src/percolator",
                                record.member().orElseThrow().path(),
                                "the shipped manifest still names the traversing member, which is"
                                        + " what makes this test worth having"),
                () -> assertEquals(List.of(record.executablePath()), report.paths()),
                () ->
                        ArtefactMirror.assertContent(
                                destination.resolve(record.executablePath()),
                                1_471_048L,
                                TRAVERSING_MEMBER_SHA256));
    }

    @Test
    @DisplayName("a record with no member unpacks the whole artefact")
    void aRecordWithoutAMemberUnpacksEverything() throws IOException {
        ArtefactRecord record = shipped("pdv", "2.7.0", "linux-x86-64");
        Path destination = work.resolve("pdv");
        ExtractionReport report =
                extractor.extract(
                        record, ArtefactMirror.artefact("v2.7.0__PDV-2.7.0.zip"), destination);
        assertAll(
                () -> assertEquals(222, report.entriesRead()),
                () -> assertTrue(report.paths().contains(record.executablePath())),
                () ->
                        assertTrue(
                                Files.isRegularFile(destination.resolve(record.executablePath()))));
    }

    @Test
    @DisplayName("a companion's members come out of its payload, at the manifest's paths")
    void aCompanionExtractsItsMembers() throws IOException {
        ArtefactRecord record = shipped("percolator", "3.07.1", "linux-x86-64");
        ArtefactCompanion companion = record.companions().get(0);
        Path destination = work.resolve("percolator-companion");
        ExtractionReport report =
                extractor.extract(
                        companion,
                        ArtefactMirror.artefact(
                                "rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb"),
                        destination);
        assertAll(
                () -> assertEquals(ArtefactKind.DEB_PAYLOAD, companion.kind()),
                /*
                 * Sorted, because the report lists what was created in the order the ARCHIVE
                 * yielded it and the manifest lists the members in its own order.  The payload tar
                 * happens to carry percolator_in.xsd first.  Asserting the manifest's order here
                 * would be asserting a coincidence.
                 */
                () ->
                        assertEquals(
                                companion.installedPaths().stream().sorted().toList(),
                                report.paths().stream().sorted().toList()),
                () ->
                        ArtefactMirror.assertContent(
                                destination.resolve(companion.installedPaths().get(0)),
                                10_388L,
                                OUT_XSD_SHA256));
    }

    @Test
    @DisplayName("a \".\" segment is dropped when a member is matched and a \"..\" segment is not")
    void memberNamesAreMatchedAsTextAndNeverAsPaths() {
        assertAll(
                () ->
                        assertEquals(
                                "usr/share/x.xsd", ArtefactExtractor.matchKey("./usr/share/x.xsd")),
                () ->
                        assertEquals(
                                "usr/share/x.xsd", ArtefactExtractor.matchKey("usr/share/x.xsd")),
                () -> assertEquals("usr/share", ArtefactExtractor.matchKey("./usr/./share/")),
                () ->
                        assertEquals(
                                "../my_build/percolator-noxml/src/percolator",
                                ArtefactExtractor.matchKey(
                                        "../my_build/percolator-noxml/src/percolator"),
                                "trimming whatever an archive begins with would also trim \"../\","
                                        + " and a real upstream artefact begins exactly that way"),
                () -> assertEquals("..", ArtefactExtractor.matchKey("..")),
                () -> assertEquals("", ArtefactExtractor.matchKey("./")));
    }

    @Test
    @DisplayName("a member the artefact does not hold is refused, naming it and the entries seen")
    void aMissingMemberIsRefused() throws IOException {
        Path artefact =
                ArchiveFixtures.build(
                        ArtefactKind.TAR_GZ,
                        archives,
                        "two.tar.gz",
                        List.of(Entry.file("a.txt", "a"), Entry.file("b.txt", "b")));
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () ->
                                extractor.extractNamedMembers(
                                        ArtefactKind.TAR_GZ,
                                        artefact,
                                        work.resolve("dest"),
                                        List.of(new RequestedMember("c.txt", "bin/c"))));
        assertAll(
                () -> assertEquals(RejectionReason.MEMBER_NOT_FOUND, rejection.reason()),
                () ->
                        assertEquals(
                                "the artefact \"two.tar.gz\" was rejected because the artefact does"
                                        + " not contain it -- the manifest names the member"
                                        + " \"c.txt\" and the artefact's 2 entries do not include"
                                        + " it",
                                rejection.getMessage()));
    }

    @Test
    @DisplayName("an artefact that does not produce the expected executable is refused")
    void aMissingExpectedExecutableIsRefused() throws IOException {
        Path artefact =
                ArchiveFixtures.build(
                        ArtefactKind.ZIP,
                        archives,
                        "wrong.zip",
                        List.of(Entry.file("readme.txt", "nothing to run")));
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () ->
                                extractor.extractWholeArtefact(
                                        ArtefactKind.ZIP,
                                        artefact,
                                        work.resolve("dest"),
                                        "bin/tool"));
        assertAll(
                () -> assertEquals(RejectionReason.EXPECTED_FILE_MISSING, rejection.reason()),
                () ->
                        assertEquals(
                                "the artefact \"wrong.zip\" was rejected because unpacking the"
                                        + " whole artefact did not produce it, so the installed"
                                        + " tool would have no executable to run -- the manifest"
                                        + " expects \"bin/tool\" and the artefact produced 1"
                                        + " path(s), none of them that one",
                                rejection.getMessage()));
    }

    @Test
    @DisplayName("named-member extraction of nothing is a programming error, not a silent no-op")
    void namedMemberExtractionNeedsAMember() throws IOException {
        Path artefact = Files.write(archives.resolve("x.bin"), new byte[] {1});
        IllegalArgumentException rejection =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                extractor.extractNamedMembers(
                                        ArtefactKind.ZIP, artefact, work.resolve("d"), List.of()));
        assertEquals(
                "named-member extraction needs at least one member: an extraction that takes"
                        + " nothing out of an artefact is a download with no reason to happen",
                rejection.getMessage());
    }

    @Test
    @DisplayName("a single-file artefact cannot be asked for two members")
    void aSingleFileKindTakesOneMember() throws IOException {
        Path artefact = Files.write(archives.resolve("x.bin"), new byte[] {1});
        IllegalArgumentException rejection =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                extractor.extractNamedMembers(
                                        ArtefactKind.BARE_EXECUTABLE,
                                        artefact,
                                        work.resolve("d"),
                                        List.of(
                                                new RequestedMember("a", "bin/a"),
                                                new RequestedMember("b", "bin/b"))));
        assertEquals(
                "a BARE_EXECUTABLE artefact is one file and has no members to choose between, so"
                        + " exactly one may be named, but 2 were",
                rejection.getMessage());
    }

    @Test
    @DisplayName("a bare executable named as a member is copied to the manifest's path")
    void aBareExecutableCompanionIsCopied() throws IOException {
        Path artefact =
                Files.write(
                        archives.resolve("CometWrapper.dll"),
                        "not really a dll".getBytes(StandardCharsets.UTF_8));
        Path destination = work.resolve("comet");
        ExtractionReport report =
                extractor.extractNamedMembers(
                        ArtefactKind.BARE_EXECUTABLE,
                        artefact,
                        destination,
                        List.of(new RequestedMember("CometWrapper.dll", "bin/CometWrapper.dll")));
        assertAll(
                () -> assertEquals(List.of("bin/CometWrapper.dll"), report.paths()),
                () -> assertEquals(1, report.entriesRead()),
                () -> assertEquals(16L, report.expandedBytes()),
                () ->
                        assertEquals(
                                "not really a dll",
                                Files.readString(destination.resolve("bin/CometWrapper.dll"))));
    }

    @Test
    @DisplayName("the extractor rejects null arguments rather than failing later")
    void nullsAreRejected() throws IOException {
        Path artefact = Files.write(archives.resolve("x.bin"), new byte[] {1});
        Path destination = work.resolve("d");
        assertAll(
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () -> new ArtefactExtractor(Nulls.of(ExtractionLimits.class))),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        extractor.extractNamedMembers(
                                                Nulls.of(ArtefactKind.class),
                                                artefact,
                                                destination,
                                                List.of(new RequestedMember("a", "b")))),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        extractor.extractNamedMembers(
                                                ArtefactKind.ZIP,
                                                Nulls.of(Path.class),
                                                destination,
                                                List.of(new RequestedMember("a", "b")))),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        extractor.extractNamedMembers(
                                                ArtefactKind.ZIP,
                                                artefact,
                                                Nulls.of(Path.class),
                                                List.of(new RequestedMember("a", "b")))),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        extractor.extractNamedMembers(
                                                ArtefactKind.ZIP,
                                                artefact,
                                                destination,
                                                Nulls.of(List.class))),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        extractor.extractWholeArtefact(
                                                Nulls.of(ArtefactKind.class),
                                                artefact,
                                                destination,
                                                "bin/x")),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        extractor.extractWholeArtefact(
                                                ArtefactKind.ZIP,
                                                Nulls.of(Path.class),
                                                destination,
                                                "bin/x")),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        extractor.extractWholeArtefact(
                                                ArtefactKind.ZIP,
                                                artefact,
                                                Nulls.of(Path.class),
                                                "bin/x")),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        extractor.extractWholeArtefact(
                                                ArtefactKind.ZIP,
                                                artefact,
                                                destination,
                                                Nulls.of(String.class))),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        extractor.extract(
                                                Nulls.of(ArtefactRecord.class),
                                                artefact,
                                                destination)),
                () ->
                        assertThrows(
                                NullPointerException.class,
                                () ->
                                        extractor.extract(
                                                Nulls.of(ArtefactCompanion.class),
                                                artefact,
                                                destination)));
    }

    @Test
    @DisplayName("a member the manifest names that turns out to be a directory is refused")
    void aMemberThatIsNotAFileIsRefused() throws IOException {
        Path artefact =
                ArchiveFixtures.build(
                        ArtefactKind.TAR_GZ,
                        archives,
                        "dir.tar.gz",
                        List.of(Entry.directory("opt"), Entry.file("opt/tool.bin", "payload")));
        ExtractionRejectedException rejection =
                assertThrows(
                        ExtractionRejectedException.class,
                        () ->
                                extractor.extractNamedMembers(
                                        ArtefactKind.TAR_GZ,
                                        artefact,
                                        work.resolve("dest"),
                                        List.of(new RequestedMember("opt", "bin/opt"))));
        assertAll(
                () -> assertEquals(RejectionReason.UNSUPPORTED_ENTRY_TYPE, rejection.reason()),
                () ->
                        assertEquals(
                                "the archive entry \"opt/\" was rejected because it is neither a"
                                        + " regular file, a directory nor a symbolic link, and this"
                                        + " extractor creates nothing else -- the manifest names it"
                                        + " as a member to install and the artefact holds it as a"
                                        + " DIRECTORY",
                                rejection.getMessage()));
    }

    @Test
    @DisplayName("the default extractor runs with the shipped limits")
    void theDefaultExtractorUsesTheShippedLimits() {
        assertEquals(ExtractionLimits.defaults(), new ArtefactExtractor().limits());
    }
}
