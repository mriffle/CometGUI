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
import java.io.InterruptedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
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
 * <h2>{@code R-PLAT-04} -- the macOS quarantine attribute</h2>
 *
 * <p><em>"On macOS, every file extracted or downloaded into the tool cache that will be executed
 * shall have its {@code com.apple.quarantine} extended attribute cleared."</em> Gatekeeper refuses
 * to run a quarantined binary with a dialog a background application cannot dismiss, so the
 * attribute is removed from every regular file in the install directory -- every file, not only the
 * executable, because a helper library loaded by a quarantined path fails the same way. Symbolic
 * links are not followed and not touched: following one would clear an attribute outside the
 * install.
 *
 * <h3>Why this runs {@code /usr/bin/xattr} and not Java's attribute view</h3>
 *
 * <p>Until phase 05 unit 14 this class removed the attribute through {@link
 * java.nio.file.attribute.UserDefinedFileAttributeView}, on the premise that the JDK stores names
 * under {@code user.} on Linux and raw on macOS, so the same code would reach the same attribute on
 * both. <strong>That premise is false, and the first macOS run in this project showed it</strong>
 * (macos-gatekeeper workflow run 36918810975, {@code macos-latest}, Temurin 21.0.12.1): {@code
 * /usr/bin/xattr} read {@code com.apple.quarantine} before and after the old code ran, the Java
 * view listed no attribute at all, and the report said nothing had been cleared.
 *
 * <p>The JDK source says why, and it says it for every JDK this product can run on. On macOS the
 * provider is {@code sun.nio.fs.BsdFileSystemProvider}, which answers a request for the view with
 * {@code new BsdUserDefinedFileAttributeView(file, followLinks)}; that class overrides only {@code
 * maxNameLength()} and inherits everything else from {@code
 * sun.nio.fs.UnixUserDefinedFileAttributeView} -- the <em>same</em> class Linux uses -- whose
 * {@code nameAsBytes} prepends {@code "user."} to every name it is given and whose {@code list()}
 * keeps only names starting with {@code "user."} and strips the prefix. Read at OpenJDK tags {@code
 * jdk-21.0.12+8} (openjdk/jdk21u), {@code jdk-21+35} and {@code jdk-25+36} (openjdk/jdk); the
 * project's own Liberica 25.0.4.1 {@code src.zip} carries the identical Unix class. So on macOS the
 * view can neither see nor delete {@code com.apple.quarantine}: asked to delete it, it would delete
 * {@code user.com.apple.quarantine}, a different attribute that nothing sets. There is no Java API
 * in the JDK that reaches a raw macOS attribute name.
 *
 * <p>So on a macOS host each regular file is handled with macOS's own tool, through the product's
 * one process launcher (the {@link ProcessRunner} port, implemented only by the process service, as
 * {@code R-PROC-02} and its ArchUnit rule require), always as an argument array and never through a
 * shell:
 *
 * <ol>
 *   <li>{@code /usr/bin/xattr -- FILE} lists the file's attribute names, one per line;
 *   <li>only if {@code com.apple.quarantine} is among them, {@code /usr/bin/xattr -d
 *       com.apple.quarantine -- FILE} deletes it, and must exit 0;
 *   <li>then the names are listed <strong>again</strong>, and the file is reported cleared only if
 *       the attribute is no longer among them. A deletion is re-checked, never assumed.
 * </ol>
 *
 * <p>The process environment is constructed, as the process service requires: {@code PATH} names
 * only the system directories, so a {@code /usr/bin/xattr} that is itself a wrapper finds its
 * interpreter, and nothing from the user's shell reaches it.
 *
 * <h3>A file that cannot be cleared is a failure, and is named</h3>
 *
 * <p>The old code's defect was that it could change nothing and say nothing. So now every regular
 * file ends in exactly one of three places: carried no attribute (not listed), {@linkplain
 * FixupReport#quarantineCleared() cleared}, or {@linkplain FixupReport#quarantineNotCleared() not
 * cleared} with the reason -- {@code /usr/bin/xattr} could not be started, exited non-zero, did not
 * finish within the timeout, or exited 0 and left the attribute in place. If any file is not
 * cleared the step throws {@link QuarantineNotClearedException}, which names each file and why and
 * carries the whole report, and the install fails -- the same outcome a failed attribute change has
 * always had in this step, and better than an installed tool Gatekeeper will refuse the first time
 * a scientist runs it. A step that cannot even ask {@code xattr} is not a step that may report
 * success.
 *
 * <h3>What has been executed where, and what has not</h3>
 *
 * <p><strong>On Linux</strong> the macOS branch is graded by {@code PlatformFixupsTest} through a
 * scripted {@link ProcessRunner} -- the port production calls -- standing in for {@code
 * /usr/bin/xattr}: the exact argument arrays, the order, the re-check, and every failure path. The
 * real process service is also run against the macOS branch on Linux, where there is no {@code
 * /usr/bin/xattr}, and the result is a named failure, not a silent pass. <strong>None of that is
 * evidence about macOS.</strong> Whether {@code /usr/bin/xattr} on a real Mac lists and deletes the
 * attribute the way the script stands in for it can be shown only by the macos-gatekeeper workflow,
 * which reads the attribute back with {@code xattr -p} after this step and grades this report
 * against what it sees. Until that workflow has run on this code and said so, the macOS branch is
 * unverified.
 *
 * <p>Gatekeeper accepting the binary afterwards is a separate question (phase 05 exit gate item 9)
 * that this class does not answer and does not claim.
 *
 * <p>CometGUI's own downloads are written by this application through {@code java.net.http} and are
 * not quarantined by LaunchServices in the first place; the step is defensive, and {@link
 * FixupReport#quarantineCleared()} reports what was actually removed rather than what was
 * attempted, so a run that removed nothing says so.
 */
public final class PlatformFixups {

    /** The extended attribute macOS marks a downloaded file with. */
    public static final String QUARANTINE_ATTRIBUTE = "com.apple.quarantine";

    /** macOS's own extended-attribute tool, named absolutely so no search path chooses it. */
    public static final String XATTR = "/usr/bin/xattr";

    /** How long one {@code xattr} invocation may take before it is cancelled and reported. */
    static final Duration XATTR_TIMEOUT = Duration.ofSeconds(30);

    /*
     * The constructed environment (R-PROC-04): the process service passes a tool nothing it was not
     * given.  The system directories only, so a wrapper script finds its interpreter.
     */
    private static final Map<String, String> XATTR_ENVIRONMENT =
            Map.of("PATH", "/usr/bin:/bin:/usr/sbin:/sbin");

    /** Which host this is, which decides whether the quarantine step runs. */
    private final HostOperatingSystem host;

    /** The product's one process launcher, through which {@code xattr} is run. */
    private final ProcessRunner processes;

    /** How long one {@code xattr} invocation may take. */
    private final Duration timeout;

    /**
     * Creates the fix-ups for a host.
     *
     * @param host the operating system this application is running on -- <strong>not</strong> the
     *     platform the artefact was built for; a macOS artefact installed under emulation is still
     *     installed on the host that has the quarantine attribute
     * @param processes the process launcher {@code /usr/bin/xattr} is run through on macOS; on any
     *     other host it is never called
     * @throws NullPointerException if either argument is {@code null}
     */
    public PlatformFixups(HostOperatingSystem host, ProcessRunner processes) {
        this(host, processes, XATTR_TIMEOUT);
    }

    /*
     * The timeout is a constructor argument only so that a test can reach the timed-out path in
     * milliseconds rather than half a minute; production always takes XATTR_TIMEOUT above.
     */
    PlatformFixups(HostOperatingSystem host, ProcessRunner processes, Duration timeout) {
        this.host = Objects.requireNonNull(host, "host");
        this.processes = Objects.requireNonNull(processes, "processes");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive, but was: " + timeout);
        }
    }

    /**
     * Creates the fix-ups for the operating-system half of a host platform.
     *
     * @param host the host platform
     * @param processes the process launcher, used on macOS only
     * @return the fix-ups
     * @throws NullPointerException if either argument is {@code null}
     */
    public static PlatformFixups forHost(HostPlatform host, ProcessRunner processes) {
        return new PlatformFixups(
                Objects.requireNonNull(host, "host").operatingSystem(), processes);
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
     * @throws QuarantineNotClearedException on macOS, if any regular file could not be shown free
     *     of {@code com.apple.quarantine} afterwards; it names each such file and carries the
     *     report
     * @throws InterruptedIOException if the thread was interrupted while {@code xattr} ran; the
     *     interrupt status is restored and the process is asked to stop
     * @throws IOException if a permission cannot be changed or the directory cannot be walked
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
        if (host != HostOperatingSystem.MACOS) {
            return new FixupReport(madeExecutable, List.of());
        }
        Quarantine quarantine = new Quarantine();
        clearQuarantine(directory, quarantine);
        FixupReport report =
                new FixupReport(madeExecutable, quarantine.cleared, quarantine.notCleared);
        if (!quarantine.notCleared.isEmpty()) {
            throw new QuarantineNotClearedException(report, quarantine.reasons);
        }
        return report;
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

    private void clearQuarantine(Path directory, Quarantine quarantine) throws IOException {
        Files.walkFileTree(
                directory,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                            throws IOException {
                        if (attributes.isRegularFile()) {
                            clearOne(directory, file, quarantine);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    /*
     * LIST, DELETE, LIST AGAIN.  Listing first means a file that never carried the attribute is not
     * reported as cleared; listing again means a deletion that exited 0 and changed nothing -- the
     * exact shape of the defect this replaced -- is reported as not cleared rather than believed.
     */
    private void clearOne(Path directory, Path file, Quarantine quarantine)
            throws InterruptedIOException {
        String relative = relative(directory, file);
        try {
            if (!file.getFileSystem().equals(FileSystems.getDefault())) {
                throw new NotCleared(
                        "it is not on the operating system's file system, so "
                                + XATTR
                                + " cannot be given its path");
            }
            Path absolute = file.toAbsolutePath();
            Path workingDirectory = directory.toAbsolutePath();
            if (!quarantined(absolute, workingDirectory)) {
                return;
            }
            Output deleted =
                    run(
                            List.of(XATTR, "-d", QUARANTINE_ATTRIBUTE, "--", absolute.toString()),
                            workingDirectory);
            if (deleted.exitCode != 0) {
                throw new NotCleared(XATTR + " -d " + deleted.describe());
            }
            if (quarantined(absolute, workingDirectory)) {
                throw new NotCleared(
                        XATTR
                                + " -d exited 0 and "
                                + QUARANTINE_ATTRIBUTE
                                + " is still listed afterwards");
            }
            quarantine.cleared.add(relative);
        } catch (NotCleared failure) {
            quarantine.notCleared.add(relative);
            quarantine.reasons.add(relative + ": " + failure.getMessage());
        }
    }

    private boolean quarantined(Path absolute, Path workingDirectory)
            throws NotCleared, InterruptedIOException {
        Output listed = run(List.of(XATTR, "--", absolute.toString()), workingDirectory);
        if (listed.exitCode != 0) {
            throw new NotCleared(XATTR + " could not list its attributes: " + listed.describe());
        }
        for (String name : listed.standardOutput) {
            if (name.strip().equals(QUARANTINE_ATTRIBUTE)) {
                return true;
            }
        }
        return false;
    }

    private Output run(List<String> argv, Path workingDirectory)
            throws NotCleared, InterruptedIOException {
        Collector collector = new Collector();
        RunningProcess process;
        try {
            process =
                    processes.start(
                            new ToolCommand(argv, workingDirectory, XATTR_ENVIRONMENT), collector);
        } catch (IOException notStarted) {
            throw new NotCleared(XATTR + " could not be started: " + wholeChain(notStarted));
        }
        boolean finished;
        try {
            finished = collector.finished.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            process.requestCancellation();
            Thread.currentThread().interrupt();
            InterruptedIOException stopped =
                    new InterruptedIOException(
                            "interrupted while waiting for " + XATTR + " to finish");
            stopped.initCause(interrupted);
            throw stopped;
        }
        if (!finished) {
            process.requestCancellation();
            throw new NotCleared(
                    XATTR
                            + " did not finish within "
                            + timeout.toMillis()
                            + " ms and was cancelled");
        }
        return collector.output();
    }

    /*
     * The process service wraps what the runtime threw -- "could not start ToolCommand[...]" -- and
     * the part a reader needs, "error=2, No such file or directory", is in the cause.
     */
    private static String wholeChain(Throwable failure) {
        StringBuilder joined = new StringBuilder();
        for (Throwable link = failure; link != null; link = link.getCause()) {
            if (joined.length() > 0) {
                joined.append(" | ");
            }
            joined.append(link.getMessage());
        }
        return joined.toString();
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

    /** What the quarantine step found, file by file, in the order the walk visited them. */
    private static final class Quarantine {
        private final List<String> cleared = new ArrayList<>();
        private final List<String> notCleared = new ArrayList<>();
        private final List<String> reasons = new ArrayList<>();
    }

    /** Why one file could not be shown free of the attribute. Never escapes this class. */
    private static final class NotCleared extends Exception {
        private static final long serialVersionUID = 1L;

        NotCleared(String reason) {
            super(reason);
        }
    }

    /** What one {@code xattr} run printed, and how it ended. */
    private static final class Output {
        private final int exitCode;
        private final List<String> standardOutput;
        private final List<String> standardError;

        Output(int exitCode, List<String> standardOutput, List<String> standardError) {
            this.exitCode = exitCode;
            this.standardOutput = standardOutput;
            this.standardError = standardError;
        }

        String describe() {
            return "exited "
                    + exitCode
                    + (standardError.isEmpty()
                            ? " and wrote nothing to standard error"
                            : ": " + String.join(" / ", standardError));
        }
    }

    /*
     * Waits for onExit, not for the process: the port promises onExit after the last output line,
     * so a listing read after it is complete.
     */
    private static final class Collector implements ProcessListener {
        private final List<String> standardOutput = Collections.synchronizedList(new ArrayList<>());
        private final List<String> standardError = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger exitCode = new AtomicInteger();
        private final CountDownLatch finished = new CountDownLatch(1);

        @Override
        public void onStandardOutput(String line) {
            standardOutput.add(line);
        }

        @Override
        public void onStandardError(String line) {
            standardError.add(line);
        }

        @Override
        public void onExit(int code) {
            exitCode.set(code);
            finished.countDown();
        }

        Output output() {
            synchronized (standardOutput) {
                synchronized (standardError) {
                    return new Output(
                            exitCode.get(),
                            List.copyOf(standardOutput),
                            List.copyOf(standardError));
                }
            }
        }
    }
}
