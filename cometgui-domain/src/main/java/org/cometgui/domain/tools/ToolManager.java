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

package org.cometgui.domain.tools;

import java.nio.file.Path;
import java.util.List;

/**
 * The seam through which the Tool Manager sees the installer, and the only one it has.
 *
 * <p><strong>What this seam protects.</strong> The architecture rules restrict {@code
 * org.cometgui.ui..} to {@code java..}, {@code javax..}, {@code javafx..} and the domain, workflow,
 * results, provenance and params packages -- and name neither {@code org.cometgui.install..} nor
 * {@code org.cometgui.tools..}. A second rule forbids the user interface from depending on {@code
 * java.net..}, {@code java.security..}, {@code java.util.zip..} or {@code java.util.jar..}. So a
 * view cannot reach a downloader, a hasher or an archive extractor, and the Tool Manager can be
 * driven in a test with no installer present at all.
 *
 * <p><strong>There is no implementation in this module and none in this work unit</strong>, exactly
 * as {@code org.cometgui.domain.ports.Downloader}, {@code HashService} and {@code ProcessRunner}
 * were declared before the phase that implemented them. Phase 05 units 2 to 8 build what stands
 * behind this interface -- the manifest and its reader, the downloader, the extractors, the atomic
 * install, the probes and local-binary registration -- and unit 9 builds the view that consumes it.
 *
 * <p>Implementations are expected to be safe to call from the JavaFX application thread: {@link
 * #offers()} answers from what is already known, and {@link #install} returns as soon as the
 * install has started.
 */
public interface ToolManager {

    /**
     * Every tool build this machine may be shown, in the order the Tool Manager should list them.
     *
     * <p>Includes builds that cannot be installed here -- unavailable on this platform, or
     * available but beyond this host's requirements -- because {@code R-PERC-01} is a rule about
     * not <em>promising</em> a build that cannot run, not a reason to hide that it exists. An offer
     * that will not run here carries the state and, where there is one, the {@link
     * LoaderDiagnostic} that explains it.
     *
     * @return the offers, immutable and possibly empty
     */
    List<ToolOffer> offers();

    /**
     * Starts installing one tool build and returns as soon as it is under way.
     *
     * <p>Progress, and the terminal phase the install ends in, arrive through {@code listener}. Use
     * a no-op listener rather than {@code null}.
     *
     * @param tool the tool to install
     * @param version the version to install, which must be one {@link #offers()} named
     * @param listener notified as the install proceeds
     * @return a handle that can cancel the install
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if no offer names that tool and version
     */
    InstallHandle install(ToolName tool, ToolVersion version, InstallProgressListener listener);

    /**
     * Registers a binary already on the machine, which CometGUI did not install ({@code
     * R-TOOL-08}).
     *
     * <p>The implementation probes it, checks the minimum version the tool requires, records its
     * checksums and probes its capabilities <em>conservatively</em>: absent positive evidence of a
     * capability, the capability is absent. This is the documented remedy wherever no managed build
     * is available for the platform, so it is a supported path rather than an escape hatch.
     *
     * @param tool which tool the binary is claimed to be
     * @param executable the absolute path of the executable or JAR
     * @return the offer for the registered binary, with {@link ToolOrigin#LOCAL}
     * @throws ToolRegistrationException if the file is not that tool, is too old, or cannot be
     *     probed -- with a message naming what was found and what was required
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code executable} is not absolute
     */
    ToolOffer registerLocalBinary(ToolName tool, Path executable) throws ToolRegistrationException;
}
