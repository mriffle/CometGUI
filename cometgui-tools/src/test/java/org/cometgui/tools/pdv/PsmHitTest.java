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

package org.cometgui.tools.pdv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** One hit's transcribed values: the rounding rule, the sign rule and the scratch round trip. */
class PsmHitTest {

    private static PsmHit hit(Optional<PsmHit.Terminal> nTerminal) {
        return new PsmHit(
                11367L,
                3,
                4,
                "controllerType=0 controllerNumber=1 scan=11367",
                Optional.of("4797.0"),
                "ACDMK",
                "R",
                "-",
                "AC[+57.021464]DM[+15.994915]K",
                "2-CHEMMOD:+57.021464,4-CHEMMOD:+15.994915",
                Map.of(4, "15.994915"),
                nTerminal);
    }

    @Test
    @DisplayName("a rounded mass agrees within half a unit of its last place, inclusive")
    void agreement() {
        assertAll(
                () -> assertTrue(PsmHit.agree("15.9949", "15.994915")),
                () -> assertTrue(PsmHit.agree("15.9949", "15.99495"), "exactly half a unit"),
                () -> assertTrue(PsmHit.agree("15.9950", "15.99495"), "exactly half a unit"),
                () -> assertFalse(PsmHit.agree("15.9949", "15.994951")),
                () -> assertFalse(PsmHit.agree("15.9948", "15.994915")),
                () -> assertTrue(PsmHit.agree("16", "15.5")),
                () -> assertFalse(PsmHit.agree("16", "15.499999")),
                () -> assertTrue(PsmHit.agree("-17.0265", "-17.026549")),
                () -> assertFalse(PsmHit.agree("17.0265", "-17.026549")));
    }

    @Test
    @DisplayName("a sign is added only to a delta that has none")
    void signs() {
        assertAll(
                () -> assertEquals("+57.021464", PsmHit.signed("57.021464")),
                () -> assertEquals("+57.021464", PsmHit.signed("+57.021464")),
                () -> assertEquals("-17.026549", PsmHit.signed("-17.026549")));
    }

    @Test
    @DisplayName("a hit reads back from the scratch record exactly as written")
    void roundTrip() throws IOException {
        for (PsmHit hit :
                new PsmHit[] {
                    hit(Optional.empty()),
                    hit(Optional.of(new PsmHit.Terminal("42.010565", true))),
                    hit(Optional.of(new PsmHit.Terminal("229.162932", false)))
                }) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                hit.write(out);
            }
            PsmHit back =
                    PsmHit.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            assertEquals(hit, back);
        }
        PsmHit noTime =
                new PsmHit(
                        1L,
                        2,
                        1,
                        "n",
                        Optional.empty(),
                        "K",
                        "-",
                        "-",
                        "K",
                        "null",
                        Map.of(),
                        Optional.empty());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            noTime.write(out);
        }
        assertEquals(
                noTime,
                PsmHit.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }

    @Test
    @DisplayName("Percolator's peptide is compared as values: flanks, residues, masses, termini")
    void mismatch() {
        PsmHit plain = hit(Optional.empty());
        PsmHit variable = hit(Optional.of(new PsmHit.Terminal("42.010565", true)));
        PsmHit fixed = hit(Optional.of(new PsmHit.Terminal("229.162932", false)));
        assertAll(
                () -> assertEquals(Optional.empty(), plain.mismatch("R.ACDM[15.9949]K.-")),
                () ->
                        assertEquals(
                                Optional.empty(),
                                variable.mismatch("R.n[42.0106]ACDM[15.9949]K.-")),
                () -> assertEquals(Optional.empty(), fixed.mismatch("R.ACDM[15.9949]K.-")),
                () ->
                        assertEquals(
                                Optional.of(
                                        "has the Percolator peptide \"R.ACDM[15.9949]K.-\", whose"
                                                + " N-terminal modification differ from its pepXML"
                                                + " hit R.AC[+57.021464]DM[+15.994915]K.-"),
                                variable.mismatch("R.ACDM[15.9949]K.-")),
                () -> assertTrue(variable.mismatch("R.n[42.0116]ACDM[15.9949]K.-").isPresent()),
                () -> assertTrue(fixed.mismatch("R.n[229.1629]ACDM[15.9949]K.-").isPresent()),
                () -> assertTrue(plain.mismatch("R.n[1.0]ACDM[15.9949]K.-").isPresent()),
                () ->
                        assertEquals(
                                Optional.of(
                                        "has the Percolator peptide \"R.ACDMK.-\", whose modified"
                                                + " positions differ from its pepXML hit"
                                                + " R.AC[+57.021464]DM[+15.994915]K.-"),
                                plain.mismatch("R.ACDMK.-")),
                () ->
                        assertEquals(
                                Optional.of(
                                        "has the Percolator peptide \"R.ACDM[15.9959]K.-\", whose"
                                                + " mass at position 4 differ from its pepXML hit"
                                                + " R.AC[+57.021464]DM[+15.994915]K.-"),
                                plain.mismatch("R.ACDM[15.9959]K.-")),
                () ->
                        assertEquals(
                                Optional.of(
                                        "has the Percolator peptide \"K.ACDM[15.9949]K.-\", whose"
                                                + " residues differ from its pepXML hit"
                                                + " R.AC[+57.021464]DM[+15.994915]K.-"),
                                plain.mismatch("K.ACDM[15.9949]K.-")),
                () -> assertTrue(plain.mismatch("R.ACDM[15.9949]K.A").isPresent()),
                () -> assertTrue(plain.mismatch("R.ACEM[15.9949]K.-").isPresent()),
                () -> assertTrue(plain.mismatch("R.ACDM[15.9949]Kc[1.0].-").isPresent()),
                () ->
                        assertEquals(
                                Optional.of(
                                        "has the Percolator peptide \"ACDMK\", which is not"
                                                + " <prev>.<peptide>.<next> with bracketed masses"),
                                plain.mismatch("ACDMK")));
    }

    @Test
    @DisplayName("hit keys pack scan, charge and position, and refuse what no pepXML holds")
    void hitKeys() {
        assertAll(
                () ->
                        assertEquals(
                                (101L << 23) | (2L << 11) | 3L, PdvMzTabExporter.hitKey(101, 2, 3)),
                () -> assertEquals(0L, PdvMzTabExporter.hitKey(0, 0, 0)),
                () ->
                        assertEquals(
                                ((1L << 40) - 1) << 23 | 4095L << 11 | 2047L,
                                PdvMzTabExporter.hitKey((1L << 40) - 1, 4095, 2047)),
                () -> assertEquals(-1L, PdvMzTabExporter.hitKey(1L << 40, 2, 1)),
                () -> assertEquals(-1L, PdvMzTabExporter.hitKey(-1, 2, 1)),
                () -> assertEquals(-1L, PdvMzTabExporter.hitKey(1, 4096, 1)),
                () -> assertEquals(-1L, PdvMzTabExporter.hitKey(1, -1, 1)),
                () -> assertEquals(-1L, PdvMzTabExporter.hitKey(1, 2, 2048)),
                () -> assertEquals(-1L, PdvMzTabExporter.hitKey(1, 2, -1)));
    }
}
