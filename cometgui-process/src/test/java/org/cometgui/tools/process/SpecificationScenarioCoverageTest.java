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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * PHASE-03 exit gate item 1: every fake-executable scenario in the specification has a passing
 * test, and deleting one of those tests fails the build instead of quietly shrinking the gate.
 *
 * <h2>The map, and why it is not a list of names that checks nothing</h2>
 *
 * <p>A coverage map that recites scenario names and asserts nothing about them is this project's
 * signature defect wearing a helpful hat. This one is tied down at four ends, and each of the four
 * fails loudly on its own:
 *
 * <ol>
 *   <li><strong>The scenario list is checked against the specification file on disk.</strong> The
 *       eleven phrases below are hand-typed, and {@link TheListMatchesTheSpecification} reads
 *       {@code specification.rst}, finds the section <em>Component tests with fake executables</em>
 *       and requires every phrase to appear in it. Adding a scenario to the specification without
 *       adding it here does not fail -- nothing can detect an omission from a hand-typed list --
 *       but changing or removing one does, which is the direction that rots silently.
 *   <li><strong>Each named test method must exist.</strong> Resolved reflectively, searching nested
 *       classes, and required to carry {@code @Test} and not {@code @Disabled}. Deleting or
 *       renaming a covering test fails here by name.
 *   <li><strong>Each named test method must actually drive that fake scenario.</strong> Its body is
 *       extracted from the source file by brace matching -- {@link TheBodyExtractor} proves the
 *       extractor on sources whose strings and comments contain braces -- and must contain the
 *       scenario's name as a quoted literal.
 *   <li><strong>Each named test must drive it THROUGH THE PROCESS SERVICE.</strong> The body must
 *       start something, and the file must not construct a {@link ProcessBuilder}. That last rule
 *       is what excludes {@code FakeToolSelfTest}: it proves the <em>fakes</em> behave as
 *       documented by launching them with a bare {@code ProcessBuilder}, and says so itself. Gate
 *       item 1 is about the service.
 * </ol>
 *
 * <p>What this cannot do is judge whether a covering test asserts anything worth asserting. That is
 * what reading it is for; the sign-off in {@code handoffs/PHASE-03-worklog.rst} records that.
 */
class SpecificationScenarioCoverageTest {

    /** This module's test source root, relative to the module directory. */
    private static final String TEST_SOURCE_ROOT = "src/test/java";

    /** The package every covering test class lives in. */
    private static final String TEST_PACKAGE = "org.cometgui.tools.process";

    /** The module this test belongs to; asserted so a mis-resolved root fails loudly. */
    private static final String MODULE_DIRECTORY_NAME = "cometgui-process";

    /** The specification, at the root of the repository. */
    private static final String SPECIFICATION_FILE = "specification.rst";

    /** The section of it that lists the scenarios. Hand-typed. */
    private static final String SPECIFICATION_SECTION = "Component tests with fake executables";

    /** A sentence from that section, so a scan that found the wrong text fails. Hand-typed. */
    private static final String SPECIFICATION_OPENING = "Create small test executables";

    /** The fewest characters that section may have. A scan over nothing is not a clean scan. */
    private static final int FEWEST_SECTION_CHARACTERS = 300;

    /**
     * One test that covers a scenario.
     *
     * @param testClass the simple name of the top-level test class
     * @param method the test method's name, which may be inside a nested class
     * @param scenario the {@code fakes.FakeTool} scenario that method must drive
     */
    private record Coverage(String testClass, String method, String scenario) {

        @Override
        public String toString() {
            return testClass + "." + method + " (" + scenario + ")";
        }
    }

    /**
     * One scenario from the specification, and every test that covers it.
     *
     * @param phrase the specification's own words, hand-typed
     * @param coveredBy the tests, at least one
     */
    private record Scenario(String phrase, List<Coverage> coveredBy) {}

