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

package org.cometgui.domain.ports;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ToolCommand}.
 *
 * <p>Every assertion here is on a value, a message or a rejected input. The class is one of the
 * ones PIT is pointed at, and it is the type that stands between a filename a user chose and a
 * process this application starts, so "constructing it did not throw" would prove nothing worth
 * knowing.
 */
class ToolCommandTest {

    /**
     * Absolute, because {@code R-PROC-04} requires it, but built by resolving a relative name
     * rather than written as an absolute literal: an absolute literal is neither portable to the
     * Windows runner nor acceptable to SpotBugs.
     */
    private static final Path WORKING_DIRECTORY =
            Path.of("cometgui-test-runs", "run-1").toAbsolutePath();

    private static final List<String> ARGV = List.of("/opt/comet/comet", "-P", "comet.params");

    private static ToolCommand command(List<String> argv) {
        return new ToolCommand(argv, WORKING_DIRECTORY, Map.of());
    }

    private static Map<String, String> mutableEnvironment(String name, String value) {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put(name, value);
        return environment;
    }

    @Nested
    @DisplayName("what it keeps")
    class Values {

        @Test
        @DisplayName("keeps the argument array, the working directory and the environment")
        void keepsWhatItWasGiven() {
            ToolCommand toolCommand =
                    new ToolCommand(ARGV, WORKING_DIRECTORY, Map.of("OMP_NUM_THREADS", "8"));

            assertAll(
                    () -> assertEquals(ARGV, toolCommand.argv()),
                    () -> assertEquals(WORKING_DIRECTORY, toolCommand.workingDirectory()),
                    () -> assertEquals(Map.of("OMP_NUM_THREADS", "8"), toolCommand.environment()));
        }

        @Test
        @DisplayName("an empty environment is allowed and stays empty")
        void anEmptyEnvironmentIsAllowed() {
            assertEquals(Map.of(), command(ARGV).environment());
        }

        @Test
        @DisplayName("two commands with the same components are equal")
        void equalComponentsMeanEqualCommands() {
            ToolCommand first = new ToolCommand(ARGV, WORKING_DIRECTORY, Map.of("A", "1"));
            ToolCommand second =
                    new ToolCommand(new ArrayList<>(ARGV), WORKING_DIRECTORY, Map.of("A", "1"));

            assertAll(
                    () -> assertEquals(first, second),
                    () -> assertEquals(first.hashCode(), second.hashCode()),
                    () ->
                            assertNotEquals(
                                    first,
                                    new ToolCommand(
                                            List.of("/bin/true"),
                                            WORKING_DIRECTORY,
                                            Map.of("A", "1"))));
        }
    }

    @Nested
    @DisplayName("defensive copying")
    class Copying {

        @Test
        @DisplayName("mutating the caller's list afterwards does not change the command")
        void theArgumentListIsCopied() {
            List<String> callersArgv = new ArrayList<>(ARGV);
            ToolCommand toolCommand = command(callersArgv);

            callersArgv.add("--rm-rf");
            callersArgv.set(0, "/bin/sh");

            assertEquals(ARGV, toolCommand.argv());
        }

        @Test
        @DisplayName("mutating the caller's environment map afterwards does not change the command")
        void theEnvironmentMapIsCopied() {
            Map<String, String> callersEnvironment = mutableEnvironment("PATH", "/usr/bin");
            ToolCommand toolCommand = new ToolCommand(ARGV, WORKING_DIRECTORY, callersEnvironment);

            callersEnvironment.put("LD_PRELOAD", "/tmp/evil.so");

            assertEquals(Map.of("PATH", "/usr/bin"), toolCommand.environment());
        }

