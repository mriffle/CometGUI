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

package org.cometgui.provenance.io;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link AtomicDocumentWriter}.
 *
 * <p><strong>Where every expected value in this file came from.</strong> Not from the class under
 * test, and not from any Java code. Every expected byte sequence below was typed out by hand; the
 * hexadecimal forms were produced independently on the command line ({@code printf ... | od -An
 * -tx1}, cross-checked against {@code python3 -c "...encode('utf-8')"}) and then transcribed as
 * literals. Every expected call sequence is a literal {@code List.of(...)}. Every expected length
 * is a literal number.
 *
 * <p>That rule is the whole point of the file. An expected value obtained by asking {@code
 * AtomicDocumentWriter} what it did -- reading back the array it was handed, deriving a buffer size
 * from the production constant, comparing a recorded sequence against a sequence the production
 * code also produced -- agrees with the implementation by construction and cannot fail, however
 * wrong the implementation is. Two properties in particular can only be proved this way: the
 * <em>order</em> of sync, rename and directory sync, which no inspection of the resulting file can
 * see, and the absence of a temporary file, which is invisible unless the directory listing itself
 * is asserted.
 *
 * <p>Test groups, in the order of the work unit's acceptance conditions: (1) round trip, (2) no
 * litter, (3) sync order, (4) fault injection mid-write, (5) interruption actually performed, (6)
 * concurrent reader torture, (7) a failed directory sync is survivable, (8) rejections. Group 9
 * covers the cleanup path when the temporary file itself cannot be removed.
 */
class AtomicDocumentWriterTest {

    // ---------------------------------------------------------------------------------------
    // Hand-typed fixtures.  See the class Javadoc for their provenance.
    // ---------------------------------------------------------------------------------------

    /** The name every test writes to, so that a directory listing can be asserted exactly. */
    private static final String TARGET_NAME = "provenance.json";

    /** The text form of a small manifest, typed as text. */
    private static final String SCHEMA_ONE_TEXT = "{\"schemaVersion\":1}\n";

    /**
     * The same manifest, typed as bytes, produced by {@code printf '{"schemaVersion":1}\n' | od -An
     * -tx1} rather than by encoding {@link #SCHEMA_ONE_TEXT} in Java. The two are deliberately
     * independent: a round-trip assertion of text against text would pass even if the writer chose
     * the wrong charset.
     */
    private static final byte[] SCHEMA_ONE_BYTES = {
        0x7b, 0x22, 0x73, 0x63, 0x68, 0x65, 0x6d, 0x61, 0x56, 0x65,
        0x72, 0x73, 0x69, 0x6f, 0x6e, 0x22, 0x3a, 0x31, 0x7d, 0x0a
    };

    /** Length of {@link #SCHEMA_ONE_BYTES}, counted by {@code wc -c}, not by {@code .length}. */
    private static final long SCHEMA_ONE_LENGTH = 20L;

    /** Text with characters outside ASCII: a micro sign (U+00B5) and a check mark (U+2713). */
    private static final String NON_ASCII_TEXT = "m/z µ ✓";

    /**
     * {@link #NON_ASCII_TEXT} in UTF-8, from {@code printf 'm/z \302\265 \342\234\223' | od -An
     * -tx1}. Ten bytes for seven characters. Under ISO-8859-1 it would be seven bytes and under
     * windows-1252 the check mark would not encode at all, so this array is what distinguishes "the
     * writer uses UTF-8" from "the writer uses whatever the host defaults to".
     */
    private static final byte[] NON_ASCII_UTF8 = {
        0x6d,
        0x2f,
        0x7a,
        0x20,
        (byte) 0xc2,
        (byte) 0xb5,
        0x20,
        (byte) 0xe2,
        (byte) 0x9c,
        (byte) 0x93
    };

    /** A binary document with a NUL, a high byte and a newline in it. */
    private static final byte[] BINARY_DOCUMENT = {
        0x00, 0x01, 0x7f, (byte) 0x80, (byte) 0xff, 0x0a
    };

    /** The same six bytes, typed a second time, as the expectation the read-back is compared to. */
    private static final byte[] BINARY_DOCUMENT_READ_BACK = {
        0x00, 0x01, 0x7f, (byte) 0x80, (byte) 0xff, 0x0a
    };

    /** The document already on disk in the tests that require the old file to survive. */
    private static final String OLD_DOCUMENT = "old-provenance\n";

    /** The document a successful write puts there instead. Deliberately a different length. */
    private static final String NEW_DOCUMENT = "new-provenance-document\n";

