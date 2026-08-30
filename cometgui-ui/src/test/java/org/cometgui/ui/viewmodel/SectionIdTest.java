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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.cometgui.ui.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The information architecture as the specification writes it, asserted value by value.
 *
 * <p>The identifiers are asserted literally, in order, rather than derived from the enum -- a test
 * that computed the expected id from the constant name would agree with any renaming and would
 * therefore prove nothing about the contract the FXML and the GUI tests depend on.
 */
class SectionIdTest {

    /** The ten identifiers, primary first, exactly as the shell and the tests use them. */
    private static final List<String> EXPECTED_IDS =
            List.of(
                    "run",
                    "comet-parameters",
                    "percolator",
                    "results",
                    "visualisation",
                    "limelight",
                    "provenance",
                    "console",
                    "tool-manager",
                    "settings");

    private static List<String> idsOf(List<SectionId> sections) {
        List<String> ids = new ArrayList<>();
        for (SectionId section : sections) {
            ids.add(section.id());
        }
        return ids;
    }

    @Nested
    @DisplayName("the sections")
    class Sections {

        @Test
        @DisplayName("are the specification's eight primary sections, in its order")
        void primarySectionsAreTheSpecificationsEight() {
            assertEquals(
                    List.of(
                            SectionId.RUN,
                            SectionId.COMET_PARAMETERS,
                            SectionId.PERCOLATOR,
                            SectionId.RESULTS,
                            SectionId.VISUALISATION,
                            SectionId.LIMELIGHT,
                            SectionId.PROVENANCE,
                            SectionId.CONSOLE),
                    SectionId.primarySections());
        }

        @Test
        @DisplayName("are the specification's two secondary sections: Tool Manager and Settings")
        void secondarySectionsAreToolManagerAndSettings() {
            assertEquals(
                    List.of(SectionId.TOOL_MANAGER, SectionId.SETTINGS),
                    SectionId.secondarySections());
        }

        @Test
        @DisplayName("are ten in display order, primary first")
        void displayOrderIsPrimaryThenSecondary() {
            assertEquals(EXPECTED_IDS, idsOf(SectionId.displayOrder()));
        }

        @Test
        @DisplayName("appear exactly once each in the display order")
        void displayOrderCoversEverySectionOnce() {
            assertEquals(
                    List.of(SectionId.values()).size(),
                    SectionId.displayOrder().size(),
                    "display order must hold every section");
            assertEquals(
                    new HashSet<>(List.of(SectionId.values())),
                    new HashSet<>(SectionId.displayOrder()));
        }

        @Test
        @DisplayName("split into eight primary and two secondary, and the two lists do not overlap")
        void primaryAndSecondaryPartitionTheSections() {
            assertEquals(8, SectionId.primarySections().size());
            assertEquals(2, SectionId.secondarySections().size());
            Set<SectionId> both = new HashSet<>(SectionId.primarySections());
            both.retainAll(SectionId.secondarySections());
            assertEquals(Set.of(), both, "no section is both primary and secondary");
        }
    }

    @Nested
    @DisplayName("each section")
    class EachSection {

        @ParameterizedTest
        @EnumSource(SectionId.class)
        @DisplayName("has a lower-case hyphenated id")
        void idIsLowerCaseHyphenated(SectionId section) {
            assertTrue(
                    Pattern.matches("[a-z]+(-[a-z]+)*", section.id()),
                    "id is not lower-case-hyphenated: " + section.id());
        }

        @ParameterizedTest
        @EnumSource(SectionId.class)
        @DisplayName("has an accessible title and a one-sentence description")
        void titleAndDescriptionArePresent(SectionId section) {
            assertFalse(section.title().isBlank(), "blank title on " + section);
            assertFalse(section.description().isBlank(), "blank description on " + section);
            assertTrue(
                    section.description().endsWith("."),
                    "description is not a sentence on " + section + ": " + section.description());
        }

        @ParameterizedTest
        @EnumSource(SectionId.class)
        @DisplayName("is primary or secondary, never both and never neither")
        void primaryAndSecondaryAreComplementary(SectionId section) {
            assertNotEquals(section.isPrimary(), section.isSecondary());
        }

        @Test
        @DisplayName("has a distinct id and a distinct title")
        void idsAndTitlesAreDistinct() {
            assertEquals(
                    SectionId.values().length,
                    new HashSet<>(idsOf(SectionId.displayOrder())).size());
            Set<String> titles = new HashSet<>();
            for (SectionId section : SectionId.values()) {
                titles.add(section.title());
            }
            assertEquals(SectionId.values().length, titles.size());
        }

        @Test
        @DisplayName("keeps the specification's exact titles")
        void titlesAreTheSpecificationsWords() {
            assertEquals("Run", SectionId.RUN.title());
            assertEquals("Comet Parameters", SectionId.COMET_PARAMETERS.title());
            assertEquals("Percolator", SectionId.PERCOLATOR.title());
            assertEquals("Results", SectionId.RESULTS.title());
            assertEquals("Visualisation", SectionId.VISUALISATION.title());
            assertEquals("Limelight", SectionId.LIMELIGHT.title());
            assertEquals("Provenance", SectionId.PROVENANCE.title());
            assertEquals("Console", SectionId.CONSOLE.title());
            assertEquals("Tool Manager", SectionId.TOOL_MANAGER.title());
            assertEquals("Settings", SectionId.SETTINGS.title());
        }
    }

    @Nested
    @DisplayName("lookup by id")
    class Lookup {

        @ParameterizedTest
        @EnumSource(SectionId.class)
        @DisplayName("round-trips every section")
        void roundTripsEverySection(SectionId section) {
            assertEquals(section, SectionId.fromId(section.id()));
        }

        @Test
        @DisplayName("rejects an unknown id, naming it and listing the ones that exist")
        void rejectsAnUnknownId() {
            IllegalArgumentException thrown =
                    assertThrows(
                            IllegalArgumentException.class, () -> SectionId.fromId("comet-params"));
            assertEquals(
                    "no navigation section has the id: comet-params; the ids are: run,"
                            + " comet-parameters, percolator, results, visualisation, limelight,"
                            + " provenance, console, tool-manager, settings",
                    thrown.getMessage());
        }

        @Test
        @DisplayName("rejects an id that differs only in case")
        void rejectsAMiscasedId() {
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> SectionId.fromId("Run"));
            assertTrue(
                    thrown.getMessage().startsWith("no navigation section has the id: Run;"),
                    thrown.getMessage());
        }

        @Test
        @DisplayName("rejects null, naming the argument")
        void rejectsNull() {
            NullPointerException thrown =
                    assertThrows(
                            NullPointerException.class,
                            () -> SectionId.fromId(Nulls.of(String.class)));
            assertEquals("id", thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("the published lists")
    class PublishedLists {

        @Test
        @DisplayName("cannot be modified by a caller")
        void areUnmodifiable() {
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> SectionId.displayOrder().add(SectionId.RUN));
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> SectionId.primarySections().add(SectionId.RUN));
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> SectionId.secondarySections().remove(0));
        }

        @Test
        @DisplayName("are equal across calls, so a view can compare them")
        void areStableAcrossCalls() {
            assertEquals(SectionId.displayOrder(), SectionId.displayOrder());
            assertEquals(SectionId.primarySections(), SectionId.primarySections());
        }
    }
}
