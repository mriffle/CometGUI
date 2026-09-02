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

package org.cometgui.domain.tools;

import java.util.Objects;

/**
 * How a capability claim was established. This is the type that keeps the manifest honest.
 *
 * <p><strong>No macOS binary has ever been executed anywhere in this project</strong>, and until
 * 2026-09-02 no Windows one had either -- on that date a GitHub {@code windows-latest} runner ran
 * Percolator 3.07.1's portable build and watched it write Percolator XML. Every row that has not
 * been through something like that is a byte-marker inference: someone looked for a writer literal
 * in the downloaded bytes. That is real evidence and it is not the same evidence as running the
 * program, and the difference decides what the application is allowed to tell a scientist.
 *
 * <p>So: <strong>a capability whose evidence is not {@link #OBSERVED_BY_EXECUTION} may never be
 * described as verified, confirmed, proven or tested.</strong> Not in the manifest, not in the Tool
 * Manager, not in a provenance record, not in the documentation. "Expected" and "not yet observed
 * on this platform" are honest; a fabricated claim is not, and it is the one failure this structure
 * cannot absorb, because the whole product's promise is that it says what it knows.
 *
 * <p>{@code R-TOOL-07} then makes the probe the final authority: on first install, and again
 * whenever the recorded executable checksum changes, the capability set is probed on the host and
 * the probe wins where it disagrees with the manifest. This enum exists so the application can say
 * something truthful in the window before that probe has run.
 */
public enum CapabilityEvidence {

    /**
     * The project ran the binary and watched it do this. The only evidence that supports the words
     * verified, confirmed, proven or tested.
     */
    OBSERVED_BY_EXECUTION("observed-by-execution", true),

    /**
     * The artefact's bytes were inspected and carry the markers of the capability -- a writer
     * literal, a linked library, a symbol -- but the binary was never run, because this project has
     * no machine of that platform to run it on.
     */
    INFERRED_FROM_ARTEFACT_BYTES("inferred-from-artefact-bytes", false),

    /**
     * Neither observed nor inferred. This is what an unknown local binary starts from: {@code
     * R-TOOL-08} requires that absent positive evidence, a capability is absent.
     */
    UNVERIFIED("unverified", false);

    private final String id;
    private final boolean observed;

    CapabilityEvidence(String id, boolean observed) {
        this.id = id;
        this.observed = observed;
    }

    /**
     * The stable identifier used in the artefact manifest and the provenance record.
     *
     * @return the identifier, never {@code null} or blank
     */
    public String id() {
        return id;
    }

    /**
     * Whether this evidence came from running the program.
     *
     * <p>The single question any wording that claims a capability has to ask first. False for
     * everything but {@link #OBSERVED_BY_EXECUTION}.
     *
     * @return {@code true} only for evidence obtained by execution
     */
    public boolean isObserved() {
        return observed;
    }

    /**
     * Resolves an identifier read from a manifest back to its constant.
     *
     * <p>Exact match: no trimming and no case folding, for the reason given on {@link
     * ToolName#fromId(String)}.
     *
     * @param id the identifier to resolve
     * @return the matching evidence
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if no evidence has that identifier, with a message naming
     *     the rejected value and listing what is accepted
     */
    public static CapabilityEvidence fromId(String id) {
        Objects.requireNonNull(id, "id");
        for (CapabilityEvidence evidence : values()) {
            if (evidence.id.equals(id)) {
                return evidence;
            }
        }
        throw new IllegalArgumentException(
                "no capability evidence has the id \""
                        + id
                        + "\"; expected one of [observed-by-execution,"
                        + " inferred-from-artefact-bytes, unverified]");
    }
}
