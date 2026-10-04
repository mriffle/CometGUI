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

package org.cometgui.params.comet.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.ParamsFiles;
import org.cometgui.params.comet.model.CometParameters;
import org.cometgui.params.comet.model.ValueOrigin;
import org.cometgui.params.comet.parser.CometParamsParser;
import org.cometgui.params.comet.parser.ParseResult;
import org.cometgui.params.comet.schema.CuratedMetadata;
import org.cometgui.params.comet.schema.ParameterCategory;
import org.cometgui.params.comet.schema.ParameterDefinition;
import org.cometgui.params.comet.schema.ValidatorId;
import org.cometgui.params.comet.schema.ValueKind;
import org.cometgui.params.comet.schema.VersionRange;

/**
 * What the validation tests share: the REAL {@code comet -q} output of Comet 2026.02.2 parsed into
 * a model, small CONSTRUCTED edits of it (one parameter's value text at a time), and CONSTRUCTED
 * variants of the bundled metadata for the rules that only bad metadata can reach.
 */
final class Models {

    static final CuratedMetadata METADATA = ParamsFiles.metadata();

    static final ToolVersion COMET = ParamsFiles.COMET;

    private Models() {}

    /**
     * The real {@code -q} file, parsed.
     *
     * @return its model
     */
    static CometParameters real() {
        return parse(METADATA, ParamsFiles.complete());
    }

    /**
     * The real {@code -q} file, parsed, with the workflow's outputs enforced: what the application
     * presents before the user changes anything.
     *
     * @return the model
     */
    static CometParameters enforced() {
        return real().withWorkflowEnforcedOutputs();
    }

    /**
     * Parses a text that must parse.
     *
     * @param metadata the metadata
     * @param text the file
     * @return the model
     */
    static CometParameters parse(CuratedMetadata metadata, String text) {
        ParseResult result = new CometParamsParser(metadata, COMET).parse(text);
        assertTrue(result.succeeded(), () -> "the test input must parse: " + result.errors());
        return result.model().orElseThrow();
    }

    /**
     * The enforced real model with CONSTRUCTED value texts.
     *
     * @param nameThenText parameter names and value texts, alternating
     * @return the model
     */
    static CometParameters with(String... nameThenText) {
        CometParameters model = enforced();
        for (int index = 0; index < nameThenText.length; index += 2) {
            model = model.withText(nameThenText[index], nameThenText[index + 1], ValueOrigin.USER);
        }
        return model;
    }

    /** Comet 2026.03.0, whose real {@code -q} output is the second fixture. */
    static final ToolVersion COMET_2026_03_0 = ToolVersion.parse("2026.03.0");

    /**
     * A release's real {@code -q} file, parsed for that release, with the workflow's outputs
     * enforced.
     *
     * @param version the release
     * @return the model
     */
    static CometParameters enforced(ToolVersion version) {
        ParseResult result =
                new CometParamsParser(METADATA, version).parse(ParamsFiles.complete(version));
        assertTrue(result.succeeded(), () -> "the test input must parse: " + result.errors());
        return result.model().orElseThrow().withWorkflowEnforcedOutputs();
    }

    /**
     * A release's enforced real model with CONSTRUCTED value texts.
     *
     * @param version the release
     * @param nameThenText parameter names and value texts, alternating
     * @return the model
     */
    static CometParameters with(ToolVersion version, String... nameThenText) {
        CometParameters model = enforced(version);
        for (int index = 0; index < nameThenText.length; index += 2) {
            model = model.withText(nameThenText[index], nameThenText[index + 1], ValueOrigin.USER);
        }
        return model;
    }

    static ValidationReport validate(CometParameters model) {
        return CometValidator.standard().validate(model);
    }

    /**
     * Asserts the one finding of a report and returns it.
     *
     * @param report the report
     * @return its only finding
     */
    static Finding only(ValidationReport report) {
        assertEquals(1, report.findings().size(), () -> "expected exactly one finding: " + report);
        return report.findings().get(0);
    }

    /**
     * Asserts everything a finding is attached to.
     *
     * @param finding the finding
     * @param rule its rule
     * @param category its category
     * @param parameters its parameters, in order
     */
    static void assertAttached(
            Finding finding, Rule rule, ParameterCategory category, String... parameters) {
        assertEquals(rule, finding.rule(), finding::toString);
        rule.fixedSeverity()
                .ifPresent(fixed -> assertEquals(fixed, finding.severity(), finding::toString));
        assertEquals(List.of(parameters), finding.parameters(), finding::toString);
        assertEquals(Optional.of(category), finding.category(), finding::toString);
    }

    /**
     * The bundled metadata with one definition changed. The result is CONSTRUCTED: it does not go
     * through the loader, so it can hold what the loader would refuse.
     *
     * @param name the parameter
     * @param change what to do to its definition
     * @return the metadata
     */
    static CuratedMetadata redefine(String name, UnaryOperator<ParameterDefinition> change) {
        List<ParameterDefinition> definitions = new ArrayList<>();
        boolean found = false;
        for (ParameterDefinition definition : METADATA.parameters()) {
            if (definition.name().equals(name)) {
                definitions.add(change.apply(definition));
                found = true;
            } else {
                definitions.add(definition);
            }
        }
        assertTrue(found, name);
        return new CuratedMetadata(
                METADATA.schemaVersion(),
                METADATA.versions(),
                definitions,
                METADATA.internal(),
                METADATA.enzymeTable());
    }

    /**
     * A definition with other validators.
     *
     * @param definition the definition
     * @param validators the validators
     * @return the changed definition
     */
    static ParameterDefinition withValidators(
            ParameterDefinition definition, List<ValidatorId> validators) {
        return copy(definition, definition.kind(), definition.supportedVersions(), validators);
    }

    /**
     * A definition that claims other versions.
     *
     * @param definition the definition
     * @param versions the versions
     * @return the changed definition
     */
    static ParameterDefinition withVersions(ParameterDefinition definition, VersionRange versions) {
        return copy(definition, definition.kind(), versions, definition.validators());
    }

    /**
     * A definition of another kind.
     *
     * @param definition the definition
     * @param kind the kind
     * @return the changed definition
     */
    static ParameterDefinition withKind(ParameterDefinition definition, ValueKind kind) {
        return copy(definition, kind, definition.supportedVersions(), definition.validators());
    }

    private static ParameterDefinition copy(
            ParameterDefinition d,
            ValueKind kind,
            VersionRange versions,
            List<ValidatorId> validators) {
        return new ParameterDefinition(
                d.name(),
                d.displayName(),
                d.category(),
                kind,
                d.visibility(),
                d.defaultValue(),
                d.minimum(),
                d.maximum(),
                d.choices(),
                d.shortHelp(),
                d.inlineComment(),
                d.detailedHelpRef(),
                versions,
                d.serialization(),
                validators,
                d.aliases(),
                d.related());
    }
}
