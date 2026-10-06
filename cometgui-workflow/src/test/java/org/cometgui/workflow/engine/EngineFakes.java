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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * Compiles {@code enginefakes.EngineFake} once per JVM and says how to launch it.
 *
 * <p>The fake's source is a test <em>resource</em> of this module, so Maven copies it rather than
 * compiling it; this class compiles it in process with {@code javax.tools} into {@code
 * target/engine-fakes/classes}. It never launches anything: every test launches the fake through
 * the process service, which is the point of the tests.
 */
final class EngineFakes {

    private static final String SOURCE_RESOURCE = "/enginefakes/EngineFake.java";

    private static final String MAIN_CLASS = "enginefakes.EngineFake";

    private EngineFakes() {}

    /**
     * The compiled class file -- the "tool" a fake invocation runs, whose digests provenance
     * records.
     *
     * @return {@code target/engine-fakes/classes/enginefakes/EngineFake.class}
     */
    static Path classFile() {
        return Compiled.DIRECTORY.resolve("enginefakes").resolve("EngineFake.class");
    }

    /**
     * The argument array running one scenario.
     *
     * @param scenario the scenario
     * @param args its arguments
     * @return {@code [java, -cp, <classes>, enginefakes.EngineFake, scenario, args...]}
     */
    static List<String> argv(String scenario, String... args) {
        List<String> argv = new ArrayList<>();
        argv.add(javaExecutable().toString());
        argv.add("-cp");
        argv.add(Compiled.DIRECTORY.toString());
        argv.add(MAIN_CLASS);
        argv.add(scenario);
        argv.addAll(List.of(args));
        return List.copyOf(argv);
    }

    static Path javaExecutable() {
        Optional<String> reported = ProcessHandle.current().info().command();
        Path candidate =
                reported.map(Path::of)
                        .filter(Files::isExecutable)
                        .orElseGet(
                                () ->
                                        Path.of(
                                                System.getProperty("java.home"),
                                                "bin",
                                                System.getProperty("os.name", "")
                                                                .toLowerCase(Locale.ROOT)
                                                                .contains("win")
                                                        ? "java.exe"
                                                        : "java"));
        if (!Files.isExecutable(candidate)) {
            throw new IllegalStateException("no executable java binary at " + candidate);
        }
        return candidate;
    }

    private static final class Compiled {

        private static final Path DIRECTORY = compile();

        private Compiled() {}
    }

    private static Path compile() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("these tests compile the fake and need a JDK");
        }
        Path output =
                Path.of(System.getProperty("cometgui.buildDirectory", "target"))
                        .toAbsolutePath()
                        .resolve("engine-fakes")
                        .resolve("classes");
        Path classFile = output.resolve("enginefakes").resolve("EngineFake.class");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean compiled;
        try (StandardJavaFileManager files =
                compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Files.createDirectories(output);
            Files.deleteIfExists(classFile);
            files.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(output));
            compiled =
                    Boolean.TRUE.equals(
                            compiler.getTask(
                                            null,
                                            files,
                                            diagnostics,
                                            List.of("-proc:none"),
                                            null,
                                            List.of(new Source(readSource())))
                                    .call());
        } catch (IOException failure) {
            throw new UncheckedIOException("could not compile " + SOURCE_RESOURCE, failure);
        }
        long size;
        try {
            size = Files.isRegularFile(classFile) ? Files.size(classFile) : -1L;
        } catch (IOException unreadable) {
            size = -1L;
        }
        if (!compiled || size <= 0) {
            throw new IllegalStateException(
                    "compiling "
                            + SOURCE_RESOURCE
                            + " produced no usable "
                            + classFile
                            + ": "
                            + diagnostics.getDiagnostics());
        }
        return output;
    }

    private static String readSource() {
        try (InputStream stream = EngineFakes.class.getResourceAsStream(SOURCE_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException(SOURCE_RESOURCE + " is not on the class path");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("could not read " + SOURCE_RESOURCE, failure);
        }
    }

    private static final class Source extends SimpleJavaFileObject {

        private final String content;

        private Source(String content) {
            super(URI.create("string:///enginefakes/EngineFake.java"), Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }
}
