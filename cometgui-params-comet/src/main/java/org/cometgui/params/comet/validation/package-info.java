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
 * Parameter validation: ranges, units, the signed precursor tolerance pair (R-PARAM-04),
 * modification tuple consistency, enzyme consistency and cross-parameter rules.
 *
 * <p>{@link org.cometgui.params.comet.validation.CometValidator} validates a typed model into a
 * {@link org.cometgui.params.comet.validation.ValidationReport} of {@link
 * org.cometgui.params.comet.validation.Finding}s, each from one {@link
 * org.cometgui.params.comet.validation.Rule} with a stable identifier and a fixed severity, and
 * attached to the responsible parameters and their category. A report with an error blocks a run.
 * The rule catalogue, with the Comet source each rule encodes, is in {@code
 * docs/developer/comet_parameter_schema.rst}.
 */
package org.cometgui.params.comet.validation;
