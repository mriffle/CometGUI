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
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The architecture rules the specification's <em>Architecture tests</em> section requires.
 *
 * <p>One test per rule, each checked against {@link ProductClasses#all()}, which {@link
 * ClassImportCensusTest} has already proved is a real import of the whole product. ArchUnit's
 * {@code archRule.failOnEmptyShould} is on, so a rule whose subject package stops matching fails
 * rather than passing empty.
 *
 * <p>Several of these rules cannot fail today, because the packages they protect hold only
 * package-info classes with no dependencies. That is the point of wiring them before the code
 * exists: the phase that fills a module inherits the rule instead of having to remember to write
 * it. scripts/verify-test-gates.sh injects real violations into a sandbox copy of the tree and
 * requires each rule to reject them, which is the only way to know a rule works before the code it
 * governs is written.
 */
class LayeringRulesTest {

    /** Packages the UI layer is allowed to reach: the domain and the application APIs. */
    private static final String[] UI_MAY_DEPEND_ON = {
        "java..",
        "javax..",
        "javafx..",
        "org.cometgui.ui..",
        "org.cometgui.domain..",
        "org.cometgui.workflow..",
        "org.cometgui.results..",
        "org.cometgui.provenance..",
        "org.cometgui.params.."
    };

    @Test
    @DisplayName("the domain does not depend on JavaFX")
    void domainDoesNotDependOnJavaFx() {
        noClasses()
                .that()
                .resideInAPackage("org.cometgui.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("javafx..", "com.sun.javafx..")
                .because(
                        "the workflow engine and domain logic shall be usable from tests without"
                                + " launching JavaFX (specification, Software Architecture)")
                .check(ProductClasses.all());
    }

    @Test
    @DisplayName("the UI depends only on the domain and the application APIs")
    void uiDependsOnlyOnDomainAndApplicationApis() {
        classes()
                .that()
                .resideInAPackage("org.cometgui.ui..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(UI_MAY_DEPEND_ON)
                .because(
                        "ui may depend on domain and application APIs and on nothing"
                                + " else: reaching into the tool adapters or the installer from"
                                + " a view is how scientific logic ends up in a controller")
                .check(ProductClasses.all());
    }

    @Test
    @DisplayName("tool adapters do not depend on UI classes")
    void toolAdaptersDoNotDependOnUi() {
        noClasses()
                .that()
                .resideInAPackage(ProductClasses.TOOLS_ADAPTERS)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.cometgui.ui..", "javafx..")
                .because(
                        "a tool adapter is driven by the workflow engine and must run"
                                + " in a test with no toolkit started (specification,"
                                + " Architecture tests)")
                .check(ProductClasses.all());
    }

    @Test
    @DisplayName("provenance and hashing do not depend on the UI")
    void provenanceAndHashingDoNotDependOnUi() {
        noClasses()
                .that()
                .resideInAPackage("org.cometgui.provenance..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.cometgui.ui..", "javafx..")
                .because(
                        "a provenance record must be reproducible from a headless"
                                + " re-run, so nothing in it may come from a JavaFX control")
                .check(ProductClasses.all());
    }

    @Test
    @DisplayName("the parameter parser and writer do not depend on JavaFX")
    void parameterParserAndWriterDoNotDependOnJavaFx() {
        noClasses()
                .that()
                .resideInAnyPackage("org.cometgui.params..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("javafx..", "com.sun.javafx..")
                .because(
                        "comet.params is written and re-read by tests and by the workflow engine"
                                + " with no UI present (specification, Architecture tests)")
                .check(ProductClasses.all());
    }

    @Test
    @DisplayName("the major layers have no dependency cycles")
    void majorLayersAreFreeOfCycles() {
        slices().matching("org.cometgui.(*)..")
                .should()
                .beFreeOfCycles()
                .because(
                        "a cycle between two layers means neither can be tested,"
                                + " replaced or reasoned about on its own")
                .check(ProductClasses.all());
    }

    @Test
    @DisplayName("process creation is confined to the process service (R-PROC-02)")
    void processCreationIsConfinedToTheProcessService() {
        noClasses()
                .that()
                .resideOutsideOfPackage(ProductClasses.PROCESS_SERVICE)
                .should()
                .dependOnClassesThat()
                .areAssignableTo(ProcessBuilder.class)
                .orShould()
                .callMethodWhere(
                        target(owner(equivalentTo(Runtime.class))).and(target(name("exec"))))
                .because(
                        "R-PROC-02: processes shall be started using argument arrays, never by"
                                + " constructing a shell command string, and ProcessBuilder"
                                + " construction shall be confined to the process service")
                .check(ProductClasses.all());
    }

    @Test
    @DisplayName("the UI contains no hashing, download or archive-extraction logic")
    void uiContainsNoHashingDownloadOrArchiveLogic() {
        /*
         * The specification's related rule is "JavaFX controllers contain no scientific, hashing,
         * download or parsing logic". Three quarters of it are expressible as a dependency rule
         * and are expressed here: hashing means java.security, download means java.net, archive
         * handling means java.util.zip and java.util.jar.
         *
         * "No scientific logic" and "no parsing logic" are NOT expressible this way and this rule
         * does not pretend to cover them: a hand-written q-value comparison or a split(",") loop
         * inside a controller uses nothing but java.lang and java.util and no dependency rule can
         * see it. Those stay a review obligation and are constrained indirectly, by the layering
         * rule above -- a controller that cannot reach the results or install packages has nothing
         * to parse. If a later phase finds an honest structural expression of them, it belongs
         * here; a rule that looked like it covered them but did not would be worse than saying so.
         */
        noClasses()
                .that()
                .resideInAPackage("org.cometgui.ui..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "java.security..", "java.net..", "java.util.zip..", "java.util.jar..")
                .because(
                        "no scientific logic, hashing, download or parsing code lives in JavaFX"
                                + " controllers (specification, Architectural style)")
                .check(ProductClasses.all());
    }
}
