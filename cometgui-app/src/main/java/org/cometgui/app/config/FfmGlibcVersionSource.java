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

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.platform.GlibcVersionSource;

/**
 * Reads the host's glibc version by calling {@code gnu_get_libc_version()} through the Java 25
 * foreign function and memory API.
 *
 * <h2>No subprocess, on purpose</h2>
 *
 * <p>The obvious probe is {@code ldd --version}. It is not available here: {@code R-PROC-02}
 * confines {@code ProcessBuilder} to {@code org.cometgui.tools.process}, an ArchUnit rule enforces
 * that, and the process service is phase 03's. Reading the symbol directly is also the better
 * answer -- it is the version of the C library this process is <em>linked against</em>, which is
 * the number that decides whether a managed tool loads, whereas {@code ldd} reports whatever {@code
 * ldd} finds on {@code PATH}.
 *
 * <h2>An answer this probe cannot give is an outcome, never a crash</h2>
 *
 * <p>{@link #detect()} returns {@link Optional#empty()} and never throws. Four things make it
 * empty: the symbol is absent (musl, macOS, Windows -- everything that is not glibc), the lookup or
 * the call itself fails, the function returns a null pointer, or the string it returns is not a
 * version {@link GlibcVersion} recognises. {@link GlibcVersionSource} documents empty as the
 * supported way to say "undetermined", the domain turns it into {@code GLIBC_UNDETERMINED}, and
 * that is a warning rather than a startup failure. An implementation that threw would convert every
 * non-glibc host into a crash.
 *
 * <h2>What can and cannot be tested on the build machine</h2>
 *
 * <p><strong>Only the glibc-present path can be exercised for real here.</strong> This machine is
 * Debian bookworm on x86-64 and its {@code gnu_get_libc_version} answers {@code 2.36}; there is no
 * musl, macOS or Windows host in this project's environment, and phase 01's handoff records the
 * same limitation for the headless JavaFX recipe. Rather than leave the other branches to a
 * comment, the lookup is a constructor parameter: {@link #FfmGlibcVersionSource(SymbolLookup)}
 * takes any {@link SymbolLookup}, so a test drives "the symbol is absent" with a lookup that finds
 * nothing, "the lookup failed" with one that throws, and "the symbol is null" with {@link
 * MemorySegment#NULL}. The one branch left untestable here is a real {@code gnu_get_libc_version}
 * that returns a null pointer, which no glibc does; it is a guard, and it is marked as one below.
 *
 * <h2>Native access</h2>
 *
 * <p>{@link Linker#downcallHandle} and {@link MemorySegment#reinterpret(long)} are restricted
 * methods. On JDK 25 a restricted call from the class path prints a warning unless the JVM is
 * started with {@code --enable-native-access=ALL-UNNAMED}; cometgui-app/pom.xml passes it to
 * surefire and phase 16's jpackage configuration must pass it to the packaged application. The
 * warning is cosmetic today and becomes an error in a later JDK, which is why it is dealt with now
 * rather than tolerated.
 */
public final class FfmGlibcVersionSource implements GlibcVersionSource {

    /** The glibc symbol that answers the version question, present only in the GNU C library. */
    public static final String SYMBOL_NAME = "gnu_get_libc_version";

    private final SymbolLookup lookup;

    /**
     * Creates a probe over the native linker's default lookup, which is the C library this process
     * is linked against.
     */
    public FfmGlibcVersionSource() {
        this(Linker.nativeLinker().defaultLookup());
    }

    /**
     * Creates a probe over a given symbol lookup.
     *
     * <p>This is the seam that makes the non-glibc outcomes testable on a glibc machine. It is
     * public rather than package-private because it is a genuine parameter -- a caller could point
     * it at a specific {@code libc} through {@link SymbolLookup#libraryLookup} -- not a hole opened
     * for a test.
     *
     * @param lookup where to look {@link #SYMBOL_NAME} up
     * @throws NullPointerException if {@code lookup} is {@code null}
     */
    public FfmGlibcVersionSource(SymbolLookup lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    /**
     * {@inheritDoc}
     *
     * <p>On this project's Linux build host this returns {@code 2.36}. On musl, macOS and Windows
     * the symbol does not exist and the result is empty.
     */
    @Override
    public Optional<GlibcVersion> detect() {
        try {
            Optional<MemorySegment> symbol = lookup.find(SYMBOL_NAME);
            if (symbol.isEmpty() || symbol.get().address() == 0L) {
                return Optional.empty();
            }
            MethodHandle call =
                    Linker.nativeLinker()
                            .downcallHandle(
                                    symbol.get(), FunctionDescriptor.of(ValueLayout.ADDRESS));
            MemorySegment version = (MemorySegment) call.invokeExact();
            if (version.address() == 0L) {
                // Guard, not a path: no glibc returns NULL here. Dereferencing a null segment
                // would kill the JVM with a signal rather than an exception, so it is checked
                // even though this project's machines cannot produce it.
                return Optional.empty();
            }
            return parseVersion(version.reinterpret(Long.MAX_VALUE).getString(0));
        } catch (Throwable failure) {
            /*
             * Throwable rather than Exception because MethodHandle.invokeExact is declared to
             * throw it, and because a foreign call that goes wrong can arrive as an Error
             * (IllegalCallerException when native access is denied, UnsatisfiedLinkError, a
             * WrongMethodTypeError). Every one of them means the same thing to the caller:
             * this host's glibc version could not be established. Swallowing it here is what
             * GlibcVersionSource asks for, and the domain reports it as a warning to the user.
             */
            return Optional.empty();
        }
    }

    /**
     * Parses what the symbol returned, treating anything unrecognised as "undetermined".
     *
     * <p>Separate and package-private so that the unparseable case is testable without a native
     * call: no real glibc returns a string {@link GlibcVersion} cannot read, so the only honest way
     * to exercise this branch is to call it directly.
     *
     * @param text what {@code gnu_get_libc_version} returned, for example {@code 2.36}
     * @return the parsed version, or empty when it is not a version this project recognises
     * @throws NullPointerException if {@code text} is {@code null}
     */
    static Optional<GlibcVersion> parseVersion(String text) {
        Objects.requireNonNull(text, "text");
        try {
            return Optional.of(GlibcVersion.parse(text));
        } catch (IllegalArgumentException notAVersion) {
            return Optional.empty();
        }
    }
}
