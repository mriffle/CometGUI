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

package org.cometgui.tools.process;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The order in which a process and its descendants must be killed, and the killing itself.
 *
 * <p>Pure with respect to time: nothing here waits, sleeps or polls. It is separate from {@code
 * StartedProcess} so that the ordering rule below can be proved against hand-built {@link
 * ProcessHandle}s, rather than only against a real process tree that a test cannot construct
 * deterministically.
 *
 * <h2>Why descendants die first, and why the snapshot is taken before anything dies</h2>
 *
 * <p>{@link Process#descendants()} is a <em>snapshot</em> of the tree as it is at the moment of the
 * call. Once the parent dies, its children are reparented away from it and the same call returns
 * nothing, so the snapshot must be taken <strong>before</strong> anything is destroyed or the
 * children become invisible.
 *
 * <p>The parent must also die <strong>last</strong>. If it goes first, its child is reparented to
 * PID 1; in a container whose PID 1 is not an init that reaps orphans, a child killed after that
 * becomes a permanent zombie -- {@code /proc/<pid>} still exists, so {@link
 * ProcessHandle#isAlive()} stays {@code true} for ever and {@link ProcessHandle#onExit()} never
 * completes. The phase's fake tool reproduced exactly that. Killing deepest-first, while every
 * parent is still alive to reap its own child, avoids it.
 *
 * <h2>Why {@link ProcessHandle#destroy()} and never {@link Process#destroy()}</h2>
 *
 * <p>On Linux, OpenJDK's {@code Process.destroy()} closes the process's standard input, output and
 * error before signalling it. The pumps then fail with {@code IOException: Stream closed} and
 * output the tool had already written -- the last thing it said before it was cancelled, which is
 * usually the interesting part -- is lost. {@code ProcessHandle.destroy()} sends the same {@code
 * SIGTERM} and leaves the pipes alone, so the pumps drain to EOF naturally. The same applies to the
 * forcible escalation.
 */
final class ProcessTree {

    private ProcessTree() {
        throw new AssertionError("ProcessTree is a helper, not a type to instantiate");
    }

    /**
     * Snapshots the tree rooted at {@code root} and returns it in the order it must be killed:
     * every descendant deepest first, then the root itself.
     *
     * <p>Depth is computed by walking each descendant's {@link ProcessHandle#parent()} chain up to
     * the root rather than by trusting the iteration order of {@link Process#descendants()}, which
     * is unspecified. A descendant whose ancestry cannot be walked -- it exited during the
     * snapshot, or the platform will not report a parent -- is treated as the deepest, so it is
     * killed before anything that might be its parent.
     *
     * @param root the process to cancel
     * @return the handles to destroy, in order, immutable and never empty
     * @throws NullPointerException if {@code root} is null
     */
    static List<ProcessHandle> terminationOrder(ProcessHandle root) {
        Objects.requireNonNull(root, "root");
        List<ProcessHandle> descendants = new ArrayList<>(root.descendants().toList());
        int unknownDepth = descendants.size() + 1;
        long rootPid = root.pid();
        descendants.sort(
                Comparator.comparingInt(
                                (ProcessHandle handle) -> depthBelow(rootPid, handle, unknownDepth))
                        .reversed());
        List<ProcessHandle> ordered = new ArrayList<>(descendants.size() + 1);
        ordered.addAll(descendants);
        ordered.add(root);
        return List.copyOf(ordered);
    }

    /**
     * Asks each handle to stop, politely, in the order given.
     *
     * @param ordered the handles, deepest first, as {@link #terminationOrder(ProcessHandle)}
     *     returned them
     */
    static void destroyAll(List<ProcessHandle> ordered) {
        for (ProcessHandle handle : ordered) {
            handle.destroy();
        }
    }

    /**
     * Kills whatever is still alive, in the order given.
     *
     * <p>The liveness check is not an optimisation: a handle whose process has already exited may
     * have had its PID reused by then, and signalling it would kill something unrelated.
     *
     * @param ordered the handles, deepest first, as {@link #terminationOrder(ProcessHandle)}
     *     returned them
     */
    static void destroyAllForcibly(List<ProcessHandle> ordered) {
        for (ProcessHandle handle : ordered) {
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
        }
    }

    /**
     * A future completing when every handle has exited.
     *
     * <p>Built from {@link ProcessHandle#onExit()} rather than from a poll or a sleep, so that the
     * escalation from terminate to kill is driven by the processes themselves.
     *
     * @param ordered the handles to wait on
     * @return a future completing when all of them have ended
     */
    static CompletableFuture<Void> whenAllExited(List<ProcessHandle> ordered) {
        CompletableFuture<?>[] exits =
                ordered.stream().map(ProcessHandle::onExit).toArray(CompletableFuture<?>[]::new);
        return CompletableFuture.allOf(exits);
    }

    /**
     * How many parent links separate {@code handle} from the process with {@code rootPid}.
     *
     * @param rootPid the pid the walk is looking for
     * @param handle the descendant whose depth is wanted
     * @param unknownDepth what to answer when the walk does not reach the root
     * @return one for a direct child, two for a grandchild, {@code unknownDepth} if unreachable
     */
    private static int depthBelow(long rootPid, ProcessHandle handle, int unknownDepth) {
        int depth = 0;
        Optional<ProcessHandle> ancestor = handle.parent();
        while (ancestor.isPresent() && depth < unknownDepth) {
            depth++;
            if (ancestor.get().pid() == rootPid) {
                return depth;
            }
            ancestor = ancestor.get().parent();
        }
        return unknownDepth;
    }
}
