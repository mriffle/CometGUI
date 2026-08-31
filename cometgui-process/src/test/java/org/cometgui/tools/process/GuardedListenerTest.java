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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cometgui.domain.ports.ProcessListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Proves a listener that throws cannot silence a run, and cannot do so invisibly either. */
class GuardedListenerTest {

    @Test
    @DisplayName("every callback reaches the delegate unchanged")
    void everyCallbackReachesTheDelegate() {
        Recorder recorder = new Recorder(false);
        GuardedListener guarded = new GuardedListener(recorder);

        guarded.onStandardOutput("out 0");
        guarded.onStandardError("err 0");
        guarded.onExit(7);

        assertEquals(List.of("out:out 0", "err:err 0", "exit:7"), recorder.seen());
        assertEquals(0L, guarded.failureCount());
        assertEquals(Optional.empty(), guarded.firstFailureDescription());
    }

    @Test
    @DisplayName("a listener that throws on every callback loses nothing and is counted")
    void aThrowingListenerIsCountedAndTheRestSurvives() {
        Recorder recorder = new Recorder(true);
        GuardedListener guarded = new GuardedListener(recorder);

        guarded.onStandardOutput("out 0");
        guarded.onStandardOutput("out 1");
        guarded.onStandardError("err 0");
        guarded.onExit(0);

        assertEquals(
                List.of("out:out 0", "out:out 1", "err:err 0", "exit:0"),
                recorder.seen(),
                "the delegate must still have been called for every one of them");
        assertEquals(4L, guarded.failureCount());
    }

    @Test
    @DisplayName("the first failure is described, and later ones do not overwrite it")
    void theFirstFailureIsDescribed() {
        GuardedListener guarded =
                new GuardedListener(
                        new ProcessListener() {
                            @Override
                            public void onStandardOutput(String line) {
                                throw new IllegalStateException("first " + line);
                            }

                            @Override
                            public void onStandardError(String line) {
                                throw new IllegalArgumentException("second " + line);
                            }

                            @Override
                            public void onExit(int exitCode) {
                                throw new UnsupportedOperationException("third");
                            }
                        });

        guarded.onStandardOutput("A");
        guarded.onStandardError("B");
        guarded.onExit(1);

        assertEquals(3L, guarded.failureCount());
        assertEquals(
                Optional.of("java.lang.IllegalStateException: first A"),
                guarded.firstFailureDescription());
    }

    @Test
    @DisplayName("an Error thrown by a listener is caught too, not just an exception")
    void anErrorIsCaughtToo() {
        GuardedListener guarded =
                new GuardedListener(
                        new ProcessListener() {
                            @Override
                            public void onStandardOutput(String line) {
                                throw new StackOverflowError("deep");
                            }

                            @Override
                            public void onStandardError(String line) {
                                throw new AssertionError("no");
                            }

                            @Override
                            public void onExit(int exitCode) {
                                throw new LinkageError("late");
                            }
                        });

        guarded.onStandardOutput("A");
        guarded.onStandardError("B");
        guarded.onExit(2);

        assertEquals(3L, guarded.failureCount());
        assertEquals(
                Optional.of("java.lang.StackOverflowError: deep"),
                guarded.firstFailureDescription());
    }

    @Test
    @DisplayName("a null delegate is rejected, naming the argument")
    void aNullDelegateIsRejected() {
        NullPointerException rejected =
                assertThrows(NullPointerException.class, () -> new GuardedListener(null));

        assertTrue(
                rejected.getMessage().contains("listener"),
                "the message should name the argument, but was: " + rejected.getMessage());
    }

    /** Records every callback it receives, and optionally throws afterwards. */
    private static final class Recorder implements ProcessListener {

        private final List<String> seen = new ArrayList<>();
        private final boolean throwing;

        private Recorder(boolean throwing) {
            this.throwing = throwing;
        }

        @Override
        public void onStandardOutput(String line) {
            seen.add("out:" + line);
            maybeThrow("onStandardOutput");
        }

        @Override
        public void onStandardError(String line) {
            seen.add("err:" + line);
            maybeThrow("onStandardError");
        }

        @Override
        public void onExit(int exitCode) {
            seen.add("exit:" + exitCode);
            maybeThrow("onExit");
        }

        private void maybeThrow(String where) {
            if (throwing) {
                throw new IllegalStateException(where + " refuses to co-operate");
            }
        }

        private List<String> seen() {
            return List.copyOf(seen);
        }
    }
}
