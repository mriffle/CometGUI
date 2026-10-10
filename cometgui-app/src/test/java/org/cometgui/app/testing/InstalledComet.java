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

package org.cometgui.app.testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.cometgui.domain.tools.InstallHandle;
import org.cometgui.domain.tools.InstallProgressListener;
import org.cometgui.domain.tools.NoManagedBuild;
import org.cometgui.domain.tools.ToolInstallState;
import org.cometgui.domain.tools.ToolManager;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolOffer;
import org.cometgui.domain.tools.ToolOrigin;
import org.cometgui.domain.tools.ToolRegistrationException;
import org.cometgui.domain.tools.ToolVersion;

/**
 * A Tool Manager whose offers are Comet builds registered at given paths, as {@link
 * ToolOrigin#LOCAL} and {@link ToolInstallState#INSTALLED} -- what the Tool Manager reports after
 * the scientist registers a Comet already on the computer -- and, since Phase 09, whatever
 * Percolator builds a test adds ({@link #with}). It installs nothing; it records which threads
 * asked for the offers; a registration is the {@link Registrar} a test gives it ({@link
 * #registering}), and a registered build is offered from then on, as the real Tool Manager does.
 */
public final class InstalledComet implements ToolManager {

    /** What a registration does: typically the real local-Percolator registration. */
    @FunctionalInterface
    public interface Registrar {

        /**
         * Registers a binary.
         *
         * @param tool which tool it is claimed to be
         * @param executable the file
         * @return the registered build
         * @throws ToolRegistrationException if it is refused
         */
        ToolOffer register(ToolName tool, Path executable) throws ToolRegistrationException;
    }

    private final List<ToolOffer> offers;

    private final Registrar registrar;

    private final List<String> askedOn = Collections.synchronizedList(new ArrayList<>());

    private InstalledComet(List<ToolOffer> offers, Registrar registrar) {
        this.offers = Collections.synchronizedList(new ArrayList<>(offers));
        this.registrar = registrar;
    }

    private InstalledComet(List<ToolOffer> offers) {
        this(
                offers,
                (tool, executable) -> {
                    throw new UnsupportedOperationException(
                            "this test's Tool Manager registers nothing");
                });
    }

    /**
     * This manager with more offers after its own -- a Percolator a test staged, say.
     *
     * @param more the offers to add, in order
     * @return a new manager
     */
    public InstalledComet with(ToolOffer... more) {
        List<ToolOffer> all = new ArrayList<>(snapshot());
        all.addAll(List.of(more));
        return new InstalledComet(all, registrar);
    }

    /**
     * This manager registering binaries through a registrar.
     *
     * @param registration what a registration does
     * @return a new manager
     */
    public InstalledComet registering(Registrar registration) {
        return new InstalledComet(snapshot(), registration);
    }

    /**
     * Comet of one release, installed at a path.
     *
     * @param release the release
     * @param executable the executable
     * @return the manager
     */
    public static InstalledComet at(String release, Path executable) {
        return new InstalledComet(
                List.of(
                        new ToolOffer(
                                ToolName.COMET,
                                ToolVersion.parse(release),
                                ToolOrigin.LOCAL,
                                ToolInstallState.INSTALLED,
                                List.of(),
                                List.of(),
                                Optional.empty(),
                                Optional.of(executable),
                                OptionalLong.empty())));
    }

    /**
     * A manager that offers nothing at all.
     *
     * @return the manager
     */
    public static InstalledComet nothing() {
        return new InstalledComet(List.of());
    }

    @Override
    public List<ToolOffer> offers() {
        askedOn.add(Thread.currentThread().getName());
        return snapshot();
    }

    private List<ToolOffer> snapshot() {
        synchronized (offers) {
            return List.copyOf(offers);
        }
    }

    /**
     * The names of the threads that asked for the offers, oldest first.
     *
     * @return the names
     */
    public List<String> askedOn() {
        synchronized (askedOn) {
            return List.copyOf(askedOn);
        }
    }

    /**
     * Nothing is reported missing: the offers this double answers with are written by the test.
     *
     * @return an empty list
     */
    @Override
    public List<NoManagedBuild> noManagedBuild() {
        return List.of();
    }

    @Override
    public InstallHandle install(
            ToolName tool, ToolVersion version, InstallProgressListener listener) {
        throw new UnsupportedOperationException("this test's Tool Manager installs nothing");
    }

    @Override
    public ToolOffer registerLocalBinary(ToolName tool, Path executable)
            throws ToolRegistrationException {
        ToolOffer registered = registrar.register(tool, executable);
        offers.add(registered);
        return registered;
    }
}
