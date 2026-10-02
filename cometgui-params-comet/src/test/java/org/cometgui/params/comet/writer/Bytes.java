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

package org.cometgui.params.comet.writer;

import java.nio.charset.StandardCharsets;

/** Byte-array equality with a diagnostic that says where two texts first differ. */
final class Bytes {

    private Bytes() {}

    static void assertSame(byte[] expected, byte[] actual, String what) {
        int limit = Math.min(expected.length, actual.length);
        int offset = -1;
        for (int index = 0; index < limit; index++) {
            if (expected[index] != actual[index]) {
                offset = index;
                break;
            }
        }
        if (offset < 0 && expected.length == actual.length) {
            return;
        }
        int at = offset < 0 ? limit : offset;
        throw new AssertionError(
                what
                        + ": the bytes differ first at offset "
                        + at
                        + " (expected "
                        + expected.length
                        + " bytes, got "
                        + actual.length
                        + "); expected around there \""
                        + around(expected, at)
                        + "\", got \""
                        + around(actual, at)
                        + "\"");
    }

    private static String around(byte[] bytes, int at) {
        int from = Math.max(0, at - 30);
        int to = Math.min(bytes.length, at + 30);
        return new String(bytes, from, to - from, StandardCharsets.UTF_8)
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
