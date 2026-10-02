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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.fixtures.UpstreamMirror;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.value.EnzymeTable;
import org.cometgui.params.comet.value.EnzymeTableCodec;
import org.cometgui.params.comet.writer.CanonicalParamsWriter;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Java side agrees with the generated parameter reference ({@code R-DOC-04}, Phase 06 gate item
 * 8).
 *
 * <p>The documentation build renders {@code reference/comet_parameters_generated.rst} with {@code
 * scripts/cometparams.py}, a standard-library Python reader of the same metadata file {@link
 * MetadataLoader} reads (decision D6-1: Read the Docs has no JDK). Two readers of one file can
 * still disagree -- about which parameters exist, or about how a default is written -- so this test
 * runs the real generator, through the project's one process launcher ({@link ProcessService},
 * argument array, constructed environment), into a temporary directory, and checks its fragment
 * against the Java model:
 *
 * <ul>
 *   <li>every parameter {@link MetadataLoader#loadBundled()} models has exactly one entry, and
 *       nothing else has one;
 *   <li>every default line the page shows is byte-for-byte the line {@link CanonicalParamsWriter}
 *       writes for that parameter in the default model;
 *   <li>every default enzyme row the page shows is the writer's own row.
 * </ul>
 *
 * <p>Running the generator rather than reading {@code docs/_generated/} means the test never
 * depends on a documentation build having happened, or on a stale fragment from one. A missing
 * Python interpreter fails the test; it never skips.
 */
class GeneratedReferenceTest {

    /** How long the generator may take; it renders in well under a second. */
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private static final Pattern ENTRY =
            Pattern.compile("^\\.\\. _comet-param-([A-Za-z0-9_]+):$", Pattern.MULTILINE);

    private static final Pattern DECLARATION = Pattern.compile("^([A-Za-z0-9_]+) =");

    /** A default line sits in a literal block inside a field body: 3 + 3 spaces. */
    private static final String DEFAULT_LINE_INDENT = "      ";

    /** An enzyme row sits in a top-level literal block: 3 spaces. */
    private static final String ENZYME_ROW_INDENT = "   ";

    private static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    @TempDir private static Path out;

    private static String fragment;

    private static List<String> generatorOutput;

    @BeforeAll
    static void runTheGenerator() throws IOException, InterruptedException {
        Path root = UpstreamMirror.repositoryRoot();
        Path script = root.resolve("scripts").resolve("cometparams.py");
        assertTrue(Files.isRegularFile(script), () -> "no generator at " + script);
        List<String> argv =
                List.of(
                        interpreter(root).toString(),
                        "-B",
                        script.toString(),
                        "--root",
                        root.toString(),
                        "--out-dir",
                        out.toString());
        ToolCommand command =
                new ToolCommand(
                        argv, root, Map.of("PYTHONDONTWRITEBYTECODE", "1", "PYTHONUTF8", "1"));
        Collector collector = new Collector();
        RunningProcess process = new ProcessService(Clock.systemUTC()).start(command, collector);
        if (!collector.exited.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            process.requestCancellation();
            throw new AssertionError(command.displayString() + " did not finish within " + TIMEOUT);
        }
        generatorOutput = collector.lines;
        assertEquals(
                0,
                collector.exitCode.get(),
                () -> command.displayString() + " failed: " + collector.lines);
        Path written = out.resolve("comet-parameters.rsti");
        assertTrue(
                Files.isRegularFile(written),
                () -> "the generator exited 0 and wrote no " + written + ": " + collector.lines);
        fragment = Files.readString(written, StandardCharsets.UTF_8);
    }

    /**
     * The interpreter the documentation build uses -- the project virtualenv's -- or else {@code
     * python3} on the {@code PATH}. The generator needs nothing but the standard library.
     */
    private static Path interpreter(Path root) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(root.resolve(".venv/bin/python3"));
        candidates.add(root.resolve(".venv/bin/python"));
        candidates.add(root.resolve(".venv/Scripts/python.exe"));
        String path = System.getenv("PATH");
        if (path != null) {
            for (String directory : path.split(Pattern.quote(File.pathSeparator))) {
                if (!directory.isBlank()) {
                    candidates.add(Path.of(directory, "python3"));
                    candidates.add(Path.of(directory, "python3.exe"));
                }
            }
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate.toAbsolutePath();
            }
        }
        throw new AssertionError(
                "no Python interpreter: looked for the project virtualenv under "
                        + root.resolve(".venv")
                        + " and for python3 on the PATH. The generated parameter reference"
                        + " cannot be checked without one, and an unchecked reference is not a"
                        + " pass.");
    }

    @Test
    @DisplayName("every parameter MetadataLoader models has exactly one entry, and nothing else")
    void oneEntryPerModelledParameter() {
        List<String> entries = new ArrayList<>();
        Matcher matcher = ENTRY.matcher(fragment);
        while (matcher.find()) {
            entries.add(matcher.group(1));
        }
        List<String> modelled =
                METADATA.parameters().stream().map(ParameterDefinition::name).toList();
        assertEquals(118, modelled.size(), "the metadata models Comet 2026.02.2's 118");
        List<String> sortedEntries = new ArrayList<>(entries);
        Collections.sort(sortedEntries);
        List<String> sortedModelled = new ArrayList<>(modelled);
        Collections.sort(sortedModelled);
        assertEquals(sortedModelled, sortedEntries, "entries in the generated reference");
        assertTrue(
                generatorOutput.stream()
                        .anyMatch(
                                line ->
                                        line.contains(
                                                "118 parameter entries for 118 modelled"
                                                        + " parameters")),
                () -> "the generator's count line: " + generatorOutput);
    }

    @Test
    @DisplayName("every default line on the page is the canonical writer's line for the default")
    void defaultLinesAreTheCanonicalWritersLines() {
        CometParameters defaults =
                CometParameters.defaults(METADATA, ParamsFiles.COMET, defaultEnzymeTable());
        String canonical = new CanonicalParamsWriter(ParamsFiles.build()).write(defaults);
        Map<String, String> lines = new LinkedHashMap<>();
        for (String line : canonical.split("\n", -1)) {
            Matcher declaration = DECLARATION.matcher(line);
            if (declaration.find()) {
                lines.put(declaration.group(1), line);
            }
        }
        assertEquals(
                METADATA.parametersFor(ParamsFiles.COMET).stream()
                        .map(ParameterDefinition::name)
                        .toList(),
                List.copyOf(lines.keySet()),
                "the canonical writer declares every modelled parameter once, in order");
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, String> entry : lines.entrySet()) {
            int found = occurrences(fragment, "\n" + DEFAULT_LINE_INDENT + entry.getValue() + "\n");
            if (found != 1) {
                wrong.add(entry.getKey() + " (" + found + "x): " + entry.getValue());
            }
        }
        assertTrue(
                wrong.isEmpty(),
                () ->
                        wrong.size()
                                + " of "
                                + lines.size()
                                + " canonical default lines are not on the page exactly once;"
                                + " the first: "
                                + wrong.subList(0, Math.min(3, wrong.size())));
    }

    @Test
    @DisplayName("every default enzyme row on the page is the canonical writer's row")
    void enzymeRowsAreTheCanonicalWritersRows() {
        List<String> rows = EnzymeTableCodec.format(defaultEnzymeTable());
        assertEquals(12, rows.size(), "Comet 2026.02.2 writes twelve default rows");
        for (String row : rows) {
            assertEquals(
                    1,
                    occurrences(fragment, "\n" + ENZYME_ROW_INDENT + row + "\n"),
                    () -> "the enzyme row " + row);
        }
    }

    private static EnzymeTable defaultEnzymeTable() {
        return new CometParamsParser(METADATA, ParamsFiles.COMET)
                .parse(ParamsFiles.complete())
                .model()
                .orElseThrow()
                .enzymeTable();
    }

    private static int occurrences(String text, String wanted) {
        int count = 0;
        int from = text.indexOf(wanted);
        while (from >= 0) {
            count++;
            from = text.indexOf(wanted, from + 1);
        }
        return count;
    }

    /** Collects both streams and the exit code, on the process service's threads. */
    private static final class Collector implements ProcessListener {

        private final List<String> lines = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger exitCode = new AtomicInteger(Integer.MIN_VALUE);
        private final CountDownLatch exited = new CountDownLatch(1);

        @Override
        public void onStandardOutput(String line) {
            lines.add("[stdout] " + line);
        }

        @Override
        public void onStandardError(String line) {
            lines.add("[stderr] " + line);
        }

        @Override
        public void onExit(int code) {
            exitCode.set(code);
            exited.countDown();
        }
    }
}