        @Test
        @DisplayName("the exposed argument list cannot be modified")
        void theExposedArgumentListIsUnmodifiable() {
            List<String> argv = command(ARGV).argv();

            assertAll(
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class, () -> argv.add("--extra")),
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> argv.set(0, "/bin/sh")),
                    () -> assertEquals(ARGV, argv));
        }

        @Test
        @DisplayName("the exposed environment map cannot be modified")
        void theExposedEnvironmentMapIsUnmodifiable() {
            Map<String, String> environment =
                    new ToolCommand(ARGV, WORKING_DIRECTORY, mutableEnvironment("PATH", "/usr/bin"))
                            .environment();

            assertAll(
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> environment.put("LD_PRELOAD", "/tmp/evil.so")),
                    () -> assertEquals(Map.of("PATH", "/usr/bin"), environment));
        }
    }

    @Nested
    @DisplayName("what it rejects")
    class Rejections {

        @Test
        @DisplayName("a null argument list is rejected by name")
        void rejectsNullArgv() {
            NullPointerException thrown =
                    assertThrows(NullPointerException.class, () -> command(null));

            assertEquals("argv", thrown.getMessage());
        }

        @Test
        @DisplayName("an empty argument list is rejected: there is no executable to run")
        void rejectsEmptyArgv() {
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> command(List.of()));

            assertEquals(
                    "argv must contain at least one element: the executable to run",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("a null argument is rejected with its index")
        void rejectsNullArgument() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> command(Arrays.asList("/opt/comet/comet", "-P", null)));

            assertEquals("argv[2] must not be null", thrown.getMessage());
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @ValueSource(strings = {"", " ", "\t", "\n"})
        @DisplayName("a blank argument is rejected with its index and its content")
        void rejectsBlankArgument(String blank) {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> command(Arrays.asList("/opt/comet/comet", blank)));

            assertEquals(
                    "argv[1] must not be blank, but was: \"" + blank + "\"", thrown.getMessage());
        }

        @Test
        @DisplayName("a null working directory is rejected by name")
        void rejectsNullWorkingDirectory() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () -> new ToolCommand(ARGV, null, Map.of()));

            assertEquals("workingDirectory", thrown.getMessage());
        }

        @Test
        @DisplayName("a relative working directory is rejected: R-PROC-04 wants an explicit one")
        void rejectsRelativeWorkingDirectory() {
            Path relative = Path.of("runs", "run-1");

            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new ToolCommand(ARGV, relative, Map.of()));

            assertEquals(
                    "workingDirectory must be absolute, but was: " + relative, thrown.getMessage());
        }

        @Test
        @DisplayName("a null environment is rejected by name")
        void rejectsNullEnvironment() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () -> new ToolCommand(ARGV, WORKING_DIRECTORY, null));

            assertEquals("environment", thrown.getMessage());
        }

        @Test
        @DisplayName("a null environment variable name is rejected")
        void rejectsNullEnvironmentName() {
            Map<String, String> environment = mutableEnvironment(null, "value");

            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new ToolCommand(ARGV, WORKING_DIRECTORY, environment));

            assertEquals("an environment variable name must not be null", thrown.getMessage());
        }

        @Test
        @DisplayName("a blank environment variable name is rejected with its content")
        void rejectsBlankEnvironmentName() {
            Map<String, String> environment = mutableEnvironment("  ", "value");

            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new ToolCommand(ARGV, WORKING_DIRECTORY, environment));

            assertEquals(
                    "an environment variable name must not be blank, but was: \"  \"",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("an environment variable name containing '=' is rejected with its content")
        void rejectsEnvironmentNameContainingEquals() {
            Map<String, String> environment = mutableEnvironment("PATH=/usr/bin", "");

            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new ToolCommand(ARGV, WORKING_DIRECTORY, environment));

            assertEquals(
                    "an environment variable name must not contain '=', but was:"
                            + " \"PATH=/usr/bin\"",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("a name that is entirely a '=' assignment is rejected too")
        void rejectsEnvironmentNameStartingWithEquals() {
            /*
             * The '=' at index 0 matters on its own: `indexOf('=') >= 0` and `indexOf('=') > 0`
             * differ only for this input, and the second would let "=value" through as a variable
             * name. PIT's ConditionalsBoundaryMutator makes exactly that change.
             */
            Map<String, String> environment = mutableEnvironment("=oops", "value");

            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new ToolCommand(ARGV, WORKING_DIRECTORY, environment));

            assertEquals(
                    "an environment variable name must not contain '=', but was: \"=oops\"",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("a null environment value is rejected with the variable's name")
        void rejectsNullEnvironmentValue() {
            Map<String, String> environment = mutableEnvironment("OMP_NUM_THREADS", null);

            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new ToolCommand(ARGV, WORKING_DIRECTORY, environment));

            assertEquals(
                    "the environment variable \"OMP_NUM_THREADS\" must not have a null value",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("an empty environment value is allowed: unset and empty are different")
        void allowsEmptyEnvironmentValue() {
            ToolCommand toolCommand =
                    new ToolCommand(ARGV, WORKING_DIRECTORY, Map.of("COMET_QUIET", ""));

            assertEquals("", toolCommand.environment().get("COMET_QUIET"));
        }
    }

    @Nested
    @DisplayName("displayString()")
    class Display {

        @Test
        @DisplayName("renders the argument array, quoted and comma-separated")
        void rendersTheArgumentArray() {
            assertEquals(
                    "[\"/opt/comet/comet\", \"-P\", \"comet.params\"]",
                    command(ARGV).displayString());
        }

        @Test
        @DisplayName("a single argument is rendered without a separator")
        void rendersASingleArgument() {
            assertEquals("[\"/bin/true\"]", command(List.of("/bin/true")).displayString());
        }

        @Test
        @DisplayName("a space inside an argument is kept, so one argument stays one argument")
        void keepsSpacesInsideArguments() {
            assertEquals(
                    "[\"/opt/comet/comet\", \"/data/my proteins.fasta\"]",
                    command(List.of("/opt/comet/comet", "/data/my proteins.fasta"))
                            .displayString());
        }

        @Test
        @DisplayName("quotes and backslashes are escaped")
        void escapesQuotesAndBackslashes() {
            assertEquals(
                    "[\"C:\\\\Program Files\\\\comet.exe\", \"say \\\"hello\\\"\"]",
                    command(List.of("C:\\Program Files\\comet.exe", "say \"hello\""))
                            .displayString());
        }

        @Test
        @DisplayName("tabs, newlines and other control characters cannot break the line")
        void escapesControlCharacters() {
            String rendered = command(List.of("/bin/echo", "a\tb\nc\rd\u0001e")).displayString();

            assertAll(
                    () -> assertEquals("[\"/bin/echo\", \"a\\tb\\nc\\rd\\u0001e\"]", rendered),
                    () ->
                            assertFalse(
                                    rendered.contains("\n"), "the rendering must stay on one line"),
                    () -> assertFalse(rendered.contains("\t"), "a raw tab would be re-parsed"));
        }

        @Test
        @DisplayName("shell metacharacters are shown but cannot escape their quotes")
        void shellMetacharactersAreInert() {
            List<String> dangerous =
                    List.of("/opt/comet/comet", "; rm -rf /", "$(whoami)", "`id`", "a|b&c>d");
            String rendered = command(dangerous).displayString();

            assertAll(
                    () ->
                            assertEquals(
                                    "[\"/opt/comet/comet\", \"; rm -rf /\", \"$(whoami)\","
                                            + " \"`id`\", \"a|b&c>d\"]",
                                    rendered),
                    () ->
                            assertNotEquals(
                                    String.join(" ", dangerous),
                                    rendered,
                                    "displayString must never be a runnable command line"),
                    () ->
                            assertTrue(
                                    rendered.startsWith("[\"") && rendered.endsWith("\"]"),
                                    "the rendering is an argument array, not a command"));
        }
    }

    @Nested
    @DisplayName("toString()")
    class Description {

        @Test
        @DisplayName("names the environment variables but never prints their values")
        void neverPrintsEnvironmentValues() {
            Map<String, String> environment = new LinkedHashMap<>();
            environment.put("LIMELIGHT_TOKEN", "s3cret-token-value");
            environment.put("OMP_NUM_THREADS", "8");

            String described = new ToolCommand(ARGV, WORKING_DIRECTORY, environment).toString();

            assertAll(
                    () ->
                            assertEquals(
                                    "ToolCommand[argv=[\"/opt/comet/comet\", \"-P\","
                                            + " \"comet.params\"], workingDirectory="
                                            + WORKING_DIRECTORY
                                            + ", environmentNames=[LIMELIGHT_TOKEN,"
                                            + " OMP_NUM_THREADS]]",
                                    described),
                    () ->
                            assertFalse(
                                    described.contains("s3cret-token-value"),
                                    "an environment value reached a description that goes to logs"),
                    () -> assertTrue(described.contains("LIMELIGHT_TOKEN")));
        }
    }
}
