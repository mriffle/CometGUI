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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.LoaderDiagnostic;
import org.cometgui.domain.tools.ProbeFailureKind;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LoaderOutputClassifier}, written against the <strong>verbatim</strong> strings
 * this project's own Debian 12 host produced on 2026-09-02 from the real Percolator 3.09 Debian
 * payload -- both layers of it -- and from a real file without its executable bit.
 *
 * <p>The whole {@link LoaderDiagnostic#message()} is asserted, hand-typed, for every case. Pinning
 * the opening words is what let this phase's eleventh catalogued failure shape through: a guard
 * that fires correctly, whose arithmetic is mutation-killed, and whose message still misstates the
 * value it rejected. {@code R-PLAT-03}'s wording is named there as the next place it would happen,
 * so it is the wording that is pinned.
 */
class LoaderOutputClassifierTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);
    private static final HostPlatform MACOS =
            new HostPlatform(HostOperatingSystem.MACOS, HostArchitecture.AARCH64);
    private static final HostPlatform WINDOWS =
            new HostPlatform(HostOperatingSystem.WINDOWS, HostArchitecture.X86_64);

    /** This project's own host: glibc 2.36, GLIBCXX_3.4.30. */
    private static final HostRuntimeVersions DEBIAN_12 =
            new HostRuntimeVersions(
                    Optional.of(GlibcVersion.parse("2.36")),
                    Optional.of(GlibcVersion.parse("3.4.30")));

    private static final List<String> ALTERNATIVES =
            List.of("percolator 3.07.1 linux-x86-64", "percolator 3.06.5 linux-x86-64");

    private static final String ALTERNATIVES_TEXT =
            "percolator 3.07.1 linux-x86-64; percolator 3.06.5 linux-x86-64";

    /** The 3.09 payload as upstream publishes it, exit 127. Recorded verbatim. */
    private static final String MISSING_OBJECT_LINE =
            "scratch/phase05/extract/deb-3.09/usr/bin/percolator: error while loading shared"
                    + " libraries: libboost_filesystem.so.1.83.0: "
                    + "cannot open shared object file: No"
                    + " such file or directory";

    /** The same payload behind a stub libboost_filesystem, exit 1. Recorded verbatim, in order. */
    private static final List<String> SYMBOL_VERSION_LINES =
            List.of(
                    "scratch/phase05/extract/deb-3.09/usr/bin/percolator:"
                            + " /lib/x86_64-linux-gnu/libstdc++.so.6: version `GLIBCXX_3.4.32' not"
                            + " found (required by "
                            + "scratch/phase05/extract/deb-3.09/usr/bin/percolator)",
                    "scratch/phase05/extract/deb-3.09/usr/bin/percolator:"
                            + " /lib/x86_64-linux-gnu/libc.so.6: version `GLIBC_2.38' not found"
                            + " (required by scratch/phase05/extract/deb-3.09/usr/bin/percolator)");

    @Test
    @DisplayName("layer 1: the real missing shared object, whole message")
    void theMissingSharedObjectLayer() {
        LoaderDiagnostic diagnostic =
                classifier(LINUX).fromOutput(List.of(MISSING_OBJECT_LINE), context()).orElseThrow();

        assertAll(
                () -> assertEquals(ProbeFailureKind.MISSING_SHARED_OBJECT, diagnostic.kind()),
                () -> assertEquals("libboost_filesystem.so.1.83.0", diagnostic.objectName()),
                () ->
                        assertEquals(
                                "This build cannot run on this host: the dynamic loader could not"
                                        + " find the shared library libboost_filesystem.so.1.83.0."
                                        + " Required: not named by the "
                                        + "loader. Available on this host:"
                                        + " none found. Alternatives: "
                                        + "percolator 3.07.1 linux-x86-64;"
                                        + " percolator 3.06.5 linux-x86-64.",
                                diagnostic.message()));
    }

    @Test
    @DisplayName("layer 2: the real GLIBCXX symbol failure behind the stub, whole message")
    void theSymbolVersionLayer() {
        LoaderDiagnostic diagnostic =
                classifier(LINUX).fromOutput(SYMBOL_VERSION_LINES, context()).orElseThrow();

        assertAll(
                () -> assertEquals(ProbeFailureKind.MISSING_SYMBOL_VERSION, diagnostic.kind()),
                () ->
                        assertEquals(
                                "/lib/x86_64-linux-gnu/libstdc++.so.6",
                                diagnostic.objectName(),
                                "the C++ runtime line is reported by the loader FIRST, and a"
                                        + " classifier that took the glibc line would name the"
                                        + " wrong library"),
                () -> assertEquals(Optional.of("GLIBCXX_3.4.32"), diagnostic.requiredVersion()),
                () -> assertEquals(Optional.of("GLIBCXX_3.4.30"), diagnostic.availableVersion()),
                () ->
                        assertEquals(
                                "This build cannot run on this host:"
                                        + " /lib/x86_64-linux-gnu/libstdc++.so.6 "
                                        + "on this host does not"
                                        + " provide a symbol version this build needs. Required:"
                                        + " GLIBCXX_3.4.32. Available on this host: GLIBCXX_3.4.30."
                                        + " Alternatives: percolator "
                                        + "3.07.1 linux-x86-64; percolator"
                                        + " 3.06.5 linux-x86-64.",
                                diagnostic.message()));
    }

    @Test
    @DisplayName("the glibc line of the same failure, whole message, when it is the one seen")
    void theGlibcLineOfTheSameFailure() {
        LoaderDiagnostic diagnostic =
                classifier(LINUX)
                        .fromOutput(List.of(SYMBOL_VERSION_LINES.get(1)), context())
                        .orElseThrow();

        assertAll(
                () -> assertEquals("/lib/x86_64-linux-gnu/libc.so.6", diagnostic.objectName()),
                () ->
                        assertEquals(
                                "This build cannot run on this host:"
                                        + " /lib/x86_64-linux-gnu/libc.so.6 on this host does not"
                                        + " provide a symbol version this build needs. Required:"
                                        + " GLIBC_2.38. Available on this host: GLIBC_2.36."
                                        + " Alternatives: percolator "
                                        + "3.07.1 linux-x86-64; percolator"
                                        + " 3.06.5 linux-x86-64.",
                                diagnostic.message()));
    }

    @Test
    @DisplayName("a host whose own version was not established says so rather than inventing one")
    void anUnknownHostVersionIsNotInvented() {
        LoaderDiagnostic diagnostic =
                new LoaderOutputClassifier(LINUX, HostRuntimeVersions.unknown())
                        .fromOutput(SYMBOL_VERSION_LINES, context())
                        .orElseThrow();

        assertAll(
                () -> assertEquals(Optional.of("GLIBCXX_3.4.32"), diagnostic.requiredVersion()),
                () -> assertEquals(Optional.empty(), diagnostic.availableVersion()),
                () ->
                        assertTrue(
                                diagnostic.message().contains("Available on this host: none found"),
                                diagnostic.message()));
    }

    @Test
    @DisplayName("a required version in neither series leaves the host's version out of it")
    void anUnrecognisedVersionSeriesNamesNoHostVersion() {
        LoaderDiagnostic diagnostic =
                classifier(LINUX)
                        .fromOutput(
                                List.of(
                                        "percolator: /usr/lib/libfoo.so.1: version `FOO_1.2' not"
                                                + " found"),
                                context())
                        .orElseThrow();

        assertAll(
                () -> assertEquals(Optional.of("FOO_1.2"), diagnostic.requiredVersion()),
                () -> assertEquals(Optional.empty(), diagnostic.availableVersion()));
    }

    @Test
    @DisplayName("output that is not a loader failure is not classified as one")
    void ordinaryOutputIsNotALoaderFailure() {
        assertAll(
                () ->
                        assertEquals(
                                Optional.empty(),
                                classifier(LINUX)
                                        .fromOutput(
                                                List.of(
                                                        "Percolator version 3.07.1, Build Date Jun"
                                                                + " 20 2024 13:20:18",
                                                        "Error: too few arguments.",
                                                        "Invoke with -h option for help"),
                                                context())),
                () ->
                        assertEquals(
                                Optional.empty(),
                                classifier(LINUX).fromOutput(List.of(), context())));
    }

    @Test
    @DisplayName("a real unexecutable file: the start failure names the executable, whole message")
    void aFileWithoutItsExecutableBit() {
        LoaderDiagnostic diagnostic =
                classifier(LINUX)
                        .fromStartFailure(
                                "Cannot run program \"/tmp/noexec.bin\": Exec failed, error: 13"
                                        + " (Permission denied)",
                                context())
                        .orElseThrow();

        assertAll(
                () -> assertEquals(ProbeFailureKind.NOT_EXECUTABLE, diagnostic.kind()),
                () ->
                        assertEquals(
                                "This build cannot run on this host: percolator is not executable"
                                        + " on this host. Required: not "
                                        + "named by the loader. Available"
                                        + " on this host: none found. "
                                        + "Alternatives: percolator 3.07.1"
                                        + " linux-x86-64; percolator 3.06.5 linux-x86-64.",
                                diagnostic.message()));
    }

    @Test
    @DisplayName("the macOS quarantine rule is confined to macOS: the same text on Linux is not it")
    void theQuarantineRuleIsConfinedToMacOs() {
        String message = "Cannot run program \"/x/percolator\": error=1, Operation not permitted";

        assertAll(
                () ->
                        assertEquals(
                                ProbeFailureKind.MACOS_QUARANTINE,
                                classifier(MACOS)
                                        .fromStartFailure(message, context())
                                        .orElseThrow()
                                        .kind()),
                () ->
                        assertEquals(
                                Optional.empty(),
                                classifier(LINUX).fromStartFailure(message, context()),
                                "EPERM on Linux means something else entirely, and calling it a"
                                        + " Gatekeeper refusal would send the reader to a"
                                        + " preference pane that does not exist"),
                () ->
                        assertEquals(
                                Optional.empty(),
                                classifier(WINDOWS).fromStartFailure(message, context())));
    }

    @Test
    @DisplayName("the Windows runtime rule names the manifest's DLLs, because Windows does not")
    void theWindowsRuntimeRuleNamesTheDeclaredLibraries() {
        String message =
                "Cannot run program \"C:\\\\tools\\\\percolator.exe\": CreateProcess error=126,"
                        + " The specified module could not be found";

        LoaderDiagnostic four =
                classifier(WINDOWS)
                        .fromStartFailure(
                                message,
                                new ProbeContext(
                                        "percolator.exe",
                                        List.of(
                                                "MSVCP140.dll",
                                                "VCRUNTIME140.dll",
                                                "VCRUNTIME140_1.dll",
                                                "VCOMP140.DLL"),
                                        List.of()))
                        .orElseThrow();
        LoaderDiagnostic one =
                classifier(WINDOWS)
                        .fromStartFailure(
                                message,
                                new ProbeContext(
                                        "percolator.exe", List.of("VCOMP140.DLL"), List.of()))
                        .orElseThrow();

        assertAll(
                () -> assertEquals(ProbeFailureKind.MISSING_WINDOWS_RUNTIME_DLL, four.kind()),
                () ->
                        assertEquals(
                                "This build cannot run on this host: one of MSVCP140.dll,"
                                        + " VCRUNTIME140.dll, VCRUNTIME140_1.dll "
                                        + "or VCOMP140.DLL is not"
                                        + " installed on this host; it is "
                                        + "part of the Microsoft Visual"
                                        + " C++ runtime, which this artefact "
                                        + "does not ship. Required:"
                                        + " not named by the loader. Available on this host: none"
                                        + " found. Alternatives: none known -- registering a local"
                                        + " binary is the documented remedy.",
                                four.message()),
                () -> assertEquals("VCOMP140.DLL", one.objectName()),
                () ->
                        assertEquals(
                                Optional.empty(),
                                classifier(WINDOWS)
                                        .fromStartFailure(
                                                message,
                                                new ProbeContext(
                                                        "percolator.exe", List.of(), List.of())),
                                "with no library declared there is nothing to name, and naming the"
                                        + " executable would say the executable is part of the"
                                        + " Visual C++ runtime"),
                () ->
                        assertEquals(
                                Optional.empty(),
                                classifier(LINUX).fromStartFailure(message, context())));
    }

    @Test
    @DisplayName("Windows' own wording for a missing import names the DLL out of the message")
    void theWindowsDialogWordingNamesTheDll() {
        LoaderDiagnostic diagnostic =
                classifier(WINDOWS)
                        .fromOutput(
                                List.of(
                                        "The code execution cannot proceed because"
                                                + " VCRUNTIME140_1.dll was not found. Reinstalling"
                                                + " the program may fix this problem."),
                                context())
                        .orElseThrow();

        assertAll(
                () -> assertEquals(ProbeFailureKind.MISSING_WINDOWS_RUNTIME_DLL, diagnostic.kind()),
                () -> assertEquals("VCRUNTIME140_1.dll", diagnostic.objectName()),
                () ->
                        assertEquals(
                                Optional.empty(),
                                classifier(LINUX)
                                        .fromOutput(
                                                List.of(
                                                        "The code execution cannot proceed because"
                                                                + " VCRUNTIME140_1.dll "
                                                                + "was not found."),
                                                context())));
    }

    @Test
    @DisplayName("a failure decided without any loader text still renders a whole diagnostic")
    void aFailureWithNoLoaderText() {
        assertAll(
                () ->
                        assertEquals(
                                "This build cannot run on this host: percolator was built for a"
                                        + " different processor architecture from the one this host"
                                        + " runs. Required: not named by "
                                        + "the loader. Available on this"
                                        + " host: none found. Alternatives: percolator 3.07.1"
                                        + " linux-x86-64; percolator 3.06.5 linux-x86-64.",
                                classifier(LINUX)
                                        .of(ProbeFailureKind.WRONG_ARCHITECTURE, context())
                                        .message()),
                () ->
                        assertEquals(
                                "This build cannot run on this host: percolator did not finish"
                                        + " starting before the probe "
                                        + "gave up waiting. Required: not"
                                        + " named by the loader. Available "
                                        + "on this host: none found."
                                        + " Alternatives: percolator "
                                        + "3.07.1 linux-x86-64; percolator"
                                        + " 3.06.5 linux-x86-64.",
                                classifier(LINUX)
                                        .of(ProbeFailureKind.TIMED_OUT, context())
                                        .message()),
                () ->
                        assertEquals(
                                ProbeFailureKind.EXECUTION_FAILED,
                                classifier(LINUX)
                                        .of(ProbeFailureKind.EXECUTION_FAILED, context())
                                        .kind()),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        classifier(LINUX)
                                                .of(ProbeFailureKind.CAPABILITY_ABSENT, context()),
                                "a capability verdict is not a loader diagnostic"));
    }

    @Test
    @DisplayName("exactly three rules claim to have been seen, and the rest say NOT OBSERVED")
    void theEvidenceClaimsAreThePinnedOnes() {
        List<LoaderOutputClassifier.Rule> rules = new ArrayList<>();
        rules.addAll(LoaderOutputClassifier.OUTPUT_RULES);
        rules.addAll(LoaderOutputClassifier.START_FAILURE_RULES);
        Set<ProbeFailureKind> observed = EnumSet.noneOf(ProbeFailureKind.class);
        List<String> unobserved = new ArrayList<>();
        for (LoaderOutputClassifier.Rule rule : rules) {
            if (rule.observedByThisProject()) {
                observed.add(rule.kind());
            } else {
                unobserved.add(rule.kind() + ": " + rule.evidence());
            }
        }

        assertAll(
                () -> assertEquals(6, rules.size(), "every rule is graded, so the count is pinned"),
                () ->
                        assertEquals(
                                EnumSet.of(
                                        ProbeFailureKind.MISSING_SHARED_OBJECT,
                                        ProbeFailureKind.MISSING_SYMBOL_VERSION,
                                        ProbeFailureKind.NOT_EXECUTABLE),
                                observed,
                                "adding a kind here means somebody watched that text appear;"
                                        + " the work log is where they say when"),
                () ->
                        assertTrue(
                                unobserved.stream()
                                        .allMatch(claim -> claim.contains(": NOT OBSERVED.")),
                                unobserved.toString()),
                () ->
                        assertTrue(
                                rules.stream().allMatch(rule -> rule.kind().isLoadabilityFailure()),
                                "every kind this classifier can produce must be one"
                                        + " LoaderDiagnostic accepts"),
                () ->
                        assertTrue(
                                rules.stream().noneMatch(rule -> rule.evidence().isBlank()),
                                "a rule with no evidence sentence "
                                        + "is a rule nobody has to justify"));
    }

    @Test
    @DisplayName("the classifier rejects a null argument by name")
    void nullArgumentsAreRejectedByName() {
        LoaderOutputClassifier classifier = classifier(LINUX);
        assertAll(
                () ->
                        assertEquals(
                                "host",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LoaderOutputClassifier(
                                                                Nulls.of(HostPlatform.class),
                                                                DEBIAN_12))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "versions",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LoaderOutputClassifier(
                                                                LINUX,
                                                                Nulls.of(
                                                                        HostRuntimeVersions.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "lines",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        classifier.fromOutput(
                                                                Nulls.of(List.class), context()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "context",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        classifier.fromOutput(
                                                                List.of(),
                                                                Nulls.of(ProbeContext.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "message",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        classifier.fromStartFailure(
                                                                Nulls.of(String.class), context()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "context",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        classifier.fromStartFailure(
                                                                "x", Nulls.of(ProbeContext.class)))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "kind",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        classifier.of(
                                                                Nulls.of(ProbeFailureKind.class),
                                                                context()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "context",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        classifier.of(
                                                                ProbeFailureKind.TIMED_OUT,
                                                                Nulls.of(ProbeContext.class)))
                                        .getMessage()));
    }

    private static LoaderOutputClassifier classifier(HostPlatform host) {
        return new LoaderOutputClassifier(host, DEBIAN_12);
    }

    private static ProbeContext context() {
        return new ProbeContext("percolator", List.of(), ALTERNATIVES);
    }

    /** Guards the constant used in the expected messages above against a silent edit. */
    @Test
    @DisplayName("the alternatives text used in the expected messages is what joining produces")
    void theAlternativesTextIsTheJoinedList() {
        assertEquals(ALTERNATIVES_TEXT, String.join("; ", ALTERNATIVES));
    }
}
