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

package org.cometgui.workflow.engine;

import static org.cometgui.workflow.engine.EngineAssertions.awaitResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.cometgui.provenance.manifest.ManifestReader;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.manifest.ToolRecord;
import org.cometgui.workflow.state.EngineStep;
import org.cometgui.workflow.state.StepState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Bounded concurrency across one step's invocations (P8-15, {@code R-CMT-05}), observed from the
 * fakes' own start and end records rather than from anything the engine says about itself.
 */
class InvocationConcurrencyTest {

    @TempDir private Path tmp;

    @Test
    void fiveInvocationsWithABoundOfTwoOverlapExactlyTwoAndAreRecordedInListOrder()
            throws IOException,
                    InterruptedException,
                    ReuseRefusedException,
                    StepFailedException,
                    ExecutionException,
                    TimeoutException {
        try (EngineFixture fixture = EngineFixture.create(tmp, 5)) {
            // Four cores, two threads per invocation: a bound of two (the cap of 8 does not bind).
            assertEquals(2, ConcurrencyBound.of(5, EngineFixture.CORES, 2, EngineFixture.CAP));
            Path records = Files.createDirectories(tmp.resolve("concurrency"));
            // Each hold waits until three holds run at once -- which a bound of two never allows --
            // or its time is up. The first holds longest, so the second finishes before it.
            long[] holdMillis = {1500L, 300L, 300L, 300L, 300L};
            List<List<String>> argvs = new ArrayList<>();
            List<Invocation> invocations = new ArrayList<>();
            for (int position = 1; position <= 5; position++) {
                Invocation invocation =
                        fixture.fake(
                                EngineFixture.stageId(position),
                                "hold",
                                records.toString(),
                                "h" + position,
                                "3",
                                Long.toString(holdMillis[position - 1]),
                                fixture.pepXml(position).toString(),
                                fixture.pin(position).toString());
                invocations.add(invocation);
                argvs.add(invocation.command().argv());
            }
            Map<EngineStep, StepAction> actions = new EnumMap<>(fixture.standardActions());
            actions.put(
                    EngineStep.RUN_COMET,
                    FakeStep.doing(
                            fixture.cometDeclaration(),
                            context -> {
                                List<InvocationResult> results = context.invokeAll(invocations, 2);
                                List<String> order = new ArrayList<>();
                                for (InvocationResult result : results) {
                                    order.add(result.stageId());
                                }
                                context.addDetail("returned", String.join(" ", order));
                            }));
            RunResult result =
                    awaitResult(
                            fixture.engine()
                                    .start(fixture.request(actions), new RecordingListener()));
            assertEquals(
                    StepState.SUCCEEDED,
                    result.states().get(EngineStep.RUN_COMET),
                    result.failures().toString());

            List<Interval> intervals = new ArrayList<>();
            for (int position = 1; position <= 5; position++) {
                intervals.add(
                        new Interval(
                                "h" + position,
                                instant(records.resolve("h" + position + ".start")),
                                instant(records.resolve("h" + position + ".end"))));
            }
            assertEquals(2, maximumOverlap(intervals), intervals::toString);

            List<String> completion = new ArrayList<>();
            intervals.stream()
                    .sorted(Comparator.comparing(Interval::end))
                    .forEach(interval -> completion.add(interval.name()));
            assertNotEquals(
                    List.of("h1", "h2", "h3", "h4", "h5"),
                    completion,
                    "the test needs a completion order that differs from the input order");
            assertTrue(
                    completion.indexOf("h2") < completion.indexOf("h1"),
                    () -> "h2 should have finished before h1: " + completion);

            // provenance.json, read back: list order, and each invocation's own argument array.
            ProvenanceManifest manifest =
                    ManifestReader.readFrom(fixture.layout().provenanceJsonFile());
            List<Optional<String>> stageIds = new ArrayList<>();
            List<List<String>> recordedArgv = new ArrayList<>();
            for (ToolRecord tool : manifest.tools()) {
                stageIds.add(tool.stageId());
                recordedArgv.add(tool.execution().command().argv());
            }
            assertEquals(
                    List.of(
                            Optional.of("comet-01"),
                            Optional.of("comet-02"),
                            Optional.of("comet-03"),
                            Optional.of("comet-04"),
                            Optional.of("comet-05")),
                    stageIds);
            assertEquals(argvs, recordedArgv);
            assertEquals(5, recordedArgv.stream().distinct().count(), "every argv is distinct");
            assertEquals(
                    List.of(
                            "hold",
                            records.toString(),
                            "h3",
                            "3",
                            "300",
                            fixture.pepXml(3).toString(),
                            fixture.pin(3).toString()),
                    recordedArgv.get(2).subList(4, 11),
                    "the third invocation's own arguments, element by element");
        }
    }

    @Test
    void theOverlapMeasureCanSeeMoreThanTwo() {
        Instant t = Instant.parse("2026-10-06T00:00:00Z");
        List<Interval> three =
                List.of(
                        new Interval("a", t, t.plusMillis(30)),
                        new Interval("b", t.plusMillis(10), t.plusMillis(40)),
                        new Interval("c", t.plusMillis(20), t.plusMillis(50)),
                        new Interval("d", t.plusMillis(50), t.plusMillis(60)));
        assertEquals(3, maximumOverlap(three));
        List<Interval> touching =
                List.of(
                        new Interval("a", t, t.plusMillis(10)),
                        new Interval("b", t.plusMillis(10), t.plusMillis(20)));
        assertEquals(1, maximumOverlap(touching));
    }

    private static Instant instant(Path record) throws java.io.IOException {
        return Instant.parse(Files.readString(record, StandardCharsets.UTF_8).trim());
    }

    /**
     * The most intervals open at once; an interval ending as another starts does not overlap it.
     */
    static int maximumOverlap(List<Interval> intervals) {
        List<long[]> points = new ArrayList<>();
        for (Interval interval : intervals) {
            points.add(new long[] {nanos(interval.start()), 1});
            points.add(new long[] {nanos(interval.end()), -1});
        }
        points.sort(Comparator.<long[]>comparingLong(p -> p[0]).thenComparingLong(p -> p[1]));
        int open = 0;
        int most = 0;
        for (long[] point : points) {
            open += (int) point[1];
            most = Math.max(most, open);
        }
        return most;
    }

    private static long nanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }

    record Interval(String name, Instant start, Instant end) {}
}
