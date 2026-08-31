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

package org.cometgui.provenance.hashing;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link FileFingerprint}.
 *
 * <p><strong>Every attribute is isolated.</strong> {@code R-PROV-02} says the cache "shall be
 * invalidated by any attribute change", which is a claim about five separate attributes, so the
 * comparison tests below vary exactly one component at a time. A suite that only ever varied two
 * components together could not tell which comparison was doing the work, and would pass with one
 * of them deleted.
 *
 * <p><strong>Nothing here takes its expected value from the class under test.</strong> Sizes come
 * from the length of the fixture the test itself wrote; timestamps and file keys are read with
 * {@link Files} directly, which is the JDK rather than this project; and the timestamps used in the
 * settling tests are typed-out {@link Instant} literals.
 *
 * <p><strong>A stated platform assumption.</strong> Two tests assert that this host's default file
 * system publishes {@code unix:ctime} and a non-null file key, which is true on Linux and macOS and
 * false on Windows. That is deliberate, in the same spirit as the read-count assumption recorded
 * for {@code StreamingHashServiceTest}: the cache's usefulness depends on the platform publishing
 * them, so the platform is asserted rather than assumed. On Windows the honest expectation is the
 * one the zip file system stands in for below -- no identity, no inode change time, no caching.
 */
class FileFingerprintTest {

    /** A timestamp well inside a second, so truncation is visible. */
    private static final Instant HALF_PAST = Instant.parse("2026-08-30T10:15:30.500Z");

    /** The start of the second {@link #HALF_PAST} lies in. */
    private static final Instant TICK = Instant.parse("2026-08-30T10:15:30Z");

    /** A second earlier than {@link #TICK}. */
    private static final Instant EARLIER = Instant.parse("2026-08-30T10:15:29.250Z");

    /** A second later than {@link #TICK}. */
    private static final Instant LATER = Instant.parse("2026-08-30T10:15:31.750Z");

    /** A stand-in for a device/inode pair; only its equality behaviour matters here. */
    private static final Object IDENTITY_ONE = "(dev=33,ino=1001)";

    /** A different device/inode pair. */
    private static final Object IDENTITY_TWO = "(dev=33,ino=1002)";

    /**
     * A path standing in for a canonical one.
     *
     * <p>Assembled from segments rather than written as {@code "/data/spectra/one.mzML"} because
     * these tests are about comparison, not about any real file, and a hard-coded absolute path in
     * a test is a portability finding SpotBugs is right to raise.
     */
    private static final Path PATH_ONE = Path.of("data", "spectra", "one.mzML");

    /** A different path, for the comparison that must fail on the path alone. */
    private static final Path PATH_TWO = Path.of("data", "spectra", "two.mzML");

    /**
     * A {@code null} of whatever type the call site needs, by a route no analyser folds away.
     *
     * <p>The same helper as {@code SecretRedactorTest}: SpotBugs at effort Max reports a {@code
     * null} literal passed to a parameter that is dereferenced, and this repository fixes findings
     * rather than filtering them, so the {@code null} arrives through a value.
     *
     * @param <T> the type the call site needs
     * @return {@code null}
     */
    private static <T> T deliberateNull() {
        return Optional.<T>empty().orElse(null);
    }

    /** A fingerprint with everything present, for varying one component at a time. */
    private static FileFingerprint complete() {
        return new FileFingerprint(PATH_ONE, 3L, HALF_PAST, IDENTITY_ONE, HALF_PAST);
    }

    // -------------------------------------------------------------------------------------------
    // Group 1 -- "invalidated by any attribute change", one attribute at a time.
    // -------------------------------------------------------------------------------------------

    /** Each of the five components is load-bearing on its own. */
    @Nested
    @DisplayName("group 1: matches() varies with every single component")
    class Matching {

        @Test
        @DisplayName("two identical fingerprints match")
        void identicalFingerprintsMatch() {
            assertTrue(complete().matches(complete()), "identical fingerprints must match");
        }

