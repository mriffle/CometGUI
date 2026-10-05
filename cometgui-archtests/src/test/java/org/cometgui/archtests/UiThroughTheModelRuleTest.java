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

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.stream.Stream;
import org.cometgui.ui.archfixtures.GoesThroughTheModel;
import org.cometgui.ui.archfixtures.HoldsAValueCodec;
import org.cometgui.ui.archfixtures.HoldsTheParameterValueCodec;
import org.cometgui.ui.archfixtures.ParsesWithARegex;
import org.cometgui.ui.archfixtures.ReadsANumber;
import org.cometgui.ui.archfixtures.ReadsParamsLines;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The negative and positive controls for {@link UiThroughTheModelRule}: each forbidden door has a
 * fixture in {@code org.cometgui.ui.archfixtures} the shared rule must reject, naming the fixture
 * and the class it reached, and the legal shape -- text handed to the model, its refusal shown --
 * must pass.
 */
class UiThroughTheModelRuleTest {

    /** A class of the value package that is not a codec: the rule must leave it open. */
    private static final String VALUE_EXCEPTION =
            "org.cometgui.params.comet.value.ValueSyntaxException";

    @Test
    @DisplayName("rejects a UI class that reads comet.params lines")
    void rejectsTheLineReader() {
        String message = rejectionOf(ReadsParamsLines.class);
        assertAll(
                () -> namesInTheViolation(message, "P7-1"),
                () -> namesInTheViolation(message, "org.cometgui.ui.archfixtures.ReadsParamsLines"),
                () ->
                        namesInTheViolation(
                                message, "org.cometgui.params.comet.parser.ParamsLineReader.read"));
    }

    @Test
    @DisplayName("rejects a UI class that reads a number with Numbers")
    void rejectsNumbers() {
        String message = rejectionOf(ReadsANumber.class);
        assertAll(
                () -> namesInTheViolation(message, "org.cometgui.ui.archfixtures.ReadsANumber"),
                () ->
                        namesInTheViolation(
                                message, "org.cometgui.params.comet.value.Numbers.decimal"));
    }

    @Test
    @DisplayName("rejects a UI class that merely holds a value codec")
    void rejectsAValueCodec() {
        String message = rejectionOf(HoldsAValueCodec.class);
        assertAll(
                () -> namesInTheViolation(message, "org.cometgui.ui.archfixtures.HoldsAValueCodec"),
                () ->
                        namesInTheViolation(
                                message, "<org.cometgui.params.comet.value.VariableModCodec>"));
    }

    @Test
    @DisplayName("rejects a UI class that holds the model's ParameterValueCodec")
    void rejectsTheParameterValueCodec() {
        String message = rejectionOf(HoldsTheParameterValueCodec.class);
        assertAll(
                () ->
                        namesInTheViolation(
                                message,
                                "org.cometgui.ui.archfixtures.HoldsTheParameterValueCodec"),
                () ->
                        namesInTheViolation(
                                message, "<org.cometgui.params.comet.model.ParameterValueCodec>"));
    }

    @Test
    @DisplayName("rejects a UI class that parses with java.util.regex")
    void rejectsRegex() {
        String message = rejectionOf(ParsesWithARegex.class);
        assertAll(
                () -> namesInTheViolation(message, "org.cometgui.ui.archfixtures.ParsesWithARegex"),
                () -> namesInTheViolation(message, "java.util.regex.Pattern"),
                () -> namesInTheViolation(message, "java.util.regex.Matcher"));
    }

    @Test
    @DisplayName("accepts a UI class that hands text to the model and shows its refusal")
    void acceptsTheModel() {
        JavaClasses legal = new ClassFileImporter().importClasses(GoesThroughTheModel.class);
        boolean namesTheException =
                legal.get(GoesThroughTheModel.class).getDirectDependenciesFromSelf().stream()
                        .anyMatch(d -> d.getTargetClass().getName().equals(VALUE_EXCEPTION));
        assertAll(
                () ->
                        assertTrue(
                                namesTheException,
                                "the legal fixture no longer names the value package's exception,"
                                        + " so accepting it proves nothing about the rule leaving"
                                        + " that package's non-codec classes open"),
                () ->
                        assertDoesNotThrow(
                                () -> UiThroughTheModelRule.UI_GOES_THROUGH_THE_MODEL.check(legal),
                                "the rule rejected the model's own operations: a rule that"
                                        + " rejects everything passes every negative control"));
    }

    @Test
    @DisplayName("the illegal fixtures never reach the product class import")
    void theFixturesAreInvisibleToTheProductImport() {
        JavaClasses product = ProductClasses.all();
        assertAll(
                Stream.of(
                                ReadsParamsLines.class,
                                ReadsANumber.class,
                                HoldsAValueCodec.class,
                                HoldsTheParameterValueCodec.class,
                                ParsesWithARegex.class,
                                GoesThroughTheModel.class)
                        .map(
                                fixture ->
                                        () ->
                                                assertFalse(
                                                        product.contain(fixture),
                                                        fixture.getName()
                                                                + " is in ProductClasses.all()")));
    }

    private static String rejectionOf(Class<?> fixture) {
        JavaClasses justTheFixture = new ClassFileImporter().importClasses(fixture);
        assertEquals(1, justTheFixture.size(), fixture.getName());
        AssertionError rejected =
                assertThrows(
                        AssertionError.class,
                        () -> UiThroughTheModelRule.UI_GOES_THROUGH_THE_MODEL.check(justTheFixture),
                        () ->
                                "the rule accepted "
                                        + fixture.getName()
                                        + "; the rule LayeringRulesTest grades the product with"
                                        + " has no teeth");
        return rejected.getMessage();
    }

    private static void namesInTheViolation(String message, String expected) {
        assertTrue(
                message.contains(expected),
                () ->
                        "the violation never mentions '"
                                + expected
                                + "'. The message was:"
                                + System.lineSeparator()
                                + message);
    }
}
