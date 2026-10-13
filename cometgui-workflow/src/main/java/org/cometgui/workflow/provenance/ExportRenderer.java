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

package org.cometgui.workflow.provenance;

import org.cometgui.domain.secrets.SecretRedactor;
import org.cometgui.provenance.manifest.ManifestWriter;
import org.cometgui.provenance.manifest.ProvenanceManifest;
import org.cometgui.provenance.report.ProvenanceReportWriter;

/**
 * Renders the two exports of a loaded record ({@code P13-3}).
 *
 * <p>Package-private, and a seam only so that a test can watch which object each export was
 * rendered from. The one production implementation, {@link #WRITERS}, is phase 04's two writers and
 * nothing else: the JSON is {@link ManifestWriter}'s and the report is {@link
 * ProvenanceReportWriter}'s, both built with the redactor they are handed.
 */
interface ExportRenderer {

    /** Phase 04's writers: {@code provenance.json} and {@code provenance.rst} exactly as a run. */
    ExportRenderer WRITERS =
            new ExportRenderer() {
                @Override
                public String json(SecretRedactor redactor, ProvenanceManifest manifest) {
                    return ManifestWriter.redactingWith(redactor).render(manifest);
                }

                @Override
                public String rst(SecretRedactor redactor, ProvenanceManifest manifest) {
                    return ProvenanceReportWriter.redactingWith(redactor).render(manifest);
                }
            };

    /**
     * The JSON document of a record.
     *
     * @param redactor the session's rule set
     * @param manifest the loaded record
     * @return the whole document
     */
    String json(SecretRedactor redactor, ProvenanceManifest manifest);

    /**
     * The reStructuredText report of a record.
     *
     * @param redactor the session's rule set
     * @param manifest the loaded record
     * @return the whole report
     */
    String rst(SecretRedactor redactor, ProvenanceManifest manifest);
}
