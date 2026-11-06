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

package org.cometgui.app.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * What the performance suite's opt-in classes share (Phase 15 unit 8, PHASE-15 exit gate item 4;
 * specification, <em>Performance and resource tests</em>): the opt-in, the plan the runner hands
 * in, a work directory for the large files the suite generates, and the one record each measurement
 * leaves for {@code scripts/perf/performance.py} to grade.
 *
 * <h2>Opt-in, never part of a plain build (P15-5)</h2>
 *
 * <p>The classes are named {@code Performance*} rather than {@code *Test}, so surefire's default
 * includes never select them, and each is disabled unless {@value #ENABLE}{@code =true}. They are
 * run by {@code scripts/perf/run-performance.sh}, which names them with {@code -Dtest}.
 *
 * <h2>The plan is the runner's; nothing is graded here</h2>
 *
 * <p>Which measurements run, how many times, and the sizes of what is generated are written by
 * {@code performance.py} into a tab-separated plan file ({@value #PLAN}): {@code measure <id>
 * <repeats>} and {@code param <name> <value>} lines. A class measures exactly the planned ids and
 * writes one {@code <report>/measurements/<id>.json} per id: every sample, the evidence, and named
 * checks proving the measured work was the real work (counts, digests, the window's title). The
 * thresholds, the runner profile and the verdict are {@code performance.py}'s; a class fails only
 * when a check fails, so a slow product is graded, never hidden by a test that did not finish.
 */
public final class PerfRecorder {

    /** The system property that opts in. */
    public static final String ENABLE = "cometgui.perf";

    /** The reason a class is skipped without the opt-in. */
    public static final String DISABLED =
            "performance suite (Phase 15 unit 8): opt-in, run by scripts/perf/run-performance.sh"
                    + " with -Dcometgui.perf=true; it generates multi-gigabyte files";

    /** The system property naming the report directory. */
    public static final String REPORT = "cometgui.perf.report";

    /** The system property naming the plan file. */
    public static final String PLAN = "cometgui.perf.plan";

    /** The system property naming the work directory for generated files. */
    public static final String WORK = "cometgui.perf.work";

    private PerfRecorder() {}

    /**
     * How many times a measurement is planned, or empty when it is not planned in this run.
     *
     * @param id the measurement's identifier in {@code scripts/perf/performance-data.json}
     * @return its repeats
     * @throws IOException if the plan cannot be read
     */
    public static OptionalInt repeats(String id) throws IOException {
        for (String[] line : plan()) {
            if (line[0].equals("measure") && line[1].equals(id)) {
                int repeats = Integer.parseInt(line[2]);
                assertTrue(repeats > 0, "a planned measurement repeats at least once: " + id);
                return OptionalInt.of(repeats);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Whether any of the given measurements is planned.
     *
     * @param ids identifiers
     * @return {@code true} if at least one is planned
     * @throws IOException if the plan cannot be read
     */
    public static boolean anyPlanned(String... ids) throws IOException {
        for (String id : ids) {
            if (repeats(id).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * A parameter the plan sets, which must be present.
     *
     * @param name its name
     * @return its value
     * @throws IOException if the plan cannot be read
     */
    public static String parameter(String name) throws IOException {
        for (String[] line : plan()) {
            if (line[0].equals("param") && line[1].equals(name)) {
                return line[2];
            }
        }
        throw new AssertionError("the performance plan sets no parameter " + name);
    }

    /**
     * A numeric parameter the plan sets.
     *
     * @param name its name
     * @return its value
     * @throws IOException if the plan cannot be read
     */
    public static long longParameter(String name) throws IOException {
        return Long.parseLong(parameter(name));
    }

    private static List<String[]> plan() throws IOException {
        String file = System.getProperty(PLAN, "");
        assertFalse(
                file.isBlank(),
                "the performance suite needs -D"
                        + PLAN
                        + "=FILE (scripts/perf/run-performance.sh)");
        List<String[]> lines = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(file), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            assertEquals(3, fields.length, () -> "a plan line has three fields: " + line);
            lines.add(fields);
        }
        return lines;
    }

    /**
     * A fresh, empty directory under the work directory the runner named, for one class's generated
     * files. The class deletes it when it is done ({@link #delete}).
     *
     * @param name the directory's name
     * @return the directory
     * @throws IOException if it exists already or cannot be created
     */
    public static Path work(String name) throws IOException {
        String work = System.getProperty(WORK, "");
        assertFalse(work.isBlank(), "the performance suite needs -D" + WORK + "=DIR");
        Path directory = Path.of(work).toAbsolutePath().resolve(name);
        assertFalse(Files.exists(directory), directory + " exists: every class starts empty");
        return Files.createDirectories(directory);
    }

    /**
     * Deletes a directory tree, if it exists.
     *
     * @param directory the directory
     * @throws IOException if it cannot be deleted
     */
    public static void delete(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walked = Files.walk(directory)) {
            for (Path path : walked.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * Forces every regular file under a directory to the storage device, so that what a class wrote
     * while setting up is not written back while it measures.
     *
     * @param directory the directory
     * @return the bytes of the files forced
     * @throws IOException if a file cannot be forced
     */
    public static long syncTree(Path directory) throws IOException {
        long bytes = 0;
        try (Stream<Path> walked = Files.walk(directory)) {
            for (Path file : walked.filter(Files::isRegularFile).toList()) {
                try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                    channel.force(true);
                    bytes += channel.size();
                }
            }
        }
        return bytes;
    }

    /**
     * Waits until a condition holds, polling every {@code pollMicros} microseconds, for at most
     * {@code boundMillis}. A bound, never a delay: it returns as soon as the condition holds.
     *
     * @param condition the condition
     * @param pollMicros the polling interval
     * @param boundMillis the most it waits
     * @return {@code true} if the condition held within the bound
     */
    public static boolean awaitCondition(
            BooleanSupplier condition, long pollMicros, long boundMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(boundMillis);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() - deadline > 0) {
                return condition.getAsBoolean();
            }
            LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(pollMicros));
        }
        return true;
    }

    /**
     * The used heap after three forced collections, through the management bean rather than {@code
     * System.gc()}.
     *
     * @return bytes in use
     */
    public static long usedHeapAfterCollection() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        for (int round = 0; round < 3; round++) {
            memory.gc();
        }
        return memory.getHeapMemoryUsage().getUsed();
    }

    /**
     * Milliseconds since a {@link System#nanoTime()} reading, as a fraction.
     *
     * @param startNanos the reading
     * @return the elapsed milliseconds
     */
    public static double millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    /**
     * Starts the record of one measurement.
     *
     * @param id its identifier in the data
     * @param unit its unit, as the data states it
     * @return the record
     */
    public static Measurement start(String id, String unit) {
        return new Measurement(id, unit);
    }

    /** One measurement's samples, evidence and checks, written once when it is finished. */
    public static final class Measurement {

        private final String id;

        private final String unit;

        private final List<Double> samples = new ArrayList<>();

        private final Map<String, Object> evidence = new LinkedHashMap<>();

        private final List<Map<String, Object>> checks = new ArrayList<>();

        private final List<String> failures = new ArrayList<>();

        private Measurement(String id, String unit) {
            this.id = id;
            this.unit = unit;
        }

        /**
         * Adds one sample.
         *
         * @param value the sample, in the measurement's unit
         * @return this record
         */
        public Measurement sample(double value) {
            samples.add(value);
            return this;
        }

        /**
         * The samples so far.
         *
         * @return a copy
         */
        public List<Double> samples() {
            return List.copyOf(samples);
        }

        /**
         * Records a piece of evidence.
         *
         * @param name its name
         * @param value a string, number, boolean, list or map of those
         * @return this record
         */
        public Measurement evidence(String name, Object value) {
            evidence.put(name, value);
            return this;
        }

        /**
         * Records a named check of the measured work's result.
         *
         * @param name what was checked
         * @param passed whether it held
         * @param detail the values compared
         * @return this record
         */
        public Measurement check(String name, boolean passed, String detail) {
            Map<String, Object> check = new LinkedHashMap<>();
            check.put("name", name);
            check.put("passed", passed);
            check.put("detail", detail);
            checks.add(check);
            if (!passed) {
                failures.add(name + ": " + detail);
            }
            return this;
        }

        /**
         * Records a check that two values are equal.
         *
         * @param name what was checked
         * @param expected the expected value
         * @param observed the observed value
         * @return this record
         */
        public Measurement checkEquals(String name, Object expected, Object observed) {
            boolean equal = String.valueOf(expected).equals(String.valueOf(observed));
            return check(
                    name,
                    equal,
                    "expected <" + expected + ">" + (equal ? "" : " but was <" + observed + ">"));
        }

        /**
         * Writes {@code <report>/measurements/<id>.json}, then fails if any check failed or no
         * sample was taken -- after writing, so the grader reads what failed.
         *
         * @throws IOException if it cannot be written
         */
        public void finish() throws IOException {
            if (samples.isEmpty()) {
                failures.add("no sample was taken");
            }
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("id", id);
            document.put("unit", unit);
            document.put("samples", List.copyOf(samples));
            document.put("evidence", evidence);
            document.put("checks", checks);
            document.put("failures", List.copyOf(failures));
            String report = System.getProperty(REPORT, "");
            assertFalse(report.isBlank(), "the performance suite needs -D" + REPORT + "=DIR");
            Path directory =
                    Files.createDirectories(
                            Path.of(report).toAbsolutePath().resolve("measurements"));
            Path file = directory.resolve(id + ".json");
            assertFalse(Files.exists(file), file + " written twice");
            Files.writeString(file, json(document) + "\n", StandardCharsets.UTF_8);
            System.out.println(
                    "performance: "
                            + id
                            + " "
                            + unit
                            + " "
                            + samples
                            + (failures.isEmpty() ? "" : " FAILED CHECKS " + failures));
            assertEquals(List.of(), failures, id + ": the measured work's checks");
        }
    }

    /**
     * A minimal JSON writer: strings, numbers, booleans, null, lists and maps.
     *
     * @param value the value
     * @return its JSON text
     */
    public static String json(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value, "");
        return out.toString();
    }

    private static void write(StringBuilder out, Object value, String indent) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            string(out, text);
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            out.append(value);
        } else if (value instanceof Double number) {
            out.append(
                    number.isNaN() || number.isInfinite()
                            ? "null"
                            : String.format(Locale.ROOT, "%.3f", number));
        } else if (value instanceof Number number) {
            out.append(String.format(Locale.ROOT, "%s", number));
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append("{\n");
            String inner = indent + "  ";
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.append(inner);
                string(out, String.valueOf(entry.getKey()));
                out.append(": ");
                write(out, entry.getValue(), inner);
                out.append(++index < map.size() ? ",\n" : "\n");
            }
            out.append(indent).append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                out.append("[]");
                return;
            }
            out.append("[\n");
            String inner = indent + "  ";
            for (int index = 0; index < list.size(); index++) {
                out.append(inner);
                write(out, list.get(index), inner);
                out.append(index + 1 < list.size() ? ",\n" : "\n");
            }
            out.append(indent).append(']');
        } else {
            string(out, String.valueOf(value));
        }
    }

    private static void string(StringBuilder out, String text) {
        out.append('"');
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