    /**
     * The specification's eleven fake-executable scenarios, in the order it lists them.
     *
     * <p>Verbatim from {@code specification.rst}, "Component tests with fake executables": "stdout/
     * stderr interleaving; exit 0 with outputs; non-zero exit; child process creation; a hanging
     * process and cancellation; huge stdout/stderr volume; missing output despite exit 0; malformed
     * output; a partial file followed by failure; delayed output creation; and paths containing
     * spaces and Unicode."
     */
    private static final List<Scenario> SCENARIOS =
            List.of(
                    new Scenario(
                            "stdout/stderr interleaving",
                            List.of(
                                    new Coverage(
                                            "ProcessServiceTest",
                                            "bothStreamsArriveCompleteAndInOrder",
                                            "interleave"),
                                    new Coverage(
                                            "StageRunnerTest",
                                            "bothStreamsInOneFile",
                                            "interleave"))),
                    new Scenario(
                            "exit 0 with outputs",
                            List.of(
                                    new Coverage(
                                            "FakeScenarioSuiteTest",
                                            "exitZeroWithOutputsWritesEveryFileAndSaysSo",
                                            "write-files"))),
                    new Scenario(
                            "non-zero exit",
                            List.of(
                                    new Coverage(
                                            "ProcessServiceTest", "nonZeroExitCode", "exit-code"))),
                    new Scenario(
                            "child process creation",
                            List.of(
                                    new Coverage(
                                            "ProcessCancellationTest",
                                            "neitherTheParentNorItsChildSurvivesCancellation",
                                            "hang-with-child"))),
                    new Scenario(
                            "a hanging process and cancellation",
                            List.of(
                                    new Coverage(
                                            "ProcessServiceTest",
                                            "aHangingProcessIsTerminated",
                                            "hang"),
                                    new Coverage(
                                            "ProcessCancellationTest",
                                            "afterTheGraceItIsKilled",
                                            "hang-ignoring-term"))),
                    new Scenario(
                            "huge stdout/stderr volume",
                            List.of(
                                    new Coverage(
                                            "FiveHundredMegabyteFloodTest",
                                            "theFloodCompletesWithABoundedHeapAndACompleteLog",
                                            "flood"),
                                    new Coverage(
                                            "FakeScenarioSuiteTest",
                                            "aHugeStandardErrorVolumeArrivesCompleteAndSeparate",
                                            "flood"),
                                    new Coverage(
                                            "FakeScenarioSuiteTest",
                                            "aHugeVolumeOnBothStreamsStaysSeparate",
                                            "flood"))),
                    new Scenario(
                            "missing output despite exit 0",
                            List.of(
                                    new Coverage(
                                            "FakeScenarioSuiteTest",
                                            "missingOutputDespiteExitZeroLeavesNothingOnDisk",
                                            "missing-output"))),
                    new Scenario(
                            "malformed output",
                            List.of(
                                    new Coverage(
                                            "FakeScenarioSuiteTest",
                                            "malformedOutputArrivesExactlyAndTheExitIsZero",
                                            "malformed-output"))),
                    new Scenario(
                            "a partial file followed by failure",
                            List.of(
                                    new Coverage(
                                            "FakeScenarioSuiteTest",
                                            "aPartialFileSurvivesTheFailureWithItsExactByteCount",
                                            "partial-then-fail"))),
                    new Scenario(
                            "delayed output creation",
                            List.of(
                                    new Coverage(
                                            "FakeScenarioSuiteTest",
                                            "theOutputFileAppearsOnlyAfterTheToolAnnouncesIt",
                                            "delayed-output"),
                                    new Coverage(
                                            "StageRunnerTest",
                                            "linesAreOnDiskWhileTheToolRuns",
                                            "delayed-output"))),
                    new Scenario(
                            "paths containing spaces and Unicode",
                            List.of(
                                    new Coverage(
                                            "AwkwardPathTest",
                                            "theWorkingDirectoryAndAnArgumentSurvive",
                                            "echo-context"),
                                    new Coverage(
                                            "AwkwardPathTest",
                                            "anAwkwardOutputFileIsWrittenAndReadBack",
                                            "write-files"),
                                    new Coverage(
                                            "AwkwardPathTest",
                                            "theClassPathItselfCanBeAwkward",
                                            "echo-context"),
                                    new Coverage(
                                            "AwkwardPathTest",
                                            "theStageLogGoesIntoAnAwkwardDirectory",
                                            "unicode"))));

    /** How the eleven phrases read when joined; hand-typed, so deleting an entry fails. */
    private static final String JOINED_PHRASES =
            "stdout/stderr interleaving; exit 0 with outputs; non-zero exit; child process"
                    + " creation; a hanging process and cancellation; huge stdout/stderr volume;"
                    + " missing output despite exit 0; malformed output; a partial file followed"
                    + " by failure; delayed output creation; paths containing spaces and Unicode";

    /** How a body is recognised as driving something rather than merely mentioning it. */
    private static final String STARTS_SOMETHING = ".start(";

    /** What a file must not contain if it is to count as covering a scenario. */
    private static final String BARE_PROCESS_BUILDER = "new ProcessBuilder";

