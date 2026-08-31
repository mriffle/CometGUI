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

package org.cometgui.provenance.manifest;

/**
 * The constants of the provenance format itself: the schema version every manifest carries, and the
 * settings keys later phases must write under rather than invent.
 *
 * <p>Everything here is pinned. A constant in this class is part of the on-disk contract, so
 * changing one of these values is a format change, not a refactoring, and it belongs in {@code
 * docs/reference/provenance_format.rst} with a version bump beside it.
 */
public final class ProvenanceSchema {

    /**
     * The schema version stamped into every manifest this build writes.
     *
     * <p><strong>What a bump obliges a reader to do.</strong> {@code R-PROV-05} requires the final
     * {@code provenance.json} to carry a schema version, and the reason is that a manifest outlives
     * the build that wrote it: a scientist re-verifying a two-year-old run opens it with a much
     * later CometGUI. So the number is a compatibility statement, not a decoration, and the rules
     * around it are:
     *
     * <ul>
     *   <li>A reader that finds a version <em>higher</em> than this constant must refuse to
     *       interpret the document rather than parse the fields it recognises. A newer writer may
     *       have changed the meaning of a field, not merely added one, and a half-understood
     *       provenance record is worse than an unreadable one -- it is wrong without saying so.
     *   <li>A reader that finds a version <em>lower</em> than this constant must migrate it
     *       explicitly, so that the fields a later version added have declared values rather than
     *       silently absent ones.
     *   <li>The number is bumped whenever a field is removed, renamed, re-typed, or given a new
     *       meaning. Adding an optional field that an older reader can ignore does not require a
     *       bump; nothing else escapes one.
     * </ul>
     *
     * <p>Version 1 is the first published format.
     */
    public static final int VERSION = 1;

    /**
     * The settings key under which the effective Percolator random seed is recorded.
     *
     * <p>{@code AC-PRV-10} requires the effective seed and the JVM locale to be recorded, and
     * "effective" is the load-bearing word: the value written here is the seed Percolator actually
     * ran with, whether the user chose it, a preset supplied it or the application generated it.
     * Percolator's cross-validation is seeded, so two runs that differ only in this number produce
     * different q-values; a run whose seed is not recorded cannot be reproduced.
     *
     * <p>The key is pinned here, once, because a settings map is an open namespace: if phase 09
     * invented {@code "percolator_seed"} while the provenance report looked for {@code
     * "percolator.seed"}, both sides would work in isolation and the seed would silently vanish
     * from the record. Read the seed from a manifest with this constant, never with a literal.
     */
    public static final String PERCOLATOR_SEED_SETTING = "percolator.seed";

    /**
     * Never instantiated: this class holds constants and has no state of its own.
     *
     * <p>It throws rather than being an empty private constructor so that the intent is enforced
     * for the one caller that can still reach it -- reflection -- instead of merely being implied.
     */
    private ProvenanceSchema() {
        throw new AssertionError("ProvenanceSchema is a constant holder and is never instantiated");
    }
}
