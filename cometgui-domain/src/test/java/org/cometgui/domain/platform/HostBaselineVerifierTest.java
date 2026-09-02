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

package org.cometgui.domain.platform;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.cometgui.domain.testing.FakeEnvironmentReader;
import org.cometgui.domain.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link HostBaselineVerifier}.
 *
 * <p>Every outcome is asserted by its exact message as well as by its enum constant, because {@code
 * R-PLAT-03} makes the message the deliverable: an outcome the user cannot act on is the opaque
 * failure the requirement exists to forbid. Each message is checked to name the host's own value
 * and the required value.
 *
 * <p>None of these hosts is this machine. That is the point of {@code R-PROC-01}: the 32-bit host,
 * the host with glibc 2.17 and the host whose {@code os.name} is not set are all reachable here.
 */
class HostBaselineVerifierTest {

    private static final GlibcVersion REQUIRED = GlibcVersion.of(2, 28, 0);

    private static final GlibcVersionSource NEVER_CONSULTED =
            () -> {
                throw new AssertionError(
                        "the glibc source must not be consulted when glibc does not apply");
            };

    private static final GlibcVersionSource UNDETECTABLE = Optional::empty;

    private static GlibcVersionSource reporting(String version) {
        return () -> Optional.of(GlibcVersion.parse(version));
    }

    private static HostBaselineReport verify(
            FakeEnvironmentReader environment, GlibcVersionSource glibcVersions) {
        return new HostBaselineVerifier(environment, glibcVersions).verify(REQUIRED);
    }

    private static FakeEnvironmentReader host(String osName, String osArch, String dataModel) {
        return new FakeEnvironmentReader().withHost(osName, osArch, dataModel);
    }

    @Nested
    @DisplayName("a supported host")
    class Supported {

        @Test
        @DisplayName("64-bit Linux with a new enough glibc is supported, and says so with numbers")
        void linuxWithANewEnoughGlibcIsSupported() {
            HostBaselineReport report =
                    verify(host("Linux", "amd64", "64"), reporting("2.36-0ubuntu1"));

            assertAll(
                    () -> assertEquals(HostBaselineOutcome.SUPPORTED, report.outcome()),
                    () -> assertFalse(report.blocking()),
                    () ->
                            assertEquals(
                                    "Host baseline OK: 64-bit (sun.arch.data.model=64,"
                                            + " os.arch=amd64), glibc 2.36-0ubuntu1 meets the"
                                            + " required 2.28.0.",
                                    report.message()));
        }

        @Test
        @DisplayName("exactly the required glibc is enough: the floor is inclusive")
        void exactlyTheRequiredGlibcIsEnough() {
            HostBaselineReport report = verify(host("Linux", "amd64", "64"), reporting("2.28"));

            assertAll(
                    () -> assertEquals(HostBaselineOutcome.SUPPORTED, report.outcome()),
                    () -> assertTrue(report.message().contains("glibc 2.28 meets the required")));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {"Mac OS X", "Windows 11", "FreeBSD"})
        @DisplayName("glibc is not checked off Linux, and the source is not even consulted")
        void glibcIsNotCheckedOffLinux(String osName) {
            HostBaselineReport report = verify(host(osName, "aarch64", "64"), NEVER_CONSULTED);

            assertAll(
                    () -> assertEquals(HostBaselineOutcome.SUPPORTED, report.outcome()),
                    () ->
                            assertEquals(
                                    "Host baseline OK: 64-bit (sun.arch.data.model=64,"
                                            + " os.arch=aarch64). The glibc requirement does not"
                                            + " apply to "
                                            + osName
                                            + ".",
                                    report.message()));
        }

        @ParameterizedTest(name = "[{index}] os.arch={0}")
        @ValueSource(strings = {"amd64", "x86_64", "aarch64", "arm64", "ppc64le", "s390x", "AMD64"})
        @DisplayName("without a data model, a known 64-bit os.arch settles the word size")
        void osArchSettlesTheWordSizeWhenTheDataModelIsAbsent(String architecture) {
            HostBaselineReport report =
                    verify(host("Linux", architecture, null), reporting("2.36"));

            assertEquals(HostBaselineOutcome.SUPPORTED, report.outcome());
        }

