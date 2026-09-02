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

/**
 * Why a probe stage failed, and which stage it failed at.
 *
 * <p><strong>The stage is carried here so that a loader failure can never be reported as a
 * capability verdict.</strong> That is the trap {@code phases/PHASE-05-tool-registry.rst} names
 * explicitly, and it is not hypothetical: the Windows Percolator zip ships none of the four Visual
 * C++ runtime DLLs it imports, so on a machine without them the binary does not start -- and
 * "Percolator is not XML-capable on Windows" would be a false statement about the product's most
 * important capability, produced by a probe that never got as far as looking.
 *
 * <p><strong>Where a kind could belong to more than one stage, it is assigned to the
 * earliest.</strong> A timed-out probe and an unexplained non-zero exit can both happen at any
 * stage; both are recorded as {@link ProbeStage#LOADABILITY} failures because the safe direction of
 * that ambiguity is "we did not establish that it starts", never "we established that it cannot do
 * this". The one kind that <em>is</em> a capability verdict is {@link #CAPABILITY_ABSENT}, and it
 * is reserved for a functional probe that ran and produced an answer.
 */
public enum ProbeFailureKind {

    /**
     * The dynamic loader could not find a shared library the binary needs. Observed on this
     * project's own Debian 12 host from the Percolator 3.09 {@code .deb} payload: {@code
     * percolator: error while loading shared libraries: libboost_filesystem.so.1.83.0: cannot open
     * shared object file: No such file or directory}, exit 127.
     */
    MISSING_SHARED_OBJECT(ProbeStage.LOADABILITY),

    /**
     * A shared library is present but does not provide a symbol version the binary needs. The
     * failure hiding beneath the one above: with a stub {@code libboost_filesystem.so.1.83.0} in
     * place, the same binary reports {@code /lib/x86_64-linux-gnu/libstdc++.so.6: version
     * 'GLIBCXX_3.4.32' not found} and {@code /lib/x86_64-linux-gnu/libc.so.6: version 'GLIBC_2.38'
     * not found}.
     */
    MISSING_SYMBOL_VERSION(ProbeStage.LOADABILITY),

    /**
     * The binary was built for a different processor architecture. On Apple silicon an x86-64
     * artefact is not this: it is eligible through Rosetta 2, and only fails this way when Rosetta
     * 2 is absent ({@code D-004}).
     */
    WRONG_ARCHITECTURE(ProbeStage.LOADABILITY),

    /**
     * macOS refused to run the file because its {@code com.apple.quarantine} attribute was never
     * cleared ({@code R-PLAT-04}). A Gatekeeper dialog the application cannot dismiss, not a defect
     * in the binary.
     */
    MACOS_QUARANTINE(ProbeStage.LOADABILITY),

    /**
     * A Windows runtime DLL the binary imports is not on the machine -- {@code MSVCP140.dll},
     * {@code VCRUNTIME140.dll}, {@code VCRUNTIME140_1.dll}, {@code VCOMP140.DLL}. The portable
     * Percolator zip ships none of them and the NSIS installer ships nine, which is why this is a
     * declared host requirement rather than a surprise.
     */
    MISSING_WINDOWS_RUNTIME_DLL(ProbeStage.LOADABILITY),

    /** The file is there but is not executable -- the permission bits {@code R-PLAT-05} sets. */
    NOT_EXECUTABLE(ProbeStage.LOADABILITY),

    /**
     * The probe gave up waiting. Assigned to the earliest stage for the reason on this type: a
     * probe that never returned established nothing, least of all that a capability is absent.
     */
    TIMED_OUT(ProbeStage.LOADABILITY),

    /**
     * The binary ran and printed something, but no version could be parsed from it. An identity
     * failure, not a capability one: the build may be perfectly good and merely unrecognised.
     */
    UNPARSEABLE_VERSION(ProbeStage.IDENTITY),

    /**
     * The functional probe ran to completion and the capability is not there. The only kind that is
     * a statement about what the build can do -- for {@code XML_OUTPUT} that means the binary was
     * run with {@code -X} over a sufficient synthetic PIN and the document it should have written
     * was not there or was not a {@code percolator_out} document.
     */
    CAPABILITY_ABSENT(ProbeStage.CAPABILITY),

    /**
     * The probe exited non-zero for a reason none of the other kinds explains, with no loader
     * marker in its output.
     *
     * <p>Recorded as a loadability failure by the rule on this type. An unrecognised non-zero exit
     * does not establish that the binary started -- the loader may have refused it in a form this
     * project has not seen -- and calling that a capability verdict is the failure this enumeration
     * exists to prevent.
     *
     * <p><strong>It is not {@link #CAPABILITY_ABSENT}.</strong> Percolator's documented false
     * negative is exactly this shape: given only 8 target and 8 decoy rows, a fully XML-capable
     * 3.07.1 exits 1 with {@code Error: median decoy score <= score at 1% FDR} and leaves a
     * zero-byte output file behind. Reporting that as a missing capability is the defect {@code
     * R-PERC-02}'s fixture size exists to avoid.
     */
    EXECUTION_FAILED(ProbeStage.LOADABILITY);

    private final ProbeStage stage;

    ProbeFailureKind(ProbeStage stage) {
        this.stage = stage;
    }

    /**
     * The probe stage this kind of failure belongs to.
     *
     * @return the stage, never {@code null}
     */
    public ProbeStage stage() {
        return stage;
    }

    /**
     * Whether this failure happened before the binary was known to start.
     *
     * <p>A kind for which this is true must never be surfaced as a statement about capability. It
     * is the question a classifier asks before it writes a verdict anywhere a user will read it.
     *
     * @return {@code true} when {@link #stage()} is {@link ProbeStage#LOADABILITY}
     */
    public boolean isLoadabilityFailure() {
        return stage == ProbeStage.LOADABILITY;
    }
}
