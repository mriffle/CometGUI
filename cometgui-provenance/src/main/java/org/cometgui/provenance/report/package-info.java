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
 * {@code provenance.rst}: the human-readable half of a run's provenance record.
 *
 * <p>The specification allows this package exactly one design decision, and makes it: "the
 * human-readable {@code provenance.rst} report shall be generated from the same machine-readable
 * model, never maintained independently". So {@link
 * org.cometgui.provenance.report.ProvenanceReportWriter} takes a {@link
 * org.cometgui.provenance.manifest.ProvenanceManifest} and nothing else -- no file, no clock, no
 * second traversal of the run -- and {@link org.cometgui.provenance.report.RstWriter} decides what
 * a byte of the document looks like, exactly as {@link org.cometgui.provenance.json.JsonWriter}
 * does for {@code provenance.json}. The two documents are two renderings of one object, and the
 * test beside the writer enumerates the model's record components reflectively so that a field
 * added to the manifest cannot reach one document without reaching the other.
 *
 * <p>Two properties recur here and both are requirements rather than taste.
 *
 * <ul>
 *   <li><b>The document survives {@code sphinx-build -n -W}.</b> The project builds its
 *       documentation with warnings as errors, so a heading whose underline is one character short
 *       or a value whose asterisk opens emphasis is a build failure rather than a cosmetic defect.
 *       Underlines are generated from their headings; every value is an inline literal, or -- where
 *       reStructuredText cannot carry one -- an escaped, quoted string.
 *   <li><b>Redaction happens inside the writer, not at the call sites.</b> {@code R-SEC-03} and
 *       phase 04's exit gate item 6 require that no seeded secret appears in the generated
 *       reStructuredText. Every value goes through the one {@link
 *       org.cometgui.domain.secrets.SecretRedactor}, so a field added later cannot open a leak path
 *       that the existing fields have closed.
 * </ul>
 *
 * <p>Written by phase 04 (hashing and provenance core). Phase 13 owns the Provenance UI that
 * displays the same model on screen and the export actions that write this document from it; it
 * should render from the manifest through this package rather than growing a second renderer.
 */
package org.cometgui.provenance.report;
