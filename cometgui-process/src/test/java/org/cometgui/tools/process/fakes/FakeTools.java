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

package org.cometgui.tools.process.fakes;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.cometgui.domain.ports.ToolCommand;

/**
 * Compiles {@code fakes.FakeTool} and hands out the command that launches it.
 *
 * <p>The fake's source is a test <em>resource</em>, {@code /fakes/FakeTool.java}, so Maven copies
 * it rather than compiling it. This class compiles it in process with {@code javax.tools}, exactly
 * once per JVM, into {@code target/fake-tools/classes}, and reads the source from the class path
 * rather than from a relative file path so that nothing here depends on the working directory.
 *
 * <p><strong>This class never constructs a {@code ProcessBuilder}.</strong> Launching is the
 * process service's job and every test that exercises a scenario launches through it; this class
 * only says what to launch. The one exception in the whole phase is {@code FakeToolSelfTest}, which
 * has to prove the fakes behave as documented without the code under test in the way.
 */
public final class FakeTools {

    /** The fake's source, on the test class path because Maven copies test resources there. */
    private static final String SOURCE_RESOURCE = "/fakes/FakeTool.java";

    /** The fake's main class, as {@code java -cp ...} wants it. */
    private static final String MAIN_CLASS = "fakes.FakeTool";

    /** The compiled class, relative to the output directory. */
    private static final String PACKAGE_DIRECTORY = "fakes";

    /** The compiled class file name. */
    private static final String CLASS_FILE = "FakeTool.class";

    private FakeTools() {
        throw new AssertionError("FakeTools is a helper, not a type to instantiate");
    }

    /**
     * The directory the fake was compiled into, compiling it on the first call in this JVM.
     *
     * <p>The compilation happens once per JVM and is safe under parallel test execution: the work
     * is done in the static initialiser of a holder class, so the JVM's own class-initialisation
     * lock serialises it and every later caller sees the finished result.
     *
     * @return the class output directory, containing {@code fakes/FakeTool.class}
     * @throws IllegalStateException if there is no system Java compiler, if the source resource is
     *     missing, or if compilation produces no non-empty class file -- the message names the path
     *     it looked for and quotes every compiler diagnostic
     */
    public static Path classesDirectory() {
        return CompiledFake.DIRECTORY;
    }

    /**
     * The {@code java} binary of the JVM running these tests.
     *
     * <p>Taken from {@code ProcessHandle.current().info().command()}, which is the actual
     * executable this JVM was started from, and falling back to {@code java.home/bin/java} when the
     * platform will not report it.
     *
     * @return an existing, executable {@code java} binary
     * @throws IllegalStateException if neither candidate is an executable file
     */
    public static Path javaExecutable() {
        Optional<String> reported = ProcessHandle.current().info().command();
        Path candidate =
                reported.map(Path::of).filter(Files::isExecutable).orElseGet(FakeTools::javaHome);
        if (!Files.isExecutable(candidate)) {
            throw new IllegalStateException(
                    "no executable java binary: ProcessHandle reported "
                            + reported.orElse("nothing")
                            + " and java.home gives "
                            + javaHome()
                            + "; neither is an executable file");
        }
        return candidate;
    }

    /**
     * The argument array that runs one scenario of the fake.
     *
     * @param scenario the scenario name, as documented on {@code fakes.FakeTool}
     * @param args that scenario's arguments, in order
     * @return {@code [java, -cp, <classes>, fakes.FakeTool, <scenario>, <args...>]}, immutable
     */
    public static List<String> argv(String scenario, String... args) {
        return argv(classesDirectory(), scenario, args);
    }

    /**
     * The argument array that runs one scenario of the fake from a chosen class path.
     *
     * <p>The overload exists for {@link #classesDirectoryCopiedTo(Path)}: a test that must prove
     * the service copes with a class path containing spaces or non-ASCII characters copies the
     * compiled classes somewhere awkward and launches them from there.
     *
     * @param classes the directory holding {@code fakes/FakeTool.class}
     * @param scenario the scenario name, as documented on {@code fakes.FakeTool}
     * @param args that scenario's arguments, in order
     * @return {@code [java, -cp, <classes>, fakes.FakeTool, <scenario>, <args...>]}, immutable
     */
    public static List<String> argv(Path classes, String scenario, String... args) {
        List<String> argv = new ArrayList<>(5 + args.length);
        argv.add(javaExecutable().toString());
        argv.add("-cp");
        argv.add(classes.toString());
        argv.add(MAIN_CLASS);
        argv.add(scenario);
        argv.addAll(List.of(args));
        return List.copyOf(argv);
    }

    /**
     * A {@link ToolCommand} running one scenario with an empty environment.
     *
     * @param workingDirectory the absolute directory to run in
     * @param scenario the scenario name, as documented on {@code fakes.FakeTool}
     * @param args that scenario's arguments, in order
     * @return the validated command
     */
    public static ToolCommand command(Path workingDirectory, String scenario, String... args) {
        return command(workingDirectory, Map.of(), scenario, args);
    }

    /**
     * A {@link ToolCommand} running one scenario with an explicit environment.
     *
     * @param workingDirectory the absolute directory to run in
     * @param environment the environment variables to set, which may be empty
     * @param scenario the scenario name, as documented on {@code fakes.FakeTool}
     * @param args that scenario's arguments, in order
     * @return the validated command
     */
    public static ToolCommand command(
            Path workingDirectory,
            Map<String, String> environment,
            String scenario,
            String... args) {
        return new ToolCommand(argv(scenario, args), workingDirectory, environment);
    }

