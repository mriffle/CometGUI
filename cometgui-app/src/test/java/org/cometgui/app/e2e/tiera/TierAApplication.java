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

package org.cometgui.app.e2e.tiera;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javafx.application.Application;
import javafx.stage.Stage;
import org.cometgui.app.bootstrap.CometGuiApplication;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.BuildIdentityResource;
import org.cometgui.app.config.PdvDisplay;
import org.cometgui.app.config.RunWiring;
import org.cometgui.app.e2e.scenario.ChooserScript;
import org.cometgui.app.e2e.scenario.ScenarioLayout;
import org.cometgui.app.e2e.support.FreshHome;
import org.cometgui.app.testing.ProjectXvfb;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.app.testing.ScriptedChooser;
import org.cometgui.app.uidriver.RemotableUiDriver;
import org.cometgui.app.uidriver.RobotFxUiDriver;
import org.cometgui.app.uidriver.RunningApplication;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.ports.EnvironmentReader;
import org.cometgui.tools.limelight.fake.LoopbackGuard;
import org.testfx.api.FxToolkit;

/**
 * Tier A's composition (design decision P14-4), said once for every Tier A scenario: the real
 * {@link CometGuiApplication}, in-process, over a production {@link ApplicationServices} for a
 * fresh home ({@link FreshHome#services(Map)}) whose environment names the project-local Xvfb as
 * {@code DISPLAY}, the production build identity, a {@link ScriptedChooser} as the file-chooser
 * factory, and {@link RunWiring.Setup#forThisApplication}'s own Tool Manager and project with PDV's
 * display environment as the one further seam -- {@link PdvDisplay#fromHost} over the application's
 * environment plus the project-local X11 and font closures' library and fontconfig locations,
 * because this build host has no X11 library at all (Phase 14 E6). See {@code
 * TierACanonicalScenarioTest} for the reasons in full.
 *
 * <h2>The network</h2>
 *
 * <p>While it is open, the module's {@link LoopbackGuard} lets through exactly the hosts the tools'
 * pinned release URLs are served from -- {@code github.com} and the {@code githubusercontent.com}
 * storage hosts -- as {@code UpstreamInstallUiTest} does; {@link #close()} puts the guard back.
 *
 * <h2>Files read outside this module</h2>
 *
 * <p>{@code manifests/tools.json}; through {@link RealSearch} the {@code D-006} local fixture under
 * {@code scratch/fixture} (both K562 mzML files and the UniProt human proteome, checksum verified);
 * through {@link ProjectXvfb} {@code tools/x11-bookworm-20260829/}, {@code
 * tools/fontstack-bookworm-20260829/} and {@code scripts/feasibility/gui-spike/xkbcomp-path-shim.c}
 * (compiled with the host's {@code gcc}).
 */
final class TierAApplication implements AutoCloseable {

    /** The hosts the tools' pinned release URLs are served from, and nothing else. */
    private static final String UPSTREAM_HOSTS = "github.com|*.githubusercontent.com";

    private final String guardedNonProxyHosts;

    private final ProjectXvfb display;

    private final Map<String, String> desktop;

    private final FreshHome home;

    private final ScriptedChooser chooser = new ScriptedChooser();

    private final ScenarioLayout layout;

    /** What the product's own {@link PdvDisplay#fromHost} answered, each time PDV asked. */
    private final List<Map<String, String>> productDisplay =
            Collections.synchronizedList(new ArrayList<>());

    private Application application;

    private TierAApplication(
            String guardedNonProxyHosts,
            ProjectXvfb display,
            FreshHome home,
            ScenarioLayout layout) {
        this.guardedNonProxyHosts = guardedNonProxyHosts;
        this.display = display;
        this.desktop = Map.of(PdvDisplay.DISPLAY, display.display());
        this.home = home;
        this.layout = layout;
    }

