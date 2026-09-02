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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.install.registry.ArtefactRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code R-TOOL-05}, proved with two real JVMs.
 *
 * <p><strong>Two threads would prove nothing.</strong> A {@link java.nio.channels.FileLock} is held
 * by the JVM, so a second lock attempt inside one process raises {@link
 * java.nio.channels.OverlappingFileLockException} instead of waiting -- a different code path from
 * the one a second CometGUI takes. Every test here launches a second JVM through {@link
 * org.cometgui.tools.process.ProcessService}, which is the only thing in this product allowed to
 * start a process.
 *
 * <p><strong>And a lock that is never observed to block has not been shown to work.</strong> This
 * project has already carried one of those: {@code _build/cometgui-maven.lock} existed for weeks,
 * the status document told readers builds were serialised by it, and nothing took it. So the first
 * test requires the two critical sections to be disjoint <em>in time</em>, and the second runs the
 * same two processes with the lock left out and requires the sections to overlap. Without that
 * control the first test would pass just as well if the two children never ran at the same time.
 */
class InstallLockRaceTest {

    /** How long a child holds the critical section for. Long enough to survive a slow JVM start. */
    private static final long HOLD_MILLIS = 2500;

    /** How long the parent waits for a child. */
    private static final Duration PATIENCE = Duration.ofSeconds(90);

    @TempDir private Path temporary;

    @Test
    @DisplayName("two JVMs holding the install lock never overlap, and the second is seen to wait")
    void twoProcessesTakingTheLockNeverOverlap() throws IOException, InterruptedException {
        Path journal = temporary.resolve("locked-journal.txt");
        Path lockFile = temporary.resolve("locks").resolve("percolator.lock");

        ChildProcesses.Child first = startChild(lockFile, journal, "lock", "first");
        awaitLine(first, journal, "ENTER FIRST");
        ChildProcesses.Child second = startChild(lockFile, journal, "lock", "second");
        ChildProcesses.Result firstResult = first.await(PATIENCE);
        ChildProcesses.Result secondResult = second.await(PATIENCE);

        assertEquals(0, firstResult.exitCode(), () -> "first child: " + firstResult.describe());
        assertEquals(0, secondResult.exitCode(), () -> "second child: " + secondResult.describe());
        List<String> events = eventsOf(journal);
        assertEquals(
                List.of("ENTER FIRST", "EXIT FIRST", "ENTER SECOND", "EXIT SECOND"),
                events,
                () ->
                        "the two critical sections must not interleave; the journal was "
                                + readJournal(journal));
        assertTrue(
                at(journal, "ENTER SECOND") >= at(journal, "EXIT FIRST"),
                () ->
                        "the second process entered at "
                                + at(journal, "ENTER SECOND")
                                + " and the first left at "
                                + at(journal, "EXIT FIRST")
                                + ", so it did not wait for the lock");
        long waited = waitedMillis(secondResult);
        assertTrue(
                waited >= HOLD_MILLIS / 4,
                () ->
                        "the second process reported waiting "
                                + waited
                                + " ms for a lock held for "
                                + HOLD_MILLIS
                                + " ms; a lock nobody is ever observed to wait on is the defect"
                                + " this test exists to prevent");
    }

    @Test
    @DisplayName("the same two JVMs WITHOUT the lock are observed to overlap, so the test can fail")
    void withoutTheLockTheSameHarnessObservesAnOverlap() throws IOException, InterruptedException {
        Path journal = temporary.resolve("unlocked-journal.txt");
        Path lockFile = temporary.resolve("locks").resolve("unused.lock");

        ChildProcesses.Child first = startChild(lockFile, journal, "nolock", "first");
        awaitLine(first, journal, "ENTER FIRST");
        ChildProcesses.Child second = startChild(lockFile, journal, "nolock", "second");
        first.await(PATIENCE);
        second.await(PATIENCE);

        assertTrue(
                at(journal, "ENTER SECOND") < at(journal, "EXIT FIRST"),
                () ->
                        "this is the control: with no lock the two sections must be seen to"
                                + " overlap, or the test above passes for the wrong reason. The"
                                + " journal was "
                                + readJournal(journal));
        assertEquals(
                List.of("ENTER FIRST", "ENTER SECOND", "EXIT FIRST", "EXIT SECOND"),
                eventsOf(journal),
                () -> "the journal was " + readJournal(journal));
    }

    @Test
    @DisplayName("the installer itself waits for another process holding that artefact's lock")
    void theInstallerWaitsForAnotherProcessesLock() throws IOException, InterruptedException {
        Path root = temporary.resolve("cache");
        Path fixture = temporary.resolve("fixture");
        Path journal = temporary.resolve("installer-journal.txt");
        CacheFixtures.writeSharedFixture(fixture);
        InstallHarness harness = InstallHarness.at(root);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);
        Path lockFile =
                harness.cache().lockFile(record.tool(), record.version(), record.platform());

        ChildProcesses.Child holder = startChild(lockFile, journal, "lock", "holder");
        awaitLine(holder, journal, "ENTER HOLDER");
        long startedAt = System.currentTimeMillis();
        Installation installation = harness.install(record);
        long elapsed = System.currentTimeMillis() - startedAt;
        ChildProcesses.Result holderResult = holder.await(PATIENCE);

