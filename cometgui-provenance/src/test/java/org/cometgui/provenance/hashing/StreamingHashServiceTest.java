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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.cometgui.domain.ports.FileHashes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link StreamingHashService}.
 *
 * <p><strong>Where every expected value in this file came from.</strong> Not from Java, and not
 * from the class under test. Each digest below was produced on the command line by two
 * implementations that share no code with each other or with CometGUI -- GNU coreutils {@code
 * md5sum}/{@code sha256sum} and {@code openssl dgst} -- which agreed, and was then typed in here as
 * a {@code String} literal. The short vectors additionally match the published RFC 1321 (MD5) and
 * FIPS 180-4 / NIST (SHA-256) test vectors, and the same values are pinned in {@code
 * handoffs/PHASE-04-worklog.rst}, recorded before this module held a single class.
 *
 * <p>That rule is the whole point of the file. An expected digest obtained by calling {@link
 * StreamingHashService}, {@code MessageDigest}, or any other Java code would agree with the
 * implementation by construction and could not fail, however wrong the implementation was.
 */
class StreamingHashServiceTest {

    // ---------------------------------------------------------------------------------------
    // Hand-typed reference digests.  See the class Javadoc for their provenance.
    // ---------------------------------------------------------------------------------------

    /** MD5 of zero bytes -- RFC 1321 test suite, first entry. */
    private static final String MD5_EMPTY = "d41d8cd98f00b204e9800998ecf8427e";

    /** SHA-256 of zero bytes -- the NIST-published empty-message digest. */
    private static final String SHA256_EMPTY =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    /** MD5 of {@code "abc"} -- RFC 1321 test suite. */
    private static final String MD5_ABC = "900150983cd24fb0d6963f7d28e17f72";

    /** SHA-256 of {@code "abc"} -- FIPS 180-4 worked example. */
    private static final String SHA256_ABC =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    /** MD5 of one million {@code 'a'} characters. */
    private static final String MD5_MILLION_A = "7707d6ae4e027c70eea2a935c2296f21";

    /** SHA-256 of one million {@code 'a'} characters -- the NIST long-message vector. */
    private static final String SHA256_MILLION_A =
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0";

    /** MD5 of the 256 distinct byte values, {@code 0x00}..{@code 0xff}, in ascending order. */
    private static final String MD5_ALL_BYTE_VALUES = "e2c865db4162bed963bfaa9ef6ac18f0";

    /** SHA-256 of the 256 distinct byte values, {@code 0x00}..{@code 0xff}, ascending. */
    private static final String SHA256_ALL_BYTE_VALUES =
            "40aff2e9d2d8922e47afd4648e6967497158785fbd1da870e7110266bf944880";

    /** MD5 of {@code 00 ff 00 ff 80 7f 01 fe 00 00 ff ff}. */
    private static final String MD5_NUL_AND_FF = "803e62fca5e7c781af842ddd641c90e5";

    /** SHA-256 of {@code 00 ff 00 ff 80 7f 01 fe 00 00 ff ff}. */
    private static final String SHA256_NUL_AND_FF =
            "aad1addeda9a66ceb32b1b254a29be9463d659eb6f106c38248c39ff66a7e679";

    /** MD5 of the first 524 295 bytes of {@link #pattern}, i.e. {@code 2 * BUFFER_SIZE + 7}. */
    private static final String MD5_TWO_BUFFERS_PLUS_SEVEN = "90ac5462c75e9d51076708bbb6261746";

    /** SHA-256 of the first 524 295 bytes of {@link #pattern}. */
    private static final String SHA256_TWO_BUFFERS_PLUS_SEVEN =
            "e271eec4fcab411c2a0d197e5c027bb5c632d5647a0feb1e40fca0555ea3d330";

    /**
     * The buffer size {@link StreamingHashService#BUFFER_SIZE} is expected to hold, typed out
     * rather than read from the class.
     *
     * <p>Every read-count expectation in this file is derived from <em>this</em> literal, never
     * from the production constant. Deriving them from the production constant would make the
     * counts move with the code: halve {@code BUFFER_SIZE} and the "expected" number of reads would
     * halve too, and the test could not fail. {@link
     * SingleReadingPass#bufferSizeIsTheDocumentedValue()} pins the production constant against this
     * number by reflection, so the two are tied together by an assertion instead of by a compiler.
     */
    private static final int BUFFER = 262_144;

    private final StreamingHashService service = new StreamingHashService();

    // ---------------------------------------------------------------------------------------
    // Group 1 -- the zero-byte file (exit gate item 1 names it explicitly).
    // ---------------------------------------------------------------------------------------

    /** The empty file: the case a loop that never runs has to get right. */
    @Nested
    @DisplayName("group 1: the zero-byte file")
    class ZeroByteFile {

        @Test
        @DisplayName("an empty file hashes to the published empty-message digests")
        void emptyFileHashesToTheEmptyMessageDigests(@TempDir Path dir) throws IOException {
            Path empty = write(dir, "empty.bin", new byte[0]);

            assertAll(
                    () -> assertEquals(0L, Files.size(empty), "the fixture must really be empty"),
                    () -> assertHashes(service.hash(empty), MD5_EMPTY, SHA256_EMPTY));
        }

