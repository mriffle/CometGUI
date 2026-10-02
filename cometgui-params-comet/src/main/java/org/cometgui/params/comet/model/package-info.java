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
 * The typed, origin-tracked Comet parameter model (R-PARAM-03, the value-origin paragraph after the
 * specification's Parameter definition model).
 *
 * <p>{@link org.cometgui.params.comet.model.CometParameters} is an immutable set of typed values
 * for one Comet version: one {@link org.cometgui.params.comet.model.ParameterEntry} per modelled
 * parameter, each with its {@link org.cometgui.params.comet.model.ValueOrigin}, plus the enzyme
 * table, the unknown parameters an imported file carried, and the diagnostics of the parse that
 * produced it. Changing a value yields a new model. The parser builds it; the canonical writer
 * writes it; validation, presets and the editor read it. Phase 06 (Comet parameter model).
 */
package org.cometgui.params.comet.model;
