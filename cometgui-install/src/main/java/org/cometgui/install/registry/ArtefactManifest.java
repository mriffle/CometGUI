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

package org.cometgui.install.registry;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.ArtefactExecutability;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;

/**
 * The whole managed artefact manifest: every artefact this build of CometGUI knows how to install,
 * and the queries over them.
 *
 * <p><strong>This is the only place a tool location comes from.</strong> The specification's rule
 * is that tool locations come from a versioned, release-bundled manifest and not from ad hoc URL
 * construction spread through the code, and the way that rule is kept is that there is nowhere else
 * to get one.
 *
 * <h2>What is absent is a statement too</h2>
 *
 * <p>Percolator 3.09 has <strong>no Linux row at all</strong>, and that is deliberate rather than
 * an oversight. {@code rel-3-09} publishes no Linux portable archive; its {@code .deb} needs {@code
 * GLIBC_2.38} <em>and</em> {@code libboost_filesystem.so.1.83.0}, which it does not ship, and both
 * failures were reproduced on this project's own Debian 12 host. {@code R-PERC-12} says plainly
 * that a version may legitimately be absent on a platform and that "absent is honest; a fabricated
 * entry is not". Adding a Linux 3.09 row to make the matrix look complete would be offering a
 * download that ends in a loader failure.
 *
 * @param schemaVersion the manifest format version; see {@link #SCHEMA_VERSION}
 * @param artefacts every artefact, in manifest order, with no tool, version and platform triple
 *     appearing twice
 */
public record ArtefactManifest(int schemaVersion, List<ArtefactRecord> artefacts) {

    /**
     * The manifest format version this build reads and writes.
     *
     * <p>One, the first published format. The reader refuses a higher version outright rather than
     * reading the fields it recognises -- a newer writer may have changed what a field means rather
     * than only added one -- and refuses a lower one until a migration exists.
     */
    public static final int SCHEMA_VERSION = 1;

    /*
     * Newest version first, because that is the order the Tool Manager shows versions in and the
     * order R-PERC-02's "latest compatible" walks.  Then native before translated, so that a host
     * which can run an artefact directly is never offered the Rosetta 2 route first.
     *
     * THERE IS DELIBERATELY NO THIRD KEY, and the reason is worth writing down because a third one
     * looks like an improvement.  Two selections cannot tie on version and translation: at most one
     * record exists per tool, version and platform, and Rosetta 2 is the only translation there is,
     * so the most a host can be offered of one version is its own platform's build and -- on Apple
     * silicon -- the x86-64 macOS build, which differ on the second key.  A third key would be a
     * comparison no input can reach: a branch that can never be observed to be wrong, which is
     * exactly the shape this project has been bitten by.  The sort is stable, so anything that did
     * tie would keep manifest order.
     */
    private static final Comparator<ArtefactSelection> OFFER_ORDER =
            Comparator.comparing(
                            (ArtefactSelection selection) -> selection.artefact().version(),
                            Comparator.reverseOrder())
                    .thenComparing(ArtefactSelection::isTranslated);

