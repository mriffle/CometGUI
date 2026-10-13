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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for the bounded retry of a refused rename in {@link AtomicDocumentWriter} (Phase 13 design
 * decision P13-1).
 *
 * <p><strong>Where the refusals come from.</strong> This platform never refuses a rename because
 * another process holds the target -- that is Windows behaviour -- so the refusals are injected
 * through the {@link Durability} seam: a durability that throws a scripted exception on chosen
 * attempts and otherwise performs the <em>real</em> rename of the real temporary file in a real
 * directory. Everything else -- the temporary file, the forcing, the target, the cleanup -- is the
 * production path on real files.
 *
 * <p><strong>Where the expected values come from.</strong> Not from the class under test. The pause
 * schedule {@code 10, 20, 40, 80, 160, 320, 500, 500, 500} and its total {@code 2130} were worked
 * out by hand from the policy in the work log (first pause 10 ms, doubling, capped at 500 ms, ten
 * attempts) and are typed here as literals; no expectation reads {@code MOVE_ATTEMPTS} or either
 * pause constant. Every expected call sequence is a literal list.
 *
 * <p><strong>Nothing here sleeps.</strong> Every pause goes through a {@link RetryWaiter} that
 * records the requested duration and returns, or blocks on a latch that only an interrupt releases.
 */
class AtomicDocumentWriterRetryTest {

    private static final String TARGET_NAME = "provenance.json";

    /** The document already on disk. */
    private static final String OLD_DOCUMENT = "old-provenance\n";

    /** {@link #OLD_DOCUMENT} as bytes, from {@code printf 'old-provenance\n' | od -An -tx1}. */
    private static final byte[] OLD_DOCUMENT_BYTES = {
        0x6f, 0x6c, 0x64, 0x2d, 0x70, 0x72, 0x6f, 0x76, 0x65, 0x6e, 0x61, 0x6e, 0x63, 0x65, 0x0a
    };

    /** The document a successful write puts there instead. */
    private static final String NEW_DOCUMENT = "new-provenance-document\n";

    /** Length of {@link #NEW_DOCUMENT}, counted by {@code wc -c}. */
    private static final long NEW_DOCUMENT_LENGTH = 24L;

    /** What the recording durability reports when the target does not exist. */
    private static final String ABSENT = "<absent>";

    /** The full schedule of pauses when every attempt is refused, worked out by hand. */
    private static final List<Long> FULL_SCHEDULE =
            List.of(10L, 20L, 40L, 80L, 160L, 320L, 500L, 500L, 500L);

    private static final long TIMEOUT_SECONDS = 30L;

    // ---------------------------------------------------------------------------------------
    // Transient refusals, then success.
    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("transient refusals followed by success")
    class RefusedThenAccepted {

        @Test
        @DisplayName("three access-denied refusals, then the fourth attempt replaces the target")
        void threeAccessDeniedThenSuccess(@TempDir Path dir) throws IOException {
            Path target = dir.resolve(TARGET_NAME);
            Files.writeString(target, OLD_DOCUMENT, UTF_8);
            ScriptedDurability durability =
                    new ScriptedDurability(
                            target,
                            attempt ->
                                    attempt <= 3
                                            ? new AccessDeniedException("refusal #" + attempt)
                                            : null);
            RecordingWaiter waiter = new RecordingWaiter();

            boolean directorySynced = write(target, durability, waiter);

            assertAll(
                    () -> assertEquals("new-provenance-document\n", read(target)),
                    () -> assertEquals(NEW_DOCUMENT_LENGTH, Files.size(target)),
                    () -> assertEquals(List.of(TARGET_NAME), listing(dir)),
                    () -> assertEquals(4, durability.attempts()),
                    () -> assertEquals(List.of(10L, 20L, 40L), waiter.pauses()),
                    () ->
                            assertEquals(
                                    List.of(
                                            "syncFile",
                                            "move",
                                            "move",
                                            "move",
                                            "move",
                                            "syncDirectory"),
                                    durability.operations()),
                    // Every refused attempt left the old document whole where it was.
                    () ->
                            assertEquals(
                                    List.of(OLD_DOCUMENT, OLD_DOCUMENT, OLD_DOCUMENT, OLD_DOCUMENT),
                                    durability.targetAtEachAttempt()),
                    () -> assertEquals(1, durability.distinctTemporaries()),
                    () ->
                            assertEquals(
                                    List.of(true, true, true, true),
                                    durability.temporaryPresentAtEachAttempt()),
                    () -> assertTrue(directorySynced));
        }

