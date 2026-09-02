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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link LoaderDiagnostic}.
 *
 * <p>The three failures in {@link RealLoaderFailures} are not invented. They were produced by the
 * phase 05 orchestrator on this project's own Debian 12 host (glibc 2.36) from the Percolator 3.09
 * {@code .deb} payload, and are recorded verbatim in {@code handoffs/PHASE-05-worklog.rst}:
 *
 * <pre>
 * percolator: error while loading shared libraries: libboost_filesystem.so.1.83.0:
 *     cannot open shared object file: No such file or directory
 * /lib/x86_64-linux-gnu/libstdc++.so.6: version 'GLIBCXX_3.4.32' not found
 *     (required by .../percolator)
 * /lib/x86_64-linux-gnu/libc.so.6: version 'GLIBC_2.38' not found
 *     (required by .../percolator)
 * </pre>
 *
 * <p>Every expected message below is hand-typed. Building one by calling {@code message()} would be
 * an expected value computed by the code under test, and could not fail.
 */
class LoaderDiagnosticTest {

    @Nested
    @DisplayName("the three loader failures observed on this host")
    class RealLoaderFailures {

        @Test
        @DisplayName("a missing Boost shared library reads as an actionable sentence")
        void missingSharedObject() {
            LoaderDiagnostic diagnostic =
                    new LoaderDiagnostic(
                            ProbeFailureKind.MISSING_SHARED_OBJECT,
                            "libboost_filesystem.so.1.83.0",
                            Optional.empty(),
                            Optional.empty(),
                            List.of(
                                    "install Percolator 3.07.1, whose portable build needs no"
                                            + " Boost shared library",
                                    "install libboost-filesystem 1.83.0 with your system package"
                                            + " manager"));

            assertEquals(
                    "This build cannot run on this host: the dynamic loader could not find the"
                            + " shared library libboost_filesystem.so.1.83.0."
                            + " Required: not named by the loader."
                            + " Available on this host: none found."
                            + " Alternatives: install Percolator 3.07.1, whose portable build"
                            + " needs no Boost shared library; install libboost-filesystem 1.83.0"
                            + " with your system package manager.",
                    diagnostic.message());
        }

        @Test
        @DisplayName("a missing GLIBCXX symbol version names both versions")
        void missingGlibcxxSymbolVersion() {
            LoaderDiagnostic diagnostic =
                    new LoaderDiagnostic(
                            ProbeFailureKind.MISSING_SYMBOL_VERSION,
                            "libstdc++.so.6",
                            Optional.of("GLIBCXX_3.4.32"),
                            Optional.of("GLIBCXX_3.4.30"),
                            List.of(
                                    "install Percolator 3.07.1, which this host's C++ runtime"
                                            + " already satisfies"));

            assertEquals(
                    "This build cannot run on this host: libstdc++.so.6 on this host does not"
                            + " provide a symbol version this build needs."
                            + " Required: GLIBCXX_3.4.32."
                            + " Available on this host: GLIBCXX_3.4.30."
                            + " Alternatives: install Percolator 3.07.1, which this host's C++"
                            + " runtime already satisfies.",
                    diagnostic.message());
        }

        @Test
        @DisplayName("a missing GLIBC symbol version names both versions and both alternatives")
        void missingGlibcSymbolVersion() {
            LoaderDiagnostic diagnostic =
                    new LoaderDiagnostic(
                            ProbeFailureKind.MISSING_SYMBOL_VERSION,
                            "libc.so.6",
                            Optional.of("GLIBC_2.38"),
                            Optional.of("GLIBC_2.36"),
                            List.of(
                                    "install Percolator 3.07.1, which needs only GLIBC_2.34",
                                    "install Percolator 3.06.5, which needs only GLIBC_2.14"));

            assertEquals(
                    "This build cannot run on this host: libc.so.6 on this host does not provide"
                            + " a symbol version this build needs."
                            + " Required: GLIBC_2.38."
                            + " Available on this host: GLIBC_2.36."
                            + " Alternatives: install Percolator 3.07.1, which needs only"
                            + " GLIBC_2.34; install Percolator 3.06.5, which needs only"
                            + " GLIBC_2.14.",
                    diagnostic.message());
        }

