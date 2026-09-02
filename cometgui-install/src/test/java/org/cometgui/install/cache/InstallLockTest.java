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

package org.cometgui.install.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The lock's in-process half.
 *
 * <p>{@link InstallLockRaceTest} is the half that matters for {@code R-TOOL-05} and it uses two
 * real JVMs. This one covers what a second <em>thread</em> does, which is a different and equally
 * real hazard: a bare {@link java.nio.channels.FileLock} would throw {@link
 * java.nio.channels.OverlappingFileLockException} at the second thread in one JVM rather than
 * making it wait, so two windows of one application would see an error instead of a queue.
 */
class InstallLockTest {

    @TempDir private Path temporary;

    @Test
    @DisplayName("the lock file and its parents are created, and the lock is uncontended")
    void anUncontendedLockIsTakenAtOnce() throws IOException, InterruptedException {
        Path lockFile = temporary.resolve("cache").resolve("locks").resolve("percolator.lock");

        try (InstallLock lock = InstallLock.acquire(lockFile)) {
            assertTrue(Files.isRegularFile(lockFile), "the lock file is created");
            assertEquals(lockFile.toAbsolutePath().normalize(), lock.file());
            assertTrue(lock.held());
            assertTrue(
                    lock.waited().compareTo(Duration.ofSeconds(5)) < 0,
                    () -> "an uncontended lock should not wait, and waited " + lock.waited());
            assertTrue(lock.toString().contains("held=true"), lock::toString);
        }
    }

    @Test
    @DisplayName("a second THREAD waits rather than failing, and the sections do not overlap")
    void aSecondThreadWaitsRatherThanFailing() throws IOException, InterruptedException {
        Path lockFile = temporary.resolve("locks").resolve("percolator.lock");
        List<String> journal = Collections.synchronizedList(new java.util.ArrayList<>());
        CountDownLatch firstIsInside = new CountDownLatch(1);
        AtomicReference<Duration> secondWaited = new AtomicReference<>();
        ArrayBlockingQueue<Throwable> failures = new ArrayBlockingQueue<>(4);

        Thread first =
                new Thread(
                        () -> {
                            try (InstallLock lock = InstallLock.acquire(lockFile)) {
                                journal.add("enter first");
                                firstIsInside.countDown();
                                Thread.sleep(600);
                                journal.add("exit first");
                            } catch (IOException | InterruptedException failed) {
                                failures.add(failed);
                            }
                        },
                        "first");
        Thread second =
                new Thread(
                        () -> {
                            try {
                                assertTrue(firstIsInside.await(30, TimeUnit.SECONDS));
                                try (InstallLock lock = InstallLock.acquire(lockFile)) {
                                    secondWaited.set(lock.waited());
                                    journal.add("enter second");
                                    journal.add("exit second");
                                }
                            } catch (IOException | InterruptedException failed) {
                                failures.add(failed);
                            }
                        },
                        "second");

        first.setDaemon(true);

        first.start();
        second.setDaemon(true);
        second.start();
        first.join(30_000);
        second.join(30_000);

        assertTrue(failures.isEmpty(), () -> "a thread failed: " + failures);
        assertEquals(
                List.of("enter first", "exit first", "enter second", "exit second"),
                List.copyOf(journal),
                "a second thread must queue, not fail: a bare FileLock would have thrown"
                        + " OverlappingFileLockException here");
        assertTrue(
                secondWaited.get().toMillis() >= 200,
                () ->
                        "the second thread reported waiting "
                                + secondWaited.get()
                                + " for a lock held for 600 ms");
    }

