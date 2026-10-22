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

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The encodings a JVM runs with, and the assertion that its file-system encoding is UTF-8 (design
 * decision P14-9; {@code STATUS.rst}, <em>Risks currently live</em>, item 1).
 *
 * <h2>Why {@code sun.jnu.encoding}, and why fail rather than skip</h2>
 *
 * <p>{@code sun.jnu.encoding} is how the JDK turns a Java string into a file-system path. Where the
 * process environment names no UTF-8 locale -- no {@code LANG}, no {@code LC_ALL}, which is this
 * project's own build host -- it is {@code ANSI_X3.4-1968}, and {@code Path.of} on any path with a
 * non-ASCII character throws {@code InvalidPathException} before a line of CometGUI runs. A runner
 * in that state cannot exercise the non-ASCII paths the specification names, so an end-to-end run
 * there must go red, saying why. Skipping would report a green build in which the coverage a
 * scientist's machine needs never ran.
 *
 * <h2>The remedy, and the one that does not work</h2>
 *
 * <p>The environment, set before the JVM starts: {@code LC_ALL=C.UTF-8} (or {@code LANG}). Passing
 * {@code -Dsun.jnu.encoding=UTF-8} is <strong>inert</strong>: the JVM fixes the value from the
 * process locale before system properties are applied, so the flag is accepted without complaint
 * and changes nothing. {@code file.encoding} is UTF-8 by default since JDK 18 whatever the locale,
 * which is why it is reported but not what is asserted.
 *
 * <h2>For another process too</h2>
 *
 * <p>{@link #ofThisJvm()} reads this JVM; {@link #reportedBy(String, Map)} takes the same values as
 * another process reported them -- Tier B's packaged application, asked over its bridge -- so the
 * one assertion, with the one message, applies to both tiers.
 *
 * @param sunJnuEncoding {@code sun.jnu.encoding}, or {@code null} when not reported
 * @param fileEncoding {@code file.encoding}, or {@code null} when not reported
 * @param nativeEncoding {@code native.encoding}, or {@code null} when not reported
 * @param localeVariables {@code LANG}, {@code LC_ALL} and {@code LC_CTYPE} as the process sees
 *     them; a variable that is not set is absent from the map
 * @param source whose values these are, for the message: for example {@code "this test JVM"}
 */
public record RunnerLocale(
        String sunJnuEncoding,
        String fileEncoding,
        String nativeEncoding,
        Map<String, String> localeVariables,
        String source) {

    /** The property the assertion is about. */
    public static final String SUN_JNU_ENCODING = "sun.jnu.encoding";

    /** Reported beside it: the default charset for file contents. */
    public static final String FILE_ENCODING = "file.encoding";

    /** Reported beside it: what the operating system's locale says. */
    public static final String NATIVE_ENCODING = "native.encoding";

    /** The environment variables that decide the locale, in the order they are reported. */
    public static final List<String> LOCALE_VARIABLES = List.of("LC_ALL", "LC_CTYPE", "LANG");

    /**
     * The values, defensively copied.
     *
     * @throws NullPointerException if {@code localeVariables} or {@code source} is {@code null}
     */
    public RunnerLocale {
        Objects.requireNonNull(localeVariables, "localeVariables");
        Objects.requireNonNull(source, "source");
        localeVariables = Map.copyOf(localeVariables);
    }

    /**
     * This JVM's encodings and locale variables.
     *
     * @return what this JVM runs with
     */
    public static RunnerLocale ofThisJvm() {
        Map<String, String> variables = new LinkedHashMap<>();
        for (String name : LOCALE_VARIABLES) {
            String value = System.getenv(name);
            if (value != null) {
                variables.put(name, value);
            }
        }
        return new RunnerLocale(
                System.getProperty(SUN_JNU_ENCODING),
                System.getProperty(FILE_ENCODING),
                System.getProperty(NATIVE_ENCODING),
                variables,
                "this test JVM (pid " + ProcessHandle.current().pid() + ")");
    }

    /**
     * Another process's encodings, as it reported them.
     *
     * @param source the process, for the message: for example {@code "the packaged CometGUI (pid
     *     4242)"}
     * @param reported the values it reported, keyed by property name ({@value #SUN_JNU_ENCODING},
     *     {@value #FILE_ENCODING}, {@value #NATIVE_ENCODING}) and environment variable name ({@link
     *     #LOCALE_VARIABLES}); a missing key is a value that was not reported
     * @return the locale
     */
    public static RunnerLocale reportedBy(String source, Map<String, String> reported) {
        Objects.requireNonNull(reported, "reported");
        Map<String, String> variables = new LinkedHashMap<>();
        for (String name : LOCALE_VARIABLES) {
            if (reported.get(name) != null) {
                variables.put(name, reported.get(name));
            }
        }
        return new RunnerLocale(
                reported.get(SUN_JNU_ENCODING),
                reported.get(FILE_ENCODING),
                reported.get(NATIVE_ENCODING),
                variables,
                source);
    }

    /**
     * Whether {@code sun.jnu.encoding} names UTF-8, under any of its aliases.
     *
     * @return {@code true} if a non-ASCII path can be represented
     */
    public boolean isUtf8() {
        if (sunJnuEncoding == null || sunJnuEncoding.isBlank()) {
            return false;
        }
        try {
            return Charset.isSupported(sunJnuEncoding)
                    && Charset.forName(sunJnuEncoding).equals(StandardCharsets.UTF_8);
        } catch (IllegalCharsetNameException notACharsetName) {
            return false;
        }
    }

    /**
     * Fails unless {@code sun.jnu.encoding} is UTF-8.
     *
     * @throws AssertionError if it is not, with {@link #diagnostic()} as the message
     */
    public void assertUtf8() {
        if (!isUtf8()) {
            throw new AssertionError(diagnostic());
        }
    }

    /**
     * Every value, on one line: the three encodings and the three locale variables.
     *
     * @return for example {@code sun.jnu.encoding=ANSI_X3.4-1968, file.encoding=UTF-8, ...}
     */
    public String describe() {
        StringBuilder text = new StringBuilder();
        text.append(SUN_JNU_ENCODING).append('=').append(shown(sunJnuEncoding));
        text.append(", ").append(FILE_ENCODING).append('=').append(shown(fileEncoding));
        text.append(", ").append(NATIVE_ENCODING).append('=').append(shown(nativeEncoding));
        for (String name : LOCALE_VARIABLES) {
            String value = localeVariables.get(name);
            text.append(", ").append(name);
            text.append(value == null ? " unset" : "=\"" + value + "\"");
        }
        return text.toString();
    }

    /**
     * Why a run in this locale cannot pass, and what to do about it.
     *
     * @return the message {@link #assertUtf8()} fails with
     */
    public String diagnostic() {
        return "The end-to-end harness needs a UTF-8 runner, and "
                + source
                + " has "
                + SUN_JNU_ENCODING
                + "="
                + shown(sunJnuEncoding)
                + ", not UTF-8 ("
                + describe()
                + "). "
                + SUN_JNU_ENCODING
                + " is how the JDK turns a Java string into a file-system path, so every path"
                + " with a non-ASCII character fails with InvalidPathException before CometGUI"
                + " runs, and the non-ASCII coverage would be lost without a word. Remedy: export"
                + " LC_ALL=C.UTF-8 (or another UTF-8 locale) in the environment BEFORE the JVM"
                + " starts -- for Maven, before mvn, which passes it to the forked test JVM."
                + " -Dsun.jnu.encoding=UTF-8 does NOT work: the JVM sets "
                + SUN_JNU_ENCODING
                + " from the process locale before system properties apply, so the flag is"
                + " accepted and ignored. See STATUS.rst, Risks currently live, item 1.";
    }

    private static String shown(String value) {
        return value == null ? "(not reported)" : value;
    }
}