        @Test
        @DisplayName("a bare FileSystemException (a sharing violation) is retried too")
        void bareFileSystemExceptionThenSuccess(@TempDir Path dir) throws IOException {
            Path target = dir.resolve(TARGET_NAME);
            ScriptedDurability durability =
                    new ScriptedDurability(
                            target,
                            attempt ->
                                    attempt <= 2
                                            ? new FileSystemException(
                                                    "a.tmp",
                                                    "provenance.json",
                                                    "The process cannot access the file")
                                            : null);
            RecordingWaiter waiter = new RecordingWaiter();

            write(target, durability, waiter);

            assertAll(
                    () -> assertEquals("new-provenance-document\n", read(target)),
                    () -> assertEquals(List.of(TARGET_NAME), listing(dir)),
                    () -> assertEquals(3, durability.attempts()),
                    () -> assertEquals(List.of(10L, 20L), waiter.pauses()),
                    () ->
                            assertEquals(
                                    List.of(ABSENT, ABSENT, ABSENT),
                                    durability.targetAtEachAttempt()));
        }

        @Test
        @DisplayName("success on the very last allowed attempt, after the whole schedule")
        void successOnTheLastAttempt(@TempDir Path dir) throws IOException {
            Path target = dir.resolve(TARGET_NAME);
            Files.writeString(target, OLD_DOCUMENT, UTF_8);
            ScriptedDurability durability =
                    new ScriptedDurability(
                            target,
                            attempt ->
                                    attempt <= 9
                                            ? new AccessDeniedException("refusal #" + attempt)
                                            : null);
            RecordingWaiter waiter = new RecordingWaiter();

            write(target, durability, waiter);

            assertAll(
                    () -> assertEquals("new-provenance-document\n", read(target)),
                    () -> assertEquals(List.of(TARGET_NAME), listing(dir)),
                    () -> assertEquals(10, durability.attempts()),
                    () -> assertEquals(FULL_SCHEDULE, waiter.pauses()));
        }

