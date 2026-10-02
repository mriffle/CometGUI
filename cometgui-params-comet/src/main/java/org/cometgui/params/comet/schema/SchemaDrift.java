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

package org.cometgui.params.comet.schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;

/**
 * Compares what a binary declares with what the metadata claims, as a value.
 *
 * <p>The rules are the specification's (<em>Schema discovery and drift detection</em>, {@code
 * R-PARAM-02}):
 *
 * <ol>
 *   <li>a declared parameter with no metadata for the dump's version and no allow-list entry is
 *       {@link DriftFinding.Kind#UNMODELLED};
 *   <li>a parameter the metadata claims for the version, or allow-lists, that a <em>complete</em>
 *       dump does not declare is {@link DriftFinding.Kind#NOT_DECLARED} -- and a {@link
 *       DiscoveryMode#PARTIAL_DISCOVERY} dump never produces this finding, because {@code -p}
 *       leaves out parameters the binary supports;
 *   <li>a curated default that differs from the binary's is {@link
 *       DriftFinding.Kind#DEFAULT_DIFFERS};
 *   <li>a dump whose version the metadata never recorded, or recorded under a different marker, is
 *       {@link DriftFinding.Kind#VERSION_RECORD}.
 * </ol>
 *
 * <p>Defaults are compared token by token, a token equal if its text is equal or both texts are
 * numbers of equal value, so that a release that wrote {@code 0.0} where the metadata says {@code
 * 0.0000} is not drift while {@code 0.0} against {@code 0.01} is.
 */
public final class SchemaDrift {

    private SchemaDrift() {}

    /**
     * Compares one dump with the metadata.
     *
     * @param discovered what the binary declared
     * @param metadata what is curated
     * @return every finding, with the counts
     */
    public static DriftReport compare(DiscoveredSchema discovered, CuratedMetadata metadata) {
        Objects.requireNonNull(discovered, "discovered");
        Objects.requireNonNull(metadata, "metadata");
        ToolVersion version = discovered.marker().toolVersion();
        List<DriftFinding> findings = new ArrayList<>();
        Optional<CometVersionRecord> record = metadata.version(version);
        if (record.isEmpty()) {
            findings.add(
                    new DriftFinding(
                            DriftFinding.Kind.VERSION_RECORD,
                            discovered.marker().text(),
                            "the metadata has no record of Comet "
                                    + version.text()
                                    + " (marker \""
                                    + discovered.marker().text()
                                    + "\"); curate it against that release's -q output"));
        } else if (!record.get().marker().equals(discovered.marker())) {
            findings.add(
                    new DriftFinding(
                            DriftFinding.Kind.VERSION_RECORD,
                            discovered.marker().text(),
                            "the binary's marker is \""
                                    + discovered.marker().text()
                                    + "\" and the metadata records \""
                                    + record.get().marker().text()
                                    + "\" for Comet "
                                    + version.text()
                                    + "; this is not the build the metadata was curated against"));
        }
        int modelled = 0;
        int allowListed = 0;
        for (DiscoveredParameter declared : discovered.parameters()) {
            Optional<ParameterDefinition> definition = metadata.parameter(declared.name(), version);
            if (definition.isPresent()) {
                modelled++;
                compareDefault(declared, definition.get(), version, findings);
            } else if (metadata.isAllowListed(declared.name())) {
                allowListed++;
            } else {
                findings.add(
                        new DriftFinding(
                                DriftFinding.Kind.UNMODELLED,
                                declared.name(),
                                "Comet "
                                        + version.text()
                                        + " declares "
                                        + declared.name()
                                        + " (line "
                                        + declared.lineNumber()
                                        + ", default \""
                                        + declared.defaultText()
                                        + "\"), which has no metadata for that version and is not"
                                        + " allow-listed"));
            }
        }
        if (discovered.mode() == DiscoveryMode.COMPLETE) {
            for (ParameterDefinition definition : metadata.parametersFor(version)) {
                notDeclared(discovered, definition.name(), "claims", version, findings);
            }
            for (InternalParameter entry : metadata.internal()) {
                notDeclared(discovered, entry.name(), "allow-lists", version, findings);
            }
        }
        return new DriftReport(
                version,
                discovered.mode(),
                findings,
                discovered.parameters().size(),
                modelled,
                allowListed);
    }

    private static void notDeclared(
            DiscoveredSchema discovered,
            String name,
            String verb,
            ToolVersion version,
            List<DriftFinding> findings) {
        if (discovered.parameter(name).isEmpty()) {
            findings.add(
                    new DriftFinding(
                            DriftFinding.Kind.NOT_DECLARED,
                            name,
                            "the metadata "
                                    + verb
                                    + " "
                                    + name
                                    + " for Comet "
                                    + version.text()
                                    + ", and that binary's complete -q output does not declare"
                                    + " it"));
        }
    }

    private static void compareDefault(
            DiscoveredParameter declared,
            ParameterDefinition definition,
            ToolVersion version,
            List<DriftFinding> findings) {
        if (!sameValue(definition.defaultValue(), declared.defaultText())) {
            findings.add(
                    new DriftFinding(
                            DriftFinding.Kind.DEFAULT_DIFFERS,
                            declared.name(),
                            "the metadata's default for "
                                    + declared.name()
                                    + " is \""
                                    + definition.defaultValue()
                                    + "\" and Comet "
                                    + version.text()
                                    + " writes \""
                                    + declared.defaultText()
                                    + "\" (line "
                                    + declared.lineNumber()
                                    + ")"));
        }
    }

    /**
     * Whether two value texts mean the same: the same number of whitespace-separated tokens, each
     * pair textually equal or numerically equal.
     *
     * @param curated the metadata's text
     * @param written the binary's text
     * @return {@code true} if they agree
     */
    static boolean sameValue(String curated, String written) {
        String[] left = tokens(curated);
        String[] right = tokens(written);
        if (left.length != right.length) {
            return false;
        }
        for (int index = 0; index < left.length; index++) {
            if (!left[index].equals(right[index]) && !sameNumber(left[index], right[index])) {
                return false;
            }
        }
        return true;
    }

    private static String[] tokens(String text) {
        String stripped = text.strip();
        return stripped.isEmpty() ? new String[0] : stripped.split("\\s+");
    }

    private static boolean sameNumber(String left, String right) {
        try {
            return new BigDecimal(left).compareTo(new BigDecimal(right)) == 0;
        } catch (NumberFormatException notNumbers) {
            return false;
        }
    }
}
