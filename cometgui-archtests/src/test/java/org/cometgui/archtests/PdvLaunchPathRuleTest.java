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

package org.cometgui.archtests;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Duration;
import org.cometgui.archtests.fixtures.ConstructsPdvFigures;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.tools.pdv.PdvFigures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every PDV figure launch goes through the one recording launch path (phase 11, design decision
 * P11-4): only {@code org.cometgui.workflow.pdv} -- where {@code PdvLaunches} hands out the runner
 * that records each launch in the run's event log before it starts -- may construct {@link
 * PdvFigures}. A construction anywhere else could hand PDV the application's own process service
 * and launch it with no record. Graded against the product and against a fixture that must be
 * rejected, both with the same rule object.
 */
class PdvLaunchPathRuleTest {

    /** The rule. */
    static final ArchRule PDV_FIGURES_ONLY_THROUGH_THE_RECORDING_PATH =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("org.cometgui.workflow.pdv..")
                    .should()
                    .callConstructor(PdvFigures.class, ProcessRunner.class, Duration.class)
                    .because(
                            "P11-4: every PDV launch is recorded in the run's event log before it"
                                    + " starts, and only org.cometgui.workflow.pdv hands PDV a"
                                    + " runner that does that");

    @Test
    @DisplayName("no product class outside org.cometgui.workflow.pdv constructs PdvFigures")
    void theProductKeepsTheRule() {
        PDV_FIGURES_ONLY_THROUGH_THE_RECORDING_PATH.check(ProductClasses.all());
    }

    @Test
    @DisplayName("the rule rejects a class that constructs PdvFigures elsewhere, naming it")
    void theRuleRejectsAShortcut() {
        JavaClasses fixture = new ClassFileImporter().importClasses(ConstructsPdvFigures.class);
        assertEquals(1, fixture.size());

        AssertionError rejected =
                assertThrows(
                        AssertionError.class,
                        () -> PDV_FIGURES_ONLY_THROUGH_THE_RECORDING_PATH.check(fixture));

        String message = rejected.getMessage();
        assertTrue(message.contains("P11-4"), message);
        assertTrue(
                message.contains("org.cometgui.archtests.fixtures.ConstructsPdvFigures.shortcut("),
                message);
        assertTrue(
                message.contains("calls constructor <org.cometgui.tools.pdv.PdvFigures.<init>("),
                message);
    }
}
