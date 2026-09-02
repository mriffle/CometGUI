/*
 * CometGUI -- Comet to Percolator proteomics search workflow with provenance.
 * Copyright (C) 2026 The CometGUI authors.
 *
 * DERIVED FILE. This file is derived from Noble-Lab/CasanovoGUI and has been
 * modified for CometGUI. Upstream project:
 * <https://github.com/Noble-Lab/CasanovoGUI>, licensed GPL-3.0.
 * Copyright (C) the CasanovoGUI authors.
 *
 * The attribution above is collective because upstream carries no per-file
 * copyright notice: every CasanovoGUI source file begins with its package
 * statement, and `grep -rl Copyright --include=*.java src` in a clone of that
 * repository matches nothing. No notice was dropped in copying.
 *
 * WHICH upstream file, and at WHICH commit, is recorded per file in the
 * documentation comment below, because this header block is fixed and
 * identical in every derived file. config/checkstyle/checkstyle-derived.xml
 * requires that record and fails the build when it is missing.
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

package org.cometgui.app.config.derived;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.application.Application;

/**
 * The AtlantaFX themes CometGUI offers, and the single place that knows how to turn one of them
 * into a JavaFX user-agent stylesheet.
 *
 * <p>Derived from Noble-Lab/CasanovoGUI src/main/java/org/casanovo/gui/ui/Themes.java at commit
 * 480b3013e7f8fb51a2b8c58681043821e3e7f865, GPL-3.0, modified.
 *
 * <p><b>What was kept from upstream.</b> The seven themes AtlantaFX publishes, the light/dark
 * distinction a hand-drawn Canvas needs in order to pick contrasting colours, and the contract that
 * matters most: <em>theming is optional and its absence is not an error</em>. AtlantaFX is reached
 * reflectively, so this class compiles, loads and behaves sensibly with the {@code atlantafx-base}
 * jar absent; JavaFX then keeps its default Modena look and nothing throws.
 *
 * <p><b>What was changed for CometGUI.</b> Upstream held the selected theme in a mutable {@code
 * private static volatile String current} that {@code apply} wrote to, so the answer to "which
 * theme is active?" was process-global, order-dependent and impossible for a test to set without
 * side effects. There is no mutable state here at all: a theme is a value, the caller holds it, and
 * every question about it is answered by an instance method. The choice is a typed enum rather than
 * a {@code String}, so an invalid theme is a compile error where it used to be a silent no-op; the
 * one place a {@code String} still arrives from outside (a settings file) goes through {@link
 * #ofStyleClassName(String)}, which rejects a typo loudly instead of quietly falling back.
 * Upstream's light/dark test was the string heuristic {@code name.endsWith("Dark") ||
 * name.equalsIgnoreCase("Dracula")}, which is right today and wrong for the next theme whose name
 * breaks the pattern; here each constant declares its own answer and {@code AtlantaFxThemesTest}
 * pins every one of them against AtlantaFX's own {@code Theme.isDarkMode()}.
 *
 * <p><b>Testability.</b> Only {@link #applyAsUserAgentStylesheet()} touches JavaFX, and it is one
 * line long for that reason: it needs a started toolkit, and nothing else in this class does.
 * {@link #stylesheet()} resolves the same stylesheet without a display, which is what makes the
 * rest of the class assertable in an ordinary unit test.
 */
public enum AtlantaFxThemes {

    /** GitHub Primer, light. The default: it is what CasanovoGUI starts in. */
    PRIMER_LIGHT("PrimerLight", false),

    /** GitHub Primer, dark. */
    PRIMER_DARK("PrimerDark", true),

    /** Nord, light. */
    NORD_LIGHT("NordLight", false),

    /** Nord, dark. */
    NORD_DARK("NordDark", true),

    /** Cupertino, light. */
    CUPERTINO_LIGHT("CupertinoLight", false),

    /** Cupertino, dark. */
    CUPERTINO_DARK("CupertinoDark", true),

    /**
     * Dracula. Dark, despite a name that says nothing about it: the reason darkness is declared per
     * constant here rather than inferred from the name, as upstream inferred it.
     */
    DRACULA("Dracula", true);

    /** The package AtlantaFX publishes its theme classes in. */
    private static final String THEME_PACKAGE = "atlantafx.base.theme.";

    private final String styleClassName;
    private final boolean dark;

    AtlantaFxThemes(String styleClassName, boolean dark) {
        this.styleClassName = styleClassName;
        this.dark = dark;
    }

    /**
     * The theme a fresh installation starts in, and the fallback for a settings file that names no
     * theme at all.
     *
     * @return {@link #PRIMER_LIGHT}
     */
    public static AtlantaFxThemes defaultTheme() {
        return PRIMER_LIGHT;
    }

