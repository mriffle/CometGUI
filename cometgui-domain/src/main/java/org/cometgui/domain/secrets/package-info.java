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
 * The one rule set that keeps a credential out of every artefact this application writes.
 *
 * <p>{@code R-SEC-03} says that credentials, tokens and passwords "shall never be written to
 * provenance or ordinary logs", and that they shall be "redacted from command display, process
 * environment capture and exported reports". Three carriers are named there and a fourth -- the
 * event log -- is implied, which is exactly the shape of failure this package exists to prevent:
 * four redaction implementations that agree on the day they are written and drift apart on the day
 * a new field is added to one of them.
 *
 * <p>So there is one object, {@link org.cometgui.domain.secrets.SecretRedactor}, and every writer
 * in this module calls it. Adding a field to the JSON manifest, a row to the reStructuredText
 * report or a line to the event log cannot open a new leak path, because the new field goes through
 * the same {@code redactText} the old ones do.
 *
 * <p><strong>Two halves, and both are needed.</strong> The pattern rules are the defence against a
 * secret nobody declared -- a credential-bearing URL, an {@code Authorization} header, an
 * assignment whose name looks secret, a token whose shape is recognisable on sight. The registry,
 * {@link org.cometgui.domain.secrets.SecretRegistry}, is the airtight half: the application knows
 * the credential it is holding, because it read it out of the OS keychain, so it registers that
 * exact value and every emitted string has that substring replaced wherever it appears. Pattern
 * rules alone cannot catch a bare token passed as {@code -k <value>}; the registry can, and that is
 * what makes phase 04's exit gate item 6 an honest claim rather than a hopeful one.
 *
 * <p>Filled by phase 04 (hashing and provenance core), work unit 3.
 */
package org.cometgui.domain.secrets;