        @Test
        @DisplayName("a data model with stray whitespace is still read")
        void aDataModelWithWhitespaceIsRead() {
            HostBaselineReport report = verify(host("Linux", "amd64", "  64  "), reporting("2.36"));

            assertEquals(HostBaselineOutcome.SUPPORTED, report.outcome());
        }
    }

    @Nested
    @DisplayName("a host that cannot run the tools")
    class Blocking {

        @Test
        @DisplayName("a 32-bit data model blocks, naming both host values")
        void aThirtyTwoBitDataModelBlocks() {
            HostBaselineReport report = verify(host("Linux", "i686", "32"), NEVER_CONSULTED);

            assertAll(
                    () -> assertEquals(HostBaselineOutcome.NOT_64_BIT, report.outcome()),
                    () -> assertTrue(report.blocking()),
                    () ->
                            assertEquals(
                                    "CometGUI requires a 64-bit operating system, but this host"
                                            + " reports a 32-bit Java runtime"
                                            + " (sun.arch.data.model=32, os.arch=i686). Every"
                                            + " managed Comet and Percolator build is 64-bit"
                                            + " only.",
                                    report.message()));
        }

        @ParameterizedTest(name = "[{index}] os.arch={0}")
        @ValueSource(strings = {"x86", "i386", "i686", "armv7l", "sparc"})
        @DisplayName("without a data model, a known 32-bit os.arch blocks and is named")
        void aKnownThirtyTwoBitArchitectureBlocks(String architecture) {
            HostBaselineReport report = verify(host("Linux", architecture, null), NEVER_CONSULTED);

            assertAll(
                    () -> assertEquals(HostBaselineOutcome.NOT_64_BIT, report.outcome()),
                    () ->
                            assertTrue(
                                    report.message()
                                            .contains(
                                                    "sun.arch.data.model=(not set), os.arch="
                                                            + architecture),
                                    report.message()));
        }

        @Test
        @DisplayName("a glibc older than required blocks, naming the host's and the requirement")
        void anOlderGlibcBlocks() {
            HostBaselineReport report = verify(host("Linux", "amd64", "64"), reporting("2.17"));

            assertAll(
                    () -> assertEquals(HostBaselineOutcome.GLIBC_TOO_OLD, report.outcome()),
                    () -> assertTrue(report.blocking()),
                    () ->
                            assertEquals(
                                    "This host has glibc 2.17, but the selected tools require"
                                            + " glibc 2.28.0 or newer. Select tool versions built"
                                            + " for an older glibc, or run CometGUI on a"
                                            + " distribution with glibc 2.28.0 or newer.",
                                    report.message()));
        }

        @Test
        @DisplayName("the diagnostic quotes the distribution's own version text")
        void theDiagnosticQuotesTheDistributionText() {
            HostBaselineReport report =
                    verify(host("Linux", "amd64", "64"), reporting("2.17-325.el7_9.3"));

            assertTrue(
                    report.message().startsWith("This host has glibc 2.17-325.el7_9.3, but"),
                    report.message());
        }
    }

    @Nested
    @DisplayName("a host that cannot be established")
    class Warnings {

        @Test
        @DisplayName("Linux with an unreadable glibc warns rather than blocking")
        void anUndeterminedGlibcWarns() {
            HostBaselineReport report = verify(host("Linux", "amd64", "64"), UNDETECTABLE);

            assertAll(
                    () -> assertEquals(HostBaselineOutcome.GLIBC_UNDETERMINED, report.outcome()),
                    () -> assertFalse(report.blocking()),
                    () ->
                            assertEquals(
                                    "This host is Linux but its glibc version could not be"
                                            + " determined; the selected tools require glibc"
                                            + " 2.28.0 or newer. Startup continues, and a tool"
                                            + " that will not load will report the missing symbol"
                                            + " version by name.",
                                    report.message()));
        }

