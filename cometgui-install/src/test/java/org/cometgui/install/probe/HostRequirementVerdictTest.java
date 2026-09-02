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

import java.util.Optional;
import org.cometgui.install.probe.HostRequirementVerdict.Status;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HostRequirementVerdict}, whose whole job is to make one sentence
 * unrepresentable: "not runnable, and I will not say why".
 */
class HostRequirementVerdictTest {

    private static final String UNMET_RULE =
            "an UNMET verdict must name the field, the library, the required version and the"
                    + " version this host has: absence of information is never a refusal";

    @Test
    @DisplayName("a met verdict names nothing and is not a refusal")
    void met() {
        HostRequirementVerdict met = HostRequirementVerdict.met();

        assertAll(
                () -> assertEquals(Status.MET, met.status()),
                () -> assertFalse(met.isRefusal()),
                () -> assertEquals(Optional.empty(), met.field()),
                () -> assertEquals(Optional.empty(), met.objectName()),
                () -> assertEquals(Optional.empty(), met.requiredVersion()),
                () -> assertEquals(Optional.empty(), met.availableVersion()));
    }

    @Test
    @DisplayName("an unmet verdict is a refusal and carries all four parts of the reason")
    void unmet() {
        HostRequirementVerdict unmet =
                HostRequirementVerdict.unmet(
                        "minimumGlibc", "libc.so.6", "GLIBC_2.34", "GLIBC_2.33");

        assertAll(
                () -> assertEquals(Status.UNMET, unmet.status()),
                () -> assertTrue(unmet.isRefusal()),
                () -> assertEquals(Optional.of("minimumGlibc"), unmet.field()),
                () -> assertEquals(Optional.of("libc.so.6"), unmet.objectName()),
                () -> assertEquals(Optional.of("GLIBC_2.34"), unmet.requiredVersion()),
                () -> assertEquals(Optional.of("GLIBC_2.33"), unmet.availableVersion()));
    }

    @Test
    @DisplayName("an undetermined verdict names only the field, and is NOT a refusal")
    void undetermined() {
        HostRequirementVerdict undetermined = HostRequirementVerdict.undetermined("minimumGlibcxx");

        assertAll(
                () -> assertEquals(Status.UNDETERMINED, undetermined.status()),
                () ->
                        assertFalse(
                                undetermined.isRefusal(),
                                "R-PLAT-02 settles compatibility by executing the binary, so a"
                                        + " floor that could not be measured is never a refusal"),
                () -> assertEquals(Optional.of("minimumGlibcxx"), undetermined.field()),
                () -> assertEquals(Optional.empty(), undetermined.objectName()),
                () -> assertEquals(Optional.empty(), undetermined.requiredVersion()),
                () -> assertEquals(Optional.empty(), undetermined.availableVersion()));
    }

    @Test
    @DisplayName("a refusal missing any one of its four parts is rejected, one part at a time")
    void anUnnamedRefusalIsUnrepresentable() {
        assertAll(
                () -> assertEquals(UNMET_RULE, rejectionOfUnmet(false, true, true, true)),
                () -> assertEquals(UNMET_RULE, rejectionOfUnmet(true, false, true, true)),
                () -> assertEquals(UNMET_RULE, rejectionOfUnmet(true, true, false, true)),
                () -> assertEquals(UNMET_RULE, rejectionOfUnmet(true, true, true, false)));
    }

    private static String rejectionOfUnmet(
            boolean field, boolean object, boolean required, boolean available) {
        return assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new HostRequirementVerdict(
                                        Status.UNMET,
                                        present(field, "minimumGlibc"),
                                        present(object, "libc.so.6"),
                                        present(required, "GLIBC_2.34"),
                                        present(available, "GLIBC_2.33")))
                .getMessage();
    }

    private static Optional<String> present(boolean wanted, String value) {
        return wanted ? Optional.of(value) : Optional.empty();
    }

    @Test
    @DisplayName("a met verdict that names a field is rejected: there is nothing to name")
    void aMetVerdictNamesNothing() {
        assertEquals(
                "a MET verdict has no field to name, but named it",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new HostRequirementVerdict(
                                                Status.MET,
                                                Optional.of("minimumGlibc"),
                                                Optional.empty(),
                                                Optional.empty(),
                                                Optional.empty()))
                        .getMessage());
    }

    @Test
    @DisplayName("an undetermined verdict carrying a version it did not establish is rejected")
    void anUndeterminedVerdictEstablishesNothing() {
        String rule =
                "an UNDETERMINED verdict names the field it could not check and nothing else,"
                        + " because it established no versions";
        assertAll(
                () ->
                        assertEquals(
                                rule,
                                rejectionOfUndetermined(
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty()),
                                "it has to name the field"),
                () ->
                        assertEquals(
                                rule,
                                rejectionOfUndetermined(
                                        Optional.of("minimumGlibc"),
                                        Optional.of("libc.so.6"),
                                        Optional.empty(),
                                        Optional.empty())),
                () ->
                        assertEquals(
                                rule,
                                rejectionOfUndetermined(
                                        Optional.of("minimumGlibc"),
                                        Optional.empty(),
                                        Optional.of("GLIBC_2.34"),
                                        Optional.empty())),
                () ->
                        assertEquals(
                                rule,
                                rejectionOfUndetermined(
                                        Optional.of("minimumGlibc"),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of("GLIBC_2.33"))));
    }

    private static String rejectionOfUndetermined(
            Optional<String> field,
            Optional<String> object,
            Optional<String> required,
            Optional<String> available) {
        return assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new HostRequirementVerdict(
                                        Status.UNDETERMINED, field, object, required, available))
                .getMessage();
    }

    @Test
    @DisplayName("a verdict rejects a null part by name")
    void nullPartsAreRejectedByName() {
        Optional<String> absent = Nulls.of(Optional.class);
        assertAll(
                () ->
                        assertEquals(
                                "status",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new HostRequirementVerdict(
                                                                Nulls.of(Status.class),
                                                                Optional.empty(),
                                                                Optional.empty(),
                                                                Optional.empty(),
                                                                Optional.empty()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "field",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new HostRequirementVerdict(
                                                                Status.MET,
                                                                absent,
                                                                Optional.empty(),
                                                                Optional.empty(),
                                                                Optional.empty()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "objectName",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new HostRequirementVerdict(
                                                                Status.MET,
                                                                Optional.empty(),
                                                                absent,
                                                                Optional.empty(),
                                                                Optional.empty()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "requiredVersion",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new HostRequirementVerdict(
                                                                Status.MET,
                                                                Optional.empty(),
                                                                Optional.empty(),
                                                                absent,
                                                                Optional.empty()))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "availableVersion",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new HostRequirementVerdict(
                                                                Status.MET,
                                                                Optional.empty(),
                                                                Optional.empty(),
                                                                Optional.empty(),
                                                                absent))
                                        .getMessage()));
    }
}
