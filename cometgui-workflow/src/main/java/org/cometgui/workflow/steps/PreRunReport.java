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

import java.util.List;
import java.util.Objects;
import org.cometgui.domain.params.PreRunFacts;
import org.cometgui.params.comet.validation.Finding;
import org.cometgui.params.comet.validation.ValidationReport;

/**
 * What the pre-run check found: the problems with the files and the selection that no parameter
 * rule can see, and the one validator's report over the model and the file-system facts.
 *
 * <p>This is Run readiness's engine half (P8-16) and the reason a run is refused: any problem, or
 * any error in the validator's report -- the decoy blocks ({@code R-DEC-02}) and the index
 * compatibility check among them -- {@linkplain #blocked() blocks} the run before a run directory
 * exists and before any process starts. Warnings do not block.
 *
 * @param problems what is wrong with the files or the selection, each a sentence naming the file;
 *     in the order they were found
 * @param validation the validator's report over the model and {@code facts}
 * @param facts the file-system facts the validator was given: the FASTA's decoy census and the
 *     description of an existing index Comet would read, each when it could be taken
 */
public record PreRunReport(List<String> problems, ValidationReport validation, PreRunFacts facts) {

    /**
     * Requires every component and copies the list.
     *
     * @throws NullPointerException naming a component or problem that is {@code null}
     */
    public PreRunReport {
        problems = List.copyOf(problems);
        Objects.requireNonNull(validation, "validation");
        Objects.requireNonNull(facts, "facts");
    }

    @Override
    public List<String> problems() {
        return List.copyOf(problems);
    }

    /**
     * Whether the run must not start.
     *
     * @return {@code true} if there is a problem or the validator reported an error
     */
    public boolean blocked() {
        return !problems.isEmpty() || validation.hasErrors();
    }

    /**
     * Everything that blocks the run, in words: each problem, then each validator error with its
     * rule's identifier.
     *
     * @return the message; {@code "nothing blocks the run"} when nothing does
     */
    public String message() {
        if (!blocked()) {
            return "nothing blocks the run";
        }
        StringBuilder text = new StringBuilder("the run cannot start:");
        for (String problem : problems) {
            text.append("\n- ").append(problem);
        }
        for (Finding error : validation.errors()) {
            text.append("\n- [").append(error.rule().id()).append("] ").append(error.message());
        }
        return text.toString();
    }
}
