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

package org.cometgui.workflow.steps;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.cometgui.domain.tools.CapabilityEvidence;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.provenance.manifest.ProvenanceSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The words and keys of {@link ToolWarnings}, every expected value typed out here.
 *
 * <p>The sentences are pinned whole: a reviewer reading a provenance record a year later reads
 * these words, and a changed word is a changed record.
 */
class ToolWarningsTest {

    private static final Pattern KEY = Pattern.compile(ProvenanceSchema.SETTINGS_KEY_PATTERN);

    private static final Path COMET =
            Path.of("tools", "comet", "2026.03.0", "bin", "comet").toAbsolutePath();

    private static final String SHA256 =
            "ad93b4cf60c2ed7afc2f41b8a3a05567c3938d999f17deb7b3676bc1fa91e7ed";

    static final ToolAdvisory COMET_ADVISORY =
            new ToolAdvisory(
                    "comet.local-binary-is-unverified",
                    "This Comet was registered from a file on this machine.");

    static final DeclaredCapability PARAMS_CLAIM =
            new DeclaredCapability(
                    ToolCapability.COMPLETE_PARAMS_QUERY,
                    CapabilityEvidence.OBSERVED_BY_EXECUTION,
                    "run on linux-x86-64 by the Comet 2026.03.0 intake on 2026-10-04");

    static final DeclaredCapability PIN_CLAIM =
            new DeclaredCapability(
                    ToolCapability.PIN_OUTPUT,
                    CapabilityEvidence.UNVERIFIED,
                    "from upstream's release notes");

    /** The sentence for {@link #PARAMS_CLAIM} of Comet 2026.03.0, typed out. */
    static final String PARAMS_SENTENCE =
            "Comet 2026.03.0: the artefact manifest claims COMPLETE_PARAMS_QUERY"
                    + " (observed-by-execution evidence), but the probe run when this build was"
                    + " installed did not find it, so CometGUI treats the build as lacking"
                    + " COMPLETE_PARAMS_QUERY (R-TOOL-07: where manifest and probe disagree, the"
                    + " probe wins).";

    /** The sentence for {@link #PIN_CLAIM} of Comet 2026.03.0, typed out. */
    static final String PIN_SENTENCE =
            "Comet 2026.03.0: the artefact manifest claims PIN_OUTPUT (unverified evidence), but"
                    + " the probe run when this build was installed did not find it, so CometGUI"
                    + " treats the build as lacking PIN_OUTPUT (R-TOOL-07: where manifest and"
                    + " probe disagree, the probe wins).";

    /** The sentence for an unconfirmed XML_DECOY_OUTPUT claim of Percolator 3.07.1, typed out. */
    static final String DECOY_SENTENCE =
            "Percolator 3.07.1: the artefact manifest claims XML_DECOY_OUTPUT"
                    + " (inferred-from-artefact-bytes evidence), but the probe run when this build"
                    + " was installed did not find it, so CometGUI treats the build as lacking"
                    + " XML_DECOY_OUTPUT (R-TOOL-07: where manifest and probe disagree, the probe"
                    + " wins).";

    private static CometSelection comet(
            List<ToolAdvisory> advisories, List<DeclaredCapability> claims) {
        return new CometSelection(
                COMET,
                ToolVersion.parse("2026.03.0"),
                SHA256,
                true,
                Optional.empty(),
                advisories,
                claims);
    }

    private static ToolOffer percolator(
            List<ToolAdvisory> advisories, List<DeclaredCapability> claims) {
        return new ToolOffer(
                ToolName.PERCOLATOR,
                ToolVersion.parse("3.07.1"),
                ToolOrigin.MANAGED,
                ToolInstallState.INSTALLED,
                List.of(
                        new DeclaredCapability(
                                ToolCapability.XML_OUTPUT,
                                CapabilityEvidence.OBSERVED_BY_EXECUTION,
                                "probed")),
                advisories,
                Optional.empty(),
                Optional.of(
                        Path.of("tools", "percolator", "3.07.1", "bin", "percolator")
                                .toAbsolutePath()),
                OptionalLong.of(1000),
                claims);
    }

