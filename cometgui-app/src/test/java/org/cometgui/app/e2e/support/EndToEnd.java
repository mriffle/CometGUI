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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks an end-to-end test class (design decisions P14-2 and P14-9).
 *
 * <p>Three things follow from the one annotation, all through {@link EndToEndExtension}:
 *
 * <ol>
 *   <li><strong>Opt-in.</strong> The class runs only when the JVM has {@code -Dcometgui.e2e=true}.
 *       An end-to-end test downloads real tools, runs real searches and takes minutes, so the
 *       ordinary build does not run it.
 *   <li><strong>Never a silent pass.</strong> Without the property the class is <em>skipped with a
 *       stated reason</em>, which surefire records in its report as skipped -- not run, not passed.
 *       A harness that grades these tests counts the tests that ran.
 *   <li><strong>The runner's locale is asserted before anything else.</strong> When the class does
 *       run, the first thing that happens -- before any {@code @BeforeAll} of the class -- is
 *       {@link RunnerLocale#assertUtf8()} on this JVM, which <em>fails</em> the class, naming the
 *       encodings and the remedy, if {@code sun.jnu.encoding} is not UTF-8. A runner without a
 *       UTF-8 locale cannot represent a non-ASCII path, and skipping would hide exactly the
 *       coverage a scientist's machine needs.
 * </ol>
 *
 * <p>It also tags the class {@code e2e}, so a run can select or exclude the tier by tag.
 *
 * <p>A class carrying it must not build a {@code Path} in a static initialiser: that runs when the
 * class is loaded, before any extension, and a non-ASCII path built there would fail with {@code
 * InvalidPathException} instead of the locale diagnostic.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag(EndToEndExtension.TAG)
@ExtendWith(EndToEndExtension.class)
public @interface EndToEnd {}
