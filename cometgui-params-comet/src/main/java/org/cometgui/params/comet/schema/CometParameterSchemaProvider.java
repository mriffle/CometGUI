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

package org.cometgui.params.comet.schema;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;

/**
 * Asks an installed Comet build for its parameters and returns the versioned schema ({@code
 * R-PARAM-01}, {@code R-PARAM-02}).
 *
 * <p>A build with {@link org.cometgui.domain.tools.ToolCapability#COMPLETE_PARAMS_QUERY} is run as
 * {@code [executable, "-q"]} and its schema is {@link DiscoveryMode#COMPLETE}; any other build is
 * run as {@code [executable, "-p"]} and its schema is marked {@link
 * DiscoveryMode#PARTIAL_DISCOVERY}, which stops drift detection reporting the parameters {@code -p}
 * leaves out as removed. Either way Comet writes {@code comet.params.new} into its working
 * directory, so each call runs in a fresh, empty directory of its own -- a file found in a shared
 * directory could be an earlier run's -- and deletes it afterwards.
 *
 * <p>The process is started through the injected domain {@link ProcessRunner}, which in the
 * application is the one process service; this class never creates a process itself. A non-zero
 * exit, a missing file, a timeout, an interrupt, an unreadable dump and a dump whose marker names a
 * different version from the build's identity each fail with a {@link CometSchemaException} whose
 * {@link CometSchemaException#failure()} says which, and whose message names the command and the
 * directory.
 */
public final class CometParameterSchemaProvider {

    /** Comet's option for the complete parameter file. */
    public static final String COMPLETE_ARGUMENT = "-q";

    /** Comet's option for the default parameter file. */
    public static final String DEFAULTS_ARGUMENT = "-p";

    /** The file Comet writes into its working directory under either option. */
    public static final String WRITTEN_FILE = "comet.params.new";

    /** The most output lines kept for a diagnostic; Comet prints about five. */
    static final int MAXIMUM_OUTPUT_LINES = 200;

    private static final String WORKSPACE_PREFIX = "comet-schema-";

    private final ProcessRunner runner;
    private final CuratedMetadata metadata;
    private final Duration timeout;
    private final Path workspaceRoot;

