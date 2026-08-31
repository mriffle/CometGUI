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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves the cancellation ORDER, against process trees built by hand.
 *
 * <p>The order is the whole point and it is not a detail. Killing a parent before its children
 * reparents them to PID 1, and in a container whose PID 1 does not reap orphans the killed child
 * becomes a permanent zombie whose {@code isAlive()} never goes false. A real process tree cannot
 * be arranged deterministically inside a test, so the tree is built out of {@link ProcessHandle}
 * implementations that record exactly what was called on them and in what order.
 *
 * <p>Every expected value is hand-typed. {@code descendants()} deliberately hands the class under
 * test the WRONG order, so that a test passing because the platform happened to return a convenient
 * order is impossible.
 */
class ProcessTreeTest {

    /** Records every destroy call made anywhere in one tree, in order. */
    private final List<String> calls = new ArrayList<>();

    @Test
    @DisplayName("descendants come first, deepest first, and the root comes last")
    void deepestDescendantsFirstThenTheRoot() {
        FakeHandle root = new FakeHandle(100, null);
        FakeHandle child = new FakeHandle(200, root);
        FakeHandle grandchild = new FakeHandle(300, child);
        FakeHandle greatGrandchild = new FakeHandle(400, grandchild);
        root.setDescendants(List.of(child, grandchild, greatGrandchild));

        List<Long> order = pidsOf(ProcessTree.terminationOrder(root));

        assertEquals(List.of(400L, 300L, 200L, 100L), order);
    }

    @Test
    @DisplayName("the order does not depend on the order descendants() happened to return")
    void theOrderDoesNotDependOnTheSnapshotOrder() {
        FakeHandle root = new FakeHandle(100, null);
        FakeHandle child = new FakeHandle(200, root);
        FakeHandle grandchild = new FakeHandle(300, child);
        FakeHandle greatGrandchild = new FakeHandle(400, grandchild);
        root.setDescendants(List.of(grandchild, greatGrandchild, child));

        List<Long> order = pidsOf(ProcessTree.terminationOrder(root));

        assertEquals(List.of(400L, 300L, 200L, 100L), order);
    }

    @Test
    @DisplayName("two siblings at the same depth keep their snapshot order, before their parent")
    void siblingsKeepTheirOrderAndPrecedeTheirParent() {
        FakeHandle root = new FakeHandle(100, null);
        FakeHandle child = new FakeHandle(200, root);
        FakeHandle firstGrandchild = new FakeHandle(310, child);
        FakeHandle secondGrandchild = new FakeHandle(320, child);
        root.setDescendants(List.of(child, firstGrandchild, secondGrandchild));

        List<Long> order = pidsOf(ProcessTree.terminationOrder(root));

        assertEquals(List.of(310L, 320L, 200L, 100L), order);
    }

    @Test
    @DisplayName("a process with no descendants is terminated alone")
    void aProcessWithNoDescendants() {
        FakeHandle root = new FakeHandle(100, null);

        List<Long> order = pidsOf(ProcessTree.terminationOrder(root));

        assertEquals(List.of(100L), order);
    }

    @Test
    @DisplayName("a descendant whose ancestry cannot be walked is treated as the deepest")
    void anOrphanedDescendantIsTreatedAsDeepest() {
        FakeHandle root = new FakeHandle(100, null);
        FakeHandle child = new FakeHandle(200, root);
        FakeHandle detached = new FakeHandle(900, null);
        root.setDescendants(List.of(child, detached));

        List<Long> order = pidsOf(ProcessTree.terminationOrder(root));

        assertEquals(List.of(900L, 200L, 100L), order);
    }

    @Test
    @DisplayName("destroyAll asks every handle to stop, in the order it was given")
    void destroyAllFollowsTheOrderGiven() {
        FakeHandle root = new FakeHandle(100, null);
        FakeHandle child = new FakeHandle(200, root);
        FakeHandle grandchild = new FakeHandle(300, child);
        root.setDescendants(List.of(child, grandchild));

        ProcessTree.destroyAll(ProcessTree.terminationOrder(root));

        assertEquals(List.of("destroy:300", "destroy:200", "destroy:100"), calls);
    }

