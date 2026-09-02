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

package org.cometgui.install.probe;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.LoaderDiagnostic;
import org.cometgui.domain.tools.ProbeFailureKind;

/**
 * Turns what a binary printed when it would not start into the {@code R-PLAT-03} diagnostic.
 *
 * <h2>Every rule says whether anyone has seen it fire</h2>
 *
 * <p>Tier 1's standing direction for this phase is that the classifier is written against observed
 * text, because "a classifier built from invented strings is a rule that has never seen its
 * subject". Two of this project's rules were produced on its own Debian 12 host from the real
 * Percolator 3.09 Debian payload, and one from a real unexecutable file; the Windows and macOS
 * rules were not, and {@link Rule#observedByThisProject()} says so for each. {@code
 * LoaderOutputClassifierTest} pins that set by hand, so a rule cannot quietly acquire an evidence
 * claim nobody earned -- the same honesty rule the artefact manifest's capability evidence carries.
 *
 * <h2>The available version comes from the host, not from the message</h2>
 *
 * <p>The loader names what a build <em>needs</em> and never what the host <em>has</em>. {@code
 * R-PLAT-03} asks for both, so the second half is read from {@link HostRuntimeVersions} -- glibc
 * through the domain's own {@code GlibcVersionSource}, {@code GLIBCXX} through {@link
 * HostCxxRuntime}. Where the host version was not established the diagnostic says so in {@link
 * LoaderDiagnostic}'s own words rather than inventing one.
 */
public final class LoaderOutputClassifier {

    /** Prefix of a C library symbol version, as the loader writes it. */
    static final String GLIBC_PREFIX = "GLIBC_";

    /** Prefix of a C++ runtime symbol version, as the loader writes it. */
    static final String GLIBCXX_PREFIX = "GLIBCXX_";

    /** What a rule names when the pattern itself did not capture a name. */
    enum Subject {

        /** The rule's own capture group. */
        MATCHED_GROUP,

        /** The executable being probed, from {@link ProbeContext#subject()}. */
        EXECUTABLE,

        /**
         * The host libraries the manifest declared. A rule using this does not apply when none was
         * declared: naming the executable instead would say a Visual C++ runtime DLL is missing
         * about a file that is not one.
         */
        DECLARED_HOST_LIBRARIES
    }

    /**
     * One way a loader failure is recognised.
     *
     * @param kind what the match means
     * @param pattern what to look for
     * @param subject where the name in the diagnostic comes from
     * @param objectGroup the capture group holding that name, used only with {@link
     *     Subject#MATCHED_GROUP}
     * @param versionGroup the capture group holding the required symbol version, or {@code 0} when
     *     the message names none
     * @param onlyOn the operating system this rule applies on, or empty for every one
     * @param observedByThisProject whether this project has actually seen this text produced
     * @param evidence where that text came from, in one sentence
     */
    record Rule(
            ProbeFailureKind kind,
            Pattern pattern,
            Subject subject,
            int objectGroup,
            int versionGroup,
            Optional<HostOperatingSystem> onlyOn,
            boolean observedByThisProject,
            String evidence) {}