        @Test
        @DisplayName("each of the three messages names what a user has to act on")
        void theThreeMessagesAreActionable() {
            LoaderDiagnostic boost =
                    new LoaderDiagnostic(
                            ProbeFailureKind.MISSING_SHARED_OBJECT,
                            "libboost_filesystem.so.1.83.0",
                            Optional.empty(),
                            Optional.empty(),
                            List.of("install Percolator 3.07.1"));
            LoaderDiagnostic glibcxx =
                    new LoaderDiagnostic(
                            ProbeFailureKind.MISSING_SYMBOL_VERSION,
                            "libstdc++.so.6",
                            Optional.of("GLIBCXX_3.4.32"),
                            Optional.of("GLIBCXX_3.4.30"),
                            List.of("install Percolator 3.07.1"));
            LoaderDiagnostic glibc =
                    new LoaderDiagnostic(
                            ProbeFailureKind.MISSING_SYMBOL_VERSION,
                            "libc.so.6",
                            Optional.of("GLIBC_2.38"),
                            Optional.of("GLIBC_2.36"),
                            List.of("install Percolator 3.06.5"));

            assertAll(
                    () ->
                            assertTrue(
                                    boost.message().contains("libboost_filesystem.so.1.83.0"),
                                    "the missing object is not named"),
                    () -> assertTrue(glibcxx.message().contains("GLIBCXX_3.4.32")),
                    () -> assertTrue(glibcxx.message().contains("GLIBCXX_3.4.30")),
                    () -> assertTrue(glibc.message().contains("GLIBC_2.38")),
                    () -> assertTrue(glibc.message().contains("GLIBC_2.36")),
                    () -> assertTrue(glibc.message().contains("install Percolator 3.06.5")));
        }
    }

    @Nested
    @DisplayName("the absent cases")
    class AbsentParts {

        @Test
        @DisplayName("a diagnostic with neither version still reads, and never says null")
        void noVersionsAtAll() {
            LoaderDiagnostic diagnostic =
                    new LoaderDiagnostic(
                            ProbeFailureKind.MISSING_WINDOWS_RUNTIME_DLL,
                            "VCRUNTIME140_1.dll",
                            Optional.empty(),
                            Optional.empty(),
                            List.of("install the Microsoft Visual C++ 2015-2022 redistributable"));

            String message = diagnostic.message();

            assertAll(
                    () ->
                            assertEquals(
                                    "This build cannot run on this host: VCRUNTIME140_1.dll is not"
                                            + " installed on this host; it is part of the"
                                            + " Microsoft Visual C++ runtime, which this artefact"
                                            + " does not ship."
                                            + " Required: not named by the loader."
                                            + " Available on this host: none found."
                                            + " Alternatives: install the Microsoft Visual C++"
                                            + " 2015-2022 redistributable.",
                                    message),
                    () -> assertFalse(message.contains("null"), message),
                    () -> assertFalse(message.contains("Optional"), message));
        }

        @Test
        @DisplayName("a diagnostic with a required version but no available one still reads")
        void noAvailableVersion() {
            LoaderDiagnostic diagnostic =
                    new LoaderDiagnostic(
                            ProbeFailureKind.MISSING_SYMBOL_VERSION,
                            "libc.so.6",
                            Optional.of("GLIBC_2.38"),
                            Optional.empty(),
                            List.of("install Percolator 3.06.5"));

            String message = diagnostic.message();

            assertAll(
                    () ->
                            assertEquals(
                                    "This build cannot run on this host: libc.so.6 on this host"
                                            + " does not provide a symbol version this build"
                                            + " needs."
                                            + " Required: GLIBC_2.38."
                                            + " Available on this host: none found."
                                            + " Alternatives: install Percolator 3.06.5.",
                                    message),
                    () -> assertFalse(message.contains("null"), message));
        }

