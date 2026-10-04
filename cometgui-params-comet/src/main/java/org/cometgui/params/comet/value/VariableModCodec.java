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

package org.cometgui.params.comet.value;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.schema.CometVersionRecord;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.ResidueAlphabet;
import org.cometgui.params.comet.schema.ValueKind;
import org.cometgui.params.comet.schema.VariableModField;
import org.cometgui.params.comet.schema.VariableModLayout;

/**
 * Reads and writes the value of a {@code variable_modNN} slot through one Comet version's {@link
 * VariableModLayout}.
 *
 * <p>The codec has no field count, field order or format string of its own ({@code R-PARAM-09}).
 * Parsing splits the value on white space, requires exactly as many fields as the layout lists, and
 * reads each by what the layout says sits at that position; a comma pair is read only where the
 * layout accepts one. Formatting walks the same layout and joins the fields with one space. A field
 * the layout does not hold is given Comet's own default when read (the {@code VarMods} constructor
 * in {@code CometSearch/CometData.h} at {@code v2026.02.2}), and a value that differs from that
 * default in such a field cannot be written under the layout and is refused rather than dropped.
 *
 * <p>The residue token is held to the layout's {@link ResidueAlphabet} both ways: a character the
 * version does not accept is refused when read and when written, with a diagnostic naming the
 * version, so a token such as {@code ^} -- the protein N-terminus in Comet 2026.03.0, a character
 * that silently matches nothing in 2026.02.2 -- is legal exactly where the version record says it
 * is.
 *
 * <p>The slots are the version's {@link ValueKind#VARIABLE_MOD_TUPLE} parameters in the metadata;
 * reading a value for any other name is refused, so a {@code variable_mod16} cannot slip through
 * for a version with fifteen slots.
 */
public final class VariableModCodec {

    private static final String WHOLE_TUPLE = "the tuple";

    private static final String UNNAMED = "this Comet version";

    private final String release;

    private final VariableModLayout layout;

    private final List<String> slots;

    /**
     * Creates a codec for a layout and its slots, for a release its diagnostics call "this Comet
     * version".
     *
     * @param layout the version's tuple layout
     * @param slots the names of the version's tuple slots, such as {@code variable_mod01}
     * @throws IllegalArgumentException if there are no slots
     */
    public VariableModCodec(VariableModLayout layout, List<String> slots) {
        this(UNNAMED, layout, slots);
    }

    /**
     * Creates a codec for a named release's layout and slots.
     *
     * @param release the release as diagnostics name it, such as {@code Comet 2026.02.2}
     * @param layout the release's tuple layout
     * @param slots the names of the release's tuple slots, such as {@code variable_mod01}
     * @throws IllegalArgumentException if there are no slots
     */
    public VariableModCodec(String release, VariableModLayout layout, List<String> slots) {
        this.release = Objects.requireNonNull(release, "release");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.slots = List.copyOf(slots);
        if (this.slots.isEmpty()) {
            throw new IllegalArgumentException("a tuple codec needs at least one slot");
        }
    }

