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

package org.cometgui.install.verify;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.install.testing.Nulls;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link ArtefactVerifier}, which is where {@code R-SEC-02} is enforced.
 *
 * <p>The expected digests below were computed with {@code sha256sum} and {@code md5sum}, not with
 * the code under test: a fixture hashed by the hasher it is meant to check would agree with itself
 * whatever either of them did.
 *
 * <p>The two tests that matter most use a stub {@link HashService}, because the case {@code
 * R-SEC-02} exists to forbid -- a file whose MD5 agrees and whose SHA-256 does not -- cannot be
 * produced with real bytes by anybody. The stub is the only way to put the product in that state
 * and watch it refuse.
 */
class ArtefactVerifierTest {

    private static final String FIXTURE = "cometgui phase 05 unit 3 verification fixture\n";

    /** {@code sha256sum} of {@link #FIXTURE}. */
    private static final String FIXTURE_SHA256 =
            "64f2099b31a63a0f03f5f6f495aacd99118da3da6a1826dcf7925234baa23e19";

    /** {@code md5sum} of {@link #FIXTURE}. */
    private static final String FIXTURE_MD5 = "8a46b4484c08e0f688de288a2093d79e";

    /** The same length, one letter different: the shape a spliced or re-tagged artefact takes. */
    private static final String IMPOSTOR = "cometgui phase 05 unit 3 verification fixtura\n";

    /** {@code sha256sum} of {@link #IMPOSTOR}. */
    private static final String IMPOSTOR_SHA256 =
            "2751a8a7cf101dd06abb66704a6fb35ef1976929a834ece1beba327f0e9439f2";

    /** {@code md5sum} of {@link #IMPOSTOR}. */
    private static final String IMPOSTOR_MD5 = "0bceb27350bb0cdb513ee0a619c882c9";

    private static final long FIXTURE_SIZE = 46L;

    private static final URI SOURCE =
            URI.create("https://github.com/percolator/percolator/releases/download/rel/a.zip");

    @TempDir private Path work;

    /** A hasher that answers whatever the test says, and counts how often it was asked. */
    private static final class StubHashService implements HashService {
        private final FileHashes answer;
        private final List<Path> hashed = new ArrayList<>();

        StubHashService(FileHashes answer) {
            this.answer = answer;
        }

        @Override
        public FileHashes hash(Path path) {
            hashed.add(path);
            return answer;
        }

        List<Path> hashed() {
            return List.copyOf(hashed);
        }
    }

