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

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;

/**
 * The installed Comet build a schema is asked for: where it is, which version the installer
 * identified, and which capabilities it was probed to have. The specification's {@code
 * CometParameterSchemaProvider.schemaFor(CometToolIdentity)} takes one.
 *
 * @param executable the absolute path of the executable
 * @param version the version the installer identified, as the manifest spells it
 * @param capabilities the probed capabilities; {@link ToolCapability#COMPLETE_PARAMS_QUERY} decides
 *     between {@code -q} and {@code -p}
 */
public record CometToolIdentity(
        Path executable, ToolVersion version, Set<ToolCapability> capabilities) {

    /**
     * Validates the identity.
     *
     * @throws IllegalArgumentException if the path is relative or a capability is not Comet's
     */
    public CometToolIdentity {
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(version, "version");
        if (!executable.isAbsolute()) {
            throw new IllegalArgumentException(
                    "a Comet executable must be named by an absolute path, not " + executable);
        }
        Set<ToolCapability> copy = EnumSet.noneOf(ToolCapability.class);
        for (ToolCapability capability : capabilities) {
            copy.add(capability.requireBelongsTo(ToolName.COMET));
        }
        capabilities = Set.copyOf(copy);
    }

    /**
     * The probed capabilities, immutable.
     *
     * @return the capabilities
     */
    @Override
    public Set<ToolCapability> capabilities() {
        return Set.copyOf(capabilities);
    }

    /**
     * Whether this build answers {@code -q} with its complete parameter set.
     *
     * @return {@code true} if it was probed to
     */
    public boolean hasCompleteParamsQuery() {
        return capabilities.contains(ToolCapability.COMPLETE_PARAMS_QUERY);
    }
}