    /** Where a Java source scanner is, inside or outside a string, a character or a comment. */
    private enum Mode {
        CODE,
        STRING,
        CHARACTER,
        LINE_COMMENT,
        BLOCK_COMMENT
    }

    private static Path moduleDirectory() {
        Path build =
                Path.of(System.getProperty("cometgui.buildDirectory", "target")).toAbsolutePath();
        Path module = build.getParent();
        assertTrue(
                module != null && Files.isDirectory(module),
                () -> "the module directory could not be derived from " + build);
        assertEquals(
                MODULE_DIRECTORY_NAME,
                String.valueOf(module.getFileName()),
                "this test resolved its roots to the wrong module");
        return module;
    }

    private static Path sourceOf(String testClass) {
        return moduleDirectory()
                .resolve(TEST_SOURCE_ROOT)
                .resolve(TEST_PACKAGE.replace('.', '/'))
                .resolve(testClass + ".java");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + file, unreadable);
        }
    }

    /**
     * The named method, wherever it is declared: on the class or in one of its nested classes.
     *
     * @param type the top-level test class
     * @param name the method's name
     * @return the method, or empty if nothing declares it
     */
    private static Optional<Method> findMethod(Class<?> type, String name) {
        for (Method declared : type.getDeclaredMethods()) {
            if (declared.getName().equals(name)) {
                return Optional.of(declared);
            }
        }
        for (Class<?> nested : type.getDeclaredClasses()) {
            Optional<Method> found = findMethod(nested, name);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * The text between the braces of the named method, with strings and comments respected.
     *
     * <p>Brace counting is the only way to get a method's own text out of a source file without a
     * parser, and it is wrong in exactly one interesting way -- a brace inside a string literal or
     * a comment -- so this tracks which of those it is in. {@link TheBodyExtractor} proves that on
     * a source built to contain all three.
     *
     * @param source the whole source file
     * @param methodName the method to extract
     * @return its body, or the empty string if it is not declared there
     */
    private static String bodyOf(String source, String methodName) {
        Matcher declaration =
                Pattern.compile("\\bvoid\\s+" + Pattern.quote(methodName) + "\\s*\\(")
                        .matcher(source);
        if (!declaration.find()) {
            return "";
        }
        Mode mode = Mode.CODE;
        int depth = 0;
        int bodyStart = -1;
        int index = declaration.end();
        while (index < source.length()) {
            char character = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            switch (mode) {
                case CODE -> {
                    if (character == '"') {
                        mode = Mode.STRING;
                    } else if (character == '\'') {
                        mode = Mode.CHARACTER;
                    } else if (character == '/' && next == '/') {
                        mode = Mode.LINE_COMMENT;
                        index++;
                    } else if (character == '/' && next == '*') {
                        mode = Mode.BLOCK_COMMENT;
                        index++;
                    } else if (character == '{') {
                        depth++;
                        if (bodyStart < 0) {
                            bodyStart = index + 1;
                        }
                    } else if (character == '}') {
                        depth--;
                        if (depth == 0 && bodyStart >= 0) {
                            return source.substring(bodyStart, index);
                        }
                    }
                }
                case STRING -> {
                    if (character == '\\') {
                        index++;
                    } else if (character == '"') {
                        mode = Mode.CODE;
                    }
                }
                case CHARACTER -> {
                    if (character == '\\') {
                        index++;
                    } else if (character == '\'') {
                        mode = Mode.CODE;
                    }
                }
                case LINE_COMMENT -> {
                    if (character == '\n') {
                        mode = Mode.CODE;
                    }
                }
                case BLOCK_COMMENT -> {
                    if (character == '*' && next == '/') {
                        mode = Mode.CODE;
                        index++;
                    }
                }
                default -> throw new IllegalStateException("unreachable mode " + mode);
            }
            index++;
        }
        return "";
    }

    /**
     * The text of the specification's fake-executable section, whitespace collapsed.
     *
     * @return the section, as one line
     */
    private static String specificationSection() {
        Path repositoryRoot =
                Objects.requireNonNull(
                        moduleDirectory().getParent(),
                        "the module directory has no parent, so the repository root cannot be"
                                + " found and the specification cannot be read");
        Path specification = repositoryRoot.resolve(SPECIFICATION_FILE);
        assertTrue(
                Files.isRegularFile(specification),
                () -> specification + " is missing, so nothing here is being checked against it");
        List<String> lines;
        try {
            lines = Files.readAllLines(specification, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + specification, unreadable);
        }
        int heading = -1;
        for (int index = 0; index < lines.size(); index++) {
            if (SPECIFICATION_SECTION.equals(lines.get(index).strip())) {
                heading = index;
                break;
            }
        }
        if (heading < 0) {
            fail(
                    "the section \""
                            + SPECIFICATION_SECTION
                            + "\" is not in "
                            + specification
                            + ", so the scenario list below is checked against nothing");
        }
        List<String> body = new ArrayList<>();
        boolean reachedTheNextSection = false;
        for (int index = heading + 2; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!line.isBlank() && line.chars().allMatch(character -> character == '-')) {
                reachedTheNextSection = true;
                break;
            }
            body.add(line);
        }
        if (reachedTheNextSection && !body.isEmpty()) {
            /* The line before the next section's underline is that section's title. */
            body.removeLast();
        }
        return String.join(" ", body).replaceAll("\\s+", " ").strip();
    }

    // ============================================ the list, against the source ==

    @Nested
    @DisplayName("the scenario list is the specification's")
    class TheListMatchesTheSpecification {

        @Test
        @DisplayName("eleven scenarios, each named once, in the order the specification lists them")
        void theListIsTheWholeList() {
            List<String> phrases = SCENARIOS.stream().map(Scenario::phrase).toList();

            assertEquals(11, SCENARIOS.size(), "the specification lists eleven");
            assertEquals(
                    phrases.size(),
                    Set.copyOf(phrases).size(),
                    () -> "a scenario is named twice: " + phrases);
            assertEquals(
                    JOINED_PHRASES,
                    String.join("; ", phrases),
                    "deleting or reordering an entry has to fail here, not shrink the gate");
        }

        @Test
        @DisplayName("every phrase really is in specification.rst, in that section")
        void everyPhraseIsInTheSpecification() {
            String section = specificationSection();

            assertTrue(
                    section.contains(SPECIFICATION_OPENING),
                    () -> "the section found does not look like the right one: " + section);
            assertTrue(
                    section.length() >= FEWEST_SECTION_CHARACTERS,
                    () ->
                            "the section read from the specification is only "
                                    + section.length()
                                    + " characters, so this test is checking almost nothing: "
                                    + section);
            List<String> absent =
                    SCENARIOS.stream()
                            .map(Scenario::phrase)
                            .filter(phrase -> !section.contains(phrase))
                            .toList();
            assertEquals(
                    List.of(),
                    absent,
                    () ->
                            "these phrases are not in the specification's own list any more, so"
                                    + " the map has drifted from the requirement it implements."
                                    + " The section reads: "
                                    + section);
        }
    }

    // ================================= the covering tests, against the class path ==

    @Nested
    @DisplayName("every scenario has a covering test that exists and runs")
    class EveryScenarioIsCovered {

        @Test
        @DisplayName("each named method exists, carries @Test, and is not disabled")
        void theCoveringTestsExist() throws ClassNotFoundException {
            List<String> problems = new ArrayList<>();
            for (Scenario scenario : SCENARIOS) {
                assertFalse(
                        scenario.coveredBy().isEmpty(),
                        () -> "\"" + scenario.phrase() + "\" names no covering test at all");
                for (Coverage coverage : scenario.coveredBy()) {
                    Class<?> type = Class.forName(TEST_PACKAGE + "." + coverage.testClass());
                    Optional<Method> found = findMethod(type, coverage.method());
                    if (found.isEmpty()) {
                        problems.add(
                                scenario.phrase()
                                        + " -> "
                                        + coverage
                                        + ": no such method in "
                                        + type.getName()
                                        + " or any of its nested classes");
                        continue;
                    }
                    Method method = found.get();
                    if (!method.isAnnotationPresent(Test.class)) {
                        problems.add(scenario.phrase() + " -> " + coverage + ": not a @Test");
                    }
                    if (method.isAnnotationPresent(Disabled.class)
                            || method.getDeclaringClass().isAnnotationPresent(Disabled.class)) {
                        problems.add(scenario.phrase() + " -> " + coverage + ": @Disabled");
                    }
                    if (method.isAnnotationPresent(Timeout.class)) {
                        problems.add(
                                scenario.phrase()
                                        + " -> "
                                        + coverage
                                        + ": carries @Timeout, which turns a failure bound into"
                                        + " the mechanism");
                    }
                }
            }
            assertEquals(
                    List.of(),
                    problems,
                    "a scenario whose covering test has been deleted, renamed or disabled has"
                            + " stopped being covered, and PHASE-03 exit gate item 1 has to fail"
                            + " rather than keep reciting the name");
        }

        @Test
        @DisplayName("each named method drives that fake scenario, through the process service")
        void theCoveringTestsDriveTheScenarioThroughTheService() {
            List<String> problems = new ArrayList<>();
            for (Scenario scenario : SCENARIOS) {
                for (Coverage coverage : scenario.coveredBy()) {
                    Path file = sourceOf(coverage.testClass());
                    if (!Files.isRegularFile(file)) {
                        problems.add(coverage + ": no source file at " + file);
                        continue;
                    }
                    String source = read(file);
                    if (source.contains(BARE_PROCESS_BUILDER)) {
                        problems.add(
                                coverage
                                        + ": "
                                        + coverage.testClass()
                                        + " constructs a ProcessBuilder, so it proves how the"
                                        + " FAKES behave and not how the SERVICE handles them");
                    }
                    String body = bodyOf(source, coverage.method());
                    if (body.isEmpty()) {
                        problems.add(coverage + ": its body could not be found in " + file);
                        continue;
                    }
                    if (!body.contains('"' + coverage.scenario() + '"')) {
                        problems.add(
                                coverage
                                        + ": its body never names the fake scenario \""
                                        + coverage.scenario()
                                        + "\", so it is not the test this map claims it is");
                    }
                    if (!body.contains(STARTS_SOMETHING)) {
                        problems.add(
                                coverage
                                        + ": its body never calls "
                                        + STARTS_SOMETHING
                                        + ", so it starts no process and covers nothing");
                    }
                }
            }
            assertEquals(List.of(), problems);
        }

        @Test
        @DisplayName("NEGATIVE CONTROL: a method that is not there is reported by name")
        void aMissingMethodIsCaught() throws ClassNotFoundException {
            Class<?> type = Class.forName(TEST_PACKAGE + ".ProcessServiceTest");

            assertTrue(
                    findMethod(type, "nonZeroExitCode").isPresent(),
                    "the search finds a method that really is in a nested class");
            assertTrue(
                    findMethod(type, "aTestThatWasDeletedLastTuesday").isEmpty(),
                    "and reports one that is not, which is what makes the map above bite");
            assertEquals(
                    "",
                    bodyOf(read(sourceOf("ProcessServiceTest")), "aTestThatWasDeletedLastTuesday"),
                    "and the body extractor agrees rather than returning something plausible");
        }
    }

    // ======================================================== the body extractor ==

    @Nested
    @DisplayName("the body extractor, on sources built to break it")
    class TheBodyExtractor {

        @Test
        @DisplayName("a brace inside a string, a char literal or a comment does not end the body")
        void bracesInsideLiteralsAndCommentsAreIgnored() {
            String source =
                    String.join(
                            "\n",
                            "class Sample {",
                            "    @Test",
                            "    void tricky() {",
                            "        String closing = \"}\";",
                            "        char brace = '}';",
                            "        String escaped = \"\\\"}\\\"\";",
                            "        // } a comment with a brace",
                            "        /* } and a block one */",
                            "        if (true) {",
                            "            run(\"missing-output\");",
                            "        }",
                            "    }",
                            "",
                            "    void after() {",
                            "        neverSeen();",
                            "    }",
                            "}");

            String body = bodyOf(source, "tricky");

            assertTrue(body.contains("run(\"missing-output\");"), () -> "body was: " + body);
            assertTrue(body.contains("if (true) {"), () -> "body was: " + body);
            assertFalse(
                    body.contains("neverSeen"),
                    () -> "the extractor ran past the end of the method: " + body);
            assertFalse(body.contains("void after"), () -> "body was: " + body);
        }

        @Test
        @DisplayName("a method whose signature spans lines still yields its own body")
        void aMultiLineSignatureIsHandled() {
            String source =
                    String.join(
                            "\n",
                            "class Sample {",
                            "    void spread(",
                            "            Path tmp, int lines)",
                            "            throws IOException, InterruptedException {",
                            "        start(\"flood\");",
                            "    }",
                            "}");

            assertEquals(
                    "\n        start(\"flood\");\n    ",
                    bodyOf(source, "spread"),
                    "the body begins at the brace that follows the throws clause");
        }

        @Test
        @DisplayName("a name that is only a prefix of a real method is not matched")
        void aPartialNameDoesNotMatch() {
            String source =
                    String.join(
                            "\n",
                            "class Sample {",
                            "    void floodTest() {",
                            "        a();",
                            "    }",
                            "}");

            assertEquals("", bodyOf(source, "flood"), "\\b and the parenthesis do the work");
            assertFalse(bodyOf(source, "floodTest").isEmpty());
        }
    }
}