    /**
     * Rules against a line the process printed.
     *
     * <p>Order is the order they are tried, and it matters only in that the first match wins; the
     * three patterns are disjoint on every text this project has seen.
     */
    static final List<Rule> OUTPUT_RULES =
            List.of(
                    new Rule(
                            ProbeFailureKind.MISSING_SHARED_OBJECT,
                            Pattern.compile(
                                    "error while loading shared libraries: ([^:]+): cannot open"
                                            + " shared object file"),
                            Subject.MATCHED_GROUP,
                            1,
                            0,
                            Optional.empty(),
                            true,
                            "executed on this project's Debian 12 host on 2026-09-02: the"
                                    + " Percolator 3.09 .deb payload as published, exit 127,"
                                    + " libboost_filesystem.so.1.83.0 named and not shipped"),
                    new Rule(
                            ProbeFailureKind.MISSING_SYMBOL_VERSION,
                            Pattern.compile("(\\S+): version `([^']+)' not found"),
                            Subject.MATCHED_GROUP,
                            1,
                            2,
                            Optional.empty(),
                            true,
                            "executed on this project's Debian 12 host on 2026-09-02: the same"
                                    + " payload behind a stub "
                                    + "libboost_filesystem, exit 1, reporting"
                                    + " GLIBCXX_3.4.32 and then GLIBC_2.38"),
                    new Rule(
                            ProbeFailureKind.MISSING_WINDOWS_RUNTIME_DLL,
                            Pattern.compile(
                                    "code execution cannot proceed because (\\S+\\.[Dd][Ll][Ll])"
                                            + " was not found"),
                            Subject.MATCHED_GROUP,
                            1,
                            0,
                            Optional.of(HostOperatingSystem.WINDOWS),
                            false,
                            "NOT OBSERVED. Windows' own wording for a missing import; no Windows"
                                    + " machine has run this project's probe. The manifest's"
                                    + " requiredHostLibraries is what "
                                    + "this failure is declared from"));

    /** Rules against the message of the failure to start the process at all. */
    static final List<Rule> START_FAILURE_RULES =
            List.of(
                    new Rule(
                            ProbeFailureKind.NOT_EXECUTABLE,
                            Pattern.compile("Permission denied"),
                            Subject.EXECUTABLE,
                            0,
                            0,
                            Optional.empty(),
                            true,
                            "executed on this project's Debian 12 host on 2026-09-02: a file"
                                    + " without its executable bit gives "
                                    + "java.io.IOException \"Cannot"
                                    + " run program ...: Exec failed, error: 13 (Permission"
                                    + " denied)\""),
                    new Rule(
                            ProbeFailureKind.MACOS_QUARANTINE,
                            Pattern.compile("Operation not permitted"),
                            Subject.EXECUTABLE,
                            0,
                            0,
                            Optional.of(HostOperatingSystem.MACOS),
                            false,
                            "NOT OBSERVED. macOS refuses a quarantined file with EPERM; no macOS"
                                    + " machine has ever run this project. The rule is confined to"
                                    + " macOS because EPERM on Linux "
                                    + "means something else entirely"),
                    new Rule(
                            ProbeFailureKind.MISSING_WINDOWS_RUNTIME_DLL,
                            Pattern.compile("The specified module could not be found|error=126"),
                            Subject.DECLARED_HOST_LIBRARIES,
                            0,
                            0,
                            Optional.of(HostOperatingSystem.WINDOWS),
                            false,
                            "NOT OBSERVED. CreateProcess error 126 is how Windows reports an"
                                    + " unresolvable import, and it does not say which one, so the"
                                    + " rule names the manifest's declared libraries instead"));

    private final HostPlatform host;
    private final HostRuntimeVersions versions;

    /**
     * Creates a classifier for one host.
     *
     * @param host the machine the probe is running on, which decides whether the macOS and Windows
     *     rules apply at all
     * @param versions what this host's C and C++ runtimes were established to be
     * @throws NullPointerException if either argument is {@code null}
     */
    public LoaderOutputClassifier(HostPlatform host, HostRuntimeVersions versions) {
        this.host = Objects.requireNonNull(host, "host");
        this.versions = Objects.requireNonNull(versions, "versions");
    }