        @Test
        @DisplayName("a diagnostic with an available version but no required one still reads")
        void noRequiredVersion() {
            LoaderDiagnostic diagnostic =
                    new LoaderDiagnostic(
                            ProbeFailureKind.MISSING_SYMBOL_VERSION,
                            "libc.so.6",
                            Optional.empty(),
                            Optional.of("GLIBC_2.36"),
                            List.of("install Percolator 3.06.5"));

            String message = diagnostic.message();

            assertAll(
                    () ->
                            assertEquals(
                                    "This build cannot run on this host: libc.so.6 on this host"
                                            + " does not provide a symbol version this build"
                                            + " needs."
                                            + " Required: not named by the loader."
                                            + " Available on this host: GLIBC_2.36."
                                            + " Alternatives: install Percolator 3.06.5.",
                                    message),
                    () -> assertFalse(message.contains("null"), message));
        }

        @Test
        @DisplayName("a diagnostic with no alternative says so rather than trailing off")
        void noAlternatives() {
            LoaderDiagnostic diagnostic =
                    new LoaderDiagnostic(
                            ProbeFailureKind.WRONG_ARCHITECTURE,
                            "percolator",
                            Optional.empty(),
                            Optional.empty(),
                            List.of());

            assertEquals(
                    "This build cannot run on this host: percolator was built for a different"
                            + " processor architecture from the one this host runs."
                            + " Required: not named by the loader."
                            + " Available on this host: none found."
                            + " Alternatives: none known -- registering a local binary is the"
                            + " documented remedy.",
                    diagnostic.message());
        }
    }

    @Nested
    @DisplayName("wording, one sentence per loadability failure")
    class Wording {

        @ParameterizedTest(name = "[{index}] {0}")
        @EnumSource(
                value = ProbeFailureKind.class,
                names = {
                    "MISSING_SHARED_OBJECT",
                    "MISSING_SYMBOL_VERSION",
                    "WRONG_ARCHITECTURE",
                    "MACOS_QUARANTINE",
                    "MISSING_WINDOWS_RUNTIME_DLL",
                    "NOT_EXECUTABLE",
                    "TIMED_OUT",
                    "EXECUTION_FAILED"
                })
        @DisplayName("every loadability failure has its own sentence, naming the object")
        void everyLoadabilityFailureHasASentence(ProbeFailureKind kind) {
            LoaderDiagnostic diagnostic =
                    new LoaderDiagnostic(
                            kind, "percolator", Optional.empty(), Optional.empty(), List.of());

            String message = diagnostic.message();

            assertAll(
                    () -> assertTrue(message.contains("percolator"), message),
                    () -> assertFalse(message.contains("%s"), message),
                    () -> assertFalse(message.contains("null"), message));
        }

        @Test
        @DisplayName("the quarantine and executable-bit sentences are the ones a Mac user needs")
        void theRemainingSentencesArePinned() {
            assertAll(
                    () ->
                            assertTrue(
                                    new LoaderDiagnostic(
                                                    ProbeFailureKind.MACOS_QUARANTINE,
                                                    "percolator",
                                                    Optional.empty(),
                                                    Optional.empty(),
                                                    List.of())
                                            .message()
                                            .contains(
                                                    "macOS refused to run percolator because it"
                                                            + " is still marked as quarantined")),
                    () ->
                            assertTrue(
                                    new LoaderDiagnostic(
                                                    ProbeFailureKind.NOT_EXECUTABLE,
                                                    "comet.linux.exe",
                                                    Optional.empty(),
                                                    Optional.empty(),
                                                    List.of())
                                            .message()
                                            .contains(
                                                    "comet.linux.exe is not executable on this"
                                                            + " host")),
                    () ->
                            assertTrue(
                                    new LoaderDiagnostic(
                                                    ProbeFailureKind.TIMED_OUT,
                                                    "percolator",
                                                    Optional.empty(),
                                                    Optional.empty(),
                                                    List.of())
                                            .message()
                                            .contains(
                                                    "percolator did not finish starting before"
                                                            + " the probe gave up waiting")),
                    () ->
                            assertTrue(
                                    new LoaderDiagnostic(
                                                    ProbeFailureKind.EXECUTION_FAILED,
                                                    "percolator",
                                                    Optional.empty(),
                                                    Optional.empty(),
                                                    List.of())
                                            .message()
                                            .contains(
                                                    "percolator exited without starting, and its"
                                                            + " output matched no loader failure"
                                                            + " this project recognises")));
        }

