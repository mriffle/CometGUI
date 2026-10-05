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

package org.cometgui.ui.viewmodel.params;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.cometgui.domain.build.BuildIdentity;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.parser.ReleaseDefaults;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.MetadataLoader;

/** What the tests of this package share: the bundled metadata and the two offered releases. */
final class Sessions {

    /** The bundled metadata, loaded once. */
    static final CuratedMetadata METADATA = MetadataLoader.loadBundled();

    /** Comet 2026.03.0, the default release. */
    static final ToolVersion C03 = ToolVersion.parse("2026.03.0");

    /** Comet 2026.02.2, the older offered release. */
    static final ToolVersion C02 = ToolVersion.parse("2026.02.2");

    /** The build the canonical header names: the version the developer page's sizes are for. */
    static final BuildIdentity BUILD =
            BuildIdentity.of("0.1.0-SNAPSHOT", "unknown", Instant.parse("2026-10-05T00:00:00Z"));

    private Sessions() {}

    /**
     * A release's own {@code comet -q} file, as bundled in the model module's jar (Comet's own
     * output, on the class path; no file outside this module is read).
     *
     * @param release the release
     * @return the text
     */
    static String cometQ(ToolVersion release) {
        return new String(ReleaseDefaults.bundledFile(release), StandardCharsets.UTF_8);
    }

    /**
     * A session offering both releases, the given one first, every stage enabled.
     *
     * @param first the release to start in
     * @return the session
     */
    static ParameterSession startingIn(ToolVersion first) {
        List<ToolVersion> offered = first.equals(C03) ? List.of(C03, C02) : List.of(C02, C03);
        return new ParameterSession(METADATA, offered, StageSwitches.ALL_ENABLED);
    }

    /**
     * Both releases, for parameterised tests.
     *
     * @return the release texts
     */
    static List<String> releases() {
        return List.of("2026.03.0", "2026.02.2");
    }
}
