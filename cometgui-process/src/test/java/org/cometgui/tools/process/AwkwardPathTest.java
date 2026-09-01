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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.StageTag;
import org.cometgui.domain.secrets.SecretRegistry;
import org.cometgui.tools.process.fakes.FakeTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PHASE-03 exit gate item 4: paths containing spaces and non-ASCII characters work.
 *
 * <p>The phase document scopes this item to the reference platform, so Linux is enough -- but it
 * has to be real. Every position a path can occupy in a tool launch is exercised with a name that
 * has <strong>both</strong> a space and characters from three non-Latin scripts, and each is
 * asserted by an observed value rather than by the absence of an exception:
 *
 * <ul>
 *   <li>the <strong>working directory</strong>, read back out of the child process itself, which
 *       prints its own {@code user.dir};
 *   <li>an <strong>argument</strong>, echoed back by the child and compared character for
 *       character;
 *   <li>an <strong>output file</strong> the tool writes, whose name is listed back out of the
 *       directory and whose content is read back;
 *   <li>the <strong>log directory and the stage log file</strong>, through the stage runner;
 *   <li>the <strong>class path</strong> the fake is launched from, which is the one path element
 *       that is neither an argument the product chose nor a directory it created.
 * </ul>
 *
 * <h2>Two different locales have to be right, and they are set in two different places</h2>
 *
 * <p><strong>This JVM.</strong> The container has no UTF-8 locale selected: {@code locale -a}
 * offers {@code C}, {@code C.utf8} and {@code POSIX}, and neither {@code LANG} nor {@code LC_ALL}
 * is set in the environment Maven runs in. A JVM started that way reports {@code
 * sun.jnu.encoding=ANSI_X3.4-1968} and <strong>cannot represent a non-ASCII file name at
 * all</strong>: {@code Path.of("café")} throws {@link java.nio.file.InvalidPathException} before
 * any product code runs. {@code -Dsun.jnu.encoding=UTF-8} does not help -- the JVM derives that
 * property from the operating system's locale during startup, before system properties are applied.
 * So {@code cometgui-process/pom.xml} forks surefire with {@code LANG} and {@code LC_ALL} of {@code
 * C.UTF-8}, with the reasoning written into the POM. {@link TheJvmCanSpellTheseNamesAtAll} exists
 * so that removing that block is a loud failure rather than a silently skipped gate item.
 *
 * <p><strong>The tool's JVM.</strong> {@code R-PROC-04} makes {@link ProcessService} <em>clear</em>
 * the inherited environment and put back exactly what the caller asked for, so a child gets no
 * {@code LANG} unless the {@link ToolCommand} names one -- and a child JVM with no locale decodes
 * its own {@code argv} and its own {@code user.dir} as ASCII. {@link
 * TheEmptyEnvironmentCostsAJavaTool} measures exactly that and pins it, because it is a real
 * consequence of a real requirement rather than a defect, and because it is the sort of fact that
 * is much cheaper to have written down than to rediscover. Every other test here therefore declares
 * {@code LANG} and {@code LC_ALL} in the command, which is what {@link ProcessService}'s own
 * documentation tells a caller to do: "a caller that needs an inherited variable must name it in
 * the ToolCommand, where it is recorded, rather than relying on it being there".
 *
 * <p>Neither locale variable's <em>value</em> is non-ASCII, which matters: phase 03 unit 2 recorded
 * that a non-ASCII environment <em>value</em> would be corrupted at the child's end for the same
 * reason, and this phase keeps environment values ASCII rather than papering over it.
 *
 * <p>A product-level consequence, recorded on the POM and repeated here because it outlives this
 * phase: CometGUI installed on a host whose locale is {@code POSIX} would be unable to open a
 * spectrum file whose name is not ASCII, for the same reason and through no fault of its own. Phase
 * 16 owns the packaged runtime's environment, and it now also owns the question of what locale the
 * application must pass to a tool it launches.
 *
 * <p><strong>There is no fixed sleep here</strong> (exit gate item 6).
 */
class AwkwardPathTest {

    /**
     * A directory name with a space and characters from three scripts.
     *
     * <p>Latin with diacritics, Japanese and Greek, so that a mangling which happened to survive
     * one of them is still caught. The space is what a single-token path handler loses.
     */
    private static final String AWKWARD_DIRECTORY = "café über 日本語 αβγ";

    /** An output file name with spaces, an accent and a symbol outside the Latin blocks. */
    private static final String AWKWARD_OUTPUT_FILE = "résultat final ✓.pin";

