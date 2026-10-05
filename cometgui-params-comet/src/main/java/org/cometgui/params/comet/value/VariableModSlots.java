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
import java.util.Optional;
import java.util.OptionalInt;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.ResidueAlphabet;
import org.cometgui.params.comet.schema.ValueKind;
import org.cometgui.params.comet.schema.VariableModLayout;

/**
 * The variable-modification slots of one Comet release, and how a slot's value is changed one part
 * at a time: what a structured editor needs so that it never splits a tuple, reads a number or
 * writes a comma itself (Phase 07, decision P7-1).
 *
 * <p>Everything here follows the release's {@link VariableModLayout} and {@link ResidueAlphabet},
 * which are data in its version record ({@code R-PARAM-09}): {@link #parts()} lists one part per
 * field the layout holds, in its order, with the minimum count and the second neutral loss only
 * where the layout accepts a pair; {@link #withPart} reads a part's text as the layout's field
 * reads it and refuses what the field cannot hold, naming the slot and the part; {@link
 * #withResidue} adds or removes one character of the release's alphabet. The result is a {@link
 * VariableModification} the caller puts into the slot with the model's own {@code withValue}.
 *
 * <p>Whether a value that reads is legal -- a minimum above the maximum, a terminus outside 0-3 --
 * is still validation's question: a part accepts any text its field can hold, exactly as the parser
 * does.
 */
public final class VariableModSlots {

    /**
     * The order an edited residue token is written in: the N-terminal codes, the residue letters,
     * then the C-terminal codes, as Comet's page writes {@code nK}. Comet sorts and de-duplicates
     * the characters before it searches, so the order carries no meaning.
     */
    private static final String TOKEN_ORDER = "n^ABCDEFGHIJKLMNOPQRSTUVWXYZc$";

    private final VariableModCodec codec;

    private final VariableModification unused;