    /**
     * The codec for one curated Comet version: its layout and its slots, from the metadata.
     *
     * @param metadata the curated metadata
     * @param version the Comet version
     * @return the codec
     * @throws IllegalArgumentException if the metadata was not curated against that version, or
     *     names no tuple slot for it
     */
    public static VariableModCodec forVersion(CuratedMetadata metadata, ToolVersion version) {
        CometVersionRecord record =
                metadata.version(version)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "the metadata was not curated against Comet "
                                                        + version.text()));
        List<String> slots =
                metadata.parametersFor(version).stream()
                        .filter(p -> p.kind() == ValueKind.VARIABLE_MOD_TUPLE)
                        .map(ParameterDefinition::name)
                        .toList();
        return new VariableModCodec("Comet " + version.text(), record.variableModTuple(), slots);
    }

    /**
     * The layout this codec reads and writes.
     *
     * @return the layout
     */
    public VariableModLayout layout() {
        return layout;
    }

    /**
     * The slot names this codec reads, in metadata order.
     *
     * @return the names
     */
    public List<String> slots() {
        return List.copyOf(slots);
    }

    /**
     * Reads the value of one slot.
     *
     * @param slot the parameter name, such as {@code variable_mod03}
     * @param text the value text of its declaration
     * @return the typed value
     * @throws IllegalArgumentException if {@code slot} is not one of this version's slots
     * @throws ValueSyntaxException naming the slot and the field, if the text cannot be read under
     *     the layout
     */
    public VariableModification parse(String slot, String text) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(text, "text");
        if (!slots.contains(slot)) {
            throw new IllegalArgumentException(
                    "\""
                            + slot
                            + "\" is not a variable-modification slot of this Comet version; its"
                            + " slots are "
                            + slots.get(0)
                            + " to "
                            + slots.get(slots.size() - 1));
        }
        String[] tokens = Numbers.tokens(text);
        List<VariableModLayout.Entry> fields = layout.fields();
        if (tokens.length != fields.size()) {
            throw new ValueSyntaxException(
                    slot,
                    WHOLE_TUPLE,
                    "\""
                            + text.strip()
                            + "\" holds "
                            + tokens.length
                            + " fields, and this Comet version's tuple holds "
                            + fields.size()
                            + ": "
                            + layout.describe());
        }
        Reading reading = new Reading(slot, this);
        for (int index = 0; index < tokens.length; index++) {
            reading.read(index, fields.get(index), tokens[index]);
        }
        return reading.value();
    }

    /**
     * Writes a value as the text of a slot's declaration.
     *
     * @param value the value
     * @return the fields in layout order, joined by one space
     * @throws IllegalArgumentException if the value holds something the layout cannot write: a
     *     non-default value in a field the layout lacks, a pair where the layout takes one value,
     *     or a residue character outside the version's alphabet
     */
    public String format(VariableModification value) {
        Objects.requireNonNull(value, "value");
        String refused = refusedResidues(value.residues());
        if (refused != null) {
            throw new IllegalArgumentException(refused + ", so it cannot be written");
        }
        for (VariableModField field : VariableModField.values()) {
            if (layout.entry(field).isEmpty() && !Defaults.holds(field, value)) {
                throw new IllegalArgumentException(
                        "this Comet version's tuple has no "
                                + field.label()
                                + " field, so a value whose "
                                + field.label()
                                + " is not Comet's default cannot be written under it");
            }
        }
        List<String> tokens = new ArrayList<>();
        for (VariableModLayout.Entry entry : layout.fields()) {
            tokens.add(token(entry, value));
        }
        return String.join(" ", tokens);
    }

    /**
     * Why a residue token holds a character the version does not accept, or {@code null} if it
     * holds none.
     */
    private String refusedResidues(String residues) {
        return layout.residueAlphabet()
                .firstRefused(residues)
                .map(
                        refused ->
                                "\""
                                        + residues
                                        + "\" holds '"
                                        + refused
                                        + "', which "
                                        + release
                                        + " does not accept in a residue token; its residue"
                                        + " alphabet is "
                                        + layout.residueAlphabet().describe())
                .orElse(null);
    }

    private static String token(VariableModLayout.Entry entry, VariableModification value) {
        return switch (entry.field()) {
            case MASS -> Numbers.text(value.mass());
            case RESIDUES -> value.residues();
            case BINARY_GROUP -> Integer.toString(value.binaryGroup());
            case COUNT -> {
                String maximum = Integer.toString(value.maximumCount());
                if (value.minimumCount().isEmpty()) {
                    yield maximum;
                }
                requirePair(entry, "a min,max count");
                yield value.minimumCount().getAsInt() + "," + maximum;
            }
            case TERMINAL_DISTANCE -> Integer.toString(value.terminalDistance());
            case TERMINUS -> Integer.toString(value.terminusCode());
            case REQUIRED -> Integer.toString(value.requirementCode());
            case NEUTRAL_LOSS -> {
                List<BigDecimal> losses = value.neutralLosses();
                if (losses.size() > 1) {
                    requirePair(entry, "two neutral losses");
                }
                yield String.join(",", losses.stream().map(Numbers::text).toList());
            }
        };
    }

    private static void requirePair(VariableModLayout.Entry entry, String what) {
        if (!entry.acceptsPair()) {
            throw new IllegalArgumentException(
                    "this Comet version's "
                            + entry.field().label()
                            + " field takes one value, so "
                            + what
                            + " cannot be written under it");
        }
    }

    /**
     * Comet's own value for a field a layout does not hold: the {@code VarMods} constructor in
     * {@code CometSearch/CometData.h} at {@code v2026.02.2}.
     */
    private static final class Defaults {

        static final int BINARY_GROUP = 0;

        static final int MAXIMUM_COUNT = 0;

        static final int TERMINAL_DISTANCE = -1;

        static final int TERMINUS = 0;

        static final int REQUIREMENT = 0;

        static final List<BigDecimal> NEUTRAL_LOSSES = List.of(new BigDecimal("0.0"));

        private Defaults() {}

        static boolean holds(VariableModField field, VariableModification value) {
            return switch (field) {
                case BINARY_GROUP -> value.binaryGroup() == BINARY_GROUP;
                case COUNT ->
                        value.minimumCount().isEmpty() && value.maximumCount() == MAXIMUM_COUNT;
                case TERMINAL_DISTANCE -> value.terminalDistance() == TERMINAL_DISTANCE;
                case TERMINUS -> value.terminusCode() == TERMINUS;
                case REQUIRED -> value.requirementCode() == REQUIREMENT;
                case NEUTRAL_LOSS ->
                        value.neutralLosses().size() == 1
                                && value.neutralLosses().get(0).signum() == 0;
                case MASS, RESIDUES -> true;
            };
        }
    }

    /** One value being read, field by field, starting from Comet's defaults. */
    private static final class Reading {

        private final String slot;

        private final VariableModCodec codec;

        private BigDecimal mass;

        private String residues;

        private int binaryGroup = Defaults.BINARY_GROUP;

        private OptionalInt minimumCount = OptionalInt.empty();

        private int maximumCount = Defaults.MAXIMUM_COUNT;

        private int terminalDistance = Defaults.TERMINAL_DISTANCE;

        private int terminusCode = Defaults.TERMINUS;

        private int requirementCode = Defaults.REQUIREMENT;

        private List<BigDecimal> neutralLosses = Defaults.NEUTRAL_LOSSES;

        Reading(String slot, VariableModCodec codec) {
            this.slot = slot;
            this.codec = codec;
        }

        void read(int index, VariableModLayout.Entry entry, String token) {
            String field = "field " + (index + 1) + " (" + entry.field().label() + ")";
            switch (entry.field()) {
                case MASS -> mass = Numbers.decimal(slot, field, single(entry, field, token));
                case RESIDUES -> {
                    String problem = VariableModification.residueProblem(token);
                    if (problem == null) {
                        problem = codec.refusedResidues(token);
                    }
                    if (problem != null) {
                        throw new ValueSyntaxException(slot, field, problem);
                    }
                    residues = token;
                }
                case BINARY_GROUP -> binaryGroup = whole(entry, field, token);
                case COUNT -> {
                    List<String> parts = parts(entry, field, token);
                    if (parts.size() > 1) {
                        minimumCount = OptionalInt.of(Numbers.whole(slot, field, parts.get(0)));
                    }
                    maximumCount = Numbers.whole(slot, field, parts.get(parts.size() - 1));
                }
                case TERMINAL_DISTANCE -> terminalDistance = whole(entry, field, token);
                case TERMINUS -> terminusCode = whole(entry, field, token);
                case REQUIRED -> requirementCode = whole(entry, field, token);
                case NEUTRAL_LOSS -> {
                    List<BigDecimal> losses = new ArrayList<>();
                    for (String part : parts(entry, field, token)) {
                        losses.add(Numbers.decimal(slot, field, part));
                    }
                    neutralLosses = losses;
                }
            }
        }

        private int whole(VariableModLayout.Entry entry, String field, String token) {
            return Numbers.whole(slot, field, single(entry, field, token));
        }

        private String single(VariableModLayout.Entry entry, String field, String token) {
            return parts(entry, field, token).get(0);
        }

        /**
         * The one or two values a token holds: a pair only where the layout accepts one, and then
         * exactly two non-empty values joined by one comma, as Comet's {@code "%lf,%lf"} and its
         * comma-to-space count reading expect.
         */
        private List<String> parts(VariableModLayout.Entry entry, String field, String token) {
            if (token.indexOf(',') < 0) {
                return List.of(token);
            }
            if (!entry.acceptsPair()) {
                throw new ValueSyntaxException(
                        slot,
                        field,
                        "\""
                                + token
                                + "\" is a comma pair, and this Comet version takes one value"
                                + " here");
            }
            String[] parts = token.split(",", -1);
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                throw new ValueSyntaxException(
                        slot,
                        field,
                        "\"" + token + "\" is not a pair: two values joined by one comma");
            }
            return List.of(parts[0], parts[1]);
        }

        VariableModification value() {
            return new VariableModification(
                    mass,
                    residues,
                    binaryGroup,
                    minimumCount,
                    maximumCount,
                    terminalDistance,
                    terminusCode,
                    requirementCode,
                    neutralLosses);
        }
    }
}
