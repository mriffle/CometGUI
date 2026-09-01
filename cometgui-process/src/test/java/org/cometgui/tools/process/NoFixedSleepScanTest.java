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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PHASE-03 exit gate item 6: no test in this phase uses a fixed sleep to synchronise.
 *
 * <h2>Scope</h2>
 *
 * <p>This scan reads <strong>this phase's own test sources</strong> and nothing else: every {@code
 * .java} file under {@code cometgui-process/src/test/java}, plus the fake tool program under {@code
 * cometgui-process/src/test/resources/fakes}, which is a test source that Maven happens to copy as
 * a resource rather than compile. It deliberately does not scan other modules. {@code cometgui-app}
 * carries two pre-existing sleeps from phase 02, both backoffs inside deadline-bounded polling
 * loops rather than synchronisation, and they are that phase's to justify.
 *
 * <h2>This file is not exempt from its own scan</h2>
 *
 * <p>{@code theScanIncludesItself} pins that, because a checker that excludes its own source can
 * hide a defect in the one file nobody is allowed to look at. The consequence is that no forbidden
 * form may ever be written out literally here -- not in a Javadoc, not in a test fixture. {@link
 * #forbiddenCall(String, String)} and its neighbours assemble the fixtures from pieces, and the
 * detectors themselves are built the same way where a literal pattern would match itself. That is
 * awkward to read and it is the price of a checker with no blind spot.
 *
 * <h2>What is forbidden, and what is not</h2>
 *
 * <p>The distinction is whether the duration is the <strong>mechanism</strong> or a <strong>failure
 * bound</strong>. A test that pauses for 200 ms and then looks is asserting that this machine is
 * fast enough, which is a property of the machine; a test that waits on a latch counted down by the
 * event it cares about is asserting that the event happened, and its timeout only decides how long
 * a broken build hangs before it fails.
 *
 * <p>Forbidden, by {@link #FORBIDDEN_FORMS}: a thread pause through {@code Thread}, through any of
 * {@code TimeUnit}'s seven units, or through any other receiver; {@code LockSupport} parking in all
 * its forms, named or statically imported; and a monitor wait bounded by a literal number.
 *
 * <p>Allowed, and used throughout this phase: a bounded {@code latch.await(n, unit)} whose latch is
 * counted down by a real event; {@code Future.get(n, unit)}; {@code ProcessHandle.onExit()}; {@code
 * StartedProcess.waitForExit()}; and {@code assertThrows(TimeoutException.class, ...)} as a
 * negative window, which asserts that something did <em>not</em> happen inside a window and cannot
 * be replaced by anything shorter. {@link TheScanIsNotOverEager} pins each of those as clean,
 * because a scan that rejected them would push this phase towards unbounded waits that hang a
 * broken build for ever.
 *
 * <h2>Why this is a test and not a grep</h2>
 *
 * <p>A grep lives in a script somebody has to remember to run. This runs in {@code mvn verify}, and
 * the three things that make it a gate rather than a decoration are proved here: it fails naming a
 * file and a line number ({@link TheScanHasTeeth}); it catches the indirect form -- a pause inside
 * a helper that a test calls, which a scan restricted to {@code @Test} method bodies would miss;
 * and it refuses to report a clean sweep having read nothing ({@link TheEmptySweepGuard}), which is
 * the phase 01 vacuous-rule failure and the likeliest way this gate item goes wrong.
 */
class NoFixedSleepScanTest {

    /** This module's test source root, relative to the module directory. */
    private static final String TEST_SOURCE_ROOT = "src/test/java";

    /** Where the fake tool program lives; Maven copies it rather than compiling it. */
    private static final String FAKE_RESOURCE_ROOT = "src/test/resources/fakes";

    /** The only file that directory is allowed to hold. */
    private static final String FAKE_PROGRAM = "FakeTool.java";

    /** The module this scan belongs to; asserted so a mis-resolved root fails loudly. */
    private static final String MODULE_DIRECTORY_NAME = "cometgui-process";

    /** This file, so the scan can be required to have read itself. */
    private static final String THIS_FILE = "NoFixedSleepScanTest.java";

    /**
     * The fewest {@code .java} files the scanned roots may hold, hand-typed.
     *
     * <p>There were twenty-five when this was written -- twenty-one classes in {@code
     * org.cometgui.tools.process}, three in its {@code fakes} sub-package and the fake tool program
     * itself. The floor is deliberately below that and enormously above zero: its whole job is to
     * fail when the scan is pointed somewhere that does not contain this phase's tests, which is
     * what a renamed module or a wrongly resolved working directory would do.
     */
    private static final int FEWEST_SOURCES_EXPECTED = 20;

    /**
     * The fewest lines the scan must actually have read, hand-typed.
     *
     * <p>There were over nine thousand when this was written. A file count on its own would still
     * pass if every file were opened and none of them read.
     */
    private static final long FEWEST_LINES_EXPECTED = 4_000L;

    /**
     * Files that must be among those scanned, named by hand.
     *
     * <p>Two of them are not beside this test: {@code FakeToolSelfTest.java} is in a sub-package
     * and {@code FakeTool.java} is under {@code src/test/resources}, so a scan that had quietly
     * stopped descending, or had dropped its second root, fails here by name rather than by an
     * arithmetic a reader would have to check.
     */
    private static final List<String> MUST_BE_SCANNED =
            List.of(
                    THIS_FILE,
                    "ProcessServiceTest.java",
                    "ProcessCancellationTest.java",
                    "StageRunnerTest.java",
                    "StageLogFileTest.java",
                    "RecordingListener.java",
                    "FakeToolSelfTest.java",
                    FAKE_PROGRAM);

    /**
     * The forbidden forms, most specific first: the first rule that matches a line names it.
     *
     * <p>Three of the six patterns are assembled from fragments rather than written out. That is
     * not obfuscation: this file is scanned by these very rules, and a pattern whose own source
     * text contains the form it looks for would report itself. The fragments are chosen so the
     * compiled pattern is unchanged and the literal source text is not an instance of it.
     */
    private static final List<Forbidden> FORBIDDEN_FORMS =
            List.of(
                    new Forbidden(
                            "Thread pause",
                            Pattern.compile("(?<![\\w$.])Thread\\s*\\.\\s*" + "sle" + "ep\\s*\\("),
                            "the thread stops for a fixed time and the test then asserts that what"
                                    + " it was waiting for has happened, which is an assertion"
                                    + " about how fast this machine is"),
                    new Forbidden(
                            "TimeUnit pause",
                            Pattern.compile(
                                    "(?<![\\w$.])(?:TimeUnit\\s*\\.\\s*)?(?:NANOSECONDS"
                                            + "|MICROSECONDS|MILLISECONDS|SECONDS|MINUTES|HOURS"
                                            + "|DAYS)\\s*\\.\\s*"
                                            + "sle"
                                            + "ep\\s*\\("),
                            "the same delay wearing a different name"),
                    new Forbidden(
                            "a pause through any receiver",
                            Pattern.compile("\\.\\s*" + "sle" + "ep\\s*\\("),
                            "a unit held in a variable pauses just as fixedly as a named one"),
                    new Forbidden(
                            "LockSupport parking",
                            Pattern.compile("(?<![\\w$.])LockSupport\\s*\\.\\s*park\\w*\\s*\\("),
                            "parking for a fixed time is the same delay with a lower-level API"),
                    new Forbidden(
                            "bare parking",
                            Pattern.compile("(?<![\\w$])" + "par" + "k(?:Nanos|Until)?\\s*\\("),
                            "the same call reached through a static import or another receiver"),
                    new Forbidden(
                            "a monitor wait with a literal bound",
                            Pattern.compile("(?<![\\w$])wait\\s*\\(\\s*[0-9]"),
                            "a monitor wait bounded by a hard-coded number of milliseconds is a"
                                    + " fixed delay that can also be woken early, which is worse"));

    /**
     * One forbidden form.
     *
     * @param rule its name, which appears in the failure
     * @param pattern how it is recognised
     * @param why it is not allowed
     */
    private record Forbidden(String rule, Pattern pattern, String why) {}

    /**
     * One forbidden call, found.
     *
     * @param file the file it is in
     * @param line its one-based line number
     * @param rule which rule caught it
     * @param text the source line, stripped
     */
    private record Finding(Path file, int line, String rule, String text) {

        @Override
        public String toString() {
            return file + ":" + line + ": " + rule + ": " + text;
        }
    }

    /**
     * What one scan saw.
     *
     * @param findings every forbidden call, in the order they were read
     * @param filesRead the files at least one line was actually read from
     * @param linesRead how many lines were read in total
     */
    private record Scan(List<Finding> findings, Set<Path> filesRead, long linesRead) {}

    /**
     * The module directory, derived from the build directory surefire passes in.
     *
     * <p>Not a hard-coded relative path: surefire's working directory is the module basedir, but a
     * scan that silently resolved its root to a directory that does not exist would report a clean
     * sweep having read nothing. The name is checked, so a wrong answer is loud.
     *
     * @return the {@code cometgui-process} directory
     */
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
                "the scan resolved its root to the wrong module, so it would be grading somebody"
                        + " else's tests or none at all");
        return module;
    }

    private static List<Path> scannedRoots() {
        Path module = moduleDirectory();
        return List.of(module.resolve(TEST_SOURCE_ROOT), module.resolve(FAKE_RESOURCE_ROOT));
    }

    /**
     * Reads every {@code .java} file under {@code roots} and reports every forbidden call.
     *
     * @param roots the directories to walk
     * @return what was found, and how much was read to find it
     */
    private static Scan scan(List<Path> roots) {
        List<Finding> findings = new ArrayList<>();
        Set<Path> filesRead = new LinkedHashSet<>();
        long linesRead = 0L;
        for (Path file : javaFilesUnder(roots)) {
            List<String> lines = linesOf(file);
            if (lines.isEmpty()) {
                continue;
            }
            filesRead.add(file);
            linesRead += lines.size();
            for (int index = 0; index < lines.size(); index++) {
                String text = lines.get(index);
                for (Forbidden forbidden : FORBIDDEN_FORMS) {
                    Matcher matcher = forbidden.pattern().matcher(text);
                    if (matcher.find()) {
                        findings.add(new Finding(file, index + 1, forbidden.rule(), text.strip()));
                        break;
                    }
                }
            }
        }
        return new Scan(List.copyOf(findings), Set.copyOf(filesRead), linesRead);
    }

    /**
     * Every {@code .java} file under {@code roots}, walked independently of {@link #scan}.
     *
     * @param roots the directories to walk
     * @return the files, in a stable order
     */
    private static Set<Path> javaFilesUnder(List<Path> roots) {
        Set<Path> files = new TreeSet<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walked = Files.walk(root)) {
                walked.filter(Files::isRegularFile)
                        .filter(path -> String.valueOf(path.getFileName()).endsWith(".java"))
                        .forEach(files::add);
            } catch (IOException unreadable) {
                throw new UncheckedIOException("could not walk " + root, unreadable);
            }
        }
        return files;
    }

    private static List<String> linesOf(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + file, unreadable);
        }
    }

    /**
     * The guard that refuses a clean sweep over nothing. Extracted so it can be falsified.
     *
     * @param scan what the scan saw
     * @param roots the roots it was pointed at
     */
    private static void requireTheWholeTreeWasRead(Scan scan, List<Path> roots) {
        Set<Path> onDisk = javaFilesUnder(roots);
        if (onDisk.isEmpty()) {
            fail(
                    "the no-fixed-sleep scan found no .java file at all under "
                            + roots
                            + ", so its clean result means nothing: it read 0 files and 0 lines");
        }
        if (onDisk.size() < FEWEST_SOURCES_EXPECTED) {
            fail(
                    "the no-fixed-sleep scan found only "
                            + onDisk.size()
                            + " .java files under "
                            + roots
                            + ", and this phase has at least "
                            + FEWEST_SOURCES_EXPECTED
                            + "; the roots are wrong or the tests have gone missing");
        }
        assertEquals(
                onDisk,
                scan.filesRead(),
                "the scan reported reading a different set of files from the ones on disk: it read "
                        + scan.filesRead().size()
                        + " of "
                        + onDisk.size());
        if (scan.linesRead() < FEWEST_LINES_EXPECTED) {
            fail(
                    "the no-fixed-sleep scan opened "
                            + scan.filesRead().size()
                            + " files but read only "
                            + scan.linesRead()
                            + " lines, and this phase has at least "
                            + FEWEST_LINES_EXPECTED
                            + "; a scan that reads nothing is clean about nothing");
        }
    }

    /**
     * Assembles a forbidden call without writing it literally, so this file stays clean.
     *
     * @param receiver the receiver, such as a thread class or a time unit
     * @param method the method
     * @return {@code <receiver>.<method>(50);}
     */
    private static String forbiddenCall(String receiver, String method) {
        return receiver + "." + method + "(50);";
    }

    /**
     * The same, reached through a static import: no receiver at all.
     *
     * @param method the method
     * @return {@code <method>(50);}
     */
    private static String forbiddenBareCall(String method) {
        return method + "(50);";
    }

    /**
     * The same call spaced out, which a naive substring search would miss.
     *
     * @param receiver the receiver
     * @param method the method
     * @return {@code <receiver> . <method> ( 50 );}
     */
    private static String forbiddenSpacedCall(String receiver, String method) {
        return receiver + " . " + method + " ( 50 );";
    }

    /**
     * Writes one synthetic Java file into a temporary directory.
     *
     * @param directory where to write it
     * @param name the file name, ending in {@code .java}
     * @param lines its lines, in order
     * @return the file
     * @throws IOException if it cannot be written
     */
    private static Path sourceFile(Path directory, String name, List<String> lines)
            throws IOException {
        Path file = directory.resolve(name);
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    private static List<String> rulesOf(Scan scan) {
        return scan.findings().stream().map(Finding::rule).toList();
    }

    private static List<Integer> lineNumbersOf(Scan scan) {
        return scan.findings().stream().map(Finding::line).toList();
    }

    private static List<String> renderedFindings(Scan scan) {
        return scan.findings().stream().map(Finding::toString).toList();
    }

    // ================================================== the real tree, scanned ==

    @Nested
    @DisplayName("this phase's own test sources")
    class TheScanReadsTheRealTree {

        @Test
        @DisplayName("gate item 6: not one fixed sleep anywhere under this module's src/test")
        void noTestInThisPhaseSynchronisesWithAFixedSleep() {
            List<Path> roots = scannedRoots();

            Scan scan = scan(roots);

            requireTheWholeTreeWasRead(scan, roots);
            assertEquals(
                    List.of(),
                    renderedFindings(scan),
                    "PHASE-03 exit gate item 6: a test synchronised by a fixed delay asserts that"
                            + " this machine is fast enough, not that the product is correct. Wait"
                            + " on the event instead -- a latch counted down by the thing you are"
                            + " waiting for, with a generous bound so a broken build fails instead"
                            + " of hanging.");
        }

        @Test
        @DisplayName("it read every .java file on disk, and it read their contents")
        void itReadEveryFileUnderTheRoots() {
            List<Path> roots = scannedRoots();

            Scan scan = scan(roots);

            assertEquals(javaFilesUnder(roots), scan.filesRead());
            assertTrue(
                    scan.filesRead().size() >= FEWEST_SOURCES_EXPECTED,
                    () ->
                            "only "
                                    + scan.filesRead().size()
                                    + " files were read, and this phase has at least "
                                    + FEWEST_SOURCES_EXPECTED);
            assertTrue(
                    scan.linesRead() >= FEWEST_LINES_EXPECTED,
                    () ->
                            "only "
                                    + scan.linesRead()
                                    + " lines were read, and this phase has at least "
                                    + FEWEST_LINES_EXPECTED);
        }

        @Test
        @DisplayName("named files, in both roots and in a sub-package, were among them")
        void theNamedFilesWereScanned() {
            Set<String> scanned = new TreeSet<>();
            scan(scannedRoots())
                    .filesRead()
                    .forEach(file -> scanned.add(String.valueOf(file.getFileName())));

            List<String> missing =
                    MUST_BE_SCANNED.stream().filter(name -> !scanned.contains(name)).toList();

            assertEquals(
                    List.of(),
                    missing,
                    () ->
                            "these files were not scanned, so the scan is not covering what it"
                                    + " claims to: the roots or the walk have changed. Scanned: "
                                    + scanned);
        }

        @Test
        @DisplayName("the scan is not exempt from itself")
        void theScanIncludesItself() {
            Path self =
                    moduleDirectory()
                            .resolve(TEST_SOURCE_ROOT)
                            .resolve("org/cometgui/tools/process")
                            .resolve(THIS_FILE);

            assertTrue(Files.isRegularFile(self), () -> self + " is not where it should be");
            assertTrue(
                    scan(scannedRoots()).filesRead().contains(self),
                    "a checker that excludes its own source can hide a defect in the one file"
                            + " nobody is allowed to look at");
        }

        @Test
        @DisplayName("the fakes resource directory holds the fake tool program and nothing else")
        void theFakesDirectoryHoldsOnlyTheProgram() throws IOException {
            Path fakes = moduleDirectory().resolve(FAKE_RESOURCE_ROOT);

            assertTrue(Files.isDirectory(fakes), () -> fakes + " is missing");
            try (Stream<Path> entries = Files.list(fakes)) {
                assertEquals(
                        List.of(FAKE_PROGRAM),
                        entries.map(entry -> String.valueOf(entry.getFileName())).sorted().toList(),
                        "the fakes are ONE program on purpose: a shell-script pair per scenario is"
                                + " two implementations of every fake, the Windows half of which"
                                + " would never run on the reference platform. Anything else that"
                                + " crept in here would also be a fake this scan cannot read.");
            }
        }
    }

    // ============================================================ it has teeth ==

    @Nested
    @DisplayName("the scan catches what it claims to")
    class TheScanHasTeeth {

        @Test
        @DisplayName("a pause inside a HELPER, not in a @Test body, is caught with its line number")
        void theIndirectFormIsCaught(@TempDir Path tmp) throws IOException {
            Path file =
                    sourceFile(
                            tmp,
                            "IndirectPauseTest.java",
                            List.of(
                                    "class IndirectPauseTest {",
                                    "    @Test",
                                    "    void theToolEventuallyWritesItsFile() {",
                                    "        settle();",
                                    "        assertTrue(theFileExists());",
                                    "    }",
                                    "",
                                    "    private static void settle() {",
                                    "        try {",
                                    "            " + forbiddenCall("Thread", "sleep"),
                                    "        } catch (InterruptedException ignored) {",
                                    "        }",
                                    "    }",
                                    "}"));

            Scan scan = scan(List.of(tmp));

            assertEquals(1, scan.findings().size(), () -> "found: " + renderedFindings(scan));
            assertEquals(List.of(10), lineNumbersOf(scan));
            assertEquals(List.of("Thread pause"), rulesOf(scan));
            assertEquals(file, scan.findings().get(0).file());
            assertEquals(
                    file + ":10: Thread pause: " + forbiddenCall("Thread", "sleep"),
                    renderedFindings(scan).get(0),
                    "a scan that only looked inside @Test methods would report this file clean,"
                            + " and that is the shape a tired agent produces");
        }

        @Test
        @DisplayName("every forbidden form is caught, each by its own rule and on its own line")
        void everyForbiddenFormIsCaught(@TempDir Path tmp) throws IOException {
            sourceFile(
                    tmp,
                    "EveryFormTest.java",
                    List.of(
                            "class EveryFormTest {",
                            "    void body() throws Exception {",
                            "        " + forbiddenCall("Thread", "sleep"),
                            "        " + forbiddenCall("TimeUnit.MILLISECONDS", "sleep"),
                            "        " + forbiddenCall("SECONDS", "sleep"),
                            "        " + forbiddenCall("unit", "sleep"),
                            "        " + forbiddenCall("LockSupport", "parkNanos"),
                            "        " + forbiddenCall("LockSupport", "park"),
                            "        " + forbiddenBareCall("parkNanos"),
                            "        " + forbiddenCall("monitor", "wait"),
                            "    }",
                            "}"));

            Scan scan = scan(List.of(tmp));

            assertEquals(
                    List.of(
                            "Thread pause",
                            "TimeUnit pause",
                            "TimeUnit pause",
                            "a pause through any receiver",
                            "LockSupport parking",
                            "LockSupport parking",
                            "bare parking",
                            "a monitor wait with a literal bound"),
                    rulesOf(scan),
                    () -> "found: " + renderedFindings(scan));
            assertEquals(List.of(3, 4, 5, 6, 7, 8, 9, 10), lineNumbersOf(scan));
        }

        @Test
        @DisplayName("a pause hidden by whitespace or a comment is still caught")
        void theEvasionsAreCaught(@TempDir Path tmp) throws IOException {
            sourceFile(
                    tmp,
                    "EvasiveTest.java",
                    List.of(
                            "class EvasiveTest {",
                            "    void body() throws Exception {",
                            "        " + forbiddenSpacedCall("Thread", "sleep"),
                            "        // " + forbiddenCall("Thread", "sleep"),
                            "    }",
                            "}"));

            Scan scan = scan(List.of(tmp));

            assertEquals(
                    List.of(3, 4), lineNumbersOf(scan), () -> renderedFindings(scan).toString());
            assertEquals(List.of("Thread pause", "Thread pause"), rulesOf(scan));
        }
    }

    // =================================================== it is not over-eager ==

    @Nested
    @DisplayName("the bounded waits this phase synchronises with stay legal")
    class TheScanIsNotOverEager {

        @Test
        @DisplayName("await, Future.get, onExit, waitForExit and a TimeoutException window pass")
        void theAllowedFormsAreClean(@TempDir Path tmp) throws IOException {
            sourceFile(
                    tmp,
                    "AllowedFormsTest.java",
                    List.of(
                            "class AllowedFormsTest {",
                            "    void body() throws Exception {",
                            "        assertTrue(latch.await(60, TimeUnit.SECONDS), \"bound\");",
                            "        assertTrue(arrived.await(60L, SECONDS));",
                            "        future.get(60, TimeUnit.SECONDS);",
                            "        handle.onExit().get(60, TimeUnit.SECONDS);",
                            "        assertEquals(0, started.waitForExit());",
                            "        assertThrows(",
                            "                TimeoutException.class,",
                            "                () -> exited.get(2, TimeUnit.SECONDS));",
                            "        process.waitFor();",
                            "        pump.join();",
                            "        awaitMarker(atLine100, \"its hundred-and-first line\");",
                            "    }",
                            "}"));

            Scan scan = scan(List.of(tmp));

            assertEquals(
                    List.of(),
                    renderedFindings(scan),
                    "a timeout that is a FAILURE BOUND on an event that really happens is not a"
                            + " fixed delay, and a scan that rejected it would push this phase's"
                            + " tests towards unbounded waits that hang a broken build for ever");
            assertEquals(1, scan.filesRead().size());
            assertEquals(15L, scan.linesRead());
        }
    }

    // ============================ the guard against a clean sweep over nothing ==

    @Nested
    @DisplayName("a scan that read nothing is not a pass")
    class TheEmptySweepGuard {

        @Test
        @DisplayName("an empty root: the scan is clean, and the guard rejects it anyway")
        void anEmptyRootIsRefused(@TempDir Path tmp) {
            Scan scan = scan(List.of(tmp));

            assertEquals(List.of(), scan.findings(), "there is nothing there to find");
            assertEquals(Set.of(), scan.filesRead());
            assertEquals(0L, scan.linesRead());

            AssertionError refused =
                    assertThrows(
                            AssertionError.class,
                            () -> requireTheWholeTreeWasRead(scan, List.of(tmp)));

            assertTrue(
                    String.valueOf(refused.getMessage()).contains("found no .java file at all"),
                    () -> "the refusal must say what it read: " + refused.getMessage());
            assertTrue(
                    String.valueOf(refused.getMessage()).contains("read 0 files and 0 lines"),
                    () -> "and it must name the counts: " + refused.getMessage());
        }

        @Test
        @DisplayName("a root with too few files: the guard names the count it wanted")
        void aThinRootIsRefused(@TempDir Path tmp) throws IOException {
            sourceFile(tmp, "LonelyTest.java", List.of("class LonelyTest {}"));
            Scan scan = scan(List.of(tmp));

            AssertionError refused =
                    assertThrows(
                            AssertionError.class,
                            () -> requireTheWholeTreeWasRead(scan, List.of(tmp)));

            assertTrue(
                    String.valueOf(refused.getMessage()).contains("found only 1 .java files"),
                    () -> refused.getMessage());
            assertTrue(
                    String.valueOf(refused.getMessage())
                            .contains("at least " + FEWEST_SOURCES_EXPECTED),
                    () -> refused.getMessage());
        }

        @Test
        @DisplayName("a root of empty files: opened is not read, and the guard says so")
        void filesThatWereNeverReadAreRefused(@TempDir Path tmp) throws IOException {
            for (int index = 0; index < FEWEST_SOURCES_EXPECTED; index++) {
                sourceFile(tmp, "Empty" + index + "Test.java", List.of());
            }
            Scan scan = scan(List.of(tmp));

            assertEquals(0L, scan.linesRead(), "nothing was read: there is nothing in them");
            assertEquals(Set.of(), scan.filesRead(), "and nothing counts as read");

            AssertionError refused =
                    assertThrows(
                            AssertionError.class,
                            () -> requireTheWholeTreeWasRead(scan, List.of(tmp)));

            assertFalse(
                    String.valueOf(refused.getMessage()).isBlank(),
                    "the guard must refuse a sweep over files it never read a line of");
        }
    }

    // ============================================================ the rule set ==

    @Nested
    @DisplayName("the rules themselves")
    class TheRuleSet {

        @Test
        @DisplayName("six rules, each named once and each with a stated reason")
        void everyRuleIsComplete() {
            assertEquals(6, FORBIDDEN_FORMS.size(), "the forbidden forms are the six documented");
            List<String> names = FORBIDDEN_FORMS.stream().map(Forbidden::rule).toList();

            assertEquals(
                    names.size(),
                    Set.copyOf(names).size(),
                    () -> "a duplicated rule name makes a failure ambiguous: " + names);
            for (Forbidden forbidden : FORBIDDEN_FORMS) {
                assertFalse(forbidden.rule().isBlank(), "a rule with no name cannot be reported");
                assertFalse(
                        forbidden.why().isBlank(),
                        () -> forbidden.rule() + " must say why it is forbidden");
            }
        }
    }
}
