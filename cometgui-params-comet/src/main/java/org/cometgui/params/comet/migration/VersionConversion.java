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

package org.cometgui.params.comet.migration;

import java.util.Objects;
import java.util.Optional;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.model.ParameterValue;
import org.cometgui.params.comet.model.ParameterValueCodec;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterDefinition;

/**
 * Carries one parameter's value text from one curated Comet version's schema to another's: the one
 * rule schema migration and the preset compatibility check share.
 *
 * <p>The text is read as a typed value under the <em>source</em> version's codec and written under
 * the <em>target</em> version's. For every kind but the variable-modification tuple the two codecs
 * are the same, so the text is unchanged. A tuple's field layout is the version's ({@code
 * R-PARAM-09}), so its text is re-laid-out field by field; the target codec refuses -- and this
 * reports, never guesses -- a value it cannot hold, such as two neutral losses where the target
 * takes one, or a non-default value in a field the target's tuple lacks. A conversion therefore
 * never changes what a value means: it is the same typed value written in the target's syntax, or
 * it is reported as not convertible.
 */
public final class VersionConversion {

    /** What became of one value. */
    public enum Status {

        /** The target takes the value as the same text. */
        SAME,

        /** The target takes the value, written as other text with the same meaning. */
        CONVERTED,

        /** The target version does not have the parameter at all. */
        NOT_IN_TARGET,

        /** The target has the parameter but cannot hold this value. */
        NOT_CONVERTIBLE;

        /**
         * Whether the target can take the value.
         *
         * @return {@code true} for {@link #SAME} and {@link #CONVERTED}
         */
        public boolean usable() {
            return this == SAME || this == CONVERTED;
        }
    }

    /**
     * One value carried, or not.
     *
     * @param parameter the parameter name
     * @param status what became of it
     * @param sourceText the value as the source version writes it
     * @param targetText the value as the target version writes it; present exactly when the status
     *     is usable
     * @param explanation one sentence saying what happened, naming both versions
     */
    public record Result(
            String parameter,
            Status status,
            String sourceText,
            Optional<String> targetText,
            String explanation) {

        /**
         * Validates the components.
         *
         * @throws IllegalArgumentException if the target text's presence does not match the status
         */
        public Result {
            Objects.requireNonNull(parameter, "parameter");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(sourceText, "sourceText");
            Objects.requireNonNull(targetText, "targetText");
            Objects.requireNonNull(explanation, "explanation");
            if (targetText.isPresent() != status.usable()) {
                throw new IllegalArgumentException(
                        parameter
                                + ": a "
                                + status
                                + " result "
                                + (status.usable() ? "needs" : "cannot have")
                                + " a target text");
            }
        }
    }

    private final CuratedMetadata metadata;

    private final ToolVersion from;

    private final ToolVersion to;

    private final ParameterValueCodec fromCodec;

    private final ParameterValueCodec toCodec;

    private VersionConversion(CuratedMetadata metadata, ToolVersion from, ToolVersion to) {
        this.metadata = metadata;
        this.from = from;
        this.to = to;
        this.fromCodec = ParameterValueCodec.forVersion(metadata, from);
        this.toCodec = ParameterValueCodec.forVersion(metadata, to);
    }

    /**
     * The conversion between two curated versions.
     *
     * @param metadata the curated metadata
     * @param from the version the values were written for
     * @param to the version they are carried to
     * @return the conversion
     * @throws IllegalArgumentException if the metadata was not curated against either version
     */
    public static VersionConversion between(
            CuratedMetadata metadata, ToolVersion from, ToolVersion to) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        for (ToolVersion version : new ToolVersion[] {from, to}) {
            if (metadata.version(version).isEmpty()) {
                throw new IllegalArgumentException(
                        "the metadata was not curated against Comet "
                                + version.text()
                                + ", so no value can be carried "
                                + (version.equals(from) ? "from" : "to")
                                + " it");
            }
        }
        return new VersionConversion(metadata, from, to);
    }

    /**
     * The source version.
     *
     * @return the version the values were written for
     */
    public ToolVersion from() {
        return from;
    }

    /**
     * The target version.
     *
     * @return the version the values are carried to
     */
    public ToolVersion to() {
        return to;
    }

    /**
     * Carries one value.
     *
     * @param parameter a parameter the source version models
     * @param text its value text, as the source version reads it
     * @return what became of it
     * @throws IllegalArgumentException if the source version does not model the parameter
     * @throws org.cometgui.params.comet.value.ValueSyntaxException if the text cannot be read as
     *     the parameter's kind in the source version
     */
    public Result convert(String parameter, String text) {
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(text, "text");
        ParameterDefinition source =
                metadata.parameter(parameter, from)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                parameter
                                                        + " is not a parameter of Comet "
                                                        + from.text()));
        ParameterValue value = fromCodec.parse(source, text);
        String sourceText = fromCodec.format(source, value);
        Optional<ParameterDefinition> target = metadata.parameter(parameter, to);
        if (target.isEmpty()) {
            return new Result(
                    parameter,
                    Status.NOT_IN_TARGET,
                    sourceText,
                    Optional.empty(),
                    "Comet "
                            + to.text()
                            + " has no parameter "
                            + parameter
                            + "; it is a parameter of Comet "
                            + from.text());
        }
        String targetText;
        try {
            targetText = toCodec.format(target.get(), value);
        } catch (IllegalArgumentException cannotHold) {
            return new Result(
                    parameter,
                    Status.NOT_CONVERTIBLE,
                    sourceText,
                    Optional.empty(),
                    parameter
                            + " = "
                            + sourceText
                            + " (Comet "
                            + from.text()
                            + ") cannot be written for Comet "
                            + to.text()
                            + ": "
                            + cannotHold.getMessage());
        }
        if (targetText.equals(sourceText)) {
            return new Result(
                    parameter,
                    Status.SAME,
                    sourceText,
                    Optional.of(targetText),
                    parameter
                            + " = "
                            + sourceText
                            + " means the same in Comet "
                            + from.text()
                            + " and Comet "
                            + to.text());
        }
        return new Result(
                parameter,
                Status.CONVERTED,
                sourceText,
                Optional.of(targetText),
                parameter
                        + " = "
                        + sourceText
                        + " (Comet "
                        + from.text()
                        + ") is written "
                        + targetText
                        + " for Comet "
                        + to.text()
                        + ", with the same meaning");
    }
}