        assertEquals(0, holderResult.exitCode(), () -> "holder: " + holderResult.describe());
        assertTrue(
                elapsed >= HOLD_MILLIS / 4,
                () ->
                        "the install returned after "
                                + elapsed
                                + " ms while another process held "
                                + lockFile
                                + " for "
                                + HOLD_MILLIS
                                + " ms, so it did not take the lock at all");
        assertTrue(
                harness.verify(record).installed(),
                "the install must still have completed once the lock was released");
        assertTrue(
                Files.isRegularFile(installation.executable()),
                () -> installation.executable() + " should have been installed");
    }

    @Test
    @DisplayName("two JVMs installing one artefact serialise: exactly one does the work")
    void twoProcessesInstallingTheSameArtefactSerialise() throws IOException, InterruptedException {
        Path root = temporary.resolve("shared-cache");
        Path fixture = temporary.resolve("shared-fixture");
        Files.createDirectories(root);
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);

        List<String> arguments = List.of(root.toString(), fixture.toString(), "1200");
        ChildProcesses.Child first =
                ChildProcesses.startJava(InstallRaceChild.class, temporary, arguments);
        ChildProcesses.Child second =
                ChildProcesses.startJava(InstallRaceChild.class, temporary, arguments);
        ChildProcesses.Result firstResult = first.await(PATIENCE);
        ChildProcesses.Result secondResult = second.await(PATIENCE);

        assertEquals(0, firstResult.exitCode(), () -> "first: " + firstResult.describe());
        assertEquals(0, secondResult.exitCode(), () -> "second: " + secondResult.describe());
        List<String> both = new ArrayList<>(firstResult.standardOutput());
        both.addAll(secondResult.standardOutput());
        assertEquals(
                1,
                both.stream().filter("alreadyInstalled=false"::equals).count(),
                () ->
                        "exactly one of two processes may perform the install; both said: "
                                + firstResult.describe()
                                + " / "
                                + secondResult.describe());
        assertEquals(
                1,
                both.stream().filter("alreadyInstalled=true"::equals).count(),
                () ->
                        "the other must have found a COMPLETE entry and done no work: "
                                + firstResult.describe()
                                + " / "
                                + secondResult.describe());
        assertEquals(
                1,
                both.stream().filter("fetches=0"::equals).count(),
                () ->
                        "the process that waited must not have downloaded anything: "
                                + firstResult.describe()
                                + " / "
                                + secondResult.describe());
        assertEquals(
                2,
                both.stream().filter("state=INSTALLED"::equals).count(),
                () ->
                        "neither process may observe a partially written entry: "
                                + firstResult.describe()
                                + " / "
                                + secondResult.describe());
        assertTrue(
                new InstallHarness(
                                root,
                                RecordingProbe.confirming(),
                                org.cometgui.domain.tools.HostOperatingSystem.LINUX)
                        .verify(record)
                        .installed(),
                "the shared cache must hold one complete entry afterwards");
    }

    private ChildProcesses.Child startChild(Path lockFile, Path journal, String mode, String name)
            throws IOException {
        return ChildProcesses.startJava(
                LockRaceChild.class,
                temporary,
                List.of(
                        lockFile.toString(),
                        journal.toString(),
                        String.valueOf(HOLD_MILLIS),
                        mode,
                        name));
    }

    /*
     * Waits for a child to say it is inside its critical section, so that the second child really
     * does start while the first is holding.  A fixed sleep here would make the test's meaning
     * depend on how fast a JVM starts today.
     */
    private static void awaitLine(ChildProcesses.Child child, Path journal, String event)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (eventsOf(journal).contains(event)) {
                return;
            }
            /*
             * A DEAD CHILD IS A FAILURE NOW, NOT IN A MINUTE.  A child that cannot start -- the
             * wrong class path, a missing class -- writes nothing, and waiting the full minute for
             * a line that is never coming turns a one-line diagnosis into a timeout.
             */
            if (!child.isAlive()) {
                throw new AssertionError(
                        "a child process ended before writing \""
                                + event
                                + "\": "
                                + child.soFar().describe());
            }
            Thread.sleep(25);
        }
        throw new AssertionError(
                "no child wrote \""
                        + event
                        + "\" to "
                        + journal
                        + " within 60 s: "
                        + readJournal(journal));
    }

    private static List<String> eventsOf(Path journal) {
        List<String> events = new ArrayList<>();
        for (String line : readJournal(journal)) {
            String[] parts = line.split(" ");
            events.add(parts[0] + " " + parts[1]);
        }
        return events;
    }

    private static long at(Path journal, String event) {
        for (String line : readJournal(journal)) {
            String[] parts = line.split(" ");
            if (event.equals(parts[0] + " " + parts[1])) {
                return Long.parseLong(parts[2]);
            }
        }
        throw new AssertionError("the journal holds no \"" + event + "\"");
    }

    /*
     * Unchecked on purpose: every call site is inside an assertion message, and a checked exception
     * there would push the journal out of the messages -- which is the one thing that makes a
     * failure of these tests diagnosable.
     */
    private static List<String> readJournal(Path journal) {
        try {
            if (!Files.isRegularFile(journal)) {
                return List.of();
            }
            return Files.readAllLines(journal).stream().filter(line -> !line.isBlank()).toList();
        } catch (IOException unreadable) {
            throw new AssertionError(
                    "the journal at " + journal + " could not be read", unreadable);
        }
    }

    private static long waitedMillis(ChildProcesses.Result result) {
        for (String line : result.standardOutput()) {
            if (line.startsWith("waitedMillis=")) {
                return Long.parseLong(line.substring("waitedMillis=".length()));
            }
        }
        throw new AssertionError("the child printed no waitedMillis: " + result.describe());
    }
}
