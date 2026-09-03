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

package org.cometgui.install.manager;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What {@code install} does with the work before anything is downloaded, and when the row settles.
 *
 * <p>{@code ToolManager.install} is documented as returning "as soon as the install has started",
 * which makes <em>where</em> the work went and <em>when</em> the row stops saying {@code
 * INSTALLING} the two things worth asserting -- and neither is visible from a test that hands the
 * manager a real thread pool and waits for the end. So these tests hand it an executor that
 * captures the work instead of running it, and run it by hand.
 *
 * <p>That is not a seam production leaves alone: the executor is a constructor argument precisely
 * because the application supplies a thread pool and a user interface has to decide which thread an
 * install runs on. What changes here is which executor, not which code path.
 */
class ManagedToolManagerLifecycleTest {

    @TempDir private Path cacheRoot;

    private static final Set<ToolCapability> XML_CAPABLE =
            Set.of(ToolCapability.XML_OUTPUT, ToolCapability.XML_DECOY_OUTPUT);

    /** An executor that keeps the work instead of running it, so a test can run it deliberately. */
    private static final class CapturingExecutor implements Executor {

        private final List<Runnable> captured = Collections.synchronizedList(new ArrayList<>());
        private volatile boolean refusing;

        CapturingExecutor refusing() {
            this.refusing = true;
            return this;
        }

        int captured() {
            return captured.size();
        }

        void runNext() {
            captured.remove(0).run();
        }

        @Override
        public void execute(Runnable command) {
            if (refusing) {
                throw new RejectedExecutionException("this executor is not taking work");
            }
            captured.add(command);
        }
    }

    @Test
    @DisplayName("install hands the work to the executor and the row says INSTALLING at once")
    void installSubmitsTheWorkAndTheRowSaysSo() throws IOException {
        CapturingExecutor executor = new CapturingExecutor();
        try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot, executor)) {
            harness.mirror().serving(harness.recordOf(ToolName.PERCOLATOR, "3.07.1"));
            harness.probe().observing(ToolName.PERCOLATOR, XML_CAPABLE);

            harness.manager()
                    .install(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), progress -> {});

            assertAll(
                    () ->
                            assertEquals(
                                    1,
                                    executor.captured(),
                                    "the install is somebody else's thread's work, and it has been"
                                            + " handed over before install() returns"),
                    () ->
                            assertEquals(
                                    ToolInstallState.INSTALLING,
                                    harness.offerOf(ToolName.PERCOLATOR, "3.07.1").state(),
                                    "and the row says so before a single byte has moved"),
                    () -> assertEquals(List.of(), harness.probe().probed()));

            executor.runNext();

            assertEquals(
                    ToolInstallState.INSTALLED,
                    harness.offerOf(ToolName.PERCOLATOR, "3.07.1").state());
        }
    }

    /*
     * AN EXECUTOR THAT RUNS THE WORK WHERE IT IS GIVEN, so that everything an install does is
     * finished by the time install() returns and every assertion below is made without waiting for
     * anything.  That matters for more than speed: a test that waits on a latch cannot tell a
     * defect which stops the install from one which merely makes it slow, and under mutation
     * testing a mutant that stops it scores as a timeout rather than as a kill -- which build.sh
     * does not count.  Runnable::run is an ordinary Executor and this is the same code path; only
     * the thread differs.
     */
    @Test
    @DisplayName("with a direct executor the whole install is over before install() returns")
    void aDirectExecutorFinishesBeforeInstallReturns() throws IOException {
        try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot, Runnable::run)) {
            harness.mirror().serving(harness.recordOf(ToolName.PERCOLATOR, "3.07.1"));
            harness.probe().observing(ToolName.PERCOLATOR, XML_CAPABLE);
            List<String> seen = Collections.synchronizedList(new ArrayList<>());

            harness.manager()
                    .install(
                            ToolName.PERCOLATOR,
                            ToolVersion.parse("3.07.1"),
                            progress -> seen.add(progress.phase().name()));

            assertAll(
                    () ->
                            assertEquals(
                                    ToolInstallState.INSTALLED,
                                    harness.offerOf(ToolName.PERCOLATOR, "3.07.1").state()),
                    () ->
                            assertEquals(
                                    "DONE",
                                    seen.isEmpty()
                                            ? "nothing was reported"
                                            : seen.get(seen.size() - 1),
                                    "exactly one report carries a terminal phase and it is the"
                                            + " last one"),
                    () ->
                            assertEquals(
                                    1,
                                    seen.stream().filter("DONE"::equals).count(),
                                    "and only one"),
                    () ->
                            assertEquals(
                                    "DOWNLOADING",
                                    seen.isEmpty() ? "nothing was reported" : seen.get(0),
                                    "the caller's listener sees the whole install and not only"
                                            + " its end"));
        }
    }

    @Test
    @DisplayName("an executor that refuses the work leaves no row claiming to be installing")
    void anExecutorThatRefusesLeavesNothingRunning() throws IOException {
        CapturingExecutor executor = new CapturingExecutor().refusing();
        try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot, executor)) {
            assertAll(
                    () ->
                            assertThrows(
                                    RejectedExecutionException.class,
                                    () ->
                                            harness.manager()
                                                    .install(
                                                            ToolName.PERCOLATOR,
                                                            ToolVersion.parse("3.07.1"),
                                                            progress -> {})),
                    () ->
                            assertEquals(
                                    ToolInstallState.NOT_INSTALLED,
                                    harness.offerOf(ToolName.PERCOLATOR, "3.07.1").state(),
                                    "an install that never started is not one that is running; a"
                                            + " row left saying INSTALLING would never settle,"
                                            + " because nothing is going to report a terminal"
                                            + " phase for it"));
        }
    }

    /*
     * TWO INSTALLS OF ONE BUILD, which R-TOOL-05 allows and this installer makes idempotent: the
     * second waits on the lock, finds the entry complete and returns it.  The row has to keep
     * saying INSTALLING until BOTH have finished, because a row that settled on the first terminal
     * report would tell a user the work was over while a second attempt was still holding the lock.
     */
    @Test
    @DisplayName("the row keeps saying INSTALLING until every install of that build has finished")
    void twoInstallsOfOneBuildBothHaveToFinish() throws IOException {
        CapturingExecutor executor = new CapturingExecutor();
        try (ToolManagerHarness harness = ToolManagerHarness.onDebian12(cacheRoot, executor)) {
            harness.mirror().serving(harness.recordOf(ToolName.PERCOLATOR, "3.07.1"));
            harness.probe().observing(ToolName.PERCOLATOR, XML_CAPABLE);
            harness.manager()
                    .install(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), progress -> {});
            harness.manager()
                    .install(ToolName.PERCOLATOR, ToolVersion.parse("3.07.1"), progress -> {});

            assertEquals(2, executor.captured());
            executor.runNext();
            ToolInstallState afterTheFirst = harness.offerOf(ToolName.PERCOLATOR, "3.07.1").state();
            executor.runNext();
            ToolInstallState afterBoth = harness.offerOf(ToolName.PERCOLATOR, "3.07.1").state();

            assertAll(
                    () ->
                            assertEquals(
                                    ToolInstallState.INSTALLING,
                                    afterTheFirst,
                                    "one of the two has finished and the other has not"),
                    () -> assertEquals(ToolInstallState.INSTALLED, afterBoth),
                    () ->
                            assertEquals(
                                    List.of("percolator 3.07.1 linux-x86-64"),
                                    harness.probe().probed(),
                                    "and the second install did no work: it found the entry"
                                            + " complete and returned it, which is R-TOOL-05's"
                                            + " idempotence"));
        }
    }
}
