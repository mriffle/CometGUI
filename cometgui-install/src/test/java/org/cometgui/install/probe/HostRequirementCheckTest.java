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

import java.util.List;
import java.util.Optional;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.tools.MinimumHostRequirements;
import org.cometgui.install.probe.HostRequirementVerdict.Status;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link HostRequirementCheck}.
 *
 * <p>The numbers are the real ones. Percolator 3.07.1 declares {@code GLIBC_2.34} and {@code
 * GLIBCXX_3.4.29}; the 3.09 Debian payload declares {@code GLIBC_2.38} and {@code GLIBCXX_3.4.32};
 * this project's own Debian 12 host provides {@code GLIBC_2.36} and {@code GLIBCXX_3.4.30}.
 *
 * <p><strong>Each floor is graded at the floor, one below and one above</strong>, because the
 * boundary is the whole question: a host with precisely {@code GLIBC_2.34} must be <em>offered</em>
 * Percolator 3.07.1, which is every RHEL 9, Ubuntu 22.04 and Debian 12 machine upstream built it
 * for. And each is graded on the axis the rule is silent about -- the <em>other</em> floor --
 * because a rule that reads only the C library is exactly the wrong answer this component exists to
 * prevent.
 */
class HostRequirementCheckTest {

    private static final GlibcVersion PERCOLATOR_3071_GLIBC = GlibcVersion.parse("2.34");
    private static final GlibcVersion PERCOLATOR_3071_GLIBCXX = GlibcVersion.parse("3.4.29");
    private static final GlibcVersion PAYLOAD_309_GLIBC = GlibcVersion.parse("2.38");
    private static final GlibcVersion PAYLOAD_309_GLIBCXX = GlibcVersion.parse("3.4.32");

    @ParameterizedTest(name = "[{index}] host glibc {0} against a floor of 2.34 -> {1}")
    @CsvSource({
        "2.33, UNMET",
        "2.34, MET",
        "2.35, MET",
    })
    @DisplayName("the C library floor is inclusive: exactly 2.34 is offered, 2.33 is not")
    void theGlibcBoundaryIsInclusive(String hostVersion, Status expected) {
        HostRequirementVerdict verdict =
                HostRequirementCheck.check(
                        glibcFloor(PERCOLATOR_3071_GLIBC),
                        new HostRuntimeVersions(
                                Optional.of(GlibcVersion.parse(hostVersion)), Optional.empty()));

        assertEquals(expected, verdict.status());
    }

    @ParameterizedTest(name = "[{index}] host GLIBCXX {0} against a floor of 3.4.29 -> {1}")
    @CsvSource({
        "3.4.28, UNMET",
        "3.4.29, MET",
        "3.4.30, MET",
    })
    @DisplayName("the C++ runtime floor is inclusive too, and is graded at its own boundary")
    void theGlibcxxBoundaryIsInclusive(String hostVersion, Status expected) {
        HostRequirementVerdict verdict =
                HostRequirementCheck.check(
                        glibcxxFloor(PERCOLATOR_3071_GLIBCXX),
                        new HostRuntimeVersions(
                                Optional.empty(), Optional.of(GlibcVersion.parse(hostVersion))));

        assertEquals(expected, verdict.status());
    }

    @Test
    @DisplayName("a refusal on the C library names the field, the library and BOTH versions")
    void aGlibcRefusalNamesBothVersions() {
        HostRequirementVerdict verdict =
                HostRequirementCheck.check(
                        glibcFloor(PERCOLATOR_3071_GLIBC),
                        new HostRuntimeVersions(
                                Optional.of(GlibcVersion.parse("2.33")), Optional.empty()));

        assertAll(
                () -> assertEquals(Status.UNMET, verdict.status()),
                () -> assertEquals(Optional.of("minimumGlibc"), verdict.field()),
                () -> assertEquals(Optional.of("libc.so.6"), verdict.objectName()),
                () -> assertEquals(Optional.of("GLIBC_2.34"), verdict.requiredVersion()),
                () -> assertEquals(Optional.of("GLIBC_2.33"), verdict.availableVersion()));
    }