    /**
     * Creates the provider.
     *
     * @param runner how processes are started; the process service in the application
     * @param metadata the curated metadata to combine with what the build declares
     * @param timeout how long one dump may take before it is cancelled
     * @param workspaceRoot an existing directory under which each call creates its own empty one
     * @throws IllegalArgumentException if the timeout is not positive
     */
    public CometParameterSchemaProvider(
            ProcessRunner runner, CuratedMetadata metadata, Duration timeout, Path workspaceRoot) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("the timeout must be positive, not " + timeout);
        }
    }

    /**
     * The schema for one installed Comet build.
     *
     * @param comet the build
     * @return what it declared, the curated metadata, and the drift between them
     * @throws CometSchemaException if the build could not be asked or its answer cannot be used
     */
    public CometParameterSchema schemaFor(CometToolIdentity comet) throws CometSchemaException {
        Objects.requireNonNull(comet, "comet");
        DiscoveryMode mode =
                comet.hasCompleteParamsQuery()
                        ? DiscoveryMode.COMPLETE
                        : DiscoveryMode.PARTIAL_DISCOVERY;
        String argument = mode == DiscoveryMode.COMPLETE ? COMPLETE_ARGUMENT : DEFAULTS_ARGUMENT;
        Path workspace;
        try {
            workspace = Files.createTempDirectory(workspaceRoot, WORKSPACE_PREFIX).toAbsolutePath();
        } catch (IOException cannotCreate) {
            throw new CometSchemaException(
                    CometSchemaException.Failure.LAUNCH_FAILED,
                    "could not create an empty working directory under "
                            + workspaceRoot
                            + " to run Comet in: "
                            + cannotCreate.getMessage(),
                    List.of(),
                    cannotCreate);
        }
        try {
            ToolCommand command =
                    new ToolCommand(
                            List.of(comet.executable().toString(), argument), workspace, Map.of());
            String text = run(command, workspace);
            DiscoveredSchema discovered;
            try {
                discovered = SchemaDiscovery.discover(text, mode);
            } catch (MalformedDumpException malformed) {
                throw new CometSchemaException(
                        CometSchemaException.Failure.UNREADABLE_DUMP,
                        command.displayString()
                                + " wrote a "
                                + WRITTEN_FILE
                                + " that is not a well-formed parameter dump: "
                                + malformed.getMessage(),
                        List.of(),
                        malformed);
            }
            if (!discovered.marker().toolVersion().equals(comet.version())) {
                throw new CometSchemaException(
                        CometSchemaException.Failure.VERSION_MISMATCH,
                        "the build at "
                                + comet.executable()
                                + " was identified as Comet "
                                + comet.version().text()
                                + " but its "
                                + argument
                                + " output is marked \""
                                + discovered.marker().text()
                                + "\", which is Comet "
                                + discovered.marker().toolVersion().text(),
                        List.of(),
                        null);
            }
            return new CometParameterSchema(
                    comet, discovered, metadata, SchemaDrift.compare(discovered, metadata));
        } finally {
            deleteRecursively(workspace);
        }
    }

    private String run(ToolCommand command, Path workspace) throws CometSchemaException {
        Collector collector = new Collector();
        RunningProcess process;
        try {
            process = runner.start(command, collector);
        } catch (IOException cannotStart) {
            throw new CometSchemaException(
                    CometSchemaException.Failure.LAUNCH_FAILED,
                    command.displayString()
                            + " could not be started in "
                            + workspace
                            + ": "
                            + cannotStart.getMessage(),
                    List.of(),
                    cannotStart);
        }
        try {
            if (!collector.exited.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.requestCancellation();
                throw new CometSchemaException(
                        CometSchemaException.Failure.TIMED_OUT,
                        command.displayString()
                                + " did not finish within "
                                + timeout
                                + " in "
                                + workspace
                                + " and was cancelled",
                        collector.lines(),
                        null);
            }
        } catch (InterruptedException interrupted) {
            process.requestCancellation();
            Thread.currentThread().interrupt();
            throw new CometSchemaException(
                    CometSchemaException.Failure.INTERRUPTED,
                    "interrupted while waiting for " + command.displayString() + "; cancelled it",
                    collector.lines(),
                    interrupted);
        }
        int exitCode = collector.exitCode.get();
        if (exitCode != 0) {
            throw new CometSchemaException(
                    CometSchemaException.Failure.NON_ZERO_EXIT,
                    command.displayString()
                            + " in "
                            + workspace
                            + " exited "
                            + exitCode
                            + ", so whatever it wrote is not its parameter set",
                    collector.lines(),
                    null);
        }
        Path written = workspace.resolve(WRITTEN_FILE);
        if (!Files.isRegularFile(written)) {
            throw new CometSchemaException(
                    CometSchemaException.Failure.NO_FILE_WRITTEN,
                    command.displayString()
                            + " exited 0 and wrote no "
                            + WRITTEN_FILE
                            + " into "
                            + workspace,
                    collector.lines(),
                    null);
        }
        try {
            byte[] bytes = Files.readAllBytes(written);
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException notText) {
            throw new CometSchemaException(
                    CometSchemaException.Failure.UNREADABLE_DUMP,
                    written + " written by " + command.displayString() + " is not UTF-8 text",
                    collector.lines(),
                    notText);
        } catch (IOException unreadable) {
            throw new CometSchemaException(
                    CometSchemaException.Failure.UNREADABLE_DUMP,
                    written + " could not be read: " + unreadable.getMessage(),
                    collector.lines(),
                    unreadable);
        }
    }

    /* Best effort: a directory left behind is litter, not a wrong answer. */
    private static void deleteRecursively(Path directory) {
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path entry : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException tidyingFailed) {
            /* Nothing useful can be done; the schema returned or the failure thrown stands. */
        }
    }

    /** Collects both streams, bounded, and the exit code, on the process runner's threads. */
    private static final class Collector implements ProcessListener {

        private final List<String> output = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger exitCode = new AtomicInteger(Integer.MIN_VALUE);
        private final CountDownLatch exited = new CountDownLatch(1);

        @Override
        public void onStandardOutput(String line) {
            add("[stdout] " + line);
        }

        @Override
        public void onStandardError(String line) {
            add("[stderr] " + line);
        }

        @Override
        public void onExit(int code) {
            exitCode.set(code);
            exited.countDown();
        }

        private void add(String line) {
            synchronized (output) {
                if (output.size() < MAXIMUM_OUTPUT_LINES) {
                    output.add(line);
                }
            }
        }

        List<String> lines() {
            synchronized (output) {
                return List.copyOf(output);
            }
        }
    }
}
