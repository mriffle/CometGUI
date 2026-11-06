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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.cometgui.app.config.FfmGlibcVersionSource;
import org.cometgui.app.config.ToolManagerWiring;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.probe.HostRuntimeVersions;
import org.junit.jupiter.api.function.Executable;

/**
 * What the version matrix's three opt-in classes share (Phase 15 unit 6, gate item 1): the opt-in
 * and its inputs, the host, the product's own Tool Manager over a fresh cache and the real upstream
 * transport, and the one way a matrix row is recorded.
 *
 * <h2>Opt-in, never part of a plain build (P15-5)</h2>
 *
 * <p>The classes are named {@code VersionMatrix*} rather than {@code *Test}, so surefire's default
 * includes never select them, and each is also disabled unless {@value #ENABLE}{@code =true}. They
 * are run by {@code scripts/matrix/run-matrix.sh}, which names them with {@code -Dtest}, gives them
 * an empty report directory ({@value #REPORT}) and grades what they write there.
 *
 * <h2>A row is a record, written whether it passed or not</h2>
 *
 * <p>Every combination a class exercises is one {@link Row}: an identifier the matrix derives from
 * the manifest, the values observed, and a list of named checks, each passed or failed with its own
 * words. The row is written to {@code <report>/rows/<id>.json} when it finishes, and then, if any
 * check failed, the test fails naming every failed check -- so the surefire report and the matrix
 * report say the same thing, and a row that never ran leaves no file, which the matrix reads as a
 * combination with no verdict (red), never as a pass.
 */
final class MatrixSupport {

    /** The system property that opts in. */
    static final String ENABLE = "cometgui.matrix";

    /** The system property naming the (empty) directory the rows are written to. */
    static final String REPORT = "cometgui.matrix.report";

    /** The system property naming the fixture directory; set from COMETGUI_MATRIX_FIXTURE. */
    static final String FIXTURE = "cometgui.matrix.fixture";

    /**
     * The system property naming a registered local Percolator 3.09 wrapper, optional: Linux has no
     * managed 3.09 ({@code D-003}), so this is "a local binary, not a managed offer".
     */
    static final String LOCAL_309 = "cometgui.matrix.local309";

    /** The reason a class is skipped without the opt-in. */
    static final String DISABLED =
            "version matrix (Phase 15 unit 6): opt-in, run by scripts/matrix/run-matrix.sh with"
                    + " -Dcometgui.matrix=true; it installs every offered tool from its upstream"
                    + " URL";

    /** How long one install may take. A bound, never a delay. */
    static final long INSTALL_MINUTES = 15;

    /**
     * The upstream hosts the installs download from: GitHub's release pages and the storage hosts
     * they redirect to (P15-5b). Nothing else passes the module's loopback guard.
     */
    static final String UPSTREAM_HOSTS = "github.com|*.githubusercontent.com";

    private static String guardedNonProxyHosts;

    private MatrixSupport() {}