    /**
     * Reads a loader failure out of what the process printed.
     *
     * @param lines everything the process wrote, standard error first
     * @param context what to name and what to offer instead
     * @return the diagnostic, or empty when nothing in the output is a loader failure this project
     *     recognises
     * @throws NullPointerException if either argument is {@code null}
     */
    public Optional<LoaderDiagnostic> fromOutput(List<String> lines, ProbeContext context) {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(context, "context");
        for (String line : lines) {
            Optional<LoaderDiagnostic> found = match(OUTPUT_RULES, line, context);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * Reads a loader failure out of the message of a process that would not start.
     *
     * @param message the message of the failure to start
     * @param context what to name and what to offer instead
     * @return the diagnostic, or empty when the message is not one this project recognises
     * @throws NullPointerException if either argument is {@code null}
     */
    public Optional<LoaderDiagnostic> fromStartFailure(String message, ProbeContext context) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(context, "context");
        return match(START_FAILURE_RULES, message, context);
    }

    /**
     * The diagnostic for a failure this project decided without reading any loader text -- a wrong
     * architecture read from the file's own header, a probe that timed out, an unexplained non-zero
     * exit.
     *
     * @param kind what happened; must be a loadability failure
     * @param context what to name and what to offer instead
     * @return the diagnostic
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code kind} is not a loadability failure
     */
    public LoaderDiagnostic of(ProbeFailureKind kind, ProbeContext context) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(context, "context");
        return new LoaderDiagnostic(
                kind,
                context.subject(),
                Optional.empty(),
                Optional.empty(),
                context.alternatives());
    }

    private Optional<LoaderDiagnostic> match(List<Rule> rules, String text, ProbeContext context) {
        for (Rule rule : rules) {
            if (!appliesHere(rule)) {
                continue;
            }
            Matcher matcher = rule.pattern().matcher(text);
            if (!matcher.find()) {
                continue;
            }
            Optional<String> objectName = objectName(rule, matcher, context);
            if (objectName.isEmpty()) {
                continue;
            }
            Optional<String> required = requiredVersion(rule, matcher);
            return Optional.of(
                    new LoaderDiagnostic(
                            rule.kind(),
                            objectName.get(),
                            required,
                            required.flatMap(this::availableOnThisHost),
                            context.alternatives()));
        }
        return Optional.empty();
    }

    private boolean appliesHere(Rule rule) {
        return rule.onlyOn().isEmpty() || rule.onlyOn().get() == host.operatingSystem();
    }

    private static Optional<String> objectName(Rule rule, Matcher matcher, ProbeContext context) {
        return switch (rule.subject()) {
            case MATCHED_GROUP -> Optional.of(matcher.group(rule.objectGroup()).strip());
            case EXECUTABLE -> Optional.of(context.subject());
            case DECLARED_HOST_LIBRARIES -> declaredLibraries(context.declaredHostLibraries());
        };
    }

    /*
     * A rule that names the manifest's libraries does not apply when the manifest declared none:
     * saying "percolator is part of the Microsoft Visual C++ runtime" about the executable itself
     * would be a false sentence produced by a correct rule, which is the shape this phase
     * catalogued as its eleventh.
     */
    private static Optional<String> declaredLibraries(List<String> libraries) {
        if (libraries.isEmpty()) {
            return Optional.empty();
        }
        if (libraries.size() == 1) {
            return Optional.of(libraries.get(0));
        }
        List<String> allButLast = libraries.subList(0, libraries.size() - 1);
        return Optional.of(
                "one of "
                        + String.join(", ", allButLast)
                        + " or "
                        + libraries.get(libraries.size() - 1));
    }

    private static Optional<String> requiredVersion(Rule rule, Matcher matcher) {
        return rule.versionGroup() == 0
                ? Optional.empty()
                : Optional.of(matcher.group(rule.versionGroup()).strip());
    }

    /*
     * The loader writes GLIBCXX_3.4.32 and GLIBC_2.38; the host's own versions are numbers.  The
     * prefix is put back so that the two halves of the sentence are directly comparable -- a user
     * reading "Required: GLIBCXX_3.4.32. Available on this host: GLIBCXX_3.4.30." can see the
     * difference without knowing which series is which.
     */
    private Optional<String> availableOnThisHost(String required) {
        if (required.startsWith(GLIBCXX_PREFIX)) {
            return versions.glibcxx().map(version -> GLIBCXX_PREFIX + version.text());
        }
        if (required.startsWith(GLIBC_PREFIX)) {
            return versions.glibc().map(LoaderOutputClassifier::glibcText);
        }
        return Optional.empty();
    }

    private static String glibcText(GlibcVersion version) {
        return GLIBC_PREFIX + version.text();
    }
}
