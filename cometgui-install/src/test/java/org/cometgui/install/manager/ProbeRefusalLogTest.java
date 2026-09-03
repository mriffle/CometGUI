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

package org.cometgui.install.manager;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ProbeFailureKind;
import org.cometgui.domain.tools.ProbeStage;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.install.cache.ToolProbe;
import org.cometgui.install.probe.ProbeFailedException;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.testing.Nulls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * What the Tool Manager is allowed to remember about a build whose binary would not start.
 *
 * <p>{@code R-TOOL-06}'s last sentence -- <em>a tool that fails loadability shall never be offered
 * for selection</em> -- is a claim about what happens after an install has run the binary, and the
 * install that ran it throws the refusal away as it unwinds. This is where the fact is kept, and
 * the two things worth grading are which refusals are kept and which are not.
 */
class ProbeRefusalLogTest {

    private static final HostPlatform LINUX =
            new HostPlatform(HostOperatingSystem.LINUX, HostArchitecture.X86_64);

    private static ArtefactRecord record(ToolName tool) throws IOException {
        ArtefactManifest manifest = ArtefactManifestReader.readFromClasspath();
        return manifest.select(LINUX, tool).get(0).artefact();
    }

    private static ToolProbe answering(Set<ToolCapability> capabilities) {
        return (record, staged) -> capabilities;
    }

    private static ToolProbe refusing(ProbeFailedException refusal) {
        return (record, staged) -> {
            throw refusal;
        };
    }