        @Test
        @DisplayName("a different canonical path does not match")
        void aDifferentPathDoesNotMatch() {
            FileFingerprint other =
                    new FileFingerprint(PATH_TWO, 3L, HALF_PAST, IDENTITY_ONE, HALF_PAST);

            assertFalse(complete().matches(other), "a different path must not match");
        }

        @Test
        @DisplayName("a different size does not match")
        void aDifferentSizeDoesNotMatch() {
            FileFingerprint other =
                    new FileFingerprint(PATH_ONE, 4L, HALF_PAST, IDENTITY_ONE, HALF_PAST);

            assertFalse(complete().matches(other), "a different size must not match");
        }

        @Test
        @DisplayName("a different modification time does not match")
        void aDifferentModificationTimeDoesNotMatch() {
            FileFingerprint other =
                    new FileFingerprint(PATH_ONE, 3L, LATER, IDENTITY_ONE, HALF_PAST);

            assertFalse(complete().matches(other), "a different mtime must not match");
        }

        @Test
        @DisplayName("a different file identity does not match")
        void aDifferentFileIdentityDoesNotMatch() {
            FileFingerprint other =
                    new FileFingerprint(PATH_ONE, 3L, HALF_PAST, IDENTITY_TWO, HALF_PAST);

            assertFalse(complete().matches(other), "a different inode must not match");
        }

        @Test
        @DisplayName("a different inode change time does not match")
        void aDifferentInodeChangeTimeDoesNotMatch() {
            FileFingerprint other =
                    new FileFingerprint(PATH_ONE, 3L, HALF_PAST, IDENTITY_ONE, LATER);

            assertFalse(complete().matches(other), "a different ctime must not match");
        }

        @Test
        @DisplayName("a missing file identity does not match a present one")
        void aMissingFileIdentityDoesNotMatchAPresentOne() {
            FileFingerprint absent = new FileFingerprint(PATH_ONE, 3L, HALF_PAST, null, HALF_PAST);

            assertAll(
                    () -> assertFalse(complete().matches(absent), "present vs absent identity"),
                    () -> assertFalse(absent.matches(complete()), "absent vs present identity"),
                    () -> assertTrue(absent.matches(absent), "absent vs absent identity"));
        }

        @Test
        @DisplayName("a missing inode change time does not match a present one")
        void aMissingInodeChangeTimeDoesNotMatchAPresentOne() {
            FileFingerprint absent =
                    new FileFingerprint(PATH_ONE, 3L, HALF_PAST, IDENTITY_ONE, null);

            assertAll(
                    () -> assertFalse(complete().matches(absent), "present vs absent ctime"),
                    () -> assertFalse(absent.matches(complete()), "absent vs present ctime"),
                    () -> assertTrue(absent.matches(absent), "absent vs absent ctime"));
        }

        @Test
        @DisplayName("matching against null is a programming error, not a mismatch")
        void matchingAgainstNullThrows() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class, () -> complete().matches(deliberateNull()));

