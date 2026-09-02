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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link CachingHashService}, the input-hash cache of {@code R-PROV-02}.
 *
 * <p><strong>Where the expected digests came from.</strong> Every digest literal below was produced
 * on the command line by two implementations that share no code with each other or with CometGUI --
 * GNU coreutils {@code md5sum}/{@code sha256sum} and {@code openssl dgst} -- which agreed, and was
 * then typed in here. Nothing in this file asks the code under test what the answer should be. That
 * matters more here than anywhere: the whole point of a cache test is to catch a digest that
 * belongs to content the file no longer holds, and an expected value computed by the hasher would
 * agree with whatever was returned.
 *
 * <p><strong>Two witnesses to every count.</strong> "The cache caches" and "the cache did not serve
 * a stale entry" are claims about numbers, so every test that makes one asserts the number twice
 * over: {@link CountingHashService} counts the calls that reached the real hasher, which is
 * evidence from outside the class, and {@link CachingHashService#hitCount()} and {@link
 * CachingHashService#missCount()} are the class's own account of itself. A cache that never hits is
 * safe and useless, and only the counts can tell the two apart.
 *
 * <p><strong>The headline cases run through the public constructor.</strong> Group 1 uses {@code
 * new CachingHashService(delegate)} against real files on the real file system with the real system
 * clock -- no seam, no substituted reader, no substituted clock -- because a property proved only
 * through a seam is a property the production path is free not to have. The seams appear later, and
 * only for the two things this host cannot otherwise show: a platform that publishes no file
 * identity, and the exact moment a clock crosses a second.
 *
 * <p><strong>A stated platform assumption.</strong> {@link #awaitSettled(Path)} reads {@code
 * unix:ctime}, so the tests that use it assume a POSIX file system, as the cache's usefulness does.
 * On Windows the expected behaviour is the zip-file-system case in group 3: no identity, no inode
 * change time, no caching, correct digests.
 */
class CachingHashServiceTest {

    /** MD5 of {@code "abc"} -- RFC 1321, and {@code md5sum} agrees. */
    private static final String MD5_ABC = "900150983cd24fb0d6963f7d28e17f72";

    /** SHA-256 of {@code "abc"} -- FIPS 180-4 worked example. */
    private static final String SHA256_ABC =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    /** MD5 of {@code "xyz"}, the same-length replacement for {@code "abc"}. */
    private static final String MD5_XYZ = "d16fb36f0911f878998c136191af705e";

    /** SHA-256 of {@code "xyz"}. */
    private static final String SHA256_XYZ =
            "3608bca1e44ea6c4d268eb6db02260269892c0b42b86bbf1e77a6fa16c3c9282";

    /** MD5 of {@code "abcd"}, one byte longer than {@code "abc"}. */
    private static final String MD5_ABCD = "e2fc714c4727ee9395f324cd2e7f331f";

    /** SHA-256 of {@code "abcd"}. */
    private static final String SHA256_ABCD =
            "88d4266fd4e6338d13b845fcf289579d209c897823b9217da3e161936f031589";

    /** MD5 of {@code "def"}. */
    private static final String MD5_DEF = "4ed9407630eb1000c0f6b63842defa7d";

    /** SHA-256 of {@code "def"}. */
    private static final String SHA256_DEF =
            "cb8379ac2098aa165029e3938a51da0bcecfc008fd6795f401178647f96c5b34";

    /** MD5 of {@code "ghi"}. */
    private static final String MD5_GHI = "826bbc5d0522f5f20a1da4b60fa8c871";

    /** SHA-256 of {@code "ghi"}. */
    private static final String SHA256_GHI =
            "50ae61e841fac4e8f9e40baf2ad36ec868922ea48368c18f9535e47db56dd7fb";

    /** MD5 of {@code "jkl"}. */
    private static final String MD5_JKL = "699a474e923b8da5d7aefbfc54a8a2bd";

    /** SHA-256 of {@code "jkl"}. */
    private static final String SHA256_JKL =
            "268f277c6d766d31334fda0f7a5533a185598d269e61c76a805870244828a5f1";

    /** MD5 of {@code "mno"}. */
    private static final String MD5_MNO = "d1cf6a6090003989122c4483ed135d55";

    /** SHA-256 of {@code "mno"}. */
    private static final String SHA256_MNO =
            "cf63b8eb216845d24edd4b249b146957b42199cd12759647df90cb57525b4e90";

    /**
     * The bound {@link CachingHashService#DEFAULT_MAXIMUM_ENTRIES} is expected to hold, typed out
     * rather than read from the class, so that changing the constant fails a test instead of
     * changing the expectation with it.
     */
    private static final int DEFAULT_BOUND = 256;

    // ===========================================================================================
    // Group 1 -- the real thing: public constructor, real file system, real clock.
    // ===========================================================================================

    /** Everything the requirement is actually about, with no seam anywhere in the path. */
    @Nested
    @DisplayName("group 1: through the public constructor, on real files, with the real clock")
    class ThroughThePublicConstructor {

        @Test
        @DisplayName("a second hash of an unchanged file is served from the cache")
        void anUnchangedFileIsServedFromTheCache(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            awaitSettled(file);
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = new CachingHashService(delegate);

            FileHashes first = service.hash(file);
            FileHashes second = service.hash(file);

            assertAll(
                    () -> assertHashes(first, MD5_ABC, SHA256_ABC, "first call"),
                    () -> assertHashes(second, MD5_ABC, SHA256_ABC, "second call"),
                    () -> assertEquals(1, delegate.callCount(), "calls that reached the hasher"),
                    () -> assertEquals(1L, service.hitCount(), "hits"),
                    () -> assertEquals(1L, service.missCount(), "misses"),
                    () -> assertEquals(1, service.size(), "entries held"));
        }

        @Test
        @DisplayName(
                "GATE ITEM 3: content replaced in place, same size, same mtime, same inode -- the"
                        + " recorded hash is of the content actually there")
        void contentReplacedWithSameSizeAndSameModificationTimeIsRehashed(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            awaitSettled(file);
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = new CachingHashService(delegate);

            // Populate, and prove the entry is live before relying on it being defeated.
            assertHashes(service.hash(file), MD5_ABC, SHA256_ABC, "populating call");
            assertHashes(service.hash(file), MD5_ABC, SHA256_ABC, "served from the cache");
            assertEquals(1, delegate.callCount(), "the entry must be live before the mutation");

            long sizeBefore = Files.size(file);
            FileTime modifiedBefore = Files.getLastModifiedTime(file);
            Object identityBefore = Files.readAttributes(file, "unix:fileKey").get("fileKey");
            overwriteInPlaceKeepingSizeAndTime(file, "xyz");

            FileHashes after = service.hash(file);

            assertAll(
                    () -> assertEquals("xyz", Files.readString(file), "the fixture really changed"),
                    () -> assertEquals(sizeBefore, Files.size(file), "the size must be unchanged"),
                    () ->
                            assertEquals(
                                    modifiedBefore,
                                    Files.getLastModifiedTime(file),
                                    "the modification time must be unchanged"),
                    () ->
                            assertEquals(
                                    identityBefore,
                                    Files.readAttributes(file, "unix:fileKey").get("fileKey"),
                                    "the inode must be unchanged"),
                    () -> assertHashes(after, MD5_XYZ, SHA256_XYZ, "the hash of what is there now"),
                    () ->
                            assertNotEquals(
                                    MD5_ABC, after.md5(), "not the digest of the old content"),
                    () -> assertEquals(2, delegate.callCount(), "the file was read again"),
                    () -> assertEquals(1L, service.hitCount(), "the one hit was before the change"),
                    () -> assertEquals(2L, service.missCount(), "misses"));
        }

        @Test
        @DisplayName("content changed with a different size is rehashed")
        void aDifferentSizeIsRehashed(@TempDir Path root) throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            awaitSettled(file);
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = new CachingHashService(delegate);
            service.hash(file);
            service.hash(file);
            assertEquals(1, delegate.callCount(), "the entry must be live before the mutation");

            Files.write(file, "abcd".getBytes(US_ASCII));
            FileHashes after = service.hash(file);

            assertAll(
                    () -> assertHashes(after, MD5_ABCD, SHA256_ABCD, "the four-byte content"),
                    () -> assertEquals(2, delegate.callCount(), "the file was read again"),
                    () -> assertEquals(1L, service.hitCount(), "hits"));
        }

        @Test
        @DisplayName("content changed with a new modification time is rehashed")
        void aNewModificationTimeIsRehashed(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            awaitSettled(file);
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = new CachingHashService(delegate);
            service.hash(file);
            service.hash(file);
            assertEquals(1, delegate.callCount(), "the entry must be live before the mutation");

            Files.write(file, "def".getBytes(US_ASCII));
            FileHashes after = service.hash(file);

            assertAll(
                    () -> assertHashes(after, MD5_DEF, SHA256_DEF, "the new content"),
                    () -> assertEquals(2, delegate.callCount(), "the file was read again"),
                    () -> assertEquals(1L, service.hitCount(), "hits"));
        }

        @Test
        @DisplayName(
                "the same path holding a different file is rehashed, even with size and time"
                        + " restored")
        void aReplacedFileIsRehashed(@TempDir Path root) throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            awaitSettled(file);
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = new CachingHashService(delegate);
            service.hash(file);
            service.hash(file);
            assertEquals(1, delegate.callCount(), "the entry must be live before the mutation");
            FileTime modifiedBefore = Files.getLastModifiedTime(file);
            Object identityBefore = Files.readAttributes(file, "unix:fileKey").get("fileKey");

            // Moved onto the path rather than deleted and rewritten: a delete and rewrite gets the
            // same inode back from this file system often enough to make the fixture a lie, and
            // "the same path now holds a different file" is what the test is about.
            Path replacement = write(dir, "replacement.tmp", "ghi");
            Files.move(replacement, file, StandardCopyOption.REPLACE_EXISTING);
            Files.setLastModifiedTime(file, modifiedBefore);
            FileHashes after = service.hash(file);

            assertAll(
                    () ->
                            assertNotEquals(
                                    identityBefore,
                                    Files.readAttributes(file, "unix:fileKey").get("fileKey"),
                                    "the fixture must really have a new inode"),
                    () ->
                            assertEquals(
                                    modifiedBefore,
                                    Files.getLastModifiedTime(file),
                                    "with the modification time put back"),
                    () -> assertHashes(after, MD5_GHI, SHA256_GHI, "the new file's content"),
                    () -> assertEquals(2, delegate.callCount(), "the file was read again"));
        }

        @Test
        @DisplayName("a path that becomes a directory is not answered from the cache")
        void aPathThatBecomesADirectoryIsNotServed(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            awaitSettled(file);
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = new CachingHashService(delegate);
            service.hash(file);
            service.hash(file);
            assertEquals(1, service.size(), "the entry must be live before the mutation");

            Files.delete(file);
            Files.createDirectory(file);

            IOException thrown = assertThrows(IOException.class, () -> service.hash(file));

            assertAll(
                    () ->
                            assertTrue(
                                    thrown.getMessage().contains("Cannot hash a directory"),
                                    "the delegate's own refusal: " + thrown.getMessage()),
                    () -> assertEquals(0, service.size(), "the stale entry was dropped"),
                    () -> assertEquals(2, delegate.callCount(), "the delegate was asked"));
        }

        @Test
        @DisplayName("a deleted file fails, and takes its entry with it")
        void aDeletedFileFails(@TempDir Path root) throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            awaitSettled(file);
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = new CachingHashService(delegate);
            service.hash(file);
            assertEquals(1, service.size(), "the entry must be live before the deletion");

            Files.delete(file);

            assertAll(
                    () ->
                            assertThrows(
                                    NoSuchFileException.class,
                                    () -> service.hash(file),
                                    "the delegate's own failure for a missing file"),
                    () -> assertEquals(0, service.size(), "the stale entry was dropped"),
                    () -> assertEquals(2, delegate.callCount(), "the delegate was asked"));
        }

        @Test
        @DisplayName("a symbolic link and its target are one entry, because the key is canonical")
        void aSymbolicLinkSharesTheTargetsEntry(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            Path link = dir.resolve("link.mzML");
            Files.createSymbolicLink(link, file);
            awaitSettled(file);
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = new CachingHashService(delegate);

            FileHashes viaFile = service.hash(file);
            FileHashes viaLink = service.hash(link);
            FileHashes viaDotSegments = service.hash(dir.resolve(".").resolve("spectra.mzML"));

            assertAll(
                    () -> assertHashes(viaFile, MD5_ABC, SHA256_ABC, "via the file"),
                    () -> assertHashes(viaLink, MD5_ABC, SHA256_ABC, "via the link"),
                    () -> assertHashes(viaDotSegments, MD5_ABC, SHA256_ABC, "via ./"),
                    () -> assertEquals(1, delegate.callCount(), "one read for three names"),
                    () -> assertEquals(2L, service.hitCount(), "two hits"),
                    () -> assertEquals(1, service.size(), "one entry for three names"));
        }
    }

    // ===========================================================================================
    // Group 2 -- revalidation is not optional, and it is countable.
    // ===========================================================================================

    /** "Revalidated on every use" as a number: no lookup is answered without a fresh read. */
    @Nested
    @DisplayName("group 2: every use re-reads the attributes")
    class Revalidation {

        @Test
        @DisplayName("a hit costs an attribute read, so nothing is served from the key alone")
        void everyCallReadsTheAttributes(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            CountingHashService delegate = new CountingHashService();
            CountingFingerprintReader reader = new CountingFingerprintReader();
            CachingHashService service =
                    new CachingHashService(delegate, DEFAULT_BOUND, true, reader, aheadByAnHour());

            service.hash(file);
            int afterFirst = reader.readCount();
            service.hash(file);
            service.hash(file);

            assertAll(
                    () -> assertEquals(2, afterFirst, "a miss reads before and after the hash"),
                    () -> assertEquals(4, reader.readCount(), "each later hit reads once more"),
                    () -> assertEquals(1, delegate.callCount(), "and the hasher ran once"),
                    () -> assertEquals(2L, service.hitCount(), "hits"));
        }

        @Test
        @DisplayName("a file whose attributes cannot be read is hashed anyway, and never cached")
        void attributesThatCannotBeReadStillHash(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            CountingHashService delegate = new CountingHashService();
            CachingHashService service =
                    new CachingHashService(
                            delegate,
                            DEFAULT_BOUND,
                            true,
                            path -> {
                                throw new IOException("attributes unavailable: " + path);
                            },
                            aheadByAnHour());

            FileHashes first = service.hash(file);
            FileHashes second = service.hash(file);

            assertAll(
                    () -> assertHashes(first, MD5_ABC, SHA256_ABC, "first call"),
                    () -> assertHashes(second, MD5_ABC, SHA256_ABC, "second call"),
                    () -> assertEquals(2, delegate.callCount(), "the hasher answered both"),
                    () -> assertEquals(0, service.size(), "and nothing was remembered"),
                    () ->
                            assertEquals(
                                    0L,
                                    service.hitCount(),
                                    "a cache that cannot see the file"
                                            + " is a pass-through, never a failure"));
        }

        @Test
        @DisplayName(
                "the attributes read after the hash decide, so a file changed under the read"
                        + " is not remembered")
        void aFileChangedUnderTheReadIsNotRemembered(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            // The delegate hashes "abc" and then makes the file four bytes long, exactly as a
            // writer racing the hasher would.
            CountingHashService delegate =
                    new CountingHashService(
                            hashed -> Files.write(hashed, "abcd".getBytes(US_ASCII)));
            CachingHashService service =
                    new CachingHashService(
                            delegate, DEFAULT_BOUND, true, FileFingerprint::of, aheadByAnHour());

            FileHashes first = service.hash(file);

            assertAll(
                    () -> assertHashes(first, MD5_ABC, SHA256_ABC, "what was read is what returns"),
                    () -> assertEquals(0, service.size(), "nothing was remembered"),
                    () -> assertEquals(0L, service.hitCount(), "and nothing can be served"));
        }

        @Test
        @DisplayName("a file that disappears under the read is not remembered")
        void aFileThatDisappearsUnderTheReadIsNotRemembered(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            CountingHashService delegate = new CountingHashService(Files::delete);
            CachingHashService service =
                    new CachingHashService(
                            delegate, DEFAULT_BOUND, true, FileFingerprint::of, aheadByAnHour());

            FileHashes first = service.hash(file);

            assertAll(
                    () -> assertHashes(first, MD5_ABC, SHA256_ABC, "the content that was read"),
                    () -> assertEquals(0, service.size(), "nothing was remembered"),
                    () -> assertFalse(Files.exists(file), "the fixture really went away"));
        }
    }

    // ===========================================================================================
    // Group 3 -- platforms that cannot prove a file is unchanged.
    // ===========================================================================================

    /** Where the unforgeable attributes are missing, the cache stops being a cache. */
    @Nested
    @DisplayName("group 3: no file identity or no inode change time means no caching")
    class WithoutTamperEvidence {

        @Test
        @DisplayName("a zip file system, which publishes neither, is never cached")
        void aZipFileSystemIsNeverCached(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path zip = dir.resolve("archive.zip");
            try (FileSystem archive = FileSystems.newFileSystem(zip, Map.of("create", "true"))) {
                Files.write(archive.getPath("/entry.bin"), "abc".getBytes(US_ASCII));
            }
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = new CachingHashService(delegate);

            try (FileSystem archive = FileSystems.newFileSystem(zip)) {
                Path entry = archive.getPath("/entry.bin");

                FileHashes first = service.hash(entry);
                FileHashes second = service.hash(entry);

                assertAll(
                        () -> assertHashes(first, MD5_ABC, SHA256_ABC, "first call"),
                        () -> assertHashes(second, MD5_ABC, SHA256_ABC, "second call"),
                        () ->
                                assertEquals(
                                        2, delegate.callCount(), "both calls reached the hasher"),
                        () -> assertEquals(0L, service.hitCount(), "no hits are possible"),
                        () -> assertEquals(0, service.size(), "and nothing is remembered"));
            }
        }

        @Test
        @DisplayName("a platform with inodes but no inode change time is never cached")
        void noInodeChangeTimeMeansNoCaching(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            CountingHashService delegate = new CountingHashService();
            CachingHashService service =
                    new CachingHashService(
                            delegate,
                            DEFAULT_BOUND,
                            true,
                            withoutInodeChangeTime(),
                            aheadByAnHour());

            service.hash(file);
            service.hash(file);

            assertAll(
                    () -> assertEquals(2, delegate.callCount(), "both calls reached the hasher"),
                    () -> assertEquals(0, service.size(), "nothing is remembered"));
        }

        @Test
        @DisplayName("a platform with timestamps but no file identity is never cached")
        void noFileIdentityMeansNoCaching(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            CountingHashService delegate = new CountingHashService();
            CachingHashService service =
                    new CachingHashService(
                            delegate, DEFAULT_BOUND, true, withoutFileIdentity(), aheadByAnHour());

            service.hash(file);
            service.hash(file);

            assertAll(
                    () -> assertEquals(2, delegate.callCount(), "both calls reached the hasher"),
                    () -> assertEquals(0, service.size(), "nothing is remembered"));
        }
    }

    // ===========================================================================================
    // Group 4 -- the settling rule.
    // ===========================================================================================

    /** An entry taken in the same second as the file's last change is not kept. */
    @Nested
    @DisplayName("group 4: a file that changed in this tick is not cached")
    class Settling {

        @Test
        @DisplayName("an inode change inside the observed second prevents caching")
        void anInodeChangeInTheObservedSecondPreventsCaching(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            // Modification time firmly in the past; the inode change time is now, because setting
            // the modification time is itself an inode change.
            Files.setLastModifiedTime(
                    file, FileTime.from(Instant.now().minus(Duration.ofHours(1))));
            Instant inodeChangedAt = inodeChangeTime(file);
            CountingHashService delegate = new CountingHashService();
            CachingHashService service =
                    new CachingHashService(
                            delegate,
                            DEFAULT_BOUND,
                            true,
                            FileFingerprint::of,
                            fixedInsideTheSecondOf(inodeChangedAt));

            service.hash(file);
            service.hash(file);

            assertAll(
                    () -> assertEquals(2, delegate.callCount(), "both calls reached the hasher"),
                    () -> assertEquals(0, service.size(), "nothing was kept"),
                    () -> assertEquals(0L, service.hitCount(), "no hits"));
        }

        @Test
        @DisplayName("a modification time that is not in the past prevents caching")
        void aFutureModificationTimePreventsCaching(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            Files.setLastModifiedTime(file, FileTime.from(Instant.now().plus(Duration.ofDays(1))));
            CountingHashService delegate = new CountingHashService();
            CachingHashService service =
                    new CachingHashService(
                            delegate, DEFAULT_BOUND, true, FileFingerprint::of, aheadByAnHour());

            service.hash(file);
            service.hash(file);

            assertAll(
                    () -> assertEquals(2, delegate.callCount(), "both calls reached the hasher"),
                    () -> assertEquals(0, service.size(), "nothing was kept"));
        }

        @Test
        @DisplayName("once the tick has passed, the same file is cached")
        void onceTheTickHasPassedTheFileIsCached(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            CountingHashService delegate = new CountingHashService();
            CachingHashService service =
                    new CachingHashService(
                            delegate, DEFAULT_BOUND, true, FileFingerprint::of, aheadByAnHour());

            service.hash(file);
            service.hash(file);

            assertAll(
                    () -> assertEquals(1, delegate.callCount(), "one read"),
                    () -> assertEquals(1L, service.hitCount(), "one hit"),
                    () -> assertEquals(1, service.size(), "one entry"));
        }
    }

    // ===========================================================================================
    // Group 5 -- the bound, and which entry goes.
    // ===========================================================================================

    /** A map keyed on path with no bound is a leak; eviction is least-recently-used. */
    @Nested
    @DisplayName("group 5: bounded, with deterministic eviction")
    class Bounded {

        @Test
        @DisplayName("the default bound is 256 entries")
        void theDefaultBoundIs256() {
            CachingHashService service = new CachingHashService(new CountingHashService());

            assertAll(
                    () -> assertEquals(256, CachingHashService.DEFAULT_MAXIMUM_ENTRIES, "constant"),
                    () -> assertEquals(256, service.maximumEntries(), "the instance's bound"),
                    () -> assertTrue(service.enabled(), "and it is switched on"));
        }

        @Test
        @DisplayName("a bound below one is rejected")
        void aBoundBelowOneIsRejected() {
            HashService delegate = new CountingHashService();

            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new CachingHashService(delegate, 0));

            assertEquals("maximumEntries must be at least 1, but was: 0", thrown.getMessage());
        }

        @Test
        @DisplayName("filling exactly to the bound evicts nothing")
        void fillingExactlyToTheBoundEvictsNothing(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path one = write(dir, "one.mzML", "abc");
            Path two = write(dir, "two.mzML", "def");
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = boundedTo(2, delegate);

            service.hash(one);
            service.hash(two);
            FileHashes oneAgain = service.hash(one);
            FileHashes twoAgain = service.hash(two);

            assertAll(
                    () -> assertHashes(oneAgain, MD5_ABC, SHA256_ABC, "one is still cached"),
                    () -> assertHashes(twoAgain, MD5_DEF, SHA256_DEF, "two is still cached"),
                    () -> assertEquals(2, delegate.callCount(), "two reads for four calls"),
                    () -> assertEquals(2L, service.hitCount(), "two hits"),
                    () -> assertEquals(2, service.size(), "two entries"));
        }

        @Test
        @DisplayName("the least recently used entry is the one evicted")
        void theLeastRecentlyUsedEntryIsEvicted(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path one = write(dir, "one.mzML", "abc");
            Path two = write(dir, "two.mzML", "def");
            Path three = write(dir, "three.mzML", "ghi");
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = boundedTo(2, delegate);

            service.hash(one);
            service.hash(two);
            service.hash(one); // a hit, which makes "two" the least recently used
            service.hash(three); // over the bound: "two" goes
            int afterThree = delegate.callCount();
            FileHashes oneAgain = service.hash(one);
            int afterOne = delegate.callCount();
            FileHashes twoAgain = service.hash(two);

            assertAll(
                    () -> assertEquals(2, service.size(), "still bounded at two"),
                    () -> assertEquals(3, afterThree, "three files, three reads"),
                    () -> assertHashes(oneAgain, MD5_ABC, SHA256_ABC, "one survived"),
                    () -> assertEquals(3, afterOne, "and was served without a read"),
                    () -> assertHashes(twoAgain, MD5_DEF, SHA256_DEF, "two was evicted"),
                    () -> assertEquals(4, delegate.callCount(), "so two had to be read again"));
        }

        @Test
        @DisplayName("a bound of one keeps only the newest entry")
        void aBoundOfOneKeepsOnlyTheNewest(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path one = write(dir, "one.mzML", "abc");
            Path two = write(dir, "two.mzML", "def");
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = boundedTo(1, delegate);

            service.hash(one);
            service.hash(two);
            service.hash(one);

            assertAll(
                    () -> assertEquals(1, service.size(), "one entry at a time"),
                    () -> assertEquals(3, delegate.callCount(), "so nothing is ever served"),
                    () -> assertEquals(0L, service.hitCount(), "no hits"));
        }
    }

    // ===========================================================================================
    // Group 6 -- the two bypasses R-PROV-02 requires.
    // ===========================================================================================

    /** One file at a time, or the whole cache. */
    @Nested
    @DisplayName("group 6: bypass, per file and per run")
    class Bypass {

        @Test
        @DisplayName("rehash reads the file again even when a live entry says it need not")
        void rehashAlwaysReadsTheFile(@TempDir Path root) throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = boundedTo(DEFAULT_BOUND, delegate);
            service.hash(file);
            service.hash(file);
            assertEquals(1, delegate.callCount(), "the entry must be live before the bypass");

            FileHashes forced = service.rehash(file);
            FileHashes afterwards = service.hash(file);

            assertAll(
                    () -> assertHashes(forced, MD5_ABC, SHA256_ABC, "the forced read"),
                    () -> assertEquals(2, delegate.callCount(), "the bypass read the file"),
                    () -> assertHashes(afterwards, MD5_ABC, SHA256_ABC, "and the entry is back"),
                    () -> assertEquals(2, delegate.callCount(), "so the next call is served"),
                    () -> assertEquals(2L, service.hitCount(), "hits, one before and one after"));
        }

        @Test
        @DisplayName("rehash returns the content that is there now, not what was remembered")
        void rehashReturnsTheContentThatIsThereNow(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = boundedTo(DEFAULT_BOUND, delegate);
            service.hash(file);
            overwriteInPlaceKeepingSizeAndTime(file, "xyz");

            FileHashes forced = service.rehash(file);

            assertAll(
                    () -> assertHashes(forced, MD5_XYZ, SHA256_XYZ, "the content now"),
                    () -> assertEquals(2, delegate.callCount(), "read again"));
        }

        @Test
        @DisplayName("a disabled cache never stores and never serves")
        void aDisabledCacheNeverStores(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            awaitSettled(file);
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = CachingHashService.disabled(delegate);

            FileHashes first = service.hash(file);
            service.hash(file);
            service.rehash(file);

            assertAll(
                    () -> assertHashes(first, MD5_ABC, SHA256_ABC, "still correct"),
                    () -> assertFalse(service.enabled(), "switched off"),
                    () -> assertEquals(3, delegate.callCount(), "every call reached the hasher"),
                    () -> assertEquals(0L, service.hitCount(), "no hits"),
                    () -> assertEquals(3L, service.missCount(), "three misses"),
                    () -> assertEquals(0, service.size(), "nothing held"));
        }

        @Test
        @DisplayName("invalidate forgets one file and says whether it knew it")
        void invalidateForgetsOneFile(@TempDir Path root) throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            Path other = write(dir, "other.mzML", "jkl");
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = boundedTo(DEFAULT_BOUND, delegate);
            service.hash(file);
            service.hash(other);

            boolean removed = service.invalidate(file);
            boolean removedAgain = service.invalidate(file);
            FileHashes afterwards = service.hash(file);
            FileHashes untouched = service.hash(other);

            assertAll(
                    () -> assertTrue(removed, "the entry was there"),
                    () -> assertFalse(removedAgain, "and is not there twice"),
                    () -> assertHashes(afterwards, MD5_ABC, SHA256_ABC, "read again"),
                    () -> assertHashes(untouched, MD5_JKL, SHA256_JKL, "the other file"),
                    () -> assertEquals(3, delegate.callCount(), "one extra read, for the one file"),
                    () -> assertEquals(1L, service.hitCount(), "the other file was still served"));
        }

        @Test
        @DisplayName("invalidate tolerates a file that is no longer there")
        void invalidateToleratesAMissingFile(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = boundedTo(DEFAULT_BOUND, delegate);
            service.hash(file);
            Files.delete(file);

            assertAll(
                    () -> assertTrue(service.invalidate(file), "removed by its absolute path"),
                    () -> assertEquals(0, service.size(), "nothing left"));
        }

        @Test
        @DisplayName("invalidateAll forgets everything")
        void invalidateAllForgetsEverything(@TempDir Path root)
                throws IOException, InterruptedException {
            Path dir = root.toRealPath();
            Path one = write(dir, "one.mzML", "abc");
            Path two = write(dir, "two.mzML", "def");
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = boundedTo(DEFAULT_BOUND, delegate);
            service.hash(one);
            service.hash(two);
            assertEquals(2, service.size(), "two entries before");

            service.invalidateAll();
            service.hash(one);
            service.hash(two);

            assertAll(
                    () -> assertEquals(4, delegate.callCount(), "everything was read again"),
                    () -> assertEquals(0L, service.hitCount(), "nothing was served"),
                    () -> assertEquals(2, service.size(), "and the cache refilled"));
        }
    }

    // ===========================================================================================
    // Group 7 -- sharing one instance between threads.
    // ===========================================================================================

    /** One instance is created at start-up and handed to every stage, so this has to hold. */
    @Nested
    @DisplayName("group 7: safe to share between threads")
    class Concurrency {

        @Test
        @DisplayName("eight threads hashing one file agree, and the counters add up")
        void eightThreadsHashingOneFileAgree(@TempDir Path root)
                throws IOException, InterruptedException, ExecutionException {
            Path dir = root.toRealPath();
            Path file = write(dir, "spectra.mzML", "abc");
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = boundedTo(DEFAULT_BOUND, delegate);
            List<FileHashes> results = inParallel(8, 50, () -> service.hash(file));

            assertAll(
                    () -> assertEquals(400, results.size(), "every call returned"),
                    () ->
                            assertTrue(
                                    results.stream()
                                            .allMatch(
                                                    h ->
                                                            MD5_ABC.equals(h.md5())
                                                                    && SHA256_ABC.equals(
                                                                            h.sha256())),
                                    "every result is the digest of the content"),
                    () -> assertEquals(1, service.size(), "one file, one entry"),
                    () ->
                            assertEquals(
                                    400L,
                                    service.hitCount() + service.missCount(),
                                    "hits plus misses is the number of calls"),
                    () ->
                            assertEquals(
                                    (long) delegate.callCount(),
                                    service.missCount(),
                                    "the cache's own miss count matches the hasher's call count"),
                    () ->
                            assertTrue(
                                    delegate.callCount() <= 8,
                                    "at most one read per thread, not one per call: "
                                            + delegate.callCount()),
                    () -> assertTrue(service.hitCount() > 0L, "and the cache did serve"));
        }

        @Test
        @DisplayName("many files against a small bound stay bounded")
        void manyFilesAgainstASmallBoundStayBounded(@TempDir Path root)
                throws IOException, InterruptedException, ExecutionException {
            Path dir = root.toRealPath();
            List<Path> files = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                files.add(write(dir, "file-" + i + ".mzML", "mno"));
            }
            CountingHashService delegate = new CountingHashService();
            CachingHashService service = boundedTo(4, delegate);
            AtomicInteger next = new AtomicInteger();
            List<FileHashes> results =
                    inParallel(
                            8,
                            40,
                            () -> service.hash(files.get(next.getAndIncrement() % files.size())));

            assertAll(
                    () -> assertEquals(320, results.size(), "every call returned"),
                    () ->
                            assertTrue(
                                    results.stream()
                                            .allMatch(
                                                    h ->
                                                            MD5_MNO.equals(h.md5())
                                                                    && SHA256_MNO.equals(
                                                                            h.sha256())),
                                    "every result is the digest of the content"),
                    () -> assertEquals(4, service.size(), "never more entries than the bound"));
        }
    }

    // ===========================================================================================
    // Group 8 -- rejected arguments.
    // ===========================================================================================

    /** What the class refuses to be built with, or called with. */
    @Nested
    @DisplayName("group 8: rejected arguments")
    class Rejections {

        @Test
        @DisplayName("a null delegate is rejected")
        void aNullDelegateIsRejected() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () -> new CachingHashService(deliberateNull()));

            assertEquals("delegate", thrown.getMessage());
        }

        @Test
        @DisplayName("a null reader or clock is rejected")
        void aNullReaderOrClockIsRejected() {
            HashService delegate = new CountingHashService();

            assertAll(
                    () ->
                            assertEquals(
                                    "reader",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new CachingHashService(
                                                                    delegate,
                                                                    1,
                                                                    true,
                                                                    deliberateNull(),
                                                                    Clock.systemUTC()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "clock",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new CachingHashService(
                                                                    delegate,
                                                                    1,
                                                                    true,
                                                                    FileFingerprint::of,
                                                                    deliberateNull()))
                                            .getMessage()));
        }

        @Test
        @DisplayName("a negative bound is rejected, naming the value")
        void aNegativeBoundIsRejected() {
            HashService delegate = new CountingHashService();

            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new CachingHashService(delegate, -3));

            assertEquals("maximumEntries must be at least 1, but was: -3", thrown.getMessage());
        }

        @Test
        @DisplayName("a null path is rejected by every entry point")
        void aNullPathIsRejected() {
            CachingHashService service = new CachingHashService(new CountingHashService());

            assertAll(
                    () ->
                            assertEquals(
                                    "path",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> service.hash(deliberateNull()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "path",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> service.rehash(deliberateNull()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "path",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () -> service.invalidate(deliberateNull()))
                                            .getMessage()));
        }
    }

    // ===========================================================================================
    // Fixtures and helpers.
    // ===========================================================================================

    /**
     * A cache with a real reader and a clock an hour ahead, so that a file written a moment ago is
     * already settled.
     *
     * <p>The settling rule is proved on its own in group 4; the tests that use this one are about
     * something else, and would otherwise all have to wait for a second to pass.
     *
     * @param bound the maximum number of entries
     * @param delegate the hasher to count
     * @return the cache
     */
    private static CachingHashService boundedTo(int bound, HashService delegate) {
        return new CachingHashService(delegate, bound, true, FileFingerprint::of, aheadByAnHour());
    }

    /**
     * A clock reading an hour into the future.
     *
     * @return the clock
     */
    private static Clock aheadByAnHour() {
        return Clock.offset(Clock.systemUTC(), Duration.ofHours(1));
    }

    /**
     * A clock stopped inside the second a timestamp falls in, whatever second that is.
     *
     * <p>Fixed rather than offset, so that the boundary the settling rule turns on is exact instead
     * of nearly always right: a test that depended on the system clock not crossing a second in the
     * middle of it would fail once in a few hundred runs and be called flaky.
     *
     * @param timestamp the timestamp whose second the clock should read
     * @return the clock
     */
    private static Clock fixedInsideTheSecondOf(Instant timestamp) {
        return Clock.fixed(
                timestamp.truncatedTo(ChronoUnit.SECONDS).plusMillis(999), ZoneOffset.UTC);
    }

    /**
     * Reads a file's inode change time with the JDK, not with the class under test.
     *
     * @param file the file
     * @return its {@code st_ctim}
     * @throws IOException if it cannot be read
     */
    private static Instant inodeChangeTime(Path file) throws IOException {
        return ((FileTime) Files.getAttribute(file, "unix:ctime")).toInstant();
    }

    /**
     * Waits until the file's last change is in a strictly earlier second than now.
     *
     * <p>Which is what {@link FileFingerprint#settledBefore} demands, so this is the price of
     * proving a real cache hit on the real system clock rather than on a substituted one. It is at
     * most one second, and usually less.
     *
     * <p><strong>Why this delay cannot be an event, asked and answered.</strong> Phase 03's
     * mechanical no-fixed-sleep scan does not reach this module and escalated the question here, so
     * the justification is written down rather than assumed. Three things make it a computed wait
     * rather than a fixed sleep, and the difference is the whole answer:
     *
     * <ul>
     *   <li><strong>The deadline is read from the file, not chosen.</strong> It is the file's own
     *       later of {@code mtime} and {@code unix:ctime}, truncated to its second, plus one
     *       second. The sleep is whatever remains of that, and is skipped entirely when the
     *       deadline has already passed -- which it usually has, because the tests do other work
     *       first.
     *   <li><strong>There is no event to wait for.</strong> The thing being awaited is not a change
     *       to the file: it is the system clock advancing past the second the file was last changed
     *       in. A {@link java.nio.file.WatchService} reports file changes and the kernel publishes
     *       nothing when a second elapses, so no notification exists to subscribe to. A poll would
     *       be a busy-wait for the same deadline and would not be more honest.
     *   <li><strong>The alternative is a seam this group exists not to use.</strong> Group 4
     *       already substitutes a clock for the boundary cases, which is the right tool there.
     *       Group 1 deliberately runs through the public constructor on the real file system with
     *       the real clock, because a property proved only through a seam is a property the
     *       production path is free not to have -- the rejection this phase's unit 1 earned.
     * </ul>
     *
     * <p>The one chosen number is the {@code 20 ms} margin past the second boundary, which covers
     * the scheduler waking the thread a moment early; without it the wait can return in the same
     * second it was waiting to leave, and the test becomes flaky rather than slow. If a mechanical
     * scan flags this method, this paragraph is the answer: it stays, and the reason is that the
     * event it would subscribe to does not exist.
     *
     * @param file the file to wait for
     * @throws IOException if its timestamps cannot be read
     * @throws InterruptedException if the wait is interrupted
     */
    private static void awaitSettled(Path file) throws IOException, InterruptedException {
        Instant modified = Files.getLastModifiedTime(file).toInstant();
        Instant changed = inodeChangeTime(file);
        Instant latest = modified.isAfter(changed) ? modified : changed;
        Instant safe = latest.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1).plusMillis(20);
        long waitMillis = Duration.between(Instant.now(), safe).toMillis();
        if (waitMillis > 0) {
            Thread.sleep(waitMillis);
        }
    }

    /**
     * Writes a fixture file.
     *
     * @param dir the directory to write into
     * @param name the file name
     * @param content ASCII content, whose digests are pinned at the top of this file
     * @return the file
     * @throws IOException if it cannot be written
     */
    private static Path write(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, content.getBytes(US_ASCII));
        return file;
    }

    /**
     * Rewrites a file's bytes in place and puts its modification time back where it was.
     *
     * <p>The result is a file whose path, size, modification time and inode are all exactly what
     * they were, holding different content: the case the exit gate is about, and the case {@code cp
     * -p} and {@code rsync -t} produce without anybody intending anything.
     *
     * @param file the file to rewrite
     * @param content the replacement, which must be the same length
     * @throws IOException if the file cannot be written
     */
    private static void overwriteInPlaceKeepingSizeAndTime(Path file, String content)
            throws IOException {
        byte[] bytes = content.getBytes(US_ASCII);
        long sizeBefore = Files.size(file);
        if (bytes.length != sizeBefore) {
            throw new IllegalArgumentException(
                    "the replacement must be the same length: "
                            + bytes.length
                            + " vs "
                            + sizeBefore);
        }
        FileTime modifiedBefore = Files.getLastModifiedTime(file);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(bytes), 0);
        }
        Files.setLastModifiedTime(file, modifiedBefore);
    }

    /**
     * A fingerprint reader that hides the inode change time, as a Windows file system would.
     *
     * @return the reader
     */
    private static CachingHashService.FingerprintReader withoutInodeChangeTime() {
        return path -> {
            FileFingerprint real = FileFingerprint.of(path);
            return new FileFingerprint(
                    real.canonicalPath(),
                    real.size(),
                    real.lastModifiedAt(),
                    real.fileIdentity(),
                    null);
        };
    }

    /**
     * A fingerprint reader that hides the file identity, as a Windows file system would.
     *
     * @return the reader
     */
    private static CachingHashService.FingerprintReader withoutFileIdentity() {
        return path -> {
            FileFingerprint real = FileFingerprint.of(path);
            return new FileFingerprint(
                    real.canonicalPath(),
                    real.size(),
                    real.lastModifiedAt(),
                    null,
                    real.inodeChangedAt());
        };
    }

    /**
     * Runs one call on several threads at once and collects every result.
     *
     * @param threads how many threads
     * @param iterations how many calls per thread
     * @param call the call to make
     * @return every result, in no particular order
     * @throws InterruptedException if the wait for the threads is interrupted
     * @throws ExecutionException if any call failed
     */
    private static List<FileHashes> inParallel(int threads, int iterations, HashCall call)
            throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<List<FileHashes>>> work = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                work.add(
                        () -> {
                            List<FileHashes> mine = new ArrayList<>();
                            for (int i = 0; i < iterations; i++) {
                                mine.add(call.call());
                            }
                            return mine;
                        });
            }
            List<FileHashes> all = new ArrayList<>();
            for (Future<List<FileHashes>> future : pool.invokeAll(work)) {
                all.addAll(future.get());
            }
            return all;
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the pool did not stop");
            }
        }
    }

    /**
     * Asserts a digest pair against hand-typed literals.
     *
     * @param actual what came back
     * @param md5 the expected MD5
     * @param sha256 the expected SHA-256
     * @param what which call this was
     */
    private static void assertHashes(FileHashes actual, String md5, String sha256, String what) {
        assertAll(
                () -> assertEquals(md5, actual.md5(), what + ": MD5"),
                () -> assertEquals(sha256, actual.sha256(), what + ": SHA-256"));
    }

    /**
     * A {@code null} of whatever type the call site needs, by a route no analyser folds away.
     *
     * @param <T> the type the call site needs
     * @return {@code null}
     */
    private static <T> T deliberateNull() {
        return Optional.<T>empty().orElse(null);
    }

    /** One call into the cache, for the concurrency helper. */
    @FunctionalInterface
    private interface HashCall {

        /**
         * Makes the call.
         *
         * @return the digests
         * @throws IOException if the file cannot be read
         */
        FileHashes call() throws IOException;
    }

    /** Something to do to a file the moment after it has been hashed. */
    @FunctionalInterface
    private interface PostHashAction {

        /**
         * Acts on the file that was just hashed.
         *
         * @param file the file
         * @throws IOException if the action fails
         */
        void act(Path file) throws IOException;
    }

    /**
     * A real hasher that counts, so "the file was read again" is a number and not a feeling.
     *
     * <p>It delegates to {@link StreamingHashService}, so every digest in these tests is a genuine
     * digest of the bytes on disk at the moment of the call -- which is exactly what makes an
     * expected value typed from {@code md5sum} able to fail.
     */
    private static final class CountingHashService implements HashService {

        private final StreamingHashService real = new StreamingHashService();
        private final AtomicInteger calls = new AtomicInteger();
        private final PostHashAction afterHash;

        CountingHashService() {
            this(
                    file -> {
                        // Nothing happens to the file between the hash and the second attribute
                        // read.
                    });
        }

        CountingHashService(PostHashAction afterHash) {
            this.afterHash = afterHash;
        }

        @Override
        public FileHashes hash(Path path) throws IOException {
            calls.incrementAndGet();
            FileHashes hashes = real.hash(path);
            afterHash.act(path);
            return hashes;
        }

        int callCount() {
            return calls.get();
        }
    }

    /**
     * The production fingerprint reader, wrapped so that the reads can be counted.
     *
     * <p>Counting is the only thing it adds: what it returns is what {@link FileFingerprint#of}
     * returns. That is what makes "revalidated on every use" checkable as a number rather than as a
     * claim in a comment.
     */
    private static final class CountingFingerprintReader
            implements CachingHashService.FingerprintReader {

        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public FileFingerprint read(Path canonicalPath) throws IOException {
            reads.incrementAndGet();
            return FileFingerprint.of(canonicalPath);
        }

        int readCount() {
            return reads.get();
        }
    }
}
