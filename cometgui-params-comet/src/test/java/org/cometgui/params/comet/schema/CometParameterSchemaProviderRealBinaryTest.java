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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.tools.ToolCapability;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.params.comet.fixtures.CometFixtures;
import org.cometgui.params.comet.fixtures.CometManifest;
import org.cometgui.params.comet.fixtures.UpstreamMirror;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The provider against the REAL pinned Comet 2026.02.2 binary, run through the one process
 * launcher, {@link ProcessService}, wrapped only to record the command it was given. The binary is
 * staged from the gitignored mirror and checked against the manifest's SHA-256 first; a missing or
 * different binary fails, never skips.
 */
@EnabledOnOs(
        value = OS.LINUX,
        disabledReason =
                "only the linux/x86-64 Comet binary has ever been executed in this project")
class CometParameterSchemaProviderRealBinaryTest {

    private Path scratch;

    @BeforeEach
    void createScratch(@TempDir Path directory) {
        scratch = directory;
    }

    /** Records each command, then hands it to the real process service. */
    private static final class Recording implements ProcessRunner {

        private final List<ToolCommand> commands = new ArrayList<>();
        private final ProcessService service = new ProcessService(Clock.systemUTC());

        List<ToolCommand> commands() {
            return commands;
        }

        @Override
        public RunningProcess start(ToolCommand command, ProcessListener listener)
                throws IOException {
            commands.add(command);
            return service.start(command, listener);
        }
    }

    private Path stagedBinary() throws IOException {
        CometManifest.Row row =
                CometManifest.cometRows(CometManifest.repositoryManifest()).stream()
                        .filter(r -> r.version().equals(CometFixtures.COMET_2026_02_2))
                        .filter(CometManifest.Row::isLinuxX8664)
                        .findFirst()
                        .orElseThrow(
                                () -> new AssertionError("no linux/x86-64 Comet 2026.02.2 row"));
        return UpstreamMirror.stage(
                        UpstreamMirror.repositoryRoot(),
                        row,
                        scratch.resolve("bin").resolve("comet"))
                .toAbsolutePath();
    }

    private CometParameterSchema run(Recording runner, Path binary, Set<ToolCapability> caps)
            throws IOException, CometSchemaException {
        Path workspaces = Files.createDirectory(scratch.resolve("workspaces-" + caps.size()));
        CometParameterSchema schema =
                new CometParameterSchemaProvider(
                                runner,
                                MetadataLoader.loadBundled(),
                                Duration.ofSeconds(60),
                                workspaces)
                        .schemaFor(
                                new CometToolIdentity(
                                        binary, ToolVersion.parse("2026.02.2"), caps));
        try (var left = Files.list(workspaces)) {
            assertEquals(List.of(), left.toList(), "the working directory was not removed");
        }
        return schema;
    }

    @Test
    @DisplayName("-q on the real binary: argv [binary, -q], 118 parameters, no drift")
    void theRealBinaryAnswersQ() throws IOException, CometSchemaException {
        Path binary = stagedBinary();
        Recording runner = new Recording();
        CometParameterSchema schema =
                run(runner, binary, Set.of(ToolCapability.COMPLETE_PARAMS_QUERY));
        assertEquals(1, runner.commands().size());
        assertEquals(List.of(binary.toString(), "-q"), runner.commands().get(0).argv());
        assertEquals(DiscoveryMode.COMPLETE, schema.mode());
        assertEquals(118, schema.discovered().names().size());
        assertTrue(schema.drift().isClean(), schema.drift()::describe);
        assertEquals("2026.02 rev. 2 (6edec91)", schema.discovered().marker().text());
        String fixture =
                new String(
                        CometFixtures.bytes(
                                CometFixtures.COMET_2026_02_2,
                                CometFixtures.LINUX_X86_64,
                                CometFixtures.Mode.COMPLETE),
                        StandardCharsets.UTF_8);
        assertEquals(
                SchemaDiscovery.discover(fixture, DiscoveryMode.COMPLETE), schema.discovered());
    }

    @Test
    @DisplayName("-p on the real binary when -q is not claimed: argv [binary, -p], PARTIAL, 96")
    void theRealBinaryAnswersP() throws IOException, CometSchemaException {
        Path binary = stagedBinary();
        Recording runner = new Recording();
        CometParameterSchema schema = run(runner, binary, Set.of());
        assertEquals(List.of(binary.toString(), "-p"), runner.commands().get(0).argv());
        assertEquals(DiscoveryMode.PARTIAL_DISCOVERY, schema.mode());
        assertEquals(96, schema.discovered().names().size());
        assertTrue(schema.drift().isClean(), schema.drift()::describe);
        assertFalse(schema.discovered().names().contains("variable_mod06"));
    }
}
