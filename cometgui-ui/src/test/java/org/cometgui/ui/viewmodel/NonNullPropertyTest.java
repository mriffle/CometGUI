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
import javafx.beans.property.SimpleStringProperty;
import org.cometgui.ui.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The guarded property every view-model in this package publishes its state through.
 *
 * <p>It is package-private, so this test is the only place its guards can be aimed at directly.
 * They are reachable through the view-models as well -- {@code select(null)}, {@code
 * setMinimumSeverity(null)} -- but those go through an argument check of their own first, so the
 * property's own guard would keep passing if it were deleted. Testing it here is what makes it
 * load-bearing.
 */
class NonNullPropertyTest {

    private final NonNullProperty<String> property =
            new NonNullProperty<>(this, "exampleProperty", "initial");

    @Test
    @DisplayName("starts at the value it was given")
    void startsAtItsInitialValue() {
        assertEquals("initial", property.get());
        assertEquals("exampleProperty", property.getName());
        assertEquals(this, property.getBean());
    }

    @Test
    @DisplayName("refuses a null initial value, naming the property")
    void refusesANullInitialValue() {
        NullPointerException thrown =
                assertThrows(
                        NullPointerException.class,
                        () -> new NonNullProperty<>(this, "stageFilter", Nulls.of(String.class)));
        assertEquals("stageFilter", thrown.getMessage());
    }

    @Test
    @DisplayName("accepts a value and publishes it to the read-only view")
    void acceptsAValue() {
        property.set("second");
        assertEquals("second", property.get());
        assertEquals("second", property.getReadOnlyProperty().get());
    }

    @Test
    @DisplayName("refuses a null from set, naming the property, and keeps the old value")
    void refusesNullFromSet() {
        property.set("kept");
        NullPointerException thrown =
                assertThrows(
                        NullPointerException.class, () -> property.set(Nulls.of(String.class)));
        assertEquals("exampleProperty", thrown.getMessage());
        assertEquals("kept", property.get());
    }

    @Test
    @DisplayName("refuses a null from setValue, which is the form a binding uses")
    void refusesNullFromSetValue() {
        NullPointerException thrown =
                assertThrows(
                        NullPointerException.class,
                        () -> property.setValue(Nulls.of(String.class)));
        assertEquals("exampleProperty", thrown.getMessage());
        assertEquals("initial", property.get());
    }

    @Test
    @DisplayName("refuses to be bound, because a binding would bypass the null check")
    void refusesToBeBound() {
        SimpleStringProperty source = new SimpleStringProperty("from the binding");
        UnsupportedOperationException thrown =
                assertThrows(UnsupportedOperationException.class, () -> property.bind(source));
        assertTrue(
                thrown.getMessage()
                        .startsWith("the view-model property 'exampleProperty' cannot be bound:"),
                thrown.getMessage());
        assertTrue(
                thrown.getMessage().endsWith("Set it instead."),
                "the diagnostic must say what to do instead: " + thrown.getMessage());
        assertEquals("initial", property.get(), "the refused binding changed nothing");
    }

    @Test
    @DisplayName("publishes a read-only view that cannot be written or bound")
    void publishesAReadOnlyView() {
        assertFalse(
                property.getReadOnlyProperty() instanceof Property,
                "the published property must not be writable");
    }

    @Test
    @DisplayName("notifies observers of the read-only view when it changes")
    void notifiesObserversOfTheReadOnlyView() {
        List<String> events = new ArrayList<>();
        property.getReadOnlyProperty()
                .addListener((observable, was, now) -> events.add(was + "->" + now));
        property.set("changed");
        property.set("changed");
        property.set("changed again");
        assertEquals(List.of("initial->changed", "changed->changed again"), events);
    }
}
