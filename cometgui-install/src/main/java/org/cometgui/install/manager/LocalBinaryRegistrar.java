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

package org.cometgui.install.manager;

import java.nio.file.Path;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolRegistrationException;

/**
 * {@code R-TOOL-08}'s "unknown local binary", as a seam this module can name.
 *
 * <p><strong>Declared here and implemented in {@code org.cometgui.tools} (phase 05 unit
 * 7)</strong>, the same shape and for the same reason as {@link
 * org.cometgui.install.probe.CapabilityProber} and {@link
 * org.cometgui.install.probe.JavaArtefactIdentity}: {@code cometgui-tools} depends on {@code
 * cometgui-domain} and {@code cometgui-process} and <em>not</em> on {@code cometgui-install}, so
 * {@code LocalPercolatorRegistration} cannot implement an interface declared in this module. Every
 * parameter and the return type here are therefore <strong>domain vocabulary only</strong>, so the
 * adapter is a lambda in {@code cometgui-app} -- the one place both modules are visible -- and no
 * module's dependency list changes.
 *
 * <p><strong>Refusing is throwing, and the sentence is for the user.</strong> {@code
 * ToolRegistrationException} is checked deliberately: being told "that is Percolator 3.04, and 3.05
 * is the minimum" is part of the normal flow of choosing a file, not a programming error. An
 * implementation names what was found and what was required rather than reporting that something
 * went wrong.
 *
 * <p>An implementation that has no registrar for a tool <strong>says so</strong> rather than
 * returning an offer of the wrong tool or an empty answer: there is exactly one tool this product
 * knows how to register by hand today, because {@code R-PERC-03} makes local registration the
 * documented remedy for Percolator specifically.
 */
@FunctionalInterface
public interface LocalBinaryRegistrar {

    /**
     * Probes a file the user chose and turns it into the row the Tool Manager renders.
     *
     * @param tool which tool the file is claimed to be
     * @param executable the absolute path of the executable or JAR
     * @return the offer for it, with {@link org.cometgui.domain.tools.ToolOrigin#LOCAL} and a
     *     capability set that was <em>probed</em> rather than assumed
     * @throws ToolRegistrationException if the file is not there, is not that tool, is older than
     *     the minimum the tool requires, cannot be probed, or is of a tool this product cannot
     *     register by hand -- with a message naming what was found and what was required
     * @throws NullPointerException if either argument is {@code null}
     */
    ToolOffer register(ToolName tool, Path executable) throws ToolRegistrationException;
}
