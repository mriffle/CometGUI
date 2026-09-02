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

package org.cometgui.install.registry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ArtefactValues}, the rules four records share.
 *
 * <p>The install-path rule is the one worth reading. It is what stops a manifest placing a file
 * outside a tool's install directory, and it is graded over every shape of escape rather than over
 * the {@code ../} that first comes to mind -- an absolute path, a Windows drive letter, a
 * backslash, an empty segment and a {@code .} segment each escape or confuse in their own way.
 */
class ArtefactValuesTest {

    private static final String GOOD_SHA256 =
            "4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1";

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "bin/percolator",
                "bin/comet.exe",
                "share/xml/percolator/xml-pout-1-5/percolator_out.xsd",
                "PDV-2.7.0/PDV-2.7.0.jar",
                "cometPercolator2LimelightXML.jar",
                "a"
            })
    @DisplayName("a relative path inside the install directory is accepted")
    void aRelativePathIsAccepted(String path) {
        assertEquals(path, ArtefactValues.installRelativePath(path, "installedPath"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "../percolator",
                "bin/../../percolator",
                "bin/./percolator",
                "/usr/bin/percolator",
                "bin/",
                "bin//percolator",
                "..",
                ".",
                "bin\\percolator",
                "\\percolator",
                "C:/percolator",
                ":percolator",
                "\u0000percolator",
                "bin/perc\u0000olator",
                "\\\\server\\share\\percolator"
            })
    @DisplayName("a path that escapes, or that spells itself two ways, is rejected by name")
    void anEscapingPathIsRejected(String path) {
        assertEquals(
                "installedPath must be a relative path inside the install directory, with no"
                        + " empty, \".\" or \"..\" segment and no backslash, but was: \""
                        + path
                        + "\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> ArtefactValues.installRelativePath(path, "installedPath"))
                        .getMessage());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", " ", "\t", "\n", "   \t  \n"})
    @DisplayName("a blank path and blank text are both rejected, naming the field")
    void blankIsRejected(String blank) {
        assertAll(
                () ->
                        assertEquals(
                                "installedPath must not be blank",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        ArtefactValues.installRelativePath(
                                                                blank, "installedPath"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "releaseTag must not be blank",
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        ArtefactValues.requiredText(
                                                                blank, "releaseTag"))
                                        .getMessage()));
    }

    @Test
    @DisplayName("text is stripped, so a value that is only whitespace cannot slip through")
    void textIsStripped() {
        assertEquals("rel-3-07-01", ArtefactValues.requiredText("  rel-3-07-01\n", "releaseTag"));
    }

    @ParameterizedTest(name = "[{index}] {0} bytes")
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    @DisplayName("a size that is not positive is rejected, naming the field and the value")
    void aNonPositiveSizeIsRejected(long size) {
        assertEquals(
                "sizeBytes must be a positive number of bytes, but was: " + size,
                assertThrows(
                                IllegalArgumentException.class,
                                () -> ArtefactValues.positiveSize(size, "sizeBytes"))
                        .getMessage());
    }

    @Test
    @DisplayName("a positive size is accepted, including one byte")
    void aPositiveSizeIsAccepted() {
        assertAll(
                () -> assertEquals(1L, ArtefactValues.positiveSize(1L, "sizeBytes")),
                () ->
                        assertEquals(
                                103407417L, ArtefactValues.positiveSize(103407417L, "sizeBytes")));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "http://github.com/example/artefact",
                "ftp://github.com/example/artefact",
                "file:///tmp/artefact",
                "github.com/example/artefact",
                "/example/artefact",
                "https:///example/artefact",
                "https://user:secret@github.com/example/artefact"
            })
    @DisplayName("a URL that is not a credential-free absolute https URL is rejected")
    void aBadUrlIsRejected(String url) {
        assertEquals(
                "url must be an absolute https URL with a host and no credentials, but was: \""
                        + url
                        + "\"",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> ArtefactValues.downloadUrl(URI.create(url), "url"))
                        .getMessage());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "https://github.com/UWPR/Comet/releases/download/v2026.02.2/comet.linux.exe",
                "HTTPS://github.com/example/artefact",
                "https://raw.githubusercontent.com/example/example/t/LICENSE"
            })
    @DisplayName("an https URL is accepted, whatever case the scheme is written in")
    void anHttpsUrlIsAccepted(String url) {
        URI parsed = URI.create(url);

        assertEquals(parsed, ArtefactValues.downloadUrl(parsed, "url"));
    }

    @Test
    @DisplayName("a digest is checked at both lengths, and the length comes from FileHashes")
    void digestsAreCheckedAtBothLengths() {
        List<Executable> assertions = new ArrayList<>();
        for (int length : List.of(FileHashes.SHA256_LENGTH, FileHashes.MD5_LENGTH)) {
            String good = GOOD_SHA256.substring(0, length);
            assertions.add(() -> assertEquals(good, ArtefactValues.digest(good, length, "sha256")));
            for (String bad : List.of(good + "0", good.substring(1), good.replace('4', 'z'), "")) {
                assertions.add(
                        () ->
                                assertEquals(
                                        "sha256 must be " + length + " hexadecimal characters",
                                        assertThrows(
                                                        IllegalArgumentException.class,
                                                        () ->
                                                                ArtefactValues.digest(
                                                                        bad, length, "sha256"))
                                                .getMessage(),
                                        "length " + length + " against \"" + bad + "\""));
            }
        }
        assertAll(assertions);
    }

    @Test
    @DisplayName("an upper-case digest is accepted here, because FileHashes canonicalises it")
    void anUpperCaseDigestIsAccepted() {
        String upper = GOOD_SHA256.toUpperCase(java.util.Locale.ROOT);

        assertEquals(upper, ArtefactValues.digest(upper, FileHashes.SHA256_LENGTH, "sha256"));
    }

    @Test
    @DisplayName("a null value is rejected by the name of its field")
    void aNullIsRejectedByName() {
        assertAll(
                () ->
                        assertEquals(
                                "releaseTag",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        ArtefactValues.requiredText(
                                                                Nulls.of(String.class),
                                                                "releaseTag"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "url",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        ArtefactValues.downloadUrl(
                                                                Nulls.of(URI.class), "url"))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "sha256",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        ArtefactValues.digest(
                                                                Nulls.of(String.class),
                                                                64,
                                                                "sha256"))
                                        .getMessage()));
    }

    @Test
    @DisplayName("the utility class cannot be instantiated, even by reflection")
    void theUtilityClassIsNotInstantiable() throws ReflectiveOperationException {
        var constructor = ArtefactValues.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertEquals(
                "ArtefactValues is a utility class and is never instantiated",
                assertThrows(
                                java.lang.reflect.InvocationTargetException.class,
                                constructor::newInstance)
                        .getCause()
                        .getMessage());
    }
}