    /**
     * The machine this runs on, as the product names it.
     *
     * @return the host platform
     */
    static HostPlatform host() {
        return HostPlatform.of(System.getProperty("os.name", ""), System.getProperty("os.arch", ""))
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "the product names no platform for os.name="
                                                + System.getProperty("os.name")
                                                + " os.arch="
                                                + System.getProperty("os.arch")));
    }

    /**
     * The host's C and C++ runtime versions, detected the way the application detects them.
     *
     * @return the versions
     */
    static HostRuntimeVersions runtimeVersions() {
        return HostRuntimeVersions.detect(new FfmGlibcVersionSource());
    }

    /**
     * The report directory the runner handed in, which must exist.
     *
     * @return the directory for rows
     * @throws IOException if it cannot be created
     */
    static Path rows() throws IOException {
        return Files.createDirectories(report().resolve("rows"));
    }

    /**
     * The report directory the runner handed in.
     *
     * @return the directory, absolute
     * @throws IOException if it cannot be created
     */
    static Path report() throws IOException {
        String report = System.getProperty(REPORT, "");
        assertTrue(
                !report.isBlank(),
                "the version matrix needs -D" + REPORT + "=DIR (scripts/matrix/run-matrix.sh)");
        return Files.createDirectories(Path.of(report).toAbsolutePath());
    }

    /**
     * The repository root.
     *
     * @return the root
     */
    static Path root() {
        return RealSearch.repositoryRoot();
    }

    /**
     * Lets the opted-in downloads through the module's loopback guard, for GitHub's release hosts
     * alone (the pattern {@code UpstreamInstallUiTest} established).
     */
    static void openGuardForUpstream() {
        guardedNonProxyHosts = System.getProperty("http.nonProxyHosts");
        if (guardedNonProxyHosts != null) {
            System.setProperty("http.nonProxyHosts", guardedNonProxyHosts + "|" + UPSTREAM_HOSTS);
        }
    }

    /** Puts the guard back exactly as it was. */
    static void restoreGuard() {
        if (guardedNonProxyHosts != null) {
            System.setProperty("http.nonProxyHosts", guardedNonProxyHosts);
        }
    }

    /**
     * The product's whole Tool Manager for this host over an empty cache, fetching through the real
     * {@code HttpDownloader} from each artefact's pinned upstream URL.
     *
     * @param cache the cache root, which must be empty
     * @param processes the process seam
     * @return the manager
     * @throws IOException if it cannot be built, or the cache is not empty
     */
    static ToolManager toolManager(Path cache, ProcessRunner processes) throws IOException {
        Files.createDirectories(cache);
        try (var listed = Files.list(cache)) {
            assertEquals(List.of(), listed.toList(), "the tool cache must start empty");
        }
        return ToolManagerWiring.toolManager(
                host(),
                runtimeVersions(),
                cache,
                processes,
                Clock.systemUTC(),
                ToolManagerWiring.installThreads());
    }

    /**
     * Installs one offer through the manager and returns the installed offer.
     *
     * @param manager the manager
     * @param tool the tool
     * @param version the release
     * @return the offer the manager reports afterwards, INSTALLED
     * @throws InterruptedException if interrupted
     */
    static ToolOffer install(ToolManager manager, ToolName tool, ToolVersion version)
            throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        List<InstallPhase> phases = Collections.synchronizedList(new ArrayList<>());
        manager.install(
                tool,
                version,
                progress -> {
                    phases.add(progress.phase());
                    if (progress.phase().isTerminal()) {
                        done.countDown();
                    }
                });
        assertTrue(
                done.await(INSTALL_MINUTES, TimeUnit.MINUTES),
                () -> tool.id() + " " + version.text() + " did not finish installing: " + phases);
        ToolOffer offer = offer(manager, tool, version);
        assertEquals(
                InstallPhase.DONE,
                phases.get(phases.size() - 1),
                () ->
                        tool.id()
                                + " "
                                + version.text()
                                + " phases "
                                + phases
                                + "; reason: "
                                + offer.failureReason().orElse("(none)"));
        assertEquals(ToolInstallState.INSTALLED, offer.state(), offer::toString);
        return offer;
    }

    /**
     * The manager's managed offer of one tool release.
     *
     * @param manager the manager
     * @param tool the tool
     * @param version the release
     * @return the offer
     */
    static ToolOffer offer(ToolManager manager, ToolName tool, ToolVersion version) {
        return manager.offers().stream()
                .filter(offer -> offer.tool() == tool && offer.version().equals(version))
                .filter(offer -> offer.origin() == ToolOrigin.MANAGED)
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "the Tool Manager offers no managed "
                                                + tool.id()
                                                + " "
                                                + version.text()));
    }

    /**
     * The capability identifiers an offer carries, sorted.
     *
     * @param offer the offer
     * @return the identifiers
     */
    static List<String> capabilityIds(ToolOffer offer) {
        return offer.capabilities().stream()
                .map(DeclaredCapability::capability)
                .map(capability -> capability.id())
                .sorted()
                .toList();
    }

    /**
     * A SHA-256, this test's own.
     *
     * @param file the file
     * @return lower-case hexadecimal
     * @throws IOException if unreadable
     */
    static String sha256(Path file) throws IOException {
        return RealSearch.sha256(file);
    }

    /** One matrix row: observed values and named checks, written whether it passed or not. */
    static final class Row {

        private final String id;

        private final Map<String, Object> observed = new LinkedHashMap<>();

        private final List<Map<String, Object>> checks = new ArrayList<>();

        private final List<String> failures = new ArrayList<>();

        private Row(String id) {
            this.id = id;
        }

        /**
         * Starts a row.
         *
         * @param id the identifier the matrix derives for this combination
         * @return the row
         */
        static Row start(String id) {
            return new Row(id);
        }

        /**
         * Records an observed value.
         *
         * @param name its name
         * @param value a string, number, boolean, list or map of those
         * @return this row
         */
        Row observe(String name, Object value) {
            observed.put(name, value);
            return this;
        }

        /**
         * Runs one named check and records it; never throws.
         *
         * @param name what it checks
         * @param check the assertions
         * @return whether it passed
         */
        boolean check(String name, Executable check) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            try {
                check.execute();
                entry.put("passed", true);
            } catch (Exception | AssertionError failed) {
                fail(entry, name, failed);
            } catch (Error fatal) {
                throw fatal;
            } catch (Throwable other) {
                fail(entry, name, other);
            }
            checks.add(entry);
            return failures.isEmpty();
        }

        private void fail(Map<String, Object> entry, String name, Throwable failed) {
            String words = failed.getClass().getSimpleName() + ": " + failed.getMessage();
            entry.put("passed", false);
            entry.put("detail", words);
            failures.add(name + " -- " + words);
        }

        /**
         * Writes the row and fails if any check did.
         *
         * @throws IOException if it cannot be written
         */
        void finish() throws IOException {
            assertTrue(!checks.isEmpty(), id + " ran no check");
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("id", id);
            document.put("passed", failures.isEmpty());
            document.put("checks", checks);
            document.put("observed", observed);
            Path file = rows().resolve(fileName(id));
            Files.writeString(file, Json.write(document) + "\n", StandardCharsets.UTF_8);
            if (!failures.isEmpty()) {
                throw new AssertionError(
                        id
                                + ": "
                                + failures.size()
                                + " check(s) failed:\n  "
                                + String.join("\n  ", failures));
            }
        }

        static String fileName(String id) {
            return id.replaceAll("[^A-Za-z0-9._-]", "_") + ".json";
        }
    }

    /** A minimal JSON writer for the rows: strings, numbers, booleans, null, lists and maps. */
    static final class Json {

        private Json() {}

        static String write(Object value) {
            StringBuilder out = new StringBuilder();
            write(out, value, "");
            return out.toString();
        }

        private static void write(StringBuilder out, Object value, String indent) {
            if (value == null) {
                out.append("null");
            } else if (value instanceof String text) {
                string(out, text);
            } else if (value instanceof Boolean
                    || value instanceof Integer
                    || value instanceof Long) {
                out.append(value);
            } else if (value instanceof Number number) {
                out.append(String.format(Locale.ROOT, "%s", number));
            } else if (value instanceof Map<?, ?> map) {
                if (map.isEmpty()) {
                    out.append("{}");
                    return;
                }
                out.append("{\n");
                String inner = indent + "  ";
                int index = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    out.append(inner);
                    string(out, String.valueOf(entry.getKey()));
                    out.append(": ");
                    write(out, entry.getValue(), inner);
                    out.append(++index < map.size() ? ",\n" : "\n");
                }
                out.append(indent).append('}');
            } else if (value instanceof List<?> list) {
                if (list.isEmpty()) {
                    out.append("[]");
                    return;
                }
                out.append("[\n");
                String inner = indent + "  ";
                for (int index = 0; index < list.size(); index++) {
                    out.append(inner);
                    write(out, list.get(index), inner);
                    out.append(index + 1 < list.size() ? ",\n" : "\n");
                }
                out.append(indent).append(']');
            } else {
                string(out, String.valueOf(value));
            }
        }

        private static void string(StringBuilder out, String text) {
            out.append('"');
            for (int index = 0; index < text.length(); index++) {
                char c = text.charAt(index);
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                    }
                }
            }
            out.append('"');
        }
    }
}