    /** The text that file is given: a further set of characters, so the two cannot be confused. */
    private static final String AWKWARD_FILE_CONTENT = "protéines humaines · 検索結果";

    /** An argument with a space and non-ASCII characters, as a real FASTA path would have. */
    private static final String AWKWARD_ARGUMENT = "--séquences=protéines humaines ✓.fasta";

    /** A log directory name, distinct from the working directory so a mix-up is visible. */
    private static final String AWKWARD_LOG_DIRECTORY = "journaux de café 記録";

    /** Where the compiled fake is copied to, so the class path element is awkward too. */
    private static final String AWKWARD_CLASSES_DIRECTORY = "clásses dü fake ✓";

    /** The exact non-ASCII sample the {@code unicode} scenario emits, hand-typed. */
    private static final String UNICODE_SAMPLE = "café über 日本語 ✓ αβγ";

    /**
     * The locale a Java tool needs in order to spell a non-ASCII path, declared by the caller.
     *
     * <p>Both names and both values are ASCII, deliberately: see this class's Javadoc.
     */
    private static final Map<String, String> UTF8_LOCALE =
            Map.of("LANG", "C.UTF-8", "LC_ALL", "C.UTF-8");

    /** The instant the fixed clock reports, so every log line's prefix is known in advance. */
    private static final Instant AT = Instant.parse("2026-08-31T19:04:51.250Z");

    /** Its rendering in a log line. Typed by hand. */
    private static final String AT_TEXT = "2026-08-31T19:04:51.250Z";

    /** What a decoder that could not read a byte leaves behind. */
    private static final char REPLACEMENT = '�';

    private static final StageTag COMET = TestStage.named("comet");

    private static ProcessService service() {
        return new ProcessService(Clock.systemUTC());
    }

    private static StageRunner runner(Path logs, RunMessageSink sink) {
        return new StageRunner(
                service(),
                Clock.fixed(AT, ZoneOffset.UTC),
                ProcessRedactor.with(SecretRegistry.empty()),
                sink,
                logs);
    }

