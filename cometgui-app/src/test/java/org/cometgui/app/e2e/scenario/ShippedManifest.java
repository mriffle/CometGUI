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

package org.cometgui.app.e2e.scenario;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.cometgui.app.testing.TestJson;

/**
 * The shipped tool manifest, {@code manifests/tools.json}, read by the scenario's own code (P14-7):
 * {@link TestJson}, which shares nothing with the product's manifest reader, over the file in the
 * repository rather than the copy on the product's class path.
 *
 * <p>It answers what the scenario must not take from the product: which Percolator the product
 * should resolve to on this platform (canonical step 9 -- "the selection was resolved from the
 * manifest rather than hard-coded"), how many tool builds the Tool Manager should list, and the
 * URL, size and checksums each artefact the installer fetches must have.
 *
 * @param artefacts every artefact record, in file order
 */
public record ShippedManifest(List<Artefact> artefacts) {

    /** The capability a Percolator build must claim to write Percolator XML. */
    public static final String XML_OUTPUT = "XML_OUTPUT";

    /**
     * One file the installer fetches beside an artefact.
     *
     * @param url where it is fetched from
     * @param sizeBytes how large upstream says it is
     * @param members each extracted member's installed path (relative to the install) and SHA-256
     */
    public record Companion(String url, long sizeBytes, Map<String, String> members) {

        /** The members, copied. */
        public Companion {
            members = Map.copyOf(members);
        }
    }

    /**
     * One capability an artefact record claims, and the evidence it names.
     *
     * @param capability the capability's identifier, for example {@code XML_OUTPUT}
     * @param evidence the evidence's identifier, for example {@code observed-by-execution}
     */
    public record Claim(String capability, String evidence) {}

    /**
     * One artefact record.
     *
     * @param tool the tool, for example {@code percolator}
     * @param version the version, for example {@code 3.07.1}
     * @param os the operating system, for example {@code linux}
     * @param arch the architecture, for example {@code x86-64}
     * @param kind how it is packaged, for example {@code ZIP}
     * @param url where it is fetched from
     * @param sizeBytes how large upstream says it is
     * @param sha256 the artefact's SHA-256
     * @param installedSha256 the SHA-256 the installed executable or JAR must have: the artefact's
     *     own for a bare executable or a JAR, the member's for an archive whose record pins one,
     *     empty for an archive whose record does not
     * @param companions what is fetched beside it
     * @param claims the capabilities the record claims
     */
    public record Artefact(
            String tool,
            String version,
            String os,
            String arch,
            String kind,
            String url,
            long sizeBytes,
            String sha256,
            String installedSha256,
            List<Companion> companions,
            List<Claim> claims) {

        /** The lists, copied. */
        public Artefact {
            companions = List.copyOf(companions);
            claims = List.copyOf(claims);
        }

        /**
         * Every byte installing this artefact transfers: the artefact and its companions.
         *
         * @return the sum of the sizes
         */
        public long transferBytes() {
            long sum = sizeBytes;
            for (Companion companion : companions) {
                sum += companion.sizeBytes();
            }
            return sum;
        }

        /**
         * Whether the record claims a capability, with whatever evidence.
         *
         * @param capability the capability's identifier
         * @return {@code true} if it is claimed
         */
        public boolean claims(String capability) {
            return claims.stream().anyMatch(claim -> claim.capability().equals(capability));
        }
    }

    /** The records, copied. */
    public ShippedManifest {
        artefacts = List.copyOf(artefacts);
    }

    /**
     * Reads the manifest.
     *
     * @param file {@code manifests/tools.json}
     * @return its records
     * @throws IOException if it cannot be read
     * @throws AssertionError if it is not the manifest's shape
     */
    public static ShippedManifest read(Path file) throws IOException {
        Map<String, Object> root = TestJson.object(Files.readString(file, StandardCharsets.UTF_8));
        if (!new BigDecimal(1).equals(root.get("schemaVersion"))) {
            throw new AssertionError(
                    file + " is not schema version 1: " + root.get("schemaVersion"));
        }
        List<Artefact> read = new ArrayList<>();
        for (Object entry : list(root.get("artefacts"), "artefacts")) {
            read.add(artefact(map(entry, "an artefact")));
        }
        return new ShippedManifest(read);
    }

