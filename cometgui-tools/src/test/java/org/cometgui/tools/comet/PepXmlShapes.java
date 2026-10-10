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

package org.cometgui.tools.comet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The CometGUI-authored pepXML fixture {@code fixtures/pepxml/comet-shapes.pep.xml}: every form
 * Comet 2026.02.2 and 2026.03.0 write into a search hit, typed in Comet's exact layout over
 * invented proteins and peptides (no K562 value, {@code D-006}), with helpers to damage a copy of
 * it.
 *
 * <p>The forms, each measured on a real Comet output or read from {@code CometWritePepXML.cpp} at
 * both tags: residue and terminal declarations (variable, static, binary, protein-terminal); hits
 * with alternative proteins, an escaped protein name, a decoy protein whose alternative has no
 * prefix; tied {@code hit_rank}s; a query with no hits and no retention time or native id; and a
 * hit carrying an N- and a C-terminal mass, a residue with both a static and a variable delta, a
 * PEFF modification with its id, both PEFF variant elements, and string-valued scores.
 */
final class PepXmlShapes {

    /** The fixture's resource name. */
    static final String RESOURCE = "/fixtures/pepxml/comet-shapes.pep.xml";

    private PepXmlShapes() {}

    /**
     * The fixture's text.
     *
     * @return the text
     */
    static String text() {
        try (InputStream in = PepXmlShapes.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, RESOURCE + " is not on the test class path");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new AssertionError(unreadable);
        }
    }

    /**
     * Writes a text into a directory as a pepXML file.
     *
     * @param directory the directory
     * @param text the text
     * @return the file
     */
    static Path write(Path directory, String text) {
        try {
            Files.createDirectories(directory);
            return Files.writeString(
                    directory.resolve("shapes.pep.xml"), text, StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new AssertionError(unwritable);
        }
    }

    /**
     * The fixture with one occurrence of a text replaced; the text must occur exactly once, so a
     * damage that did not land fails the test instead of passing it.
     *
     * @param find the text
     * @param replacement its replacement
     * @return the damaged text
     */
    static String damaged(String find, String replacement) {
        String text = text();
        int at = text.indexOf(find);
        assertTrue(at >= 0, "the fixture has no \"" + find + "\"");
        assertEquals(-1, text.indexOf(find, at + 1), "the fixture has \"" + find + "\" twice");
        String damaged = text.substring(0, at) + replacement + text.substring(at + find.length());
        assertTrue(!damaged.equals(text), "the damage changed nothing");
        return damaged;
    }

    /**
     * The message a refused file produces.
     *
     * @param file the file
     * @return the message
     */
    static String refusal(Path file) {
        CometOutputException refused =
                assertThrows(
                        CometOutputException.class,
                        () -> PepXmlSearchHitReader.read(file, new Recorder()));
        assertEquals(file, refused.file());
        return refused.getMessage();
    }

    static PepXmlDecimal d(String text) {
        return new PepXmlDecimal(text);
    }

    static Optional<PepXmlDecimal> od(String text) {
        return Optional.of(new PepXmlDecimal(text));
    }

    /** One delivered query. */
    record Visited(PepXmlQuery query, List<PepXmlSearchHit> hits) {}

    /** Records everything delivered, in order. */
    static final class Recorder implements PepXmlVisitor<RuntimeException> {

        private final List<PepXmlSearchSummary> summaries = new ArrayList<>();

        private final List<Visited> queries = new ArrayList<>();

        /** The summaries delivered, in order. */
        List<PepXmlSearchSummary> summaries() {
            return summaries;
        }

        /** The queries delivered, in order. */
        List<Visited> queries() {
            return queries;
        }

        @Override
        public void searchSummary(PepXmlSearchSummary summary) {
            assertEquals(List.of(), queries, "the summary must come before every query");
            summaries.add(summary);
        }

        @Override
        public void spectrumQuery(PepXmlQuery query, List<PepXmlSearchHit> hits) {
            queries.add(new Visited(query, hits));
        }
    }
}