    @Test
    @DisplayName("a refusal on the C++ runtime names its own field, library and both versions")
    void aGlibcxxRefusalNamesBothVersions() {
        HostRequirementVerdict verdict =
                HostRequirementCheck.check(
                        glibcxxFloor(PAYLOAD_309_GLIBCXX),
                        new HostRuntimeVersions(
                                Optional.empty(), Optional.of(GlibcVersion.parse("3.4.30"))));

        assertAll(
                () -> assertEquals(Status.UNMET, verdict.status()),
                () -> assertEquals(Optional.of("minimumGlibcxx"), verdict.field()),
                () -> assertEquals(Optional.of("libstdc++.so.6"), verdict.objectName()),
                () -> assertEquals(Optional.of("GLIBCXX_3.4.32"), verdict.requiredVersion()),
                () -> assertEquals(Optional.of("GLIBCXX_3.4.30"), verdict.availableVersion()));
    }

    @Test
    @DisplayName("the C++ floor is checked even when the C library floor is met -- the 3.09 case")
    void aMetGlibcFloorDoesNotHideAnUnmetCxxFloor() {
        MinimumHostRequirements bothFloors =
                new MinimumHostRequirements(
                        Optional.of(GlibcVersion.parse("2.34")),
                        Optional.of(PAYLOAD_309_GLIBCXX),
                        Optional.empty(),
                        List.of());

        HostRequirementVerdict verdict = HostRequirementCheck.check(bothFloors, debian12());

        assertAll(
                () ->
                        assertEquals(
                                Status.UNMET,
                                verdict.status(),
                                "glibc 2.36 clears the 2.34 floor, and a check that stopped there"
                                        + " would call the build runnable"),
                () -> assertEquals(Optional.of("minimumGlibcxx"), verdict.field()),
                () -> assertEquals(Optional.of("GLIBCXX_3.4.32"), verdict.requiredVersion()),
                () -> assertEquals(Optional.of("GLIBCXX_3.4.30"), verdict.availableVersion()));
    }

    @Test
    @DisplayName("both of the real 3.09 payload's floors are unmet here, and glibc is named first")
    void theRealPayloadIsRefusedOnTheFirstFloorDeclared() {
        MinimumHostRequirements payload =
                new MinimumHostRequirements(
                        Optional.of(PAYLOAD_309_GLIBC),
                        Optional.of(PAYLOAD_309_GLIBCXX),
                        Optional.empty(),
                        List.of());

        HostRequirementVerdict verdict = HostRequirementCheck.check(payload, debian12());

        assertAll(
                () -> assertEquals(Status.UNMET, verdict.status()),
                () -> assertEquals(Optional.of("minimumGlibc"), verdict.field()),
                () -> assertEquals(Optional.of("GLIBC_2.38"), verdict.requiredVersion()),
                () -> assertEquals(Optional.of("GLIBC_2.36"), verdict.availableVersion()));
    }

    @Test
    @DisplayName("an unknown host C library leaves the C++ refusal standing, and names it")
    void anUnknownGlibcDoesNotHideAKnownCxxRefusal() {
        MinimumHostRequirements bothFloors =
                new MinimumHostRequirements(
                        Optional.of(PAYLOAD_309_GLIBC),
                        Optional.of(PAYLOAD_309_GLIBCXX),
                        Optional.empty(),
                        List.of());

        HostRequirementVerdict verdict =
                HostRequirementCheck.check(
                        bothFloors,
                        new HostRuntimeVersions(
                                Optional.empty(), Optional.of(GlibcVersion.parse("3.4.30"))));

        assertAll(
                () ->
                        assertEquals(
                                Status.UNMET,
                                verdict.status(),
                                "a named unmet floor is returned ahead of an undetermined one,"
                                        + " because it is the answer a user can act on"),
                () -> assertEquals(Optional.of("minimumGlibcxx"), verdict.field()));
    }

    @Test
    @DisplayName("an artefact declaring nothing is met, on a host about which nothing is known")
    void nothingDeclaredIsMet() {
        assertAll(
                () ->
                        assertEquals(
                                Status.MET,
                                HostRequirementCheck.check(
                                                MinimumHostRequirements.none(),
                                                HostRuntimeVersions.unknown())
                                        .status()),
                () ->
                        assertEquals(
                                Status.MET,
                                HostRequirementCheck.check(
                                                MinimumHostRequirements.none(), debian12())
                                        .status()));
    }

