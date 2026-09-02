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

package org.cometgui.provenance.manifest;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * The application and the machine it ran on, as they were during one run.
 *
 * <p>This is the half of the provenance record that answers "why did the same input give a
 * different answer on my laptop?" -- a different JVM, a different architecture, a different
 * operating system version, or a different locale. The specification's application-provenance list
 * requires all of them.
 *
 * <p><strong>{@code R-PROV-04} and why the locale is not decoration.</strong> The requirement is
 * explicit: "the recorded locale shall be the JVM default locale in effect during the run,
 * precisely because locale can affect serialisation". It does: {@code String.format("%.4f", 0.02)}
 * is {@code 0.0200} under {@code Locale.US} and {@code 0,0200} under {@code Locale.GERMANY}, and a
 * {@code comet.params} file written with commas for decimal points is a file Comet will not read.
 * Recording the locale is what lets a scientist -- or a defect report -- tell "the parameters were
 * wrong" from "the parameters were written by a JVM whose default locale used a comma". The same is
 * true of the time zone for every timestamp in the record.
 *
 * <p><strong>Two locales, because the requirement's words and the requirement's reason are not the
 * same field.</strong> Java keeps a primary default plus two category defaults, {@link
 * Locale.Category#DISPLAY} and {@link Locale.Category#FORMAT}, and it is {@code FORMAT} -- not the
 * primary default -- that {@link java.text.DecimalFormatSymbols#getInstance()} and every other
 * number formatter consult. {@code R-PROV-04} names "the JVM default locale", which is {@link
 * Locale#getDefault()}; the reason it gives for wanting it is serialisation, which is {@code
 * FORMAT}. Recording only the first would satisfy the sentence and miss the point of it, so both
 * are here.
 *
 * <p>They agree on every machine the product runs on today, because nothing in it calls {@link
 * Locale#setDefault(Locale.Category, Locale)} and the whole-locale setter assigns both. This record
 * does not assume that: they are separate components, a run that set them apart is representable,
 * and the test that proves it sets them apart deliberately -- an assertion that cannot pass with
 * one field.
 *
 * @param cometGuiVersion the version of CometGUI that produced the run
 * @param buildIdentifier the build identifier or git commit that version was built from
 * @param osName the operating system name, from {@code os.name}
 * @param osVersion the operating system version, from {@code os.version}
 * @param architecture the CPU architecture, from {@code os.arch}
 * @param jvmVersion the Java runtime version, from {@code java.version}
 * @param locale the JVM default locale in effect during the run, as {@code R-PROV-04} requires
 * @param formatLocale the {@link Locale.Category#FORMAT} default in effect during the run, which is
 *     the one that actually governs number formatting and therefore {@code R-PARAM-11}
 * @param zoneId the JVM default time zone in effect during the run
 */
public record ApplicationRecord(
        String cometGuiVersion,
        String buildIdentifier,
        String osName,
        String osVersion,
        String architecture,
        String jvmVersion,
        Locale locale,
        Locale formatLocale,
        ZoneId zoneId) {

    /**
     * Validates the record.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if any of the text components is blank, with a message
     *     naming the field and the rejected value
     */
    public ApplicationRecord {
        cometGuiVersion = ManifestChecks.requireNonBlank(cometGuiVersion, "cometGuiVersion");
        buildIdentifier = ManifestChecks.requireNonBlank(buildIdentifier, "buildIdentifier");
        osName = ManifestChecks.requireNonBlank(osName, "osName");
        osVersion = ManifestChecks.requireNonBlank(osVersion, "osVersion");
        architecture = ManifestChecks.requireNonBlank(architecture, "architecture");
        jvmVersion = ManifestChecks.requireNonBlank(jvmVersion, "jvmVersion");
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(formatLocale, "formatLocale");
        Objects.requireNonNull(zoneId, "zoneId");
    }

    /**
     * Captures the environment this JVM is running in right now.
     *
     * <p><strong>Read at call time, and that is the requirement rather than an implementation
     * detail.</strong> {@code R-PROV-04} asks for "the JVM default locale in effect during the
     * run", so this reads {@link Locale#getDefault()}, the {@link Locale.Category#FORMAT} default
     * and {@link ZoneId#systemDefault()} when it is called rather than caching them in a static
     * field. All three are mutable process-wide state -- any library on the classpath may call
     * {@link Locale#setDefault(Locale)} during start-up -- and a value read once at
     * class-initialisation time would describe the JVM before the run, which is not what the
     * requirement asks for and not what serialised the parameter files.
     *
     * <p>The two arguments are the only facts a JVM cannot tell you about itself: which build of
     * this application is executing.
     *
     * @param cometGuiVersion the version of CometGUI that is running
     * @param buildIdentifier the build identifier or git commit it was built from
     * @return a record describing this JVM and this machine
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if either argument is blank
     * @throws IllegalStateException if the JVM does not define one of the standard system
     *     properties this record is built from
     */
    public static ApplicationRecord capture(String cometGuiVersion, String buildIdentifier) {
        return capture(
                cometGuiVersion,
                buildIdentifier,
                System::getProperty,
                Locale.getDefault(),
                Locale.getDefault(Locale.Category.FORMAT),
                ZoneId.systemDefault());
    }

    /**
     * Builds the record from supplied sources, so that a test can prove each fact lands in the
     * field it belongs in.
     *
     * <p>Package-private because it is a seam, not a configuration point: production code has
     * exactly one environment and reads it through {@link #capture(String, String)}. Without the
     * seam, a test could only compare the record against the same system properties the production
     * code read, and a defect that swapped {@code os.name} with {@code os.version} would agree with
     * itself and pass.
     *
     * @param cometGuiVersion the version of CometGUI that is running
     * @param buildIdentifier the build identifier or git commit it was built from
     * @param systemProperty resolves a system property name to its value, or to {@code null}
     * @param defaultLocale the JVM default locale to record
     * @param defaultFormatLocale the {@link Locale.Category#FORMAT} default locale to record
     * @param defaultZone the JVM default time zone to record
     * @return a record built from those sources
     * @throws IllegalStateException if {@code systemProperty} returns {@code null} for one of the
     *     properties, naming the property
     */
    static ApplicationRecord capture(
            String cometGuiVersion,
            String buildIdentifier,
            UnaryOperator<String> systemProperty,
            Locale defaultLocale,
            Locale defaultFormatLocale,
            ZoneId defaultZone) {
        Objects.requireNonNull(systemProperty, "systemProperty");
        return new ApplicationRecord(
                cometGuiVersion,
                buildIdentifier,
                required(systemProperty, "os.name"),
                required(systemProperty, "os.version"),
                required(systemProperty, "os.arch"),
                required(systemProperty, "java.version"),
                defaultLocale,
                defaultFormatLocale,
                defaultZone);
    }

    /**
     * Reads a system property that the Java platform guarantees to define.
     *
     * <p>A missing one is a broken or deliberately stripped JVM rather than a condition to work
     * around, and failing here names the property. Falling back to {@code "unknown"} would produce
     * a manifest that looks complete and describes nothing.
     *
     * @param systemProperty the property lookup
     * @param property the property name
     * @return the property's value
     */
    private static String required(UnaryOperator<String> systemProperty, String property) {
        String value = systemProperty.apply(property);
        if (value == null) {
            throw new IllegalStateException(
                    "the system property \""
                            + property
                            + "\" is not set, so this JVM cannot describe itself");
        }
        return value;
    }
}
