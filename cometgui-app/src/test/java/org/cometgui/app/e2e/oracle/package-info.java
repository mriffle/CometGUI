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

/**
 * Independent oracles for the end-to-end scenarios (design decision P14-7): readers, counters and
 * digests written for the tests, on the JDK alone, so that a number the application shows is
 * compared with one the application did not compute.
 *
 * <p><strong>No product parser, hasher or counter is on the path of the fact an oracle
 * checks.</strong> Each class here imports nothing from {@code org.cometgui} outside the test tree,
 * and the two test helpers it builds on -- {@code org.cometgui.app.testing.OwnDigests} and {@code
 * org.cometgui.app.testing.IndependentCounts} -- import nothing from it either. Each oracle is
 * proved by an ordinary unit test against small hand-written inputs whose expected values were
 * worked out by hand and typed into the test, so a reader that agreed with the product on a wrong
 * answer would still be caught.
 */
package org.cometgui.app.e2e.oracle;