        @Test
        @DisplayName("a rename accepted first time never pauses")
        void noRefusalNoPause(@TempDir Path dir) throws IOException {
            Path target = dir.resolve(TARGET_NAME);
            ScriptedDurability durability = new ScriptedDurability(target, attempt -> null);
            RecordingWaiter waiter = new RecordingWaiter();

            write(target, durability, waiter);

            assertAll(
                    () -> assertEquals("new-provenance-document\n", read(target)),
                    () -> assertEquals(1, durability.attempts()),
                    () -> assertEquals(List.of(), waiter.pauses()),
                    () ->
                            assertEquals(
                                    List.of("syncFile", "move", "syncDirectory"),
                                    durability.operations()));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Refused until the bound.
    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("refused on every attempt")
    class RefusedThroughTheBound {

        @Test
        @DisplayName("an existing target survives byte for byte; the last refusal is thrown")
        void existingTargetSurvives(@TempDir Path dir) throws IOException {
            Path target = dir.resolve(TARGET_NAME);
            Files.writeString(target, OLD_DOCUMENT, UTF_8);
            List<IOException> refusals = new ArrayList<>();
            ScriptedDurability durability =
                    new ScriptedDurability(
                            target,
                            attempt -> {
                                IOException refusal =
                                        new AccessDeniedException("refusal #" + attempt);
                                refusals.add(refusal);
                                return refusal;
                            });
            RecordingWaiter waiter = new RecordingWaiter();

            AccessDeniedException thrown =
                    assertThrows(
                            AccessDeniedException.class, () -> write(target, durability, waiter));

            assertAll(
                    () -> assertEquals("refusal #10", thrown.getMessage()),
                    () -> assertSame(refusals.get(9), thrown),
                    () -> assertEquals(refusals.subList(0, 9), List.of(thrown.getSuppressed())),
                    () -> assertEquals(10, durability.attempts()),
                    () -> assertEquals(FULL_SCHEDULE, waiter.pauses()),
                    () -> assertEquals(2130L, waiter.total()),
                    () -> assertArrayEquals(OLD_DOCUMENT_BYTES, Files.readAllBytes(target)),
                    () -> assertEquals(List.of(TARGET_NAME), listing(dir)),
                    () ->
                            assertEquals(
                                    List.of(
                                            "syncFile",
                                            "move",
                                            "move",
                                            "move",
                                            "move",
                                            "move",
                                            "move",
                                            "move",
                                            "move",
                                            "move",
                                            "move"),
                                    durability.operations()));
        }

        @Test
        @DisplayName("an absent target stays absent and no temporary file remains")
        void absentTargetStaysAbsent(@TempDir Path dir) {
            Path target = dir.resolve(TARGET_NAME);
            ScriptedDurability durability =
                    new ScriptedDurability(
                            target,
                            attempt ->
                                    new FileSystemException(
                                            "a.tmp", "provenance.json", "sharing #" + attempt));
            RecordingWaiter waiter = new RecordingWaiter();

            FileSystemException thrown =
                    assertThrows(
                            FileSystemException.class, () -> write(target, durability, waiter));

            assertAll(
                    () -> assertEquals(FileSystemException.class, thrown.getClass()),
                    () -> assertEquals("sharing #10", thrown.getReason()),
                    () -> assertEquals(9, thrown.getSuppressed().length),
                    () -> assertEquals(10, durability.attempts()),
                    () -> assertEquals(FULL_SCHEDULE, waiter.pauses()),
                    () -> assertFalse(Files.exists(target), "a refused write created the target"),
                    () -> assertEquals(List.of(), listing(dir)));
        }

        @Test
        @DisplayName(
                "one exception instance thrown on every attempt is thrown, not self-suppressed")
        void sameInstanceEveryTime(@TempDir Path dir) throws IOException {
            Path target = dir.resolve(TARGET_NAME);
            Files.writeString(target, OLD_DOCUMENT, UTF_8);
            AccessDeniedException always = new AccessDeniedException("always the same");
            ScriptedDurability durability = new ScriptedDurability(target, attempt -> always);
            RecordingWaiter waiter = new RecordingWaiter();

            AccessDeniedException thrown =
                    assertThrows(
                            AccessDeniedException.class, () -> write(target, durability, waiter));

            assertAll(
                    () -> assertSame(always, thrown),
                    () -> assertEquals(0, thrown.getSuppressed().length),
                    () -> assertEquals(10, durability.attempts()),
                    () -> assertEquals(OLD_DOCUMENT, read(target)),
                    () -> assertEquals(List.of(TARGET_NAME), listing(dir)));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Failures that waiting cannot fix: attempted exactly once.
    // ---------------------------------------------------------------------------------------

    /** A {@code FileSystemException} subclass this project invents, to pin "exactly that class". */
    private static final class SomeOtherFileSystemException extends FileSystemException {

        private static final long serialVersionUID = 1L;

        SomeOtherFileSystemException(String file) {
            super(file);
        }
    }

    /** An {@link Error} distinguishable from anything the JVM or JUnit throws on its own. */
    private static final class InjectedError extends Error {

        private static final long serialVersionUID = 1L;

        InjectedError(String message) {
            super(message);
        }
    }

    static Stream<Arguments> nonRetryable() {
        return Stream.of(
                Arguments.of(
                        "AtomicMoveNotSupportedException",
                        new AtomicMoveNotSupportedException("a.tmp", "b", "not here")),
                Arguments.of("NoSuchFileException", new NoSuchFileException("a.tmp")),
                Arguments.of("DirectoryNotEmptyException", new DirectoryNotEmptyException("b")),
                Arguments.of("FileAlreadyExistsException", new FileAlreadyExistsException("b")),
                Arguments.of("NotDirectoryException", new NotDirectoryException("b")),
                Arguments.of(
                        "a project-invented FileSystemException subclass",
                        new SomeOtherFileSystemException("b")),
                Arguments.of("plain IOException", new IOException("disk on fire")),
                Arguments.of(
                        "UncheckedIOException",
                        new UncheckedIOException(new AccessDeniedException("wrapped"))),
                Arguments.of("IllegalStateException", new IllegalStateException("bug")),
                Arguments.of("an Error", new InjectedError("fatal")));
    }

    @ParameterizedTest(name = "{0} is attempted once and thrown as it was")
    @MethodSource("nonRetryable")
    @DisplayName("a failure waiting cannot fix is attempted once")
    void nonRetryableIsAttemptedOnce(String label, Throwable failure, @TempDir Path dir)
            throws IOException {
        Path target = dir.resolve(TARGET_NAME);
        Files.writeString(target, OLD_DOCUMENT, UTF_8);
        ScriptedDurability durability =
                new ScriptedDurability(target, attempt -> attempt == 1 ? failure : null);
        RecordingWaiter waiter = new RecordingWaiter();

        Throwable thrown = assertThrows(Throwable.class, () -> write(target, durability, waiter));

        assertAll(
                () -> assertSame(failure, thrown, label),
                () -> assertEquals(0, thrown.getSuppressed().length),
                () -> assertEquals(1, durability.attempts(), label),
                () -> assertEquals(List.of(), waiter.pauses(), label),
                () -> assertArrayEquals(OLD_DOCUMENT_BYTES, Files.readAllBytes(target)),
                () -> assertEquals(List.of(TARGET_NAME), listing(dir)));
    }

    @Test
    @DisplayName("a non-retryable failure after two refusals ends it, carrying both refusals")
    void nonRetryableAfterRefusals(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);
        Files.writeString(target, OLD_DOCUMENT, UTF_8);
        AccessDeniedException first = new AccessDeniedException("refusal #1");
        AccessDeniedException second = new AccessDeniedException("refusal #2");
        NoSuchFileException gone = new NoSuchFileException("a.tmp");
        List<IOException> script = List.of(first, second, gone);
        ScriptedDurability durability =
                new ScriptedDurability(target, attempt -> script.get(attempt - 1));
        RecordingWaiter waiter = new RecordingWaiter();

        NoSuchFileException thrown =
                assertThrows(NoSuchFileException.class, () -> write(target, durability, waiter));

        assertAll(
                () -> assertSame(gone, thrown),
                () -> assertEquals(List.of(first, second), List.of(thrown.getSuppressed())),
                () -> assertEquals(3, durability.attempts()),
                () -> assertEquals(List.of(10L, 20L), waiter.pauses()),
                () -> assertEquals(OLD_DOCUMENT, read(target)),
                () -> assertEquals(List.of(TARGET_NAME), listing(dir)));
    }

    // ---------------------------------------------------------------------------------------
    // Interruption during a pause.
    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("interruption during a pause")
    class Interruption {

        @Test
        @DisplayName("an interrupt during the first pause stops at once and is reported")
        void interruptedDuringTheFirstPause(@TempDir Path dir) throws Exception {
            Outcome outcome = interruptDuringPause(dir, 1);

            assertAll(
                    () -> assertFalse(outcome.writerAlive(), "the interrupted writer never ended"),
                    () -> assertInstanceOf(InterruptedIOException.class, outcome.thrown()),
                    () ->
                            assertEquals(
                                    "Interrupted while waiting to retry replacing "
                                            + dir.resolve(TARGET_NAME)
                                            + " after 1 refused attempt(s)",
                                    outcome.thrown().getMessage()),
                    () -> assertSame(outcome.refusals().get(0), outcome.thrown().getCause()),
                    () -> assertEquals(1, outcome.thrown().getSuppressed().length),
                    () ->
                            assertInstanceOf(
                                    InterruptedException.class,
                                    outcome.thrown().getSuppressed()[0]),
                    () -> assertTrue(outcome.interruptedAtTheEnd(), "interrupt status swallowed"),
                    // No attempt after the interrupt.
                    () -> assertEquals(1, outcome.durability().attempts()),
                    () -> assertEquals(List.of(10L), outcome.waiter().pauses()),
                    () -> assertEquals(OLD_DOCUMENT, read(dir.resolve(TARGET_NAME))),
                    () -> assertEquals(List.of(TARGET_NAME), listing(dir)));
        }

        @Test
        @DisplayName("an interrupt during the third pause carries the earlier refusals")
        void interruptedDuringTheThirdPause(@TempDir Path dir) throws Exception {
            Outcome outcome = interruptDuringPause(dir, 3);
            Throwable cause = outcome.thrown().getCause();

            assertAll(
                    () -> assertInstanceOf(InterruptedIOException.class, outcome.thrown()),
                    () -> assertSame(outcome.refusals().get(2), cause),
                    () ->
                            assertEquals(
                                    outcome.refusals().subList(0, 2),
                                    List.of(cause.getSuppressed())),
                    () -> assertTrue(outcome.interruptedAtTheEnd(), "interrupt status swallowed"),
                    () -> assertEquals(3, outcome.durability().attempts()),
                    () -> assertEquals(List.of(10L, 20L, 40L), outcome.waiter().pauses()),
                    () -> assertEquals(OLD_DOCUMENT, read(dir.resolve(TARGET_NAME))),
                    () -> assertEquals(List.of(TARGET_NAME), listing(dir)));
        }

        @Test
        @DisplayName("the production pause is a real, interruptible sleep")
        void theProductionPauseIsInterruptible(@TempDir Path dir) throws Exception {
            // The three-argument overload is the production path, whose pause is Thread.sleep.
            // Setting the interrupt flag inside the refused attempt makes that sleep throw at once,
            // so this proves the real waiter honours an interrupt without the test sleeping.
            Path target = dir.resolve(TARGET_NAME);
            Files.writeString(target, OLD_DOCUMENT, UTF_8);
            ScriptedDurability durability =
                    new ScriptedDurability(
                            target,
                            attempt -> {
                                Thread.currentThread().interrupt();
                                return new AccessDeniedException("refusal #" + attempt);
                            });
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            AtomicBoolean interruptedAtTheEnd = new AtomicBoolean();

            Thread writer =
                    new Thread(
                            () -> {
                                try {
                                    AtomicDocumentWriter.write(
                                            target,
                                            out -> out.write(NEW_DOCUMENT.getBytes(UTF_8)),
                                            durability);
                                } catch (Throwable failure) {
                                    thrown.set(failure);
                                } finally {
                                    interruptedAtTheEnd.set(Thread.currentThread().isInterrupted());
                                }
                            },
                            "atomic-document-writer-production-pause");
            writer.start();
            writer.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));

            assertAll(
                    () -> assertFalse(writer.isAlive(), "the writer never ended"),
                    () -> assertInstanceOf(InterruptedIOException.class, thrown.get()),
                    () -> assertTrue(interruptedAtTheEnd.get(), "interrupt status swallowed"),
                    () -> assertEquals(1, durability.attempts()),
                    () -> assertEquals(OLD_DOCUMENT, read(target)),
                    () -> assertEquals(List.of(TARGET_NAME), listing(dir)));
        }

        /**
         * Runs a write on its own thread whose every rename is refused, and interrupts it while it
         * is blocked in pause number {@code pauseToInterrupt}.
         */
        private Outcome interruptDuringPause(Path dir, int pauseToInterrupt) throws Exception {
            Path target = dir.resolve(TARGET_NAME);
            Files.writeString(target, OLD_DOCUMENT, UTF_8);
            List<IOException> refusals = Collections.synchronizedList(new ArrayList<>());
            ScriptedDurability durability =
                    new ScriptedDurability(
                            target,
                            attempt -> {
                                IOException refusal =
                                        new AccessDeniedException("refusal #" + attempt);
                                refusals.add(refusal);
                                return refusal;
                            });
            CountDownLatch inTheChosenPause = new CountDownLatch(1);
            CountDownLatch neverReleased = new CountDownLatch(1);
            RecordingWaiter waiter =
                    new RecordingWaiter() {
                        @Override
                        public void pause(long millis) throws InterruptedException {
                            super.pause(millis);
                            if (pauses().size() == pauseToInterrupt) {
                                inTheChosenPause.countDown();
                                neverReleased.await();
                            }
                        }
                    };
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            AtomicBoolean interruptedAtTheEnd = new AtomicBoolean();

            Thread writer =
                    new Thread(
                            () -> {
                                try {
                                    AtomicDocumentWriter.write(
                                            target,
                                            out -> out.write(NEW_DOCUMENT.getBytes(UTF_8)),
                                            durability,
                                            waiter);
                                } catch (Throwable failure) {
                                    thrown.set(failure);
                                } finally {
                                    interruptedAtTheEnd.set(Thread.currentThread().isInterrupted());
                                }
                            },
                            "atomic-document-writer-interrupted-in-pause");
            writer.start();
            assertTrue(
                    inTheChosenPause.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "the writer never reached the chosen pause");
            writer.interrupt();
            writer.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            return new Outcome(
                    writer.isAlive(),
                    thrown.get(),
                    interruptedAtTheEnd.get(),
                    List.copyOf(refusals),
                    durability,
                    waiter);
        }
    }

