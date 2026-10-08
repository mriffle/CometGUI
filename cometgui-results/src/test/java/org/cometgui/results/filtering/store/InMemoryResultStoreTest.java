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

package org.cometgui.results.filtering.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.cometgui.results.testing.Fixtures;
import org.cometgui.results.testing.TestHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The store contract ({@link ResultStoreContract}) run on the in-memory store, opened through the
 * factory -- every contract table is at or below {@link ResultStores#IN_MEMORY_ROW_LIMIT}, so the
 * factory chooses memory -- and the checks that only this store needs.
 */
class InMemoryResultStoreTest extends ResultStoreContract {

    @Override
    protected ResultStore open(Path table, TableKind kind, Path workDirectory) throws IOException {
        ResultStore store = ResultStores.open(table, kind, workDirectory, new TestHasher());
        assertTrue(store instanceof InMemoryResultStore, "the factory opens the in-memory store");
        return store;
    }

    @Test
    @DisplayName("the in-memory store writes nothing: its work directory stays empty")
    void writesNothing() throws IOException {
        Path directory = Files.createDirectories(work().resolve("unused"));
        try (ResultStore store =
                open(
                        Fixtures.verified(SHUFFLED, SHUFFLED_SHA256),
                        TableKind.TARGET_PSMS,
                        directory)) {
            store.query(
                    ResultQuery.firstPage(org.cometgui.results.filtering.PsmQValueFilter.DEFAULT));
        }
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(List.of(), files.toList());
        }
    }

    @Test
    @DisplayName("the factory refuses a null file or kind")
    void factoryRefusesNull() {
        Path table = Fixtures.verified(SHUFFLED, SHUFFLED_SHA256);
        TestHasher hasher = new TestHasher();
        Path index = work();
        assertThrows(
                NullPointerException.class,
                () -> ResultStores.open(null, TableKind.TARGET_PSMS, index, hasher));
        assertThrows(
                NullPointerException.class, () -> ResultStores.open(table, null, index, hasher));
        assertThrows(
                NullPointerException.class,
                () -> ResultStores.open(table, TableKind.TARGET_PSMS, null, hasher));
        assertThrows(
                NullPointerException.class,
                () -> ResultStores.open(table, TableKind.TARGET_PSMS, index, null));
        assertThrows(NullPointerException.class, () -> ResultStores.inMemory(table, null));
        assertThrows(
                NullPointerException.class,
                () -> ResultStores.inMemory(null, TableKind.DECOY_PSMS));
        assertEquals(0, hasher.calls(), "a table held in memory is not hashed");
    }

    @Test
    @DisplayName(
            "independence: the oracle's compiled class names no CometGUI production class; the same"
                    + " scan finds them in the store's own test")
    void oracleUsesNoProductionClass() throws IOException {
        for (Class<?> type : List.of(StoreOracle.class, StoreOracle.Row.class)) {
            List<String> found = productionReferences(type);
            assertEquals(List.of(), found, type.getName());
        }
        List<String> control = productionReferences(ResultStoreContract.class);
        assertTrue(
                control.contains("org/cometgui/results/filtering/PsmQValueFilter"),
                "the scan must see production classes where they are used; it saw " + control);
    }

    /** Every {@code org/cometgui/} name in a class file, but the test helpers and the oracle. */
    private static List<String> productionReferences(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        byte[] bytes;
        try (InputStream in = type.getResourceAsStream(resource)) {
            assertTrue(in != null, "no class file " + resource);
            bytes = in.readAllBytes();
        }
        Matcher names =
                Pattern.compile("org/cometgui/[A-Za-z0-9_/$]+")
                        .matcher(new String(bytes, StandardCharsets.ISO_8859_1));
        List<String> found = new ArrayList<>();
        while (names.find()) {
            String name = names.group();
            if (!name.startsWith("org/cometgui/results/testing/")
                    && !name.startsWith("org/cometgui/results/filtering/store/StoreOracle")) {
                found.add(name);
            }
        }
        return found;
    }
}
