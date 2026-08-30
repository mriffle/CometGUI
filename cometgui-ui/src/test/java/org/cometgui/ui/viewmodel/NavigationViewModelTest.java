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

package org.cometgui.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.Property;
import org.cometgui.ui.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The navigation selection model: value, boundary, rejection and observability. */
class NavigationViewModelTest {

    private final NavigationViewModel navigation = new NavigationViewModel();

    @Nested
    @DisplayName("the section list")
    class SectionList {

        @Test
        @DisplayName("is the display order from SectionId, primary sections first")
        void isTheDisplayOrder() {
            assertEquals(SectionId.displayOrder(), List.copyOf(navigation.sections()));
        }

        @Test
        @DisplayName("cannot be modified by a view")
        void cannotBeModified() {
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> navigation.sections().add(SectionId.RUN));
            assertThrows(
                    UnsupportedOperationException.class, () -> navigation.sections().remove(0));
        }
    }

    @Nested
    @DisplayName("selection")
    class Selection {

        @Test
        @DisplayName("starts on Run")
        void startsOnRun() {
            assertEquals(SectionId.RUN, navigation.selectedSection());
            assertEquals(SectionId.RUN, navigation.selectedSectionProperty().get());
        }

        @Test
        @DisplayName("moves to the section that was selected")
        void selectMovesTheSelection() {
            navigation.select(SectionId.PROVENANCE);
            assertEquals(SectionId.PROVENANCE, navigation.selectedSection());
            navigation.select(SectionId.SETTINGS);
            assertEquals(SectionId.SETTINGS, navigation.selectedSection());
        }

        @Test
        @DisplayName("rejects null, naming the argument, and keeps the previous selection")
        void selectRejectsNull() {
            navigation.select(SectionId.RESULTS);
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () -> navigation.select(Nulls.of(SectionId.class)));
            assertEquals("section", thrown.getMessage());
            assertEquals(SectionId.RESULTS, navigation.selectedSection());
        }

        @Test
        @DisplayName("is published read-only, so nothing outside the view-model can write it")
        void thePublishedPropertyIsNotWritable() {
            assertFalse(
                    navigation.selectedSectionProperty() instanceof Property,
                    "the published selection property must not be writable or bindable");
        }
    }

    @Nested
    @DisplayName("keyboard movement")
    class KeyboardMovement {

        @Test
        @DisplayName("selectNext steps one section later and reports that it moved")
        void selectNextStepsForward() {
            assertTrue(navigation.selectNext());
            assertEquals(SectionId.COMET_PARAMETERS, navigation.selectedSection());
        }

        @Test
        @DisplayName("selectPrevious steps one section earlier and reports that it moved")
        void selectPreviousStepsBackward() {
            navigation.select(SectionId.SETTINGS);
            assertTrue(navigation.selectPrevious());
            assertEquals(SectionId.TOOL_MANAGER, navigation.selectedSection());
        }

        /**
         * Walks the navigation with one move method until it refuses to move, or until a step
         * budget runs out.
         *
         * <p>The budget is the point. Written as a bare {@code while (navigation.selectNext())}
         * this walk never terminates if the ends ever start wrapping -- which is exactly the defect
         * the two boundary tests below exist to catch, so the suite would hang instead of failing,
         * and a hung build says far less than a red one. This was not hypothetical: it is what the
         * wrapping defect injected into {@code moveBy} actually did to this suite.
         *
         * @param move the movement to repeat, returning whether it moved
         * @return every section visited, in order, starting with the one selected on entry
         */
        private List<SectionId> walk(java.util.function.BooleanSupplier move) {
            int budget = SectionId.displayOrder().size() + 1;
            List<SectionId> visited = new ArrayList<>();
            visited.add(navigation.selectedSection());
            while (move.getAsBoolean()) {
                visited.add(navigation.selectedSection());
                if (visited.size() > budget) {
                    throw new AssertionError(
                            "the navigation moved more than "
                                    + budget
                                    + " times without refusing: the ends are wrapping. Visited: "
                                    + visited);
                }
            }
            return visited;
        }

        @Test
        @DisplayName("reaches every section from Run using selectNext alone (exit gate item 1)")
        void reachesEverySectionByKeyboardAlone() {
            assertEquals(SectionId.displayOrder(), walk(navigation::selectNext));
        }

        @Test
        @DisplayName("reaches every section backwards from Settings using selectPrevious alone")
        void reachesEverySectionBackwards() {
            navigation.select(SectionId.SETTINGS);
            List<SectionId> expected = new ArrayList<>(SectionId.displayOrder());
            java.util.Collections.reverse(expected);
            assertEquals(expected, walk(navigation::selectPrevious));
        }

        @Test
        @DisplayName("does not wrap past the last section: it reports false and stays there")
        void selectNextDoesNotWrapAtTheEnd() {
            navigation.select(SectionId.SETTINGS);
            assertFalse(navigation.selectNext(), "the ends must not wrap");
            assertEquals(SectionId.SETTINGS, navigation.selectedSection());
        }

        @Test
        @DisplayName("does not wrap before the first section: it reports false and stays there")
        void selectPreviousDoesNotWrapAtTheStart() {
            assertEquals(SectionId.RUN, navigation.selectedSection());
            assertFalse(navigation.selectPrevious(), "the ends must not wrap");
            assertEquals(SectionId.RUN, navigation.selectedSection());
        }

        @Test
        @DisplayName("fires no change event when it refuses to move at a boundary")
        void aRefusedMoveFiresNothing() {
            List<String> events = new ArrayList<>();
            navigation
                    .selectedSectionProperty()
                    .addListener((observable, was, now) -> events.add(was + "->" + now));
            assertFalse(navigation.selectPrevious());
            assertEquals(List.of(), events);
        }
    }

    @Nested
    @DisplayName("observation")
    class Observation {

        @Test
        @DisplayName("reports the section moved from and the section moved to")
        void listenersSeeBothValues() {
            List<String> events = new ArrayList<>();
            navigation
                    .selectedSectionProperty()
                    .addListener((observable, was, now) -> events.add(was + "->" + now));
            navigation.select(SectionId.CONSOLE);
            navigation.selectNext();
            navigation.selectPrevious();
            assertEquals(
                    List.of("RUN->CONSOLE", "CONSOLE->TOOL_MANAGER", "TOOL_MANAGER->CONSOLE"),
                    events);
        }

        @Test
        @DisplayName("reselecting the current section changes nothing and fires nothing")
        void reselectingTheSameSectionIsSilent() {
            List<String> events = new ArrayList<>();
            navigation.select(SectionId.LIMELIGHT);
            navigation
                    .selectedSectionProperty()
                    .addListener((observable, was, now) -> events.add(was + "->" + now));
            navigation.select(SectionId.LIMELIGHT);
            assertEquals(List.of(), events);
            assertEquals(SectionId.LIMELIGHT, navigation.selectedSection());
        }
    }
}