        @Test
        @DisplayName("no two failure kinds render the same sentence")
        void everySentenceIsDistinct() {
            List<String> messages = new ArrayList<>();
            for (ProbeFailureKind kind : ProbeFailureKind.values()) {
                if (kind.isLoadabilityFailure()) {
                    messages.add(
                            new LoaderDiagnostic(
                                            kind,
                                            "percolator",
                                            Optional.empty(),
                                            Optional.empty(),
                                            List.of())
                                    .message());
                }
            }

            assertEquals(8, messages.size());
            assertEquals(8, Set.copyOf(messages).size(), "two failure kinds read identically");
        }
    }

    @Nested
    @DisplayName("what a diagnostic refuses to describe")
    class Rejections {

        @ParameterizedTest(name = "[{index}] {0}")
        @EnumSource(
                value = ProbeFailureKind.class,
                names = {"UNPARSEABLE_VERSION", "CAPABILITY_ABSENT"})
        @DisplayName("a failure from a later stage cannot be dressed up as a loader failure")
        void aLaterStageFailureIsRejected(ProbeFailureKind kind) {
            IllegalArgumentException rejected =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new LoaderDiagnostic(
                                            kind,
                                            "percolator",
                                            Optional.empty(),
                                            Optional.empty(),
                                            List.of()));

            assertEquals(
                    "a loader diagnostic can only describe a loadability failure, but "
                            + kind.name()
                            + " belongs to the "
                            + kind.stage().name()
                            + " stage",
                    rejected.getMessage());
        }

        @Test
        @DisplayName("the kinds a diagnostic accepts are exactly the loadability failures")
        void theAcceptedKindsAreTheLoadabilityFailures() {
            Set<ProbeFailureKind> accepted = EnumSet.noneOf(ProbeFailureKind.class);
            Set<ProbeFailureKind> loadability = EnumSet.noneOf(ProbeFailureKind.class);
            for (ProbeFailureKind kind : ProbeFailureKind.values()) {
                if (kind.isLoadabilityFailure()) {
                    loadability.add(kind);
                }
                try {
                    new LoaderDiagnostic(
                            kind, "percolator", Optional.empty(), Optional.empty(), List.of());
                    accepted.add(kind);
                } catch (IllegalArgumentException expected) {
                    // this kind is not a loadability failure, which the assertion below checks
                }
            }

            assertEquals(loadability, accepted);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @ValueSource(strings = {"", " ", "\t\n"})
        @DisplayName("a blank object name is rejected, naming the field")
        void aBlankObjectNameIsRejected(String blank) {
            IllegalArgumentException rejected =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new LoaderDiagnostic(
                                            ProbeFailureKind.MISSING_SHARED_OBJECT,
                                            blank,
                                            Optional.empty(),
                                            Optional.empty(),
                                            List.of()));

            assertEquals(
                    "objectName must not be blank: an R-PLAT-03 diagnostic has to name what the"
                            + " loader complained about",
                    rejected.getMessage());
        }

        @Test
        @DisplayName("a present but blank version is rejected, naming which one")
        void aBlankVersionIsRejected() {
            assertAll(
                    () ->
                            assertEquals(
                                    "requiredVersion must not be blank when it is present; leave"
                                            + " it absent instead",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            new LoaderDiagnostic(
                                                                    ProbeFailureKind
                                                                            .MISSING_SYMBOL_VERSION,
                                                                    "libc.so.6",
                                                                    Optional.of("  "),
                                                                    Optional.empty(),
                                                                    List.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "availableVersion must not be blank when it is present; leave"
                                            + " it absent instead",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            new LoaderDiagnostic(
                                                                    ProbeFailureKind
                                                                            .MISSING_SYMBOL_VERSION,
                                                                    "libc.so.6",
                                                                    Optional.empty(),
                                                                    Optional.of(""),
                                                                    List.of()))
                                            .getMessage()));
        }

