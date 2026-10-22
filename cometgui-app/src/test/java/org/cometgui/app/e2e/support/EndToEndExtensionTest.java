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

package org.cometgui.app.e2e.support;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The opt-in (P14-2) enables an end-to-end class for exactly {@code -Dcometgui.e2e=true}, and
 * otherwise disables it with a reason that names the value seen and the flag that enables it; and
 * {@link EndToEnd} carries the extension and the tag. The real skip, run and locale failure of a
 * marked class are shown by running {@code EndToEndCanaryTest} three ways (recorded in the work
 * log), because a condition proved only on its own would not show surefire recording the skip.
 */
class EndToEndExtensionTest {

    @Test
    @DisplayName("exactly \"true\" enables the class")
    void trueEnables() {
        ConditionEvaluationResult result = EndToEndExtension.decide(Optional.of("true"));
        assertFalse(result.isDisabled());
        assertEquals(Optional.of("cometgui.e2e=true: end-to-end tests run"), result.getReason());
    }

    @Test
    @DisplayName("an unset property disables the class, saying so and naming the flag")
    void unsetDisables() {
        ConditionEvaluationResult result = EndToEndExtension.decide(Optional.empty());
        assertTrue(result.isDisabled());
        assertEquals(
                Optional.of(
                        "end-to-end test, not run: the system property cometgui.e2e is not set."
                                + " It runs real tools against real data for minutes, so it is"
                                + " opt-in; run with -Dcometgui.e2e=true, under a UTF-8 locale"
                                + " (export LC_ALL=C.UTF-8 before mvn)."),
                result.getReason());
    }

    @Test
    @DisplayName("any other value -- false, TRUE, yes, empty -- disables it, naming the value")
    void anyOtherValueDisables() {
        for (String value : new String[] {"false", "TRUE", "yes", "", " true"}) {
            ConditionEvaluationResult result = EndToEndExtension.decide(Optional.of(value));
            assertAll(
                    "the value \"" + value + "\"",
                    () -> assertTrue(result.isDisabled()),
                    () ->
                            assertTrue(
                                    result.getReason()
                                            .orElseThrow()
                                            .startsWith(
                                                    "end-to-end test, not run: the system property"
                                                            + " cometgui.e2e is \""
                                                            + value
                                                            + "\".")));
        }
    }

    @Test
    @DisplayName("@EndToEnd registers the extension and tags the class e2e")
    void theAnnotationCarriesTheExtensionAndTheTag() {
        ExtendWith extension = EndToEnd.class.getAnnotation(ExtendWith.class);
        Tag tag = EndToEnd.class.getAnnotation(Tag.class);
        assertArrayEquals(new Class<?>[] {EndToEndExtension.class}, extension.value());
        assertEquals("e2e", tag.value());
    }
}