    /**
     * Copies the compiled fake to another directory, creating it if it does not exist.
     *
     * @param destination the directory to copy {@code fakes/FakeTool.class} into
     * @return {@code destination}
     * @throws UncheckedIOException if the copy fails
     */
    public static Path classesDirectoryCopiedTo(Path destination) {
        Path source = classesDirectory();
        try {
            Files.createDirectories(destination);
            Files.walkFileTree(source, new CopyTree(source, destination));
        } catch (IOException failure) {
            throw new UncheckedIOException(
                    "could not copy " + source + " to " + destination, failure);
        }
        return destination;
    }

    // -------------------------------------------------------------------- internals --

    /**
     * Holds the compiled directory. The class-initialisation lock is what makes the compilation
     * happen exactly once per JVM even when JUnit runs test classes in parallel.
     */
    private static final class CompiledFake {

        private static final Path DIRECTORY = compile();

        private CompiledFake() {
            throw new AssertionError("holder");
        }
    }

    private static Path compile() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException(
                    "ToolProvider.getSystemJavaCompiler() returned null: these tests compile "
                            + SOURCE_RESOURCE
                            + " in process and therefore need a JDK, not a JRE. Run them on the"
                            + " project toolchain (. tools/env.sh).");
        }
        Path output = buildDirectory().resolve("fake-tools").resolve("classes");
        Path classFile = output.resolve(PACKAGE_DIRECTORY).resolve(CLASS_FILE);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean reportedSuccess;
        try (StandardJavaFileManager files =
                compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Files.createDirectories(output);
            Files.deleteIfExists(classFile);
            files.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(output));
            reportedSuccess =
                    Boolean.TRUE.equals(
                            compiler.getTask(
                                            null,
                                            files,
                                            diagnostics,
                                            List.of("-proc:none"),
                                            null,
                                            List.of(new SourceOnTheClassPath(readSource())))
                                    .call());
        } catch (IOException failure) {
            throw new UncheckedIOException("could not compile " + SOURCE_RESOURCE, failure);
        }
        /*
         * Exit code 0 proves nothing, and neither does javac reporting success: check that the
         * class file this whole helper exists to produce is really on disk and is not empty.
         */
        long size = sizeOrMinusOne(classFile);
        if (!reportedSuccess || size <= 0) {
            throw new IllegalStateException(
                    "compiling "
                            + SOURCE_RESOURCE
                            + " did not produce a usable class file: "
                            + classFile
                            + " (javac reported "
                            + (reportedSuccess ? "success" : "failure")
                            + ", the file is "
                            + (size < 0 ? "absent" : size + " bytes")
                            + ")"
                            + describe(diagnostics));
        }
        return output;
    }

    private static long sizeOrMinusOne(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.size(file) : -1L;
        } catch (IOException unreadable) {
            return -1L;
        }
    }

    private static String describe(DiagnosticCollector<JavaFileObject> diagnostics) {
        List<Diagnostic<? extends JavaFileObject>> reported = diagnostics.getDiagnostics();
        if (reported.isEmpty()) {
            return ". The compiler reported no diagnostics at all.";
        }
        StringBuilder rendered = new StringBuilder(". Compiler diagnostics:");
        for (Diagnostic<? extends JavaFileObject> diagnostic : reported) {
            rendered.append(System.lineSeparator())
                    .append("  ")
                    .append(diagnostic.getKind())
                    .append(" at line ")
                    .append(diagnostic.getLineNumber())
                    .append(": ")
                    .append(diagnostic.getMessage(Locale.ROOT));
        }
        return rendered.toString();
    }

    private static String readSource() {
        try (InputStream stream = FakeTools.class.getResourceAsStream(SOURCE_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException(
                        SOURCE_RESOURCE
                                + " is not on the test class path. Maven copies"
                                + " src/test/resources to target/test-classes, so this means the"
                                + " fake was renamed or the module was not built.");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("could not read " + SOURCE_RESOURCE, failure);
        }
    }

    /**
     * The module's build directory. Surefire passes it in, because a test that hard-codes {@code
     * "target"} breaks the moment a module moves its build output.
     */
    private static Path buildDirectory() {
        return Path.of(System.getProperty("cometgui.buildDirectory", "target")).toAbsolutePath();
    }

    private static Path javaHome() {
        String suffix =
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                        ? ".exe"
                        : "";
        return Path.of(System.getProperty("java.home"), "bin", "java" + suffix);
    }

    /** The fake's source handed to javac from memory, so nothing writes it back out to disk. */
    private static final class SourceOnTheClassPath extends SimpleJavaFileObject {

        private final String content;

        private SourceOnTheClassPath(String content) {
            super(URI.create("string:///" + PACKAGE_DIRECTORY + "/FakeTool.java"), Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }

    /** Copies a directory tree, file by file, preserving its shape. */
    private static final class CopyTree extends SimpleFileVisitor<Path> {

        private final Path source;
        private final Path destination;

        private CopyTree(Path source, Path destination) {
            this.source = source;
            this.destination = destination;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                throws IOException {
            Files.createDirectories(destination.resolve(source.relativize(directory).toString()));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                throws IOException {
            Files.copy(
                    file,
                    destination.resolve(source.relativize(file).toString()),
                    StandardCopyOption.REPLACE_EXISTING);
            return FileVisitResult.CONTINUE;
        }
    }
}
