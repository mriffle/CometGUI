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

package org.cometgui.app.bootstrap;

import java.util.Objects;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.cometgui.app.config.ApplicationServices;
import org.cometgui.app.config.derived.AtlantaFxThemes;
import org.cometgui.domain.log.BoundedMessageLog;
import org.cometgui.domain.log.LogMessage;
import org.cometgui.domain.log.MessageSeverity;
import org.cometgui.domain.platform.GlibcVersion;
import org.cometgui.domain.platform.HostBaselineReport;
import org.cometgui.domain.platform.HostBaselineVerifier;
import org.cometgui.ui.view.ShellView;
import org.cometgui.ui.viewmodel.ConsoleViewModel;
import org.cometgui.ui.viewmodel.HostBaselineViewModel;
import org.cometgui.ui.viewmodel.NavigationViewModel;
import org.cometgui.ui.viewmodel.StageStepperViewModel;

/**
 * The running application: build the composition root, check the host, build the shell, show the
 * window.
 *
 * <h2>What happens, in order</h2>
 *
 * <ol>
 *   <li>The AtlantaFX theme is applied as the JavaFX user-agent stylesheet. Theming is optional --
 *       {@link AtlantaFxThemes#applyAsUserAgentStylesheet()} returns {@code false} and the
 *       application keeps JavaFX's default look if the library is absent.
 *   <li>{@link HostBaselineVerifier} is run ({@code R-PLAT-01}) and its report is turned into a
 *       {@link HostBaselineViewModel}.
 *   <li>The report is appended to the shared message log, so the console carries the same statement
 *       the banner does and a later provenance record can quote it.
 *   <li>The shell is built with that view-model and the three others, put into a {@link Scene},
 *       given to the primary {@link Stage}, and shown.
 * </ol>
 *
 * <p><strong>The order is the requirement, not an implementation detail.</strong> {@code R-PLAT-01}
 * says the baseline is verified <em>at startup</em> and reported to the user, so the banner is
 * populated before {@link Stage#show()} is called -- never filled in afterwards by a listener,
 * which would leave a window on screen that briefly says nothing about a host that cannot run
 * anything. {@code CometGuiApplicationBaselineTest} asserts this by reading the banner from a
 * listener on the stage's {@code showing} property.
 *
 * <h2>A blocking outcome is reported, and does not exit the JVM</h2>
 *
 * <p>{@link org.cometgui.domain.platform.HostBaselineOutcome#NOT_64_BIT} and {@link
 * org.cometgui.domain.platform.HostBaselineOutcome#GLIBC_TOO_OLD} are blocking: no managed tool can
 * run on such a host. <strong>This phase reports them and starts anyway.</strong> There is no
 * {@code Platform.exit()} and no {@code System.exit()} on this path, deliberately: a user whose
 * machine is unsupported should be able to read the diagnostic, look at the tool manager and the
 * settings, and copy the message -- not watch a window vanish. The phase that owns running a
 * workflow owns refusing to start one, which is where a blocking outcome has to bite.
 *
 * <p>Blocking and warning are distinguishable in <em>text</em>, not by colour alone: {@link
 * HostBaselineViewModel#bannerText()} begins with {@code "Cannot continue: "} for a blocking
 * outcome and {@code "Warning: "} for a warning, and the shell shows no banner at all for a
 * supported host. That is the specification's accessibility principle applied -- a screen reader
 * that never sees a border still reads the severity.
 *
 * <h2>The glibc floor checked at startup is the lowest one in the product</h2>
 *
 * <p>{@link HostBaselineVerifier} takes the required glibc version as an argument and its
 * documentation forbids it to grow a hard-coded floor, because the trustworthy statement about a
 * particular binary comes from executing it ({@code R-PLAT-02}, phase 05's runtime probe). At
 * startup no tool has been selected yet, so the only question that can honestly be asked is whether
 * this host is below the floor of <em>everything the product could ever offer</em>. That is {@link
 * #STARTUP_GLIBC_FLOOR}: {@code 2.14}, which specification.rst records as the requirement of
 * Percolator 3.06.5's portable Linux build and "the lowest floor found anywhere". A host below it
 * can load no managed tool at all. <strong>It is not a per-tool requirement and must never be used
 * as one</strong> -- a host that passes this check may still be too old for the tools the user goes
 * on to choose, and phase 05's probe is what settles that.
 *
 * <h2>No logic beyond wiring</h2>
 *
 * <p>There is no scientific logic here, no hashing, no download, no parsing and no {@code
 * ProcessBuilder}. Everything this class does is choose implementations and connect them, which is
 * what makes the shell testable without it.
 */
