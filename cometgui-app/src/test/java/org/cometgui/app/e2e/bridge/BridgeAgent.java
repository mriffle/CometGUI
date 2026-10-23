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

package org.cometgui.app.e2e.bridge;

import java.lang.instrument.Instrumentation;

/**
 * The test bridge's entry point: {@code premain}, run by the JVM before {@code main} when it is
 * started with {@code -javaagent:cometgui-e2e-bridge.jar=port-file=/abs/path}. Tier B passes that
 * in {@code JAVA_TOOL_OPTIONS}, which the unmodified jpackage launcher's JVM reads (measured by
 * Phase 14 unit 5); nothing in the packaged artefact names it.
 *
 * <h2>It does nothing in any other JVM</h2>
 *
 * <p>An environment variable reaches every JVM started with that environment, and the product
 * starts JVMs of its own -- the Limelight converter and PDV, from the bundled runtime's {@code
 * bin/java}. The product's process service builds each child's environment rather than inheriting
 * it ({@code R-PROC-04}), so the variable should never get there; but the bridge does not rely on
 * that. The <strong>first</strong> thing {@link #premain} does is decide whether this JVM is the
 * packaged CometGUI launcher, from two system properties alone, and if it is not, it returns: no
 * transformer, no thread, no socket, no file, no output, and no other class of this package loaded.
 * The end-to-end test proves that by running the installed runtime's {@code java -jar} of the
 * converter with the same {@code JAVA_TOOL_OPTIONS} and reading its class-loading log.
 *
 * <p>"The packaged CometGUI launcher" is: {@code sun.java.command} names {@value #MAIN_CLASS} as
 * its first word, and {@code jpackage.app-path} -- which the jpackage launcher sets, and nothing
 * else does -- names an executable called {@code CometGUI}. Measured on the installed image on
 * 2026-10-23: {@code sun.java.command=org.cometgui.app.bootstrap.CometGuiLauncher} and {@code
 * jpackage.app-path=<install>/CometGUI/bin/CometGUI}; for {@code java -jar converter.jar
 * --version}, {@code sun.java.command=converter.jar --version} and no {@code jpackage.app-path}.
 */
public final class BridgeAgent {

    /**
     * The packaged application's main class, as {@code packaging/jpackage/common.args} names it.
     */
    static final String MAIN_CLASS = "org.cometgui.app.bootstrap.CometGuiLauncher";

    /** The launcher executable's name, as {@code --name} in the same file gives it. */
    static final String LAUNCHER_NAME = "CometGUI";

    /** The prefix of everything the bridge prints, which is only ever why it did not start. */
    static final String SAYS = "cometgui-e2e-bridge: ";

    private BridgeAgent() {}

    /**
     * Starts the bridge, if and only if this JVM is the packaged CometGUI launcher.
     *
     * @param arguments what followed {@code =} in the {@code -javaagent} option: {@code
     *     port-file=/absolute/path}, optionally {@code ,bind=127.0.0.1}
     * @param instrumentation the JVM's instrumentation, through which the native file dialog is
     *     replaced
     */
    public static void premain(String arguments, Instrumentation instrumentation) {
        if (!isCometGuiLauncher(
                System.getProperty("sun.java.command"), System.getProperty("jpackage.app-path"))) {
            return;
        }
        // One static call whose signature names only JDK types: verifying this method therefore
        // loads no other class of the bridge, and in any other JVM none is ever loaded.
        BridgeServer.startInThisJvm(arguments, instrumentation);
    }

    /**
     * Whether the JVM these two properties describe is the packaged CometGUI launcher.
     *
     * <p>Deliberately nothing but string operations on its two arguments, so that deciding loads no
     * class and touches nothing.
     *
     * @param javaCommand {@code sun.java.command}: the main class or JAR, then the arguments
     * @param appPath {@code jpackage.app-path}: the native launcher's own path, or {@code null}
     * @return {@code true} only if both say CometGUI's packaged launcher
     */
    static boolean isCometGuiLauncher(String javaCommand, String appPath) {
        if (javaCommand == null || appPath == null) {
            return false;
        }
        String command = javaCommand.strip();
        int space = command.indexOf(' ');
        String first = space < 0 ? command : command.substring(0, space);
        if (!MAIN_CLASS.equals(first)) {
            return false;
        }
        int slash = Math.max(appPath.lastIndexOf('/'), appPath.lastIndexOf('\\'));
        String name = appPath.substring(slash + 1);
        return name.equals(LAUNCHER_NAME) || (LAUNCHER_NAME + ".exe").equals(name);
    }
}
