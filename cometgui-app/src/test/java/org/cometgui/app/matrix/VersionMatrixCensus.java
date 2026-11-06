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

package org.cometgui.app.matrix;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cometgui.app.config.ToolManagerWiring;
import org.cometgui.domain.tools.HostArchitecture;
import org.cometgui.domain.tools.HostOperatingSystem;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.NoManagedBuild;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.registry.ArtefactSelection;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * The product's own answer to "what is offered where", for every platform it names: the version
 * matrix's row list is derived from this and from {@code manifests/tools.json} read a second,
 * independent way ({@code scripts/matrix/matrix.py}), never from a typed list.
 *
 * <p>Needs no network and no spectra. Written to {@code <report>/census.json}:
 *
 * <ul>
 *   <li>every record the product's own manifest reader returns (the shipped manifest on the class
 *       path, as the application reads it);
 *   <li>for every platform the product names -- each {@link HostOperatingSystem} with each {@link
 *       HostArchitecture} -- and every release of every tool, the records {@link
 *       ArtefactManifest#select} would offer there, and whether that host would run them translated
 *       (Rosetta 2, {@code D-004});
 *   <li>for every platform, what the product's Tool Manager lists over an empty cache, and the
 *       tools it says have no managed build there at all ({@code D-011}).
 * </ul>
 */
@EnabledIfSystemProperty(
        named = MatrixSupport.ENABLE,
        matches = "true",
        disabledReason = MatrixSupport.DISABLED)
class VersionMatrixCensus {

    @TempDir private Path temporary;

    @Test
    @DisplayName("the product's offers on every platform it names, and every record it read")
    void theProductsViewOfEveryPlatform() throws IOException {
        ArtefactManifest manifest = ArtefactManifestReader.readFromClasspath();
        List<ArtefactRecord> records = manifest.artefacts();
        assertFalse(records.isEmpty(), "the shipped manifest has no record");

        List<Object> recordRows = new ArrayList<>();
        Map<ToolName, Set<ToolVersion>> releases = new LinkedHashMap<>();
        for (ArtefactRecord record : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tool", record.tool().id());
            row.put("version", record.version().text());
            row.put("platform", record.platform().id());
            row.put("kind", record.kind().name());
            row.put("url", record.url().toString());
            row.put("sha256", record.hashes().sha256());
            row.put(
                    "capabilities",
                    record.capabilities().stream()
                            .map(declared -> declared.capability().id())
                            .sorted()
                            .toList());
            row.put("advisories", record.advisories().stream().map(ToolAdvisory::id).toList());
            recordRows.add(row);
            releases.computeIfAbsent(record.tool(), tool -> new LinkedHashSet<>())
                    .add(record.version());
        }

        List<Object> platforms = new ArrayList<>();
        List<Object> selections = new ArrayList<>();
        Map<String, Object> managers = new LinkedHashMap<>();
        HostPlatform here = MatrixSupport.host();
        for (HostOperatingSystem system : HostOperatingSystem.values()) {
            for (HostArchitecture architecture : HostArchitecture.values()) {
                HostPlatform platform = new HostPlatform(system, architecture);
                platforms.add(platform.id());
                for (Map.Entry<ToolName, Set<ToolVersion>> tool : releases.entrySet()) {
                    for (ToolVersion version : tool.getValue()) {
                        List<Object> chosen = new ArrayList<>();
                        for (ArtefactSelection selection :
                                manifest.select(platform, tool.getKey(), version)) {
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("platform", selection.artefact().platform().id());
                            entry.put("translated", selection.isTranslated());
                            chosen.add(entry);
                        }
                        Map<String, Object> selected = new LinkedHashMap<>();
                        selected.put("platform", platform.id());
                        selected.put("tool", tool.getKey().id());
                        selected.put("version", version.text());
                        selected.put("records", chosen);
                        selections.add(selected);
                    }
                }
                managers.put(platform.id(), toolManagerView(platform, platform.equals(here)));
            }
        }

        Map<String, Object> census = new LinkedHashMap<>();
        census.put("host", here.id());
        census.put("manifestRecords", recordRows);
        census.put("platforms", platforms);
        census.put("selections", selections);
        census.put("toolManager", managers);
        Path file = MatrixSupport.report().resolve("census.json");
        Files.writeString(file, MatrixSupport.Json.write(census) + "\n", StandardCharsets.UTF_8);
    }

    /** The Tool Manager's list for one platform over an empty cache: nothing installs or runs. */
    private Map<String, Object> toolManagerView(HostPlatform platform, boolean isHost)
            throws IOException {
        Path cache = Files.createDirectories(temporary.resolve("cache-" + platform.id()));
        ToolManager manager =
                ToolManagerWiring.toolManager(
                        platform,
                        isHost ? MatrixSupport.runtimeVersions() : HostRuntimeVersions.unknown(),
                        cache,
                        new ProcessService(Clock.systemUTC()),
                        Clock.systemUTC(),
                        ToolManagerWiring.installThreads());
        List<Object> offers = new ArrayList<>();
        for (ToolOffer offer : manager.offers()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("tool", offer.tool().id());
            entry.put("version", offer.version().text());
            entry.put("origin", offer.origin().name());
            entry.put("state", offer.state().name());
            offers.add(entry);
        }
        List<Object> none = new ArrayList<>();
        for (NoManagedBuild missing : manager.noManagedBuild()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("tool", missing.tool().id());
            entry.put("localRegistration", missing.localRegistration());
            none.add(entry);
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("offers", offers);
        view.put("noManagedBuild", none);
        return view;
    }
}
