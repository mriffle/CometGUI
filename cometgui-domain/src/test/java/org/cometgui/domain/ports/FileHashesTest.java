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

package org.cometgui.domain.ports;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link FileHashes}.
 *
 * <p>The digests are the real ones for an empty file, so that the constants in this test are
 * checkable against {@code md5sum /dev/null} and {@code sha256sum /dev/null} by anyone who
 * distrusts them -- which is the right attitude to a checksum fixture.
 */
class FileHashesTest {

    private static final String EMPTY_MD5 = "d41d8cd98f00b204e9800998ecf8427e";
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    @DisplayName("keeps both digests")
    void keepsBothDigests() {
        FileHashes hashes = new FileHashes(EMPTY_MD5, EMPTY_SHA256);

        assertAll(
                () -> assertEquals(EMPTY_MD5, hashes.md5()),
                () -> assertEquals(EMPTY_SHA256, hashes.sha256()));
    }

    @Test
    @DisplayName("the declared lengths are the algorithms' hexadecimal lengths")
    void declaredLengthsAreCorrect() {
        assertAll(
                () -> assertEquals(32, FileHashes.MD5_LENGTH),
                () -> assertEquals(64, FileHashes.SHA256_LENGTH),
                () -> assertEquals(FileHashes.MD5_LENGTH, EMPTY_MD5.length()),
                () -> assertEquals(FileHashes.SHA256_LENGTH, EMPTY_SHA256.length()));
    }

    @Test
    @DisplayName("an uppercase digest is stored lowercase, so two records of one file are equal")
    void uppercaseIsCanonicalised() {
        FileHashes upper =
                new FileHashes(
                        EMPTY_MD5.toUpperCase(java.util.Locale.ROOT),
                        EMPTY_SHA256.toUpperCase(java.util.Locale.ROOT));

        assertAll(
                () -> assertEquals(EMPTY_MD5, upper.md5()),
                () -> assertEquals(EMPTY_SHA256, upper.sha256()),
                () -> assertEquals(new FileHashes(EMPTY_MD5, EMPTY_SHA256), upper),
                () ->
                        assertEquals(
                                new FileHashes(EMPTY_MD5, EMPTY_SHA256).hashCode(),
                                upper.hashCode()));
    }

    @Test
    @DisplayName("a different digest is a different value")
    void differentDigestsAreNotEqual() {
        assertNotEquals(
                new FileHashes(EMPTY_MD5, EMPTY_SHA256),
                new FileHashes(EMPTY_MD5.replace('d', 'a'), EMPTY_SHA256));
    }

    @Test
    @DisplayName("a null digest is rejected by field name")
    void rejectsNullDigests() {
        assertAll(
                () ->
                        assertEquals(
                                "md5",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> new FileHashes(null, EMPTY_SHA256))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "sha256",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> new FileHashes(EMPTY_MD5, null))
                                        .getMessage()));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(
            strings = {
                "",
                "d41d8cd98f00b204e9800998ecf8427",
                "d41d8cd98f00b204e9800998ecf8427ee",
                "d41d8cd98f00b204e9800998ecf8427g",
                "d41d8cd9 8f00b204e9800998ecf8427"
            })
    @DisplayName("an MD5 that is not 32 hexadecimal characters is rejected, quoting it")
    void rejectsMalformedMd5(String malformed) {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new FileHashes(malformed, EMPTY_SHA256));

        assertEquals(
                "md5 must be 32 hexadecimal characters, but was: \"" + malformed + "\"",
                thrown.getMessage());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(
            strings = {
                "",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b85",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855a",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b85z"
            })
    @DisplayName("a SHA-256 that is not 64 hexadecimal characters is rejected, quoting it")
    void rejectsMalformedSha256(String malformed) {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class, () -> new FileHashes(EMPTY_MD5, malformed));

        assertEquals(
                "sha256 must be 64 hexadecimal characters, but was: \"" + malformed + "\"",
                thrown.getMessage());
    }

    @Test
    @DisplayName("the description carries both digests")
    void descriptionCarriesBothDigests() {
        assertEquals(
                "FileHashes[md5=" + EMPTY_MD5 + ", sha256=" + EMPTY_SHA256 + "]",
                new FileHashes(EMPTY_MD5, EMPTY_SHA256).toString());
    }
}
