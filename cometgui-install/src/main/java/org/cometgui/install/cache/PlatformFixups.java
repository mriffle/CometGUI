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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.install.registry.ArtefactRecord;

/**
 * Step 5 of the atomic install: the two things a freshly extracted file needs before it can be run.
 *
 * <h2>{@code R-PLAT-05} -- the executable bit</h2>
 *
 * <p><em>"Downloaded executables shall be made executable (POSIX permission bits) as part of the
 * atomic install, since Comet and Percolator artefacts include bare executables and
 * archive-preserved modes cannot be relied on."</em> Neither source of a mode can be trusted here:
 * a bare executable arrives over HTTP with no mode at all, and the modes inside an archive are
 * upstream's, written on upstream's machine. So the bit is set rather than preserved.
 *
 * <p>The bit is added the way {@code chmod +x} adds it: the owner always gets it, and group and
 * other get it only where they can already read the file. Setting all three unconditionally would
 * make a file executable by users who cannot read it, which is a permission nobody asked for.
 *
 * <h2>{@code R-PLAT-04} -- the macOS quarantine attribute, and what is honestly known about it</h2>
 *
 * <p><em>"On macOS, every file extracted or downloaded into the tool cache that will be executed
 * shall have its {@code com.apple.quarantine} extended attribute cleared."</em> Gatekeeper refuses
 * to run a quarantined binary with a dialog a background application cannot dismiss, so the
 * attribute is removed from every file in the install directory -- every file, not only the
 * executable, because a helper library loaded by a quarantined path fails the same way.
 *
 * <p><strong>What has been executed, and what has not.</strong> The removal itself runs on this
 * Linux host: {@link UserDefinedFileAttributeView} is the same API on both platforms, and Linux
 * stores the attribute under the {@code user.} namespace while macOS stores it raw, so the code
 * that lists and deletes it is <em>the same code</em> and is exercised here against a real
 * attribute of that name. By {@code STATUS.rst}'s two-tier rule that makes it <strong>tier
 * A</strong> -- a divergent branch executed here by a faithful stand-in -- and not residue.
 *
 * <p><strong>What is not proved, and is not claimed.</strong> That macOS's Gatekeeper then accepts
 * the binary. No macOS machine exists in this project and no macOS binary has ever been executed
 * anywhere in it, so exit gate item 9 of this phase cannot be met here and this class does not
 * pretend otherwise. What is proved is that a file carrying an attribute called {@code
 * com.apple.quarantine} does not carry one afterwards.
 *
 * <p>Note also that CometGUI's own downloads are written by this application through {@code
 * java.net.http} and are not quarantined by LaunchServices in the first place; the step is
 * defensive, and {@link FixupReport#quarantineCleared()} reports what was actually removed rather
 * than what was attempted, so a run that removed nothing says so.
 */
public final class PlatformFixups {

    /** The extended attribute macOS marks a downloaded file with. */
    public static final String QUARANTINE_ATTRIBUTE = "com.apple.quarantine";

    /** Which host this is, which decides whether the quarantine step runs. */
    private final HostOperatingSystem host;

    /**
     * Creates the fix-ups for a host.
     *
     * @param host the operating system this application is running on -- <strong>not</strong> the
     *     platform the artefact was built for; a macOS artefact installed under emulation is still
     *     installed on the host that has the quarantine attribute
     * @throws NullPointerException if {@code host} is {@code null}
     */
    public PlatformFixups(HostOperatingSystem host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    /**
     * Creates the fix-ups for the operating-system half of a host platform.
     *
     * @param host the host platform
     * @return the fix-ups
     * @throws NullPointerException if {@code host} is {@code null}
     */
    public static PlatformFixups forHost(HostPlatform host) {
        return new PlatformFixups(Objects.requireNonNull(host, "host").operatingSystem());
    }

    /**
     * The host these fix-ups are for.
     *
     * @return the operating system
     */
    public HostOperatingSystem host() {
        return host;
    }

    /**
     * Applies both fix-ups to a staged install directory.
     *
     * @param directory the directory holding the extracted files
     * @param record the manifest record, which says whether the installed file is an executable
     * @return what was changed
     * @throws IOException if a permission or an attribute cannot be changed
     * @throws NullPointerException if either argument is {@code null}
     */
    public FixupReport apply(Path directory, ArtefactRecord record) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(record, "record");
        List<String> madeExecutable = new ArrayList<>();
        if (record.executable()) {
            String relative = record.executablePath();
            if (makeExecutable(directory.resolve(relative))) {
                madeExecutable.add(relative);
            }
        }
        List<String> quarantineCleared =
                host == HostOperatingSystem.MACOS ? clearQuarantine(directory) : List.of();
        return new FixupReport(madeExecutable, quarantineCleared);
    }

    /*
     * Returns whether anything changed, so that FixupReport lists a file only when this step is the
     * reason it is executable.  A file system with no POSIX view -- Windows -- has no bit to set
     * and nothing to report; that is not a failure, it is a platform without the concept.
     */
    private static boolean makeExecutable(Path file) throws IOException {
        PosixFileAttributeView view =
                Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (view == null) {
            return false;
        }
        Set<PosixFilePermission> current = view.readAttributes().permissions();
        Set<PosixFilePermission> wanted = EnumSet.copyOf(current);
        wanted.add(PosixFilePermission.OWNER_EXECUTE);
        if (current.contains(PosixFilePermission.GROUP_READ)) {
            wanted.add(PosixFilePermission.GROUP_EXECUTE);
        }
        if (current.contains(PosixFilePermission.OTHERS_READ)) {
            wanted.add(PosixFilePermission.OTHERS_EXECUTE);
        }
        if (wanted.equals(current)) {
            return false;
        }
        view.setPermissions(wanted);
        return true;
    }

    private static List<String> clearQuarantine(Path directory) throws IOException {
        List<String> cleared = new ArrayList<>();
        Files.walkFileTree(
                directory,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                            throws IOException {
                        if (attributes.isRegularFile() && removeQuarantine(file)) {
                            cleared.add(relative(directory, file));
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
        return cleared;
    }

    /*
     * LIST FIRST, THEN DELETE.  Deleting an attribute that is not there throws on both platforms,
     * and a caught-and-ignored exception here would make the step report success whatever happened.
     * Asking first means the returned list is a record of what was actually removed.
     */
    private static boolean removeQuarantine(Path file) throws IOException {
        UserDefinedFileAttributeView attributes =
                Files.getFileAttributeView(
                        file, UserDefinedFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes == null || !attributes.list().contains(QUARANTINE_ATTRIBUTE)) {
            return false;
        }
        attributes.delete(QUARANTINE_ATTRIBUTE);
        return true;
    }

    private static String relative(Path directory, Path file) {
        StringBuilder path = new StringBuilder(32);
        for (Path segment : directory.relativize(file)) {
            if (path.length() > 0) {
                path.append('/');
            }
            path.append(segment);
        }
        return path.toString();
    }

    /**
     * Describes the fix-ups without disclosing a path.
     *
     * @return the host these fix-ups are for
     */
    @Override
    public String toString() {
        return "PlatformFixups[host=" + host.id() + "]";
    }
}