    /**
     * The platform this JVM runs on, as the manifest spells it, by this class's own rule.
     *
     * @return for example {@code linux/x86-64}
     * @throws AssertionError on a platform the manifest has no spelling for
     */
    public static String hostPlatform() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String os;
        if (name.startsWith("linux")) {
            os = "linux";
        } else if (name.contains("mac") || name.contains("darwin")) {
            os = "macos";
        } else if (name.startsWith("windows")) {
            os = "windows";
        } else {
            throw new AssertionError("no manifest spelling for the operating system " + name);
        }
        String arch =
                switch (System.getProperty("os.arch", "")) {
                    case "amd64", "x86_64" -> "x86-64";
                    case "aarch64", "arm64" -> "aarch64";
                    default ->
                            throw new AssertionError(
                                    "no manifest spelling for the architecture "
                                            + System.getProperty("os.arch"));
                };
        return os + "/" + arch;
    }

    /**
     * How many tool builds the Tool Manager lists on any host: one per tool and version the
     * manifest names on some platform, since a build upstream does not publish for this platform is
     * listed as such rather than hidden.
     *
     * @return the number of distinct tool-and-version pairs
     */
    public int toolBuilds() {
        Set<String> builds = new LinkedHashSet<>();
        for (Artefact artefact : artefacts) {
            builds.add(artefact.tool() + " " + artefact.version());
        }
        return builds.size();
    }

    /**
     * How many versions of one tool the manifest names on some platform.
     *
     * @param tool the tool
     * @return the number of distinct versions
     */
    public int versionsOf(String tool) {
        return (int)
                artefacts.stream()
                        .filter(artefact -> artefact.tool().equals(tool))
                        .map(Artefact::version)
                        .distinct()
                        .count();
    }

    /**
     * The record for one tool, version and platform.
     *
     * @param tool the tool
     * @param version the version
     * @param platform {@code os/arch}, as {@link #hostPlatform()} gives it
     * @return the record
     * @throws AssertionError if there is none, or more than one
     */
    public Artefact artefact(String tool, String version, String platform) {
        List<Artefact> found =
                artefacts.stream()
                        .filter(artefact -> artefact.tool().equals(tool))
                        .filter(artefact -> artefact.version().equals(version))
                        .filter(
                                artefact ->
                                        (artefact.os() + "/" + artefact.arch()).equals(platform))
                        .toList();
        if (found.size() != 1) {
            throw new AssertionError(
                    found.size() + " records for " + tool + " " + version + " on " + platform);
        }
        return found.get(0);
    }

    /**
     * The newest Percolator whose record for a platform claims {@value #XML_OUTPUT}: what the
     * product's default for a Limelight-enabled run must be there, worked out from the records' own
     * fields.
     *
     * @param platform {@code os/arch}
     * @return the record
     * @throws AssertionError if no Percolator record for the platform claims it
     */
    public Artefact newestXmlCapablePercolator(String platform) {
        return artefacts.stream()
                .filter(artefact -> artefact.tool().equals("percolator"))
                .filter(artefact -> (artefact.os() + "/" + artefact.arch()).equals(platform))
                .filter(artefact -> artefact.claims(XML_OUTPUT))
                .max(Comparator.comparing(Artefact::version, ShippedManifest::compareVersions))
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "no Percolator record for "
                                                + platform
                                                + " claims "
                                                + XML_OUTPUT));
    }

    /**
     * Compares two dotted versions numerically, part by part: {@code 3.07.1} is newer than {@code
     * 3.06.5} and older than {@code 3.09}.
     *
     * @param left one version
     * @param right the other
     * @return negative, zero or positive as {@code left} is older, the same or newer
     */
    static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int part = 0; part < Math.max(a.length, b.length); part++) {
            int x = part < a.length ? Integer.parseInt(a[part]) : 0;
            int y = part < b.length ? Integer.parseInt(b[part]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static Artefact artefact(Map<String, Object> record) {
        String kind = text(record, "kind");
        String sha256 = text(record, "sha256");
        String installed = sha256;
        if ("ZIP".equals(kind)) {
            // an archive's record pins its member only where the member is one executable
            installed = record.get("memberSha256") instanceof String member ? member : "";
        }
        List<Companion> companions = new ArrayList<>();
        for (Object entry : list(record.get("companions"), "companions")) {
            Map<String, Object> companion = map(entry, "a companion");
            Map<String, String> members = new LinkedHashMap<>();
            Object listed = companion.get("members");
            if (listed != null) {
                for (Object member : list(listed, "members")) {
                    Map<String, Object> fields = map(member, "a member");
                    members.put(text(fields, "installedPath"), text(fields, "sha256"));
                }
            }
            companions.add(
                    new Companion(text(companion, "url"), number(companion, "sizeBytes"), members));
        }
        List<Claim> claims = new ArrayList<>();
        for (Object entry : list(record.get("capabilities"), "capabilities")) {
            Map<String, Object> claim = map(entry, "a capability");
            claims.add(new Claim(text(claim, "capability"), text(claim, "evidence")));
        }
        return new Artefact(
                text(record, "tool"),
                text(record, "version"),
                text(record, "os"),
                text(record, "arch"),
                kind,
                text(record, "url"),
                number(record, "sizeBytes"),
                sha256,
                installed,
                companions,
                claims);
    }

    private static String text(Map<String, Object> record, String name) {
        Object value = record.get(name);
        if (!(value instanceof String string)) {
            throw new AssertionError("no text " + name + " in " + record.keySet());
        }
        return string;
    }

    private static long number(Map<String, Object> record, String name) {
        Object value = record.get(name);
        if (!(value instanceof BigDecimal number)) {
            throw new AssertionError("no number " + name + " in " + record.keySet());
        }
        return number.longValueExact();
    }

    private static List<?> list(Object value, String what) {
        if (!(value instanceof List<?> listed)) {
            throw new AssertionError(what + " is not a JSON array: " + value);
        }
        return listed;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value, String what) {
        if (!(value instanceof Map<?, ?>)) {
            throw new AssertionError(what + " is not a JSON object: " + value);
        }
        return (Map<String, Object>) Objects.requireNonNull(value);
    }
}
