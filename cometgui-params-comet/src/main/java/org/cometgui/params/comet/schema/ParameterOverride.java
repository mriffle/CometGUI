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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What one Comet release says differently about one parameter: the fields of the curated {@link
 * ParameterDefinition} that this release replaces, and only those.
 *
 * <p>An override lives in its release's {@link CometVersionRecord}, so it is data keyed by version
 * and never an {@code if (version ...)} in a codec or a rule. {@link
 * CuratedMetadata#parameter(String, org.cometgui.domain.tools.ToolVersion)} and {@link
 * CuratedMetadata#parametersFor(org.cometgui.domain.tools.ToolVersion)} apply it ({@link
 * #applyTo(ParameterDefinition)}); everything that reads a definition through them -- the parser's
 * defaults, the canonical writer's inline comment, the choice rule, drift detection -- sees the
 * release's own facts.
 *
 * <p>Five fields can differ by release: the default {@code comet -q} writes, the enum choices, the
 * short help, the help reference and the inline comment. An absent field is inherited from the
 * curated definition. The inline comment alone may be replaced by <em>none</em> (JSON {@code
 * null}), which is why it carries its own {@code replacesInlineComment} flag.
 *
 * <p>An override can also record where <em>CometGUI</em>, not Comet, differs for the release: a
 * {@linkplain #startingValue() starting value}, the value a new configuration of the release starts
 * with in place of Comet's default, always with the {@linkplain #decision() decision} that made it
 * ({@code D-012}: {@code spectral_library_name} starts empty rather than at {@code -q}'s
 * placeholder). It is not part of the release's definition -- {@link #applyTo} leaves the default
 * Comet's -- and is read through {@link CuratedMetadata#startingValue}, never by asking which
 * version it is. A configuration that took it carries {@link
 * org.cometgui.params.comet.model.ValueOrigin#COMETGUI_DEFAULT}, so it is never shown as Comet's.
 *
 * @param name the parameter the override is for
 * @param source the {@code https://} reference to where the release shows the difference
 * @param defaultValue the release's default, if it differs
 * @param choices the release's choices, if they differ
 * @param shortHelp the release's short help, if it differs
 * @param helpUrl the release's help reference, if it differs
 * @param replacesInlineComment whether the release has its own inline comment (or none)
 * @param inlineComment that comment; empty for none, and always empty when not replaced
 * @param startingValue CometGUI's starting value for a new configuration of the release, if it
 *     departs from the release's default
 * @param decision the recorded decision ({@code D-} and three digits) behind the starting value;
 *     present exactly when the starting value is
 */
public record ParameterOverride(
        String name,
        String source,
        Optional<String> defaultValue,
        Optional<List<Choice>> choices,
        Optional<String> shortHelp,
        Optional<String> helpUrl,
        boolean replacesInlineComment,
        Optional<String> inlineComment,
        Optional<String> startingValue,
        Optional<String> decision) {

    /**
     * The JSON names of the definition fields an override may replace: what the release itself says
     * differently.
     */
    public static final List<String> FIELDS =
            List.of("default", "choices", "inlineComment", "shortHelp", "helpUrl");

    /**
     * The JSON names of what an override may record about CometGUI rather than Comet: a starting
     * value and the decision behind it, always together.
     */
    public static final List<String> STARTING_FIELDS = List.of("startingValue", "decision");

    /**
     * How a decision is named: {@code D-} and three digits, as {@code DECISIONS.rst} numbers them.
     */
    private static final Pattern DECISION = Pattern.compile("D-[0-9]{3}");

    /**
     * Validates the components and takes an immutable copy of the choices.
     *
     * @throws IllegalArgumentException if the override replaces nothing, or carries an inline
     *     comment it does not replace
     */
    public ParameterOverride {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(choices, "choices");
        choices = choices.map(List::copyOf);
        Objects.requireNonNull(shortHelp, "shortHelp");
        Objects.requireNonNull(helpUrl, "helpUrl");
        Objects.requireNonNull(inlineComment, "inlineComment");
        Objects.requireNonNull(startingValue, "startingValue");
        Objects.requireNonNull(decision, "decision");
        if (startingValue.isPresent() != decision.isPresent()) {
            throw new IllegalArgumentException(
                    "the override for "
                            + name
                            + " has a starting value without its decision, or a decision without"
                            + " a starting value; CometGUI departs from Comet's default only by a"
                            + " recorded decision");
        }
        if (decision.isPresent() && !DECISION.matcher(decision.get()).matches()) {
            throw new IllegalArgumentException(
                    "the override for "
                            + name
                            + " names the decision \""
                            + decision.get()
                            + "\", which is not D- and three digits");
        }
        if (!replacesInlineComment && inlineComment.isPresent()) {
            throw new IllegalArgumentException(
                    "the override for " + name + " carries an inline comment it does not replace");
        }
        if (defaultValue.isEmpty()
                && choices.isEmpty()
                && shortHelp.isEmpty()
                && helpUrl.isEmpty()
                && !replacesInlineComment
                && startingValue.isEmpty()) {
            throw new IllegalArgumentException(
                    "the override for "
                            + name
                            + " replaces no field; it names at least one of "
                            + FIELDS
                            + " or a startingValue");
        }
    }

    /**
     * An override of the release's definition alone, recording no starting value.
     *
     * @param name the parameter the override is for
     * @param source where the release shows the difference
     * @param defaultValue the release's default, if it differs
     * @param choices the release's choices, if they differ
     * @param shortHelp the release's short help, if it differs
     * @param helpUrl the release's help reference, if it differs
     * @param replacesInlineComment whether the release has its own inline comment (or none)
     * @param inlineComment that comment; empty for none, and always empty when not replaced
     */
    public ParameterOverride(
            String name,
            String source,
            Optional<String> defaultValue,
            Optional<List<Choice>> choices,
            Optional<String> shortHelp,
            Optional<String> helpUrl,
            boolean replacesInlineComment,
            Optional<String> inlineComment) {
        this(
                name,
                source,
                defaultValue,
                choices,
                shortHelp,
                helpUrl,
                replacesInlineComment,
                inlineComment,
                Optional.empty(),
                Optional.empty());
    }

    /**
     * An override of the default alone.
     *
     * @param name the parameter
     * @param source where the release writes that default
     * @param defaultValue the release's default
     * @return the override
     */
    public static ParameterOverride ofDefault(String name, String source, String defaultValue) {
        return new ParameterOverride(
                name,
                source,
                Optional.of(defaultValue),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                Optional.empty());
    }

    /**
     * The JSON names of the fields this override replaces, in {@link #FIELDS} order.
     *
     * @return the replaced fields
     */
    public List<String> replacedFields() {
        List<String> fields = new ArrayList<>();
        defaultValue.ifPresent(value -> fields.add("default"));
        choices.ifPresent(value -> fields.add("choices"));
        if (replacesInlineComment) {
            fields.add("inlineComment");
        }
        shortHelp.ifPresent(value -> fields.add("shortHelp"));
        helpUrl.ifPresent(value -> fields.add("helpUrl"));
        return List.copyOf(fields);
    }

    /**
     * The definition as the release has it: the curated one with this override's fields replaced.
     * The starting value is not one of them: the definition's default stays the release's own.
     *
     * @param curated the curated definition of the same parameter
     * @return the release's definition
     * @throws IllegalArgumentException if {@code curated} is another parameter's
     */
    public ParameterDefinition applyTo(ParameterDefinition curated) {
        Objects.requireNonNull(curated, "curated");
        if (!curated.name().equals(name)) {
            throw new IllegalArgumentException(
                    "the override for " + name + " cannot apply to " + curated.name());
        }
        return new ParameterDefinition(
                curated.name(),
                curated.displayName(),
                curated.category(),
                curated.kind(),
                curated.visibility(),
                defaultValue.orElse(curated.defaultValue()),
                curated.minimum(),
                curated.maximum(),
                choices.orElse(curated.choices()),
                shortHelp.orElse(curated.shortHelp()),
                replacesInlineComment ? inlineComment : curated.inlineComment(),
                helpUrl.orElse(curated.detailedHelpRef()),
                curated.supportedVersions(),
                curated.serialization(),
                curated.validators(),
                curated.aliases(),
                curated.related());
    }
}
