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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves the architecture rules are graded against the real product, before they are graded at all.
 *
 * <p>The failure mode this exists to stop is silent and total: if the class import comes back empty
 * -- a wrong package name, a missing module dependency, an ArchUnit that cannot read Java 25 class
 * files -- then every {@code noClasses()} rule in {@link LayeringRulesTest} passes, the build is
 * green, and nothing is being checked. So the size and the composition of the import are asserted
 * here, and the census is written to the build directory for scripts/build.sh to read back.
 */
class ClassImportCensusTest {

    /**
     * A floor, not the exact count: it is deliberately below today's number so that a later phase
     * adding classes does not have to edit it, and far above zero so that an empty or truncated
     * import fails. The exact-composition guarantee is {@link
     * #everyProductModuleContributesClasses} below, which is the assertion that actually holds as
     * the product grows.
     */
    private static final int MINIMUM_IMPORTED_CLASSES = 50;

    private static Map<String, Long> census(JavaClasses classes) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String modulePackage : ProductClasses.MODULE_PACKAGES) {
            long inThisModule =
                    classes.stream()
                            .filter(candidate -> belongsTo(candidate, modulePackage))
                            .count();
            counts.put(modulePackage, inThisModule);
        }
        return counts;
    }

    /**
     * True when the class is in the package or one of its subpackages, but not in a sibling module
     * that happens to share the prefix: {@code org.cometgui.tools.process} is its own module, so a
     * class in it does not count towards {@code org.cometgui.tools}.
     */
    private static boolean belongsTo(JavaClass candidate, String modulePackage) {
        String name = candidate.getPackageName();
        if (!name.equals(modulePackage) && !name.startsWith(modulePackage + ".")) {
            return false;
        }
        return ProductClasses.MODULE_PACKAGES.stream()
                .noneMatch(
                        other ->
                                other.length() > modulePackage.length()
                                        && (name.equals(other) || name.startsWith(other + ".")));
    }

    @Test
    @DisplayName("the import is not empty, so no rule can pass by having nothing to check")
    void theImportIsNotEmpty() {
        JavaClasses classes = ProductClasses.all();

        assertTrue(
                classes.size() >= MINIMUM_IMPORTED_CLASSES,
                () ->
                        "ArchUnit imported "
                                + classes.size()
                                + " classes from "
                                + ProductClasses.ROOT_PACKAGE
                                + ", which is below the floor of "
                                + MINIMUM_IMPORTED_CLASSES
                                + ". Every rule in this module would pass vacuously. Check the"
                                + " module dependencies of cometgui-archtests and that ArchUnit"
                                + " can read this JDK's class files.");
    }

    @Test
    @DisplayName("every product module contributes at least one class to the import")
    void everyProductModuleContributesClasses() {
        Map<String, Long> counts = census(ProductClasses.all());

        assertAll(
                counts.entrySet().stream()
                        .map(
                                entry ->
                                        () ->
                                                assertTrue(
                                                        entry.getValue() > 0,
                                                        "no classes were imported from "
                                                                + entry.getKey()
                                                                + "; that module is missing from"
                                                                + " the archtests class path and"
                                                                + " its rules check nothing")));
    }

    @Test
    @DisplayName(
            "three known classes are present, so the import is of the product and not of stubs")
    void namedProductClassesArePresent() {
        JavaClasses classes = ProductClasses.all();

        assertAll(
                () ->
                        assertTrue(
                                classes.contain("org.cometgui.domain.build.BuildIdentity"),
                                "the domain module's only behavioural class is missing"),
                () ->
                        assertTrue(
                                classes.contain("org.cometgui.ui.view.ShellView"),
                                "the UI module is missing from the import"),
                () ->
                        assertTrue(
                                classes.contain("org.cometgui.app.bootstrap.CometGuiApplication"),
                                "the application module is missing from the import"));
    }

    @Test
    @DisplayName("a rule that matches no class fails instead of passing quietly")
    void aRuleThatMatchesNothingFails() {
        /*
         * ArchUnit's archRule.failOnEmptyShould defaults to true, and this module keeps it that
         * way (src/test/resources/archunit.properties says so explicitly). This test is what makes
         * that a fact rather than an assumption: it builds a rule over a package that does not
         * exist and requires the check to fail. If a future ArchUnit, or a stray properties file,
         * ever turns the setting off, every misspelt package name in LayeringRulesTest would start
         * passing silently -- and this test goes red first.
         */
        ArchRule ruleOverNothing =
                noClasses()
                        .that()
                        .resideInAPackage("org.cometgui.nosuchlayer..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("javafx..");

        AssertionError thrown =
                assertThrows(
                        AssertionError.class, () -> ruleOverNothing.check(ProductClasses.all()));

        assertTrue(
                thrown.getMessage().contains("failed to check any classes"),
                "expected the empty-rule diagnostic, but was: " + thrown.getMessage());
    }

    @Test
    @DisplayName("the census is written to the build directory as evidence for scripts/build.sh")
    void writesTheCensusForTheBuildScript() throws IOException {
        JavaClasses classes = ProductClasses.all();
        Map<String, Long> counts = census(classes);
        Path evidence = buildDirectory().resolve("archunit-import.txt");

        String report =
                "imported-classes "
                        + classes.size()
                        + System.lineSeparator()
                        + counts.entrySet().stream()
                                .map(entry -> entry.getKey() + " " + entry.getValue())
                                .collect(Collectors.joining(System.lineSeparator()))
                        + System.lineSeparator();
        Files.writeString(evidence, report);

        assertEquals(
                report,
                Files.readString(evidence),
                "the census file must contain what this run measured, not a stale copy");
    }

    private static Path buildDirectory() {
        String configured = System.getProperty("cometgui.buildDirectory");
        assertTrue(
                configured != null && !configured.isBlank(),
                "cometgui.buildDirectory is not set; surefire in the parent POM must pass it");
        return Path.of(configured);
    }
}
