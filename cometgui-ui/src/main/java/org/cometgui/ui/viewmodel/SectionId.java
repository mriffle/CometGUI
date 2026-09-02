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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The application's navigation sections: the specification's information architecture, as a type.
 *
 * <h2>Primary and secondary</h2>
 *
 * <p>The specification's <em>Information Architecture</em> lists eight recommended <strong>primary
 * sections</strong> -- {@link #RUN}, {@link #COMET_PARAMETERS}, {@link #PERCOLATOR}, {@link
 * #RESULTS}, {@link #VISUALISATION}, {@link #LIMELIGHT}, {@link #PROVENANCE} and {@link #CONSOLE}
 * -- and then says that "Tool Manager and application Settings may be secondary navigation or
 * dialogs". Both kinds are constants of this enum, told apart by {@link #isPrimary()}.
 *
 * <p>Modelling the two secondary sections here rather than leaving them to whichever view happens
 * to open them is deliberate. Whether they end up in the left navigation or behind a dialog, each
 * one still needs an accessible name and a stable test identifier, and the shell has to be able to
 * reach them; a section that exists only as a hand-written string in one FXML file has neither.
 * {@link #isPrimary()} is what a view uses to decide where a section is drawn, so the distinction
 * stays a presentation decision and never becomes a difference in identity.
 *
 * <h2>Identifiers</h2>
 *
 * <p>{@link #id()} is stable, lower-case and hyphenated. It is used <em>verbatim</em> as the {@code
 * fx:id} of the section's pane and as the identifier a GUI test looks a section up by, so changing
 * one breaks tests loudly rather than silently -- which is the point. It is deliberately neither
 * {@link #name()}, which would leak Java naming into markup, nor {@link #title()}, which changes
 * whenever the wording of the interface does.
 *
 * <p>{@link #title()} is the accessible name the navigation entry and the section header carry, and
 * {@link #description()} is one sentence of accessible help describing what the section holds,
 * taken from what the specification says about it. The specification's <em>Accessibility</em>
 * principle requires every interactive control to have an accessible label; carrying the text on
 * the model rather than composing it in a view is what lets a test enumerate the sections and
 * assert that none is missing one.
 *
 * <h2>Spelling</h2>
 *
 * <p>{@link #VISUALISATION} keeps the specification's British spelling in its title and its
 * identifier. This is not a preference: the identifier is a contract between this enum, the FXML
 * and the tests, so it has to be written down once and copied, not spelled from memory.
 */
public enum SectionId {

    /** The Run screen: the workflow's inputs, its stage stepper, and Run/Cancel. */
    RUN(
            "run",
            "Run",
            "Inputs, workflow summary, selected tool versions, high-level parameter summary,"
                    + " validation, and Run and Cancel controls.",
            true),

    /** The typed Comet parameter editor. */
    COMET_PARAMETERS(
            "comet-parameters",
            "Comet Parameters",
            "Typed parameter editor with Essentials, Advanced and Expert modes.",
            true),

    /** Percolator version selection and options. */
    PERCOLATOR(
            "percolator",
            "Percolator",
            "Version selection, result-filter defaults, advanced learning options, and version"
                    + " capability and advisory information.",
            true),

    /** The parsed results: PSMs, peptides, feature weights and export. */
    RESULTS(
            "results",
            "Results",
            "Run summary, PSM table, peptide table, learned feature weights, and export.",
            true),

    /** Spectrum visualisation through PDV. */
    VISUALISATION(
            "visualisation",
            "Visualisation",
            "PDV status, the selected spectrum and PSM context, and Open in PDV actions.",
            true),

    /** Limelight conversion and upload. */
    LIMELIGHT(
            "limelight",
            "Limelight",
            "Converter compatibility, converter parameters, generated Limelight XML, upload"
                    + " configuration, and upload log and status.",
            true),

    /** The provenance record for the run. */
    PROVENANCE(
            "provenance",
            "Provenance",
            "Tool versions and checksums, file hashes, exact commands, parameter files, run"
                    + " timeline, environment, warnings, and export.",
            true),

    /** The live console. */
    CONSOLE(
            "console",
            "Console",
            "A persistent or collapsible live console that can filter messages by workflow stage.",
            true),

    /**
     * Secondary: the Tool Manager.
     *
     * <p>The description comes from <em>Percolator installation modes</em>, which is where the
     * specification says what this section shows: every supported verified version that is
     * available and runnable on the user's platform, under one of three installation modes.
     */
    TOOL_MANAGER(
            "tool-manager",
            "Tool Manager",
            "Managed, registered and custom tool installations, the versions available and runnable"
                    + " on this platform, and their verification state.",
            false),

    /**
     * Secondary: application settings.
     *
     * <p>The specification names this section and says it may be secondary navigation or a dialog,
     * but does not enumerate its contents; the phase that gives it content owns the wording. What
     * is fixed here is only that it has an identity, a name and a description, because the shell
     * must be able to reach it and a test must be able to find it.
     */
    SETTINGS(
            "settings",
            "Settings",
            "Application-wide preferences and defaults that persist between runs.",
            false);

    /*
     * The three lists below are already immutable -- every one is built with List.copyOf -- but
     * each accessor returns List.copyOf(field) rather than the field itself.  That is not
     * ceremony: SpotBugs reads mutability from the DECLARED type, sees a public static method
     * handing out a java.util.List field, and reports MS_EXPOSE_REP at effort=Max threshold=Low.
     * Its documented remedy is to publish an unmodifiable copy, which is exactly what List.copyOf
     * does -- and on an already-immutable list it returns the same instance, so the fix costs
     * nothing at run time.  Fixed in the code rather than by adding a SpotBugs exclusion.
     */

    /**
     * Every section in display order: the eight primary ones first, then the two secondary ones.
     */
    private static final List<SectionId> DISPLAY_ORDER = displayOrderOf(values());

    /** The eight primary sections, in display order. */
    private static final List<SectionId> PRIMARY = filter(DISPLAY_ORDER, true);

    /** The two secondary sections, in display order. */
    private static final List<SectionId> SECONDARY = filter(DISPLAY_ORDER, false);

    /** Every section by {@link #id()}, for {@link #fromId(String)}. */
    private static final Map<String, SectionId> BY_ID = indexById(DISPLAY_ORDER);

    private final String id;

    private final String title;

    private final String description;

    private final boolean primary;

    SectionId(String id, String title, String description, boolean primary) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.primary = primary;
    }

    /**
     * Every section in the order the shell shows it: primary sections first, then secondary ones,
     * each group in declaration order.
     *
     * <p>Built by partitioning rather than by trusting declaration order, so that moving a constant
     * cannot silently interleave a secondary section into the primary navigation.
     *
     * @return an immutable list of all ten sections
     */
    public static List<SectionId> displayOrder() {
        return List.copyOf(DISPLAY_ORDER);
    }

    /**
     * The primary sections, in display order.
     *
     * @return an immutable list of the eight sections the specification recommends as primary
     */
    public static List<SectionId> primarySections() {
        return List.copyOf(PRIMARY);
    }

    /**
     * The secondary sections, in display order.
     *
     * @return an immutable list of the two sections the specification allows to be secondary
     *     navigation or dialogs
     */
    public static List<SectionId> secondarySections() {
        return List.copyOf(SECONDARY);
    }

    /**
     * The section with the given stable identifier.
     *
     * @param id the identifier, as {@link #id()} returns it
     * @return the section
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if no section has that identifier; the message names the
     *     identifier that was asked for and lists the ones that exist, because this is what a
     *     mistyped {@code fx:id} looks like from the inside and a diagnostic that does not name it
     *     is of no use at all
     */
    public static SectionId fromId(String id) {
        Objects.requireNonNull(id, "id");
        SectionId section = BY_ID.get(id);
        if (section == null) {
            throw new IllegalArgumentException(
                    "no navigation section has the id: " + id + "; the ids are: " + knownIds());
        }
        return section;
    }

    /**
     * The identifiers that do exist, in display order, for a diagnostic.
     *
     * <p>Read from {@link #DISPLAY_ORDER} rather than from {@link #BY_ID}, whose iteration order
     * {@link Map#copyOf(Map)} does not define -- a message listing the same ten identifiers in a
     * different order on each JVM cannot be asserted exactly, and a diagnostic nobody can assert is
     * a diagnostic nobody maintains.
     *
     * @return the ten identifiers, comma-separated, in display order
     */
    private static String knownIds() {
        List<String> ids = new ArrayList<>(DISPLAY_ORDER.size());
        for (SectionId section : DISPLAY_ORDER) {
            ids.add(section.id);
        }
        return String.join(", ", ids);
    }

    private static List<SectionId> displayOrderOf(SectionId[] sections) {
        List<SectionId> ordered = new ArrayList<>(sections.length);
        for (SectionId section : sections) {
            if (section.primary) {
                ordered.add(section);
            }
        }
        for (SectionId section : sections) {
            if (!section.primary) {
                ordered.add(section);
            }
        }
        return List.copyOf(ordered);
    }

    private static List<SectionId> filter(List<SectionId> sections, boolean primary) {
        List<SectionId> matched = new ArrayList<>();
        for (SectionId section : sections) {
            if (section.primary == primary) {
                matched.add(section);
            }
        }
        return List.copyOf(matched);
    }

    /**
     * Indexes the sections by identifier.
     *
     * <p>No duplicate check here: a clash would silently drop a section from the index and go
     * unnoticed, so it is asserted as a test over {@link #values()} instead -- which fails naming
     * the two constants, where a thrown {@link IllegalStateException} in a static initialiser would
     * surface as an {@link ExceptionInInitializerError} in whatever unrelated class touched the
     * enum first.
     *
     * @param sections every section, in display order
     * @return an immutable map from identifier to section
     */
    private static Map<String, SectionId> indexById(List<SectionId> sections) {
        Map<String, SectionId> index = new LinkedHashMap<>();
        for (SectionId section : sections) {
            index.put(section.id, section);
        }
        return Map.copyOf(index);
    }

    /**
     * The stable identifier, used verbatim as an {@code fx:id} and in tests.
     *
     * @return the identifier: lower-case, hyphenated, never blank
     */
    public String id() {
        return id;
    }

    /**
     * The section's title, which is also its accessible name.
     *
     * @return the display title, never blank
     */
    public String title() {
        return title;
    }

    /**
     * One sentence describing what the section holds, for accessible help and tooltips.
     *
     * @return the description, never blank
     */
    public String description() {
        return description;
    }

    /**
     * Whether this is one of the eight sections the specification recommends as primary navigation.
     *
     * @return {@code true} for a primary section, {@code false} for a secondary one
     */
    public boolean isPrimary() {
        return primary;
    }

    /**
     * Whether this is one of the two sections the specification allows to be secondary navigation
     * or a dialog.
     *
     * @return the negation of {@link #isPrimary()}
     */
    public boolean isSecondary() {
        return !primary;
    }
}
