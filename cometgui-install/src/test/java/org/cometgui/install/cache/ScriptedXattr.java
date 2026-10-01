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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;

/**
 * A {@link ProcessRunner} that answers the two {@code /usr/bin/xattr} command shapes {@link
 * PlatformFixups} uses, from a set of files it holds to be quarantined.
 *
 * <p><strong>What this is, and what it is not.</strong> The {@link ProcessRunner} port is the seam
 * production really calls -- the application passes the process service through it -- so the branch
 * of {@link PlatformFixups} under test here is the production branch, with nothing replaced but the
 * external program. What this class is <em>not</em> is macOS's {@code xattr}. It models the
 * behaviour the product relies on: {@code xattr -- FILE} prints one attribute name per line and
 * exits 0; {@code xattr -d NAME -- FILE} removes the name and exits 0, or exits 1 with {@code No
 * such xattr} if it is absent. Whether the real tool behaves like that on a real Mac is a question
 * only the macos-gatekeeper workflow answers, by reading the attribute back with {@code xattr -p}.
 *
 * <p>Any command of another shape is a test failure, not an answer: a product that started passing
 * different arguments would otherwise be graded against a model of something it no longer runs.
 */
final class ScriptedXattr implements ProcessRunner {

    /** For every host that is not macOS: the quarantine step must never start a process there. */
    static final ProcessRunner NEVER_CALLED =
            (command, listener) -> {
                throw new AssertionError(
                        "no process may be started by the fix-ups on a host that is not macOS, but"
                                + " this was: "
                                + command);
            };

    /** How one file's commands are answered. */
    enum Behaviour {
        /** As the product relies on xattr behaving. */
        FAITHFUL,
        /** The deletion exits 0 and removes nothing: the silent no-op, in a process. */
        DELETE_IGNORED,
        /** The deletion is refused with exit 1 and a message on standard error. */
        DELETE_REFUSED,
        /** The listing is refused with exit 1 and a message on standard error. */
        LIST_REFUSED,
        /** The listing exits 1 and prints nothing at all. */
        LIST_SILENTLY_REFUSED,
        /** The program cannot be started at all. */
        NOT_STARTED,
        /** The process never ends of its own accord. */
        NEVER_ENDS
    }

    private final Set<Path> quarantined = Collections.synchronizedSet(new HashSet<>());
    private final Map<Path, Behaviour> behaviours = Collections.synchronizedMap(new HashMap<>());
    private final List<ToolCommand> commands = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final boolean fromAnotherThread;

    private ScriptedXattr(boolean fromAnotherThread) {
        this.fromAnotherThread = fromAnotherThread;
    }

    /**
     * A model that answers before {@code start} returns.
     *
     * @return the model
     */
    static ScriptedXattr answeringAtOnce() {
        return new ScriptedXattr(false);
    }

    /**
     * A model that answers on another thread after {@code start} has returned, as the process
     * service does.
     *
     * @return the model
     */
    static ScriptedXattr answeringFromAnotherThread() {
        return new ScriptedXattr(true);
    }

    /**
     * Marks a file as carrying {@code com.apple.quarantine}.
     *
     * @param file the absolute path
     * @return this model
     */
    ScriptedXattr quarantine(Path file) {
        quarantined.add(file);
        return this;
    }

    /**
     * Answers one file's commands in a particular way.
     *
     * @param file the absolute path
     * @param behaviour how
     * @return this model
     */
    ScriptedXattr behave(Path file, Behaviour behaviour) {
        behaviours.put(file, behaviour);
        return this;
    }

    /**
     * Whether the model still holds the file quarantined.
     *
     * @param file the absolute path
     * @return {@code true} if it does
     */
    boolean isQuarantined(Path file) {
        return quarantined.contains(file);
    }

    /**
     * Every command started, in order.
     *
     * @return the commands
     */
    List<ToolCommand> commands() {
        synchronized (commands) {
            return List.copyOf(commands);
        }
    }