        @Test
        @DisplayName("a null or blank alternative is rejected, naming its position")
        void aBlankAlternativeIsRejected() {
            assertAll(
                    () ->
                            assertEquals(
                                    "alternatives[1] must not be blank",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            new LoaderDiagnostic(
                                                                    ProbeFailureKind
                                                                            .MISSING_SHARED_OBJECT,
                                                                    "libc.so.6",
                                                                    Optional.empty(),
                                                                    Optional.empty(),
                                                                    Arrays.asList("upgrade", " ")))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "alternatives[0] must not be null",
                                    assertThrows(
                                                    IllegalArgumentException.class,
                                                    () ->
                                                            new LoaderDiagnostic(
                                                                    ProbeFailureKind
                                                                            .MISSING_SHARED_OBJECT,
                                                                    "libc.so.6",
                                                                    Optional.empty(),
                                                                    Optional.empty(),
                                                                    Arrays.asList(null, "upgrade")))
                                            .getMessage()));
        }

        @Test
        @DisplayName("a null part is rejected by name")
        void nullPartsAreRejectedByName() {
            assertAll(
                    () ->
                            assertEquals(
                                    "kind",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new LoaderDiagnostic(
                                                                    Nulls.of(
                                                                            ProbeFailureKind.class),
                                                                    "libc.so.6",
                                                                    Optional.empty(),
                                                                    Optional.empty(),
                                                                    List.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "objectName",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new LoaderDiagnostic(
                                                                    ProbeFailureKind
                                                                            .MISSING_SHARED_OBJECT,
                                                                    Nulls.of(String.class),
                                                                    Optional.empty(),
                                                                    Optional.empty(),
                                                                    List.of()))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "alternatives",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new LoaderDiagnostic(
                                                                    ProbeFailureKind
                                                                            .MISSING_SHARED_OBJECT,
                                                                    "libc.so.6",
                                                                    Optional.empty(),
                                                                    Optional.empty(),
                                                                    Nulls.of(List.class)))
                                            .getMessage()));
        }
    }

    @Nested
    @DisplayName("value semantics")
    class ValueSemantics {

        @Test
        @DisplayName("the object name, versions and alternatives are stripped")
        void partsAreStripped() {
            LoaderDiagnostic diagnostic =
                    new LoaderDiagnostic(
                            ProbeFailureKind.MISSING_SYMBOL_VERSION,
                            "  libc.so.6 ",
                            Optional.of(" GLIBC_2.38 "),
                            Optional.of(" GLIBC_2.36\n"),
                            Arrays.asList(" install Percolator 3.06.5 "));

            assertAll(
                    () -> assertEquals("libc.so.6", diagnostic.objectName()),
                    () -> assertEquals("GLIBC_2.38", diagnostic.requiredVersion().orElseThrow()),
                    () -> assertEquals("GLIBC_2.36", diagnostic.availableVersion().orElseThrow()),
                    () ->
                            assertEquals(
                                    List.of("install Percolator 3.06.5"),
                                    diagnostic.alternatives()));
        }

        @Test
        @DisplayName("the alternatives handed out are a copy that cannot be modified")
        void alternativesAreCopied() {
            List<String> mutable = new ArrayList<>(List.of("install Percolator 3.06.5"));
            LoaderDiagnostic diagnostic =
                    new LoaderDiagnostic(
                            ProbeFailureKind.MISSING_SHARED_OBJECT,
                            "libc.so.6",
                            Optional.empty(),
                            Optional.empty(),
                            mutable);

            mutable.add("something the diagnostic never said");

            assertAll(
                    () ->
                            assertEquals(
                                    List.of("install Percolator 3.06.5"),
                                    diagnostic.alternatives()),
                    () ->
                            assertThrows(
                                    UnsupportedOperationException.class,
                                    () -> diagnostic.alternatives().add("x")));
        }
    }
}
