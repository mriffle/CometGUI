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

package org.cometgui.workflow.pdv;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.HashService;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventLog;
import org.cometgui.provenance.events.ProvenanceEventType;
import org.cometgui.tools.pdv.PdvJarIdentity;

/**
 * The one way a PDV process is started: every launch is recorded in the run's event log
 * <strong>before</strong> the process service is asked to start it (design decision P11-4).
 *
 * <h2>Why a runner and not a method</h2>
 *
 * <p>PDV is started by code that builds its own argument array -- {@code
 * org.cometgui.tools.pdv.PdvFigures} for a figure, the {@code denovo-gui} launcher for the window
 * -- and hands it to a {@link ProcessRunner}. So the record is made <em>at</em> that runner: {@link
 * #forRun} returns one bound to a run, an installed PDV and a launch kind, and each {@link
 * ProcessRunner#start} through it
 *
 * <ol>
 *   <li>refuses a command that does not launch that PDV JAR -- this runner starts PDV and nothing
 *       else;
 *   <li>hashes the JAR with the one hasher (MD5 and SHA-256 in one pass) and reads the version the
 *       JAR's own manifest reports ({@link PdvJarIdentity}: PDV has no version option and cannot
 *       run headless), and refuses a JAR that reports a version other than the one the Tool Manager
 *       offered it as;
 *   <li>appends one {@link ProvenanceEventType#PDV_LAUNCHED} event to the run's {@code events.log}
 *       and closes the log;
 *   <li>and only then starts the process through the process service.
 * </ol>
 *
 * <p>A failure in steps 1 to 3 starts nothing. A failure from step 4 onwards -- PDV cannot be
 * started, exits non-zero, writes nothing -- leaves the record, because the record says that PDV
 * was launched, which it was. Every process started through such a runner has exactly one event
 * written before it, so "recorded whenever PDV is launched" holds by construction rather than by
 * each caller remembering.
 *
 * <h2>The payload</h2>
 *
 * <ul>
 *   <li>{@value #RUN_ID} the run; {@value #TOOL} {@code pdv}; {@value #VERSION} the reported
 *       version;
 *   <li>{@value #JAR_PATH}, {@value #JAR_MD5}, {@value #JAR_SHA256};
 *   <li>{@value #KIND} the {@link PdvLaunchKind}'s wire name;
 *   <li>{@value #ARGV_COUNT} and {@value #ARGV_PREFIX}{@code 0} .. {@code n-1}: the argument array
 *       after {@link SecretRedactor#redactArgv}, the executable first;
 *   <li>{@value #WORKING_DIRECTORY}; {@value #ENVIRONMENT}, the variables the command sets, sorted
 *       by name, one {@code NAME=value} per line, after {@link SecretRedactor#redactEnvironment};
 *   <li>{@value #CONTEXT_PREFIX}{@code <key>}: what the launch was for, as its caller states it --
 *       the PSM, the spectrum, the source file.
 * </ul>
 *
 * <p>Every value then passes through the event log's own redaction as well. The run's event log is
 * written by the workflow engine while the run executes; a caller launches PDV only from a run that
 * is not executing, and says so to the scientist rather than racing the engine for the file.
 */
public final class PdvLaunches {

    /** The run the launch was made from. */
    public static final String RUN_ID = ProvenanceEvent.RUN_ID_KEY;

    /** The tool: always {@value #PDV}. */
    public static final String TOOL = ProvenanceEvent.TOOL_KEY;

    /** The version the JAR's manifest reports. */
    public static final String VERSION = ProvenanceEvent.TOOL_VERSION_KEY;

    /** The JAR's absolute path. */
    public static final String JAR_PATH = "pdv.jar.path";

    /** The JAR's MD5. */
    public static final String JAR_MD5 = "pdv.jar.md5";

    /** The JAR's SHA-256. */
    public static final String JAR_SHA256 = "pdv.jar.sha256";

    /** The launch kind's wire name. */
    public static final String KIND = "pdv.launch";

    /** How many elements the argument array has. */
    public static final String ARGV_COUNT = "argv.count";

    /** Each element of the argument array is under this prefix and its 0-based index. */
    public static final String ARGV_PREFIX = "argv.";

    /** The process's working directory. */
    public static final String WORKING_DIRECTORY = "process.working-directory";

    /** The environment the command sets. */
    public static final String ENVIRONMENT = "process.environment";

    /** What the launch was for, under this prefix and the caller's key. */
    public static final String CONTEXT_PREFIX = "launch.";

    /** The value of {@value #TOOL}. */
    public static final String PDV = "pdv";

    /** What a caller's context key must look like: lower case, digits and inner hyphens. */
    private static final Pattern CONTEXT_KEY = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private final ProcessRunner processes;

    private final HashService hashes;

    private final SecretRedactor redactor;

    private final Clock clock;

    private final HostPlatform host;

    private final PdvJarIdentity identity = new PdvJarIdentity();

