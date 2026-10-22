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

package org.cometgui.app.uidriver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The remotable subset stays remotable (P14-3): every argument and result of every method of {@link
 * RemotableUiDriver} is a {@code String}, a {@code boolean}, an {@code int}, {@code void} or a
 * {@code List<String>} -- what can be written down and sent to another process -- and both
 * in-process drivers implement it through {@link FxUiDriver}.
 */
class RemotableUiDriverShapeTest {

    private static final Set<Type> ARGUMENTS = Set.of(String.class, boolean.class, int.class);

    private static final Set<Type> RESULTS =
            Set.of(void.class, String.class, boolean.class, int.class);

    @Test
    @DisplayName(
            "every method of the subset takes and returns only strings, booleans, ints and lists"
                    + " of strings")
    void everyMethodIsRemotable() {
        List<String> wrong = new ArrayList<>();
        for (Method method : RemotableUiDriver.class.getMethods()) {
            for (Type argument : method.getGenericParameterTypes()) {
                if (!ARGUMENTS.contains(argument)) {
                    wrong.add(method.getName() + " takes a " + argument.getTypeName());
                }
            }
            Type result = method.getGenericReturnType();
            if (!RESULTS.contains(result) && !isListOfStrings(result)) {
                wrong.add(method.getName() + " returns a " + result.getTypeName());
            }
        }
        assertEquals(List.of(), wrong);
    }

    @Test
    @DisplayName("the subset is the operations later units were promised, by name")
    void theSubsetIsTheDocumentedOne() {
        Set<String> names = new TreeSet<>();
        for (Method method : RemotableUiDriver.class.getMethods()) {
            names.add(method.getName());
        }
        assertEquals(
                new TreeSet<>(
                        List.of(
                                "accessibleTextOf",
                                "clickOn",
                                "columnsOf",
                                "exists",
                                "focusedNodeId",
                                "isDisabled",
                                "isVisible",
                                "itemsOf",
                                "pressKey",
                                "pressKeyWith",
                                "rowsOf",
                                "select",
                                "selectedTextOf",
                                "shiftTab",
                                "tab",
                                "textOf",
                                "type",
                                "typeInto")),
                names);
    }

    @Test
    @DisplayName("both in-process drivers implement the subset, through FxUiDriver")
    void bothDriversImplementIt() {
        assertTrue(RemotableUiDriver.class.isAssignableFrom(FxUiDriver.class));
        assertTrue(RemotableUiDriver.class.isAssignableFrom(TestFxUiDriver.class));
        assertTrue(RemotableUiDriver.class.isAssignableFrom(RobotFxUiDriver.class));
    }

    private static boolean isListOfStrings(Type type) {
        return type instanceof ParameterizedType list
                && list.getRawType() == List.class
                && list.getActualTypeArguments()[0] == String.class;
    }
}