    /**
     * Widens the loopback guard to the upstream hosts, starts the project-local Xvfb, makes a fresh
     * home with a space and a non-ASCII segment, and stages the {@code D-006} inputs beside it.
     * Nothing is launched yet.
     *
     * @param parent a directory of the test's own
     * @return the composition, not yet started
     * @throws IOException if a directory, the display or a fixture cannot be made
     */
    static TierAApplication prepare(Path parent) throws IOException {
        assertTrue(
                LoopbackGuard.installed(), "the loopback guard is in place before it is widened");
        String guarded = System.getProperty("http.nonProxyHosts");
        System.setProperty("http.nonProxyHosts", guarded + "|" + UPSTREAM_HOSTS);
        ProjectXvfb display = null;
        try {
            Path root = parent.toRealPath();
            display = ProjectXvfb.start(root.resolve("xvfb"));
            FreshHome home = FreshHome.create(root, FreshHome.Naming.SPACE_AND_NON_ASCII);
            Path inputs = Files.createDirectories(home.root().resolve("inputs"));
            List<Path> spectra = RealSearch.spectra(inputs);
            Path database = RealSearch.proteome(inputs.resolve("UP000005640_9606.fasta"));
            ScenarioLayout layout =
                    ScenarioLayout.defaultsUnder(
                            home,
                            inputs,
                            spectra,
                            database,
                            RealSearch.repositoryRoot().resolve("manifests/tools.json"),
                            Files.createDirectories(root.resolve("oracle-work")));
            return new TierAApplication(guarded, display, home, layout);
        } catch (IOException | RuntimeException | Error failed) {
            System.setProperty("http.nonProxyHosts", guarded);
            if (display != null) {
                display.close();
            }
            throw failed;
        }
    }

    /**
     * Where the scenario's files are.
     *
     * @return the layout over the fresh home
     */
    ScenarioLayout layout() {
        return layout;
    }

    /**
     * The scenario's answer to the file dialogs, over the chooser the application is built with.
     *
     * @return the chooser script
     */
    ChooserScript chooserScript() {
        return new ScriptedChooserScript(chooser);
    }

    /**
     * The display the application and PDV draw on.
     *
     * @return its {@code DISPLAY} value
     */
    String display() {
        return display.display();
    }

    /**
     * What the product's own {@link PdvDisplay#fromHost} answered, each time PDV asked.
     *
     * @return the answers so far
     */
    List<Map<String, String>> productDisplay() {
        synchronized (productDisplay) {
            return List.copyOf(productDisplay);
        }
    }

    /**
     * Starts the real application over a new production composition root for the fresh home: the
     * first start, or a relaunch after {@link #stopApplication()} (P14-4: a new {@code
     * CometGuiApplication} over a new composition root, from the same home, in the same JVM).
     *
     * @return a driver of the running application
     * @throws TimeoutException if the application does not start
     */
    RemotableUiDriver start() throws TimeoutException {
        ApplicationServices services = home.services(desktop);
        RunWiring.Setup production = RunWiring.Setup.forThisApplication(services);
        RunWiring.Setup setup =
                new RunWiring.Setup(
                        production.tools(), production.projectDirectory(), this::pdvEnvironment);
        Stage primary = FxToolkit.registerPrimaryStage();
        application =
                FxToolkit.setupApplication(
                        () ->
                                new CometGuiApplication(
                                        services,
                                        new BoundedMessageLog(),
                                        BuildIdentityResource::load,
                                        owner -> chooser,
                                        setup));
        return new RobotFxUiDriver(RunningApplication.showing(primary));
    }

    /**
     * Stops the running application through its own {@code stop()}, releasing the project lock.
     *
     * @throws TimeoutException if it does not stop
     */
    void stopApplication() throws TimeoutException {
        if (application != null) {
            Application stopping = application;
            application = null;
            FxToolkit.cleanupApplication(stopping);
        }
    }

    /**
     * What PDV is given to reach a display: the product's own {@link PdvDisplay#fromHost} over the
     * application's environment and, beside it, the project-local X11 and font closures' library
     * and fontconfig locations ({@link ProjectXvfb#clientEnvironment()}), which a real Linux
     * desktop has in its system library directories and this build host does not have at all.
     * Nothing here replaces a variable the product chose.
     */
    private Map<String, String> pdvEnvironment(EnvironmentReader host) {
        Map<String, String> chosen = PdvDisplay.fromHost(host);
        productDisplay.add(chosen);
        Map<String, String> environment = new LinkedHashMap<>(display.clientEnvironment());
        environment.remove(PdvDisplay.DISPLAY);
        environment.putAll(chosen);
        return Map.copyOf(environment);
    }

    /**
     * Stops the application if it runs, puts the loopback guard back and stops the display.
     *
     * @throws TimeoutException if the application does not stop
     */
    @Override
    public void close() throws TimeoutException {
        try {
            stopApplication();
        } finally {
            System.setProperty("http.nonProxyHosts", guardedNonProxyHosts);
            display.close();
        }
    }
}