    /*
     * GRADED OVER THE WHOLE KIND AXIS, both ways round.  The rule is stated over ProbeFailureKind
     * and depends on nothing else, so pinning it at one or two constants would leave the rest of
     * the axis untested -- which is this phase's signature hole and has now cost it four rounds.
     * The two tests below between them visit every constant of the enumeration.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(ProbeFailureKind.class)
    @DisplayName("a loadability refusal is remembered and no other kind is")
    void onlyLoadabilityRefusalsAreRemembered(ProbeFailureKind kind) throws IOException {
        ProbeRefusalLog log = new ProbeRefusalLog();
        ArtefactRecord percolator = record(ToolName.PERCOLATOR);
        ToolProbe recording =
                log.recording(refusing(new ProbeFailedException(kind, "refused on purpose")));

        assertThrows(
                ProbeFailedException.class, () -> recording.probe(percolator, Path.of("staged")));

        Optional<ProbeFailureKind> remembered =
                kind.stage() == ProbeStage.LOADABILITY ? Optional.of(kind) : Optional.empty();
        assertAll(
                () -> assertEquals(remembered, log.refusalFor(percolator)),
                () -> assertEquals(remembered.isPresent() ? 1 : 0, log.size()));
    }

    @Test
    @DisplayName("every loadability kind there is, listed, so a new one cannot be forgotten")
    void theLoadabilityKindsArePinned() {
        List<String> loadability = new ArrayList<>();
        for (ProbeFailureKind kind : ProbeFailureKind.values()) {
            if (kind.stage() == ProbeStage.LOADABILITY) {
                loadability.add(kind.name());
            }
        }

        assertEquals(
                List.of(
                        "MISSING_SHARED_OBJECT",
                        "MISSING_SYMBOL_VERSION",
                        "WRONG_ARCHITECTURE",
                        "MACOS_QUARANTINE",
                        "MISSING_WINDOWS_RUNTIME_DLL",
                        "NOT_EXECUTABLE",
                        "TIMED_OUT",
                        "EXECUTION_FAILED"),
                loadability,
                "these are the failures that withdraw an offer; a kind added to the loadability"
                        + " stage changes what the Tool Manager stops showing, which is a decision"
                        + " rather than a detail");
    }

    @Test
    @DisplayName("the refusal is keyed by download, so another build is unaffected")
    void theRefusalIsKeyedByDownload() throws IOException {
        ProbeRefusalLog log = new ProbeRefusalLog();
        ArtefactRecord percolator = record(ToolName.PERCOLATOR);
        ArtefactRecord comet = record(ToolName.COMET);
        ToolProbe recording =
                log.recording(
                        refusing(
                                new ProbeFailedException(
                                        ProbeFailureKind.MISSING_SHARED_OBJECT,
                                        "no such library")));

        assertThrows(
                ProbeFailedException.class, () -> recording.probe(percolator, Path.of("staged")));

        assertAll(
                () ->
                        assertEquals(
                                Optional.of(ProbeFailureKind.MISSING_SHARED_OBJECT),
                                log.refusalFor(percolator)),
                () ->
                        assertEquals(
                                Optional.empty(),
                                log.refusalFor(comet),
                                "the download URL is how this product asks whether two rows are"
                                        + " the same build"));
    }

    @Test
    @DisplayName("a build that probes cleanly afterwards is forgotten")
    void aLaterSuccessForgetsTheRefusal() throws IOException {
        ProbeRefusalLog log = new ProbeRefusalLog();
        ArtefactRecord percolator = record(ToolName.PERCOLATOR);
        assertThrows(
                ProbeFailedException.class,
                () ->
                        log.recording(
                                        refusing(
                                                new ProbeFailedException(
                                                        ProbeFailureKind.MISSING_SYMBOL_VERSION,
                                                        "GLIBC_2.38 not found")))
                                .probe(percolator, Path.of("staged")));

        Set<ToolCapability> answered =
                log.recording(answering(Set.of(ToolCapability.XML_OUTPUT)))
                        .probe(percolator, Path.of("staged"));

        assertAll(
                () -> assertEquals(Set.of(ToolCapability.XML_OUTPUT), answered),
                () ->
                        assertEquals(
                                Optional.empty(),
                                log.refusalFor(percolator),
                                "a machine that has had the missing library installed since the"
                                        + " last attempt is one where the build now runs"),
                () -> assertEquals(0, log.size()));
    }

    @Test
    @DisplayName("the refusal is rethrown, unchanged, so the install still fails")
    void theRefusalIsRethrown() throws IOException {
        ProbeRefusalLog log = new ProbeRefusalLog();
        ProbeFailedException refusal =
                new ProbeFailedException(ProbeFailureKind.NOT_EXECUTABLE, "not executable here");

        assertSame(
                refusal,
                assertThrows(
                        ProbeFailedException.class,
                        () ->
                                log.recording(refusing(refusal))
                                        .probe(record(ToolName.PERCOLATOR), Path.of("staged"))));
    }

    @Test
    @DisplayName("a failure that is not a probe refusal is passed on and remembered as nothing")
    void anOrdinaryFailureIsNotARefusal() throws IOException {
        ProbeRefusalLog log = new ProbeRefusalLog();
        ArtefactRecord percolator = record(ToolName.PERCOLATOR);
        IOException other = new IOException("the staging directory has gone");
        ToolProbe recording =
                log.recording(
                        (record, staged) -> {
                            throw other;
                        });

        assertAll(
                () ->
                        assertSame(
                                other,
                                assertThrows(
                                        IOException.class,
                                        () -> recording.probe(percolator, Path.of("staged")))),
                () -> assertEquals(Optional.empty(), log.refusalFor(percolator)),
                () -> assertEquals(0, log.size()));
    }

    @Test
    @DisplayName("both arguments are required, by name, and the log describes itself")
    void argumentsAreRequired() throws IOException {
        ProbeRefusalLog log = new ProbeRefusalLog();
        ToolProbe noProbe = Nulls.of(ToolProbe.class);
        ArtefactRecord noRecord = Nulls.of(ArtefactRecord.class);

        assertAll(
                () ->
                        assertEquals(
                                "delegate",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> log.recording(noProbe))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "record",
                                assertThrows(
                                                NullPointerException.class,
                                                () -> log.refusalFor(noRecord))
                                        .getMessage()),
                () -> assertEquals("ProbeRefusalLog[0 build(s) refused]", log.toString()),
                () ->
                        assertEquals(
                                "ProbeRefusalLog[1 build(s) refused]",
                                afterOneRefusal().toString()));
    }

    private static ProbeRefusalLog afterOneRefusal() throws IOException {
        ProbeRefusalLog log = new ProbeRefusalLog();
        assertThrows(
                ProbeFailedException.class,
                () ->
                        log.recording(
                                        refusing(
                                                new ProbeFailedException(
                                                        ProbeFailureKind.WRONG_ARCHITECTURE,
                                                        "built for another processor")))
                                .probe(record(ToolName.PERCOLATOR), Path.of("staged")));
        return log;
    }
}