    /** What an interrupted write left behind, gathered off its thread. */
    private record Outcome(
            boolean writerAlive,
            Throwable thrown,
            boolean interruptedAtTheEnd,
            List<IOException> refusals,
            ScriptedDurability durability,
            RecordingWaiter waiter) {}

    // ---------------------------------------------------------------------------------------
    // Rejections.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("a null waiter is rejected by name, before anything is written")
    void aNullWaiterIsRejected(@TempDir Path dir) throws IOException {
        Path target = dir.resolve(TARGET_NAME);

        NullPointerException thrown =
                assertThrows(
                        NullPointerException.class,
                        () ->
                                AtomicDocumentWriter.write(
                                        target,
                                        out -> out.write(NEW_DOCUMENT.getBytes(UTF_8)),
                                        new FileSystemDurability(),
                                        null));

        assertAll(
                () -> assertEquals("waiter", thrown.getMessage()),
                () -> assertEquals(List.of(), listing(dir)));
    }

    // ---------------------------------------------------------------------------------------
    // Fixtures.
    // ---------------------------------------------------------------------------------------

    private static boolean write(Path target, Durability durability, RetryWaiter waiter)
            throws IOException {
        return AtomicDocumentWriter.write(
                target, out -> out.write(NEW_DOCUMENT.getBytes(UTF_8)), durability, waiter);
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, UTF_8);
    }