    /**
     * Slots for a codec and the value an unused slot holds.
     *
     * @param codec the release's tuple codec
     * @param unused what a slot holds when it holds no modification
     * @throws IllegalArgumentException if {@code unused} has a mass difference or cannot be written
     *     by the codec
     */
    public VariableModSlots(VariableModCodec codec, VariableModification unused) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.unused = Objects.requireNonNull(unused, "unused");
        if (!unused.isUnused()) {
            throw new IllegalArgumentException(
                    "an unused slot's value has a mass difference of 0, not "
                            + Numbers.text(unused.mass()));
        }
        codec.unwritable(unused)
                .ifPresent(
                        reason -> {
                            throw new IllegalArgumentException(
                                    "the unused value cannot be written: " + reason);
                        });
    }

    /**
     * The slots of one curated release. An unused slot holds what the release's own {@code comet
     * -q} writes for one: the curated default of the first slot whose default has no mass
     * difference ({@code 0.0 X 0 3 -1 0 0 0.0} in 2026.02.2 and 2026.03.0, which is also what
     * Comet's {@code variable_modXX} page gives as the value of a missing slot).
     *
     * @param metadata the curated metadata
     * @param version the release
     * @return the slots
     * @throws IllegalArgumentException if the metadata was not curated against the release, or
     *     curates no unused slot default for it
     */
    public static VariableModSlots forRelease(CuratedMetadata metadata, ToolVersion version) {
        VariableModCodec codec = VariableModCodec.forVersion(metadata, version);
        for (ParameterDefinition definition : metadata.parametersFor(version)) {
            if (definition.kind() == ValueKind.VARIABLE_MOD_TUPLE) {
                VariableModification value =
                        codec.parse(definition.name(), definition.defaultValue());
                if (value.isUnused()) {
                    return new VariableModSlots(codec, value);
                }
            }
        }
        throw new IllegalArgumentException(
                "the metadata curates no unused variable-modification slot for Comet "
                        + version.text());
    }

    /**
     * The slot names, in metadata order.
     *
     * @return for example {@code variable_mod01} to {@code variable_mod15}
     */
    public List<String> slots() {
        return codec.slots();
    }

    /**
     * The release's tuple layout.
     *
     * @return the layout
     */
    public VariableModLayout layout() {
        return codec.layout();
    }

    /**
     * The release's residue alphabet: the letters and terminal codes a residue token may hold.
     *
     * @return the alphabet
     */
    public ResidueAlphabet alphabet() {
        return codec.layout().residueAlphabet();
    }

    /**
     * What an unused slot holds in this release.
     *
     * @return the value, mass difference 0
     */
    public VariableModification unused() {
        return unused;
    }

    /**
     * The parts an editor shows, one per field of the layout and in its order; a field that accepts
     * a pair is two parts, the optional half first for the count ({@code min,max}) and second for
     * the neutral loss.
     *
     * @return the parts
     */
    public List<VariableModPart> parts() {
        List<VariableModPart> parts = new ArrayList<>();
        for (VariableModLayout.Entry entry : codec.layout().fields()) {
            switch (entry.field()) {
                case MASS -> parts.add(VariableModPart.MASS);
                case RESIDUES -> parts.add(VariableModPart.RESIDUES);
                case BINARY_GROUP -> parts.add(VariableModPart.BINARY_GROUP);
                case COUNT -> {
                    if (entry.acceptsPair()) {
                        parts.add(VariableModPart.MINIMUM_COUNT);
                    }
                    parts.add(VariableModPart.MAXIMUM_COUNT);
                }
                case TERMINAL_DISTANCE -> parts.add(VariableModPart.TERMINAL_DISTANCE);
                case TERMINUS -> parts.add(VariableModPart.TERMINUS);
                case REQUIRED -> parts.add(VariableModPart.REQUIRED);
                case NEUTRAL_LOSS -> {
                    parts.add(VariableModPart.NEUTRAL_LOSS);
                    if (entry.acceptsPair()) {
                        parts.add(VariableModPart.SECOND_NEUTRAL_LOSS);
                    }
                }
            }
        }
        return List.copyOf(parts);
    }

    /**
     * The text of one part of a value, as the part's control shows it.
     *
     * @param value the value
     * @param part a part of {@link #parts()}
     * @return the text; empty for an absent minimum count or second neutral loss
     * @throws IllegalArgumentException if the release's layout has no such part
     */
    public String partText(VariableModification value, VariableModPart part) {
        Objects.requireNonNull(value, "value");
        requirePart(part);
        return switch (part) {
            case MASS -> Numbers.text(value.mass());
            case RESIDUES -> value.residues();
            case BINARY_GROUP -> Integer.toString(value.binaryGroup());
            case MINIMUM_COUNT -> {
                OptionalInt minimum = value.minimumCount();
                yield minimum.isPresent() ? Integer.toString(minimum.getAsInt()) : "";
            }
            case MAXIMUM_COUNT -> Integer.toString(value.maximumCount());
            case TERMINAL_DISTANCE -> Integer.toString(value.terminalDistance());
            case TERMINUS -> Integer.toString(value.terminusCode());
            case REQUIRED -> Integer.toString(value.requirementCode());
            case NEUTRAL_LOSS -> Numbers.text(value.neutralLosses().get(0));
            case SECOND_NEUTRAL_LOSS -> {
                List<BigDecimal> losses = value.neutralLosses();
                yield losses.size() > 1 ? Numbers.text(losses.get(1)) : "";
            }
        };
    }

    /**
     * A value with one part set from text, read as the release's layout reads that field.
     *
     * @param slot the slot being edited, for the diagnostic
     * @param value the slot's current value
     * @param part the part
     * @param text what the scientist entered; surrounding white space is ignored, and an empty text
     *     clears an {@linkplain VariableModPart#optionalHalf() optional half}
     * @return the new value; every other part kept
     * @throws IllegalArgumentException if the slot is not one of the release's
     * @throws ValueSyntaxException naming the slot and the part, if the release's layout has no
     *     such part or the text cannot be read as it -- not one number, not a whole number, a
     *     residue character the release does not accept
     */
    public VariableModification withPart(
            String slot, VariableModification value, VariableModPart part, String text) {
        requireSlot(slot);
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(text, "text");
        if (!parts().contains(part)) {
            throw new ValueSyntaxException(
                    slot,
                    part.label(),
                    codec.release()
                            + " has no "
                            + part.label()
                            + " in its variable-modification tuple, whose fields are "
                            + codec.layout().describe());
        }
        String[] tokens = Numbers.tokens(text);
        if (tokens.length == 0 && part.optionalHalf()) {
            return part == VariableModPart.MINIMUM_COUNT
                    ? with(value, OptionalInt.empty(), value.maximumCount())
                    : withLosses(value, List.of(value.neutralLosses().get(0)));
        }
        if (tokens.length != 1) {
            throw new ValueSyntaxException(
                    slot,
                    part.label(),
                    "\""
                            + text.strip()
                            + "\" is not one value; enter "
                            + (part == VariableModPart.RESIDUES
                                    ? "one residue token"
                                    : "one number")
                            + (part.optionalHalf() ? ", or nothing for none" : ""));
        }
        String token = tokens[0];
        return switch (part) {
            case MASS -> withMass(value, Numbers.decimal(slot, part.label(), token));
            case RESIDUES -> withResidues(value, residues(slot, token));
            case BINARY_GROUP -> withCodes(value, whole(slot, part, token), null, null, null);
            case MINIMUM_COUNT ->
                    with(value, OptionalInt.of(whole(slot, part, token)), value.maximumCount());
            case MAXIMUM_COUNT -> with(value, value.minimumCount(), whole(slot, part, token));
            case TERMINAL_DISTANCE -> withCodes(value, null, whole(slot, part, token), null, null);
            case TERMINUS -> withCodes(value, null, null, whole(slot, part, token), null);
            case REQUIRED -> withCodes(value, null, null, null, whole(slot, part, token));
            case NEUTRAL_LOSS -> {
                List<BigDecimal> losses = new ArrayList<>(value.neutralLosses());
                losses.set(0, Numbers.decimal(slot, part.label(), token));
                yield withLosses(value, losses);
            }
            case SECOND_NEUTRAL_LOSS ->
                    withLosses(
                            value,
                            List.of(
                                    value.neutralLosses().get(0),
                                    Numbers.decimal(slot, part.label(), token)));
        };
    }

    /**
     * A value with one character of the residue token selected or cleared: the residue multi-select
     * and the terminus choices. The token is rewritten in a fixed order -- N-terminal codes,
     * letters, C-terminal codes -- which Comet, sorting the characters, reads the same way.
     *
     * @param slot the slot being edited, for the diagnostic
     * @param value the slot's current value
     * @param character a residue letter or terminal code
     * @param selected whether the token should hold it
     * @return the new value; the same value if nothing changes
     * @throws IllegalArgumentException if the slot is not one of the release's
     * @throws ValueSyntaxException naming the slot, if the release's alphabet does not hold the
     *     character, or clearing it would leave the token empty
     */
    public VariableModification withResidue(
            String slot, VariableModification value, char character, boolean selected) {
        requireSlot(slot);
        Objects.requireNonNull(value, "value");
        String label = VariableModPart.RESIDUES.label();
        if (!alphabet().accepts(character)) {
            throw new ValueSyntaxException(
                    slot,
                    label,
                    "'"
                            + character
                            + "' is not offered: "
                            + codec.release()
                            + " does not accept it in a residue token; its residue alphabet is "
                            + alphabet().describe());
        }
        if (value.holds(character) == selected) {
            return value;
        }
        StringBuilder token = new StringBuilder();
        for (int index = 0; index < TOKEN_ORDER.length(); index++) {
            char candidate = TOKEN_ORDER.charAt(index);
            boolean held = candidate == character ? selected : value.holds(candidate);
            if (held) {
                token.append(candidate);
            }
        }
        if (token.isEmpty()) {
            throw new ValueSyntaxException(
                    slot,
                    label,
                    "a modification applies to at least one residue or terminus; remove the"
                            + " modification to leave the slot unused");
        }
        return withResidues(value, token.toString());
    }

    /**
     * The documented values of a coded part, with their words: the four termini and the three
     * requirements. Other parts are entered as numbers or a residue token and have none.
     *
     * @param part the part
     * @return the choices, in code order; empty for a part without coded values
     */
    public List<VariableModChoice> choices(VariableModPart part) {
        Objects.requireNonNull(part, "part");
        List<VariableModChoice> choices = new ArrayList<>();
        if (part == VariableModPart.TERMINUS) {
            for (VariableModification.Terminus terminus : VariableModification.Terminus.values()) {
                choices.add(
                        new VariableModChoice(
                                Integer.toString(terminus.code()),
                                terminus.words(),
                                "A terminal distance of 0 or more counts from the "
                                        + terminus.words()
                                        + "."));
            }
        } else if (part == VariableModPart.REQUIRED) {
            for (VariableModification.Requirement requirement :
                    VariableModification.Requirement.values()) {
                choices.add(
                        new VariableModChoice(
                                Integer.toString(requirement.code()),
                                requirement.words(),
                                requirement.explanation()));
            }
        }
        return List.copyOf(choices);
    }

    /**
     * What a part means, in a sentence, from Comet's {@code variable_modXX} page.
     *
     * @param part the part
     * @return the explanation
     */
    public static String explanation(VariableModPart part) {
        Objects.requireNonNull(part, "part");
        return switch (part) {
            case MASS -> "The mass difference the modification adds; 0.0 leaves the slot unused.";
            case RESIDUES ->
                    "The residues it may modify, and n or c for any peptide N- or C-terminus"
                            + " (with ^ or $ for the protein's own termini where the release has"
                            + " them).";
            case BINARY_GROUP ->
                    "0: a variable modification, every permutation of modified and unmodified"
                            + " residues is analysed. Any other number: a binary group, whose"
                            + " residues are all modified or all unmodified.";
            case MINIMUM_COUNT ->
                    "The fewest residues a peptide must carry with this modification; empty for"
                            + " no minimum.";
            case MAXIMUM_COUNT ->
                    "The most residues of one peptide this modification may be applied to.";
            case TERMINAL_DISTANCE ->
                    "-1: no distance constraint. -2: anywhere except the peptide's C-terminal"
                            + " residue. 0: only the terminal residue. N: the terminal residue"
                            + " through the next N residues.";
            case TERMINUS -> "Which terminus a distance of 0 or more counts from.";
            case REQUIRED ->
                    "0 optional, 1 required (only peptides carrying it are analysed), -1"
                            + " exclusive (at most one of the exclusive set in a peptide).";
            case NEUTRAL_LOSS ->
                    "A fragment neutral loss also analysed for fragments carrying the"
                            + " modification; 0.0 for none.";
            case SECOND_NEUTRAL_LOSS -> "A second fragment neutral loss; empty for none.";
        };
    }

    /**
     * Why the release cannot hold a value, such as a preset made with {@code ^} offered to a
     * release without it.
     *
     * @param value the value
     * @return the reason, or empty if a slot of the release can hold it
     */
    public Optional<String> unwritable(VariableModification value) {
        return codec.unwritable(value);
    }

    private void requireSlot(String slot) {
        Objects.requireNonNull(slot, "slot");
        if (!codec.slots().contains(slot)) {
            throw new IllegalArgumentException(
                    "\"" + slot + "\" is not a variable-modification slot of " + codec.release());
        }
    }

    private void requirePart(VariableModPart part) {
        Objects.requireNonNull(part, "part");
        if (!parts().contains(part)) {
            throw new IllegalArgumentException(
                    codec.release() + " has no " + part.label() + " in its tuple");
        }
    }

    private String residues(String slot, String token) {
        String problem = VariableModification.residueProblem(token);
        if (problem == null) {
            problem = codec.refusedResidues(token);
        }
        if (problem != null) {
            throw new ValueSyntaxException(slot, VariableModPart.RESIDUES.label(), problem);
        }
        return token;
    }

    private static int whole(String slot, VariableModPart part, String token) {
        return Numbers.whole(slot, part.label(), token);
    }

    private static VariableModification withMass(VariableModification value, BigDecimal mass) {
        return new VariableModification(
                mass,
                value.residues(),
                value.binaryGroup(),
                value.minimumCount(),
                value.maximumCount(),
                value.terminalDistance(),
                value.terminusCode(),
                value.requirementCode(),
                value.neutralLosses());
    }

    private static VariableModification withResidues(VariableModification value, String residues) {
        return new VariableModification(
                value.mass(),
                residues,
                value.binaryGroup(),
                value.minimumCount(),
                value.maximumCount(),
                value.terminalDistance(),
                value.terminusCode(),
                value.requirementCode(),
                value.neutralLosses());
    }

    private static VariableModification with(
            VariableModification value, OptionalInt minimum, int maximum) {
        return new VariableModification(
                value.mass(),
                value.residues(),
                value.binaryGroup(),
                minimum,
                maximum,
                value.terminalDistance(),
                value.terminusCode(),
                value.requirementCode(),
                value.neutralLosses());
    }

    private static VariableModification withCodes(
            VariableModification value,
            Integer binaryGroup,
            Integer distance,
            Integer terminus,
            Integer requirement) {
        return new VariableModification(
                value.mass(),
                value.residues(),
                binaryGroup == null ? value.binaryGroup() : binaryGroup,
                value.minimumCount(),
                value.maximumCount(),
                distance == null ? value.terminalDistance() : distance,
                terminus == null ? value.terminusCode() : terminus,
                requirement == null ? value.requirementCode() : requirement,
                value.neutralLosses());
    }

    private static VariableModification withLosses(
            VariableModification value, List<BigDecimal> losses) {
        return new VariableModification(
                value.mass(),
                value.residues(),
                value.binaryGroup(),
                value.minimumCount(),
                value.maximumCount(),
                value.terminalDistance(),
                value.terminusCode(),
                value.requirementCode(),
                losses);
    }
}
