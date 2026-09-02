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

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;

/**
 * The one class import every architecture rule in this module is checked against.
 *
 * <p><strong>Why this class exists at all.</strong> An ArchUnit rule set that imports the wrong
 * class path passes while the violation sits in front of it: {@code noClasses().that()...} over an
 * empty set has nothing to reject. The import therefore happens exactly once, here, and {@link
 * ClassImportCensusTest} asserts what came back before any rule is evaluated -- how many classes,
 * and that every product module contributed at least one of them.
 *
 * <p>{@link ImportOption.DoNotIncludeTests} keeps this module's own test classes out of the import.
 * Without it the rules would be graded partly on themselves: these classes live in {@code
 * org.cometgui.archtests}, which is inside the imported package, and they legitimately depend on
 * things product code may not.
 */
final class ProductClasses {

    /** The package every product class lives under. */
    static final String ROOT_PACKAGE = "org.cometgui";

    /**
     * One package per product module, used as the census: each must contribute at least one class.
     *
     * <p>The list is a module map, not a package list. {@code org.cometgui.tools.process} is a
     * separate Maven module (cometgui-process) that shares the {@code org.cometgui.tools} prefix,
     * so it is named separately here and {@link ClassImportCensusTest} does not count its classes
     * towards {@code org.cometgui.tools}. That separation is this list's alone; {@link
     * #TOOLS_ADAPTERS} does not make it, and its Javadoc says why.
     */
    static final List<String> MODULE_PACKAGES =
            List.of(
                    "org.cometgui.app",
                    "org.cometgui.domain",
                    "org.cometgui.install",
                    "org.cometgui.params.comet",
                    "org.cometgui.params.percolator",
                    "org.cometgui.provenance",
                    "org.cometgui.results",
                    "org.cometgui.tools",
                    "org.cometgui.tools.process",
                    "org.cometgui.ui",
                    "org.cometgui.workflow");

    /** The process service, the only place a process may be created (R-PROC-02). */
    static final String PROCESS_SERVICE = "org.cometgui.tools.process..";

    /**
     * Tool adapters: everything under {@code org.cometgui.tools}, <em>including</em> the process
     * service, which lives at {@code org.cometgui.tools.process} and is therefore underneath this
     * pattern.
     *
     * <p>This Javadoc used to say the pattern excluded the process service. It never did, and phase
     * 03 corrected the sentence rather than the constant, because the constant is right. Its one
     * use is {@code LayeringRulesTest.toolAdaptersDoNotDependOnUi}, and a process service that
     * reached into a JavaFX control would break that rule for exactly the reason a Comet adapter
     * would: R-PROC-03's pumps run on their own threads with no toolkit started. Narrowing the
     * pattern to make the comment true would have taken the process service out of the reach of a
     * rule that should cover it, which is a weakening.
     *
     * <p>A later phase that genuinely needs "the tool adapters and not the process service" should
     * add a constant of its own and leave this one covering both.
     */
    static final String TOOLS_ADAPTERS = "org.cometgui.tools..";

    private static final JavaClasses PRODUCT_CLASSES =
            new ClassFileImporter()
                    .withImportOption(new ImportOption.DoNotIncludeTests())
                    .importPackages(ROOT_PACKAGE);

    private ProductClasses() {}

    /**
     * @return every product class on the test class path, imported once and shared
     */
    static JavaClasses all() {
        return PRODUCT_CLASSES;
    }
}