    /**
     * Validates the manifest and takes a defensive, immutable copy of the artefact list.
     *
     * @throws NullPointerException if {@code artefacts} is {@code null} or holds a {@code null}
     * @throws IllegalArgumentException if the schema version is not {@link #SCHEMA_VERSION}, if the
     *     manifest holds no artefact at all, or if two records describe the same tool, version and
     *     platform -- naming both records and where they are
     */
    public ArtefactManifest {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "schemaVersion must be "
                            + SCHEMA_VERSION
                            + ", which is the manifest format this build reads, but was: "
                            + schemaVersion);
        }
        artefacts = checkedArtefacts(artefacts);
    }

    private static List<ArtefactRecord> checkedArtefacts(List<ArtefactRecord> artefacts) {
        List<ArtefactRecord> copy = List.copyOf(Objects.requireNonNull(artefacts, "artefacts"));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(
                    "artefacts must name at least one artefact: a manifest with no rows offers"
                            + " nothing and would be indistinguishable from one that failed to"
                            + " load");
        }
        /*
         * THE KEY IS THE NORMALISED VERSION, NOT THE TEXT UPSTREAM WROTE.  ToolVersion.toString()
         * renders 3.09 and 3.09.0 identically because they ARE one version -- that is what
         * ToolVersion.equals says, and it is what select(host, tool, version) matches on.  Keying
         * this check on describe(), which keeps upstream's spelling, would let one release be
         * written two ways in one manifest and then offered twice for a single selection: a
         * duplicate the check exists to catch and would have missed.
         */
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (int index = 0; index < copy.size(); index++) {
            ArtefactRecord record = copy.get(index);
            String key = record.tool().id() + " " + record.version() + " " + record.platform().id();
            Integer first = seen.putIfAbsent(key, index);
            if (first != null) {
                throw new IllegalArgumentException(
                        "artefacts describes "
                                + record.describe()
                                + " twice, at index "
                                + first
                                + " and index "
                                + index
                                + "; two versions that differ only in how they are written --"
                                + " 3.09 and 3.09.0 -- are one version");
            }
        }
        requireOneUrlDescribedOneWay(copy);
        return copy;
    }

    /*
     * ONE URL, ONE SET OF BYTES.  A platform-independent artefact -- PDV's zip, the Limelight
     * converter's JAR -- is one download offered on five platforms, so its URL, size and digests
     * appear five times.  That is duplication, and duplication is how two answers to one question
     * start: someone corrects one row's digest after an upstream re-tag and leaves the other four,
     * and the product then trusts different bytes depending on which platform a user is on.
     *
     * The duplication is kept, because the specification requires an operating system and an
     * architecture in every record, and the risk it creates is converted into a rejection here.
     * Companion downloads are included: the Percolator XSD pair really is fetched from the same
     * .deb by the Linux and the Windows records, and those two rows must agree about it.
     */
    private static void requireOneUrlDescribedOneWay(List<ArtefactRecord> artefacts) {
        Map<URI, String> described = new LinkedHashMap<>();
        for (ArtefactRecord record : artefacts) {
            requireAgreement(
                    described,
                    record.url(),
                    record.sizeBytes(),
                    record.hashes(),
                    record.describe());
            for (ArtefactCompanion companion : record.companions()) {
                requireAgreement(
                        described,
                        companion.url(),
                        companion.sizeBytes(),
                        companion.hashes(),
                        record.describe() + " companion " + companion.id());
            }
        }
    }

    private static void requireAgreement(
            Map<URI, String> described, URI url, long sizeBytes, FileHashes hashes, String where) {
        String bytes = sizeBytes + " bytes, sha256 " + hashes.sha256() + ", md5 " + hashes.md5();
        String previous = described.putIfAbsent(url, bytes + " (" + where + ")");
        if (previous != null && !previous.startsWith(bytes + " (")) {
            throw new IllegalArgumentException(
                    "artefacts describes the same download two different ways: "
                            + url
                            + " is "
                            + bytes
                            + " in "
                            + where
                            + ", and "
                            + previous);
        }
    }

    /**
     * Every artefact, immutable and in manifest order.
     *
     * @return the artefacts, never empty
     */
    @Override
    public List<ArtefactRecord> artefacts() {
        return List.copyOf(artefacts);
    }

    /**
     * Every artefact of one tool that the given host can run.
     *
     * <p>"Can run" is {@link ArtefactExecutability}'s question and not an equality test on the
     * platform, which is what makes the Rosetta 2 case work: on {@code macos-aarch64} a {@code
     * macos-x86-64} Percolator is selectable and is marked as translated. Nothing else translates
     * -- a Linux host is never offered a Windows build, and an x86-64 machine is never offered
     * {@code aarch64} code.
     *
     * <p>The result is ordered newest version first, native before translated, then by platform
     * identifier, and it is a list rather than a set because that order is the answer.
     *
     * @param host the machine in front of the user
     * @param tool the tool being offered
     * @return the artefacts this host can run, possibly empty -- which is the honest answer for
     *     Percolator 3.09 on Linux
     * @throws NullPointerException if either argument is {@code null}
     */
    public List<ArtefactSelection> select(HostPlatform host, ToolName tool) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(tool, "tool");
        List<ArtefactSelection> selected = new ArrayList<>();
        for (ArtefactRecord record : artefacts) {
            if (record.tool() != tool) {
                continue;
            }
            ArtefactExecutability executability = ArtefactExecutability.of(host, record.platform());
            if (executability.isRunnable()) {
                selected.add(new ArtefactSelection(record, executability));
            }
        }
        selected.sort(OFFER_ORDER);
        return List.copyOf(selected);
    }

    /**
     * Every artefact of one tool and one version that the given host can run.
     *
     * @param host the machine in front of the user
     * @param tool the tool being offered
     * @param version the release wanted, compared numerically, so {@code 3.09} and {@code 3.09.0}
     *     are one version
     * @return the artefacts this host can run, possibly empty
     * @throws NullPointerException if any argument is {@code null}
     */
    public List<ArtefactSelection> select(HostPlatform host, ToolName tool, ToolVersion version) {
        Objects.requireNonNull(version, "version");
        List<ArtefactSelection> selected = new ArrayList<>();
        for (ArtefactSelection selection : select(host, tool)) {
            if (selection.artefact().version().equals(version)) {
                selected.add(selection);
            }
        }
        return List.copyOf(selected);
    }
}
