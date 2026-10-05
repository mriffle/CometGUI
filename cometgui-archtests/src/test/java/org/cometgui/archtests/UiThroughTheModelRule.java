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

import com.tngtech.archunit.lang.ArchRule;

/**
 * The UI reads and writes Comet parameter values only through the parameter model (Phase 07,
 * decision P7-1), built exactly once.
 *
 * <p>{@link LayeringRulesTest} grades the product with this rule and {@link
 * UiThroughTheModelRuleTest} grades deliberately illegal fixtures with it; both hold this one
 * object, for the reason {@link ProcessCreationRule} gives.
 *
 * <p><strong>What it forbids</strong>, to every class in {@code org.cometgui.ui}: the parser's line
 * reader ({@code ParamsLineReader}, the one place that knows how a {@code comet.params} line is
 * shaped), the one number reader and writer ({@code Numbers}), every value codec ({@code ...Codec}
 * in {@code org.cometgui.params.comet.value}, and {@code ParameterValueCodec}), and {@code
 * java.util.regex}. A view or view-model that reached any of them would be reading or writing a
 * value itself instead of handing text to {@code CometParameters.withText} and showing what the
 * model says -- a second, unsynchronised parser in the place the specification says holds none.
 *
 * <p><strong>What it cannot see</strong>, stated rather than papered over: {@code String.split},
 * {@code String.matches} and a hand-written character loop depend on nothing but {@code java.lang},
 * so "no parsing in the UI" stays a review obligation beyond what this rule expresses. The rule
 * closes the doors that lead to the model's own machinery, and the regular-expression door that
 * makes ad-hoc parsing cheap.
 */
final class UiThroughTheModelRule {

    /** The forbidden classes and their nested classes, by name. */
    static final String FORBIDDEN =
            "(org\\.cometgui\\.params\\.comet\\.parser\\.ParamsLineReader"
                    + "|org\\.cometgui\\.params\\.comet\\.value\\.Numbers"
                    + "|org\\.cometgui\\.params\\.comet\\.value\\.[A-Za-z0-9]*Codec"
                    + "|org\\.cometgui\\.params\\.comet\\.model\\.ParameterValueCodec"
                    + "|java\\.util\\.regex\\.[A-Za-z0-9]+)(\\$.*)?";

    /** No class in {@code org.cometgui.ui} may depend on a class {@link #FORBIDDEN} names. */
    static final ArchRule UI_GOES_THROUGH_THE_MODEL =
            noClasses()
                    .that()
                    .resideInAPackage("org.cometgui.ui..")
                    .should()
                    .dependOnClassesThat()
                    .haveNameMatching(FORBIDDEN)
                    .because(
                            "P7-1: the parameter model is the source of truth, so the UI hands a"
                                    + " value's text to CometParameters and shows what the model"
                                    + " says; it neither reads comet.params lines nor numbers nor"
                                    + " tuples itself, nor parses with regular expressions");

    private UiThroughTheModelRule() {}
}
