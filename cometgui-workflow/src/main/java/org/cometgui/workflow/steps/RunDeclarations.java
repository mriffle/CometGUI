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
import org.cometgui.domain.run.OutputBase;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.domain.run.SpectrumInput;
import org.cometgui.params.comet.model.DecoySource;
import org.cometgui.workflow.engine.DeclaredFile;
import org.cometgui.workflow.engine.StepDeclaration;

/**
 * Every file each Comet step reads and writes, and the invocations it makes, derived from the run's
 * identity alone (design decision P8-12): the declarations the engine hashes into provenance and
 * re-hashes before it reuses a step.
 *
 * <p>Pure: nothing here touches the disk. A step's declaration is fixed before the step runs, so it
 * is computed from what the run recorded, never from what the step finds.
 *
 * <h2>Roles</h2>
 *
 * <p>The {@code role} of each provenance file record: {@value #SPECTRUM}, {@value #FASTA}, {@value
 * #INDEX} (an index Comet searches, cached or chosen), {@value #PARAMS}, {@value #PEP_XML}, {@value
 * #DECOY_PEP_XML}, {@value #PIN} and {@value #MERGED_PIN}.
 */
final class RunDeclarations {

    /** A spectrum file. */
    static final String SPECTRUM = "spectrum";

    /** The FASTA. */
    static final String FASTA = "fasta";

    /** A Comet index. */
    static final String INDEX = "comet-index";

    /** The archived canonical parameter file. */
    static final String PARAMS = "comet-params";

    /** One input's pepXML. */
    static final String PEP_XML = "pepxml";

    /** One input's separate decoy pepXML ({@code decoy_search = 2}). */
    static final String DECOY_PEP_XML = "decoy-pepxml";

    /** One input's PIN. */
    static final String PIN = "pin";

    /** The merged PIN. */
    static final String MERGED_PIN = "merged-pin";

    /** The stage identifier of the index build's one invocation. */
    static final String INDEX_INVOCATION = "comet-index";

    /** The suffix of the decoy pepXML Comet writes with {@code decoy_search = 2}. */
    static final String DECOY_PEP_XML_SUFFIX = ".decoy.pep.xml";

    private RunDeclarations() {}

    /** The role of the recorded database: an index searched as it is, or a FASTA. */
    static String databaseRole(CometRun run) {
        return PreRunChecks.isIndex(run.database()) ? INDEX : FASTA;
    }

    /** The separate decoy pepXML of one input. */
    static Path decoyPepXml(RunLayout layout, String base) {
        return layout.cometOutputDirectory().resolve(base + DECOY_PEP_XML_SUFFIX);
    }

    /** Whether Comet writes a separate decoy pepXML per input. */
    static boolean separateDecoys(CometRun run) {
        return run.decoySource() == DecoySource.COMET_INTERNAL_SEPARATE;
    }

    /** {@code serialise-comet-params}: the archived parameter file, written before the run. */
    static StepDeclaration serialise(CometRun run) {
        return new StepDeclaration(
                List.of(DeclaredFile.output(PARAMS, run.layout().cometParamsFile())), List.of());
    }

    /** {@code hash-inputs}: every spectrum file and the database. */
    static StepDeclaration hashInputs(CometRun run) {
        List<DeclaredFile> files = new ArrayList<>();
        for (SpectrumInput spectrum : run.identity().spectra()) {
            files.add(DeclaredFile.input(SPECTRUM, spectrum.file().path()));
        }
        files.add(DeclaredFile.input(databaseRole(run), run.database()));
        return new StepDeclaration(files, List.of());
    }

    /**
     * {@code build-comet-index}: reads the archived parameters and the FASTA, writes the cached
     * index, and invokes Comet once when the index is to be built.
     */
    static StepDeclaration buildIndex(CometRun run) {
        IndexCacheEntry entry =
                run.cacheEntry()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "a run without an index mode has no index step"));
        return new StepDeclaration(
                List.of(
                        DeclaredFile.input(PARAMS, run.layout().cometParamsFile()),
                        DeclaredFile.input(FASTA, run.database()),
                        DeclaredFile.output(INDEX, entry.indexFile())),
                run.buildIndex() ? List.of(INDEX_INVOCATION) : List.of());
    }

    /**
     * {@code run-comet}: reads every spectrum file, what Comet searches and the archived
     * parameters; writes each input's pepXML and PIN (and decoy pepXML); one invocation per input.
     */
    static StepDeclaration runComet(CometRun run) {
        List<DeclaredFile> files = new ArrayList<>();
        for (SpectrumInput spectrum : run.identity().spectra()) {
            files.add(DeclaredFile.input(SPECTRUM, spectrum.file().path()));
        }
        files.add(
                DeclaredFile.input(
                        run.cacheEntry().isPresent() ? INDEX : databaseRole(run), run.searched()));
        files.add(DeclaredFile.input(PARAMS, run.layout().cometParamsFile()));
        List<String> invocations = new ArrayList<>();
        for (OutputBase output : run.outputs()) {
            files.addAll(outputsOf(run, output.base(), true));
            invocations.add(output.stageId());
        }
        return new StepDeclaration(files, invocations);
    }

    /** {@code validate-comet-outputs}: reads every output Comet wrote. */
    static StepDeclaration validateOutputs(CometRun run) {
        List<DeclaredFile> files = new ArrayList<>();
        for (OutputBase output : run.outputs()) {
            files.addAll(outputsOf(run, output.base(), false));
        }
        return new StepDeclaration(files, List.of());
    }

    /** {@code merge-pin}: reads every PIN, writes the merged PIN. */
    static StepDeclaration mergePin(CometRun run) {
        List<DeclaredFile> files = new ArrayList<>();
        for (OutputBase output : run.outputs()) {
            files.add(DeclaredFile.input(PIN, run.layout().pinFile(output.base())));
        }
        files.add(DeclaredFile.output(MERGED_PIN, run.layout().mergedPinFile()));
        return new StepDeclaration(files, List.of());
    }

    /** One input's Comet outputs, as written ({@code written}) or as read. */
    private static List<DeclaredFile> outputsOf(CometRun run, String base, boolean written) {
        RunLayout layout = run.layout();
        List<DeclaredFile> files = new ArrayList<>();
        files.add(file(written, PEP_XML, layout.pepXmlFile(base)));
        files.add(file(written, PIN, layout.pinFile(base)));
        if (separateDecoys(run)) {
            files.add(file(written, DECOY_PEP_XML, decoyPepXml(layout, base)));
        }
        return files;
    }

    private static DeclaredFile file(boolean written, String role, Path path) {
        return written ? DeclaredFile.output(role, path) : DeclaredFile.input(role, path);
    }
}
