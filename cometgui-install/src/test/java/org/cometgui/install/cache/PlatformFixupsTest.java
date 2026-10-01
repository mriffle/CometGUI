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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.cache.ScriptedXattr.Behaviour;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.testing.Nulls;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@code R-PLAT-05}'s executable bits and {@code R-PLAT-04}'s quarantine removal.
 *
 * <h2>What this class proves, and on which platform</h2>
 *
 * <p>It runs on Linux. <strong>Nothing in it is evidence about macOS.</strong> Until phase 05 unit
 * 14 this comment said the opposite: that writing an attribute <em>named</em> {@code
 * com.apple.quarantine} through Java's {@link UserDefinedFileAttributeView} here and removing it
 * through the same view exercised "the same code macOS would run". The first macOS run showed that
 * code removing nothing, and the JDK source shows why: the view prefixes {@code user.} on macOS as
 * well, so it never reaches the raw attribute (see {@link PlatformFixups}). That test proved the
 * Linux behaviour of a code path that had no effect on the platform it existed for.
 *
 * <ul>
 *   <li><strong>The executable bit</strong> is proved here, for real, on this file system.
 *   <li><strong>The non-macOS branch</strong> is proved here, for real: a real extended attribute
 *       is set on a real file (Linux stores it as {@code user.com.apple.quarantine}), the step runs
 *       for a Linux and a Windows host, and the attribute is still there and no process was
 *       started.
 *   <li><strong>The macOS branch</strong> is graded through a scripted {@link
 *       org.cometgui.domain.ports.ProcessRunner} -- the port production passes the process service
 *       through -- answering for {@code /usr/bin/xattr}: the exact argument arrays, the working
 *       directory and environment, the list-delete-list order, and every way a file can fail to be
 *       cleared. The real process service is run against the macOS branch too, on this machine
 *       where {@code /usr/bin/xattr} does not exist, and the result must be a named failure.
 * </ul>
 *
 * <p><strong>Only the macos-gatekeeper workflow can show</strong> that the real {@code
 * /usr/bin/xattr} lists and deletes the real attribute the way the script stands in for it, and
 * that {@code xattr -p} finds nothing afterwards. Gatekeeper accepting the binary is a further
 * question that no test here or there has answered.
 */
class PlatformFixupsTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final String SCRIPT = "#!/bin/sh" + System.lineSeparator() + "echo tool";

    /** Long enough for a scripted answer that arrives before start returns; never waited out. */
    private static final Duration SHORT = Duration.ofMillis(500);

    @TempDir private Path temporary;

    @Test
    @DisplayName("an executable gets the owner bit, and group and other only where they can read")
    void theExecutableBitFollowsChmodPlusXSemantics() throws IOException, InterruptedException {
        byte[] binary = CacheFixtures.bytes(SCRIPT);
        ArtefactRecord record =
                CacheFixtures.bareExecutable(
                        ToolName.COMET, "2026.02.2", binary, "comet.linux.exe", List.of());
        Path directory = Files.createDirectories(temporary.resolve("payload"));
        Path file = directory.resolve("comet.linux.exe");
        Files.write(file, binary);
        Files.setPosixFilePermissions(
                file,
                EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.GROUP_READ));

        FixupReport report = linux().apply(directory, record);

        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
        assertTrue(permissions.contains(PosixFilePermission.OWNER_EXECUTE), "the owner always");
        assertTrue(
                permissions.contains(PosixFilePermission.GROUP_EXECUTE),
                "the group, because the group could already read it");
        assertFalse(
                permissions.contains(PosixFilePermission.OTHERS_EXECUTE),
                "and not other, which could not read it: an executable nobody may read is a"
                        + " permission nobody asked for");
        assertEquals(List.of("comet.linux.exe"), report.madeExecutable());
        assertEquals(List.of(), report.quarantineCleared());
        assertEquals(List.of(), report.quarantineNotCleared());
        assertFalse(report.changedNothing());
    }

    @Test
    @DisplayName("a file that is already executable is not reported as changed")
    void anAlreadyExecutableFileIsNotReported() throws IOException, InterruptedException {
        byte[] binary = CacheFixtures.bytes(SCRIPT);
        ArtefactRecord record =
                CacheFixtures.bareExecutable(
                        ToolName.COMET, "2026.02.2", binary, "comet.linux.exe", List.of());
        Path directory = Files.createDirectories(temporary.resolve("payload"));
        Path file = directory.resolve("comet.linux.exe");
        Files.write(file, binary);
        Files.setPosixFilePermissions(
                file,
                EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));

        FixupReport report = linux().apply(directory, record);

        assertEquals(
                List.of(),
                report.madeExecutable(),
                "the report says what changed, so that a test can prove this step is the reason a"
                        + " file is runnable rather than merely that it was called");
        assertTrue(report.changedNothing());
    }

    @Test
    @DisplayName("a JAR is left alone: R-PLAT-05 is about executables")
    void aJarIsNotMadeExecutable() throws IOException, InterruptedException {
        byte[] jar = CacheFixtures.bytes("PK not really a jar");
        ArtefactRecord record = CacheFixtures.jar(ToolName.PDV, "2.7.0", jar, "pdv.jar");
        Path directory = Files.createDirectories(temporary.resolve("payload"));
        Files.write(directory.resolve("pdv.jar"), jar);

        FixupReport report = linux().apply(directory, record);

        assertEquals(List.of(), report.madeExecutable());
        assertFalse(Files.isExecutable(directory.resolve("pdv.jar")));
    }

    // ------------------------------------------------------------ the macOS branch, scripted --

    @Test
    @DisplayName(
            "macOS branch (scripted xattr): list, delete, list again -- every quarantined file")
    void onMacOsEveryQuarantinedFileIsClearedThroughXattr() throws IOException {
        Payload payload = payload();
        ScriptedXattr xattr =
                ScriptedXattr.answeringAtOnce()
                        .quarantine(payload.executable)
                        .quarantine(payload.library);

        FixupReport report =
                new PlatformFixups(HostOperatingSystem.MACOS, xattr, SHORT)
                        .apply(payload.directory, payload.record);

        assertEquals(
                List.of("comet.macos.exe", "lib/helper.dylib"),
                report.quarantineCleared().stream().sorted().toList(),
                "R-PLAT-04 covers every file that will be executed, and a library loaded from a"
                        + " quarantined path fails the same way an executable does");
        assertEquals(List.of(), report.quarantineNotCleared());
        assertFalse(xattr.isQuarantined(payload.executable), "the model saw the deletion");
        assertFalse(xattr.isQuarantined(payload.library), "the model saw the deletion");
        for (Path cleared : List.of(payload.executable, payload.library)) {
            assertEquals(
                    List.of(
                            List.of("/usr/bin/xattr", "--", cleared.toString()),
                            List.of(
                                    "/usr/bin/xattr",
                                    "-d",
                                    "com.apple.quarantine",
                                    "--",
                                    cleared.toString()),
                            List.of("/usr/bin/xattr", "--", cleared.toString())),
                    xattr.argvFor(cleared),
                    "listed, deleted, and listed AGAIN: a deletion is re-checked, never assumed");
        }
        assertEquals(
                List.of(List.of("/usr/bin/xattr", "--", payload.readme.toString())),
                xattr.argvFor(payload.readme),
                "a file with no quarantine attribute is asked about once and never deleted from");
        assertEquals(
                List.of(),
                xattr.argvFor(payload.alias),
                "a symbolic link is never named: following it would clear an attribute outside the"
                        + " install");
        assertEquals(
                7,
                xattr.commands().size(),
                "three for each quarantined file, one for the clean file, none for the link");
        for (ToolCommand command : xattr.commands()) {
            assertEquals(payload.directory.toAbsolutePath(), command.workingDirectory());
            assertEquals(
                    Map.of("PATH", "/usr/bin:/bin:/usr/sbin:/sbin"),
                    command.environment(),
                    "the environment is constructed (R-PROC-04): system directories only");
        }
    }

    @Test
    @DisplayName("macOS branch (scripted xattr): answers arriving on another thread are waited for")
    void answersFromAnotherThreadAreWaitedFor() throws IOException {
        Payload payload = payload();
        ScriptedXattr xattr =
                ScriptedXattr.answeringFromAnotherThread()
                        .quarantine(payload.executable)
                        .quarantine(payload.library);

        FixupReport report =
                new PlatformFixups(HostOperatingSystem.MACOS, xattr)
                        .apply(payload.directory, payload.record);

        assertEquals(
                List.of("comet.macos.exe", "lib/helper.dylib"),
                report.quarantineCleared().stream().sorted().toList(),
                "the process service delivers output on its own threads after start returns; a"
                        + " listing read before onExit would be empty and the file never cleared");
        assertEquals(List.of(), report.quarantineNotCleared());
    }

    @Test
    @DisplayName("THE SILENT NO-OP: a deletion that exits 0 and changes nothing is a named failure")
    void aDeletionThatChangesNothingIsReportedAsNotCleared() throws IOException {
        Payload payload = payload();
        ScriptedXattr xattr =
                ScriptedXattr.answeringAtOnce()
                        .quarantine(payload.executable)
                        .behave(payload.executable, Behaviour.DELETE_IGNORED);

        QuarantineNotClearedException failure =
                assertThrows(
                        QuarantineNotClearedException.class,
                        () ->
                                new PlatformFixups(HostOperatingSystem.MACOS, xattr, SHORT)
                                        .apply(payload.directory, payload.record));

        assertEquals(
                "com.apple.quarantine could not be shown removed from 1 installed file(s), so"
                        + " Gatekeeper may refuse to run them (R-PLAT-04): comet.macos.exe:"
                        + " /usr/bin/xattr -d exited 0 and com.apple.quarantine is still listed"
                        + " afterwards",
                failure.getMessage(),
                "this is the defect macos-gatekeeper run 36918810975 found, in the one shape a"
                        + " process can take it: the step must say so rather than report success");
        assertEquals(List.of("comet.macos.exe"), failure.report().quarantineNotCleared());
        assertEquals(List.of(), failure.report().quarantineCleared());
        assertTrue(xattr.isQuarantined(payload.executable));
    }

    @Test
    @DisplayName(
            "macOS branch (scripted xattr): one refused file fails the step, the rest reported")
    void aRefusedDeletionIsNamedBesideTheFilesThatWereCleared() throws IOException {
        Payload payload = payload();
        ScriptedXattr xattr =
                ScriptedXattr.answeringAtOnce()
                        .quarantine(payload.executable)
                        .quarantine(payload.library)
                        .behave(payload.library, Behaviour.DELETE_REFUSED);

        QuarantineNotClearedException failure =
                assertThrows(
                        QuarantineNotClearedException.class,
                        () ->
                                new PlatformFixups(HostOperatingSystem.MACOS, xattr, SHORT)
                                        .apply(payload.directory, payload.record));

        assertEquals(List.of("comet.macos.exe"), failure.report().quarantineCleared());
        assertEquals(List.of("lib/helper.dylib"), failure.report().quarantineNotCleared());
        assertEquals(List.of("comet.macos.exe"), failure.report().madeExecutable());
        assertEquals(
                List.of(
                        "lib/helper.dylib: /usr/bin/xattr -d exited 1: xattr: [Errno 1] Operation"
                                + " not permitted: '"
                                + payload.library
                                + "'"),
                failure.reasons());
    }

    @Test
    @DisplayName("macOS branch (scripted xattr): a listing that fails is a failure, with its words")
    void aFailedListingIsNotTakenAsNoAttribute() throws IOException {
        Payload payload = payload();
        ScriptedXattr xattr =
                ScriptedXattr.answeringAtOnce()
                        .behave(payload.readme, Behaviour.LIST_REFUSED)
                        .behave(payload.library, Behaviour.LIST_SILENTLY_REFUSED);

        QuarantineNotClearedException failure =
                assertThrows(
                        QuarantineNotClearedException.class,
                        () ->
                                new PlatformFixups(HostOperatingSystem.MACOS, xattr, SHORT)
                                        .apply(payload.directory, payload.record));

        assertEquals(
                List.of(
                        "README: /usr/bin/xattr could not list its attributes: exited 1: xattr: No"
                                + " such file: "
                                + payload.readme,
                        "lib/helper.dylib: /usr/bin/xattr could not list its attributes: exited 1"
                                + " and wrote nothing to standard error"),
                failure.reasons().stream().sorted().toList(),
                "a file whose attributes cannot be read cannot be shown free of the quarantine");
        assertEquals(List.of(), failure.report().quarantineCleared());
    }

    @Test
    @DisplayName("macOS branch (scripted xattr): an xattr that cannot start is a named failure")
    void anXattrThatCannotStartIsNamed() throws IOException {
        Payload payload = payload();
        ScriptedXattr xattr =
                ScriptedXattr.answeringAtOnce().behave(payload.executable, Behaviour.NOT_STARTED);

        QuarantineNotClearedException failure =
                assertThrows(
                        QuarantineNotClearedException.class,
                        () ->
                                new PlatformFixups(HostOperatingSystem.MACOS, xattr, SHORT)
                                        .apply(payload.directory, payload.record));

        assertEquals(
                List.of(
                        "comet.macos.exe: /usr/bin/xattr could not be started: could not start"
                                + " ToolCommand[argv=[\"/usr/bin/xattr\", \"--\", \""
                                + payload.executable
                                + "\"], workingDirectory="
                                + payload.directory
                                + ", environmentNames=[PATH]] | Cannot run program"
                                + " \"/usr/bin/xattr\": error=2, No such file or directory"),
                failure.reasons(),
                "the whole cause chain: the part a reader needs is in the cause");
    }

    @Test
    @DisplayName("macOS branch (scripted xattr): an xattr that never ends is cancelled and named")
    void anXattrThatNeverEndsIsCancelledAndNamed() throws IOException {
        Payload payload = payload();
        ScriptedXattr xattr =
                ScriptedXattr.answeringAtOnce().behave(payload.executable, Behaviour.NEVER_ENDS);

        QuarantineNotClearedException failure =
                assertThrows(
                        QuarantineNotClearedException.class,
                        () ->
                                new PlatformFixups(
                                                HostOperatingSystem.MACOS,
                                                xattr,
                                                Duration.ofMillis(50))
                                        .apply(payload.directory, payload.record));

        assertEquals(
                List.of(
                        "comet.macos.exe: /usr/bin/xattr did not finish within 50 ms and was"
                                + " cancelled"),
                failure.reasons());
        assertTrue(xattr.wasCancelled(), "a process given up on is asked to stop, not abandoned");
    }

    @Test
    @DisplayName("macOS branch (scripted xattr): an interrupt stops the step and is kept")
    void anInterruptStopsTheStepAndIsKept() throws IOException {
        Payload payload = payload();
        ScriptedXattr xattr =
                ScriptedXattr.answeringAtOnce().behave(payload.executable, Behaviour.NEVER_ENDS);
        PlatformFixups fixups = new PlatformFixups(HostOperatingSystem.MACOS, xattr);
        // Only the executable is in this directory, so the first command is the one that hangs.
        Path alone = Files.createDirectories(temporary.resolve("alone"));
        Files.move(payload.executable, alone.resolve("comet.macos.exe"));
        xattr.behave(alone.resolve("comet.macos.exe"), Behaviour.NEVER_ENDS);

        Thread.currentThread().interrupt();
        InterruptedIOException stopped;
        try {
            stopped =
                    assertThrows(
                            InterruptedIOException.class,
                            () -> fixups.apply(alone, payload.record));
        } finally {
            assertTrue(Thread.interrupted(), "the interrupt status is restored for the caller");
        }

        assertEquals(
                "interrupted while waiting for /usr/bin/xattr to finish", stopped.getMessage());
        assertInstanceOf(InterruptedException.class, stopped.getCause());
        assertTrue(xattr.wasCancelled(), "the process is asked to stop, not left running");
    }

    @Test
    @DisplayName("macOS branch: a file on no operating-system file system is a named failure")
    void aFileOutsideTheDefaultFileSystemIsNamed() throws IOException {
        byte[] binary = CacheFixtures.bytes("a tool");
        ArtefactRecord record =
                CacheFixtures.bareExecutable(
                        ToolName.COMET, "2026.02.2", binary, "comet.exe", List.of());
        Path archive = temporary.resolve("elsewhere.zip");
        ScriptedXattr xattr = ScriptedXattr.answeringAtOnce();
        try (FileSystem zip = FileSystems.newFileSystem(archive, Map.of("create", "true"))) {
            Files.write(zip.getPath("comet.exe"), binary);

            QuarantineNotClearedException failure =
                    assertThrows(
                            QuarantineNotClearedException.class,
                            () ->
                                    new PlatformFixups(HostOperatingSystem.MACOS, xattr, SHORT)
                                            .apply(zip.getPath("/"), record));

            assertEquals(
                    List.of(
                            "comet.exe: it is not on the operating system's file system, so"
                                    + " /usr/bin/xattr cannot be given its path"),
                    failure.reasons());
        }
        assertEquals(List.of(), xattr.commands());
    }

    /*
     * THE REAL PROCESS SERVICE, THROUGH THE MACOS BRANCH, ON A MACHINE WITH NO /usr/bin/xattr.
     * This is production's composition with nothing scripted, and on Linux its one honest outcome
     * is a named failure.  It proves the failure path is wired; it proves nothing about what xattr
     * does on a Mac.  FAILS RATHER THAN SKIPS if this machine has an xattr of its own, because then
     * the test would be about some other program.
     */
    @Test
    @DisplayName(
            "macOS branch through the REAL process service, here with no xattr: a named failure")
    void theRealProcessServiceWithNoXattrIsANamedFailure() throws IOException {
        assertFalse(
                Files.exists(absolute("usr/bin/xattr")),
                "this machine has a /usr/bin/xattr, which is not macOS's; this test needs one"
                        + " without it, and must not quietly grade a different program");
        Payload payload = payload();

        QuarantineNotClearedException failure =
                assertThrows(
                        QuarantineNotClearedException.class,
                        () ->
                                new PlatformFixups(
                                                HostOperatingSystem.MACOS,
                                                new ProcessService(Clock.systemUTC()))
                                        .apply(payload.directory, payload.record));

        assertEquals(
                List.of("README", "comet.macos.exe", "lib/helper.dylib"),
                failure.report().quarantineNotCleared().stream().sorted().toList(),
                "every regular file is named: none of them could be shown free of the attribute");
        /*
         * The JDK's own wording after "Cannot run program" depends on the launch mechanism
         * (observed here: "Exec failed, error: 2 (No such file or directory)"), so the parts
         * pinned are the product's, the process service's, and the errno text.
         */
        for (String reason : failure.reasons()) {
            assertTrue(
                    reason.contains(
                                    ": /usr/bin/xattr could not be started: could not start"
                                            + " ToolCommand[argv=[\"/usr/bin/xattr\", \"--\", \""
                                            + payload.directory)
                            && reason.contains(" | Cannot run program \"/usr/bin/xattr\"")
                            && reason.contains("No such file or directory"),
                    "the process service's own words, cause chain included: " + reason);
        }
    }

    // ------------------------------------------------------------ the non-macOS branch, real --

    /*
     * PROOF OF THE LINUX (AND WINDOWS) BRANCH, AND OF NOTHING ELSE.  A real extended attribute is
     * written on a real file; on Linux the JDK stores it as user.com.apple.quarantine.  The step
     * must leave it alone and start no process.
     */
    @ParameterizedTest
    @EnumSource(
            value = HostOperatingSystem.class,
            names = {"LINUX", "WINDOWS"})
    @DisplayName("on a host that is not macOS no process is started and the attribute is untouched")
    void onOtherHostsTheAttributeIsNotTouched(HostOperatingSystem host)
            throws IOException, InterruptedException {
        byte[] binary = CacheFixtures.bytes(SCRIPT);
        ArtefactRecord record =
                CacheFixtures.bareExecutable(
                        ToolName.COMET, "2026.02.2", binary, "comet.linux.exe", List.of());
        Path directory = Files.createDirectories(temporary.resolve("payload-" + host));
        Path executable = directory.resolve("comet.linux.exe");
        Files.write(executable, binary);
        quarantine(executable);

        FixupReport report =
                new PlatformFixups(host, ScriptedXattr.NEVER_CALLED).apply(directory, record);

        assertEquals(List.of(), report.quarantineCleared());
        assertEquals(List.of(), report.quarantineNotCleared());
        assertTrue(
                attributesOf(executable).contains(PlatformFixups.QUARANTINE_ATTRIBUTE),
                "a fix-up that ran everywhere would be one nobody could tell had run at all");
    }

    // ------------------------------------------------------------------ construction --

    @Test
    @DisplayName("the fix-ups are chosen by the HOST, not by the artefact's platform")
    void theHostChoosesTheFixups() {
        assertEquals(
                HostOperatingSystem.MACOS,
                PlatformFixups.forHost(
                                new HostPlatform(
                                        HostOperatingSystem.MACOS, HostArchitecture.AARCH64),
                                ScriptedXattr.NEVER_CALLED)
                        .host(),
                "an x86-64 artefact running under Rosetta 2 is still installed on a macOS host");
        assertEquals(
                HostOperatingSystem.LINUX,
                PlatformFixups.forHost(LINUX, ScriptedXattr.NEVER_CALLED).host());
        assertEquals("PlatformFixups[host=linux]", linux().toString());
        assertEquals("/usr/bin/xattr", PlatformFixups.XATTR);
        assertEquals(Duration.ofSeconds(30), PlatformFixups.XATTR_TIMEOUT);
    }

    @Test
    @DisplayName(
            "the fix-ups reject nulls, naming the argument, and a timeout that is not positive")
    void nullsAndBadTimeoutsAreRejected() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new PlatformFixups(
                                Nulls.of(HostOperatingSystem.class), ScriptedXattr.NEVER_CALLED));
        assertEquals(
                "processes",
                assertThrows(
                                NullPointerException.class,
                                () ->
                                        new PlatformFixups(
                                                HostOperatingSystem.MACOS,
                                                Nulls.of(
                                                        org.cometgui.domain.ports.ProcessRunner
                                                                .class)))
                        .getMessage());
        assertThrows(
                NullPointerException.class,
                () ->
                        PlatformFixups.forHost(
                                Nulls.of(HostPlatform.class), ScriptedXattr.NEVER_CALLED));
        assertThrows(
                NullPointerException.class,
                () ->
                        new PlatformFixups(
                                HostOperatingSystem.MACOS,
                                ScriptedXattr.NEVER_CALLED,
                                Nulls.of(Duration.class)));
        assertEquals(
                "timeout must be positive, but was: PT0S",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new PlatformFixups(
                                                HostOperatingSystem.MACOS,
                                                ScriptedXattr.NEVER_CALLED,
                                                Duration.ZERO))
                        .getMessage());
        assertEquals(
                "timeout must be positive, but was: PT-0.001S",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new PlatformFixups(
                                                HostOperatingSystem.MACOS,
                                                ScriptedXattr.NEVER_CALLED,
                                                Duration.ofMillis(-1)))
                        .getMessage());
        PlatformFixups fixups = linux();
        assertThrows(
                NullPointerException.class,
                () -> fixups.apply(Nulls.of(Path.class), Nulls.of(ArtefactRecord.class)));
    }

    // ------------------------------------------------------------------ the report types --

    @Test
    @DisplayName("a report cannot call one file both cleared and not cleared")
    void aReportCannotContradictItself() {
        assertEquals(
                "a file cannot be reported both cleared of and still carrying the quarantine"
                        + " attribute: [bin/a, bin/b]",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new FixupReport(
                                                List.of(),
                                                List.of("bin/b", "bin/a", "bin/c"),
                                                List.of("bin/a", "bin/d", "bin/b")))
                        .getMessage());
        FixupReport partial = new FixupReport(List.of(), List.of("bin/c"), List.of("bin/d"));
        assertEquals(List.of("bin/d"), partial.quarantineNotCleared());
        assertFalse(partial.changedNothing(), "bin/c was cleared");
        assertTrue(
                new FixupReport(List.of(), List.of(), List.of("bin/d")).changedNothing(),
                "a file that could not be cleared is not a change");
        assertEquals(List.of(), new FixupReport(List.of(), List.of("x")).quarantineNotCleared());
        assertThrows(
                NullPointerException.class,
                () -> new FixupReport(List.of(), List.of(), Nulls.of(List.class)));
        assertThrows(
                UnsupportedOperationException.class,
                () -> partial.quarantineNotCleared().add("bin/e"));
    }

    @Test
    @DisplayName(
            "the not-cleared exception refuses a report with nothing not cleared, or no reason")
    void theExceptionIsConsistentWithItsReport() {
        FixupReport cleared = new FixupReport(List.of(), List.of("bin/a"));
        assertEquals(
                "a report that cleared every file is not a failure to clear one",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> {
                                    throw new QuarantineNotClearedException(cleared, List.of());
                                })
                        .getMessage());
        FixupReport failed = new FixupReport(List.of(), List.of(), List.of("bin/a", "bin/b"));
        assertEquals(
                "every file that was not cleared needs exactly one reason: 2 file(s), 1 reason(s)",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> {
                                    throw new QuarantineNotClearedException(
                                            failed, List.of("bin/a: why"));
                                })
                        .getMessage());
        QuarantineNotClearedException exception =
                new QuarantineNotClearedException(failed, List.of("bin/a: one", "bin/b: two"));
        assertEquals(failed, exception.report());
        assertEquals(List.of("bin/a: one", "bin/b: two"), exception.reasons());
        assertEquals(
                "com.apple.quarantine could not be shown removed from 2 installed file(s), so"
                        + " Gatekeeper may refuse to run them (R-PLAT-04): bin/a: one; bin/b: two",
                exception.getMessage());
        assertThrows(UnsupportedOperationException.class, () -> exception.reasons().add("x"));
        assertThrows(
                NullPointerException.class,
                () -> {
                    throw new QuarantineNotClearedException(Nulls.of(FixupReport.class), List.of());
                });
        assertThrows(
                NullPointerException.class,
                () -> {
                    throw new QuarantineNotClearedException(failed, Nulls.of(List.class));
                });
    }

    // ------------------------------------------------------------------ fixtures --

    /**
     * Builds an absolute POSIX path, adding the leading separator here rather than in a literal.
     *
     * <p>SpotBugs at effort Max reports a string constant that looks like an absolute pathname as
     * {@code DMI_HARDCODED_ABSOLUTE_FILENAME}; this is the narrow fix {@code ManifestReaderTest}
     * and {@code ManifestWriterTest} already use.
     *
     * @param posixPath a path in POSIX form, without its leading separator
     * @return the absolute path
     */
    private static Path absolute(String posixPath) {
        return Path.of("/" + posixPath);
    }

    private static PlatformFixups linux() {
        return new PlatformFixups(HostOperatingSystem.LINUX, ScriptedXattr.NEVER_CALLED);
    }

    /** An install directory with an executable, a library, a plain file and a link. */
    private record Payload(
            Path directory,
            ArtefactRecord record,
            Path executable,
            Path library,
            Path readme,
            Path alias) {}

    private Payload payload() throws IOException {
        byte[] binary = CacheFixtures.bytes(SCRIPT);
        ArtefactRecord record =
                CacheFixtures.bareExecutable(
                        ToolName.COMET, "2026.02.2", binary, "comet.macos.exe", List.of());
        Path directory = Files.createDirectories(temporary.resolve("payload")).toAbsolutePath();
        Path executable = directory.resolve("comet.macos.exe");
        Files.write(executable, binary);
        Path library = Files.createDirectories(directory.resolve("lib")).resolve("helper.dylib");
        Files.write(library, CacheFixtures.bytes("a library the executable loads"));
        Path readme = directory.resolve("README");
        Files.write(readme, CacheFixtures.bytes("no attribute on this one"));
        Path alias =
                Files.createSymbolicLink(directory.resolve("alias"), Path.of("comet.macos.exe"));
        return new Payload(directory, record, executable, library, readme, alias);
    }

    /*
     * FAILS RATHER THAN SKIPS.  On a file system with no user-defined attribute support the
     * non-macOS branch's "left alone" would be vacuous, and a check that quietly stops running is
     * exactly the shape this project keeps finding.  The message says what to do about it.
     */
    private void quarantine(Path file) throws IOException {
        UserDefinedFileAttributeView view =
                Files.getFileAttributeView(file, UserDefinedFileAttributeView.class);
        if (view == null) {
            throw new AssertionError(
                    "this file system does not publish a UserDefinedFileAttributeView, so the"
                            + " non-macOS branch's 'left alone' cannot be exercised here. Run the"
                            + " tests on a file system with extended attributes (ext4, xfs)"
                            + " rather than accepting a vacuous pass.");
        }
        view.write(
                PlatformFixups.QUARANTINE_ATTRIBUTE,
                StandardCharsets.UTF_8.encode("0083;68b6f0a0;Safari;"));
    }

    private static List<String> attributesOf(Path file) throws IOException {
        UserDefinedFileAttributeView view =
                Files.getFileAttributeView(file, UserDefinedFileAttributeView.class);
        return view == null ? List.of() : view.list();
    }
}
