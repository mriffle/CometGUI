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

package org.cometgui.tools.percolator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import javax.xml.stream.XMLInputFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reading back what Percolator wrote, and refusing everything that is not it.
 *
 * <p>The valid documents here are shortened copies of the real 3.07.1 output, keeping the exact
 * root element, namespace declarations and attribute spelling the binary produces -- the full
 * documents are read in {@code PercolatorRealBinaryTest}, which is what proves this reader against
 * the tool rather than against a fixture.
 */
class PoutDocumentTest {

    private static final String OPEN =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<percolator_output \n"
                    + "xmlns=\"http://per-colator.com/percolator_out/15\" \n"
                    + "xmlns:p=\"http://per-colator.com/percolator_out/15\" \n"
                    + "p:majorVersion=\"3\" p:minorVersion=\"07\">\n";

    private static Path write(Path directory, String name, String content) throws IOException {
        return Files.writeString(directory.resolve(name), content, StandardCharsets.UTF_8);
    }

    private static String targetsOnly(int psms) {
        StringBuilder document = new StringBuilder(OPEN).append("<psms>\n");
        for (int index = 0; index < psms; index++) {
            document.append("<psm p:psm_id=\"psm")
                    .append(index)
                    .append("\"><svm_score>0.1</svm_score></psm>\n");
        }
        return document.append("</psms>\n</percolator_output>\n").toString();
    }

    private static String withDecoys(int psms) {
        StringBuilder document = new StringBuilder(OPEN).append("<psms>\n");
        for (int index = 0; index < psms; index++) {
            document.append("<psm p:decoy=\"")
                    .append(index % 2 == 0 ? "false" : "true")
                    .append("\" p:psm_id=\"psm")
                    .append(index)
                    .append("\"/>\n");
        }
        return document.append("</psms>\n</percolator_output>\n").toString();
    }

    @Test
    @DisplayName("the namespace is the full percolator_out/15 string, not a prefix of it")
    void theNamespaceIsPinnedInFull() {
        assertAll(
                () ->
                        assertEquals(
                                "http://per-colator.com/percolator_out/15", PoutDocument.NAMESPACE),
                () -> assertEquals("percolator_output", PoutDocument.ROOT_ELEMENT));
    }

    @Test
    @DisplayName("a target-only document is read: root, namespace, counts, and no decoy values")
    void aTargetOnlyDocument(@TempDir Path directory) throws IOException {
        PoutDocument document = PoutDocument.read(write(directory, "targets.xml", targetsOnly(64)));

        assertAll(
                () -> assertTrue(document.isPercolatorOutput()),
                () -> assertEquals("percolator_output", document.rootElement()),
                () ->
                        assertEquals(
                                "http://per-colator.com/percolator_out/15", document.namespace()),
                () -> assertEquals(64, document.psmCount()),
                () -> assertEquals(Set.of(), document.psmDecoyValues()),
                () ->
                        assertFalse(
                                document.hasBothDecoyValues(),
                                "the real binary writes no decoy attribute at all without -Z, so"
                                        + " target-only output must not read as decoy output"));
    }

    @Test
    @DisplayName("a decoy document carries both values, and both are seen")
    void aDecoyDocument(@TempDir Path directory) throws IOException {
        PoutDocument document = PoutDocument.read(write(directory, "decoys.xml", withDecoys(128)));

        assertAll(
                () -> assertEquals(128, document.psmCount()),
                () -> assertEquals(Set.of("false", "true"), document.psmDecoyValues()),
                () -> assertTrue(document.hasBothDecoyValues()));
    }

    @Test
    @DisplayName("one decoy value on its own is not both: a run that wrote only targets is not -Z")
    void oneDecoyValueIsNotBoth(@TempDir Path directory) throws IOException {
        String onlyFalse = OPEN + "<psms><psm p:decoy=\"false\"/></psms></percolator_output>";
        String onlyTrue = OPEN + "<psms><psm p:decoy=\"true\"/></psms></percolator_output>";

        assertAll(
                () ->
                        assertFalse(
                                PoutDocument.read(write(directory, "f.xml", onlyFalse))
                                        .hasBothDecoyValues()),
                () ->
                        assertFalse(
                                PoutDocument.read(write(directory, "t.xml", onlyTrue))
                                        .hasBothDecoyValues()));
    }

