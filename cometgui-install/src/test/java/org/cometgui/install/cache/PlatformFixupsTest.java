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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@code R-PLAT-05}'s executable bits and {@code R-PLAT-04}'s quarantine removal.
 *
 * <p>The quarantine half runs here, on Linux, against a real extended attribute called {@code
 * com.apple.quarantine} -- the same {@link UserDefinedFileAttributeView} code macOS would run, with
 * the platform supplying its own namespace. That makes it <em>tier A</em> by {@code STATUS.rst}'s
 * rule: a divergent branch executed here by a faithful stand-in, rather than code that has never
 * run. <strong>What is not proved, and is not claimed anywhere, is that macOS's Gatekeeper then
 * accepts the binary</strong>; no macOS machine exists in this project.
 */
class PlatformFixupsTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static final String SCRIPT = "#!/bin/sh" + System.lineSeparator() + "echo tool";

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

        FixupReport report = new PlatformFixups(HostOperatingSystem.LINUX).apply(directory, record);

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

        FixupReport report = new PlatformFixups(HostOperatingSystem.LINUX).apply(directory, record);

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

        FixupReport report = new PlatformFixups(HostOperatingSystem.LINUX).apply(directory, record);

        assertEquals(List.of(), report.madeExecutable());
        assertFalse(Files.isExecutable(directory.resolve("pdv.jar")));
    }

    @Test
    @DisplayName("on a macOS host the quarantine attribute is removed from every file, and named")
    void onMacOsEveryFileLosesItsQuarantineAttribute() throws IOException, InterruptedException {
        byte[] binary = CacheFixtures.bytes(SCRIPT);
        ArtefactRecord record =
                CacheFixtures.bareExecutable(
                        ToolName.COMET, "2026.02.2", binary, "comet.macos.exe", List.of());
        Path directory = Files.createDirectories(temporary.resolve("payload"));
        Path executable = directory.resolve("comet.macos.exe");
        Files.write(executable, binary);
        Path library = Files.createDirectories(directory.resolve("lib")).resolve("helper.dylib");
        Files.write(library, CacheFixtures.bytes("a library the executable loads"));
        Path untouched = directory.resolve("README");
        Files.write(untouched, CacheFixtures.bytes("no attribute on this one"));
        // A symbolic link is delivered to the walk as something that is not a regular file, and
        // must be left exactly alone: following it would clear an attribute outside the install.
        Files.createSymbolicLink(directory.resolve("alias"), Path.of("comet.macos.exe"));
        quarantine(executable);
        quarantine(library);

        FixupReport report = new PlatformFixups(HostOperatingSystem.MACOS).apply(directory, record);

        assertEquals(
                List.of("comet.macos.exe", "lib/helper.dylib"),
                report.quarantineCleared().stream().sorted().toList(),
                "R-PLAT-04 covers every file that will be executed, and a library loaded from a"
                        + " quarantined path fails the same way an executable does");
        assertFalse(attributesOf(executable).contains(PlatformFixups.QUARANTINE_ATTRIBUTE));
        assertFalse(attributesOf(library).contains(PlatformFixups.QUARANTINE_ATTRIBUTE));
        assertTrue(
                Files.isRegularFile(untouched),
                "a file that carried no attribute is neither reported nor disturbed");
    }

    @ParameterizedTest
    @EnumSource(
            value = HostOperatingSystem.class,
            names = {"LINUX", "WINDOWS"})
    @DisplayName("on a host that is not macOS the quarantine attribute is left exactly where it is")
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

        FixupReport report = new PlatformFixups(host).apply(directory, record);

        assertEquals(List.of(), report.quarantineCleared());
        assertTrue(
                attributesOf(executable).contains(PlatformFixups.QUARANTINE_ATTRIBUTE),
                "a fix-up that ran everywhere would be one nobody could tell had run at all");
    }

    @Test
    @DisplayName("the fix-ups are chosen by the HOST, not by the artefact's platform")
    void theHostChoosesTheFixups() {
        assertEquals(
                HostOperatingSystem.MACOS,
                PlatformFixups.forHost(
                                new HostPlatform(
                                        HostOperatingSystem.MACOS, HostArchitecture.AARCH64))
                        .host(),
                "an x86-64 artefact running under Rosetta 2 is still installed on a macOS host");
        assertEquals(HostOperatingSystem.LINUX, PlatformFixups.forHost(LINUX).host());
        assertEquals(
                "PlatformFixups[host=linux]",
                new PlatformFixups(HostOperatingSystem.LINUX).toString());
    }

    @Test
    @DisplayName("the fix-ups reject nulls, naming the argument")
    void nullsAreRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new PlatformFixups(Nulls.of(HostOperatingSystem.class)));
        assertThrows(
                NullPointerException.class,
                () -> PlatformFixups.forHost(Nulls.of(HostPlatform.class)));
        PlatformFixups fixups = new PlatformFixups(HostOperatingSystem.LINUX);
        assertThrows(
                NullPointerException.class,
                () -> fixups.apply(Nulls.of(Path.class), Nulls.of(ArtefactRecord.class)));
    }

    /*
     * FAILS RATHER THAN SKIPS.  On a file system with no user-defined attribute support the macOS
     * branch would silently stop being exercised, and a check that quietly stops running is exactly
     * the shape this project keeps finding.  The message says what to do about it.
     */
    private void quarantine(Path file) throws IOException {
        UserDefinedFileAttributeView view =
                Files.getFileAttributeView(file, UserDefinedFileAttributeView.class);
        if (view == null) {
            throw new AssertionError(
                    "this file system does not publish a UserDefinedFileAttributeView, so the"
                            + " R-PLAT-04 quarantine step cannot be exercised here. Run the"
                            + " tests on a"
                            + " file system with extended attributes (ext4, xfs, apfs) rather than"
                            + " accepting a skipped security fix-up.");
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
