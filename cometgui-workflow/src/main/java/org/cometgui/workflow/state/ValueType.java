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

package org.cometgui.workflow.state;

/**
 * The shape of an {@link InputKind}'s value, and therefore how that value is reduced to the digest
 * a step fingerprint contains.
 *
 * <p>Each input kind has exactly one value type, fixed in {@link InputKind}. That is what makes the
 * per-type encodings below unambiguous without a type tag: two values of different types can never
 * be compared, because {@link StepInputs} refuses a value whose type is not its kind's.
 *
 * <p>The encodings are documented on {@link InputValue}, next to the code that applies them.
 */
public enum ValueType {

    /**
     * An ordered list of named files, each contributing the SHA-256 its caller obtained from the
     * one hasher ({@code HashService}). See {@link InputValue.Files}.
     */
    FILES,

    /**
     * A byte sequence identified by its SHA-256 -- the canonical Comet parameter file. See {@link
     * InputValue.Bytes}.
     */
    BYTES,

    /** A tool identity: its version and its binary's SHA-256. See {@link InputValue.Tool}. */
    TOOL,

    /** A canonical text serialisation of a group of settings. See {@link InputValue.Text}. */
    TEXT,

    /** A decimal number such as a q-value cutoff. See {@link InputValue.Decimal}. */
    DECIMAL
}
