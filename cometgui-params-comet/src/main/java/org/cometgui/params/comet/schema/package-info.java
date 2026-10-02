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
 * The versioned parameter schema: what a Comet binary declares about its own parameters (comet -q,
 * or comet -p marked PARTIAL_DISCOVERY), the curated metadata that says what each one means, and
 * the drift between the two (R-PARAM-01, R-PARAM-02, R-PARAM-03, AC-PAR-01, AC-PAR-02).
 *
 * <p>{@link org.cometgui.params.comet.schema.MetadataLoader} reads the curated metadata file, which
 * is also the source of the generated reference documentation (R-DOC-04). {@link
 * org.cometgui.params.comet.schema.SchemaDiscovery} reads a dump, {@link
 * org.cometgui.params.comet.schema.SchemaDrift} compares the two, and {@link
 * org.cometgui.params.comet.schema.CometParameterSchemaProvider} runs the binary through the domain
 * ProcessRunner port to produce a dump. Phase 06 (Comet parameter model).
 */
package org.cometgui.params.comet.schema;
