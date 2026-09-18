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

package org.cometgui.ui.testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgress;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolVersion;

/**
 * A {@link ToolManager} whose answers a test writes out.
 *
 * <p>It exists for the states the real runtime cannot produce on this machine -- {@link
 * ToolInstallState#INSTALLING}, {@link ToolInstallState#FAILED}, {@link
 * ToolInstallState#HOST_REQUIREMENTS_NOT_MET} and a registered local binary -- and for driving an
 * install a step at a time. It is <strong>not</strong> a substitute for the real {@code
 * ManagedToolManager}: the tests that prove the section renders this host's actual offers drive
 * that one over the shipped manifest.
 *
 * <p>Two behaviours are copied from the real runtime on purpose, because a double that did not have
 * them would let a test pass over a view that could not work:
 *
 * <ul>
 *   <li>{@link #install} marks the matching offer {@link ToolInstallState#INSTALLING} before it
 *       returns, so the next read of {@link #offers()} says so -- exactly as the real manager's
 *       attempt map does;
 *   <li>progress arrives <em>after</em> {@code install} returns, through {@link
 *       #report(InstallProgress)}, because that is when it arrives from a real install thread. A
 *       double that reported from inside {@code install} would test an ordering the product never
 *       sees.
 * </ul>
 */
public final class ScriptedToolManager implements ToolManager {

    private final List<String> installsAsked = new ArrayList<>();

    private final RecordingInstallHandle handle = new RecordingInstallHandle();

    private List<ToolOffer> offers;

    private InstallProgressListener listener;

    /**
     * A manager answering with these offers, in this order.
     *
     * @param offers what {@link #offers()} returns
     */
    public ScriptedToolManager(ToolOffer... offers) {
        this.offers = List.of(offers);
    }

    /**
     * Changes what the next read of the offered list returns.
     *
     * @param newOffers the new answer
     */
    public void answerWith(ToolOffer... newOffers) {
        this.offers = List.of(newOffers);
    }

    /** {@inheritDoc} */
    @Override
    public List<ToolOffer> offers() {
        return List.copyOf(offers);
    }

    /** {@inheritDoc} */
    @Override
    public InstallHandle install(
            ToolName tool, ToolVersion version, InstallProgressListener progressListener) {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(version, "version");
        this.listener = Objects.requireNonNull(progressListener, "progressListener");
        installsAsked.add(tool.id() + " " + version.text());
        List<ToolOffer> running = new ArrayList<>(offers.size());
        for (ToolOffer offer : offers) {
            running.add(
                    offer.tool() == tool && offer.version().equals(version)
                            ? installing(offer)
                            : offer);
        }
        offers = List.copyOf(running);
        return handle;
    }

    /** {@inheritDoc} */
    @Override
    public ToolOffer registerLocalBinary(ToolName tool, Path executable) {
        throw new UnsupportedOperationException(
                "this double does not register local binaries; the offers it answers with are"
                        + " written out by the test");
    }

    /**
     * Every install this manager was asked for, in order.
     *
     * @return {@code "<tool id> <version text>"} for each, which is what a view can ask for
     */
    public List<String> installsAsked() {
        return List.copyOf(installsAsked);
    }

    /**
     * The handle every install of this manager returns.
     *
     * @return the handle, which records how often it was cancelled
     */
    public RecordingInstallHandle handle() {
        return handle;
    }

    /**
     * Sends one progress report to the listener the last install was started with.
     *
     * @param progress where the install has got to
     * @throws IllegalStateException if no install has been started, so that a test which forgot to
     *     start one fails rather than quietly reporting to nobody
     */
    public void report(InstallProgress progress) {
        Objects.requireNonNull(progress, "progress");
        if (listener == null) {
            throw new IllegalStateException(
                    "no install has been started on this manager, so there is nobody to report to");
        }
        listener.onInstallProgress(progress);
    }

    private static ToolOffer installing(ToolOffer offer) {
        return new ToolOffer(
                offer.tool(),
                offer.version(),
                offer.origin(),
                ToolInstallState.INSTALLING,
                offer.capabilities(),
                offer.advisories(),
                offer.loaderDiagnostic(),
                offer.installedPath(),
                offer.downloadSizeBytes());
    }

    /** An {@link InstallHandle} that records being asked to stop. */
    public static final class RecordingInstallHandle implements InstallHandle {

        private int cancellations;

        /** {@inheritDoc} */
        @Override
        public void cancel() {
            cancellations++;
        }

        /**
         * How often this handle was asked to stop.
         *
         * @return the count, zero until something calls {@link #cancel()}
         */
        public int cancellations() {
            return cancellations;
        }
    }
}