    private static List<String> listing(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(entry -> directory.relativize(entry).toString()).sorted().toList();
        }
    }

    /** A waiter that records each requested pause and returns at once. */
    private static class RecordingWaiter implements RetryWaiter {

        private final List<Long> pauses = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void pause(long millis) throws InterruptedException {
            pauses.add(millis);
        }

        List<Long> pauses() {
            return List.copyOf(pauses);
        }

        long total() {
            long total = 0;
            for (long pause : pauses()) {
                total += pause;
            }
            return total;
        }
    }

    /**
     * A {@link Durability} that does the real thing, except that a rename attempt for which the
     * script returns a failure throws that failure instead -- before touching anything, exactly as
     * a refused {@code MoveFileEx} leaves both files as they were.
     */
    private static final class ScriptedDurability implements Durability {

        private final Durability real = new FileSystemDurability();
        private final Path target;
        private final IntFunction<Throwable> script;
        private final List<String> operations = Collections.synchronizedList(new ArrayList<>());
        private final List<Path> temporaries = Collections.synchronizedList(new ArrayList<>());
        private final List<String> targetAtEachAttempt =
                Collections.synchronizedList(new ArrayList<>());
        private final List<Boolean> temporaryPresentAtEachAttempt =
                Collections.synchronizedList(new ArrayList<>());
        private int attempts;

        ScriptedDurability(Path target, IntFunction<Throwable> script) {
            this.target = target;
            this.script = script;
        }

        @Override
        public void syncFile(FileChannel channel) throws IOException {
            operations.add("syncFile");
            real.syncFile(channel);
        }

        @Override
        public void moveIntoPlace(Path temporary, Path destination) throws IOException {
            operations.add("move");
            temporaries.add(temporary);
            temporaryPresentAtEachAttempt.add(Files.exists(temporary));
            targetAtEachAttempt.add(
                    Files.exists(target) ? Files.readString(target, UTF_8) : ABSENT);
            int attempt;
            synchronized (this) {
                attempt = ++attempts;
            }
            Throwable failure = script.apply(attempt);
            if (failure instanceof IOException io) {
                throw io;
            }
            if (failure instanceof RuntimeException unchecked) {
                throw unchecked;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            real.moveIntoPlace(temporary, destination);
        }

        @Override
        public void syncDirectory(Path directory) throws IOException {
            operations.add("syncDirectory");
            real.syncDirectory(directory);
        }

        synchronized int attempts() {
            return attempts;
        }

        List<String> operations() {
            return List.copyOf(operations);
        }

        List<String> targetAtEachAttempt() {
            return List.copyOf(targetAtEachAttempt);
        }

        List<Boolean> temporaryPresentAtEachAttempt() {
            return List.copyOf(temporaryPresentAtEachAttempt);
        }

        long distinctTemporaries() {
            return List.copyOf(temporaries).stream().distinct().count();
        }
    }
}
