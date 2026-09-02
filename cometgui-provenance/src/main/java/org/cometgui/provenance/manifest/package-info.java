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
 * The provenance manifest: the immutable value types that hold everything one run has to be able to
 * prove about itself.
 *
 * <p>Everything in this package is a pure value -- validating constructors, defensively copied
 * collections, derived rather than stored quantities -- and nothing in it reads or writes a file.
 * That separation is deliberate. The record types are what the specification's hash, tool and
 * application provenance lists demand, and they must be decidable from those requirements alone;
 * the moment a serialiser's convenience reaches back into the model, the model starts describing
 * the file format instead of the run.
 *
 * <p>{@link org.cometgui.provenance.manifest.ProvenanceManifest} is the root, and it is the single
 * model both {@code provenance.json} and {@code provenance.rst} are generated from, as {@code
 * R-PROV-05} requires. {@link org.cometgui.provenance.manifest.ProvenanceSchema} pins the constants
 * that are part of the on-disk contract -- the schema version, and the settings keys a later phase
 * must write under rather than invent.
 *
 * <p>Two properties recur throughout and both are requirements rather than taste. Every enum's wire
 * name is an explicit field, never {@code name().toLowerCase()}, so that neither the JVM default
 * locale nor a Java rename can change the on-disk format ({@code R-PROV-04}). And every collection
 * that is serialised has a deterministic iteration order, so that two identical runs produce
 * byte-identical documents that can be diffed and checksummed.
 *
 * <p>Written by phase 04 (hashing and provenance core).
 */
package org.cometgui.provenance.manifest;
