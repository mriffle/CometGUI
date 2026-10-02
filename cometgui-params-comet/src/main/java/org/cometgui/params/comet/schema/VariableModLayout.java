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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The field layout of one Comet version's variable-modification tuple: which fields the value of
 * {@code variable_mod01} .. {@code variable_modNN} holds, in which order, and which of them accept
 * a {@code a,b} comma pair.
 *
 * <p>{@code R-PARAM-09} says field count and order come from the version schema, not from a
 * hard-coded format string, so this is data: the curated metadata records one layout per Comet
 * version ({@code versions[].variableModTuple}), {@link MetadataLoader} reads it, and the codec in
 * {@code org.cometgui.params.comet.value} is driven by it and has no field count of its own.
 *
 * <p>The constructor refuses a layout that cannot describe a tuple: no fields, a field listed
 * twice, a field whose declared kind is not the field's kind, a pair on a field no Comet release
 * pairs, or a layout without the mass difference and the residues, without which a tuple means
 * nothing.
 *
 * @param fields the fields in the order Comet reads them
 * @param source where the layout was taken from: the upstream source line that reads the tuple
 */
public record VariableModLayout(List<Entry> fields, String source) {

    /**
     * One position in the layout.
     *
     * @param field what the position holds
     * @param kind the kind of text it holds; must be {@code field.kind()}
     * @param acceptsPair whether this version accepts a {@code a,b} comma pair there
     */
    public record Entry(VariableModField field, VariableModField.Kind kind, boolean acceptsPair) {

        /** Validates presence. */
        public Entry {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(kind, "kind");
        }
    }

    /**
     * Validates the layout and takes an immutable copy.
     *
     * @throws IllegalArgumentException naming the position at fault, if the layout cannot describe
     *     a tuple
     */
    public VariableModLayout {
        fields = List.copyOf(fields);
        Objects.requireNonNull(source, "source");
        Set<VariableModField> seen = EnumSet.noneOf(VariableModField.class);
        for (int index = 0; index < fields.size(); index++) {
            Entry entry = fields.get(index);
            String where = "field " + (index + 1) + " (" + entry.field() + ")";
            if (!seen.add(entry.field())) {
                throw new IllegalArgumentException(where + " is listed twice");
            }
            if (entry.kind() != entry.field().kind()) {
                throw new IllegalArgumentException(
                        where
                                + " is declared "
                                + entry.kind()
                                + ", and a "
                                + entry.field().label()
                                + " is "
                                + entry.field().kind());
            }
            if (entry.acceptsPair() && !entry.field().pairable()) {
                throw new IllegalArgumentException(
                        where + " is given a comma pair, which no Comet release accepts there");
            }
        }
        for (VariableModField essential :
                List.of(VariableModField.MASS, VariableModField.RESIDUES)) {
            if (!seen.contains(essential)) {
                throw new IllegalArgumentException(
                        "the layout has no "
                                + essential
                                + " field; a tuple without its "
                                + essential.label()
                                + " means nothing");
            }
        }
    }

    /**
     * The fields in order, immutable.
     *
     * @return the entries
     */
    @Override
    public List<Entry> fields() {
        return List.copyOf(fields);
    }

    /**
     * The entry for a field, if this layout has that field.
     *
     * @param field the field
     * @return its entry, or empty if the version's tuple does not hold it
     */
    public Optional<Entry> entry(VariableModField field) {
        Objects.requireNonNull(field, "field");
        return fields.stream().filter(entry -> entry.field() == field).findFirst();
    }

    /**
     * The layout in words, for a diagnostic: each field's label in order, with {@code [,pair]}
     * where a pair is accepted.
     *
     * @return for example {@code mass difference, residues, ..., neutral loss[,pair]}
     */
    public String describe() {
        List<String> words = new ArrayList<>();
        for (Entry entry : fields) {
            words.add(entry.field().label() + (entry.acceptsPair() ? "[,pair]" : ""));
        }
        return String.join(", ", words);
    }
}
