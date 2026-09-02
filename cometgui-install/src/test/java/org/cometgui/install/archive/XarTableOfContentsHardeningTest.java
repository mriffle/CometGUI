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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.cometgui.domain.tools.ArtefactKind;
import org.cometgui.install.archive.ArchiveFixtures.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The one parser in this phase that reads hostile XML, and the five settings that make it safe.
 *
 * <h2>Why this test exists</h2>
 *
 * <p>A macOS flat package carries its table of contents as a zlib-compressed XML document. That
 * document arrives over the network inside a downloaded {@code .pkg} and is attacker-controlled by
 * construction: an external entity in it would reach the network or the local file system from
 * inside an extraction, and a nested entity would expand until the heap is gone.
 *
 * <p>The protections were written with the reader and were <strong>never exercised</strong>. When
 * the extraction package was first put under mutation testing, all five of them could be deleted
 * and every test still passed -- a rule present, believed and unproven, on exactly the input class
 * it exists for. This test is the repair, and it works two ways because neither alone is enough.
 *
 * <dl>
 *   <dt>By configuration
 *   <dd>{@link #everySettingIsForcedFromItsUnsafeValue()} hands {@link PkgPayloadReader#harden} a
 *       factory deliberately set to every unsafe value and requires all five to come back safe. Two
 *       of the five happen to match the JDK's defaults, so a test that merely inspected a fresh
 *       factory would pass with those calls deleted; starting from the unsafe state is what makes
 *       each of the five load-bearing.
 *   <dt>By behaviour
 *   <dd>Real {@code .pkg} artefacts whose tables of contents carry an external entity, a local file
 *       entity and a billion-laughs expansion, each refused, with the artefact named.
 * </dl>
 */
class XarTableOfContentsHardeningTest {

    @TempDir private Path work;

    private Path archives;

    private Path destination;

    @BeforeEach
    void createTree() throws IOException {
        archives = Files.createDirectories(work.resolve("archives"));
        destination = Files.createDirectories(work.resolve("dest"));
    }

    @Test
    @DisplayName(
            "hardening forces every setting from its unsafe value, not merely from the default")
    void everySettingIsForcedFromItsUnsafeValue() throws ParserConfigurationException {
        /* The same implementation the reader pins, so this measures that parser and no other. */
        DocumentBuilderFactory unsafe = DocumentBuilderFactory.newDefaultInstance();
        unsafe.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        unsafe.setFeature(PkgPayloadReader.DISALLOW_DOCTYPE, false);
        unsafe.setXIncludeAware(true);
        unsafe.setExpandEntityReferences(true);
        unsafe.setNamespaceAware(true);

        DocumentBuilderFactory hardened = PkgPayloadReader.harden(unsafe);

        assertAll(
                () ->
                        assertTrue(
                                hardened.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING),
                                "secure processing must be on: it is the runtime's own ceiling on"
                                        + " entity expansion and attribute counts"),
                () ->
                        assertTrue(
                                hardened.getFeature(PkgPayloadReader.DISALLOW_DOCTYPE),
                                "a DOCTYPE declaration must be refused outright: it is where an"
                                        + " external entity would be declared"),
                () ->
                        assertFalse(
                                hardened.isXIncludeAware(),
                                "XInclude must be off: it is a second way to pull in a document"
                                        + " this reader did not fetch"),
                () ->
                        assertFalse(
                                hardened.isExpandEntityReferences(),
                                "entity references must not be expanded"),
                () ->
                        assertFalse(
                                hardened.isNamespaceAware(),
                                "the table of contents is read by tag name, and namespace"
                                        + " awareness would make the same document parse into"
                                        + " different tag names"));
    }

    @Test
    @DisplayName("the reader's own parser is hardened, not just the one this test builds")
    void theReaderUsesTheHardenedFactory() throws IOException {
        /*
         * The behavioural tests below go through PkgPayloadReader.parse, so this is not the only
         * evidence -- but it pins the seam, so that a later change which hardens a factory nobody
         * parses with is visible here rather than in an incident.
         */
        byte[] pkg = ArchiveFixtures.pkgBytesWithToc(externalEntityToc(), new byte[] {1});
        ExtractionRejectedException rejection = refuse(pkg, "entity.pkg");
        assertEquals(RejectionReason.MALFORMED_ARCHIVE, rejection.reason());
    }

    @Test
    @DisplayName("a table of contents declaring an external entity is refused, naming the artefact")
    void anExternalEntityIsRefused() throws IOException {
        byte[] pkg = ArchiveFixtures.pkgBytesWithToc(externalEntityToc(), new byte[] {1});
        ExtractionRejectedException rejection = refuse(pkg, "external-entity.pkg");
        String opening =
                "the artefact \"external-entity.pkg\" was rejected because its container structure"
                        + " is not readable -- its table of contents is not readable as XML: ";
        assertAll(
                () -> assertEquals(RejectionReason.MALFORMED_ARCHIVE, rejection.reason()),
                () -> assertEquals("external-entity.pkg", rejection.subject()),
                () ->
                        assertTrue(
                                rejection.getMessage().startsWith(opening),
                                () -> "wrong message: " + rejection.getMessage()),
                () ->
                        assertTrue(
                                rejection.getMessage().contains("DOCTYPE"),
                                () ->
                                        "the reader must say what it refused, and it refused a"
                                                + " doctype declaration: "
                                                + rejection.getMessage()));
    }

    @Test
    @DisplayName("an entity pointing at a local file is refused before the file is opened")
    void aLocalFileEntityIsRefused() throws IOException {
        Path secret = Files.writeString(work.resolve("secret.txt"), "not for an archive to read");
        String toc =
                "<?xml version=\"1.0\"?>"
                        + "<!DOCTYPE xar [<!ENTITY leak SYSTEM \""
                        + secret.toUri()
                        + "\">]>"
                        + "<xar><toc><file id=\"1\"><name>&leak;</name><type>file</type>"
                        + "<data><offset>0</offset><length>1</length></data></file></toc></xar>";
        byte[] pkg = ArchiveFixtures.pkgBytesWithToc(toc, new byte[] {1});
        ExtractionRejectedException rejection = refuse(pkg, "file-entity.pkg");
        assertAll(
                () -> assertEquals(RejectionReason.MALFORMED_ARCHIVE, rejection.reason()),
                () ->
                        assertFalse(
                                rejection.getMessage().contains("not for an archive to read"),
                                "the file's contents must not appear anywhere, and least of all in"
                                        + " a message"),
                () ->
                        assertTrue(
                                rejection.getMessage().contains("DOCTYPE"),
                                () -> "wrong message: " + rejection.getMessage()));
    }

    @Test
    @DisplayName("a billion-laughs expansion is refused rather than expanded")
    void anEntityExpansionBombIsRefused() throws IOException {
        StringBuilder entities = new StringBuilder("<!ENTITY laugh0 \"ha\">");
        for (int level = 1; level <= 9; level++) {
            entities.append("<!ENTITY laugh").append(level).append(" \"");
            for (int repeat = 0; repeat < 10; repeat++) {
                entities.append("&laugh").append(level - 1).append(';');
            }
            entities.append("\">");
        }
        String toc =
                "<?xml version=\"1.0\"?><!DOCTYPE xar ["
                        + entities
                        + "]><xar><toc><file id=\"1\"><name>&laugh9;</name><type>file</type>"
                        + "<data><offset>0</offset><length>1</length></data></file></toc></xar>";
        byte[] pkg = ArchiveFixtures.pkgBytesWithToc(toc, new byte[] {1});
        ExtractionRejectedException rejection = refuse(pkg, "billion-laughs.pkg");
        assertAll(
                () -> assertEquals(RejectionReason.MALFORMED_ARCHIVE, rejection.reason()),
                () ->
                        assertTrue(
                                rejection.getMessage().contains("DOCTYPE"),
                                () ->
                                        "the expansion must be refused at the declaration, before"
                                                + " anything is expanded: "
                                                + rejection.getMessage()),
                () ->
                        assertTrue(
                                rejection.getMessage().length() < 500,
                                "a refusal that quoted the expansion would be the bomb going off"
                                        + " in the log"));
    }

    @Test
    @DisplayName("an ordinary table of contents still parses, so the hardening is not a blanket no")
    void anOrdinaryTableOfContentsStillParses() throws IOException {
        byte[] cpio = ArchiveFixtures.cpioBytes(List.of(Entry.file("tool.bin", "payload")));
        Path artefact =
                Files.write(
                        archives.resolve("ordinary.pkg"),
                        ArchiveFixtures.pkgBytesWithPayload(
                                ArchiveFixtures.gzip(cpio), "application/octet-stream"));
        ExtractionReport report =
                new ArtefactExtractor()
                        .extractWholeArtefact(
                                ArtefactKind.PKG_PAYLOAD, artefact, destination, "tool.bin");
        assertEquals(List.of("tool.bin"), report.paths());
    }

    private static String externalEntityToc() {
        return "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE xar [<!ENTITY reach SYSTEM \"http://127.0.0.1:1/x\">]>"
                + "<xar><toc><file id=\"1\"><name>&reach;</name><type>file</type>"
                + "<data><offset>0</offset><length>1</length></data></file></toc></xar>";
    }

    private ExtractionRejectedException refuse(byte[] pkg, String name) throws IOException {
        Path artefact = Files.write(archives.resolve(name), pkg);
        return assertThrows(
                ExtractionRejectedException.class,
                () ->
                        new ArtefactExtractor()
                                .extractWholeArtefact(
                                        ArtefactKind.PKG_PAYLOAD,
                                        artefact,
                                        destination,
                                        "whatever"),
                () -> name + " was parsed rather than refused");
    }
}