    @Test
    @DisplayName("a declared floor this host cannot be measured against is UNDETERMINED, by field")
    void anUnmeasurableFloorIsUndetermined() {
        assertAll(
                () ->
                        assertEquals(
                                HostRequirementVerdict.undetermined("minimumGlibc"),
                                HostRequirementCheck.check(
                                        glibcFloor(PERCOLATOR_3071_GLIBC),
                                        HostRuntimeVersions.unknown())),
                () ->
                        assertEquals(
                                HostRequirementVerdict.undetermined("minimumGlibcxx"),
                                HostRequirementCheck.check(
                                        glibcxxFloor(PERCOLATOR_3071_GLIBCXX),
                                        HostRuntimeVersions.unknown())),
                () ->
                        assertEquals(
                                HostRequirementVerdict.undetermined("minimumMacOsVersion"),
                                HostRequirementCheck.check(
                                        new MinimumHostRequirements(
                                                Optional.empty(),
                                                Optional.empty(),
                                                Optional.of("12.7"),
                                                List.of()),
                                        debian12()),
                                "nothing in this project reads a macOS release version"),
                () ->
                        assertEquals(
                                HostRequirementVerdict.undetermined("requiredHostLibraries"),
                                HostRequirementCheck.check(
                                        new MinimumHostRequirements(
                                                Optional.empty(),
                                                Optional.empty(),
                                                Optional.empty(),
                                                List.of("MSVCP140.dll", "VCRUNTIME140.dll")),
                                        debian12()),
                                "no Windows machine has run this probe"));
    }

    @Test
    @DisplayName("a met floor beside an unmeasurable one is UNDETERMINED, never MET")
    void aMetFloorDoesNotApproveWhatWasNotChecked() {
        MinimumHostRequirements windowsRow =
                new MinimumHostRequirements(
                        Optional.of(PERCOLATOR_3071_GLIBC),
                        Optional.empty(),
                        Optional.empty(),
                        List.of("MSVCP140.dll"));

        assertEquals(
                HostRequirementVerdict.undetermined("requiredHostLibraries"),
                HostRequirementCheck.check(windowsRow, debian12()),
                "\"we checked what we could and it was fine\" is not \"it is fine\"");
    }

    @Test
    @DisplayName("the check rejects a null argument by name")
    void nullArgumentsAreRejectedByName() {
        assertAll(
                () ->
                        assertEquals(
                                "requirements",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        HostRequirementCheck.check(
                                                                Nulls.of(
                                                                        MinimumHostRequirements
                                                                                .class),
                                                                debian12()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "host",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        HostRequirementCheck.check(
                                                                MinimumHostRequirements.none(),
                                                                Nulls.of(
                                                                        HostRuntimeVersions.class)))
                                        .getMessage()));
    }

    @Test
    @DisplayName("the utility class cannot be instantiated, even by reflection")
    void theUtilityClassIsNotInstantiable() throws ReflectiveOperationException {
        var constructor = HostRequirementCheck.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertEquals(
                "HostRequirementCheck is a utility class and is never instantiated",
                assertThrows(
                                java.lang.reflect.InvocationTargetException.class,
                                constructor::newInstance)
                        .getCause()
                        .getMessage());
    }

    /** This project's own host: Debian 12, glibc 2.36, GLIBCXX_3.4.30. */
    private static HostRuntimeVersions debian12() {
        return new HostRuntimeVersions(
                Optional.of(GlibcVersion.parse("2.36")), Optional.of(GlibcVersion.parse("3.4.30")));
    }

    private static MinimumHostRequirements glibcFloor(GlibcVersion floor) {
        return new MinimumHostRequirements(
                Optional.of(floor), Optional.empty(), Optional.empty(), List.of());
    }

    private static MinimumHostRequirements glibcxxFloor(GlibcVersion floor) {
        return new MinimumHostRequirements(
                Optional.empty(), Optional.of(floor), Optional.empty(), List.of());
    }
}