    @Test
    @DisplayName("closing twice is not an error, and the operating system agrees it is released")
    void closingTwiceIsNotAnError() throws IOException, InterruptedException {
        Path lockFile = temporary.resolve("locks").resolve("percolator.lock");
        InstallLock lock = InstallLock.acquire(lockFile);
        assertTrue(lock.held(), "the lock itself says it is held, not a flag this class sets");
        assertTrue(
                openDescriptorsOn(lockFile),
                "and the channel that holds it is open on the lock file");

        lock.close();
        lock.close();

        assertFalse(lock.held());
        assertFalse(
                openDescriptorsOn(lockFile),
                "a closed lock leaves no descriptor open on its file: an install that leaked one"
                        + " per artefact would run a desktop application out of handles");
        assertTrue(lock.toString().contains("held=false"), lock::toString);
        try (InstallLock again = InstallLock.acquire(lockFile)) {
            assertTrue(again.held(), "and the lock can be taken again afterwards");
        }
    }

    @Test
    @DisplayName("an acquisition that fails releases the monitor, so the next one is not stuck")
    void aFailedAcquisitionDoesNotStrandTheMonitor() throws IOException, InterruptedException {
        Path lockFile = temporary.resolve("locks").resolve("percolator.lock");
        Files.createDirectories(lockFile);

        assertThrows(IOException.class, () -> InstallLock.acquire(lockFile));

        Files.delete(lockFile);
        ArrayBlockingQueue<Object> outcome = new ArrayBlockingQueue<>(1);
        Thread other =
                new Thread(
                        () -> {
                            try (InstallLock lock = InstallLock.acquire(lockFile)) {
                                outcome.add(lock.file());
                            } catch (IOException failed) {
                                outcome.add(failed);
                            }
                        },
                        "other");
        other.setDaemon(true);
        other.start();

        Object result = outcome.poll(2, TimeUnit.SECONDS);
        assertEquals(
                lockFile.toAbsolutePath().normalize(),
                result,
                "if the JVM-wide monitor had been left locked by the failed attempt, another"
                        + " thread would block on it for ever; the bound is short so that the"
                        + " defect fails an assertion rather than hanging");
    }

    @Test
    @DisplayName("an acquisition that fails leaves no descriptor open either")
    void aFailedAcquisitionLeavesNoOpenDescriptor() throws IOException, InterruptedException {
        Path locks = Files.createDirectories(temporary.resolve("locks"));
        Path alias = Files.createSymbolicLink(temporary.resolve("locks-alias"), locks);
        Path direct = locks.resolve("percolator.lock");

        try (InstallLock held = InstallLock.acquire(direct)) {
            assertThrows(
                    java.nio.channels.OverlappingFileLockException.class,
                    () -> InstallLock.acquire(alias.resolve("percolator.lock")));
            assertEquals(
                    1,
                    countDescriptorsOn(direct),
                    "the refused attempt opened a channel and must have closed it; only the lock"
                            + " that was granted may still hold one");
        }
        assertFalse(openDescriptorsOn(direct));
    }

    @Test
    @DisplayName("the lock rejects a null path")
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> InstallLock.acquire(Nulls.of(Path.class)));
    }

    /*
     * DESCRIPTORS BY NAME, NOT BY COUNT.  The archive package's own handle test makes the same
     * choice for the same reason: a descriptor count drifts for reasons that have nothing to do
     * with the code under test, while "does anything in this process still hold THIS file open" is
     * a question with one answer.
     */
    private static boolean openDescriptorsOn(Path file) throws IOException {
        return countDescriptorsOn(file) > 0;
    }

    private static int countDescriptorsOn(Path file) throws IOException {
        Path descriptors = Path.of("/proc/self/fd");
        if (!Files.isDirectory(descriptors)) {
            throw new AssertionError(
                    "this host publishes no /proc/self/fd, so a leaked descriptor cannot be"
                            + " observed here; run the tests on Linux rather than accepting an"
                            + " unchecked resource leak");
        }
        Path wanted = file.toAbsolutePath().normalize();
        int open = 0;
        try (java.util.stream.Stream<Path> entries = Files.list(descriptors)) {
            for (Path entry : entries.toList()) {
                try {
                    if (Files.readSymbolicLink(entry).equals(wanted)) {
                        open++;
                    }
                } catch (IOException closedUnderneathUs) {
                    // A descriptor that vanished while the directory was being walked was not the
                    // one being looked for; the walk itself opens and closes some.
                }
            }
        }
        return open;
    }
}
