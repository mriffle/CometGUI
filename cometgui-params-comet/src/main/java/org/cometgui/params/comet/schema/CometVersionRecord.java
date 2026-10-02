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

package org.cometgui.params.comet.schema;

import java.util.Objects;
import org.cometgui.domain.tools.ToolVersion;

/**
 * The metadata's record of one Comet version it was curated against.
 *
 * <p>The variable-modification tuple's field layout is version-dependent ({@code R-PARAM-09}), so
 * it is recorded here, per version, as data the codec reads.
 *
 * @param version the version as the manifest spells it, such as {@code 2026.02.2}
 * @param marker how that release's binary spells itself on its {@code # comet_version} line
 * @param parameterPages the upstream parameter documentation for the release
 * @param source the upstream source tree at the release's tag
 * @param variableModTuple the field layout of the release's variable-modification tuple
 */
public record CometVersionRecord(
        ToolVersion version,
        CometVersionMarker marker,
        String parameterPages,
        String source,
        VariableModLayout variableModTuple) {

    /** Validates the components. */
    public CometVersionRecord {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(parameterPages, "parameterPages");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(variableModTuple, "variableModTuple");
    }
}