        @Test
        @DisplayName("an empty stream hashes to the same digests, without a single update")
        void emptyStreamHashesToTheEmptyMessageDigests() throws IOException {
            assertHashes(
                    service.hash(new ByteArrayInputStream(new byte[0])), MD5_EMPTY, SHA256_EMPTY);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Group 2 -- the published short vectors.
    // ---------------------------------------------------------------------------------------

    /** RFC 1321 and NIST short vectors, hashed from files on disk. */
    @Nested
    @DisplayName("group 2: published short vectors")
    class PublishedShortVectors {

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @CsvSource({
            "'',"
                    + "d41d8cd98f00b204e9800998ecf8427e,"
                    + "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            "a,"
                    + "0cc175b9c0f1b6a831c399e269772661,"
                    + "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
            "abc,"
                    + "900150983cd24fb0d6963f7d28e17f72,"
                    + "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "'message digest',"
                    + "f96b697d7cb7938d525a2f31aaf161d0,"
                    + "f7846f55cf23e14eebeab5b4e1550cad5b509e3348fbc4efa3a1413d393cb650",
            "abcdefghijklmnopqrstuvwxyz,"
                    + "c3fcd3d76192e4007dfb496cca67e13b,"
                    + "71c480df93d6ae2f1efad1447c66c9525e316218cf51fc8d9ed832f2daf18b73",
            "'The quick brown fox jumps over the lazy dog',"
                    + "9e107d9d372bb6826bd81d3542a419d6,"
                    + "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
        })
        @DisplayName("matches the RFC 1321 and NIST vector for the text")
        void matchesThePublishedVector(String text, String md5, String sha256, @TempDir Path dir)
                throws IOException {
            byte[] content = text.getBytes(US_ASCII);
            Path file = write(dir, "vector.bin", content);

            assertAll(
                    () -> assertEquals((long) text.length(), Files.size(file), "fixture length"),
                    () -> assertHashes(service.hash(file), md5, sha256));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Group 3 -- the one-million-'a' vector, which crosses several buffer boundaries.
    // ---------------------------------------------------------------------------------------

    /** The NIST long-message vector: 1 000 000 bytes, three whole chunks and a short one. */
    @Nested
    @DisplayName("group 3: one million 'a'")
    class OneMillionA {

        @Test
        @DisplayName("hashes to the published long-message digests")
        void hashesToThePublishedLongMessageDigests(@TempDir Path dir) throws IOException {
            byte[] content = new byte[1_000_000];
            Arrays.fill(content, (byte) 'a');
            Path file = write(dir, "million-a.bin", content);

            assertAll(
                    () -> assertEquals(1_000_000L, Files.size(file), "fixture length"),
                    () -> assertHashes(service.hash(file), MD5_MILLION_A, SHA256_MILLION_A));
        }

        @Test
        @DisplayName("the fixture really is one million 'a' and nothing else")
        void theFixtureIsWhatItClaims() {
            byte[] content = new byte[1_000_000];
            Arrays.fill(content, (byte) 'a');

            int notAnA = 0;
            for (byte b : content) {
                if (b != (byte) 0x61) {
                    notAnA++;
                }
            }
            final int wrong = notAnA;

            assertAll(
                    () -> assertEquals(1_000_000, content.length),
                    () -> assertEquals(0, wrong, "bytes that are not 'a'"));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Group 4 -- chunk boundaries.
    // ---------------------------------------------------------------------------------------

    /**
     * Lengths chosen to straddle the read buffer, filled with a non-uniform pattern so that a
     * defect which mixes up an offset or a length changes the bytes digested rather than
     * reshuffling identical ones.
     */
    @Nested
    @DisplayName("group 4: chunk boundaries")
    class ChunkBoundaries {

        @ParameterizedTest(name = "[{index}] {0} bytes")
        @CsvSource({
            "262143,"
                    + "be43030cf831059e40449b0f875fe898,"
                    + "1fb879e092e503707600dbf78bc82e397467cf68085f64b5d155bb2429701c5a",
            "262144,"
                    + "9727ea0e5ba034aaed9c7c98dd64f6b2,"
                    + "503b844fa3b400c687fbd559c844636f6808b3aedeaa9d2c5d8824894bc4cf2a",
            "262145,"
                    + "ff5bdfe579881014a8ef9fece44a777a,"
                    + "601312c4d582bbbfc80fd71d10f3c3ce95eab73e08568be40447ebf2cb520ef2",
            "524295,"
                    + "90ac5462c75e9d51076708bbb6261746,"
                    + "e271eec4fcab411c2a0d197e5c027bb5c632d5647a0feb1e40fca0555ea3d330",
        })
        @DisplayName("a file that straddles the buffer boundary hashes correctly")
        void straddlingFilesHashCorrectly(int length, String md5, String sha256, @TempDir Path dir)
                throws IOException {
            Path file = write(dir, length + ".bin", pattern(length));

            assertAll(
                    () -> assertEquals((long) length, Files.size(file), "fixture length"),
                    () -> assertHashes(service.hash(file), md5, sha256));
        }

        @Test
        @DisplayName("the four lengths really are BUFFER-1, BUFFER, BUFFER+1 and 2*BUFFER+7")
        void theLengthsStraddleTheBuffer() {
            assertAll(
                    () -> assertEquals(262_143, BUFFER - 1),
                    () -> assertEquals(262_144, BUFFER),
                    () -> assertEquals(262_145, BUFFER + 1),
                    () -> assertEquals(524_295, 2 * BUFFER + 7));
        }

        @Test
        @DisplayName("the pattern generator reproduces the bytes md5sum was given")
        void thePatternIsTheOneThatWasHashedOnTheCommandLine() {
            // Hand-typed from the independent Python generator whose output md5sum and openssl
            // read.  Without this pin, a drift in pattern() would silently invalidate every
            // boundary digest above, and the failure would look like a hashing bug.
            byte[] expectedFirstSixteen = {
                (byte) 0x2c, (byte) 0xca, (byte) 0x86, (byte) 0xe2,
                (byte) 0xc6, (byte) 0x04, (byte) 0xf7, (byte) 0xd8,
                (byte) 0x0c, (byte) 0x0d, (byte) 0xe3, (byte) 0xb8,
                (byte) 0x76, (byte) 0x38, (byte) 0x7f, (byte) 0x3c,
            };

            assertAll(
                    () -> assertArrayEquals(expectedFirstSixteen, pattern(16)),
                    () ->
                            assertArrayEquals(
                                    expectedFirstSixteen,
                                    Arrays.copyOf(pattern(BUFFER + 1), 16),
                                    "a longer run must start with the same bytes"));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Group 5 -- binary content, including NUL and 0xFF.
    // ---------------------------------------------------------------------------------------

    /**
     * Bytes that are not text. {@code 0x00} terminates a C string, {@code 0xFF} is not valid UTF-8
     * and sign-extends to {@code -1} in Java, and both appear in every mzML index and every
     * compressed spectrum file this product will hash.
     */
    @Nested
    @DisplayName("group 5: binary content")
    class BinaryContent {

        @Test
        @DisplayName("all 256 byte values, in order, hash correctly")
        void everyByteValueHashesCorrectly(@TempDir Path dir) throws IOException {
            byte[] content = new byte[256];
            for (int i = 0; i < 256; i++) {
                content[i] = (byte) i;
            }
            Path file = write(dir, "all-byte-values.bin", content);

            assertAll(
                    () -> assertEquals(0, content[0], "first byte is NUL"),
                    () -> assertEquals(-1, content[255], "last byte is 0xFF"),
                    () ->
                            assertHashes(
                                    service.hash(file),
                                    MD5_ALL_BYTE_VALUES,
                                    SHA256_ALL_BYTE_VALUES));
        }

        @Test
        @DisplayName("embedded NUL and 0xFF runs hash correctly")
        void embeddedNulAndHighBytesHashCorrectly(@TempDir Path dir) throws IOException {
            byte[] content = {
                (byte) 0x00, (byte) 0xff, (byte) 0x00, (byte) 0xff,
                (byte) 0x80, (byte) 0x7f, (byte) 0x01, (byte) 0xfe,
                (byte) 0x00, (byte) 0x00, (byte) 0xff, (byte) 0xff,
            };
            Path file = write(dir, "nul-and-ff.bin", content);

            assertAll(
                    () -> assertEquals(12L, Files.size(file), "fixture length"),
                    () -> assertHashes(service.hash(file), MD5_NUL_AND_FF, SHA256_NUL_AND_FF));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Group 6 -- the single-pass, bounded-buffer property, observed through the stream seam.
    // ---------------------------------------------------------------------------------------

    /**
     * The property {@code R-PROV-01} and {@code R-PROV-03} actually state. Correct digests do not
     * prove it: an implementation that read the file twice, or slurped it whole, would produce the
     * same digests. Only the shape of the reads can distinguish them, so the reads are counted.
     */
    @Nested
    @DisplayName("group 6: one pass, one bounded buffer")
    class SingleReadingPass {

        @Test
        @DisplayName("the production buffer size is the number this test reasons about")
        void bufferSizeIsTheDocumentedValue() throws ReflectiveOperationException {
            // Read reflectively on purpose.  BUFFER_SIZE is a compile-time constant, so a direct
            // reference would be inlined into this class at compile time and the assertion would
            // compare 262144 with 262144 no matter what the field says.
            int declared = (int) StreamingHashService.class.getField("BUFFER_SIZE").get(null);

            assertEquals(262_144, declared);
        }

        @ParameterizedTest(name = "[{index}] {0} bytes in {1} read calls")
        @CsvSource({
            "0,1,"
                    + "d41d8cd98f00b204e9800998ecf8427e,"
                    + "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            "1,2,"
                    + "c0cb5f0fcf239ab3d9c1fcd31fff1efc,"
                    + "d03502c43d74a30b936740a9517dc4ea2b2ad7168caa0a774cefe793ce0b33e7",
            "262143,2,"
                    + "be43030cf831059e40449b0f875fe898,"
                    + "1fb879e092e503707600dbf78bc82e397467cf68085f64b5d155bb2429701c5a",
            "262144,2,"
                    + "9727ea0e5ba034aaed9c7c98dd64f6b2,"
                    + "503b844fa3b400c687fbd559c844636f6808b3aedeaa9d2c5d8824894bc4cf2a",
            "262145,3,"
                    + "ff5bdfe579881014a8ef9fece44a777a,"
                    + "601312c4d582bbbfc80fd71d10f3c3ce95eab73e08568be40447ebf2cb520ef2",
            "524295,4,"
                    + "90ac5462c75e9d51076708bbb6261746,"
                    + "e271eec4fcab411c2a0d197e5c027bb5c632d5647a0feb1e40fca0555ea3d330",
            "786432,4,"
                    + "b908d0664b6637cb24f6548d76b541e0,"
                    + "2bb87269a598e3305bcc0a765d11e7eed096256d48a324c1c6e4b321e018b5be",
        })
        @DisplayName("exactly the reads one pass needs, none larger than the buffer, closed once")
        void makesExactlyOnePassOverTheStream(
                int length, int expectedReadCalls, String md5, String sha256) throws IOException {
            byte[] content = pattern(length);
            RecordingInputStream recorder =
                    new RecordingInputStream(new ByteArrayInputStream(content));

            FileHashes hashes = service.hash(recorder);

            assertAll(
                    // (a) no read ever asks for more than one buffer.  The implementation always
                    // offers the whole buffer, so the requested length is not merely bounded by
                    // BUFFER, it equals it on every call.
                    () -> assertEquals(BUFFER, recorder.maximumRequestedLength(), "largest read"),
                    () ->
                            assertEquals(
                                    List.of(),
                                    recorder.requestsLongerThan(BUFFER),
                                    "reads asking for more than one buffer"),
                    () ->
                            assertEquals(
                                    List.of(),
                                    recorder.offsetsOtherThanZero(),
                                    "reads that did not fill the buffer from its start"),
                    () ->
                            assertEquals(
                                    0,
                                    recorder.singleByteReadCount(),
                                    "single-byte read() calls (a byte-at-a-time pass)"),
                    // (b) the number of reads is exactly what one pass over this length needs:
                    // ceil(length / BUFFER) chunks plus the one that reports end of stream.
                    () -> assertEquals(expectedReadCalls, recorder.readCallCount(), "read calls"),
                    // (c) fully consumed, and closed exactly once.
                    () ->
                            assertEquals(
                                    (long) length,
                                    recorder.totalBytesDelivered(),
                                    "bytes handed to the digests"),
                    () ->
                            assertEquals(
                                    1,
                                    recorder.endOfStreamCount(),
                                    "reads returning -1: end of stream, reached exactly once"),
                    () -> assertEquals(1, recorder.closeCount(), "close() calls"),
                    // ...and the digests are still the independently computed ones, so the read
                    // pattern above is the pattern of a pass that got the right answer.
                    () -> assertHashes(hashes, md5, sha256));
        }

        @Test
        @DisplayName("the stream is closed exactly once even when a read fails")
        void closesTheStreamWhenAReadFails() {
            RecordingInputStream recorder =
                    new RecordingInputStream(new FailingInputStream("disk went away"));

            IOException thrown = hashExpectingFailure(recorder);

            assertAll(
                    () -> assertEquals("disk went away", thrown.getMessage()),
                    () -> assertEquals(1, recorder.closeCount(), "close() calls"));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Group 8 -- ONE OPEN, asserted on hash(Path), the path production actually takes.
    //
    // Group 6 proves that hash(InputStream) makes exactly one pass over the stream it is given.
    // That is only half the promise, and on its own it is bypassable: hash(Path) is free not to
    // use the seam the property was proved on.  An implementation that opened the file, hashed
    // it, threw the result away and did the whole thing again would return correct digests and
    // would satisfy every assertion in group 6, while doubling the I/O on the 2 GB spectrum
    // files this phase exists for.  Only the number of opens tells the two apart, so this group
    // counts opens, and counts the bytes across EVERY stream the hasher was ever handed.
    // ---------------------------------------------------------------------------------------

    /** The single-pass property, asserted where production reads its files. */
    @Nested
    @DisplayName("group 8: one open of the file, on the production path")
    class SingleOpenOfTheFile {

        @ParameterizedTest(name = "[{index}] {0} bytes: 1 open, {1} read calls")
        @CsvSource({
            "0,1,"
                    + "d41d8cd98f00b204e9800998ecf8427e,"
                    + "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            "1,2,"
                    + "c0cb5f0fcf239ab3d9c1fcd31fff1efc,"
                    + "d03502c43d74a30b936740a9517dc4ea2b2ad7168caa0a774cefe793ce0b33e7",
            "262143,2,"
                    + "be43030cf831059e40449b0f875fe898,"
                    + "1fb879e092e503707600dbf78bc82e397467cf68085f64b5d155bb2429701c5a",
            "262144,2,"
                    + "9727ea0e5ba034aaed9c7c98dd64f6b2,"
                    + "503b844fa3b400c687fbd559c844636f6808b3aedeaa9d2c5d8824894bc4cf2a",
            "262145,3,"
                    + "ff5bdfe579881014a8ef9fece44a777a,"
                    + "601312c4d582bbbfc80fd71d10f3c3ce95eab73e08568be40447ebf2cb520ef2",
            "524295,4,"
                    + "90ac5462c75e9d51076708bbb6261746,"
                    + "e271eec4fcab411c2a0d197e5c027bb5c632d5647a0feb1e40fca0555ea3d330",
            "786432,4,"
                    + "b908d0664b6637cb24f6548d76b541e0,"
                    + "2bb87269a598e3305bcc0a765d11e7eed096256d48a324c1c6e4b321e018b5be",
        })
        @DisplayName("hash(Path) opens the file once and reads its length exactly once over")
        void opensTheFileOnceAndReadsItOnce(
                int length, int expectedReadCalls, String md5, String sha256, @TempDir Path dir)
                throws IOException {
            Path file = write(dir, length + ".bin", pattern(length));
            RecordingFileOpener opener = new RecordingFileOpener();
            StreamingHashService hasher = new StreamingHashService(opener);

            FileHashes hashes = hasher.hash(file);

            assertAll(
                    // The assertion the two-pass defect fails on, and the only one that can.
                    () -> assertEquals(1, opener.openCount(), "open() calls"),
                    () -> assertEquals(List.of(file), opener.openedPaths(), "paths opened"),
                    () -> assertEquals(1, opener.streamCount(), "streams handed to the hasher"),
                    // Totals across EVERY stream, so a discarded extra pass shows up as double.
                    () ->
                            assertEquals(
                                    (long) length,
                                    opener.totalBytesDelivered(),
                                    "bytes read from the file, summed over every stream opened"),
                    () ->
                            assertEquals(
                                    expectedReadCalls,
                                    opener.totalReadCalls(),
                                    "read calls, summed over every stream opened"),
                    () -> assertEquals(BUFFER, opener.maximumRequestedLength(), "largest read"),
                    () ->
                            assertEquals(
                                    List.of(),
                                    opener.requestsLongerThan(BUFFER),
                                    "reads asking for more than one buffer"),
                    () ->
                            assertEquals(
                                    List.of(),
                                    opener.offsetsOtherThanZero(),
                                    "reads that did not fill the buffer from its start"),
                    () ->
                            assertEquals(
                                    0,
                                    opener.totalSingleByteReads(),
                                    "single-byte read() calls (a byte-at-a-time pass)"),
                    () -> assertEquals(1, opener.totalCloses(), "close() calls"),
                    () -> assertHashes(hashes, md5, sha256));
        }

        @Test
        @DisplayName("a directory is rejected before the file system is touched at all")
        void aDirectoryIsNeverOpened(@TempDir Path dir) throws IOException {
            Path subdirectory = Files.createDirectory(dir.resolve("a-directory"));
            RecordingFileOpener opener = new RecordingFileOpener();
            StreamingHashService hasher = new StreamingHashService(opener);

            IOException thrown = assertThrows(IOException.class, () -> hasher.hash(subdirectory));

            assertAll(
                    () ->
                            assertEquals(
                                    "Cannot hash a directory, only a regular file: " + subdirectory,
                                    thrown.getMessage()),
                    () -> assertEquals(0, opener.openCount(), "open() calls"));
        }

        @Test
        @DisplayName("a null path is rejected before the file system is touched at all")
        void aNullPathIsNeverOpened() {
            RecordingFileOpener opener = new RecordingFileOpener();
            StreamingHashService hasher = new StreamingHashService(opener);

            NullPointerException thrown =
                    assertThrows(NullPointerException.class, () -> hasher.hash(nullOf(Path.class)));

            assertAll(
                    () -> assertEquals("path", thrown.getMessage()),
                    () -> assertEquals(0, opener.openCount(), "open() calls"));
        }

        @Test
        @DisplayName("a missing file is attempted once, and hands out no stream")
        void aMissingFileIsOpenedOnceAndFails(@TempDir Path dir) {
            Path missing = dir.resolve("not-here.mzML");
            RecordingFileOpener opener = new RecordingFileOpener();
            StreamingHashService hasher = new StreamingHashService(opener);

            assertThrows(NoSuchFileException.class, () -> hasher.hash(missing));

            assertAll(
                    () -> assertEquals(1, opener.openCount(), "open() calls"),
                    () -> assertEquals(List.of(missing), opener.openedPaths(), "paths opened"),
                    () -> assertEquals(0, opener.streamCount(), "streams handed to the hasher"));
        }

        @Test
        @DisplayName("the stream the opener handed out is closed exactly once when a read fails")
        void closesTheOpenedStreamWhenAReadFails(@TempDir Path dir) throws IOException {
            Path file = write(dir, "abc.bin", "abc".getBytes(US_ASCII));
            RecordingFileOpener opener = new RecordingFileOpener();
            StreamingHashService hasher =
                    new StreamingHashService(path -> opener.record(new FailingInputStream("gone")));

            IOException thrown = assertThrows(IOException.class, () -> hasher.hash(file));

            assertAll(
                    () -> assertEquals("gone", thrown.getMessage()),
                    () -> assertEquals(1, opener.streamCount(), "streams handed to the hasher"),
                    () -> assertEquals(1, opener.totalCloses(), "close() calls"));
        }

        @Test
        @DisplayName("a null opener is rejected by name")
        void rejectsANullOpener() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () ->
                                    new StreamingHashService(
                                            nullOf(StreamingHashService.FileOpener.class)));

            assertEquals("opener", thrown.getMessage());
        }

        @Test
        @DisplayName("the public constructor really reads the real file system")
        void theProductionConstructorReadsRealFiles(@TempDir Path dir) throws IOException {
            // Everything else in this class that hashes a real file goes through the no-argument
            // constructor, so the production wiring is exercised throughout; this states it.
            Path file = write(dir, "abc.bin", "abc".getBytes(US_ASCII));

            assertHashes(new StreamingHashService().hash(file), MD5_ABC, SHA256_ABC);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Group 7 -- one instance, many threads.
    // ---------------------------------------------------------------------------------------

    /**
     * The statelessness claim in the class Javadoc, asserted rather than asserted-in-prose. A
     * shared {@code MessageDigest} field, or a shared buffer field, would pass every test above and
     * fail here.
     */
    @Nested
    @DisplayName("group 7: concurrent use of one instance")
    class ConcurrentUse {

        private static final int THREADS = 8;
        private static final int ROUNDS = 4;

        @Test
        @DisplayName("eight threads sharing one instance all get the published digests")
        void oneInstanceServesManyThreads(@TempDir Path dir)
                throws IOException, InterruptedException, ExecutionException, TimeoutException {
            byte[] millionA = new byte[1_000_000];
            Arrays.fill(millionA, (byte) 'a');
            byte[] allByteValues = new byte[256];
            for (int i = 0; i < 256; i++) {
                allByteValues[i] = (byte) i;
            }
            Path abc = write(dir, "abc.bin", "abc".getBytes(US_ASCII));
            Path million = write(dir, "million-a.bin", millionA);
            Path values = write(dir, "all-byte-values.bin", allByteValues);
            Path straddling = write(dir, "straddling.bin", pattern(2 * BUFFER + 7));

            List<String> expectedPerRound =
                    List.of(
                            MD5_ABC,
                            SHA256_ABC,
                            MD5_MILLION_A,
                            SHA256_MILLION_A,
                            MD5_ALL_BYTE_VALUES,
                            SHA256_ALL_BYTE_VALUES,
                            MD5_TWO_BUFFERS_PLUS_SEVEN,
                            SHA256_TWO_BUFFERS_PLUS_SEVEN);
            List<String> expected = new ArrayList<>();
            for (int round = 0; round < ROUNDS; round++) {
                expected.addAll(expectedPerRound);
            }

            List<Callable<List<String>>> tasks = new ArrayList<>();
            for (int thread = 0; thread < THREADS; thread++) {
                tasks.add(
                        () -> {
                            List<String> seen = new ArrayList<>();
                            for (int round = 0; round < ROUNDS; round++) {
                                for (Path file : List.of(abc, million, values, straddling)) {
                                    FileHashes hashes = service.hash(file);
                                    seen.add(hashes.md5());
                                    seen.add(hashes.sha256());
                                }
                            }
                            return seen;
                        });
            }

            List<List<String>> results = new ArrayList<>();
            ExecutorService pool = Executors.newFixedThreadPool(THREADS);
            try {
                List<Future<List<String>>> futures = pool.invokeAll(tasks);
                for (Future<List<String>> future : futures) {
                    results.add(future.get(120, TimeUnit.SECONDS));
                }
            } finally {
                pool.shutdownNow();
            }

            assertAll(
                    () -> assertEquals(THREADS, results.size(), "threads that produced a result"),
                    () ->
                            assertEquals(
                                    Collections.nCopies(THREADS, expected),
                                    results,
                                    "every thread's digests, in order"));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Failure behaviour: exception type and message, never merely "it threw something".
    // ---------------------------------------------------------------------------------------

    /** What the service does with input it cannot hash. */
    @Nested
    @DisplayName("failure behaviour")
    class FailureBehaviour {

        @Test
        @DisplayName("a null path is rejected by name")
        void rejectsANullPath() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class, () -> service.hash(nullOf(Path.class)));

            assertEquals("path", thrown.getMessage());
        }

        @Test
        @DisplayName("a null stream is rejected by name")
        void rejectsANullStream() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () -> service.hash(nullOf(InputStream.class)));

            assertEquals("stream", thrown.getMessage());
        }

        @Test
        @DisplayName("a missing file raises NoSuchFileException naming the path")
        void missingFileRaisesNoSuchFileException(@TempDir Path dir) {
            Path missing = dir.resolve("not-here.mzML");

            NoSuchFileException thrown =
                    assertThrows(NoSuchFileException.class, () -> service.hash(missing));

            assertAll(
                    () -> assertEquals(missing.toString(), thrown.getFile()),
                    () ->
                            assertTrue(
                                    thrown.getMessage().contains(missing.toString()),
                                    () ->
                                            "message should name the path, was: "
                                                    + thrown.getMessage()));
        }

        @Test
        @DisplayName("a directory raises IOException with this project's own wording")
        void directoryRaisesIoException(@TempDir Path dir) {
            Path subdirectory = dir.resolve("a-directory");

            IOException thrown =
                    assertThrows(
                            IOException.class,
                            () -> {
                                Files.createDirectory(subdirectory);
                                service.hash(subdirectory);
                            });

            assertAll(
                    () ->
                            assertEquals(
                                    "Cannot hash a directory, only a regular file: " + subdirectory,
                                    thrown.getMessage()),
                    () ->
                            assertEquals(
                                    IOException.class,
                                    thrown.getClass(),
                                    "the platform's own 'Is a directory' text must not leak out"));
        }

        @Test
        @DisplayName("an algorithm this JVM lacks fails loudly, not silently")
        void anAbsentAlgorithmFailsLoudly() {
            IllegalStateException thrown =
                    assertThrows(
                            IllegalStateException.class,
                            () -> StreamingHashService.newDigest("NO-SUCH-ALGORITHM"));

            assertAll(
                    () ->
                            assertEquals(
                                    "This JVM provides no NO-SUCH-ALGORITHM digest,"
                                            + " which Java SE requires",
                                    thrown.getMessage()),
                    () -> assertInstanceOf(NoSuchAlgorithmException.class, thrown.getCause()));
        }

        @Test
        @DisplayName("the algorithms this class actually asks for are present")
        void theRequiredAlgorithmsArePresent() {
            assertAll(
                    () -> assertEquals("MD5", StreamingHashService.newDigest("MD5").getAlgorithm()),
                    () ->
                            assertEquals(
                                    "SHA-256",
                                    StreamingHashService.newDigest("SHA-256").getAlgorithm()),
                    () ->
                            assertNotSame(
                                    StreamingHashService.newDigest("MD5"),
                                    StreamingHashService.newDigest("MD5"),
                                    "each call must return an unshared digest"));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Locale independence of the hexadecimal rendering (R-PROV-04's locale requirement).
    // ---------------------------------------------------------------------------------------

    /** The digest text must be the same in Ankara, Cairo and London. */
    @Nested
    @DisplayName("locale independence")
    class LocaleIndependence {

        @ParameterizedTest(name = "[{index}] default locale {0}")
        @ValueSource(
                strings = {
                    "tr-TR",
                    "ar-EG-u-nu-arab",
                    "th-TH-u-nu-thai",
                    "hi-IN-u-nu-deva",
                    "bn-BD-u-nu-beng"
                })
        @DisplayName("hexadecimal digits do not follow the default locale")
        void hexadecimalDoesNotFollowTheDefaultLocale(String languageTag, @TempDir Path dir)
                throws IOException {
            Locale original = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag(languageTag));
                Path file = write(dir, "abc.bin", "abc".getBytes(US_ASCII));

                assertHashes(service.hash(file), MD5_ABC, SHA256_ABC);
            } finally {
                Locale.setDefault(original);
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Fixtures and helpers.
    // ---------------------------------------------------------------------------------------

    /**
     * A {@code null} typed as {@code T}, for the tests that prove a rejection.
     *
     * <p>A {@code null} literal at the call site is reported by SpotBugs as {@code
     * NP_NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS} -- the analyser calling the test's entire purpose
     * the defect. This project fixes a finding rather than excluding it, and the fix is the one
     * {@code org.cometgui.domain.testing.Nulls} already uses in the domain module: hand the null
     * through a value the analyser cannot constant-fold. What is asserted is unchanged.
     */
    private static <T> T nullOf(Class<T> type) {
        return type.cast(null);
    }

    /**
     * Hashes a stream that is expected to fail, and returns the failure.
     *
     * <p>The stream is passed as an argument here rather than captured by a lambda in the caller so
     * that SpotBugs can see the hand-off; a {@code Closeable} that is only ever captured looks to
     * {@code FindOpenStream} like one that is never closed.
     */
    private IOException hashExpectingFailure(InputStream stream) {
        return assertThrows(IOException.class, () -> service.hash(stream));
    }

    private static Path write(Path directory, String name, byte[] content) throws IOException {
        Path file = directory.resolve(name);
        Files.write(file, content);
        return file;
    }

    private static void assertHashes(FileHashes actual, String md5, String sha256) {
        assertAll(
                () -> assertEquals(md5, actual.md5(), "MD5"),
                () -> assertEquals(sha256, actual.sha256(), "SHA-256"));
    }

    /**
     * A deterministic, non-uniform, non-repeating byte pattern.
     *
     * <p>A 64-bit linear congruential generator with Knuth's MMIX constants, taking the top byte of
     * each state. Three properties are wanted. It is reproducible, so the same bytes could be
     * written to a file and handed to {@code md5sum} while these tests were being written. Its
     * period is 2^64, so no chunk of it repeats and a defect that shifts a chunk by any distance
     * changes the content digested. And its bytes are spread over the whole 0..255 range, so NUL
     * and 0xFF occur naturally.
     *
     * <p>{@link ChunkBoundaries#thePatternIsTheOneThatWasHashedOnTheCommandLine()} pins the first
     * sixteen bytes against the independent generator's output.
     */
    private static byte[] pattern(int length) {
        byte[] bytes = new byte[length];
        long state = 0x0123456789ABCDEFL;
        for (int i = 0; i < length; i++) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            bytes[i] = (byte) (state >>> 56);
        }
        return bytes;
    }

    /**
     * Wraps a stream and remembers every read and every close, so the single-pass property can be
     * asserted as numbers instead of described in a comment.
     */
    private static final class RecordingInputStream extends InputStream {

        private final InputStream delegate;
        private final List<Integer> requestedOffsets = new ArrayList<>();
        private final List<Integer> requestedLengths = new ArrayList<>();
        private final List<Integer> returnedCounts = new ArrayList<>();
        private int singleByteReads;
        private int closes;

        RecordingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            singleByteReads++;
            return delegate.read();
        }

        @Override
        public int read(byte[] destination, int offset, int length) throws IOException {
            int returned = delegate.read(destination, offset, length);
            requestedOffsets.add(offset);
            requestedLengths.add(length);
            returnedCounts.add(returned);
            return returned;
        }

        @Override
        public void close() throws IOException {
            closes++;
            delegate.close();
        }

        int readCallCount() {
            return requestedLengths.size();
        }

        int singleByteReadCount() {
            return singleByteReads;
        }

        int closeCount() {
            return closes;
        }

        int maximumRequestedLength() {
            return requestedLengths.stream().mapToInt(Integer::intValue).max().orElse(-1);
        }

        List<Integer> requestsLongerThan(int limit) {
            return requestedLengths.stream().filter(length -> length > limit).toList();
        }

        List<Integer> offsetsOtherThanZero() {
            return requestedOffsets.stream().filter(offset -> offset != 0).toList();
        }

        long totalBytesDelivered() {
            return returnedCounts.stream()
                    .filter(count -> count > 0)
                    .mapToLong(Integer::intValue)
                    .sum();
        }

        int endOfStreamCount() {
            return (int) returnedCounts.stream().filter(count -> count == -1).count();
        }
    }

    /**
     * A {@link StreamingHashService.FileOpener} that opens real files and remembers every open,
     * every read and every close, so "one open" can be asserted as a number.
     *
     * <p>The totals deliberately span <em>every</em> stream it has ever handed out, not the last
     * one: an implementation that opened the file twice and discarded the first pass would look
     * perfect on either stream taken alone.
     */
    private static final class RecordingFileOpener implements StreamingHashService.FileOpener {

        private final List<Path> openedPaths = new ArrayList<>();
        private final List<RecordingInputStream> streams = new ArrayList<>();

        @Override
        public InputStream open(Path path) throws IOException {
            openedPaths.add(path);
            // Files.newInputStream throws for a missing file, which is what production does, and
            // leaves streams empty -- so an attempted open still counts as an open.
            return record(new RecordingInputStream(Files.newInputStream(path)));
        }

        /** Registers an already-built stream, for the tests that supply their own. */
        InputStream record(InputStream stream) {
            RecordingInputStream recording =
                    stream instanceof RecordingInputStream already
                            ? already
                            : new RecordingInputStream(stream);
            streams.add(recording);
            return recording;
        }

        int openCount() {
            return openedPaths.size();
        }

        List<Path> openedPaths() {
            return List.copyOf(openedPaths);
        }

        int streamCount() {
            return streams.size();
        }

        int totalReadCalls() {
            return streams.stream().mapToInt(RecordingInputStream::readCallCount).sum();
        }

        long totalBytesDelivered() {
            return streams.stream().mapToLong(RecordingInputStream::totalBytesDelivered).sum();
        }

        int totalSingleByteReads() {
            return streams.stream().mapToInt(RecordingInputStream::singleByteReadCount).sum();
        }

        int totalCloses() {
            return streams.stream().mapToInt(RecordingInputStream::closeCount).sum();
        }

        int maximumRequestedLength() {
            return streams.stream()
                    .mapToInt(RecordingInputStream::maximumRequestedLength)
                    .max()
                    .orElse(-1);
        }

        List<Integer> requestsLongerThan(int limit) {
            return streams.stream().flatMap(s -> s.requestsLongerThan(limit).stream()).toList();
        }

        List<Integer> offsetsOtherThanZero() {
            return streams.stream().flatMap(s -> s.offsetsOtherThanZero().stream()).toList();
        }
    }

    /** A stream whose first bulk read fails, to prove the close happens on the failure path too. */
    private static final class FailingInputStream extends InputStream {

        private final String message;

        FailingInputStream(String message) {
            this.message = message;
        }

        @Override
        public int read() throws IOException {
            throw new IOException(message);
        }

        @Override
        public int read(byte[] destination, int offset, int length) throws IOException {
            throw new IOException(message);
        }
    }
}