public final class CometGuiApplication extends Application {

    /** The window title, stable because tests and later phases identify the window by it. */
    public static final String WINDOW_TITLE = "CometGUI";

    /** The window's initial width in pixels. */
    public static final double INITIAL_WIDTH = 1280;

    /** The window's initial height in pixels. */
    public static final double INITIAL_HEIGHT = 800;

    /**
     * The glibc floor checked at startup: {@code 2.14}, the lowest requirement of any binary the
     * product attempts to manage. See this class's documentation for why it is this number and why
     * it is not a per-tool requirement.
     */
    public static final GlibcVersion STARTUP_GLIBC_FLOOR = GlibcVersion.of(2, 14, 0);

    /** The theme a started application applies. */
    public static final AtlantaFxThemes THEME = AtlantaFxThemes.defaultTheme();

    private final ApplicationServices services;

    private final BoundedMessageLog messageLog;

    /**
     * The constructor JavaFX itself calls: the real services for this host and a fresh run message
     * log.
     *
     * <p>{@link Application} is instantiated reflectively by the JavaFX launcher, which requires a
     * public no-argument constructor. {@link ApplicationServices#forThisHost()} does no I/O, so
     * constructing this class does none either.
     */
    public CometGuiApplication() {
        this(ApplicationServices.forThisHost(), new BoundedMessageLog());
    }

    /**
     * The application over a given composition root and a given run message log.
     *
     * <p>This is how a test starts the real application against a host it chose -- a 32-bit
     * machine, a musl one -- none of which exists in this project's environment, and how it reads
     * back what startup wrote to the console's log. Both are genuine parameters rather than holes
     * opened for a test: the composition root is exactly the thing that is meant to vary, and the
     * log is a collaborator shared with whatever writes to it (phase 03's process service, when it
     * arrives), which is why it is injected here rather than published from {@link
     * ApplicationServices}.
     *
     * @param services the wiring to run with
     * @param messageLog the bounded log the console shows
     * @throws NullPointerException if either argument is {@code null}
     */
    public CometGuiApplication(ApplicationServices services, BoundedMessageLog messageLog) {
        this.services = Objects.requireNonNull(services, "services");
        this.messageLog = Objects.requireNonNull(messageLog, "messageLog");
    }

    /**
     * Builds and shows the application window.
     *
     * <p>Called by the JavaFX launcher on the application thread. See this class's documentation
     * for the order of the four steps and why it is the order.
     *
     * @param primaryStage the stage JavaFX created for this application
     * @throws NullPointerException if {@code primaryStage} is {@code null}
     */
    @Override
    public void start(Stage primaryStage) {
        Objects.requireNonNull(primaryStage, "primaryStage");

        THEME.applyAsUserAgentStylesheet();

        HostBaselineReport baseline =
                new HostBaselineVerifier(services.environment(), services.glibcVersions())
                        .verify(STARTUP_GLIBC_FLOOR);
        HostBaselineViewModel hostBaseline = new HostBaselineViewModel(baseline);
        recordBaseline(hostBaseline);

        ShellView shell =
                new ShellView(
                        new NavigationViewModel(),
                        hostBaseline,
                        new StageStepperViewModel(),
                        new ConsoleViewModel(messageLog));

        primaryStage.setTitle(WINDOW_TITLE);
        primaryStage.setScene(new Scene(shell, INITIAL_WIDTH, INITIAL_HEIGHT));
        primaryStage.show();
    }

    /**
     * Puts the baseline statement into the shared message log, at a severity that matches the
     * banner: an error for a blocking outcome, a warning for a warning, information for a host that
     * is fine.
     *
     * @param hostBaseline the view-model built from the report
     */
    private void recordBaseline(HostBaselineViewModel hostBaseline) {
        MessageSeverity severity;
        if (hostBaseline.blocking()) {
            severity = MessageSeverity.ERROR;
        } else if (hostBaseline.bannerVisible()) {
            severity = MessageSeverity.WARNING;
        } else {
            severity = MessageSeverity.INFO;
        }
        messageLog.append(
                LogMessage.recordedBy(services.clock(), null, severity, hostBaseline.bannerText()));
    }
}
