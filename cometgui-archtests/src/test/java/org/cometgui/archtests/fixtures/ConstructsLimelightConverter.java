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

package org.cometgui.archtests.fixtures;

import java.nio.file.Path;
import java.time.Duration;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.limelight.LimelightConverter;

/**
 * Constructs the Limelight converter over a process runner it was handed, outside {@code
 * org.cometgui.workflow.limelight}: the "just convert it here" shortcut that would run a conversion
 * with no prerequisite checked and no event recorded. A committed fixture, so that the proof that
 * {@code LimelightConversionPathRuleTest}'s rule rejects it runs on every build.
 */
public final class ConstructsLimelightConverter {

    private ConstructsLimelightConverter() {}

    /**
     * @param processes any process runner -- the application's own, unrecorded
     * @param java the runtime
     * @param jar the converter JAR
     * @return a converter no record will be made for
     */
    static LimelightConverter shortcut(ProcessRunner processes, JavaRuntime java, Path jar) {
        return new LimelightConverter(processes, Duration.ofMinutes(30), java, jar);
    }
}
