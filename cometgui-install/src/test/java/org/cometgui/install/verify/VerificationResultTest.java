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

import java.net.URI;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for {@link VerificationResult} and {@link VerificationOutcome}.
 *
 * <p>The record refuses to describe a state that contradicts itself -- a {@code MATCHED} whose
 * digests differ, an {@code SHA256_MISMATCH} whose digests agree -- so that a defect in the
 * verifier fails here rather than quietly letting an unverified artefact through.
 */
class VerificationResultTest {

    private static final URI SOURCE = URI.create("https://example.org/percolator.zip");
    private static final Path FILE = Path.of("percolator.zip");

    private static final FileHashes EXPECTED =
            new FileHashes(
                    "9c86de1c45d2d93dae1ab43216b5864c",
                    "4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1");

    private static final FileHashes OTHER_SHA =
            new FileHashes(
                    "9c86de1c45d2d93dae1ab43216b5864c",
                    "1ba38acf09520cc89d5ed907ed0382c4d23876a7e20ec3e91cbbaa2ed431237c");

    private static final FileHashes OTHER_MD5 =
            new FileHashes(
                    "0b77b68fd859639d7421f1c5e006ade5",
                    "4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1");

    private static VerificationResult matched() {
        return new VerificationResult(
                VerificationOutcome.MATCHED,
                SOURCE,
                FILE,
                EXPECTED,
                946_303,
                Optional.of(EXPECTED),
                946_303);
    }

    @ParameterizedTest(name = "{0}.accepted()")
    @EnumSource(VerificationOutcome.class)
    void onlyMatchedIsAccepted(VerificationOutcome outcome) {
        assertEquals(outcome == VerificationOutcome.MATCHED, outcome.accepted());
    }

    @Test
    @DisplayName("exactly one of the four outcomes accepts")
    void exactlyOneOutcomeAccepts() {
        Set<VerificationOutcome> accepting =
                EnumSet.allOf(VerificationOutcome.class).stream()
                        .filter(VerificationOutcome::accepted)
                        .collect(
                                java.util.stream.Collectors.toCollection(
                                        () -> EnumSet.noneOf(VerificationOutcome.class)));
        assertEquals(EnumSet.of(VerificationOutcome.MATCHED), accepting);
    }

    @Test
    @DisplayName("a match records both digests and reports them")
    void aMatchRecordsBothDigests() {
        VerificationResult result = matched();
        assertAll(
                () -> assertTrue(result.accepted()),
                () -> assertTrue(result.md5Matches()),
                () -> assertEquals(SOURCE, result.source()),
                () -> assertEquals(FILE, result.file()),
                () -> assertEquals(EXPECTED, result.expected()),
                () -> assertEquals(946_303L, result.expectedSizeBytes()),
                () -> assertEquals(946_303L, result.actualSizeBytes()),
                () -> assertTrue(result.message().contains(EXPECTED.sha256())));
    }

    @Test
    @DisplayName("md5Matches is independent of the verdict in both directions")
    void md5MatchesIsIndependentOfTheVerdict() {
        VerificationResult acceptedWithWrongMd5 =
                new VerificationResult(
                        VerificationOutcome.MATCHED,
                        SOURCE,
                        FILE,
                        EXPECTED,
                        946_303,
                        Optional.of(OTHER_MD5),
                        946_303);
        VerificationResult rejectedWithRightMd5 =
                new VerificationResult(
                        VerificationOutcome.SHA256_MISMATCH,
                        SOURCE,
                        FILE,
                        EXPECTED,
                        946_303,
                        Optional.of(OTHER_SHA),
                        946_303);

        assertAll(
                () -> assertTrue(acceptedWithWrongMd5.accepted()),
                () -> assertFalse(acceptedWithWrongMd5.md5Matches()),
                () -> assertFalse(rejectedWithRightMd5.accepted()),
                () -> assertTrue(rejectedWithRightMd5.md5Matches()),
                () ->
                        assertFalse(
                                matched().md5Matches() == acceptedWithWrongMd5.md5Matches(),
                                "the two differ only in their MD5, and only md5Matches sees it"));
    }

