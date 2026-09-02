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

package org.cometgui.app.config;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.platform.GlibcVersionSource;
import org.cometgui.domain.ports.Downloader;
import org.cometgui.domain.ports.EnvironmentReader;
import org.cometgui.domain.ports.FileSystemAccess;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunIdSource;

/**
 * The composition root: the one place where the injectable seams of {@code R-PROC-01} are chosen,
 * built once, and handed out.
 *
 * <p>{@code R-PROC-01} names seven: <em>clock, environment reader, process runner, downloader,
 * filesystem abstraction, run-ID source and hash service</em>. All seven are here. An eighth,
 * {@link GlibcVersionSource}, is here for the same reason -- {@code R-PLAT-01}'s baseline check
 * reads a native symbol, and a check that could only be tested on a glibc host would not be a
 * check.
 *
 * <h2>Three seams have no implementation yet, and they say so</h2>
 *
 * <p>{@link ProcessRunner} is phase 03's, {@link HashService} is phase 04's and {@link Downloader}
 * is phase 05's. Two tempting things are deliberately not done with them. There is <strong>no
 * fake</strong> -- a no-op hash service that returns a constant would let a later phase build a
 * provenance record out of fiction and stay green. And there is <strong>no null field</strong> -- a
 * seam that is silently {@code null} fails at a call site far from the cause, in a {@code
 * NullPointerException} that names nothing.
 *
 * <p>Instead the absence is modelled twice over, and both are tested:
 *
 * <ul>
 *   <li>{@link #processRunner()}, {@link #hashService()} and {@link #downloader()} return {@link
 *       Optional}, so a caller that can work without one is made to say so at compile time.
 *   <li>{@link #requireProcessRunner()}, {@link #requireHashService()} and {@link
 *       #requireDownloader()} are for callers that cannot, and throw {@link IllegalStateException}
 *       with a message naming the seam and the phase that delivers it -- {@code "the process runner
 *       is not wired yet: org.cometgui.domain.ports.ProcessRunner is delivered by phase 03"}. A
 *       developer who hits it is told which phase to look at rather than which line dereferenced
 *       null.
 * </ul>
 *
 * <p>When a phase lands one of the three, it passes it to the constructor and the {@code Optional}
 * becomes present; the {@code require} accessors then simply stop throwing. Nothing else changes,
 * and no caller has to be found and rewritten.
 *
 * <h2>Why the run message log is NOT here</h2>
 *
 * <p>It was, and it came out. A composition root that hands out a mutable {@code BoundedMessageLog}
 * is publishing shared mutable state, which SpotBugs reports as {@code EI_EXPOSE_REP} and is right
 * about: nothing at the call site says whether the caller is receiving the application's log or a
 * copy of it. The log is therefore <em>injected</em> rather than published -- {@link
 * org.cometgui.app.bootstrap.CometGuiApplication} takes one and passes it to the console -- which
 * is the same sharing with the ownership stated at the call site. When phase 03's process service
 * arrives it needs to write to that same log; whoever wires it up decides then whether the log is
 * passed to both, and this class is deliberately not the place that decides it in advance.
 *
 * <p>Instances are immutable and hold no I/O resources; {@link #forThisHost()} does no I/O either,
 * and in particular creates no directory.
 */
public final class ApplicationServices {

    private final Clock clock;

    private final EnvironmentReader environment;

    private final FileSystemAccess fileSystem;

    private final RunIdSource runIds;

    private final GlibcVersionSource glibcVersions;

    private final ProcessRunner processRunner;

    private final HashService hashService;

    private final Downloader downloader;

