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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.app.testing.ArtefactMirror;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.process.ProcessService;
import org.cometgui.tools.process.StartedProcess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Performance (a): application startup to the first usable window -- a fresh JVM running the
 * application's own {@code main} ({@link PerformanceStartupChild}), headless through the project's
 * Monocle recipe, timed from just before the process service starts it to the line it prints from
 * the JavaFX application thread when the shell's window is showing. JVM start, class loading, the
 * composition root and the first layout are all inside the number, as they are for a user.
 *
 * <p>The child's JVM options are the module's own headless recipe, read from {@code
 * cometgui-app/pom.xml}'s {@code cometgui.surefire.extraArgLine} rather than copied, and its
 * environment is the font stack surefire hands this JVM; its home and temporary directories are
 * fresh and empty, so nothing a previous sample left is reused. The checks prove each sample was
 * the real window: titled {@code CometGUI}, the shell as its root, 1280 by 800.
 *
 * <p>Opt-in (P15-5).
 */
@EnabledIfSystemProperty(
        named = PerfRecorder.ENABLE,
        matches = "true",
        disabledReason = PerfRecorder.DISABLED)
class PerformanceStartup {

    /** The measurement's identifier in the data. */
    static final String ID = "startup-first-window";

    /** How long one start may take before it is recorded as a failure. A bound, never a delay. */
    private static final long BOUND_SECONDS = 120;

    private static final Pattern EXTRA_ARG_LINE =
            Pattern.compile(
                    "<cometgui\\.surefire\\.extraArgLine>(.*?)"
                            + "</cometgui\\.surefire\\.extraArgLine>",
                    Pattern.DOTALL);

    /** What the child prints for the real window. */
    private static final String EXPECTED_WINDOW =
            "FIRST-WINDOW title=CometGUI root=shell-root size=1280x800";

    private Path work;

    @AfterEach
    void cleanUp() throws IOException {
        PerfRecorder.delete(work);
    }

    @Test
    @DisplayName("the application's main, in a fresh JVM, to its first usable window")
    void startToTheFirstWindow()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        OptionalInt repeats = PerfRecorder.repeats(ID);
        if (repeats.isEmpty()) {
            return;
        }
        work = PerfRecorder.work("startup");
        List<String> options = jvmOptions();
        String classPath = classPath();
        Map<String, String> environment = environment();
        ProcessService processes = new ProcessService(Clock.systemUTC());

        PerfRecorder.Measurement measurement = PerfRecorder.start(ID, "ms");
        measurement.evidence("jvmOptions", options);
        List<String> lines = new ArrayList<>();
        int wrong = 0;
        for (int sample = 0; sample < repeats.getAsInt(); sample++) {
            Path home = Files.createDirectories(work.resolve("home-" + sample));
            Path temporary = Files.createDirectories(work.resolve("tmp-" + sample));
            List<String> argv = new ArrayList<>();
            argv.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            argv.addAll(options);
            argv.add("-Duser.home=" + home);
            argv.add("-Djava.io.tmpdir=" + temporary);
            argv.add("-cp");
            argv.add(classPath);
            argv.add(PerformanceStartupChild.class.getName());
            CompletableFuture<String> window = new CompletableFuture<>();
            CompletableFuture<Integer> exited = new CompletableFuture<>();
            List<String> printed = new CopyOnWriteArrayList<>();
            long start = System.nanoTime();
            StartedProcess child =
                    processes.start(
                            new ToolCommand(argv, work.toAbsolutePath(), environment),
                            new ProcessListener() {
                                @Override
                                public void onStandardOutput(String line) {
                                    printed.add(line);
                                    if (line.startsWith("FIRST-WINDOW ")) {
                                        window.complete(line);
                                    }
                                }

                                @Override
                                public void onStandardError(String line) {
                                    printed.add("stderr: " + line);
                                }

                                @Override
                                public void onExit(int code) {
                                    exited.complete(code);
                                }
                            });
            String line;
            try {
                CompletableFuture.anyOf(window, exited).get(BOUND_SECONDS, TimeUnit.SECONDS);
            } finally {
                if (!window.isDone()) {
                    child.requestCancellation();
                }
            }
            double millis = PerfRecorder.millisSince(start);
            if (!window.isDone()) {
                throw new AssertionError(
                        "the application showed no window; the child printed " + printed);
            }
            line = window.get();
            measurement.sample(millis);
            lines.add(line);
            exited.get(BOUND_SECONDS, TimeUnit.SECONDS);
            if (!EXPECTED_WINDOW.equals(line)) {
                wrong++;
            }
        }
        measurement.evidence("windows", lines);
        measurement.checkEquals(
                "every sample's window is titled CometGUI with the shell as its root, 1280x800",
                0,
                wrong);
        measurement.finish();
    }

    /** The module's headless recipe, read from its POM, with the Monocle jar resolved. */
    private static List<String> jvmOptions() throws IOException {
        Path root = ArtefactMirror.repositoryRoot();
        String pom = Files.readString(root.resolve("cometgui-app/pom.xml"), StandardCharsets.UTF_8);
        Matcher matcher = EXTRA_ARG_LINE.matcher(pom);
        assertTrue(matcher.find(), "cometgui-app/pom.xml defines cometgui.surefire.extraArgLine");
        Path monocle = root.resolve("cometgui-app/target/monocle/openjfx-monocle.jar");
        assertTrue(Files.isRegularFile(monocle), "the Monocle jar Maven fetched: " + monocle);
        List<String> options = new ArrayList<>();
        for (String token : matcher.group(1).trim().split("\\s+")) {
            String resolved = token.replace("${cometgui.monocleJar}", monocle.toString());
            assertFalse(resolved.contains("${"), "an unresolved property in the recipe: " + token);
            options.add(resolved);
        }
        return options;
    }

    /** The test class path surefire built, which holds the application and every library. */
    private static String classPath() {
        String surefire = System.getProperty("surefire.test.class.path", "");
        String classPath =
                surefire.isBlank() ? System.getProperty("java.class.path", "") : surefire;
        boolean hasApplication = false;
        for (String entry : classPath.split(File.pathSeparator)) {
            if (entry.endsWith(
                    "cometgui-app" + File.separator + "target" + File.separator + "classes")) {
                hasApplication = true;
            }
        }
        assertTrue(hasApplication, "the class path holds cometgui-app's classes: " + classPath);
        return classPath;
    }

    /** The font stack surefire set for this JVM, which the child needs as much. */
    private static Map<String, String> environment() {
        Map<String, String> environment = new LinkedHashMap<>();
        for (String name :
                List.of("LD_LIBRARY_PATH", "FONTCONFIG_PATH", "XDG_DATA_HOME", "XDG_CACHE_HOME")) {
            String value = System.getenv(name);
            assertTrue(value != null && !value.isBlank(), "surefire sets " + name);
            environment.put(name, value);
        }
        environment.put("LANG", "C.UTF-8");
        return environment;
    }
}
