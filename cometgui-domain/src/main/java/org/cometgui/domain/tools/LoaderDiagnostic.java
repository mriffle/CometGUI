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

package org.cometgui.domain.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code R-PLAT-03} diagnostic for a binary that will not load, as a value rather than a string
 * built at a call site.
 *
 * <p>{@code R-PLAT-03} requires a loader failure to be "a distinct, actionable diagnostic naming
 * the host's version, the required version, and the available alternatives", and never "an opaque
 * non-zero exit". A string assembled wherever the failure happened satisfies none of that reliably:
 * it cannot be tested without the failure, it cannot be re-rendered in another context, and the
 * second call site always words it differently. So the parts are held here, {@link #message()} is
 * pure logic over them, and the user interface renders what it returns.
 *
 * <p>Phase 05 unit 6 owns the classifier that produces one of these from real loader output. This
 * type owns the vocabulary and the wording -- including the wording when a version is missing,
 * which is the case that otherwise reaches a user as the word {@code null}.
 *
 * <p>Only a {@link ProbeStage#LOADABILITY} failure can be described by this type, and the
 * constructor enforces it. That is the same guard {@link ProbeFailureKind} carries, made
 * unavoidable: a diagnostic that said "this build cannot run on this host" over a capability
 * verdict would be exactly the confusion both exist to prevent.
 *
 * @param kind what went wrong; must be a loadability failure
 * @param objectName the shared object, DLL or executable the loader named -- {@code
 *     libboost_filesystem.so.1.83.0}, {@code libstdc++.so.6}, {@code VCRUNTIME140_1.dll}
 * @param requiredVersion the symbol or library version the binary demands, such as {@code
 *     GLIBC_2.38}; absent when the loader did not name one, as it does not for a missing shared
 *     object
 * @param availableVersion the newest version this host actually provides, such as {@code
 *     GLIBC_2.36}; absent when the host provides none at all
 * @param alternatives what the user can do instead, in the order they should be offered -- another
 *     tool version with a lower floor, a local binary, another machine. May be empty; empty is
 *     rendered as such rather than omitted, because "there is nothing else you can do" is itself
 *     information.
 */
public record LoaderDiagnostic(
        ProbeFailureKind kind,
        String objectName,
        Optional<String> requiredVersion,
        Optional<String> availableVersion,
        List<String> alternatives) {

    /*
     * One sentence per loadability failure, with %s for the object the loader named.
     *
     * The key set of this map IS the set of kinds a loader diagnostic accepts, so the constructor's
     * check and the wording can never disagree: a kind added to ProbeFailureKind as a loadability
     * failure and not given a sentence here is rejected by the constructor, and a sentence here for
     * a kind that is not a loadability failure is caught by this type's test. There is deliberately
     * no default arm and no fallback sentence: an unreachable branch is a mutation no test can
     * kill, and a fallback would let a missing sentence reach a user as a vague message.
     */
    private static final Map<ProbeFailureKind, String> SENTENCES =
            Map.of(
                    ProbeFailureKind.MISSING_SHARED_OBJECT,
                    "the dynamic loader could not find the shared library %s",
                    ProbeFailureKind.MISSING_SYMBOL_VERSION,
                    "%s on this host does not provide a symbol version this build needs",
                    ProbeFailureKind.WRONG_ARCHITECTURE,
                    "%s was built for a different processor architecture from the one this host"
                            + " runs",
                    ProbeFailureKind.MACOS_QUARANTINE,
                    "macOS refused to run %s because it is still marked as quarantined",
                    ProbeFailureKind.MISSING_WINDOWS_RUNTIME_DLL,
                    "%s is not installed on this host; it is part of the Microsoft Visual C++"
                            + " runtime, which this artefact does not ship",
                    ProbeFailureKind.NOT_EXECUTABLE,
                    "%s is not executable on this host",
                    ProbeFailureKind.TIMED_OUT,
                    "%s did not finish starting before the probe gave up waiting",
                    ProbeFailureKind.EXECUTION_FAILED,
                    "%s exited without starting, and its output matched no loader failure this"
                            + " project recognises");

    /** What {@link #message()} says where the loader named no required version. */
    static final String NO_REQUIRED_VERSION = "not named by the loader";

    /** What {@link #message()} says where the host provides no version of the object at all. */
    static final String NO_AVAILABLE_VERSION = "none found";

    /** What {@link #message()} says where no alternative was recorded. */
    static final String NO_ALTERNATIVES =
            "none known -- registering a local binary is the documented remedy";

    /**
     * Validates the diagnostic and takes a defensive, immutable copy of the alternatives.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if {@code kind} is not a loadability failure, if {@code
     *     objectName} is blank, if a present version is blank, or if an alternative is null or
     *     blank -- with a message naming the field and the rejected value
     */
    public LoaderDiagnostic {
        Objects.requireNonNull(kind, "kind");
        if (!SENTENCES.containsKey(kind)) {
            throw new IllegalArgumentException(
                    "a loader diagnostic can only describe a loadability failure, but "
                            + kind.name()
                            + " belongs to the "
                            + kind.stage().name()
                            + " stage");
        }
        objectName = requireNonBlank(objectName, "objectName");
        requiredVersion = nonBlankIfPresent(requiredVersion, "requiredVersion");
        availableVersion = nonBlankIfPresent(availableVersion, "availableVersion");
        alternatives = checkedAlternatives(alternatives);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    field
                            + " must not be blank: an R-PLAT-03 diagnostic has to name what the"
                            + " loader complained about");
        }
        return value.strip();
    }

    /*
     * Written with map rather than an isEmpty early return for the reason given on
     * MinimumHostRequirements: a method that returns an already-empty Optional is indistinguishable
     * from one mutated to return Optional.empty(), and a survivor no test can kill is worse than
     * one more small method.
     */
    private static Optional<String> nonBlankIfPresent(Optional<String> value, String field) {
        Objects.requireNonNull(value, field);
        return value.map(version -> nonBlankVersion(version, field));
    }

    private static String nonBlankVersion(String version, String field) {
        if (version.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank when it is present; leave it absent instead");
        }
        return version.strip();
    }

    private static List<String> checkedAlternatives(List<String> alternatives) {
        List<String> copy = new ArrayList<>(Objects.requireNonNull(alternatives, "alternatives"));
        for (int index = 0; index < copy.size(); index++) {
            String alternative = copy.get(index);
            if (alternative == null) {
                throw new IllegalArgumentException("alternatives[" + index + "] must not be null");
            }
            if (alternative.isBlank()) {
                throw new IllegalArgumentException("alternatives[" + index + "] must not be blank");
            }
            copy.set(index, alternative.strip());
        }
        return List.copyOf(copy);
    }

    /**
     * What the user can do instead.
     *
     * <p>Immutable, in the order they should be offered, and copied for the reason given on {@code
     * org.cometgui.domain.ports.ToolCommand#argv()}: nothing at a record accessor's call site shows
     * which kind of list it received, which is what SpotBugs reports as {@code EI_EXPOSE_REP}.
     *
     * @return the alternatives, immutable and possibly empty
     */
    public List<String> alternatives() {
        return List.copyOf(alternatives);
    }

    /**
     * The sentence the Tool Manager shows for this failure.
     *
     * <p>Names what failed, the version required, the version this host actually has, and the
     * alternatives -- the four things {@code R-PLAT-03} asks for. Every part has wording for its
     * absent case, so a diagnostic assembled from partial loader output still produces a usable
     * message and never the word {@code null}.
     *
     * <p>Pure logic over the record's own components: the same diagnostic always renders the same
     * string, on any machine and in any locale, which is what makes it assertable in a test.
     *
     * @return the rendered diagnostic, never {@code null} or blank
     */
    public String message() {
        return "This build cannot run on this host: "
                + String.format(Locale.ROOT, SENTENCES.get(kind), objectName)
                + ". Required: "
                + requiredVersion.orElse(NO_REQUIRED_VERSION)
                + ". Available on this host: "
                + availableVersion.orElse(NO_AVAILABLE_VERSION)
                + ". Alternatives: "
                + (alternatives.isEmpty() ? NO_ALTERNATIVES : String.join("; ", alternatives))
                + ".";
    }
}
