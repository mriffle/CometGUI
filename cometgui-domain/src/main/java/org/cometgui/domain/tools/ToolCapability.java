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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * One thing a tool build can do, as the specification's <em>Capability and runtime probing</em>
 * section names it.
 *
 * <p>The specification's central rule is that a version number does not imply a capability: what a
 * Percolator build can do is a property of that build, probed. Percolator 3.09 removed XML output
 * altogether; the {@code noxml} and {@code XML_SUPPORT=ON} twins of 3.07.1 print
 * <em>byte-identical</em> help text and both list {@code -X}, so a text probe discriminates
 * nothing. Every capability therefore reaches the application as a {@link DeclaredCapability},
 * carrying how it was established.
 *
 * <p><strong>Each constant carries the tool it belongs to.</strong> A capability is not a free
 * label: {@link #THERMO_RAW_WINDOWS} is a fact about Comet and means nothing said of Percolator.
 * Carrying the owner here is what lets a manifest entry or a tool offer that mixes them be rejected
 * -- see {@link #requireBelongsTo(ToolName)} -- rather than rendering a row that claims something
 * impossible.
 *
 * <p>PDV and the Limelight converter have no constants. Neither is capability-probed: they are
 * JARs, identified by version, and phase 05's probe establishes that they load and report a
 * version. Adding a capability for either is a specification change, not a convenience.
 */
public enum ToolCapability {

    /**
     * Percolator can write pout XML with {@code -X}.
     *
     * <p>Probed functionally and never from help text, per {@code R-PERC-02}: run the binary over a
     * synthetic PIN of at least 64 target and 64 decoy rows and require the file it writes to carry
     * the {@code percolator_out} root element, the namespace and the expected {@code psm} count.
     * "The file exists" is not sufficient -- an under-sized fixture makes a capable binary abort
     * and leave a zero-byte file.
     */
    XML_OUTPUT("XML_OUTPUT", ToolName.PERCOLATOR),

    /**
     * Percolator can write decoys into that XML with {@code -X -Z}.
     *
     * <p><strong>Separate from {@link #XML_OUTPUT} on purpose.</strong> {@code
     * docs/feasibility/noxml-capability.rst} recommends two flags rather than one because
     * Limelight's {@code --import-decoys} needs this second one, and because a future release could
     * keep one and drop the other -- exactly the kind of non-monotonic change 3.09 already made
     * once. One combined flag would make that release indistinguishable from a fully capable one.
     */
    XML_DECOY_OUTPUT("XML_DECOY_OUTPUT", ToolName.PERCOLATOR),

    /** Percolator writes the PSM-level tab-separated results the Results tab reads. */
    PSM_TSV_OUTPUT("PSM_TSV_OUTPUT", ToolName.PERCOLATOR),

    /** Percolator writes the peptide-level tab-separated results, filtered independently. */
    PEPTIDE_TSV_OUTPUT("PEPTIDE_TSV_OUTPUT", ToolName.PERCOLATOR),

    /** Percolator writes the decoy results alongside the targets. */
    DECOY_OUTPUT("DECOY_OUTPUT", ToolName.PERCOLATOR),

    /** Percolator writes the learned feature weights the Definition of Done requires shown. */
    WEIGHTS_OUTPUT("WEIGHTS_OUTPUT", ToolName.PERCOLATOR),

    /** Percolator accepts a thread-count option. */
    THREAD_OPTION("THREAD_OPTION", ToolName.PERCOLATOR),

    /** Percolator accepts a random seed, without which a rerun is not reproducible. */
    SEED_OPTION("SEED_OPTION", ToolName.PERCOLATOR),

    /** Comet writes pepXML. */
    PEPXML_OUTPUT("PEPXML_OUTPUT", ToolName.COMET),

    /** Comet writes the tab-delimited PIN that Percolator reads. */
    PIN_OUTPUT("PIN_OUTPUT", ToolName.COMET),

    /** Comet answers {@code -q}, printing the complete parameter set for its own version. */
    COMPLETE_PARAMS_QUERY("COMPLETE_PARAMS_QUERY", ToolName.COMET),

    /**
     * Comet reads Thermo RAW files directly. Windows only, and only when {@code CometWrapper.dll},
     * {@code ThermoFisher.CommonCore.Data.dll} and {@code
     * ThermoFisher.CommonCore.RawFileReader.dll} are installed beside the executable: {@code
     * R-TOOL-02} requires an install missing them not to advertise this.
     */
    THERMO_RAW_WINDOWS("THERMO_RAW_WINDOWS", ToolName.COMET),

    /** Comet accepts the fragment-ion index option {@code -i}. */
    FRAGMENT_ION_INDEX("FRAGMENT_ION_INDEX", ToolName.COMET),

    /** Comet accepts the peptide index option {@code -j}. */
    PEPTIDE_INDEX("PEPTIDE_INDEX", ToolName.COMET),

    /** Comet accepts a scan range, {@code -F} and {@code -L}. */
    SCAN_RANGE("SCAN_RANGE", ToolName.COMET),

    /** Comet accepts an output basename, {@code -N}. */
    OUTPUT_BASENAME("OUTPUT_BASENAME", ToolName.COMET);

    private final String id;
    private final ToolName tool;

    ToolCapability(String id, ToolName tool) {
        this.id = id;
        this.tool = tool;
    }

    /**
     * The stable identifier used in the artefact manifest and in the provenance record's capability
     * set.
     *
     * <p>It is the token the specification itself uses. It is stored rather than returned from
     * {@code name()} for the reason given on {@link ToolName}: a rename of a Java constant must be
     * a visible change to what every provenance record says, not a silent one.
     *
     * @return the identifier, never {@code null} or blank
     */
    public String id() {
        return id;
    }

    /**
     * The tool this capability is a fact about.
     *
     * @return the owning tool, never {@code null}
     */
    public ToolName tool() {
        return tool;
    }

    /**
     * Whether this capability may be declared of the given tool.
     *
     * @param candidate the tool a caller is about to attach this capability to
     * @return {@code true} if the capability belongs to that tool
     * @throws NullPointerException if {@code candidate} is {@code null}
     */
    public boolean belongsTo(ToolName candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return tool == candidate;
    }

    /**
     * Checks that this capability may be declared of the given tool, and refuses if it may not.
     *
     * <p>This is the guard that stops a manifest claiming {@code THERMO_RAW_WINDOWS} for
     * Percolator. A capability attached to the wrong tool is not a cosmetic error: it would render
     * in the Tool Manager as something the build can do and would be recorded in provenance as
     * something the run relied on.
     *
     * @param candidate the tool this capability is being declared of
     * @return this capability, so that the check can sit in a stream or an assignment
     * @throws NullPointerException if {@code candidate} is {@code null}
     * @throws IllegalArgumentException if the capability belongs to a different tool, with a
     *     message naming the capability, its own tool and the tool it was offered to
     */
    public ToolCapability requireBelongsTo(ToolName candidate) {
        if (!belongsTo(candidate)) {
            throw new IllegalArgumentException(
                    id
                            + " is a capability of "
                            + tool.id()
                            + " and cannot be declared for "
                            + candidate.id());
        }
        return this;
    }

    /**
     * Every capability that may be declared of one tool.
     *
     * @param tool the tool to enumerate
     * @return the capabilities belonging to it, immutable and possibly empty -- PDV and the
     *     Limelight converter have none
     * @throws NullPointerException if {@code tool} is {@code null}
     */
    public static Set<ToolCapability> declarableFor(ToolName tool) {
        Objects.requireNonNull(tool, "tool");
        EnumSet<ToolCapability> declarable = EnumSet.noneOf(ToolCapability.class);
        for (ToolCapability capability : values()) {
            if (capability.tool == tool) {
                declarable.add(capability);
            }
        }
        return Collections.unmodifiableSet(declarable);
    }

    /**
     * Resolves an identifier read from a manifest or a provenance record back to its constant.
     *
     * <p>Exact match: no trimming and no case folding, for the reason given on {@link
     * ToolName#fromId(String)}.
     *
     * @param id the identifier to resolve
     * @return the matching capability
     * @throws NullPointerException if {@code id} is {@code null}
     * @throws IllegalArgumentException if no capability has that identifier, with a message naming
     *     the rejected value and listing what is accepted
     */
    public static ToolCapability fromId(String id) {
        Objects.requireNonNull(id, "id");
        for (ToolCapability capability : values()) {
            if (capability.id.equals(id)) {
                return capability;
            }
        }
        throw new IllegalArgumentException(
                "no tool capability has the id \""
                        + id
                        + "\"; expected one of [XML_OUTPUT, XML_DECOY_OUTPUT, PSM_TSV_OUTPUT,"
                        + " PEPTIDE_TSV_OUTPUT, DECOY_OUTPUT, WEIGHTS_OUTPUT, THREAD_OPTION,"
                        + " SEED_OPTION, PEPXML_OUTPUT, PIN_OUTPUT, COMPLETE_PARAMS_QUERY,"
                        + " THERMO_RAW_WINDOWS, FRAGMENT_ION_INDEX, PEPTIDE_INDEX, SCAN_RANGE,"
                        + " OUTPUT_BASENAME]");
    }
}
