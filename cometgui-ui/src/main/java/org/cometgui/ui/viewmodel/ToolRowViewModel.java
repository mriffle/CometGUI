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

package org.cometgui.ui.viewmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.cometgui.domain.tools.DeclaredCapability;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallPhase;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.LoaderDiagnostic;
import org.cometgui.domain.tools.ToolAdvisory;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;

/**
 * One tool build, as the Tool Manager shows it.
 *
 * <h2>Everything here is a rendering of one {@link ToolOffer}</h2>
 *
 * <p>The offer is the port's answer and this class never second-guesses it. Every method below is a
 * pure function of the offer it currently holds -- no filtering, no re-ordering, no re-deriving of
 * what the manager already decided -- so a row can be read in a test with no toolkit started and
 * the string a user sees is the string the test asserts.
 *
 * <p>Two components carry no wording of this class's own and are rendered
 * <strong>verbatim</strong>: the {@code R-PLAT-03} loader diagnostic, which already names the
 * required version, the host's version and the alternatives, and each advisory's sentence ({@code
 * R-PERC-11}). Re-wording either would be this application saying something the domain did not.
 *
 * <h2>What is deliberately not here</h2>
 *
 * <p><strong>A failed install carries no reason.</strong> {@link ToolInstallState#FAILED} says an
 * install was attempted and did not succeed, and the only reason-carrying component an offer has is
 * {@link ToolOffer#loaderDiagnostic()} -- which a checksum, extraction or probe failure does not
 * set. {@link #stateText()} therefore says that the attempt failed and stops. Inventing a sentence
 * for it would be this layer making up a fact about a download it did not watch.
 *
 * <h2>The key, and why it is not the tool and the version</h2>
 *
 * <p>Two offers can legitimately share a tool and a version: on Apple silicon Comet 2026.02.2 is
 * published as a native build and an x86-64 build and both are offered. The key is supplied by
 * {@link ToolManagerViewModel}, which is the only place that can see the whole offered list, and it
 * carries an ordinal for exactly that case.
 *
 * <h2>Threading</h2>
 *
 * <p>Read and written on the interface thread. {@link #reportProgress(InstallProgress)} is called
 * from an install thread only after {@link ToolManagerViewModel} has hopped the report onto that
 * thread, which is what keeps this class free of any marshalling of its own.
 */
public final class ToolRowViewModel {

    /** What {@link #capabilitiesText()} says for a build that declares nothing. */
    public static final String NO_CAPABILITIES = "Capabilities: none declared";

    /** What {@link #advisoriesText()} says for a build with no caveats. */
    public static final String NO_ADVISORIES = "Advisories: none";

    /** What {@link #downloadText()} says where this host would fetch nothing. */
    public static final String NOTHING_TO_DOWNLOAD = "Download: nothing to fetch on this host";

    /**
     * What separates two advisory sentences.
     *
     * <p>A line feed, not {@code System.lineSeparator()}: this string is asserted character for
     * character by tests and is read by a user on whichever platform the application runs on, and a
     * separator that changes with the host would make one of those two things wrong.
     */
    private static final String ADVISORY_SEPARATOR = "\n";

    private final String key;

    private final NonNullProperty<ToolOffer> offer;

    private final NonNullProperty<Optional<InstallProgress>> progress =
            new NonNullProperty<>(this, "progress", Optional.empty());

    /**
     * The handle of the install this row started, or {@code null} when none is running.
     *
     * <p>Held here rather than in a map beside the rows because a handle belongs to exactly one row
     * and a second place to look it up is a second answer to "what would Cancel stop".
     */
    private InstallHandle handle;

