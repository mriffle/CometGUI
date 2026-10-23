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

package org.cometgui.app.e2e.support;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The locale assertion (P14-9) fails on the encodings this project's own build host reports, with a
 * message that names them and the remedy; passes on UTF-8 under its aliases; and -- the check that
 * matters -- agrees with the JDK about the failure it exists to predict, in whatever locale this
 * test happens to run.
 */
class RunnerLocaleTest {

    /** What a JVM reports where no locale is exported, measured on this host (STATUS.rst). */
    private static Map<String, String> noLocale() {
        Map<String, String> reported = new HashMap<>();
        reported.put("sun.jnu.encoding", "ANSI_X3.4-1968");
        reported.put("file.encoding", "UTF-8");
        reported.put("native.encoding", "ANSI_X3.4-1968");
        return reported;
    }

    @Test
    @DisplayName(
            "the encodings of a JVM started with no locale fail, naming each value and the remedy,"
                    + " and calling the -D flag inert")
    void theHostDefaultFails() {
        RunnerLocale locale =
                RunnerLocale.reportedBy("the packaged CometGUI (pid 4242)", noLocale());
        AssertionError failed = assertThrows(AssertionError.class, locale::assertUtf8);
        assertAll(
                () -> assertFalse(locale.isUtf8()),
                () ->
                        assertEquals(
                                "The end-to-end harness needs a UTF-8 runner, and the packaged"
                                        + " CometGUI (pid 4242) has"
                                        + " sun.jnu.encoding=ANSI_X3.4-1968,"
                                        + " not UTF-8 (sun.jnu.encoding=ANSI_X3.4-1968,"
                                        + " file.encoding=UTF-8, native.encoding=ANSI_X3.4-1968,"
                                        + " LC_ALL unset, LC_CTYPE unset, LANG unset)."
                                        + " sun.jnu.encoding is how the JDK turns a Java string"
                                        + " into a file-system path, so every path with a"
                                        + " non-ASCII character fails with InvalidPathException"
                                        + " before CometGUI runs, and the non-ASCII coverage would"
                                        + " be lost without a word. Remedy: export LC_ALL=C.UTF-8"
                                        + " (or another UTF-8 locale) in the environment BEFORE the"
                                        + " JVM starts -- for Maven, before mvn, which passes it to"
                                        + " the forked test JVM. -Dsun.jnu.encoding=UTF-8 does NOT"
                                        + " work: the JVM sets sun.jnu.encoding from the process"
                                        + " locale before system properties apply, so the flag is"
                                        + " accepted and ignored. See STATUS.rst, Risks currently"
                                        + " live, item 1.",
                                failed.getMessage()));
    }

    @Test
    @DisplayName("the locale variables a process reports are named in the message, set or unset")
    void theLocaleVariablesAreReported() {
        Map<String, String> reported = noLocale();
        reported.put("LANG", "");
        reported.put("LC_CTYPE", "POSIX");
        RunnerLocale locale = RunnerLocale.reportedBy("a runner", reported);
        assertEquals(
                "sun.jnu.encoding=ANSI_X3.4-1968, file.encoding=UTF-8,"
                        + " native.encoding=ANSI_X3.4-1968, LC_ALL unset, LC_CTYPE=\"POSIX\","
                        + " LANG=\"\"",
                locale.describe());
    }

    @Test
    @DisplayName("UTF-8 passes, under the name and the alias the JDK accepts")
    void utf8Passes() {
        for (String name : new String[] {"UTF-8", "utf-8", "UTF8"}) {
            Map<String, String> reported = noLocale();
            reported.put("sun.jnu.encoding", name);
            reported.put("LC_ALL", "C.UTF-8");
            RunnerLocale locale = RunnerLocale.reportedBy("a runner", reported);
            assertTrue(locale.isUtf8(), name);
            assertDoesNotThrow(locale::assertUtf8, name);
        }
    }

    @Test
    @DisplayName("an unreported, blank, unknown or malformed encoding fails rather than passes")
    void anythingElseFails() {
        for (String name : new String[] {null, "", "  ", "ISO-8859-1", "not a charset!"}) {
            Map<String, String> reported = noLocale();
            reported.put("sun.jnu.encoding", name);
            RunnerLocale locale = RunnerLocale.reportedBy("a runner", reported);
            assertFalse(locale.isUtf8(), String.valueOf(name));
            assertThrows(AssertionError.class, locale::assertUtf8, String.valueOf(name));
        }
        assertTrue(
                RunnerLocale.reportedBy("a runner", Map.of())
                        .describe()
                        .startsWith(
                                "sun.jnu.encoding=(not reported), file.encoding=(not reported)"));
    }

    @Test
    @DisplayName(
            "in THIS JVM, the assertion passes exactly when the JDK can represent a non-ASCII path")
    void theAssertionPredictsTheRealFailure() {
        RunnerLocale here = RunnerLocale.ofThisJvm();
        boolean representable;
        try {
            Path.of(System.getProperty("java.io.tmpdir"), FreshHome.NON_ASCII_SEGMENT, "x.mzML");
            representable = true;
        } catch (InvalidPathException unmappable) {
            representable = false;
        }
        assertEquals(
                representable,
                here.isUtf8(),
                () -> "the JDK and the assertion disagree in " + here.describe());
        assertEquals(System.getProperty("sun.jnu.encoding"), here.sunJnuEncoding());
        assertEquals(System.getProperty("file.encoding"), here.fileEncoding());
        assertEquals(System.getProperty("native.encoding"), here.nativeEncoding());
        assertEquals(System.getenv("LC_ALL"), here.localeVariables().get("LC_ALL"));
        assertEquals(System.getenv("LANG"), here.localeVariables().get("LANG"));
    }
}