    private Path write(String content) throws IOException {
        Path file = work.resolve("artefact.zip");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName("real bytes with the pinned digests are accepted, and both digests are recorded")
    void realBytesAreAccepted() throws IOException {
        Path file = write(FIXTURE);
        VerificationResult result =
                new ArtefactVerifier(new StreamingHashService())
                        .verify(
                                file,
                                new FileHashes(FIXTURE_MD5, FIXTURE_SHA256),
                                FIXTURE_SIZE,
                                SOURCE);

        assertAll(
                () -> assertEquals(VerificationOutcome.MATCHED, result.outcome()),
                () -> assertTrue(result.accepted()),
                () ->
                        assertEquals(
                                Optional.of(new FileHashes(FIXTURE_MD5, FIXTURE_SHA256)),
                                result.actual(),
                                "MD5 is computed and recorded for provenance (R-SEC-02)"),
                () -> assertTrue(result.md5Matches()),
                () -> assertEquals(FIXTURE_SIZE, result.actualSizeBytes()),
                () -> assertTrue(result.message().startsWith("verified ")),
                () -> assertTrue(result.message().contains(FIXTURE_SHA256)));
    }

    @Test
    @DisplayName("real bytes that are not the pinned artefact are rejected, digests in the message")
    void realBytesThatAreNotTheArtefactAreRejected() throws IOException {
        Path file = write(IMPOSTOR);
        VerificationResult result =
                new ArtefactVerifier(new StreamingHashService())
                        .verify(
                                file,
                                new FileHashes(FIXTURE_MD5, FIXTURE_SHA256),
                                FIXTURE_SIZE,
                                SOURCE);

        assertAll(
                () -> assertEquals(VerificationOutcome.SHA256_MISMATCH, result.outcome()),
                () -> assertFalse(result.accepted()),
                () -> assertTrue(result.message().contains("expected " + FIXTURE_SHA256)),
                () -> assertTrue(result.message().contains("hashes to " + IMPOSTOR_SHA256)),
                () -> assertTrue(result.message().contains(SOURCE.toString())),
                () -> assertFalse(result.md5Matches()));
    }

    @Test
    @DisplayName("a matching MD5 does not rescue a mismatched SHA-256")
    void aMatchingMd5DoesNotRescueAMismatchedSha256() throws IOException {
        // The exact shape R-SEC-02 exists to forbid. It cannot be produced with real bytes by
        // anybody, so the hasher is stubbed: the file is the right length and the hasher reports
        // the pinned MD5 with a different SHA-256.
        Path file = write(FIXTURE);
        StubHashService hasher = new StubHashService(new FileHashes(FIXTURE_MD5, IMPOSTOR_SHA256));

        VerificationResult result =
                new ArtefactVerifier(hasher)
                        .verify(
                                file,
                                new FileHashes(FIXTURE_MD5, FIXTURE_SHA256),
                                FIXTURE_SIZE,
                                SOURCE);

        assertAll(
                () ->
                        assertEquals(
                                VerificationOutcome.SHA256_MISMATCH,
                                result.outcome(),
                                "MD5 is recorded for provenance and is never the trust mechanism"),
                () -> assertFalse(result.accepted()),
                () ->
                        assertTrue(
                                result.md5Matches(),
                                "the MD5 did agree, and it made no difference to the verdict"),
                () -> assertTrue(result.message().contains("expected " + FIXTURE_SHA256)),
                () -> assertTrue(result.message().contains("hashes to " + IMPOSTOR_SHA256)),
                () -> assertTrue(result.message().contains("MD5 is recorded for provenance")),
                () -> assertEquals(List.of(file), hasher.hashed()));
    }

    @Test
    @DisplayName("a mismatched MD5 does not reject a matching SHA-256")
    void aMismatchedMd5DoesNotRejectAMatchingSha256() throws IOException {
        // The other direction of the same rule, and the one a "check both digests" implementation
        // would get wrong. MD5 decides nothing, so a manifest whose MD5 is stale still installs.
        Path file = write(FIXTURE);
        StubHashService hasher = new StubHashService(new FileHashes(IMPOSTOR_MD5, FIXTURE_SHA256));

        VerificationResult result =
                new ArtefactVerifier(hasher)
                        .verify(
                                file,
                                new FileHashes(FIXTURE_MD5, FIXTURE_SHA256),
                                FIXTURE_SIZE,
                                SOURCE);

        assertAll(
                () -> assertEquals(VerificationOutcome.MATCHED, result.outcome()),
                () -> assertTrue(result.accepted()),
                () -> assertFalse(result.md5Matches()),
                () ->
                        assertEquals(
                                Optional.of(IMPOSTOR_MD5),
                                result.actual().map(FileHashes::md5),
                                "and the MD5 that was actually computed is recorded, not the"
                                        + " manifest's"));
    }

    @Test
    @DisplayName("a file of the wrong length is rejected without being hashed at all")
    void aFileOfTheWrongLengthIsRejectedWithoutHashing() throws IOException {
        Path file = write(FIXTURE);
        StubHashService hasher = new StubHashService(new FileHashes(FIXTURE_MD5, FIXTURE_SHA256));

        VerificationResult result =
                new ArtefactVerifier(hasher)
                        .verify(
                                file,
                                new FileHashes(FIXTURE_MD5, FIXTURE_SHA256),
                                FIXTURE_SIZE + 1,
                                SOURCE);

        assertAll(
                () -> assertEquals(VerificationOutcome.SIZE_MISMATCH, result.outcome()),
                () -> assertFalse(result.accepted()),
                () -> assertEquals(Optional.empty(), result.actual()),
                () -> assertEquals(FIXTURE_SIZE, result.actualSizeBytes()),
                () ->
                        assertEquals(
                                List.of(),
                                hasher.hashed(),
                                "a 99 MB download that arrived at the wrong length is not read back"
                                        + " off the disk to be told so"),
                () -> assertTrue(result.message().contains("size mismatch")),
                () -> assertTrue(result.message().contains(String.valueOf(FIXTURE_SIZE + 1))));
    }

    @Test
    @DisplayName("a missing file is rejected as absent, not as a mismatch")
    void aMissingFileIsAbsent() throws IOException {
        Path file = work.resolve("never-downloaded.zip");
        VerificationResult result =
                new ArtefactVerifier(new StreamingHashService())
                        .verify(
                                file,
                                new FileHashes(FIXTURE_MD5, FIXTURE_SHA256),
                                FIXTURE_SIZE,
                                SOURCE);

        assertAll(
                () -> assertEquals(VerificationOutcome.FILE_ABSENT, result.outcome()),
                () -> assertEquals(VerificationResult.NO_FILE, result.actualSizeBytes()),
                () -> assertFalse(result.md5Matches()),
                () -> assertTrue(result.message().contains("no file to verify")));
    }

    @Test
    @DisplayName("a directory where the artefact should be is absent, not hashed")
    void aDirectoryIsAbsent() throws IOException {
        Path directory = Files.createDirectory(work.resolve("artefact.zip"));
        StubHashService hasher = new StubHashService(new FileHashes(FIXTURE_MD5, FIXTURE_SHA256));

        VerificationResult result =
                new ArtefactVerifier(hasher)
                        .verify(
                                directory,
                                new FileHashes(FIXTURE_MD5, FIXTURE_SHA256),
                                FIXTURE_SIZE,
                                SOURCE);

        assertAll(
                () -> assertEquals(VerificationOutcome.FILE_ABSENT, result.outcome()),
                () -> assertEquals(List.of(), hasher.hashed()));
    }

    @Test
    @DisplayName("the record overload takes the URL, the size and the digests from the manifest")
    void theRecordOverloadUsesTheManifest() throws IOException {
        var manifest = org.cometgui.install.registry.ArtefactManifestReader.readFromClasspath();
        var record =
                manifest.artefacts().stream()
                        .filter(candidate -> "percolator".equals(candidate.tool().id()))
                        .filter(candidate -> "3.07.1".equals(candidate.version().text()))
                        .filter(candidate -> "linux-x86-64".equals(candidate.platform().id()))
                        .findFirst()
                        .orElseThrow();

        Path file = write(FIXTURE);
        VerificationResult result =
                new ArtefactVerifier(new StreamingHashService()).verify(record, file);

        assertAll(
                () ->
                        assertEquals(
                                VerificationOutcome.SIZE_MISMATCH,
                                result.outcome(),
                                "a 46-byte text file is not a 946303-byte zip"),
                () -> assertEquals(record.url(), result.source()),
                () -> assertEquals(record.sizeBytes(), result.expectedSizeBytes()),
                () -> assertEquals(record.hashes(), result.expected()));
    }

    @Test
    @DisplayName("null arguments are rejected by name")
    void nullArgumentsAreRejected() throws IOException {
        ArtefactVerifier verifier = new ArtefactVerifier(new StreamingHashService());
        FileHashes expected = new FileHashes(FIXTURE_MD5, FIXTURE_SHA256);
        Path file = write(FIXTURE);

        assertAll(
                () ->
                        assertEquals(
                                "hashes",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ArtefactVerifier(
                                                                Nulls.of(HashService.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "file",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        verifier.verify(
                                                                Nulls.of(Path.class),
                                                                expected,
                                                                FIXTURE_SIZE,
                                                                SOURCE))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "expected",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        verifier.verify(
                                                                file,
                                                                Nulls.of(FileHashes.class),
                                                                FIXTURE_SIZE,
                                                                SOURCE))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "source",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        verifier.verify(
                                                                file,
                                                                expected,
                                                                FIXTURE_SIZE,
                                                                Nulls.of(URI.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "record",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        verifier.verify(
                                                                Nulls.of(
                                                                        org.cometgui.install
                                                                                .registry
                                                                                .ArtefactRecord
                                                                                .class),
                                                                file))
                                        .getMessage()));
    }
}