    /** Length of {@link #NEW_DOCUMENT}, counted by {@code wc -c}. */
    private static final long NEW_DOCUMENT_LENGTH = 24L;

    /** What the recording durability reports when the target does not exist yet. */
    private static final String ABSENT = "<absent>";

    /** Message of the failure injected halfway through a document. */
    private static final String INJECTED_FAILURE = "the serialiser gave up halfway through";

    /** How long any test is willing to wait for another thread before failing. */
    private static final long TIMEOUT_SECONDS = 30L;

    // ---------------------------------------------------------------------------------------
    // Group 1 -- round trip.  The bytes that come back are the bytes that went in.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("group 1: text is written as UTF-8 and reads back byte for byte")
    void textIsWrittenAsUtf8(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);

        AtomicDocumentWriter.write(target, SCHEMA_ONE_TEXT);

        assertAll(
                () -> assertArrayEquals(SCHEMA_ONE_BYTES, Files.readAllBytes(target)),
                () -> assertEquals(SCHEMA_ONE_LENGTH, Files.size(target)));
    }

    @Test
    @DisplayName("group 1: non-ASCII text is UTF-8, not the platform default charset")
    void nonAsciiTextIsUtf8(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);

        AtomicDocumentWriter.write(target, NON_ASCII_TEXT);

        assertAll(
                () -> assertArrayEquals(NON_ASCII_UTF8, Files.readAllBytes(target)),
                () -> assertEquals(10L, Files.size(target)),
                () -> assertEquals("m/z µ ✓", Files.readString(target, UTF_8)));
    }

    @Test
    @DisplayName("group 1: an array of bytes reads back as the document it spells")
    void bytesAreWrittenExactly(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);

        AtomicDocumentWriter.write(target, SCHEMA_ONE_BYTES);

        assertAll(
                () -> assertEquals("{\"schemaVersion\":1}\n", Files.readString(target, UTF_8)),
                () -> assertEquals(SCHEMA_ONE_LENGTH, Files.size(target)));
    }

    @Test
    @DisplayName("group 1: binary content survives, NUL and high bytes included")
    void binaryContentSurvives(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);

        AtomicDocumentWriter.write(target, BINARY_DOCUMENT);

        assertAll(
                () -> assertArrayEquals(BINARY_DOCUMENT_READ_BACK, Files.readAllBytes(target)),
                () -> assertEquals(6L, Files.size(target)));
    }

    @Test
    @DisplayName("group 1: a content writer's chunks arrive concatenated and in order")
    void contentWriterChunksArriveInOrder(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);

        AtomicDocumentWriter.write(
                target,
                out -> {
                    out.write("{\"schema".getBytes(UTF_8));
                    out.write("Version\"".getBytes(UTF_8));
                    out.write(":1}\n".getBytes(UTF_8));
                });

        assertAll(
                () -> assertArrayEquals(SCHEMA_ONE_BYTES, Files.readAllBytes(target)),
                () -> assertEquals(SCHEMA_ONE_LENGTH, Files.size(target)));
    }

    @Test
    @DisplayName("group 1: an empty document is a real, empty file")
    void anEmptyDocumentIsAnEmptyFile(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);

        AtomicDocumentWriter.write(target, new byte[0]);

        assertAll(
                () -> assertTrue(Files.isRegularFile(target)),
                () -> assertEquals(0L, Files.size(target)),
                () -> assertEquals(List.of(TARGET_NAME), listing(dir)));
    }

    @Test
    @DisplayName("group 1: replacing a long document with a short one leaves no tail behind")
    void aShorterDocumentReplacesALongerOneCompletely(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);
        Files.writeString(target, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n", UTF_8);

        AtomicDocumentWriter.write(target, "short\n");

        assertAll(
                () -> assertEquals("short\n", Files.readString(target, UTF_8)),
                () -> assertEquals(6L, Files.size(target)));
    }

    @Test
    @DisplayName("group 1: a document larger than the internal buffer round-trips unchanged")
    void aLargeDocumentRoundTrips(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);
        byte[] large = repeated((byte) 'x', 1_048_576);

        AtomicDocumentWriter.write(target, out -> out.write(large));

        byte[] readBack = Files.readAllBytes(target);
        assertAll(
                () -> assertEquals(1_048_576L, Files.size(target)),
                () -> assertEquals(1_048_576, readBack.length),
                () -> assertEquals(0, countOtherThan((byte) 'x', readBack)));
    }

    // ---------------------------------------------------------------------------------------
    // Group 2 -- no litter.  A leaked temporary file is invisible unless the listing is asserted.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName(
            "group 2: after a successful write the directory holds the target and nothing else")
    void aSuccessfulWriteLeavesNothingBehind(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);

        AtomicDocumentWriter.write(target, SCHEMA_ONE_TEXT);

        assertEquals(List.of(TARGET_NAME), listing(dir));
    }

    @Test
    @DisplayName("group 2: repeated writes do not accumulate temporary files")
    void repeatedWritesDoNotAccumulateTemporaryFiles(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);

        for (int i = 0; i < 25; i++) {
            AtomicDocumentWriter.write(target, SCHEMA_ONE_TEXT);
        }

        assertAll(
                () -> assertEquals(List.of(TARGET_NAME), listing(dir)),
                () -> assertArrayEquals(SCHEMA_ONE_BYTES, Files.readAllBytes(target)));
    }

    @Test
    @DisplayName("group 2: the temporary file is created beside the target, not in the system tmp")
    void theTemporaryFileIsBesideTheTarget(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);
        RecordingDurability durability = new RecordingDurability(target, new ArrayList<>());

        AtomicDocumentWriter.write(target, out -> out.write(SCHEMA_ONE_BYTES), durability);

        Path temporary = durability.movedFrom();
        String temporaryName = dir.relativize(temporary).toString();
        assertAll(
                () -> assertEquals(dir, temporary.getParent()),
                () ->
                        assertTrue(
                                temporaryName.startsWith("provenance.json.tmp-"),
                                () -> "temporary file was named " + temporaryName),
                () -> assertEquals(dir.resolve(TARGET_NAME), durability.movedTo()));
    }

    // ---------------------------------------------------------------------------------------
    // Group 3 -- sync order.  The property no finished file can show.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("group 3: the operations happen in exactly the order write, sync, move, sync")
    void theDurableStepsHappenInOneFixedOrder(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);
        Files.writeString(target, OLD_DOCUMENT, UTF_8);
        List<String> operations = new ArrayList<>();
        RecordingDurability durability = new RecordingDurability(target, operations);

        boolean directorySynced =
                AtomicDocumentWriter.write(
                        target,
                        out -> {
                            operations.add("write");
                            out.write(NEW_DOCUMENT.getBytes(UTF_8));
                        },
                        durability);

        assertAll(
                () ->
                        assertEquals(
                                List.of("write", "syncFile", "move", "syncDirectory"), operations),
                // The target still holds the OLD document when the data is forced, and the NEW one
                // when the directory is forced.  That is the ordering, read off the filesystem
                // rather than off the sequence above: a sync after the rename would see the new
                // document at syncFile time, and a rename before the sync would see it too.
                () -> assertEquals("old-provenance\n", durability.targetContentAtSyncFile()),
                () ->
                        assertEquals(
                                "new-provenance-document\n", durability.targetContentAtSyncDir()),
                // The whole document was flushed before the force: a force over a half-filled
                // buffer forces the wrong thing.
                () -> assertEquals(NEW_DOCUMENT_LENGTH, durability.temporarySizeAtSyncFile()),
                () -> assertTrue(durability.channelOpenAtSyncFile(), "forced a closed channel"),
                () ->
                        assertFalse(
                                durability.channelOpenAtMove(), "renamed before closing the file"),
                () -> assertTrue(directorySynced),
                () -> assertEquals("new-provenance-document\n", Files.readString(target, UTF_8)));
    }

    @Test
    @DisplayName("group 3: the same order holds when the target did not exist beforehand")
    void theOrderHoldsForANewFileToo(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);
        List<String> operations = new ArrayList<>();
        RecordingDurability durability = new RecordingDurability(target, operations);

        AtomicDocumentWriter.write(
                target,
                out -> {
                    operations.add("write");
                    out.write(NEW_DOCUMENT.getBytes(UTF_8));
                },
                durability);

        assertAll(
                () ->
                        assertEquals(
                                List.of("write", "syncFile", "move", "syncDirectory"), operations),
                () -> assertEquals(ABSENT, durability.targetContentAtSyncFile()),
                () ->
                        assertEquals(
                                "new-provenance-document\n", durability.targetContentAtSyncDir()),
                () -> assertTrue(durability.temporaryPresentAtMove()));
    }

    @Test
    @DisplayName(
            "group 3: on Linux and macOS the shipped durability really does sync the directory")
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void theShippedDurabilitySyncsTheDirectoryOnThisPlatform(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);

        boolean directorySynced =
                AtomicDocumentWriter.write(
                        target, out -> out.write(SCHEMA_ONE_BYTES), new FileSystemDurability());

        assertAll(
                () -> assertTrue(directorySynced, "the platform refused to sync the directory"),
                () -> assertEquals("{\"schemaVersion\":1}\n", Files.readString(target, UTF_8)));
    }

    // ---------------------------------------------------------------------------------------
    // Group 4 -- fault injection mid-write.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("group 4: a writer that fails halfway leaves no file where there was none")
    void aFailedWriteCreatesNothing(@TempDir Path dir) {
        Path target = dir.resolve(TARGET_NAME);

        IOException thrown =
                assertThrows(
                        IOException.class,
                        () -> AtomicDocumentWriter.write(target, halfThenFail()));

        assertAll(
                () -> assertEquals(INJECTED_FAILURE, thrown.getMessage()),
                () -> assertFalse(Files.exists(target), "a failed write created the target"),
                () -> assertEquals(List.of(), listing(dir)));
    }

    @Test
    @DisplayName("group 4: a writer that fails halfway leaves the existing document byte-identical")
    void aFailedWriteLeavesTheExistingDocumentIntact(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);
        Files.writeString(target, OLD_DOCUMENT, UTF_8);

        IOException thrown =
                assertThrows(
                        IOException.class,
                        () -> AtomicDocumentWriter.write(target, halfThenFail()));

        assertAll(
                () -> assertEquals(INJECTED_FAILURE, thrown.getMessage()),
                () -> assertEquals("old-provenance\n", Files.readString(target, UTF_8)),
                () -> assertEquals(15L, Files.size(target)),
                () -> assertEquals(List.of(TARGET_NAME), listing(dir)));
    }

    @Test
    @DisplayName("group 4: an unchecked exception is treated exactly like an IOException")
    void anUncheckedExceptionAlsoLeavesNothingBehind(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);
        Files.writeString(target, OLD_DOCUMENT, UTF_8);

        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                AtomicDocumentWriter.write(
                                        target,
                                        out -> {
                                            out.write(NEW_DOCUMENT.getBytes(UTF_8));
                                            out.flush();
                                            throw new IllegalStateException(INJECTED_FAILURE);
                                        }));

        assertAll(
                () -> assertEquals(INJECTED_FAILURE, thrown.getMessage()),
                () -> assertEquals("old-provenance\n", Files.readString(target, UTF_8)),
                () -> assertEquals(List.of(TARGET_NAME), listing(dir)));
    }

    @Test
    @DisplayName("group 4: an Error is treated exactly like an IOException")
    void anErrorAlsoLeavesNothingBehind(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);
        Files.writeString(target, OLD_DOCUMENT, UTF_8);

        InjectedError thrown =
                assertThrows(
                        InjectedError.class,
                        () ->
                                AtomicDocumentWriter.write(
                                        target,
                                        out -> {
                                            out.write(NEW_DOCUMENT.getBytes(UTF_8));
                                            throw new InjectedError(INJECTED_FAILURE);
                                        }));

        assertAll(
                () -> assertEquals(INJECTED_FAILURE, thrown.getMessage()),
                () -> assertEquals("old-provenance\n", Files.readString(target, UTF_8)),
                () -> assertEquals(List.of(TARGET_NAME), listing(dir)));
    }

    @Test
    @DisplayName("group 4: a failure in the move itself leaves the old document in place")
    void aFailedMoveLeavesTheOldDocumentInPlace(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);
        Files.writeString(target, OLD_DOCUMENT, UTF_8);
        Durability moveFails =
                new DelegatingDurability() {
                    @Override
                    public void moveIntoPlace(Path temporary, Path movedTo) throws IOException {
                        throw new IOException(INJECTED_FAILURE);
                    }
                };

        IOException thrown =
                assertThrows(
                        IOException.class,
                        () ->
                                AtomicDocumentWriter.write(
                                        target,
                                        out -> out.write(NEW_DOCUMENT.getBytes(UTF_8)),
                                        moveFails));

        assertAll(
                () -> assertEquals(INJECTED_FAILURE, thrown.getMessage()),
                () -> assertEquals("old-provenance\n", Files.readString(target, UTF_8)),
                () -> assertEquals(List.of(TARGET_NAME), listing(dir)));
    }

    // ---------------------------------------------------------------------------------------
    // Group 5 -- interruption, actually performed.  Exit gate item 5 is about an interrupted
    // finalise, and the only way to test an interrupted finalise is to interrupt one.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("group 5: interrupting a write in progress leaves the old document untouched")
    void anInterruptedWriteLeavesTheOldDocumentUntouched(@TempDir Path dir)
            throws IOException, InterruptedException {
        Path target = dir.resolve(TARGET_NAME);
        Files.writeString(target, OLD_DOCUMENT, UTF_8);
        byte[] firstHalf = repeated((byte) 'A', 1_048_576);
        byte[] secondHalf = repeated((byte) 'B', 1_048_576);
        CountDownLatch halfWritten = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptStatusAtTheEnd = new AtomicBoolean();

        Thread writer =
                new Thread(
                        () -> {
                            try {
                                AtomicDocumentWriter.write(
                                        target,
                                        out -> {
                                            out.write(firstHalf);
                                            out.flush();
                                            halfWritten.countDown();
                                            try {
                                                neverReleased.await();
                                            } catch (InterruptedException interrupted) {
                                                // Restore the flag and carry on writing.  The next
                                                // touch of the interruptible channel is where the
                                                // production code meets the interrupt.
                                                Thread.currentThread().interrupt();
                                            }
                                            out.write(secondHalf);
                                        });
                            } catch (Throwable failure) {
                                thrown.set(failure);
                            } finally {
                                interruptStatusAtTheEnd.set(Thread.currentThread().isInterrupted());
                            }
                        },
                        "atomic-document-writer-under-interruption");
        writer.start();

        assertTrue(halfWritten.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "writer never started");
        writer.interrupt();
        writer.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));

        assertAll(
                () -> assertFalse(writer.isAlive(), "the interrupted writer never finished"),
                () -> assertInstanceOf(ClosedByInterruptException.class, thrown.get()),
                () ->
                        assertTrue(
                                interruptStatusAtTheEnd.get(),
                                "the interrupt status was swallowed by the writer"),
                () -> assertEquals("old-provenance\n", Files.readString(target, UTF_8)),
                () -> assertEquals(15L, Files.size(target)),
                () -> assertEquals(List.of(TARGET_NAME), listing(dir)));
    }

    @Test
    @DisplayName("group 5: interrupting a write of a file that did not exist creates no file")
    void anInterruptedWriteOfANewFileCreatesNothing(@TempDir Path dir)
            throws IOException, InterruptedException {
        Path target = dir.resolve(TARGET_NAME);
        byte[] firstHalf = repeated((byte) 'A', 1_048_576);
        byte[] secondHalf = repeated((byte) 'B', 1_048_576);
        CountDownLatch halfWritten = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        AtomicReference<Throwable> thrown = new AtomicReference<>();

        Thread writer =
                new Thread(
                        () -> {
                            try {
                                AtomicDocumentWriter.write(
                                        target,
                                        out -> {
                                            out.write(firstHalf);
                                            out.flush();
                                            halfWritten.countDown();
                                            try {
                                                neverReleased.await();
                                            } catch (InterruptedException interrupted) {
                                                Thread.currentThread().interrupt();
                                            }
                                            out.write(secondHalf);
                                        });
                            } catch (Throwable failure) {
                                thrown.set(failure);
                            }
                        },
                        "atomic-document-writer-under-interruption-new-file");
        writer.start();

        assertTrue(halfWritten.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "writer never started");
        writer.interrupt();
        writer.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));

        assertAll(
                () -> assertFalse(writer.isAlive(), "the interrupted writer never finished"),
                () -> assertInstanceOf(ClosedByInterruptException.class, thrown.get()),
                () -> assertFalse(Files.exists(target), "an interrupted write created the target"),
                () -> assertEquals(List.of(), listing(dir)));
    }

    // ---------------------------------------------------------------------------------------
    // Group 6 -- concurrent reader torture.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("group 6: a reader racing a rewriter sees whole documents only, never a prefix")
    void aConcurrentReaderNeverSeesAPartialDocument(@TempDir Path dir)
            throws IOException, InterruptedException {
        Path target = dir.resolve(TARGET_NAME);
        byte[] documentA = repeated((byte) 'A', 200_000);
        byte[] documentB = repeated((byte) 'B', 150_000);
        AtomicDocumentWriter.write(target, documentA);

        AtomicBoolean writing = new AtomicBoolean(true);
        AtomicInteger observedA = new AtomicInteger();
        AtomicInteger observedB = new AtomicInteger();
        AtomicInteger observedPartial = new AtomicInteger();
        AtomicInteger observedMissing = new AtomicInteger();
        AtomicInteger partialLength = new AtomicInteger(-1);
        AtomicReference<Throwable> readerFailure = new AtomicReference<>();

        Thread reader =
                new Thread(
                        () -> {
                            try {
                                while (writing.get()) {
                                    byte[] seen;
                                    try {
                                        seen = Files.readAllBytes(target);
                                    } catch (NoSuchFileException absent) {
                                        observedMissing.incrementAndGet();
                                        continue;
                                    }
                                    if (Arrays.equals(documentA, seen)) {
                                        observedA.incrementAndGet();
                                    } else if (Arrays.equals(documentB, seen)) {
                                        observedB.incrementAndGet();
                                    } else {
                                        observedPartial.incrementAndGet();
                                        partialLength.set(seen.length);
                                    }
                                }
                            } catch (Throwable failure) {
                                readerFailure.set(failure);
                            }
                        },
                        "atomic-document-reader");
        reader.start();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        int rewrites = 0;
        while (rewrites < 60
                || ((observedA.get() == 0 || observedB.get() == 0)
                        && System.nanoTime() < deadline)) {
            AtomicDocumentWriter.write(target, documentB);
            AtomicDocumentWriter.write(target, documentA);
            rewrites += 2;
        }
        writing.set(false);
        reader.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));

        int finalRewrites = rewrites;
        assertAll(
                () -> assertFalse(reader.isAlive(), "the reader never finished"),
                () -> assertNull(readerFailure.get(), "the reader failed"),
                () ->
                        assertEquals(
                                0,
                                observedPartial.get(),
                                () ->
                                        "the reader saw a document of "
                                                + partialLength.get()
                                                + " bytes, which is neither of the two written"),
                () -> assertEquals(0, observedMissing.get(), "the target vanished mid-rename"),
                () -> assertTrue(observedA.get() > 0, "the reader never observed document A"),
                () -> assertTrue(observedB.get() > 0, "the reader never observed document B"),
                () -> assertTrue(finalRewrites >= 60, "too few rewrites to be a race at all"),
                () -> assertArrayEquals(documentA, Files.readAllBytes(target)),
                () -> assertEquals(List.of(TARGET_NAME), listing(dir)));
    }

    // ---------------------------------------------------------------------------------------
    // Group 7 -- a directory sync that fails is not a write that failed.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("group 7: a platform that refuses to sync a directory still gets its document")
    void aFailedDirectorySyncStillLeavesTheDocument(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);
        Files.writeString(target, OLD_DOCUMENT, UTF_8);
        Durability windowsLike =
                new DelegatingDurability() {
                    @Override
                    public void syncDirectory(Path directory) throws IOException {
                        throw new IOException("a directory cannot be opened as a channel here");
                    }
                };

        boolean directorySynced =
                AtomicDocumentWriter.write(
                        target, out -> out.write(NEW_DOCUMENT.getBytes(UTF_8)), windowsLike);

        assertAll(
                () -> assertFalse(directorySynced, "a refused directory sync reported success"),
                () -> assertEquals("new-provenance-document\n", Files.readString(target, UTF_8)),
                () -> assertEquals(NEW_DOCUMENT_LENGTH, Files.size(target)),
                () -> assertEquals(List.of(TARGET_NAME), listing(dir)));
    }

    // ---------------------------------------------------------------------------------------
    // Group 8 -- rejections, by type and by message.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("group 8: a null target is rejected by name, through every overload")
    void aNullTargetIsRejected() {
        NullPointerException fromContentWriter =
                assertThrows(
                        NullPointerException.class,
                        () -> AtomicDocumentWriter.write(null, out -> out.write(1)));
        NullPointerException fromBytes =
                assertThrows(
                        NullPointerException.class,
                        () -> AtomicDocumentWriter.write(null, BINARY_DOCUMENT));
        NullPointerException fromText =
                assertThrows(
                        NullPointerException.class,
                        () -> AtomicDocumentWriter.write(null, SCHEMA_ONE_TEXT));

        assertAll(
                () -> assertEquals("target", fromContentWriter.getMessage()),
                () -> assertEquals("target", fromBytes.getMessage()),
                () -> assertEquals("target", fromText.getMessage()));
    }

    @Test
    @DisplayName("group 8: null content is rejected by name, through every overload")
    void nullContentIsRejected(@TempDir Path dir) {
        Path target = dir.resolve(TARGET_NAME);

        NullPointerException noContentWriter =
                assertThrows(
                        NullPointerException.class,
                        () -> AtomicDocumentWriter.write(target, (ContentWriter) null));
        NullPointerException noBytes =
                assertThrows(
                        NullPointerException.class,
                        () -> AtomicDocumentWriter.write(target, (byte[]) null));
        NullPointerException noText =
                assertThrows(
                        NullPointerException.class,
                        () -> AtomicDocumentWriter.write(target, (CharSequence) null));

        assertAll(
                () -> assertEquals("content", noContentWriter.getMessage()),
                () -> assertEquals("content", noBytes.getMessage()),
                () -> assertEquals("content", noText.getMessage()),
                () -> assertEquals(List.of(), listing(dir)));
    }

    @Test
    @DisplayName("group 8: a null durability is rejected by name")
    void aNullDurabilityIsRejected(@TempDir Path dir) {
        Path target = dir.resolve(TARGET_NAME);

        NullPointerException thrown =
                assertThrows(
                        NullPointerException.class,
                        () ->
                                AtomicDocumentWriter.write(
                                        target, out -> out.write(SCHEMA_ONE_BYTES), null));

        assertEquals("durability", thrown.getMessage());
    }

    @Test
    @DisplayName("group 8: a target whose parent directory does not exist is rejected")
    void aMissingParentDirectoryIsRejected(@TempDir Path dir) {
        Path missing = dir.resolve("no-such-directory");
        Path target = missing.resolve(TARGET_NAME);

        NoSuchFileException thrown =
                assertThrows(
                        NoSuchFileException.class,
                        () -> AtomicDocumentWriter.write(target, SCHEMA_ONE_TEXT));

        assertAll(
                () ->
                        assertEquals(
                                missing + ": the directory to write into does not exist",
                                thrown.getMessage()),
                () -> assertEquals(List.of(), listing(dir)));
    }

    @Test
    @DisplayName("group 8: a target that is an existing directory is rejected in our own words")
    void anExistingDirectoryAsTargetIsRejected(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);
        Files.createDirectory(target);
        Files.writeString(target.resolve("occupant.txt"), "not yours\n", UTF_8);

        IOException thrown =
                assertThrows(
                        IOException.class,
                        () -> AtomicDocumentWriter.write(target, SCHEMA_ONE_TEXT));

        assertAll(
                () ->
                        assertEquals(
                                "Cannot write " + target + ": it is an existing directory",
                                thrown.getMessage()),
                () -> assertEquals(List.of(TARGET_NAME), listing(dir)),
                () -> assertEquals(List.of("occupant.txt"), listing(target)));
    }

    @Test
    @DisplayName("group 8: the class is a set of static operations and refuses instantiation")
    void theClassRefusesInstantiation() throws NoSuchMethodException {
        Constructor<AtomicDocumentWriter> constructor =
                AtomicDocumentWriter.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException thrown =
                assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertAll(
                () -> assertInstanceOf(AssertionError.class, thrown.getCause()),
                () ->
                        assertEquals(
                                "AtomicDocumentWriter is a utility class and is never instantiated",
                                thrown.getCause().getMessage()));
    }

    // ---------------------------------------------------------------------------------------
    // Group 9 -- when the debris itself cannot be removed, the original failure still wins.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("group 9: a cleanup failure is suppressed onto the original, never substituted")
    void aCleanupFailureIsSuppressedNotSubstituted(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);
        Files.writeString(target, OLD_DOCUMENT, UTF_8);

        // Replace the temporary file with a non-empty directory of the same name, so that the
        // writer's own cleanup fails.  Nothing else can make deleteIfExists fail portably.
        IOException thrown =
                assertThrows(
                        IOException.class,
                        () ->
                                AtomicDocumentWriter.write(
                                        target,
                                        out -> {
                                            out.write(NEW_DOCUMENT.getBytes(UTF_8));
                                            Path temporary = onlyTemporaryFileIn(dir);
                                            Files.delete(temporary);
                                            Files.createDirectory(temporary);
                                            Files.writeString(
                                                    temporary.resolve("occupant.txt"),
                                                    "in the way\n",
                                                    UTF_8);
                                            throw new IOException(INJECTED_FAILURE);
                                        }));

        Throwable[] suppressed = thrown.getSuppressed();
        assertAll(
                () -> assertEquals(INJECTED_FAILURE, thrown.getMessage()),
                () -> assertEquals(1, suppressed.length),
                () -> assertInstanceOf(DirectoryNotEmptyException.class, suppressed[0]),
                () -> assertEquals("old-provenance\n", Files.readString(target, UTF_8)),
                () -> assertEquals(15L, Files.size(target)));
    }

    // ---------------------------------------------------------------------------------------
    // Fixtures and recorders.
    // ---------------------------------------------------------------------------------------

    /** A content writer that produces half a document and then gives up. */
    private static ContentWriter halfThenFail() {
        return out -> {
            out.write("{\"schemaVer".getBytes(UTF_8));
            out.flush();
            throw new IOException(INJECTED_FAILURE);
        };
    }

    /** The names in a directory, sorted, so that a listing can be compared to a literal list. */
    private static List<String> listing(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(entry -> directory.relativize(entry).toString()).sorted().toList();
        }
    }

    /** The one entry in the directory that is not the target: the temporary file, mid-write. */
    private static Path onlyTemporaryFileIn(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            Path target = directory.resolve(TARGET_NAME);
            return entries.filter(entry -> !target.equals(entry))
                    .findFirst()
                    .orElseThrow(() -> new IOException("no temporary file in " + directory));
        }
    }

    /** An array of {@code length} copies of one byte. */
    private static byte[] repeated(byte value, int length) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, value);
        return bytes;
    }

    /** How many bytes of an array are not the expected one; zero proves the whole array. */
    private static int countOtherThan(byte expected, byte[] bytes) {
        int wrong = 0;
        for (byte actual : bytes) {
            if (actual != expected) {
                wrong++;
            }
        }
        return wrong;
    }

    /** An {@link Error} distinguishable from anything the JVM or JUnit throws on its own. */
    private static final class InjectedError extends Error {

        private static final long serialVersionUID = 1L;

        InjectedError(String message) {
            super(message);
        }
    }

    /**
     * A {@link Durability} that does the real thing, so that a subclass can replace exactly one
     * step and leave the rest genuinely working.
     */
    private static class DelegatingDurability implements Durability {

        private final Durability real = new FileSystemDurability();

        @Override
        public void syncFile(FileChannel channel) throws IOException {
            real.syncFile(channel);
        }

        @Override
        public void moveIntoPlace(Path temporary, Path target) throws IOException {
            real.moveIntoPlace(temporary, target);
        }

        @Override
        public void syncDirectory(Path directory) throws IOException {
            real.syncDirectory(directory);
        }
    }

    /**
     * A {@link Durability} that performs every real operation and records what the filesystem
     * looked like at the moment of each one.
     *
     * <p>Two independent records come out of it. The first is the sequence of operation names,
     * compared against a literal list, so that a reordering fails. The second is what the target
     * file actually held when the data was forced and when the directory was forced, which pins the
     * rename between the two without trusting the sequence at all: if the rename happened before
     * the data sync, the target would already hold the new document at {@code syncFile} time.
     */
    private static final class RecordingDurability implements Durability {

        private final Durability real = new FileSystemDurability();
        private final Path target;
        private final List<String> operations;
        private String targetContentAtSyncFile = "<never called>";
        private String targetContentAtSyncDir = "<never called>";
        private long temporarySizeAtSyncFile = -1L;
        private boolean channelOpenAtSyncFile;
        private boolean channelOpenAtMove = true;
        private boolean temporaryPresentAtMove;
        private FileChannel syncedChannel;
        private Path movedFrom;
        private Path movedTo;

        RecordingDurability(Path target, List<String> operations) {
            this.target = target;
            this.operations = operations;
        }

        @Override
        public void syncFile(FileChannel channel) throws IOException {
            operations.add("syncFile");
            syncedChannel = channel;
            channelOpenAtSyncFile = channel.isOpen();
            temporarySizeAtSyncFile = channel.size();
            targetContentAtSyncFile = targetContent();
            real.syncFile(channel);
        }

        @Override
        public void moveIntoPlace(Path temporary, Path destination) throws IOException {
            operations.add("move");
            movedFrom = temporary;
            movedTo = destination;
            temporaryPresentAtMove = Files.exists(temporary);
            channelOpenAtMove = syncedChannel != null && syncedChannel.isOpen();
            real.moveIntoPlace(temporary, destination);
        }

        @Override
        public void syncDirectory(Path directory) throws IOException {
            operations.add("syncDirectory");
            targetContentAtSyncDir = targetContent();
            real.syncDirectory(directory);
        }

        private String targetContent() throws IOException {
            return Files.exists(target) ? Files.readString(target, UTF_8) : ABSENT;
        }

        String targetContentAtSyncFile() {
            return targetContentAtSyncFile;
        }

        String targetContentAtSyncDir() {
            return targetContentAtSyncDir;
        }

        long temporarySizeAtSyncFile() {
            return temporarySizeAtSyncFile;
        }

        boolean channelOpenAtSyncFile() {
            return channelOpenAtSyncFile;
        }

        boolean channelOpenAtMove() {
            return channelOpenAtMove;
        }

        boolean temporaryPresentAtMove() {
            return temporaryPresentAtMove;
        }

        Path movedFrom() {
            return movedFrom;
        }

        Path movedTo() {
            return movedTo;
        }
    }
}
