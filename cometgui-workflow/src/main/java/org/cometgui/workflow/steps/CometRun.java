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

package org.cometgui.workflow.steps;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.project.ProjectLayout;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.OutputBase;
import org.cometgui.domain.run.OutputBaseNames;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.run.SpectrumInput;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.provenance.hashing.CachingHashService;
import org.cometgui.tools.comet.PinDecoyConfiguration;
import org.cometgui.workflow.engine.ToolIdentity;

/**
 * Everything the steps of one recorded Comet run share: the run's immutable identity and directory,
 * the parameters it was recorded with, the Comet it runs, and the index cache entry it uses.
 *
 * <p>Every path a step reads or writes is derived here, from the identity, so that a step's
 * declaration and its work cannot disagree about a file.
 *
 * @param project the project the run belongs to
 * @param layout the run's directory
 * @param identity the run's identity, as {@code run.json} records it
 * @param model the parameters the run's {@code comet.params} was written from
 * @param comet the selected Comet
 * @param tool the Comet as provenance records each invocation of it
 * @param cacheEntry the index cache entry, present exactly when the run has an index mode
 * @param buildIndex whether the index is to be built, rather than reused from a complete entry --
 *     decided when the run was prepared, because it decides whether the index step declares an
 *     invocation
 * @param hashes the one hasher
 * @param checks the pre-run check, which the run's validate step repeats
 */
record CometRun(
        ProjectLayout project,
        RunLayout layout,
        RunIdentity identity,
        CometParameters model,
        CometSelection comet,
        ToolIdentity tool,
        Optional<IndexCacheEntry> cacheEntry,
        boolean buildIndex,
        CachingHashService hashes,
        PreRunChecks checks) {

    /** The parameter holding Comet's thread count. */
    static final String NUM_THREADS = "num_threads";

    CometRun {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(comet, "comet");
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(cacheEntry, "cacheEntry");
        Objects.requireNonNull(hashes, "hashes");
        Objects.requireNonNull(checks, "checks");
        if (cacheEntry.isPresent() != (identity.indexMode() != IndexMode.NONE)) {
            throw new IllegalArgumentException(
                    "a run has an index cache entry exactly when it has an index mode; mode "
                            + identity.indexMode().wireName()
                            + ", entry "
                            + cacheEntry);
        }
    }

    /**
     * The spectrum files, as the run recorded them, in input order.
     *
     * @return the canonical paths
     */
    List<Path> spectra() {
        List<Path> paths = new ArrayList<>();
        for (SpectrumInput spectrum : identity.spectra()) {
            paths.add(spectrum.file().path());
        }
        return List.copyOf(paths);
    }

    /**
     * Each spectrum file with its position and {@code -N} base name -- the names {@code run.json}
     * records, which {@link RunIdentity} holds to be {@link OutputBaseNames#derive}'s.
     *
     * @return one entry per file, in input order
     */
    List<OutputBase> outputs() {
        return OutputBaseNames.derive(spectra());
    }

    /**
     * The database the run recorded: the FASTA, or an existing index searched as it is.
     *
     * @return its canonical path
     */
    Path database() {
        return identity.fasta().path();
    }

    /**
     * What Comet searches: the cached index in an index mode, otherwise the recorded database.
     *
     * @return the file
     */
    Path searched() {
        return cacheEntry.map(IndexCacheEntry::indexFile).orElse(database());
    }

    /**
     * The decoy source the parameters set.
     *
     * @return the source
     * @throws IllegalStateException if {@code decoy_search} holds a value with no source, which the
     *     validator refuses before a run is recorded
     */
    DecoySource decoySource() {
        return decoySourceOf(model);
    }

    private static DecoySource decoySourceOf(CometParameters model) {
        return model.decoySource()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "decoy_search = "
                                                + model.text(DecoySource.PARAMETER)
                                                + " is no decoy source"));
    }

    /**
     * The decoy configuration, for the PIN's decoy check ({@code R-DEC-04}).
     *
     * @return the configuration
     */
    PinDecoyConfiguration decoys() {
        return decoysOf(model);
    }

    /**
     * The decoy configuration a set of parameters gives the PIN's decoy check: its {@code
     * decoy_search} with that value's meaning, and its one {@code decoy_prefix}.
     *
     * @param model the parameters
     * @return the configuration
     * @throws IllegalStateException if {@code decoy_search} holds a value with no source
     */
    static PinDecoyConfiguration decoysOf(CometParameters model) {
        DecoySource source = decoySourceOf(model);
        return new PinDecoyConfiguration(
                source.decoySearch(),
                source.meaning(),
                ((ParameterValue.Text) model.value(PreRunChecks.DECOY_PREFIX)).text());
    }

    /**
     * Comet's thread count, which bounds how many invocations run at once.
     *
     * @return {@code num_threads}; zero means all cores
     */
    int threads() {
        return ((ParameterValue.Whole) model.value(NUM_THREADS)).value();
    }
}