    @Test
    @DisplayName("destroyAllForcibly kills what is still alive and leaves what is not")
    void destroyAllForciblySkipsTheDead() {
        FakeHandle root = new FakeHandle(100, null);
        FakeHandle child = new FakeHandle(200, root);
        FakeHandle grandchild = new FakeHandle(300, child);
        root.setDescendants(List.of(child, grandchild));
        child.setAlive(false);

        ProcessTree.destroyAllForcibly(ProcessTree.terminationOrder(root));

        assertEquals(List.of("destroyForcibly:300", "destroyForcibly:100"), calls);
    }

    @Test
    @DisplayName("destroyAllForcibly on a tree that is entirely dead signals nothing at all")
    void destroyAllForciblyOnADeadTreeSignalsNothing() {
        FakeHandle root = new FakeHandle(100, null);
        FakeHandle child = new FakeHandle(200, root);
        root.setDescendants(List.of(child));
        root.setAlive(false);
        child.setAlive(false);

        ProcessTree.destroyAllForcibly(ProcessTree.terminationOrder(root));

        assertEquals(List.of(), calls);
    }

    @Test
    @DisplayName("whenAllExited completes only once every handle has exited")
    void whenAllExitedWaitsForEveryHandle()
            throws ExecutionException, InterruptedException, TimeoutException {
        FakeHandle root = new FakeHandle(100, null);
        FakeHandle child = new FakeHandle(200, root);
        root.setDescendants(List.of(child));
        List<ProcessHandle> ordered = ProcessTree.terminationOrder(root);

        CompletableFuture<Void> all = ProcessTree.whenAllExited(ordered);

        assertFalse(all.isDone(), "nothing has exited yet");
        child.exit();
        assertFalse(all.isDone(), "the root is still running");
        root.exit();
        all.get(60, TimeUnit.SECONDS);
        assertTrue(all.isDone(), "both have exited, so the aggregate has completed");
    }

    @Test
    @DisplayName("a null root is rejected")
    void aNullRootIsRejected() {
        assertThrows(NullPointerException.class, () -> ProcessTree.terminationOrder(null));
    }

    private static List<Long> pidsOf(List<ProcessHandle> handles) {
        return handles.stream().map(ProcessHandle::pid).toList();
    }

    /**
     * A process handle that exists only in this test's head.
     *
     * <p>It records what was asked of it and never touches an operating system process, which is
     * what lets the ordering rule be asserted exactly rather than observed statistically.
     */
    private final class FakeHandle implements ProcessHandle {

        private final long pid;
        private final ProcessHandle parent;
        private final CompletableFuture<ProcessHandle> exit = new CompletableFuture<>();
        private List<ProcessHandle> descendants = List.of();
        private boolean alive = true;

        private FakeHandle(long pid, ProcessHandle parent) {
            this.pid = pid;
            this.parent = parent;
        }

        private void setDescendants(List<ProcessHandle> tree) {
            descendants = List.copyOf(tree);
        }

        private void setAlive(boolean stillRunning) {
            alive = stillRunning;
        }

        private void exit() {
            alive = false;
            exit.complete(this);
        }

        @Override
        public long pid() {
            return pid;
        }

        @Override
        public Optional<ProcessHandle> parent() {
            return Optional.ofNullable(parent);
        }

        @Override
        public Stream<ProcessHandle> children() {
            return descendants.stream()
                    .filter(handle -> handle.parent().map(ProcessHandle::pid).orElse(-1L) == pid);
        }

        @Override
        public Stream<ProcessHandle> descendants() {
            return descendants.stream();
        }

        @Override
        public Info info() {
            throw new UnsupportedOperationException("a fake handle has no process information");
        }

        @Override
        public CompletableFuture<ProcessHandle> onExit() {
            /* A copy, not the field: handing out the future itself would let a caller complete
             * this handle's exit, and the real ProcessHandle makes no such offer either. The copy
             * completes on the same thread that completes the source, so a test can still assert
             * on it the instant exit() returns. */
            return exit.copy();
        }

        @Override
        public boolean supportsNormalTermination() {
            return true;
        }

        @Override
        public boolean destroy() {
            calls.add("destroy:" + pid);
            return alive;
        }

        @Override
        public boolean destroyForcibly() {
            calls.add("destroyForcibly:" + pid);
            return alive;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public int compareTo(ProcessHandle other) {
            return Long.compare(pid, other.pid());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof FakeHandle handle && handle.pid == pid;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(pid);
        }
    }
}
