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

import java.util.Optional;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * The behaviour behind {@link EndToEnd}: an execution condition that enables an end-to-end class
 * only under {@code -Dcometgui.e2e=true} and otherwise disables it with a stated reason, and a
 * before-all callback that asserts this JVM's locale first.
 *
 * <p>One extension rather than JUnit's {@code @EnabledIfSystemProperty} plus a second one, so that
 * the reason a class was skipped names the value the property actually had, and so that the opt-in
 * and the locale assertion cannot be applied one without the other.
 */
public final class EndToEndExtension implements ExecutionCondition, BeforeAllCallback {

    /** The system property that opts in to the end-to-end tier. */
    public static final String PROPERTY = "cometgui.e2e";

    /** The JUnit tag every end-to-end class carries. */
    public static final String TAG = "e2e";

    /** Creates the extension; JUnit does this. */
    public EndToEndExtension() {
        // Stateless.
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return decide(Optional.ofNullable(System.getProperty(PROPERTY)));
    }

    /**
     * Whether an end-to-end class runs, given the value of {@value #PROPERTY}.
     *
     * @param value the property's value, or empty when it is not set
     * @return enabled for exactly {@code true}; otherwise disabled, with a reason naming the value
     *     seen and the flag that enables the class
     */
    static ConditionEvaluationResult decide(Optional<String> value) {
        if (value.isPresent() && value.get().equals("true")) {
            return ConditionEvaluationResult.enabled(PROPERTY + "=true: end-to-end tests run");
        }
        String seen = value.map(text -> "is \"" + text + "\"").orElse("is not set");
        return ConditionEvaluationResult.disabled(
                "end-to-end test, not run: the system property "
                        + PROPERTY
                        + " "
                        + seen
                        + ". It runs real tools against real data for minutes, so it is opt-in;"
                        + " run with -D"
                        + PROPERTY
                        + "=true, under a UTF-8 locale (export LC_ALL=C.UTF-8 before mvn).");
    }

    /**
     * Asserts this JVM's locale before the class's own {@code @BeforeAll} methods run.
     *
     * @param context the class's context
     * @throws AssertionError if {@code sun.jnu.encoding} is not UTF-8
     */
    @Override
    public void beforeAll(ExtensionContext context) {
        RunnerLocale.ofThisJvm().assertUtf8();
    }
}