    private static List<String> entriesOf(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(entry -> String.valueOf(entry.getFileName())).sorted().toList();
        }
    }

    private static List<String> linesOf(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    private static List<String> tagged(List<String> lines, String tag) {
        String prefix = AT_TEXT + " [" + tag + "] ";
        return lines.stream()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()))
                .toList();
    }

    /**
     * Whether an encoding name is some spelling of UTF-8.
     *
     * @param encoding the name to test
     * @return true for {@code UTF-8}, {@code utf8} and the rest
     */
    private static boolean isUtf8(String encoding) {
        String normalised = encoding.toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
        return "UTF8".equals(normalised);
    }

    // ============================================ the precondition, made loud ==

    @Nested
    @DisplayName("this JVM can spell these names at all")
    class TheJvmCanSpellTheseNamesAtAll {

        @Test
        @DisplayName("sun.jnu.encoding is UTF-8, because this module's POM forks with C.UTF-8")
        void theForkedJvmUsesAUtf8FileNameEncoding() {
            String jnu = System.getProperty("sun.jnu.encoding", "(not set)");

            assertTrue(
                    isUtf8(jnu),
                    () ->
                            "sun.jnu.encoding is \""
                                    + jnu
                                    + "\", so this JVM cannot represent a non-ASCII file name and"
                                    + " PHASE-03 exit gate item 4 cannot be tested at all."
                                    + " cometgui-process/pom.xml configures maven-surefire-plugin"
                                    + " with <environmentVariables><LANG>C.UTF-8</LANG>"
                                    + "<LC_ALL>C.UTF-8</LC_ALL></environmentVariables> exactly to"
                                    + " prevent this; if that block has been removed, restore it."
                                    + " -Dsun.jnu.encoding=UTF-8 does NOT work: the JVM resolves"
                                    + " the property from the OS locale before system properties"
                                    + " are applied. LANG=\""
                                    + System.getenv("LANG")
                                    + "\", LC_ALL=\""
                                    + System.getenv("LC_ALL")
                                    + "\", native.encoding=\""
                                    + System.getProperty("native.encoding")
                                    + "\".");
        }

        @Test
        @DisplayName("a non-ASCII directory name survives being written to disk and listed back")
        void aNonAsciiNameRoundTripsThroughTheFileSystem(@TempDir Path tmp) throws IOException {
            Path awkward = Files.createDirectory(tmp.resolve(AWKWARD_DIRECTORY));

            assertEquals(
                    List.of(AWKWARD_DIRECTORY),
                    entriesOf(tmp),
                    "the name the file system gives back must be the name that was asked for,"
                            + " character for character; anything else means the encoding is lossy"
                            + " and every assertion below would be about the wrong path");
            assertEquals(AWKWARD_DIRECTORY, String.valueOf(awkward.getFileName()));
            assertTrue(Files.isDirectory(awkward));
        }
    }

    // ================================== the working directory and the argument ==

    @Nested
    @DisplayName("through the process service")
    class ThroughTheService {

        @Test
        @DisplayName("the working directory and an argument arrive intact, character for character")
        void theWorkingDirectoryAndAnArgumentSurvive(@TempDir Path tmp)
                throws IOException, InterruptedException {
            Path work = Files.createDirectories(tmp.resolve(AWKWARD_DIRECTORY));
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service()
                            .start(
                                    FakeTools.command(
                                            work,
                                            UTF8_LOCALE,
                                            "echo-context",
                                            "LANG",
                                            AWKWARD_ARGUMENT),
                                    listener);

            assertEquals(0, started.waitForExit());
            assertEquals(
                    List.of(
                            "argc 2",
                            "arg 0 LANG",
                            "arg 1 " + AWKWARD_ARGUMENT,
                            "cwd " + work.toRealPath(),
                            "env LANG C.UTF-8",
                            "env " + AWKWARD_ARGUMENT + " -absent-",
                            "envcount 2"),
                    listener.standardOutput(),
                    "the child prints its own user.dir and its own argv, so these are the values"
                            + " the operating system actually delivered rather than the ones this"
                            + " test hoped for");
            assertTrue(
                    String.valueOf(work.toRealPath()).contains(AWKWARD_DIRECTORY),
                    "and the directory really does carry the awkward name");
        }

        @Test
        @DisplayName("an output file with a space and non-ASCII is written and read back")
        void anAwkwardOutputFileIsWrittenAndReadBack(@TempDir Path tmp)
                throws IOException, InterruptedException {
            Path work = Files.createDirectories(tmp.resolve(AWKWARD_DIRECTORY));
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service()
                            .start(
                                    FakeTools.command(
                                            work,
                                            UTF8_LOCALE,
                                            "write-files",
                                            AWKWARD_OUTPUT_FILE + "=" + AWKWARD_FILE_CONTENT),
                                    listener);

            assertEquals(0, started.waitForExit());
            assertEquals(List.of("wrote " + AWKWARD_OUTPUT_FILE), listener.standardOutput());
            assertEquals(
                    List.of(AWKWARD_OUTPUT_FILE),
                    entriesOf(work),
                    "the file the tool created is named exactly what it was told to name it");
            assertEquals(
                    AWKWARD_FILE_CONTENT + "\n",
                    Files.readString(work.resolve(AWKWARD_OUTPUT_FILE), StandardCharsets.UTF_8),
                    "and its content came back through the file system unchanged");
        }

        @Test
        @DisplayName("the fake runs from a class path directory with a space and non-ASCII in it")
        void theClassPathItselfCanBeAwkward(@TempDir Path tmp)
                throws IOException, InterruptedException {
            Path classes =
                    FakeTools.classesDirectoryCopiedTo(tmp.resolve(AWKWARD_CLASSES_DIRECTORY));
            Path work = Files.createDirectories(tmp.resolve(AWKWARD_DIRECTORY));
            RecordingListener listener = new RecordingListener();
            ToolCommand command =
                    new ToolCommand(
                            FakeTools.argv(classes, "echo-context", AWKWARD_ARGUMENT),
                            work,
                            UTF8_LOCALE);

            StartedProcess started = service().start(command, listener);

            assertEquals(
                    0,
                    started.waitForExit(),
                    "the JVM found fakes.FakeTool on a class path element containing a space and"
                            + " three non-Latin scripts, which is the one path in a launch that is"
                            + " neither an argument the product chose nor a directory it made");
            assertTrue(
                    Files.isRegularFile(classes.resolve("fakes").resolve("FakeTool.class")),
                    "and the class really is under the awkward directory");
            assertEquals(
                    List.of(
                            "argc 1",
                            "arg 0 " + AWKWARD_ARGUMENT,
                            "cwd " + work.toRealPath(),
                            "env " + AWKWARD_ARGUMENT + " -absent-",
                            "envcount 2"),
                    listener.standardOutput());
            assertTrue(
                    command.displayString().contains(AWKWARD_CLASSES_DIRECTORY),
                    () ->
                            "the rendered command keeps the class path readable rather than"
                                    + " escaping it into unrecognisability: "
                                    + command.displayString());
        }
    }

    // ============================================= the log directory and file ==

    @Nested
    @DisplayName("through the stage runner")
    class ThroughTheStageRunner {

        @Test
        @DisplayName("the log directory, the log file and every line in it survive the names")
        void theStageLogGoesIntoAnAwkwardDirectory(@TempDir Path tmp)
                throws IOException, InterruptedException {
            Path work = Files.createDirectories(tmp.resolve(AWKWARD_DIRECTORY));
            Path logs = tmp.resolve(AWKWARD_LOG_DIRECTORY);
            RecordingSink sink = new RecordingSink();

            StageOutcome outcome =
                    runner(logs, sink)
                            .start(
                                    COMET,
                                    FakeTools.command(
                                            work, UTF8_LOCALE, "unicode", AWKWARD_OUTPUT_FILE))
                            .awaitOutcome();

            assertEquals(0, outcome.exitCode());
            assertEquals(
                    logs.resolve("comet.log"),
                    outcome.logFile(),
                    "the log file is inside the directory with the awkward name, and the stage"
                            + " identifier is what names the file");
            assertEquals(List.of("comet.log"), entriesOf(logs));
            assertEquals(
                    AWKWARD_LOG_DIRECTORY,
                    String.valueOf(logs.getFileName()),
                    "which the file system gave back unchanged");

            List<String> lines = linesOf(outcome.logFile());

            assertEquals(
                    AT_TEXT + " [cometgui] stage comet started in " + work,
                    lines.get(0),
                    "the header names the awkward working directory in full");
            assertTrue(
                    lines.get(1).contains(AWKWARD_OUTPUT_FILE),
                    () -> "and the rendered command keeps the argument readable: " + lines.get(1));
            assertEquals(
                    List.of(UNICODE_SAMPLE),
                    tagged(lines, "stdout"),
                    "the tool's non-ASCII output reached the disk undamaged");
            assertEquals(
                    List.of(UNICODE_SAMPLE),
                    tagged(lines, "stderr"),
                    "on both streams, separately");
            assertEquals(
                    UNICODE_SAMPLE,
                    Files.readString(work.resolve(AWKWARD_OUTPUT_FILE), StandardCharsets.UTF_8),
                    "and the output file the tool wrote holds exactly the same characters");
            assertEquals(List.of(AWKWARD_OUTPUT_FILE), entriesOf(work));
            assertEquals(
                    List.of(UNICODE_SAMPLE, UNICODE_SAMPLE),
                    sink.texts(),
                    "the console got both lines too, undamaged");
        }
    }

    // =================================== what R-PROC-04's cleared environment costs ==

    @Nested
    @DisplayName("the empty environment costs a Java tool its non-ASCII paths")
    class TheEmptyEnvironmentCostsAJavaTool {

        @Test
        @DisplayName("with NO locale in the command, the child decodes its own argv as ASCII")
        void withoutALocaleAJavaToolCannotSpellTheName(@TempDir Path tmp)
                throws IOException, InterruptedException {
            Path work = Files.createDirectories(tmp.resolve(AWKWARD_DIRECTORY));
            RecordingListener listener = new RecordingListener();

            StartedProcess started =
                    service()
                            .start(
                                    FakeTools.command(work, "echo-context", AWKWARD_ARGUMENT),
                                    listener);

            assertEquals(0, started.waitForExit());
            List<String> output = listener.standardOutput();

            assertEquals("argc 1", output.get(0));
            assertEquals("envcount 0", output.get(output.size() - 1), "R-PROC-04, as designed");
            assertNotEquals(
                    "arg 0 " + AWKWARD_ARGUMENT,
                    output.get(1),
                    "THIS IS THE POINT OF THE TEST. R-PROC-04 makes the service clear the"
                            + " environment, so the child JVM has no LANG and reports"
                            + " sun.jnu.encoding=ANSI_X3.4-1968; it therefore cannot decode the"
                            + " bytes of its own argv. The service delivered the right bytes -- a"
                            + " native tool such as Comet, which never decodes them, is"
                            + " unaffected -- but a JAVA tool given a non-ASCII path with no"
                            + " locale gets replacement characters. If this assertion ever starts"
                            + " failing, a JDK or a container has changed the default and the"
                            + " caller-declares-its-locale rule can be revisited.");
            assertTrue(
                    output.get(1).indexOf(REPLACEMENT) >= 0,
                    () ->
                            "and what it got instead is replacement characters, one per byte it"
                                    + " could not read: "
                                    + output.get(1));
            assertTrue(
                    output.get(2).startsWith("cwd ") && output.get(2).indexOf(REPLACEMENT) >= 0,
                    () -> "the working directory reaches it the same way: " + output.get(2));
        }
    }
}
