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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.run.OutputBase;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.workflow.engine.Invocation;
import org.cometgui.workflow.engine.ReuseRefusedException;
import org.cometgui.workflow.engine.RunRequest;
import org.cometgui.workflow.engine.RunResult;
import org.cometgui.workflow.engine.StepAction;
import org.cometgui.workflow.engine.StepContext;
import org.cometgui.workflow.engine.StepDeclaration;
import org.cometgui.workflow.engine.StepFailedException;
import org.cometgui.workflow.engine.StepStateListener;
import org.cometgui.workflow.state.EngineStep;

/**
 * The PSM identifiers of a real run, read here from the files the tools wrote -- nothing of the
 * product's readers -- and the one way these tests make a run as it was made before Comet was given
 * the bare {@code -N} base.
 *
 * <h2>What an identifier must be</h2>
 *
 * <p>Comet writes its {@code -N} value at the start of every PIN row's {@code SpecId}, and
 * Percolator carries the {@code SpecId} into every {@code PSMId} and {@code psm_id}. A run searches
 * with {@code -N<base>}, so each is exactly {@code <base>_<scan>_<charge>_<rank>}: {@link
 * #assertIds} requires that of every identifier of a file, character for character, which also
 * means none holds a path separator or anything of the run directory.
 *
 * <h2>A run made before that</h2>
 *
 * <p>{@link #runAsBefore} runs a prepared run with one step replaced: {@code run-comet} gives Comet
 * what {@code CometSearchCommands} gave it until Phase 16 unit 3b -- {@code
 * -N<run>/outputs/comet/<base>}, in the run directory -- through the same engine, so the commands
 * are recorded as they ran. Every other step is the product's. The result is a run directory whose
 * identifiers begin with the absolute path, written by the real tools: what a project made by an
 * earlier CometGUI holds.
 */
final class RunIdentifiers {

    private static final Pattern PSM_ID_ATTRIBUTE = Pattern.compile("psm_id=\"([^\"]*)\"");

    private static final Pattern PSM_ID_ELEMENT = Pattern.compile("<psm_id>([^<]*)</psm_id>");

    private RunIdentifiers() {}

    /** The first column of every data row of a tab-separated file with one header line. */
    static List<String> firstColumn(Path table) throws IOException {
        List<String> lines = Files.readAllLines(table, StandardCharsets.UTF_8);
        List<String> ids = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (!line.isEmpty()) {
                ids.add(line.substring(0, line.indexOf('\t')));
            }
        }
        return ids;
    }

    /**
     * Every PSM identifier of a Percolator XML: each {@code p:psm_id} attribute and each peptide's
     * {@code psm_id} element, in document order.
     */
    static List<String> poutIds(Path pout) throws IOException {
        String text = Files.readString(pout, StandardCharsets.UTF_8);
        List<String> ids = new ArrayList<>();
        Matcher attribute = PSM_ID_ATTRIBUTE.matcher(text);
        while (attribute.find()) {
            ids.add(attribute.group(1));
        }
        Matcher element = PSM_ID_ELEMENT.matcher(text);
        while (element.find()) {
            ids.add(element.group(1));
        }
        return ids;
    }

    /**
     * Requires every identifier of a tab-separated file to be {@code <base>_<scan>_<charge>_<rank>}
     * for one of the bases.
     *
     * @return how many identifiers the file holds
     */
    static int assertIds(Path table, List<String> bases) throws IOException {
        return assertIds(table.toString(), firstColumn(table), bases);
    }

    /**
     * Requires every identifier to be {@code <base>_<scan>_<charge>_<rank>} for one of the bases,
     * and so to hold no path separator.
     *
     * @return how many identifiers there are
     */
    static int assertIds(String where, List<String> ids, List<String> bases) {
        List<Pattern> shapes = new ArrayList<>();
        for (String base : bases) {
            assertTrue(
                    base.indexOf('/') < 0 && base.indexOf('\\') < 0,
                    () -> "a base holds no path separator: " + base);
            shapes.add(Pattern.compile(Pattern.quote(base) + "_[0-9]+_[0-9]+_[0-9]+"));
        }
        assertTrue(!ids.isEmpty(), () -> where + " holds no identifier");
        for (String id : ids) {
            assertTrue(
                    shapes.stream().anyMatch(shape -> shape.matcher(id).matches()),
                    () ->
                            where
                                    + ": the identifier \""
                                    + id
                                    + "\" is not <base>_<scan>_<charge>_<rank> for a base of "
                                    + bases);
        }
        return ids.size();
    }

    /**
     * The {@code run-comet} step as it was before Phase 16 unit 3b: the product's own commands,
     * each with {@code -N} naming the absolute output base and the run directory as its working
     * directory.
     */
    static StepAction runCometAsBefore(PreparedRun prepared) {
        CometRun run = prepared.cometRun();
        SearchSteps.RunComet current = new SearchSteps.RunComet(run);
        return new StepAction() {
            @Override
            public StepDeclaration declaration() {
                return current.declaration();
            }

            @Override
            public void execute(StepContext context)
                    throws StepFailedException, IOException, InterruptedException {
                RunLayout layout = run.layout();
                List<OutputBase> outputs = run.outputs();
                List<ToolCommand> commands = current.commands();
                List<Invocation> invocations = new ArrayList<>();
                for (int index = 0; index < outputs.size(); index++) {
                    OutputBase output = outputs.get(index);
                    ToolCommand now = commands.get(index);
                    List<String> argv = new ArrayList<>(now.argv());
                    // Whatever -N the product gives today is replaced, so that this is the
                    // earlier command even on a tree where the product still builds it.
                    int option = -1;
                    for (int at = 0; at < argv.size(); at++) {
                        if (argv.get(at).startsWith("-N")) {
                            option = at;
                        }
                    }
                    if (option != argv.size() - 2) {
                        throw new StepFailedException("no -N before the input in " + argv);
                    }
                    argv.set(option, "-N" + layout.cometOutputBase(output.base()));
                    invocations.add(
                            new Invocation(
                                    output.stageId(),
                                    run.tool(),
                                    new ToolCommand(argv, layout.root(), now.environment())));
                }
                context.invokeAll(invocations, run.threads());
            }
        };
    }

    /**
     * Runs a prepared run with {@link #runCometAsBefore} in place of its {@code run-comet} step,
     * and waits for it.
     */
    static RunResult runAsBefore(RealProject project, PreparedRun prepared)
            throws IOException, InterruptedException, ReuseRefusedException {
        Map<EngineStep, StepAction> actions = new java.util.EnumMap<>(prepared.actions());
        actions.put(EngineStep.RUN_COMET, runCometAsBefore(prepared));
        RunRequest request =
                new RunRequest(
                        project.store(),
                        project.lock(),
                        prepared.layout(),
                        prepared.plan(),
                        prepared.inputs(),
                        java.util.Set.of(),
                        actions,
                        RealProject.application(),
                        prepared.settings());
        return project.engine()
                .start(request, StepStateListener.NONE)
                .await(RealProject.RUN_BOUND)
                .orElseThrow();
    }
}