            assertEquals("other", thrown.getMessage());
        }
    }

    // -------------------------------------------------------------------------------------------
    // Group 2 -- tamper evidence.
    // -------------------------------------------------------------------------------------------

    /** Both unforgeable attributes are required, and each one is required separately. */
    @Nested
    @DisplayName("group 2: tamper evidence needs both unforgeable attributes")
    class TamperEvidence {

        @Test
        @DisplayName("identity and inode change time together are tamper-evident")
        void bothPresentIsTamperEvident() {
            assertTrue(complete().tamperEvident(), "both attributes present");
        }

        @Test
        @DisplayName("no file identity is not tamper-evident")
        void noIdentityIsNotTamperEvident() {
            assertFalse(
                    new FileFingerprint(PATH_ONE, 3L, HALF_PAST, null, HALF_PAST).tamperEvident(),
                    "a Windows-shaped fingerprint must not be trusted");
        }

        @Test
        @DisplayName("no inode change time is not tamper-evident")
        void noInodeChangeTimeIsNotTamperEvident() {
            assertFalse(
                    new FileFingerprint(PATH_ONE, 3L, HALF_PAST, IDENTITY_ONE, null)
                            .tamperEvident(),
                    "without ctime an in-place rewrite is invisible");
        }

        @Test
        @DisplayName("neither attribute is not tamper-evident")
        void neitherIsTamperEvident() {
            assertFalse(
                    new FileFingerprint(PATH_ONE, 3L, HALF_PAST, null, null).tamperEvident(),
                    "nothing to trust");
        }
    }

    // -------------------------------------------------------------------------------------------
    // Group 3 -- the settling rule, at whole-second boundaries.
    // -------------------------------------------------------------------------------------------

    /** An entry taken in the same tick as the file's last change is not trusted. */
    @Nested
    @DisplayName("group 3: settling is measured against the whole second")
    class Settling {

        @Test
        @DisplayName("timestamps in an earlier second are settled")
        void earlierSecondIsSettled() {
            FileFingerprint fingerprint =
                    new FileFingerprint(PATH_ONE, 3L, EARLIER, IDENTITY_ONE, EARLIER);

            assertTrue(fingerprint.settledBefore(HALF_PAST), "29.250 is before the 30 tick");
        }

        @Test
        @DisplayName("a modification time inside the observed second is not settled")
        void modificationTimeInTheSameSecondIsNotSettled() {
            FileFingerprint fingerprint =
                    new FileFingerprint(PATH_ONE, 3L, HALF_PAST, IDENTITY_ONE, EARLIER);

            assertFalse(fingerprint.settledBefore(HALF_PAST), "30.500 is not before the 30 tick");
        }

        @Test
        @DisplayName("an inode change time inside the observed second is not settled")
        void inodeChangeTimeInTheSameSecondIsNotSettled() {
            FileFingerprint fingerprint =
                    new FileFingerprint(PATH_ONE, 3L, EARLIER, IDENTITY_ONE, HALF_PAST);

            assertFalse(fingerprint.settledBefore(HALF_PAST), "ctime 30.500 is inside the tick");
        }

        @Test
        @DisplayName("a timestamp exactly on the tick is not settled")
        void aTimestampExactlyOnTheTickIsNotSettled() {
            FileFingerprint fingerprint =
                    new FileFingerprint(PATH_ONE, 3L, TICK, IDENTITY_ONE, TICK);

            assertFalse(
                    fingerprint.settledBefore(HALF_PAST),
                    "the comparison is strictly earlier, not earlier-or-equal");
        }

        @Test
        @DisplayName("a fingerprint with no inode change time is never settled")
        void noInodeChangeTimeIsNeverSettled() {
            FileFingerprint fingerprint =
                    new FileFingerprint(PATH_ONE, 3L, EARLIER, IDENTITY_ONE, null);

            assertFalse(fingerprint.settledBefore(LATER), "there is nothing to settle");
        }

        @Test
        @DisplayName("settling needs an observation instant")
        void settlingNeedsAnInstant() {
            assertThrows(
                    NullPointerException.class, () -> complete().settledBefore(deliberateNull()));
        }
    }

    // -------------------------------------------------------------------------------------------
    // Group 4 -- the two rules together.
    // -------------------------------------------------------------------------------------------

    /** An entry is worth keeping only when it is both tamper-evident and settled. */
    @Nested
    @DisplayName("group 4: trustworthiness needs both rules")
    class Trustworthiness {

        @Test
        @DisplayName("tamper-evident and settled is trustworthy")
        void tamperEvidentAndSettledIsTrustworthy() {
            FileFingerprint fingerprint =
                    new FileFingerprint(PATH_ONE, 3L, EARLIER, IDENTITY_ONE, EARLIER);

            assertTrue(fingerprint.trustworthyAt(LATER));
        }

        @Test
        @DisplayName("settled but not tamper-evident is not trustworthy")
        void settledButNotTamperEvidentIsNotTrustworthy() {
            FileFingerprint fingerprint = new FileFingerprint(PATH_ONE, 3L, EARLIER, null, EARLIER);

            assertFalse(fingerprint.trustworthyAt(LATER), "no identity, no trust");
        }

        @Test
        @DisplayName("tamper-evident but unsettled is not trustworthy")
        void tamperEvidentButUnsettledIsNotTrustworthy() {
            FileFingerprint fingerprint =
                    new FileFingerprint(PATH_ONE, 3L, HALF_PAST, IDENTITY_ONE, HALF_PAST);

            assertFalse(fingerprint.trustworthyAt(HALF_PAST), "same tick, no trust");
        }
    }

    // -------------------------------------------------------------------------------------------
    // Group 5 -- reading a real file, and a real file system that has no identity to offer.
    // -------------------------------------------------------------------------------------------

    /** What {@link FileFingerprint#of} actually reads. */
    @Nested
    @DisplayName("group 5: reading attributes from real file systems")
    class Reading {

        @Test
        @DisplayName("on this host the default file system publishes ctime and a file key")
        void theDefaultFileSystemPublishesEverything(@TempDir Path dir) throws IOException {
            Path file = writeAbc(dir);
            BasicFileAttributes basic = Files.readAttributes(file, BasicFileAttributes.class);
            Instant ctime = ((FileTime) Files.getAttribute(file, "unix:ctime")).toInstant();

            FileFingerprint fingerprint = FileFingerprint.of(file);

            assertAll(
                    () ->
                            assertTrue(
                                    dir.getFileSystem()
                                            .supportedFileAttributeViews()
                                            .contains(FileFingerprint.UNIX_VIEW),
                                    "this platform is expected to publish the unix view"),
                    () -> assertEquals(file, fingerprint.canonicalPath(), "canonical path"),
                    () -> assertEquals(3L, fingerprint.size(), "the fixture is three bytes"),
                    () ->
                            assertEquals(
                                    Files.getLastModifiedTime(file).toInstant(),
                                    fingerprint.lastModifiedAt(),
                                    "modification time"),
                    () ->
                            assertEquals(
                                    basic.fileKey(),
                                    fingerprint.fileIdentity(),
                                    "device and inode"),
                    () -> assertEquals(ctime, fingerprint.inodeChangedAt(), "inode change time"),
                    () -> assertTrue(fingerprint.tamperEvident(), "tamper-evident here"));
        }

        @Test
        @DisplayName("a zip file system publishes no file identity, so nothing may be trusted")
        void aZipFileSystemHasNoIdentity(@TempDir Path dir) throws IOException {
            Path zip = dir.resolve("archive.zip");
            try (FileSystem archive = FileSystems.newFileSystem(zip, Map.of("create", "true"))) {
                Files.write(archive.getPath("/entry.bin"), "abc".getBytes(US_ASCII));
            }
            try (FileSystem archive = FileSystems.newFileSystem(zip)) {
                Path entry = archive.getPath("/entry.bin");

                FileFingerprint fingerprint = FileFingerprint.of(entry);

                assertAll(
                        () ->
                                assertFalse(
                                        archive.supportedFileAttributeViews()
                                                .contains(FileFingerprint.UNIX_VIEW),
                                        "a zip file system has no unix view"),
                        () -> assertEquals(3L, fingerprint.size(), "size still readable"),
                        () -> assertNull(fingerprint.fileIdentity(), "no device or inode"),
                        () -> assertNull(fingerprint.inodeChangedAt(), "no inode change time"),
                        () ->
                                assertFalse(
                                        fingerprint.tamperEvident(),
                                        "absence must reduce trust, never raise it"),
                        () ->
                                assertFalse(
                                        fingerprint.trustworthyAt(Instant.now()),
                                        "and so it can never be kept"));
            }
        }

        @Test
        @DisplayName("the basic attributes alone carry no inode change time")
        void basicAttributesCarryNoInodeChangeTime(@TempDir Path dir) throws IOException {
            Path file = writeAbc(dir);
            BasicFileAttributes basic = Files.readAttributes(file, BasicFileAttributes.class);

            FileFingerprint fingerprint = FileFingerprint.fromBasicAttributes(file, basic);

            assertAll(
                    () -> assertEquals(3L, fingerprint.size(), "size"),
                    () ->
                            assertEquals(
                                    basic.lastModifiedTime().toInstant(),
                                    fingerprint.lastModifiedAt(),
                                    "modification time"),
                    () -> assertNull(fingerprint.inodeChangedAt(), "no ctime in the basic view"),
                    () -> assertFalse(fingerprint.tamperEvident(), "so it is not tamper-evident"));
        }

        @Test
        @DisplayName("the unix attribute map carries all five components")
        void unixAttributesCarryEverything(@TempDir Path dir) throws IOException {
            Path file = writeAbc(dir);
            Map<String, Object> attributes =
                    Files.readAttributes(file, "unix:size,lastModifiedTime,fileKey,ctime");

            FileFingerprint fingerprint = FileFingerprint.fromUnixAttributes(file, attributes);

            assertAll(
                    () -> assertEquals(file, fingerprint.canonicalPath(), "path"),
                    () -> assertEquals(3L, fingerprint.size(), "size"),
                    () ->
                            assertEquals(
                                    ((FileTime) attributes.get("lastModifiedTime")).toInstant(),
                                    fingerprint.lastModifiedAt(),
                                    "modification time"),
                    () ->
                            assertEquals(
                                    attributes.get("fileKey"),
                                    fingerprint.fileIdentity(),
                                    "device and inode"),
                    () ->
                            assertEquals(
                                    ((FileTime) attributes.get("ctime")).toInstant(),
                                    fingerprint.inodeChangedAt(),
                                    "inode change time"),
                    () -> assertTrue(fingerprint.tamperEvident(), "tamper-evident"));
        }

        @Test
        @DisplayName("a file that is not there cannot be fingerprinted")
        void aMissingFileCannotBeFingerprinted(@TempDir Path dir) {
            assertThrows(IOException.class, () -> FileFingerprint.of(dir.resolve("absent.bin")));
        }

        @Test
        @DisplayName("a null path is rejected")
        void aNullPathIsRejected() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class, () -> FileFingerprint.of(deliberateNull()));

            assertEquals("canonicalPath", thrown.getMessage());
        }
    }

    // -------------------------------------------------------------------------------------------
    // Group 6 -- the record's own contract.
    // -------------------------------------------------------------------------------------------

    /** The components that may never be absent, and the generated members. */
    @Nested
    @DisplayName("group 6: the record's own contract")
    class RecordContract {

        @Test
        @DisplayName("a canonical path is required")
        void aCanonicalPathIsRequired() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () ->
                                    new FileFingerprint(
                                            deliberateNull(),
                                            3L,
                                            HALF_PAST,
                                            IDENTITY_ONE,
                                            HALF_PAST));

            assertEquals("canonicalPath", thrown.getMessage());
        }

        @Test
        @DisplayName("a modification time is required")
        void aModificationTimeIsRequired() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () ->
                                    new FileFingerprint(
                                            PATH_ONE,
                                            3L,
                                            deliberateNull(),
                                            IDENTITY_ONE,
                                            HALF_PAST));

            assertEquals("lastModifiedAt", thrown.getMessage());
        }

        @Test
        @DisplayName("equal components make equal fingerprints")
        void equalComponentsMakeEqualFingerprints() {
            assertAll(
                    () -> assertEquals(complete(), complete(), "equal"),
                    () ->
                            assertEquals(
                                    complete().hashCode(), complete().hashCode(), "same hash code"),
                    () ->
                            assertNotEquals(
                                    complete(),
                                    new FileFingerprint(
                                            PATH_ONE, 4L, HALF_PAST, IDENTITY_ONE, HALF_PAST),
                                    "a different size is a different fingerprint"),
                    () ->
                            assertTrue(
                                    complete().toString().contains("one.mzML"),
                                    "toString names the file: " + complete()));
        }
    }

    /**
     * Writes the three-byte fixture whose digests are pinned in the cache tests.
     *
     * @param dir the directory to write into
     * @return the file
     * @throws IOException if it cannot be written
     */
    private static Path writeAbc(Path dir) throws IOException {
        Path file = dir.resolve("fixture.bin");
        Files.write(file, "abc".getBytes(US_ASCII));
        return file;
    }
}
