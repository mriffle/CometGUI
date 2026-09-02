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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.cometgui.install.registry.ArtefactRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@code R-TOOL-04}, one interruption per step: <em>"interrupted installations shall be safely
 * discarded or resumed and shall never leave a tool that appears installed but is incomplete."</em>
 *
 * <h2>Why a second JVM, and why {@link Runtime#halt(int)}</h2>
 *
 * <p>An in-process test that stopped the pipeline early would still unwind: {@link
 * InstallPipeline#close()} would run, the staging directory would be deleted and the lock released
 * by this program rather than by the operating system. That proves the tidy case and not the rule.
 * {@code halt} runs no {@code finally} block and no shutdown hook, which is what a power cut does
 * to an install -- so the assertions below are made against whatever a crashed process actually
 * left on disk.
 *
 * <h2>Why the enumeration drives it</h2>
 *
 * <p>The parameter is {@link InstallStep} itself, so <strong>a step added to the pipeline is
 * covered by this test the day it is added</strong> and a test that "samples one interruption"
 * cannot creep back in. {@link InstallStepTest} holds the other half: a hand-typed list of the
 * eight, which fails the moment a ninth appears and sends whoever added it here.
 */
class InstallInterruptionTest {

    /** How long to wait for a child JVM. */
    private static final Duration PATIENCE = Duration.ofSeconds(90);

    @TempDir private Path temporary;

    @ParameterizedTest
    @EnumSource(InstallStep.class)
    @DisplayName(
            "a process killed after each step in turn never leaves an entry reporting itself"
                    + " installed")
    void anInstallKilledAfterEachStepNeverReportsItselfInstalled(InstallStep step)
            throws IOException, InterruptedException {
        Path root = Files.createDirectories(temporary.resolve("cache-" + step.number()));
        Path fixture = temporary.resolve("fixture-" + step.number());
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);

        ChildProcesses.Result crashed =
                ChildProcesses.runJava(
                        InstallHaltChild.class,
                        temporary,
                        List.of(root.toString(), fixture.toString(), String.valueOf(step.number())),
                        PATIENCE);

        assertEquals(
                17,
                crashed.exitCode(),
                () ->
                        "the child must have died by halt() after "
                                + step.number()
                                + " step(s): "
                                + crashed.describe());
        assertTrue(
                crashed.standardOutput().contains("ran=" + step.name()),
                () ->
                        "the child must have reached "
                                + step
                                + " and stopped there: "
                                + crashed.describe());
        assertEquals(
                step.number(),
                crashed.standardOutput().stream().filter(line -> line.startsWith("ran=")).count(),
                () ->
                        "the child must have run exactly "
                                + step.number()
                                + " step(s): "
                                + crashed.describe());

        InstallHarness reader = InstallHarness.at(root);
        InstallationCheck check = reader.verify(record);
        boolean installFinished = step == InstallStep.RECORD_INSTALLATION_METADATA;
        assertEquals(
                installFinished,
                check.installed(),
                () ->
                        "a process killed after step "
                                + step.number()
                                + " ("
                                + step
                                + ") left "
                                + check.state()
                                + ": "
                                + check.detail());
        assertEquals(
                expectedState(step),
                check.state(),
                () ->
                        "after step "
                                + step.number()
                                + " the cache should read as follows: "
                                + check.detail());
    }

    /*
     * HAND-WRITTEN, AND IT IS AN ASSERTION ABOUT THE ORDER OF THE STEPS.  Nothing is in the tool
     * cache until step 7 moves it there, so a crash before then leaves no directory at all; a crash
     * between the move and the marker leaves a directory with no marker, which is exactly the shape
     * R-TOOL-04's "written last" exists to make detectable.  A step added between the move and the
     * marker would make this mapping wrong and the test would say so.
     */
    private static InstallationState expectedState(InstallStep step) {
        if (step == InstallStep.RECORD_INSTALLATION_METADATA) {
            return InstallationState.INSTALLED;
        }
        if (step.number() >= InstallStep.MOVE_ATOMICALLY_INTO_CACHE.number()) {
            return InstallationState.NO_MARKER;
        }
        return InstallationState.NOT_PRESENT;
    }

    @ParameterizedTest
    @EnumSource(InstallStep.class)
    @DisplayName("whatever a crash left, the next install produces a complete, verified entry")
    void theNextInstallRecoversFromWhateverTheCrashLeft(InstallStep step)
            throws IOException, InterruptedException {
        Path root = Files.createDirectories(temporary.resolve("recover-" + step.number()));
        Path fixture = temporary.resolve("recover-fixture-" + step.number());
        CacheFixtures.writeSharedFixture(fixture);
        ArtefactRecord record = CacheFixtures.sharedRecord(fixture);
        ChildProcesses.runJava(
                InstallHaltChild.class,
                temporary,
                List.of(root.toString(), fixture.toString(), String.valueOf(step.number())),
                PATIENCE);

        InstallHarness harness = InstallHarness.at(root);
        CacheFixtures.serveSharedFixture(harness.fetcher(), record, fixture);
        Installation recovered = harness.install(record);

        assertTrue(
                harness.verify(record).installed(),
                () -> "the install after a crash at step " + step.number() + " must be complete");
        assertEquals(
                step == InstallStep.RECORD_INSTALLATION_METADATA,
                recovered.alreadyInstalled(),
                () ->
                        "a crash before the marker was written leaves nothing to reuse, and a crash"
                                + " after it leaves a complete entry; step "
                                + step.number()
                                + " disagreed");
        assertEquals(
                List.of(),
                harness.stagingDirectories(record),
                () ->
                        "the staging directory a crashed attempt left must be swept by the next"
                                + " attempt, which holds the same lock; after step "
                                + step.number()
                                + " it was not");
        assertFalse(
                Files.exists(harness.downloadsOf(record)),
                () -> "a completed install removes its downloads; step " + step.number());
    }
}