    /**
     * The argument arrays of every command that named a file, in order.
     *
     * @param file the absolute path
     * @return the argument arrays
     */
    List<List<String>> argvFor(Path file) {
        List<List<String>> found = new ArrayList<>();
        for (ToolCommand command : commands()) {
            List<String> argv = command.argv();
            if (argv.get(argv.size() - 1).equals(file.toString())) {
                found.add(argv);
            }
        }
        return found;
    }

    /**
     * Whether a process was asked to stop.
     *
     * @return {@code true} once a cancellation was requested
     */
    boolean wasCancelled() {
        return cancelled.get();
    }

    @Override
    public RunningProcess start(ToolCommand command, ProcessListener listener) throws IOException {
        commands.add(command);
        List<String> argv = command.argv();
        Path file = Path.of(argv.get(argv.size() - 1));
        Behaviour behaviour = behaviours.getOrDefault(file, Behaviour.FAITHFUL);
        if (behaviour == Behaviour.NOT_STARTED) {
            throw new IOException(
                    "could not start " + command,
                    new IOException(
                            "Cannot run program \"/usr/bin/xattr\": error=2, No such file or"
                                    + " directory"));
        }
        if (behaviour == Behaviour.NEVER_ENDS) {
            return new Handle(cancelled);
        }
        Reply reply = answer(argv, file, behaviour);
        if (fromAnotherThread) {
            Thread answering = new Thread(() -> reply.deliver(listener), "scripted-xattr");
            answering.setDaemon(true);
            answering.start();
        } else {
            reply.deliver(listener);
        }
        return new Handle(cancelled);
    }

    private Reply answer(List<String> argv, Path file, Behaviour behaviour) {
        String name = PlatformFixups.QUARANTINE_ATTRIBUTE;
        if (argv.equals(List.of("/usr/bin/xattr", "--", file.toString()))) {
            if (behaviour == Behaviour.LIST_REFUSED) {
                return new Reply(List.of(), List.of("xattr: No such file: " + file), 1);
            }
            if (behaviour == Behaviour.LIST_SILENTLY_REFUSED) {
                return new Reply(List.of(), List.of(), 1);
            }
            List<String> names = new ArrayList<>();
            names.add("com.apple.provenance");
            if (quarantined.contains(file)) {
                names.add(name);
            }
            return new Reply(names, List.of(), 0);
        }
        if (argv.equals(List.of("/usr/bin/xattr", "-d", name, "--", file.toString()))) {
            if (behaviour == Behaviour.DELETE_REFUSED) {
                return new Reply(
                        List.of(),
                        List.of("xattr: [Errno 1] Operation not permitted: '" + file + "'"),
                        1);
            }
            if (behaviour == Behaviour.DELETE_IGNORED) {
                return new Reply(List.of(), List.of(), 0);
            }
            if (!quarantined.remove(file)) {
                return new Reply(
                        List.of(), List.of("xattr: " + file + ": No such xattr: " + name), 1);
            }
            return new Reply(List.of(), List.of(), 0);
        }
        throw new AssertionError("the fix-ups ran a command xattr's model does not know: " + argv);
    }

    /** What a scripted process prints and how it ends. */
    private record Reply(List<String> standardOutput, List<String> standardError, int exitCode) {
        void deliver(ProcessListener listener) {
            standardOutput.forEach(listener::onStandardOutput);
            standardError.forEach(listener::onStandardError);
            listener.onExit(exitCode);
        }
    }

    /** The handle on a scripted process. */
    private static final class Handle implements RunningProcess {
        private final AtomicBoolean cancelled;

        Handle(AtomicBoolean cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public boolean isAlive() {
            return false;
        }

        @Override
        public int waitForExit() {
            throw new AssertionError(
                    "the fix-ups must wait for onExit, which follows the last output line, and"
                            + " not for the process");
        }

        @Override
        public void requestCancellation() {
            cancelled.set(true);
        }
    }
}