    /**
     * The launch path over the application's one process service and one hasher.
     *
     * @param processes the process service, which this hands every recorded start to
     * @param hashes the one hasher
     * @param redactor the one secret rule set
     * @param clock dates every event
     * @param host this computer, which {@link PdvJarIdentity} is asked about
     */
    public PdvLaunches(
            ProcessRunner processes,
            HashService hashes,
            SecretRedactor redactor,
            Clock clock,
            HostPlatform host) {
        this.processes = Objects.requireNonNull(processes, "processes");
        this.hashes = Objects.requireNonNull(hashes, "hashes");
        this.redactor = Objects.requireNonNull(redactor, "redactor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.host = Objects.requireNonNull(host, "host");
    }

    /**
     * A runner through which PDV is launched from one run, every start recorded first.
     *
     * @param run the run's directory, whose {@code events.log} receives the record
     * @param runId the run's identifier, recorded with it
     * @param pdv the installed PDV the launch uses
     * @param kind what the launch is
     * @param context what the launch is for: each key lower case, digits and inner hyphens; each
     *     value recorded under {@value #CONTEXT_PREFIX} and the key
     * @return the runner
     * @throws IllegalArgumentException if a context key is not of that form
     */
    public ProcessRunner forRun(
            RunLayout run,
            RunId runId,
            PdvInstallation pdv,
            PdvLaunchKind kind,
            Map<String, String> context) {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(pdv, "pdv");
        Objects.requireNonNull(kind, "kind");
        Map<String, String> stated = new TreeMap<>();
        for (Map.Entry<String, String> entry :
                Objects.requireNonNull(context, "context").entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "a context key");
            if (!CONTEXT_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException(
                        "a PDV launch's context key must be lower case, digits and inner hyphens,"
                                + " but was: \""
                                + key
                                + "\"");
            }
            stated.put(
                    CONTEXT_PREFIX + key,
                    Objects.requireNonNull(entry.getValue(), () -> "the value of " + key));
        }
        return new Recording(run, runId, pdv, kind, Map.copyOf(stated));
    }

    /** A runner bound to one run and one PDV. */
    private final class Recording implements ProcessRunner {

        private final RunLayout run;
        private final RunId runId;
        private final PdvInstallation pdv;
        private final PdvLaunchKind kind;
        private final Map<String, String> context;

        Recording(
                RunLayout run,
                RunId runId,
                PdvInstallation pdv,
                PdvLaunchKind kind,
                Map<String, String> context) {
            this.run = run;
            this.runId = runId;
            this.pdv = pdv;
            this.kind = kind;
            this.context = context;
        }

        @Override
        public RunningProcess start(ToolCommand command, ProcessListener listener)
                throws IOException {
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(listener, "listener");
            Path jar = pdv.jar();
            if (!command.argv().contains(jar.toString())) {
                throw new IOException(
                        "this runner launches PDV from "
                                + jar
                                + " and nothing else, and was asked to start: "
                                + command.displayString());
            }
            FileHashes jarHashes = hashes.hash(jar);
            ToolVersion reported = identity.identify(ToolName.PDV, host, jar);
            if (!reported.equals(pdv.version())) {
                throw new IOException(
                        jar
                                + " reports PDV "
                                + reported.text()
                                + " in its manifest, but the Tool Manager offered it as PDV "
                                + pdv.version().text()
                                + "; nothing was launched");
            }
            Map<String, String> payload = new LinkedHashMap<>(context);
            payload.put(RUN_ID, runId.value());
            payload.put(TOOL, PDV);
            payload.put(VERSION, reported.text());
            payload.put(JAR_PATH, jar.toString());
            payload.put(JAR_MD5, jarHashes.md5());
            payload.put(JAR_SHA256, jarHashes.sha256());
            payload.put(KIND, kind.wireName());
            List<String> argv = redactor.redactArgv(command.argv());
            payload.put(ARGV_COUNT, Integer.toString(argv.size()));
            for (int index = 0; index < argv.size(); index++) {
                payload.put(ARGV_PREFIX + index, argv.get(index));
            }
            payload.put(WORKING_DIRECTORY, command.workingDirectory().toString());
            payload.put(ENVIRONMENT, environment(command.environment()));
            try (ProvenanceEventLog log =
                    ProvenanceEventLog.openAppend(run.eventLogFile(), redactor, clock)) {
                log.append(ProvenanceEventType.PDV_LAUNCHED, payload);
            }
            return processes.start(command, listener);
        }

        private String environment(Map<String, String> variables) {
            Map<String, String> sorted = new TreeMap<>(redactor.redactEnvironment(variables));
            List<String> lines = new ArrayList<>(sorted.size());
            for (Map.Entry<String, String> variable : sorted.entrySet()) {
                lines.add(variable.getKey() + "=" + variable.getValue());
            }
            return String.join("\n", lines);
        }
    }
}
