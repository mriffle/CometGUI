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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.params.percolator.PercolatorSettings;
import org.cometgui.params.percolator.resolution.PercolatorResolution;

/**
 * The Percolator half of a run as the Percolator section has it now: the build that would run, the
 * resolution shown, the settings, and every reason it cannot run yet (decision P9-12). The Run
 * section passes it to the engine with the Comet half; its {@link #problems()} are reasons of the
 * engine's half of the one Run readiness ({@code P8-16}).
 *
 * <p>Runnable exactly when there are no problems, and then whole: a build that is installed with a
 * path, the resolution it was chosen against, and valid settings. The composition root hashes the
 * build's executable and turns this into the workflow's {@code PercolatorChoice}; nothing here
 * hashes or reads a file.
 *
 * @param build the build that would run: the scientist's choice, or the resolved default; empty
 *     when there is none
 * @param resolvedDefault whether {@code build} is the resolution's default rather than the
 *     scientist's choice
 * @param resolution the <em>latest compatible</em> resolution for the enabled downstream stages;
 *     empty until the builds have been read
 * @param settings the Percolator settings; empty while a setting holds text the model refused
 * @param problems why the Percolator half cannot run, one sentence each; empty when it can
 * @param pending whether the half is not known yet -- the Percolator builds are still to be read --
 *     so that the Run section waits for it rather than answering on a half nobody computed
 */
public record PercolatorRequest(
        Optional<ToolOffer> build,
        boolean resolvedDefault,
        Optional<PercolatorResolution> resolution,
        Optional<PercolatorSettings> settings,
        List<String> problems,
        boolean pending) {

    /**
     * Validates and copies.
     *
     * @throws NullPointerException if a component or a problem is {@code null}
     * @throws IllegalArgumentException if a problem is blank, the build is not Percolator, a
     *     request without problems lacks an installed build with a path, the resolution or the
     *     settings, or a pending one has no problem saying it is pending
     */
    public PercolatorRequest {
        Objects.requireNonNull(build, "build");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(settings, "settings");
        problems = List.copyOf(problems);
        for (String problem : problems) {
            if (problem.isBlank()) {
                throw new IllegalArgumentException(
                        "a reason Percolator cannot run is never blank: Run would be disabled with"
                                + " no explanation");
            }
        }
        if (build.isPresent() && build.get().tool() != ToolName.PERCOLATOR) {
            throw new IllegalArgumentException(
                    "the Percolator half runs a Percolator build, not one of "
                            + build.get().tool().id());
        }
        if (pending && problems.isEmpty()) {
            throw new IllegalArgumentException(
                    "a Percolator half that is not known yet cannot run, and says so");
        }
        if (problems.isEmpty()) {
            boolean installed =
                    build.isPresent()
                            && build.get().state() == ToolInstallState.INSTALLED
                            && build.get().installedPath().isPresent();
            if (!installed || resolution.isEmpty() || settings.isEmpty()) {
                throw new IllegalArgumentException(
                        "a Percolator half with no problem must name an installed build with a"
                                + " path, its resolution and valid settings");
            }
        }
    }

    /**
     * A Percolator half that cannot run, for these reasons.
     *
     * @param problems why, one sentence each; at least one
     * @return the request, with nothing else known
     * @throws IllegalArgumentException if there is no reason
     */
    public static PercolatorRequest blocked(List<String> problems) {
        if (problems.isEmpty()) {
            throw new IllegalArgumentException("a blocked Percolator half says why");
        }
        return new PercolatorRequest(
                Optional.empty(), false, Optional.empty(), Optional.empty(), problems, false);
    }

    /**
     * A Percolator half that is not known yet: the builds are still to be read.
     *
     * @param why what is awaited, as a sentence
     * @return the request, pending
     */
    public static PercolatorRequest pending(String why) {
        return new PercolatorRequest(
                Optional.empty(), false, Optional.empty(), Optional.empty(), List.of(why), true);
    }

    /**
     * Whether the Percolator half can run.
     *
     * @return {@code true} when there is no problem
     */
    public boolean runnable() {
        return problems.isEmpty();
    }

    @Override
    public List<String> problems() {
        return List.copyOf(problems);
    }
}