    @Test
    @DisplayName("a decoy attribute in another namespace is not the one Percolator writes")
    void aDecoyAttributeInAnotherNamespace(@TempDir Path directory) throws IOException {
        String foreign =
                OPEN
                        + "<psms><psm xmlns:q=\"http://example.invalid/\" q:decoy=\"true\"/>"
                        + "<psm xmlns:q=\"http://example.invalid/\" q:decoy=\"false\"/></psms>"
                        + "</percolator_output>";

        PoutDocument document = PoutDocument.read(write(directory, "foreign.xml", foreign));

        assertAll(
                () -> assertEquals(2, document.psmCount()),
                () -> assertEquals(Set.of(), document.psmDecoyValues()));
    }

    @Test
    @DisplayName("a ZERO-BYTE file is refused by name: it is what an aborted run leaves behind")
    void aZeroByteFile(@TempDir Path directory) throws IOException {
        Path empty = write(directory, "aborted.xml", "");

        assertEquals(
                "Percolator left a ZERO-BYTE file at "
                        + empty
                        + ": the file exists and says nothing, which is what an aborted run leaves"
                        + " behind, so its existence is not evidence of XML output",
                assertThrows(IOException.class, () -> PoutDocument.read(empty)).getMessage());
    }

    @Test
    @DisplayName("a file that was never written is refused differently from an empty one")
    void aMissingFile(@TempDir Path directory) {
        Path missing = directory.resolve("never-written.xml");

        assertEquals(
                "Percolator wrote no file at " + missing + ", so it produced no XML to inspect",
                assertThrows(IOException.class, () -> PoutDocument.read(missing)).getMessage());
    }

    @Test
    @DisplayName("output that is not XML at all is refused, naming the file")
    void notXmlAtAll(@TempDir Path directory) throws IOException {
        Path text = write(directory, "not-xml.xml", "Exception caught: Error: median decoy score");

        IOException refused = assertThrows(IOException.class, () -> PoutDocument.read(text));

        assertTrue(
                refused.getMessage()
                        .startsWith("the file Percolator wrote at " + text + " is not well-formed"),
                refused.getMessage());
    }

    @Test
    @DisplayName("a truncated document is refused rather than read as a shorter one")
    void aTruncatedDocument(@TempDir Path directory) throws IOException {
        String whole = targetsOnly(64);
        Path truncated = write(directory, "cut.xml", whole.substring(0, whole.length() / 2));

        assertThrows(IOException.class, () -> PoutDocument.read(truncated));
    }

    @Test
    @DisplayName("a document in another namespace is read but is not Percolator output")
    void anotherNamespace(@TempDir Path directory) throws IOException {
        String other =
                "<percolator_output xmlns=\"http://per-colator.com/percolator_out/\">"
                        + "<psms><psm/></psms></percolator_output>";

        PoutDocument document = PoutDocument.read(write(directory, "other.xml", other));

        assertAll(
                () ->
                        assertFalse(
                                document.isPercolatorOutput(),
                                "the specification and this phase's work log quote the namespace"
                                        + " one path segment short; a prefix match would accept"
                                        + " this document, and the real binaries write the /15"
                                        + " form"),
                () -> assertEquals("http://per-colator.com/percolator_out/", document.namespace()),
                () -> assertEquals(1, document.psmCount()));
    }

    @Test
    @DisplayName("a document with the right namespace and the wrong root is not Percolator output")
    void anotherRootElement(@TempDir Path directory) throws IOException {
        String other =
                "<something_else xmlns=\"http://per-colator.com/percolator_out/15\">"
                        + "<psms><psm/></psms></something_else>";

        PoutDocument document = PoutDocument.read(write(directory, "root.xml", other));

        assertAll(
                () -> assertFalse(document.isPercolatorOutput()),
                () -> assertEquals("something_else", document.rootElement()));
    }

