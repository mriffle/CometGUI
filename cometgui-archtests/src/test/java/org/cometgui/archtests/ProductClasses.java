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
     * so it is named separately and {@link #TOOLS_ADAPTERS} excludes it.
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

    /** Tool adapters: everything under {@code org.cometgui.tools} except the process service. */
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
