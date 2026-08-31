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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.stream.Stream;
import org.cometgui.archtests.fixtures.CallsProcessBuilderStartPipeline;
import org.cometgui.archtests.fixtures.CallsRuntimeExec;
import org.cometgui.archtests.fixtures.ConstructsProcessBuilder;
import org.cometgui.archtests.fixtures.HoldsProcessBuilder;
import org.cometgui.archtests.fixtures.HoldsProcessBuilderRedirect;
import org.cometgui.archtests.fixtures.StartsNoProcess;
import org.cometgui.tools.process.fixtures.ConstructsProcessBuilderInsideTheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The negative and positive controls for R-PROC-02, in the suite rather than in a shell harness.
 *
 * <p><strong>What was missing.</strong> The only proof that {@link
 * ProcessCreationRule#CONFINED_TO_THE_PROCESS_SERVICE} rejects anything lived in
 * scripts/verify-test-gates.sh, which copies the tree, writes an illegal class into the copy and
 * watches the build go red. That is a good check and it stays, but it runs on demand and it damages
 * a sandbox to do it. The proof a rule has teeth should travel with the rule and run whenever it
 * runs, so every clause of R-PROC-02 has a fixture here that only that clause rejects.
 *
 * <p><strong>Why the fixtures are imported separately.</strong> {@link ProductClasses#all()} sets
 * {@code ImportOption.DoNotIncludeTests}, so these fixtures -- which are test sources -- are
 * invisible to it and the product rule set stays green with them in the tree. That exclusion is
 * load-bearing rather than incidental, so {@link #theIllegalFixturesAreInvisibleToTheProductImport}
 * asserts it directly. The controls below use a plain {@link ClassFileImporter} over one fixture at
 * a time, which also keeps each failure message about exactly one violation.
 *
 * <p><strong>Why the rule is not restated here.</strong> Every check below runs the same {@code
 * ArchRule} object {@link LayeringRulesTest} grades the product with. A control written against a
 * copy of a rule proves the copy has teeth and says nothing at all about the rule the build runs.
 */
class ProcessCreationRuleTest {

    @Test
    @DisplayName("R-PROC-02 rejects a new ProcessBuilder outside the process service")
    void rejectsConstructingAProcessBuilderOutsideTheProcessService() {
        String message = rejectionOf(ConstructsProcessBuilder.class);

        assertAll(
                () -> namesInTheViolation(message, "R-PROC-02"),
                () ->
                        namesInTheViolation(
                                message,
                                "org.cometgui.archtests.fixtures.ConstructsProcessBuilder"),
                () -> namesInTheViolation(message, "calls constructor <java.lang.ProcessBuilder."));
    }

    @Test
    @DisplayName("R-PROC-02 rejects Runtime.exec outside the process service")
    void rejectsRuntimeExecOutsideTheProcessService() {
        String message = rejectionOf(CallsRuntimeExec.class);

        assertAll(
                () -> namesInTheViolation(message, "R-PROC-02"),
                () ->
                        namesInTheViolation(
                                message, "org.cometgui.archtests.fixtures.CallsRuntimeExec"),
                () -> namesInTheViolation(message, "calls method <java.lang.Runtime.exec("));
    }

    @Test
    @DisplayName("R-PROC-02 rejects ProcessBuilder.startPipeline outside the process service")
    void rejectsStartPipelineOutsideTheProcessService() {
        String message = rejectionOf(CallsProcessBuilderStartPipeline.class);

        assertAll(
                () -> namesInTheViolation(message, "R-PROC-02"),
                () ->
                        namesInTheViolation(
                                message,
                                "org.cometgui.archtests.fixtures.CallsProcessBuilderStartPipeline"),
                () ->
                        namesInTheViolation(
                                message, "calls method <java.lang.ProcessBuilder.startPipeline("));
    }

    @Test
    @DisplayName("R-PROC-02 rejects a class that merely holds a ProcessBuilder")
    void rejectsAClassThatMerelyHoldsAProcessBuilder() {
        /*
         * This fixture never calls anything: it declares a field, a constructor parameter and a
         * return type. Observed, not assumed -- ArchUnit reports all three as separate violations,
         * so "confined to the process service" is about the type and not only about the
         * constructor call, and a class one refactor away from starting a process is caught before
         * the refactor.
         */
        String message = rejectionOf(HoldsProcessBuilder.class);

        assertAll(
                () -> namesInTheViolation(message, "R-PROC-02"),
                () ->
                        namesInTheViolation(
                                message,
                                "Field <org.cometgui.archtests.fixtures.HoldsProcessBuilder.held>"
                                        + " has type <java.lang.ProcessBuilder>"),
                () ->
                        namesInTheViolation(
                                message, "has parameter of type <java.lang.ProcessBuilder>"),
                () -> namesInTheViolation(message, "has return type <java.lang.ProcessBuilder>"));
    }

    @Test
    @DisplayName("R-PROC-02 rejects a class that holds a ProcessBuilder.Redirect")
    void rejectsAClassThatHoldsAProcessBuilderRedirect() {
        /*
         * The clause this fixture grades is phase 03's one addition to the rule. Before it, this
         * class passed: a ProcessBuilder.Redirect is not assignable to a ProcessBuilder, so the
         * assignability clause never saw it, while a class deciding where a tool's output is
         * redirected is doing process-service work. If the name-based clause is ever removed this
         * test goes red and says which hole re-opened.
         */
        String message = rejectionOf(HoldsProcessBuilderRedirect.class);

        assertAll(
                () -> namesInTheViolation(message, "R-PROC-02"),
                () ->
                        namesInTheViolation(
                                message,
                                "org.cometgui.archtests.fixtures.HoldsProcessBuilderRedirect"),
                () -> namesInTheViolation(message, "<java.lang.ProcessBuilder$Redirect>"));
    }

    @Test
    @DisplayName("R-PROC-02 accepts a ProcessBuilder created inside the process service")
    void acceptsProcessCreationInsideTheProcessService() {
        /*
         * The positive control, and the reason the negative ones mean anything. A rule that
         * rejected every mention of ProcessBuilder anywhere would pass all five tests above and
         * would fail the product's own ProcessService on the next build; nothing above can tell
         * that rule from a correct one.
         *
         * StartsNoProcess is in the import on purpose. R-PROC-02's subject is "classes residing
         * outside the process service", so a class set holding only the legal in-package fixture
         * matches no subject at all and archRule.failOnEmptyShould fails the check -- correctly,
         * but for a reason this control is not asking about.
         */
        JavaClasses legalAndBenign =
                new ClassFileImporter()
                        .importClasses(
                                ConstructsProcessBuilderInsideTheService.class,
                                StartsNoProcess.class);
        JavaClass legal = legalAndBenign.get(ConstructsProcessBuilderInsideTheService.class);
        JavaClass benign = legalAndBenign.get(StartsNoProcess.class);

        assertAll(
                () ->
                        assertTrue(
                                dependsOnProcessBuilder(legal),
                                "the in-package fixture does not depend on java.lang.ProcessBuilder"
                                        + " at all, so a rule accepting it proves nothing about"
                                        + " legal process creation"),
                () ->
                        assertTrue(
                                legal.getPackageName().startsWith("org.cometgui.tools.process."),
                                "the legal fixture has moved out of the process service package;"
                                        + " it is no longer the case this control describes"),
                () ->
                        assertFalse(
                                benign.getPackageName().startsWith("org.cometgui.tools.process"),
                                "the benign fixture must reside OUTSIDE the process service, or"
                                        + " the rule matches no subject and failOnEmptyShould"
                                        + " fails this check for the wrong reason"),
                () ->
                        assertDoesNotThrow(
                                () ->
                                        ProcessCreationRule.CONFINED_TO_THE_PROCESS_SERVICE.check(
                                                legalAndBenign),
                                "R-PROC-02 rejected process creation inside the process service:"
                                        + " a rule that rejects everything passes every negative"
                                        + " control in this class and breaks the product"));
    }

    @Test
    @DisplayName("the illegal fixtures never reach the product class import")
    void theIllegalFixturesAreInvisibleToTheProductImport() {
        JavaClasses product = ProductClasses.all();

        assertAll(
                Stream.of(
                                ConstructsProcessBuilder.class,
                                CallsRuntimeExec.class,
                                CallsProcessBuilderStartPipeline.class,
                                HoldsProcessBuilder.class,
                                HoldsProcessBuilderRedirect.class)
                        .map(
                                fixture ->
                                        () ->
                                                assertFalse(
                                                        product.contain(fixture),
                                                        fixture.getName()
                                                                + " is in ProductClasses.all();"
                                                                + " ImportOption.DoNotIncludeTests"
                                                                + " has stopped excluding this"
                                                                + " module's test classes, so"
                                                                + " LayeringRulesTest is about to"
                                                                + " fail on a fixture instead of"
                                                                + " on a product defect")));
    }

    /**
     * Checks the shared R-PROC-02 rule against one fixture and requires it to be rejected.
     *
     * @param fixture the deliberately illegal class, imported on its own so that the message that
     *     comes back is about it and nothing else
     * @return the rejection message
     */
    private static String rejectionOf(Class<?> fixture) {
        JavaClasses justTheFixture = new ClassFileImporter().importClasses(fixture);
        assertEquals(
                1,
                justTheFixture.size(),
                () ->
                        "expected exactly one imported class for "
                                + fixture.getName()
                                + ", so that the rejection below can only be about that class");

        AssertionError rejected =
                assertThrows(
                        AssertionError.class,
                        () ->
                                ProcessCreationRule.CONFINED_TO_THE_PROCESS_SERVICE.check(
                                        justTheFixture),
                        () ->
                                "R-PROC-02 accepted "
                                        + fixture.getName()
                                        + ", which creates or holds a process outside"
                                        + " org.cometgui.tools.process; the rule LayeringRulesTest"
                                        + " grades the product with has no teeth");
        return rejected.getMessage();
    }

    private static void namesInTheViolation(String message, String expected) {
        assertTrue(
                message.contains(expected),
                () ->
                        "the violation never mentions '"
                                + expected
                                + "'; a rejection that does not name what it rejected, or the"
                                + " requirement it rejected it under, cannot be acted on. The"
                                + " message was:"
                                + System.lineSeparator()
                                + message);
    }

    private static boolean dependsOnProcessBuilder(JavaClass candidate) {
        return candidate.getDirectDependenciesFromSelf().stream()
                .anyMatch(
                        dependency ->
                                dependency
                                        .getTargetClass()
                                        .getName()
                                        .equals("java.lang.ProcessBuilder"));
    }
}