    /**
     * A row over one offer.
     *
     * @param key the stable key this row's controls are identified by
     * @param offer the port's answer for this build
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code key} is blank
     */
    ToolRowViewModel(String key, ToolOffer offer) {
        this.key = Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException(
                    "a tool row key must not be blank: it is what every control in the row is"
                            + " identified by");
        }
        this.offer = new NonNullProperty<>(this, "offer", Objects.requireNonNull(offer, "offer"));
    }

    /**
     * The stable key this row's controls are identified by.
     *
     * @return the key, never blank
     */
    public String key() {
        return key;
    }

    /**
     * The offer this row is showing.
     *
     * <p>Read-only: it changes when {@link ToolManagerViewModel#refresh()} reads the port again,
     * and a view that watches it does not have to be rebuilt when a row's state moves.
     *
     * @return the read-only property, never holding {@code null}
     */
    public ReadOnlyObjectProperty<ToolOffer> offerProperty() {
        return offer.getReadOnlyProperty();
    }

    /**
     * The offer this row is showing.
     *
     * @return the offer, never {@code null}
     */
    public ToolOffer offer() {
        return offer.get();
    }

    /**
     * The last progress report this row's install produced, if one has.
     *
     * @return the read-only property, holding an empty optional until an install reports
     */
    public ReadOnlyObjectProperty<Optional<InstallProgress>> progressProperty() {
        return progress.getReadOnlyProperty();
    }

    /**
     * The tool and the version, as upstream spells the version.
     *
     * <p>{@link org.cometgui.domain.tools.ToolVersion#text()} and never the cache directory name,
     * which is normalised: Percolator 3.07.1 installs under {@code percolator/3.7.1/} and calls
     * itself 3.07.1 everywhere a person reads it.
     *
     * @return for example {@code "percolator 3.07.1"}
     */
    public String nameText() {
        ToolOffer current = offer.get();
        return current.tool().id() + " " + current.version().text();
    }

    /**
     * Where this build came from and what state it is in, in words.
     *
     * @return for example {@code "CometGUI-managed: not installed"}
     */
    public String stateText() {
        ToolOffer current = offer.get();
        return originWords(current.origin()) + ": " + stateWords(current.state());
    }

    /**
     * What this build can do, each claim with the evidence behind it.
     *
     * <p>The evidence is rendered because it is the difference between a capability this project
     * watched a binary perform and one inferred from its bytes, and a user choosing a tool for a
     * run needs to know which they are being offered.
     *
     * @return for example {@code "Capabilities: XML_OUTPUT (observed-by-execution),
     *     XML_DECOY_OUTPUT (observed-by-execution)"}, or {@link #NO_CAPABILITIES}
     */
    public String capabilitiesText() {
        List<DeclaredCapability> declared = offer.get().capabilities();
        if (declared.isEmpty()) {
            return NO_CAPABILITIES;
        }
        List<String> claims = new ArrayList<>(declared.size());
        for (DeclaredCapability capability : declared) {
            claims.add(capability.capability().id() + " (" + capability.evidence().id() + ")");
        }
        return "Capabilities: " + String.join(", ", claims);
    }

    /**
     * The caveats {@code R-PERC-11} requires shown at selection time, one per line and verbatim.
     *
     * @return the advisories under a lead-in line, or {@link #NO_ADVISORIES}
     */
    public String advisoriesText() {
        List<ToolAdvisory> advisories = offer.get().advisories();
        if (advisories.isEmpty()) {
            return NO_ADVISORIES;
        }
        StringBuilder text = new StringBuilder("Advisories:");
        for (ToolAdvisory advisory : advisories) {
            text.append(ADVISORY_SEPARATOR).append(advisory.text());
        }
        return text.toString();
    }

    /**
     * How many bytes installing this build would transfer.
     *
     * <p>The whole transfer, which is what the offer carries: Percolator 3.07.1 on Linux fetches a
     * portable zip <em>and</em> the package the two XSD companions come out of, and a figure three
     * times smaller than the transfer it names would be a number a user acts on and is wrong. The
     * grouping separator is {@link Locale#ROOT}'s, so the sentence is the same on every machine.
     *
     * @return for example {@code "Download: 2,798,963 bytes"}, or {@link #NOTHING_TO_DOWNLOAD}
     */
    public String downloadText() {
        OptionalLong bytes = offer.get().downloadSizeBytes();
        if (bytes.isEmpty()) {
            return NOTHING_TO_DOWNLOAD;
        }
        return "Download: " + String.format(Locale.ROOT, "%,d", bytes.getAsLong()) + " bytes";
    }

    /**
     * The {@code R-PLAT-03} diagnostic, exactly as the domain rendered it.
     *
     * @return the diagnostic's own sentence, or empty where the loader refused nothing
     */
    public Optional<String> diagnosticText() {
        return offer.get().loaderDiagnostic().map(LoaderDiagnostic::message);
    }

    /**
     * Where an installed build lives on this machine.
     *
     * @return for example {@code "Installed at: /home/you/.local/share/cometgui/tools/..."}, or
     *     empty where nothing is installed
     */
    public Optional<String> installedPathText() {
        return offer.get().installedPath().map(path -> "Installed at: " + path);
    }

    /**
     * Where a running install has got to.
     *
     * @return the phase and the bytes moved so far, or empty until an install reports
     */
    public Optional<String> progressText() {
        return progress.get().map(ToolRowViewModel::progressTextFor);
    }

    /**
     * Whether an install can be started for this build.
     *
     * <p>A managed build that is not installed, or whose last attempt failed, can be fetched. An
     * installed one needs nothing; one already installing is under way; and a build that is not
     * published here or cannot run here is not offered as a one-click install at all, which is
     * {@code R-PERC-01} read literally. A registered local binary was never downloaded and cannot
     * be.
     *
     * @return {@code true} when the Install action should be available
     */
    public boolean canInstall() {
        ToolOffer current = offer.get();
        return current.origin() == ToolOrigin.MANAGED
                && (current.state() == ToolInstallState.NOT_INSTALLED
                        || current.state() == ToolInstallState.FAILED);
    }

    /**
     * Whether a running install can be stopped from this row.
     *
     * @return {@code true} when this row holds the handle of an install that is still running
     */
    public boolean canCancel() {
        return handle != null && offer.get().state() == ToolInstallState.INSTALLING;
    }

    /**
     * Asks this row's install to stop.
     *
     * <p>Does nothing when no install is running, for the reason {@link InstallHandle#cancel()}
     * gives: the user's click and the install's last step race by nature.
     */
    public void cancelInstall() {
        InstallHandle running = handle;
        if (running != null) {
            running.cancel();
        }
    }

    /**
     * Describes the row by its key and the offer it is showing.
     *
     * @return a description for an assertion message
     */
    @Override
    public String toString() {
        return "ToolRowViewModel[" + key + ", " + nameText() + " " + offer.get().state() + "]";
    }

    /**
     * Shows a newer answer from the port for the same build.
     *
     * @param newer the offer to show
     * @throws NullPointerException if {@code newer} is {@code null}
     */
    void update(ToolOffer newer) {
        offer.set(Objects.requireNonNull(newer, "newer"));
    }

    /**
     * Records the handle of an install this row has just started.
     *
     * @param started the handle that can stop it
     * @throws NullPointerException if {@code started} is {@code null}
     */
    void installStarted(InstallHandle started) {
        this.handle = Objects.requireNonNull(started, "started");
        this.progress.set(Optional.empty());
    }

    /** Forgets the handle of an install that has reached a terminal phase. */
    void installFinished() {
        this.handle = null;
    }

    /**
     * Shows a progress report.
     *
     * @param report where the install has got to
     * @throws NullPointerException if {@code report} is {@code null}
     */
    void reportProgress(InstallProgress report) {
        progress.set(Optional.of(Objects.requireNonNull(report, "report")));
    }

    /**
     * One progress report in words.
     *
     * @param report the report to render
     * @return the phase and the bytes moved, with the total where the server declared one
     * @throws NullPointerException if {@code report} is {@code null}
     */
    public static String progressTextFor(InstallProgress report) {
        Objects.requireNonNull(report, "report");
        String moved = String.format(Locale.ROOT, "%,d", report.bytesTransferred());
        if (report.hasKnownTotal()) {
            return phaseWords(report.phase())
                    + ": "
                    + moved
                    + " of "
                    + String.format(Locale.ROOT, "%,d", report.totalBytes())
                    + " bytes";
        }
        return phaseWords(report.phase()) + ": " + moved + " bytes";
    }

    /**
     * One install state in words.
     *
     * <p>A switch expression with no default arm, so that a state added to the domain fails to
     * compile here rather than reaching a user as a blank label.
     *
     * @param state the state to render
     * @return the state in words, lower case, to be read after {@link #originWords(ToolOrigin)}
     * @throws NullPointerException if {@code state} is {@code null}
     */
    public static String stateWords(ToolInstallState state) {
        Objects.requireNonNull(state, "state");
        return switch (state) {
            case NOT_INSTALLED -> "not installed";
            case INSTALLING -> "installing";
            case INSTALLED -> "installed";
            case FAILED -> "the last install attempt did not succeed";
            case UNAVAILABLE_ON_THIS_PLATFORM -> "not published for this platform";
            case HOST_REQUIREMENTS_NOT_MET -> "cannot run on this host";
        };
    }

    /**
     * Where a build came from, in words.
     *
     * @param origin the origin to render
     * @return the origin in words
     * @throws NullPointerException if {@code origin} is {@code null}
     */
    public static String originWords(ToolOrigin origin) {
        Objects.requireNonNull(origin, "origin");
        return switch (origin) {
            case MANAGED -> "CometGUI-managed";
            case LOCAL -> "Your own binary";
        };
    }

    /**
     * One install phase in words.
     *
     * @param phase the phase to render
     * @return the phase in words
     * @throws NullPointerException if {@code phase} is {@code null}
     */
    public static String phaseWords(InstallPhase phase) {
        Objects.requireNonNull(phase, "phase");
        return switch (phase) {
            case DOWNLOADING -> "Downloading";
            case VERIFYING -> "Verifying the download";
            case EXTRACTING -> "Extracting";
            case INSTALLING -> "Installing";
            case PROBING -> "Probing";
            case DONE -> "Done";
            case CANCELLED -> "Cancelled";
            case FAILED -> "Failed";
        };
    }
}