    @Test
    @DisplayName("a result with no digests reports md5Matches as false rather than throwing")
    void noDigestsMeansNoMd5Match() {
        VerificationResult absent =
                new VerificationResult(
                        VerificationOutcome.FILE_ABSENT,
                        SOURCE,
                        FILE,
                        EXPECTED,
                        946_303,
                        Optional.empty(),
                        VerificationResult.NO_FILE);
        assertFalse(absent.md5Matches());
    }

    @Test
    @DisplayName("MATCHED cannot be built over digests that do not match")
    void matchedCannotLie() {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new VerificationResult(
                                        VerificationOutcome.MATCHED,
                                        SOURCE,
                                        FILE,
                                        EXPECTED,
                                        946_303,
                                        Optional.of(OTHER_SHA),
                                        946_303));
        assertTrue(thrown.getMessage().startsWith("MATCHED requires the actual SHA-256"));
    }

    @Test
    @DisplayName("MATCHED cannot be built over a size that does not match")
    void matchedCannotLieAboutSize() {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new VerificationResult(
                                        VerificationOutcome.MATCHED,
                                        SOURCE,
                                        FILE,
                                        EXPECTED,
                                        946_303,
                                        Optional.of(EXPECTED),
                                        946_302));
        assertTrue(thrown.getMessage().startsWith("MATCHED requires the actual size"));
    }

    @Test
    @DisplayName("MATCHED cannot be built without the digests that were computed")
    void matchedNeedsDigests() {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new VerificationResult(
                                        VerificationOutcome.MATCHED,
                                        SOURCE,
                                        FILE,
                                        EXPECTED,
                                        946_303,
                                        Optional.empty(),
                                        946_303));
        assertTrue(thrown.getMessage().startsWith("MATCHED requires the digests"));
    }

    @Test
    @DisplayName("SHA256_MISMATCH cannot be built over digests that agree")
    void mismatchCannotLie() {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new VerificationResult(
                                        VerificationOutcome.SHA256_MISMATCH,
                                        SOURCE,
                                        FILE,
                                        EXPECTED,
                                        946_303,
                                        Optional.of(EXPECTED),
                                        946_303));
        assertTrue(thrown.getMessage().startsWith("SHA256_MISMATCH requires the two digests"));
    }

    @Test
    @DisplayName("SHA256_MISMATCH cannot be built without digests")
    void mismatchNeedsDigests() {
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new VerificationResult(
                                        VerificationOutcome.SHA256_MISMATCH,
                                        SOURCE,
                                        FILE,
                                        EXPECTED,
                                        946_303,
                                        Optional.empty(),
                                        946_303));
        assertTrue(thrown.getMessage().startsWith("SHA256_MISMATCH requires the digests"));
    }

    @Test
    @DisplayName("SIZE_MISMATCH cannot be built over sizes that agree, or with digests")
    void sizeMismatchCannotLie() {
        assertAll(
                () ->
                        assertTrue(
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new VerificationResult(
                                                                VerificationOutcome.SIZE_MISMATCH,
                                                                SOURCE,
                                                                FILE,
                                                                EXPECTED,
                                                                946_303,
                                                                Optional.empty(),
                                                                946_303))
                                        .getMessage()
                                        .startsWith("SIZE_MISMATCH requires the two sizes")),
                () ->
                        assertTrue(
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new VerificationResult(
                                                                VerificationOutcome.SIZE_MISMATCH,
                                                                SOURCE,
                                                                FILE,
                                                                EXPECTED,
                                                                946_303,
                                                                Optional.of(EXPECTED),
                                                                12))
                                        .getMessage()
                                        .startsWith("SIZE_MISMATCH is reached without computing")));
    }

    @Test
    @DisplayName("FILE_ABSENT cannot carry a size, or digests")
    void absentCannotCarryASize() {
        assertAll(
                () ->
                        assertTrue(
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new VerificationResult(
                                                                VerificationOutcome.FILE_ABSENT,
                                                                SOURCE,
                                                                FILE,
                                                                EXPECTED,
                                                                946_303,
                                                                Optional.empty(),
                                                                0))
                                        .getMessage()
                                        .startsWith("FILE_ABSENT carries no size")),
                () ->
                        assertTrue(
                                assertThrows(
                                                IllegalArgumentException.class,
                                                () ->
                                                        new VerificationResult(
                                                                VerificationOutcome.FILE_ABSENT,
                                                                SOURCE,
                                                                FILE,
                                                                EXPECTED,
                                                                946_303,
                                                                Optional.of(EXPECTED),
                                                                VerificationResult.NO_FILE))
                                        .getMessage()
                                        .startsWith("FILE_ABSENT is reached without computing")));
    }

    @Test
    @DisplayName("every outcome produces a message that names the file and the URL")
    void everyOutcomeHasAMessage() {
        VerificationResult mismatch =
                new VerificationResult(
                        VerificationOutcome.SHA256_MISMATCH,
                        SOURCE,
                        FILE,
                        EXPECTED,
                        946_303,
                        Optional.of(OTHER_SHA),
                        946_303);
        VerificationResult sizeMismatch =
                new VerificationResult(
                        VerificationOutcome.SIZE_MISMATCH,
                        SOURCE,
                        FILE,
                        EXPECTED,
                        946_303,
                        Optional.empty(),
                        12);
        VerificationResult absent =
                new VerificationResult(
                        VerificationOutcome.FILE_ABSENT,
                        SOURCE,
                        FILE,
                        EXPECTED,
                        946_303,
                        Optional.empty(),
                        VerificationResult.NO_FILE);

        assertAll(
                () -> assertTrue(matched().message().contains(FILE.toString())),
                () -> assertTrue(matched().message().contains(SOURCE.toString())),
                () -> assertTrue(mismatch.message().contains(OTHER_SHA.sha256())),
                () -> assertTrue(mismatch.message().contains("R-SEC-02")),
                () -> assertTrue(mismatch.message().contains(SOURCE.toString())),
                () -> assertTrue(sizeMismatch.message().contains("946303")),
                () -> assertTrue(sizeMismatch.message().contains("12")),
                () -> assertTrue(sizeMismatch.message().contains(SOURCE.toString())),
                () -> assertTrue(absent.message().contains("no file to verify")),
                () -> assertTrue(absent.message().contains(SOURCE.toString())));
    }

    @Test
    @DisplayName("a message survives digests that were never computed")
    void aMessageSurvivesAbsentDigests() {
        // SIZE_MISMATCH never carries digests, and the mismatch branch of the message has to say
        // so rather than throwing on an empty Optional.
        VerificationResult sizeMismatch =
                new VerificationResult(
                        VerificationOutcome.SIZE_MISMATCH,
                        SOURCE,
                        FILE,
                        EXPECTED,
                        946_303,
                        Optional.empty(),
                        12);
        assertFalse(sizeMismatch.message().contains("null"));
    }

    @Test
    @DisplayName("null components are rejected by name")
    void nullComponentsAreRejected() {
        assertAll(
                () -> assertEquals("outcome", nullMessage(0)),
                () -> assertEquals("source", nullMessage(1)),
                () -> assertEquals("file", nullMessage(2)),
                () -> assertEquals("expected", nullMessage(3)),
                () -> assertEquals("actual", nullMessage(4)));
    }

    private static String nullMessage(int which) {
        return assertThrows(
                        NullPointerException.class,
                        () ->
                                new VerificationResult(
                                        which == 0
                                                ? Nulls.of(VerificationOutcome.class)
                                                : VerificationOutcome.MATCHED,
                                        which == 1 ? Nulls.of(URI.class) : SOURCE,
                                        which == 2 ? Nulls.of(Path.class) : FILE,
                                        which == 3 ? Nulls.of(FileHashes.class) : EXPECTED,
                                        946_303,
                                        which == 4
                                                ? Nulls.of(Optional.class)
                                                : Optional.of(EXPECTED),
                                        946_303))
                .getMessage();
    }
}
