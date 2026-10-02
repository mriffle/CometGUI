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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the curated metadata says about one Comet parameter: the specification's <em>Parameter
 * definition model</em>, plus the search aliases and related parameters the editor and the
 * generated reference page need.
 *
 * <p>Values are held as the text {@code comet.params} uses -- {@code 20.0}, {@code 0 0}, {@code
 * 15.9949 M 0 3 -1 0 0 0.0} -- not as typed Java values. Typing them is the parser's and codecs'
 * work; what a definition must guarantee is that the text is consistent with its own kind, bounds
 * and choices, and {@link MetadataLoader} refuses a file in which it is not.
 *
 * @param name the parameter name exactly as Comet spells it
 * @param displayName the label the editor shows
 * @param category the Advanced-mode group
 * @param kind the structural kind
 * @param visibility the lowest editor level that shows it
 * @param defaultValue the value {@code comet -q} writes for this version, as text; empty only where
 *     the serialisation rule allows it
 * @param minimum the smallest legal number, as text, where one exists
 * @param maximum the largest legal number, as text, where one exists
 * @param choices the labelled values of an enumerated kind; empty for every other kind
 * @param shortHelp one or two sentences, in this project's words
 * @param detailedHelpRef the upstream page or source line the help was written from
 * @param supportedVersions the Comet versions this definition claims
 * @param serialization how the value is written
 * @param validators the named rules beyond kind, bounds and choices
 * @param aliases extra words the parameter search matches, such as "missed cleavage"
 * @param related other parameters a reader of this one should look at
 */
public record ParameterDefinition(
        String name,
        String displayName,
        ParameterCategory category,
        ValueKind kind,
        VisibilityLevel visibility,
        String defaultValue,
        Optional<String> minimum,
        Optional<String> maximum,
        List<Choice> choices,
        String shortHelp,
        String detailedHelpRef,
        VersionRange supportedVersions,
        SerializationRule serialization,
        List<ValidatorId> validators,
        List<String> aliases,
        List<String> related) {

    /** Validates presence and takes immutable copies of the lists. */
    public ParameterDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        choices = List.copyOf(choices);
        Objects.requireNonNull(shortHelp, "shortHelp");
        Objects.requireNonNull(detailedHelpRef, "detailedHelpRef");
        Objects.requireNonNull(supportedVersions, "supportedVersions");
        Objects.requireNonNull(serialization, "serialization");
        validators = List.copyOf(validators);
        aliases = List.copyOf(aliases);
        related = List.copyOf(related);
    }

    /**
     * The labelled choices, immutable.
     *
     * @return the choices
     */
    @Override
    public List<Choice> choices() {
        return List.copyOf(choices);
    }

    /**
     * The named validators, immutable.
     *
     * @return the validators
     */
    @Override
    public List<ValidatorId> validators() {
        return List.copyOf(validators);
    }

    /**
     * The search aliases, immutable.
     *
     * @return the aliases
     */
    @Override
    public List<String> aliases() {
        return List.copyOf(aliases);
    }

    /**
     * The related parameter names, immutable.
     *
     * @return the names
     */
    @Override
    public List<String> related() {
        return List.copyOf(related);
    }
}