    private static final DeclaredCapability DECOY_CLAIM =
            new DeclaredCapability(
                    ToolCapability.XML_DECOY_OUTPUT,
                    CapabilityEvidence.INFERRED_FROM_ARTEFACT_BYTES,
                    "a writer literal in the artefact's bytes");

    private static final ToolAdvisory PERCOLATOR_ADVISORY =
            new ToolAdvisory(
                    "percolator.3-07-1-predates-pep-above-one-fix",
                    "Percolator 3.07.1 predates the fix for PEP values exceeding 1.0.");

    @Test
    @DisplayName(
            "a discrepancy names the tool, the version, the capability, the claim and the probe")
    void theSentence() {
        assertAll(
                () ->
                        assertEquals(
                                PARAMS_SENTENCE,
                                ToolWarnings.discrepancy(
                                        "Comet", ToolVersion.parse("2026.03.0"), PARAMS_CLAIM)),
                () ->
                        assertEquals(
                                DECOY_SENTENCE,
                                ToolWarnings.discrepancy(
                                        "Percolator", ToolVersion.parse("3.07.1"), DECOY_CLAIM)));
    }

    @Test
    @DisplayName("Comet's tool record: each advisory as written, then each discrepancy, in order")
    void cometWarnings() {
        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "This Comet was registered from a file on this machine.",
                                        PARAMS_SENTENCE,
                                        PIN_SENTENCE),
                                ToolWarnings.comet(
                                        comet(
                                                List.of(COMET_ADVISORY),
                                                List.of(PARAMS_CLAIM, PIN_CLAIM)))),
                () ->
                        assertEquals(
                                List.of(PIN_SENTENCE, PARAMS_SENTENCE),
                                ToolWarnings.comet(
                                        comet(List.of(), List.of(PIN_CLAIM, PARAMS_CLAIM)))),
                () ->
                        assertEquals(
                                List.of("This Comet was registered from a file on this machine."),
                                ToolWarnings.comet(comet(List.of(COMET_ADVISORY), List.of()))),
                () ->
                        assertEquals(
                                List.of(),
                                ToolWarnings.comet(comet(List.of(), List.of())),
                                "no advisory and no unconfirmed claim: no warning at all"));
    }

    @Test
    @DisplayName("Percolator's tool record: each advisory as written, then each discrepancy")
    void percolatorWarnings() {
        assertAll(
                () ->
                        assertEquals(
                                List.of(
                                        "Percolator 3.07.1 predates the fix for PEP values"
                                                + " exceeding 1.0.",
                                        DECOY_SENTENCE),
                                ToolWarnings.percolator(
                                        percolator(
                                                List.of(PERCOLATOR_ADVISORY),
                                                List.of(DECOY_CLAIM)))),
                () ->
                        assertEquals(
                                List.of(),
                                ToolWarnings.percolator(percolator(List.of(), List.of()))));
    }

    @Test
    @DisplayName("Comet's settings: comet.advisory.<id> and comet.probe-discrepancy.<nn> from 01")
    void cometSettings() {
        Map<String, String> expected = new TreeMap<>();
        expected.put(
                "comet.advisory.comet.local-binary-is-unverified",
                "This Comet was registered from a file on this machine.");
        expected.put("comet.probe-discrepancy.01", PARAMS_SENTENCE);
        expected.put("comet.probe-discrepancy.02", PIN_SENTENCE);
        assertAll(
                () ->
                        assertEquals(
                                expected,
                                ToolWarnings.cometSettings(
                                        comet(
                                                List.of(COMET_ADVISORY),
                                                List.of(PARAMS_CLAIM, PIN_CLAIM)))),
                () ->
                        assertEquals(
                                Map.of(),
                                ToolWarnings.cometSettings(comet(List.of(), List.of())),
                                "nothing to warn about: no key at all"));
    }

    @Test
    @DisplayName("Percolator's settings: percolator.probe-discrepancy.<nn>, never its advisories")
    void percolatorSettings() {
        assertAll(
                () ->
                        assertEquals(
                                Map.of("percolator.probe-discrepancy.01", DECOY_SENTENCE),
                                ToolWarnings.percolatorSettings(
                                        percolator(
                                                List.of(PERCOLATOR_ADVISORY),
                                                List.of(DECOY_CLAIM)))),
                () ->
                        assertEquals(
                                Map.of(),
                                ToolWarnings.percolatorSettings(
                                        percolator(List.of(PERCOLATOR_ADVISORY), List.of()))));
    }

    @Test
    @DisplayName("ten and more discrepancies keep two-digit numbering in order")
    void numbering() {
        List<DeclaredCapability> many = new ArrayList<>();
        for (ToolCapability capability : ToolCapability.values()) {
            if (capability.belongsTo(ToolName.PERCOLATOR)
                    && capability != ToolCapability.XML_OUTPUT) {
                many.add(
                        new DeclaredCapability(
                                capability, CapabilityEvidence.UNVERIFIED, "release notes"));
            }
        }
        Map<String, String> settings = ToolWarnings.percolatorSettings(percolator(List.of(), many));
        assertAll(
                () -> assertEquals(11, many.size(), "eleven Percolator claims, hand-counted"),
                () ->
                        assertEquals(
                                List.of(
                                        "percolator.probe-discrepancy.01",
                                        "percolator.probe-discrepancy.02",
                                        "percolator.probe-discrepancy.03",
                                        "percolator.probe-discrepancy.04",
                                        "percolator.probe-discrepancy.05",
                                        "percolator.probe-discrepancy.06",
                                        "percolator.probe-discrepancy.07",
                                        "percolator.probe-discrepancy.08",
                                        "percolator.probe-discrepancy.09",
                                        "percolator.probe-discrepancy.10",
                                        "percolator.probe-discrepancy.11"),
                                List.copyOf(settings.keySet())),
                () ->
                        assertTrue(
                                settings.get("percolator.probe-discrepancy.01")
                                        .contains(" claims XML_DECOY_OUTPUT "),
                                "manifest order: the first claim is first"),
                () ->
                        assertTrue(
                                settings.get("percolator.probe-discrepancy.11")
                                        .contains(" claims NO_ANALYTICS_OPTION "),
                                "and the last is last"));
    }

    @Test
    @DisplayName("every key this class makes matches the settings-key pattern")
    void everyKeyMatchesThePattern() {
        assertEquals("comet.advisory.", ToolWarnings.COMET_ADVISORY_PREFIX);
        assertEquals("comet.probe-discrepancy.", ToolWarnings.COMET_DISCREPANCY_PREFIX);
        assertEquals("percolator.probe-discrepancy.", ToolWarnings.PERCOLATOR_DISCREPANCY_PREFIX);
        List<String> keys = new ArrayList<>();
        keys.addAll(
                ToolWarnings.cometSettings(
                                comet(List.of(COMET_ADVISORY), List.of(PARAMS_CLAIM, PIN_CLAIM)))
                        .keySet());
        keys.addAll(
                ToolWarnings.percolatorSettings(percolator(List.of(), List.of(DECOY_CLAIM)))
                        .keySet());
        assertEquals(4, keys.size(), keys::toString);
        for (String key : keys) {
            assertTrue(KEY.matcher(key).matches(), key);
        }
    }

    @Test
    @DisplayName("a Comet selection refuses an unconfirmed claim of another tool's capability")
    void cometClaimsAreComets() {
        assertThrows(IllegalArgumentException.class, () -> comet(List.of(), List.of(DECOY_CLAIM)));
    }
}