    /**
     * Resolves the theme a settings file names.
     *
     * <p>A {@code null} or blank name is not an error: it means "no preference recorded" and yields
     * {@link #defaultTheme()}. A non-blank name that matches no theme is an error, because it is a
     * typo or a theme removed by an AtlantaFX upgrade, and silently starting in the wrong theme is
     * how such a mistake survives to a release.
     *
     * @param styleClassName the AtlantaFX theme class name, for example {@code PrimerDark}; may be
     *     {@code null} or blank
     * @return the matching theme, or {@link #defaultTheme()} when the name is {@code null} or blank
     * @throws IllegalArgumentException if the name is non-blank and matches no known theme; the
     *     message lists every name that would have been accepted
     */
    public static AtlantaFxThemes ofStyleClassName(String styleClassName) {
        if (styleClassName == null || styleClassName.isBlank()) {
            return defaultTheme();
        }
        String wanted = styleClassName.trim();
        return Arrays.stream(values())
                .filter(theme -> theme.styleClassName.equals(wanted))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Unknown AtlantaFX theme '"
                                                + wanted
                                                + "'. Known themes: "
                                                + knownStyleClassNames()
                                                + "."));
    }

    /**
     * Every theme name {@link #ofStyleClassName(String)} accepts, in declaration order, joined with
     * ", ". Written for the error message above and for a settings-file comment.
     *
     * @return the seven AtlantaFX theme class names, comma-separated
     */
    public static String knownStyleClassNames() {
        return Arrays.stream(values())
                .map(AtlantaFxThemes::styleClassName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Whether AtlantaFX is on the class path at all.
     *
     * <p>False is a supported state, not a failure: the application runs with JavaFX's default
     * Modena look. This is the upstream contract, kept.
     *
     * @return {@code true} if the AtlantaFX theme classes can be loaded
     */
    public static boolean isThemingAvailable() {
        return PRIMER_LIGHT.stylesheet().isPresent();
    }

    /**
     * The AtlantaFX class name of this theme, which is also the value written to and read from the
     * application settings file.
     *
     * @return for example {@code PrimerDark}
     */
    public String styleClassName() {
        return styleClassName;
    }

    /**
     * Whether this theme is a dark one.
     *
     * <p>Declared per constant rather than inferred from the name. Callers that draw on a Canvas
     * pick contrasting colours from this, and CSS cannot tell them.
     *
     * @return {@code true} for a dark theme
     */
    public boolean isDark() {
        return dark;
    }

    /**
     * The user-agent stylesheet for this theme, resolved through AtlantaFX itself so that the
     * stylesheet path is never hard-coded here and cannot drift from the jar.
     *
     * <p>Empty means AtlantaFX is not on the class path, which is a supported state and not an
     * error. The returned value is a class-path resource path such as {@code
     * /atlantafx/base/theme/primer-dark.css}.
     *
     * @return the stylesheet path, or empty when theming is unavailable
     */
    public Optional<String> stylesheet() {
        try {
            Class<?> themeClass = Class.forName(THEME_PACKAGE + styleClassName);
            Object theme = themeClass.getDeclaredConstructor().newInstance();
            Object stylesheet = themeClass.getMethod("getUserAgentStylesheet").invoke(theme);
            String path = String.valueOf(stylesheet);
            return path.isBlank() ? Optional.empty() : Optional.of(path);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return Optional.empty();
        }
    }

    /**
     * Applies this theme to the running JavaFX application, if theming is available.
     *
     * <p>The whole of this class's dependence on a started JavaFX toolkit is the one-line {@link
     * #setUserAgentStylesheet(String)} below; everything else here is assertable without a display,
     * which is the point. A headless test drives this method once cometgui-app has the Monocle
     * surefire configuration a later work unit adds.
     *
     * @return {@code true} if a stylesheet was applied, {@code false} if AtlantaFX is absent, in
     *     which case JavaFX keeps its default look and nothing is wrong
     */
    public boolean applyAsUserAgentStylesheet() {
        Optional<String> stylesheet = stylesheet();
        stylesheet.ifPresent(AtlantaFxThemes::setUserAgentStylesheet);
        return stylesheet.isPresent();
    }

    /**
     * The only call in this class that needs a started JavaFX toolkit, isolated on its own line so
     * that no test of anything else has to start one.
     *
     * @param stylesheet the class-path resource path {@link #stylesheet()} resolved
     */
    private static void setUserAgentStylesheet(String stylesheet) {
        Application.setUserAgentStylesheet(stylesheet);
    }
}
