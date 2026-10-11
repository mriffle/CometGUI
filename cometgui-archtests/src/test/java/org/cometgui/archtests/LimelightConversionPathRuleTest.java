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
import java.nio.file.Path;
import java.time.Duration;
import org.cometgui.archtests.fixtures.ConstructsLimelightConverter;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.tools.api.JavaRuntime;
import org.cometgui.tools.limelight.LimelightConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every Limelight conversion goes through the one path (phase 12, design decisions P12-1 and
 * P12-5): only {@code org.cometgui.workflow.limelight} -- where {@code LimelightConversions} checks
 * every prerequisite by name before anything starts and hands the converter a runner that records
 * the conversion in the run's event log before it starts -- may construct {@link
 * LimelightConverter}. Anywhere else could hand the converter the application's own process service
 * and convert a run with nothing checked and nothing recorded. Graded against the product and
 * against a fixture that must be rejected, both with the same rule object.
 */
class LimelightConversionPathRuleTest {

    /** The rule. */
    static final ArchRule LIMELIGHT_CONVERSIONS_ONLY_THROUGH_THE_RECORDING_PATH =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("org.cometgui.workflow.limelight..")
                    .should()
                    .callConstructor(
                            LimelightConverter.class,
                            ProcessRunner.class,
                            Duration.class,
                            JavaRuntime.class,
                            Path.class)
                    .because(
                            "P12-5: every Limelight conversion is checked and recorded in the run's"
                                    + " event log before it starts, and only"
                                    + " org.cometgui.workflow.limelight hands the converter a"
                                    + " runner that does that");

    @Test
    @DisplayName(
            "no product class outside org.cometgui.workflow.limelight constructs the converter")
    void theProductKeepsTheRule() {
        LIMELIGHT_CONVERSIONS_ONLY_THROUGH_THE_RECORDING_PATH.check(ProductClasses.all());
    }

    @Test
    @DisplayName("the rule rejects a class that constructs the converter elsewhere, naming it")
    void theRuleRejectsAShortcut() {
        JavaClasses fixture =
                new ClassFileImporter().importClasses(ConstructsLimelightConverter.class);
        assertEquals(1, fixture.size());

        AssertionError rejected =
                assertThrows(
                        AssertionError.class,
                        () -> LIMELIGHT_CONVERSIONS_ONLY_THROUGH_THE_RECORDING_PATH.check(fixture));

        String message = rejected.getMessage();
        assertTrue(message.contains("P12-5"), message);
        assertTrue(
                message.contains(
                        "org.cometgui.archtests.fixtures.ConstructsLimelightConverter.shortcut("),
                message);
        assertTrue(
                message.contains(
                        "calls constructor"
                                + " <org.cometgui.tools.limelight.LimelightConverter.<init>("),
                message);
    }
}
