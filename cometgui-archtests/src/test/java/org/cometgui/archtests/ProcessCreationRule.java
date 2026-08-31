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

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;

/**
 * R-PROC-02, built exactly once.
 *
 * <p><strong>Why this is a constant and not two rules.</strong> {@link LayeringRulesTest} grades
 * the product with this rule; {@link ProcessCreationRuleTest} grades deliberately illegal fixtures
 * with it and requires each to be rejected. Those are the two halves of one claim -- "the rule the
 * build runs has teeth" -- and they are only two halves of the same claim while they hold the same
 * object. A negative control written against a copied rule proves that the copy has teeth and says
 * nothing whatever about the rule the build actually runs; that is this project's signature defect,
 * so the rule is built here, once, and neither test may restate it.
 *
 * <p><strong>Why the rule is wider than the exit gate item.</strong> Phase 03's gate item 5 asks
 * only that {@code ProcessBuilder} be confined to the process service. A rule that stopped there
 * would leave {@code Runtime.exec} open, which starts a process just as well, so phase 01 covered
 * it too and it stays covered. Every clause below has a fixture in {@code
 * org.cometgui.archtests.fixtures} that it, and only it, rejects.
 */
final class ProcessCreationRule {

    /**
     * {@code java.lang.ProcessBuilder} and its nested types, by name.
     *
     * <p>The assignability clause below cannot see {@code ProcessBuilder.Redirect}: a {@code
     * Redirect} is not assignable to a {@code ProcessBuilder}. Observed, not assumed -- with the
     * assignability clause alone, {@link
     * org.cometgui.archtests.fixtures.HoldsProcessBuilderRedirect} passes the rule. Deciding where
     * a tool's stdout is redirected is process-service work whether or not the class holding the
     * decision ever calls a constructor, so this clause closes that hole. It is a hardening: it
     * rejects strictly more than the rule did before, and rejects nothing the product does today.
     */
    private static final String PROCESS_BUILDER_AND_ITS_NESTED_TYPES =
            "java\\.lang\\.ProcessBuilder(\\$.*)?";

    /**
     * No class outside {@link ProductClasses#PROCESS_SERVICE} may create, call, hold or name a
     * process builder, and none may call {@code Runtime.exec}.
     */
    static final ArchRule CONFINED_TO_THE_PROCESS_SERVICE =
            noClasses()
                    .that()
                    .resideOutsideOfPackage(ProductClasses.PROCESS_SERVICE)
                    .should()
                    .dependOnClassesThat()
                    .areAssignableTo(ProcessBuilder.class)
                    .orShould()
                    .callMethodWhere(
                            target(owner(equivalentTo(Runtime.class))).and(target(name("exec"))))
                    .orShould()
                    .dependOnClassesThat()
                    .haveNameMatching(PROCESS_BUILDER_AND_ITS_NESTED_TYPES)
                    .because(
                            "R-PROC-02: processes shall be started using argument arrays, never by"
                                    + " constructing a shell command string, and ProcessBuilder"
                                    + " construction shall be confined to the process service");

    private ProcessCreationRule() {}
}
