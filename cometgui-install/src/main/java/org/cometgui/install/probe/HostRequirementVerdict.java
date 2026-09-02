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

package org.cometgui.install.probe;

import java.util.Objects;
import java.util.Optional;

/**
 * What the advance check made of one artefact's declared host requirements.
 *
 * <p><strong>Three answers, and the third is not the second.</strong> {@code R-PLAT-02} settles
 * compatibility by executing the binary, so an advance check that cannot decide has to say so:
 * {@link Status#UNDETERMINED} is neither a refusal nor an approval, and {@link ProbeGatedOffers}
 * treats it by letting the probe decide. The invariant this whole package is held to is that an
 * offer is marked not-runnable <em>only</em> when the specific unmet floor can be named -- so
 * {@link Status#UNMET} carries the field, the library, the version required and the version this
 * host has, and the constructor refuses a verdict that claims a refusal without them.
 *
 * @param status what was established
 * @param field which manifest field the verdict is about, absent only for {@link Status#MET}
 * @param objectName the library the floor is about, as the loader names it -- {@code libc.so.6},
 *     {@code libstdc++.so.6}; present only for {@link Status#UNMET}
 * @param requiredVersion the symbol version the artefact declares, with its prefix, as in {@code
 *     GLIBC_2.34}; present only for {@link Status#UNMET}
 * @param availableVersion the version this host provides, with its prefix; present only for {@link
 *     Status#UNMET}, because a floor cannot be shown unmet without knowing what the host has
 */
public record HostRequirementVerdict(
        Status status,
        Optional<String> field,
        Optional<String> objectName,
        Optional<String> requiredVersion,
        Optional<String> availableVersion) {

    /** What an advance check can conclude. */
    public enum Status {

        /** Every floor the artefact declares was checked and is met. */
        MET,

        /** A specific floor is not met, and this verdict names it. */
        UNMET,

        /**
         * A floor was declared that this host could not be measured against. Never a refusal and
         * never an approval: the binary is run and the probe decides.
         */
        UNDETERMINED
    }

    private static final HostRequirementVerdict MET_VERDICT =
            new HostRequirementVerdict(
                    Status.MET,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());

    /**
     * Validates the verdict against the invariant its own status implies.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if the parts do not match the status
     */
    public HostRequirementVerdict {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(objectName, "objectName");
        Objects.requireNonNull(requiredVersion, "requiredVersion");
        Objects.requireNonNull(availableVersion, "availableVersion");
        requireConsistent(status, field, objectName, requiredVersion, availableVersion);
    }

    private static void requireConsistent(
            Status status,
            Optional<String> field,
            Optional<String> objectName,
            Optional<String> requiredVersion,
            Optional<String> availableVersion) {
        if (status == Status.UNMET
                && (field.isEmpty()
                        || objectName.isEmpty()
                        || requiredVersion.isEmpty()
                        || availableVersion.isEmpty())) {
            throw new IllegalArgumentException(
                    "an UNMET verdict must name the field, the library, the required version and"
                            + " the version this host has: absence of information is never a"
                            + " refusal");
        }
        if (status == Status.MET && field.isPresent()) {
            throw new IllegalArgumentException("a MET verdict has no field to name, but named it");
        }
        if (status == Status.UNDETERMINED
                && (field.isEmpty()
                        || objectName.isPresent()
                        || requiredVersion.isPresent()
                        || availableVersion.isPresent())) {
            throw new IllegalArgumentException(
                    "an UNDETERMINED verdict names the field it could not check and nothing else,"
                            + " because it established no versions");
        }
    }

    /**
     * The verdict for an artefact whose every declared floor was checked and met.
     *
     * @return the met verdict
     */
    public static HostRequirementVerdict met() {
        return MET_VERDICT;
    }

    /**
     * The verdict for a floor this host does not meet.
     *
     * @param field the manifest field, {@code minimumGlibc} or {@code minimumGlibcxx}
     * @param objectName the library the floor is about, as the loader names it
     * @param requiredVersion the version the artefact declares, with its prefix
     * @param availableVersion the version this host provides, with its prefix
     * @return the verdict
     * @throws NullPointerException if any argument is {@code null}
     */
    public static HostRequirementVerdict unmet(
            String field, String objectName, String requiredVersion, String availableVersion) {
        return new HostRequirementVerdict(
                Status.UNMET,
                Optional.of(field),
                Optional.of(objectName),
                Optional.of(requiredVersion),
                Optional.of(availableVersion));
    }

    /**
     * The verdict for a floor that was declared and could not be measured on this host.
     *
     * @param field the manifest field that could not be checked
     * @return the verdict
     * @throws NullPointerException if {@code field} is {@code null}
     */
    public static HostRequirementVerdict undetermined(String field) {
        return new HostRequirementVerdict(
                Status.UNDETERMINED,
                Optional.of(field),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /**
     * Whether this verdict is a reason not to offer the artefact.
     *
     * <p>True for {@link Status#UNMET} alone. {@link Status#UNDETERMINED} is deliberately not a
     * refusal: {@code R-PLAT-02} makes execution the authority, so an unmeasurable floor costs an
     * advance answer and nothing else.
     *
     * @return {@code true} only when a specific floor was shown unmet
     */
    public boolean isRefusal() {
        return status == Status.UNMET;
    }
}