        @Test
        @DisplayName("an unset os.name leaves the glibc question open, and says which property")
        void anUnsetOperatingSystemWarns() {
            HostBaselineReport report = verify(host(null, "amd64", "64"), UNDETECTABLE);

            assertAll(
                    () -> assertEquals(HostBaselineOutcome.GLIBC_UNDETERMINED, report.outcome()),
                    () ->
                            assertEquals(
                                    "The operating system could not be determined (os.name is not"
                                            + " set), so the selected tools' requirement of glibc"
                                            + " 2.28.0 or newer could not be checked. Startup"
                                            + " continues.",
                                    report.message()));
        }

        @Test
        @DisplayName("an unrecognised architecture warns, naming both properties as it found them")
        void anUnrecognisedArchitectureWarns() {
            HostBaselineReport report =
                    verify(host("Linux", "sparc64plus", "sixty-four"), reporting("2.36"));

            assertAll(
                    () ->
                            assertEquals(
                                    HostBaselineOutcome.ARCHITECTURE_UNDETERMINED,
                                    report.outcome()),
                    () -> assertFalse(report.blocking()),
                    () ->
                            assertEquals(
                                    "CometGUI requires a 64-bit operating system and could not"
                                            + " establish this host's word size"
                                            + " (sun.arch.data.model=sixty-four,"
                                            + " os.arch=sparc64plus). Startup continues; a tool"
                                            + " that cannot run here will report it when it is"
                                            + " probed.",
                                    report.message()));
        }

        @Test
        @DisplayName("with neither property set, the word size is undetermined")
        void withNoArchitecturePropertiesTheWordSizeIsUndetermined() {
            HostBaselineReport report = verify(host("Linux", null, null), reporting("2.36"));

            assertAll(
                    () ->
                            assertEquals(
                                    HostBaselineOutcome.ARCHITECTURE_UNDETERMINED,
                                    report.outcome()),
                    () ->
                            assertTrue(
                                    report.message()
                                            .contains(
                                                    "(sun.arch.data.model=(not set), os.arch=(not"
                                                            + " set))"),
                                    report.message()));
        }
    }

    @Nested
    @DisplayName("which outcome is reported when more than one applies")
    class Precedence {

        @Test
        @DisplayName("a 32-bit host is reported even when its glibc is also too old")
        void theBlockingArchitectureOutranksTheBlockingGlibc() {
            HostBaselineReport report = verify(host("Linux", "i686", "32"), reporting("2.17"));

            assertEquals(HostBaselineOutcome.NOT_64_BIT, report.outcome());
        }

        @Test
        @DisplayName("a blocking glibc outranks an undetermined architecture")
        void theBlockingGlibcOutranksTheArchitectureWarning() {
            HostBaselineReport report =
                    verify(host("Linux", "sparc64plus", null), reporting("2.17"));

            assertEquals(HostBaselineOutcome.GLIBC_TOO_OLD, report.outcome());
        }

        @Test
        @DisplayName("of two warnings the glibc one is reported: it is the actionable one")
        void theGlibcWarningOutranksTheArchitectureWarning() {
            HostBaselineReport report = verify(host("Linux", "sparc64plus", null), UNDETECTABLE);

            assertEquals(HostBaselineOutcome.GLIBC_UNDETERMINED, report.outcome());
        }
    }

    @Nested
    @DisplayName("its own arguments")
    class Arguments {

        @Test
        @DisplayName("both seams are required, by name")
        void bothSeamsAreRequired() {
            assertAll(
                    () ->
                            assertEquals(
                                    "environment",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new HostBaselineVerifier(
                                                                    null, UNDETECTABLE))
                                            .getMessage()),
                    () ->
                            assertEquals(
                                    "glibcVersions",
                                    assertThrows(
                                                    NullPointerException.class,
                                                    () ->
                                                            new HostBaselineVerifier(
                                                                    new FakeEnvironmentReader(),
                                                                    null))
                                            .getMessage()));
        }

        @Test
        @DisplayName("the requirement is required: there is no default glibc floor here")
        void theRequirementIsRequired() {
            HostBaselineVerifier verifier =
                    new HostBaselineVerifier(host("Linux", "amd64", "64"), UNDETECTABLE);

            GlibcVersion absent = Nulls.of(GlibcVersion.class);

            NullPointerException thrown =
                    assertThrows(NullPointerException.class, () -> verifier.verify(absent));

            assertEquals("requiredGlibcVersion", thrown.getMessage());
        }
    }
}
