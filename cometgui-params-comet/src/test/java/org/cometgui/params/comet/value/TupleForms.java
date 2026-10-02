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

package org.cometgui.params.comet.value;

import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalInt;

/**
 * Every variable-modification tuple form the round-trip suite runs, each with the typed value it
 * must read as.
 *
 * <p><strong>CONSTRUCTED test input, not Comet's output.</strong> The texts marked {@code UPSTREAM}
 * are copied from the examples on Comet's own documentation page for the 2026.02 release,
 * https://uwpr.github.io/Comet/parameters/parameters_202602/variable_modXX.html (fetched
 * 2026-10-02); the ones marked {@code DESCRIBED} are built from that page's description of a field
 * where it gives no whole-line example (the {@code min,max} count, the exclusive {@code -1}, the
 * {@code -2} distance, the peptide C-terminus); {@code Q_DEFAULT} and {@code SCIENTIFIC_PATH} are
 * the two values a real file in this project holds -- {@code comet -q}'s own default for slot 1,
 * and Phase 00's edited oxidation mass.
 */
final class TupleForms {

    /** Where a form's text comes from. */
    enum Origin {
        UPSTREAM,
        DESCRIBED,
        Q_DEFAULT,
        SCIENTIFIC_PATH
    }

    /**
     * One form.
     *
     * @param what what the form exercises
     * @param origin where its text comes from
     * @param text the value text
     * @param expected what it must read as
     */
    record Form(String what, Origin origin, String text, VariableModification expected) {

        @Override
        public String toString() {
            return what + " [" + text + "]";
        }
    }

    private TupleForms() {}

    private static BigDecimal d(String text) {
        return new BigDecimal(text);
    }

    private static VariableModification mod(
            String mass,
            String residues,
            int group,
            OptionalInt minimum,
            int maximum,
            int distance,
            int terminus,
            int required,
            String... losses) {
        return new VariableModification(
                d(mass),
                residues,
                group,
                minimum,
                maximum,
                distance,
                terminus,
                required,
                List.of(losses).stream().map(BigDecimal::new).toList());
    }

    private static final OptionalInt NO_MIN = OptionalInt.empty();

    /** All forms, each a different shape. */
    static final List<Form> ALL =
            List.of(
                    new Form(
                            "plain, comet -q's slot 1 default",
                            Origin.Q_DEFAULT,
                            "15.9949 M 0 3 -1 0 0 0.0",
                            mod("15.9949", "M", 0, NO_MIN, 3, -1, 0, 0, "0.0")),
                    new Form(
                            "unused slot, comet -q's slot 2-15 default",
                            Origin.Q_DEFAULT,
                            "0.0 X 0 3 -1 0 0 0.0",
                            mod("0.0", "X", 0, NO_MIN, 3, -1, 0, 0, "0.0")),
                    new Form(
                            "the user's precise oxidation mass",
                            Origin.SCIENTIFIC_PATH,
                            "15.994915 M 0 3 -1 0 0 0.0",
                            mod("15.994915", "M", 0, NO_MIN, 3, -1, 0, 0, "0.0")),
                    new Form(
                            "one neutral loss",
                            Origin.UPSTREAM,
                            "79.966331 STY 0 3 -1 0 0 97.976896",
                            mod("79.966331", "STY", 0, NO_MIN, 3, -1, 0, 0, "97.976896")),
                    new Form(
                            "two neutral losses",
                            Origin.UPSTREAM,
                            "79.966331 STY 0 3 -1 0 0 97.976896,79.966331",
                            mod(
                                    "79.966331",
                                    "STY",
                                    0,
                                    NO_MIN,
                                    3,
                                    -1,
                                    0,
                                    0,
                                    "97.976896",
                                    "79.966331")),
                    new Form(
                            "required",
                            Origin.UPSTREAM,
                            "79.966331 STY 0 3 -1 0 1 0.0",
                            mod("79.966331", "STY", 0, NO_MIN, 3, -1, 0, 1, "0.0")),
                    new Form(
                            "exclusive",
                            Origin.DESCRIBED,
                            "79.966331 STY 0 3 -1 0 -1 0.0",
                            mod("79.966331", "STY", 0, NO_MIN, 3, -1, 0, -1, "0.0")),
                    new Form(
                            "min,max count",
                            Origin.DESCRIBED,
                            "79.966331 STY 0 2,4 -1 0 0 0.0",
                            mod("79.966331", "STY", 0, OptionalInt.of(2), 4, -1, 0, 0, "0.0")),
                    new Form(
                            "residue plus N-terminus, nK",
                            Origin.UPSTREAM,
                            "42.010565 nK 0 3 -1 0 0 0.0",
                            mod("42.010565", "nK", 0, NO_MIN, 3, -1, 0, 0, "0.0")),
                    new Form(
                            "n at distance 0 from the protein N-terminus (terminus 0)",
                            Origin.UPSTREAM,
                            "15.994915 n 0 3 0 0 0 0.0",
                            mod("15.994915", "n", 0, NO_MIN, 3, 0, 0, 0, "0.0")),
                    new Form(
                            "c within distance 8 of the protein C-terminus (terminus 1)",
                            Origin.UPSTREAM,
                            "28.0 c 0 3 8 1 0 0.0",
                            mod("28.0", "c", 0, NO_MIN, 3, 8, 1, 0, "0.0")),
                    new Form(
                            "negative mass at the peptide N-terminus (terminus 2)",
                            Origin.UPSTREAM,
                            "-17.026549 Q 0 1 0 2 0 0.0",
                            mod("-17.026549", "Q", 0, NO_MIN, 1, 0, 2, 0, "0.0")),
                    new Form(
                            "c at the peptide C-terminus (terminus 3)",
                            Origin.DESCRIBED,
                            "-0.984016 c 0 1 0 3 0 0.0",
                            mod("-0.984016", "c", 0, NO_MIN, 1, 0, 3, 0, "0.0")),
                    new Form(
                            "anywhere but the peptide C-terminal residue (distance -2)",
                            Origin.DESCRIBED,
                            "114.042927 K 0 3 -2 3 0 0.0",
                            mod("114.042927", "K", 0, NO_MIN, 3, -2, 3, 0, "0.0")),
                    new Form(
                            "binary group 1",
                            Origin.UPSTREAM,
                            "6.0 R 1 3 -1 0 0 0.0",
                            mod("6.0", "R", 1, NO_MIN, 3, -1, 0, 0, "0.0")),
                    new Form(
                            "binary group 2",
                            Origin.UPSTREAM,
                            "50.0 K 2 3 -1 0 0 0.0",
                            mod("50.0", "K", 2, NO_MIN, 3, -1, 0, 0, "0.0")),
                    new Form(
                            "everything at once: group, min,max, terminal, required, two losses",
                            Origin.DESCRIBED,
                            "79.966331 nSTc 3 1,2 5 2 1 97.976896,79.966331",
                            mod(
                                    "79.966331",
                                    "nSTc",
                                    3,
                                    OptionalInt.of(1),
                                    2,
                                    5,
                                    2,
                                    1,
                                    "97.976896",
                                    "79.966331")));
}
