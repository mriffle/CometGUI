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

package org.cometgui.app.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.cometgui.domain.platform.GlibcVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The foreign-function glibc probe.
 *
 * <p><strong>What this machine can and cannot prove.</strong> The build host is Debian bookworm on
 * x86-64 with the GNU C library, so the success path is exercised for real against the actual
 * symbol -- and the value asserted is a real one, not "not null": glibc's major version has been
 * {@code 2} since 1997 and this host answers {@code 2.36}. The three not-glibc outcomes (musl,
 * macOS, Windows) cannot occur here at all, so they are driven through the {@link SymbolLookup}
 * constructor parameter instead, which is the same code path with a different place to look. The
 * one thing left unproved anywhere is a real {@code gnu_get_libc_version} returning a null pointer,
 * which no glibc does; the guard for it is marked as a guard in the source.
 *
 * <p>The exact version is deliberately <em>not</em> pinned to {@code 2.36}: that would make the
 * test a statement about one container image rather than about the probe. The floor asserted is
 * {@code 2.0}, which is what the work unit asked for, plus the structural facts -- major version,
 * and that the text the symbol returned re-parses to the same version.
 */
class FfmGlibcVersionSourceTest {

    @Test
    @DisplayName("on this host the probe reads a real glibc version of at least 2.0")
    void readsThisHostsGlibcVersion() {
        Optional<GlibcVersion> detected = new FfmGlibcVersionSource().detect();

        assertTrue(
                detected.isPresent(),
                "gnu_get_libc_version was not readable on this host; the build machine is"
                        + " Linux/glibc and this is the path the probe exists for");
        GlibcVersion version = detected.orElseThrow();
        assertAll(
                "glibc " + version.text(),
                () ->
                        assertTrue(
                                version.isAtLeast(GlibcVersion.of(2, 0, 0)),
                                "read " + version + ", which is below glibc 2.0"),
                () ->
                        assertEquals(
                                2, version.major(), "glibc's major version has been 2 since 1997"),
                () ->
                        assertTrue(
                                version.minor() >= 14,
                                "read "
                                        + version
                                        + ", which is below the 2.14 floor of the lowest managed"
                                        + " tool build; this host could run none of them"),
                () ->
                        assertEquals(
                                version,
                                GlibcVersion.parse(version.text()),
                                "the text the symbol returned must re-parse to the same version"),
                () ->
                        assertTrue(
                                version.text().startsWith(version.major() + "."),
                                "the reported text " + version.text() + " is not a version"));
    }

    @Test
    @DisplayName("it asks for gnu_get_libc_version by that exact name")
    void looksUpTheRightSymbol() {
        AtomicReference<String> asked = new AtomicReference<>();

        Optional<GlibcVersion> detected =
                new FfmGlibcVersionSource(
                                name -> {
                                    asked.set(name);
                                    return Optional.empty();
                                })
                        .detect();

        assertAll(
                () -> assertEquals("gnu_get_libc_version", asked.get()),
                () -> assertEquals("gnu_get_libc_version", FfmGlibcVersionSource.SYMBOL_NAME),
                () -> assertEquals(Optional.empty(), detected));
    }

    @Test
    @DisplayName(
            "a host without the symbol -- musl, macOS, Windows -- is undetermined, not a crash")
    void anAbsentSymbolIsUndetermined() {
        SymbolLookup findsNothing = name -> Optional.empty();

        assertEquals(Optional.empty(), new FfmGlibcVersionSource(findsNothing).detect());
    }

    @Test
    @DisplayName("a symbol that resolves to the null address is undetermined, not a segfault")
    void aNullSymbolIsUndetermined() {
        SymbolLookup findsNull = name -> Optional.of(MemorySegment.NULL);

        assertEquals(Optional.empty(), new FfmGlibcVersionSource(findsNull).detect());
    }

    @Test
    @DisplayName("a lookup that fails is undetermined: detect() never throws")
    void aFailingLookupIsUndetermined() {
        SymbolLookup explodes =
                name -> {
                    throw new UnsatisfiedLinkError("no C library here");
                };

        assertEquals(Optional.empty(), new FfmGlibcVersionSource(explodes).detect());
    }

    @Test
    @DisplayName("a version string glibc could never emit is undetermined rather than guessed at")
    void anUnparseableVersionIsUndetermined() {
        assertAll(
                () ->
                        assertEquals(
                                Optional.of(GlibcVersion.of(2, 36, 0)),
                                FfmGlibcVersionSource.parseVersion("2.36")),
                () ->
                        assertEquals(
                                Optional.of(GlibcVersion.of(2, 31, 0)),
                                FfmGlibcVersionSource.parseVersion("2.31-0ubuntu9.9"),
                                "a distribution suffix is part of the text, not of the version"),
                () -> assertEquals(Optional.empty(), FfmGlibcVersionSource.parseVersion("musl")),
                () -> assertEquals(Optional.empty(), FfmGlibcVersionSource.parseVersion("")),
                () ->
                        assertEquals(
                                "text",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> FfmGlibcVersionSource.parseVersion(null))
                                        .getMessage()));
    }

    @Test
    @DisplayName("a null lookup is rejected, naming the argument")
    void rejectsANullLookup() {
        assertEquals(
                "lookup",
                assertThrows(
                                NullPointerException.class,
                                () -> new FfmGlibcVersionSource((SymbolLookup) null))
                        .getMessage());
    }

    @Test
    @DisplayName("the version this host reported is written to the build directory as evidence")
    void recordsWhatItRead() throws IOException {
        GlibcVersion version = new FfmGlibcVersionSource().detect().orElseThrow();
        Path evidence = buildDirectory().resolve("glibc-probe.txt");

        String report =
                String.format(
                        Locale.ROOT,
                        "symbol %s text %s parsed %s%n",
                        FfmGlibcVersionSource.SYMBOL_NAME,
                        version.text(),
                        version);
        Files.writeString(evidence, report);

        assertEquals(
                report,
                Files.readString(evidence),
                "the evidence file must hold what this run measured, not a stale copy");
    }

    /** The module's target directory, which surefire passes in the parent POM. */
    private static Path buildDirectory() {
        String configured = System.getProperty("cometgui.buildDirectory");
        assertTrue(
                configured != null && !configured.isBlank(),
                "cometgui.buildDirectory is not set; surefire in the parent POM must pass it");
        return Path.of(configured);
    }
}
