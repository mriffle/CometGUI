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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.platform.GlibcVersionSource;
import org.cometgui.domain.tools.LoaderDiagnostic;
import org.cometgui.domain.tools.ProbeFailureKind;
import org.cometgui.domain.tools.ProbeStage;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for the small value types this package is stated in. */
class ProbeValuesTest {

    @Test
    @DisplayName("an unknown host has neither runtime version and is a shared constant")
    void unknownHost() {
        assertAll(
                () -> assertEquals(Optional.empty(), HostRuntimeVersions.unknown().glibc()),
                () -> assertEquals(Optional.empty(), HostRuntimeVersions.unknown().glibcxx()),
                () ->
                        assertEquals(
                                new HostRuntimeVersions(Optional.empty(), Optional.empty()),
                                HostRuntimeVersions.unknown()));
    }

    @Test
    @DisplayName("host versions reject a null half by name")
    void hostVersionsRejectNulls() {
        Optional<GlibcVersion> absent = Nulls.of(Optional.class);
        assertAll(
                () ->
                        assertEquals(
                                "glibc",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new HostRuntimeVersions(
                                                                absent, Optional.empty()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "glibcxx",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new HostRuntimeVersions(
                                                                Optional.empty(), absent))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "glibcVersions",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        HostRuntimeVersions.detect(
                                                                Nulls.of(GlibcVersionSource.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("detect takes the C library from the domain's own source, not from anywhere else")
    void detectUsesTheGlibcSource() {
        GlibcVersionSource source = () -> Optional.of(GlibcVersion.parse("2.31-0ubuntu9.9"));

        HostRuntimeVersions detected = HostRuntimeVersions.detect(source);

        assertEquals("2.31-0ubuntu9.9", detected.glibc().orElseThrow().text());
    }

    @Test
    @DisplayName("a source that cannot determine the C library leaves that half absent")
    void detectKeepsAnUndeterminedGlibcAbsent() {
        HostRuntimeVersions detected = HostRuntimeVersions.detect(Optional::empty);

        assertEquals(Optional.empty(), detected.glibc());
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("a probe context with a blank subject is rejected: a diagnostic must name a thing")
    void aBlankSubjectIsRejected(String blank) {
        assertEquals(
                "subject must not be blank: a diagnostic has to name something the user can"
                        + " recognise",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new ProbeContext(blank, List.of(), List.of()))
                        .getMessage());
    }

    @Test
    @DisplayName("a probe context copies both lists, so a later change to the caller's is unseen")
    void probeContextCopiesItsLists() {
        List<String> libraries = new ArrayList<>(List.of("MSVCP140.dll"));
        List<String> alternatives = new ArrayList<>(List.of("percolator 3.06.5 linux-x86-64"));
        ProbeContext context = new ProbeContext("percolator", libraries, alternatives);

        libraries.add("VCOMP140.DLL");
        alternatives.add("something else");

        assertAll(
                () -> assertEquals(List.of("MSVCP140.dll"), context.declaredHostLibraries()),
                () ->
                        assertEquals(
                                List.of("percolator 3.06.5 linux-x86-64"), context.alternatives()),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> context.declaredHostLibraries().add("X.dll")),
                () ->
                        assertThrows(
                                UnsupportedOperationException.class,
                                () -> context.alternatives().add("x")));
    }

    @Test
    @DisplayName("a probe context rejects a null part by name")
    void probeContextRejectsNulls() {
        assertAll(
                () ->
                        assertEquals(
                                "subject",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ProbeContext(
                                                                Nulls.of(String.class),
                                                                List.of(),
                                                                List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "declaredHostLibraries",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ProbeContext(
                                                                "percolator",
                                                                Nulls.of(List.class),
                                                                List.of()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "alternatives",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new ProbeContext(
                                                                "percolator",
                                                                List.of(),
                                                                Nulls.of(List.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("a loadability result that carries no failure has started, and copies its streams")
    void loadabilityResultStarted() {
        List<String> out = new ArrayList<>(List.of("on stdout"));
        List<String> err = new ArrayList<>(List.of("on stderr"));
        LoadabilityResult result =
                new LoadabilityResult(Optional.empty(), out, err, OptionalInt.of(0));

        out.add("later");
        err.add("later");

        assertAll(
                () -> assertTrue(result.started()),
                () -> assertEquals(List.of("on stdout"), result.standardOutput()),
                () -> assertEquals(List.of("on stderr"), result.standardError()),
                () ->
                        assertEquals(
                                List.of("on stderr", "on stdout"),
                                result.output(),
                                "output() is standard error FIRST, which is where the loader and"
                                        + " every banner this project has measured arrive"),
                () -> assertEquals(OptionalInt.of(0), result.exitCode()));
    }

    @Test
    @DisplayName("a loadability result carrying a failure has not started")
    void loadabilityResultFailed() {
        LoadabilityResult result =
                new LoadabilityResult(
                        Optional.of(
                                new LoaderDiagnostic(
                                        ProbeFailureKind.TIMED_OUT,
                                        "percolator",
                                        Optional.empty(),
                                        Optional.empty(),
                                        List.of())),
                        List.of(),
                        List.of(),
                        OptionalInt.empty());

        assertFalse(result.started());
    }

    @Test
    @DisplayName("a loadability result rejects a null part by name")
    void loadabilityResultRejectsNulls() {
        assertAll(
                () ->
                        assertEquals(
                                "failure",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LoadabilityResult(
                                                                Nulls.of(Optional.class),
                                                                List.of(),
                                                                List.of(),
                                                                OptionalInt.empty()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "standardOutput",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LoadabilityResult(
                                                                Optional.empty(),
                                                                Nulls.of(List.class),
                                                                List.of(),
                                                                OptionalInt.empty()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "standardError",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LoadabilityResult(
                                                                Optional.empty(),
                                                                List.of(),
                                                                Nulls.of(List.class),
                                                                OptionalInt.empty()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "exitCode",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new LoadabilityResult(
                                                                Optional.empty(),
                                                                List.of(),
                                                                List.of(),
                                                                Nulls.of(OptionalInt.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("a probe failure carries its kind and the stage that kind belongs to")
    void probeFailureCarriesKindAndStage() {
        ProbeFailedException loader =
                new ProbeFailedException(ProbeFailureKind.MISSING_SHARED_OBJECT, "a message");
        ProbeFailedException identity =
                new ProbeFailedException(ProbeFailureKind.UNPARSEABLE_VERSION, "another");

        assertAll(
                () -> assertEquals(ProbeFailureKind.MISSING_SHARED_OBJECT, loader.kind()),
                () -> assertEquals(ProbeStage.LOADABILITY, loader.stage()),
                () -> assertEquals("a message", loader.getMessage()),
                () -> assertEquals(ProbeStage.IDENTITY, identity.stage()),
                () ->
                        assertEquals(
                                "kind",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> {
                                                    throw new ProbeFailedException(
                                                            Nulls.of(ProbeFailureKind.class), "m");
                                                })
                                        .getMessage()),
                () ->
                        assertEquals(
                                "message",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> {
                                                    throw new ProbeFailedException(
                                                            ProbeFailureKind.TIMED_OUT,
                                                            Nulls.of(String.class));
                                                })
                                        .getMessage()));
    }
}