    @Test
    @DisplayName("a document with no namespace at all is not Percolator output")
    void noNamespace(@TempDir Path directory) throws IOException {
        PoutDocument document =
                PoutDocument.read(
                        write(
                                directory,
                                "bare.xml",
                                "<percolator_output><psm/></percolator_output>"));

        assertAll(
                () -> assertFalse(document.isPercolatorOutput()),
                () -> assertEquals("", document.namespace()),
                () -> assertEquals("percolator_output", document.rootElement()));
    }

    @Test
    @DisplayName("a DOCTYPE is refused: the writer is a binary the user may have chosen")
    void aDoctypeIsRefused(@TempDir Path directory) throws IOException {
        Path bomb =
                write(
                        directory,
                        "doctype.xml",
                        "<?xml version=\"1.0\"?>\n"
                                + "<!DOCTYPE percolator_output [\n"
                                + "  <!ENTITY payload \"boom\">\n"
                                + "]>\n"
                                + "<percolator_output"
                                + " xmlns=\"http://per-colator.com/percolator_out/15\">"
                                + "<psms><psm p:decoy=\"&payload;\""
                                + " xmlns:p=\"http://per-colator.com/percolator_out/15\"/></psms>"
                                + "</percolator_output>");

        IOException refused = assertThrows(IOException.class, () -> PoutDocument.read(bomb));

        assertTrue(
                refused.getMessage().contains("is not well-formed XML"),
                "DTD support is forced off, so a document that needs one cannot be read at all: "
                        + refused.getMessage());
    }

    @Test
    @DisplayName("an external entity is refused rather than fetched")
    void anExternalEntityIsRefused(@TempDir Path directory) throws IOException {
        Path secret = write(directory, "secret.txt", "the-secret-contents");
        Path attack =
                write(
                        directory,
                        "external.xml",
                        "<?xml version=\"1.0\"?>\n"
                                + "<!DOCTYPE percolator_output [\n"
                                + "  <!ENTITY leak SYSTEM \""
                                + secret.toUri()
                                + "\">\n"
                                + "]>\n"
                                + "<percolator_output"
                                + " xmlns=\"http://per-colator.com/percolator_out/15\">"
                                + "<psms><psm>&leak;</psm></psms></percolator_output>");

        IOException refused = assertThrows(IOException.class, () -> PoutDocument.read(attack));

        assertAll(
                () -> assertTrue(refused.getMessage().contains("is not well-formed XML")),
                () ->
                        assertFalse(
                                refused.getMessage().contains("the-secret-contents"),
                                "the file's contents must not reach the diagnostic: "
                                        + refused.getMessage()));
    }

    @Test
    @DisplayName("both hardening settings are FORCED from their unsafe values, not merely assumed")
    void everySettingIsForcedFromItsUnsafeValue() {
        XMLInputFactory unsafe = XMLInputFactory.newDefaultFactory();
        unsafe.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.TRUE);
        unsafe.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.TRUE);

        XMLInputFactory hardened = PoutDocument.harden(unsafe);

        assertAll(
                () ->
                        assertEquals(
                                Boolean.FALSE,
                                hardened.getProperty(XMLInputFactory.SUPPORT_DTD),
                                "a factory that arrived with DTD support ON must leave with it"
                                        + " off, or the call is one that can be deleted with the"
                                        + " suite green"),
                () ->
                        assertEquals(
                                Boolean.FALSE,
                                hardened.getProperty(
                                        XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES)),
                () -> assertSame(unsafe, hardened),
                () ->
                        assertEquals(
                                "factory",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> PoutDocument.harden(null))
                                        .getMessage()));
    }

    @Test
    @DisplayName("the file is required")
    void theFileIsRequired() {
        assertEquals(
                "file",
                assertThrows(NullPointerException.class, () -> PoutDocument.read(null))
                        .getMessage());
    }
}