    /**
     * Wires a set of services explicitly. The three seams later phases own may be {@code null},
     * which is what "not delivered yet" means; every other argument is required.
     *
     * @param clock the clock seam
     * @param environment the environment seam
     * @param fileSystem the filesystem seam
     * @param runIds the run-ID seam
     * @param glibcVersions the host's C library version, for the baseline check
     * @param processRunner phase 03's process service, or {@code null} until it exists
     * @param hashService phase 04's hash service, or {@code null} until it exists
     * @param downloader phase 05's downloader, or {@code null} until it exists
     * @throws NullPointerException if any argument other than the last three is {@code null}
     */
    public ApplicationServices(
            Clock clock,
            EnvironmentReader environment,
            FileSystemAccess fileSystem,
            RunIdSource runIds,
            GlibcVersionSource glibcVersions,
            ProcessRunner processRunner,
            HashService hashService,
            Downloader downloader) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        this.runIds = Objects.requireNonNull(runIds, "runIds");
        this.glibcVersions = Objects.requireNonNull(glibcVersions, "glibcVersions");
        this.processRunner = processRunner;
        this.hashService = hashService;
        this.downloader = downloader;
    }

    /**
     * The services the application runs with on this machine: a UTC system clock, the real
     * environment, the real filesystem, a run-ID source over that clock, the foreign-function glibc
     * probe, a message log at its default capacity, and nothing for the three seams later phases
     * own.
     *
     * <p>No I/O happens here. In particular no directory is created: {@link
     * FileSystemAccess#applicationDataDirectory()} computes a path and the caller that needs it
     * creates it.
     *
     * @return the production wiring
     */
    public static ApplicationServices forThisHost() {
        Clock clock = Clock.systemUTC();
        EnvironmentReader environment = new SystemEnvironmentReader();
        return new ApplicationServices(
                clock,
                environment,
                new PlatformFileSystemAccess(environment),
                new ClockRunIdSource(clock),
                new FfmGlibcVersionSource(),
                null,
                null,
                null);
    }

    /**
     * The clock seam.
     *
     * @return the clock, never {@code null}
     */
    public Clock clock() {
        return clock;
    }

    /**
     * The environment seam.
     *
     * @return the environment reader, never {@code null}
     */
    public EnvironmentReader environment() {
        return environment;
    }

    /**
     * The filesystem seam.
     *
     * @return the filesystem access, never {@code null}
     */
    public FileSystemAccess fileSystem() {
        return fileSystem;
    }

    /**
     * The run-ID seam.
     *
     * @return the run-ID source, never {@code null}
     */
    public RunIdSource runIds() {
        return runIds;
    }

    /**
     * The host's C library version, for {@code R-PLAT-01}'s baseline check.
     *
     * @return the glibc version source, never {@code null}
     */
    public GlibcVersionSource glibcVersions() {
        return glibcVersions;
    }

    /**
     * The process seam, if the phase that owns it has landed.
     *
     * @return the process runner, or empty until phase 03 delivers one
     */
    public Optional<ProcessRunner> processRunner() {
        return Optional.ofNullable(processRunner);
    }

    /**
     * The hash seam, if the phase that owns it has landed.
     *
     * @return the hash service, or empty until phase 04 delivers one
     */
    public Optional<HashService> hashService() {
        return Optional.ofNullable(hashService);
    }

    /**
     * The download seam, if the phase that owns it has landed.
     *
     * @return the downloader, or empty until phase 05 delivers one
     */
    public Optional<Downloader> downloader() {
        return Optional.ofNullable(downloader);
    }

    /**
     * The process seam for a caller that cannot proceed without it.
     *
     * @return the process runner
     * @throws IllegalStateException if phase 03 has not delivered one, naming the seam and the
     *     phase
     */
    public ProcessRunner requireProcessRunner() {
        return processRunner()
                .orElseThrow(() -> notWiredYet("process runner", ProcessRunner.class, "03"));
    }

    /**
     * The hash seam for a caller that cannot proceed without it.
     *
     * @return the hash service
     * @throws IllegalStateException if phase 04 has not delivered one, naming the seam and the
     *     phase
     */
    public HashService requireHashService() {
        return hashService()
                .orElseThrow(() -> notWiredYet("hash service", HashService.class, "04"));
    }

    /**
     * The download seam for a caller that cannot proceed without it.
     *
     * @return the downloader
     * @throws IllegalStateException if phase 05 has not delivered one, naming the seam and the
     *     phase
     */
    public Downloader requireDownloader() {
        return downloader().orElseThrow(() -> notWiredYet("downloader", Downloader.class, "05"));
    }

    /**
     * The one diagnostic the three {@code require} accessors share.
     *
     * @param seam the seam's name in prose
     * @param port the port interface, named in full so the message is greppable
     * @param phase the two-digit phase that delivers an implementation
     * @return the exception to throw
     */
    private static IllegalStateException notWiredYet(String seam, Class<?> port, String phase) {
        return new IllegalStateException(
                "the "
                        + seam
                        + " is not wired yet: "
                        + port.getName()
                        + " is delivered by phase "
                        + phase);
    }
}
