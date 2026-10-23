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

package org.cometgui.app.e2e.tierb;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.cometgui.app.e2e.bridge.BridgeProtocol.Request;
import org.cometgui.app.e2e.bridge.BridgeProtocol.Response;
import org.cometgui.app.e2e.bridge.BridgeProtocol.Status;
import org.cometgui.app.e2e.scenario.Screen;
import org.cometgui.app.e2e.scenario.ShippedManifest;
import org.cometgui.app.e2e.support.EndToEnd;
import org.cometgui.app.e2e.support.FreshHome;
import org.cometgui.app.testing.RealSearch;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.tools.api.ToolRunOutcome;
import org.cometgui.tools.api.ToolRunner;
import org.cometgui.tools.process.ProcessService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/**
 * The test-only loopback bridge proved against the installed artefact (Phase 14 unit 6; P14-11;
 * {@code R-TEST-06}): the exact archive {@code scripts/package/build-app-image.sh} built, installed
 * read-only outside the repository, launched unmodified with the bridge attached through {@code
 * JAVA_TOOL_OPTIONS}, driven, closed, and launched again over the same home.
 *
 * <p>What it shows, in order:
 *
 * <ol>
 *   <li>the bridge attaches, and the <em>launched process</em> reports a UTF-8 {@code
 *       sun.jnu.encoding} (P14-9), the installed runtime as its {@code java.home}, the installed
 *       launcher as its own path, and the fresh home;
 *   <li>the application is ready: the Tool Manager has read its list, which says the number of
 *       builds the shipped manifest names -- counted by the test's own reader;
 *   <li>remotable operations, each with a value: section navigation, texts, a disabled control,
 *       typing a value, a combo-box selection, and a driver failure arriving as the driver's own
 *       message;
 *   <li>a native dialog answered: a spectrum file under the home's space and accented letter is
 *       chosen through Add spectrum files... and listed; the product's chooser set the dialog's
 *       title and filters; an unanswered dialog is cancelled;
 *   <li>the refusals: no token, a wrong token, a client that does not speak the protocol, and a
 *       connection to the host's non-loopback address;
 *   <li>a child JVM -- the installed runtime's {@code java -jar} of the Limelight converter, with
 *       the same {@code JAVA_TOOL_OPTIONS} -- loads the agent's entry class and nothing else, and
 *       starts no bridge, while the application's own JVM loaded the server;
 *   <li>a clean stop: the window's close message ends the process with status 0, and it printed
 *       nothing but the JVM's own notice of the options and Prism's two no-GLX lines;
 *   <li>a real restart: a second process over the same home, attached again.
 * </ol>
 *
 * <p>Reads outside the module (opt-in only): {@code _build/package/linux} (the archive), {@code
 * manifests/tools.json}, {@code scratch/phase05/artefacts} (the pinned converter JAR), {@code
 * tools/x11-bookworm-20260829}, {@code tools/fontstack-bookworm-20260829} and {@code
 * scripts/feasibility/gui-spike/xkbcomp-path-shim.c} (the display), {@code scripts/package} and
 * {@code scripts/e2e/build-bridge.sh}.
 */
@EndToEnd
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BridgeEndToEndTest {

    /** Bounds on observable events: deadlock detectors, never timing assumptions. */
    private static final Duration START = Duration.ofMinutes(3);

    private static final Duration STOP = Duration.ofSeconds(60);

    private static final Duration READ = Duration.ofMinutes(2);

    private static final String NOT_READ_YET = "The tool list has not been read yet.";

    private static final String CONCATENATED_DECOYS =
            "Concatenated: targets and decoys compete, one result per spectrum";

    private static final List<String> DECOY_CHOICES =
            List.of(
                    "No internal decoys",
                    CONCATENATED_DECOYS,
                    "Separate: targets and decoys searched and reported separately");

    /** The pinned converter, as {@code scripts/package/smoke-installed.sh} uses it. */
    private static final String CONVERTER =
            "scratch/phase05/artefacts/v2.8.1__cometPercolator2LimelightXML.jar";

    private static final String CONVERTER_BANNER = "cometPercolator2LimelightXML.jar v2.8.1";

    /**
     * What a healthy start prints here: the JVM's notice, and Prism's two lines on a GLX-less X.
     */
    private static final Pattern ALLOWED_OUTPUT =
            Pattern.compile(
                    "Picked up JAVA_TOOL_OPTIONS: -javaagent:.*"
                            + "|ES2 Prism: Error - GLX extension is not supported"
                            + "|\\s*GLX version 1\\.3 or higher is required"
                            + "|\\s*");

    private Path work;

    private Path bridgeJar;

    private InstalledCometGui installed;

    private PackagedDisplay display;

    private FreshHome home;

    private Path spectrum;

    private LaunchedCometGui first;

    private LaunchedCometGui second;

    @BeforeAll
    void installAndLaunch(@TempDir Path scratch) throws IOException {
        work = scratch.toRealPath();
        assertFalse(
                work.toString().chars().anyMatch(Character::isWhitespace),
                "the work directory must have no whitespace (JAVA_TOOL_OPTIONS): " + work);
        Path archive = InstalledCometGui.builtArchive();
        bridgeJar = BridgeJar.build(work.resolve("bridge").resolve("cometgui-e2e-bridge.jar"));
        installed = InstalledCometGui.install(archive, work.resolve("install"));
        display = PackagedDisplay.start(work.resolve("display"));
        home =
                FreshHome.create(
                        Files.createDirectories(work.resolve("homes")),
                        FreshHome.Naming.SPACE_AND_NON_ASCII);
        Path inputs = Files.createDirectories(home.root().resolve("inputs"));
        spectrum = inputs.resolve("échantillon un.mzML");
        Files.writeString(spectrum, "<mzML/>\n", StandardCharsets.UTF_8);
        first = launch("first", List.of("-Xlog:class+load=info:file=" + classLog("first")));
    }

    @AfterAll
    void stopAndUninstall() throws IOException {
        List<Throwable> failures = new ArrayList<>();
        for (AutoCloseable resource : new AutoCloseable[] {first, second, display}) {
            if (resource != null) {
                try {
                    resource.close();
                } catch (Exception failed) {
                    failures.add(failed);
                }
            }
        }
        if (installed != null) {
            installed.uninstall();
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("cleaning up failed: " + failures, failures.get(0));
        }
    }

    private LaunchedCometGui launch(String name, List<String> jvmOptions) throws IOException {
        Path launch = work.resolve("launch-" + name);
        return LaunchedCometGui.start(
                installed,
                display,
                home.home(),
                launch.resolve("cwd"),
                bridgeJar,
                launch.resolve("bridge.port"),
                jvmOptions);
    }

    private Path classLog(String name) {
        return work.resolve("class-load-" + name + ".log");
    }

    @Test
    @Order(1)
    @DisplayName(
            "the bridge attaches to the unmodified installed launcher, which reports a UTF-8"
                    + " locale, the installed runtime and launcher, and the fresh home")
    void attaches() {
        assertEquals(LaunchedCometGui.WINDOW_TITLE, first.attach(START));
        first.locale().assertUtf8();
        Map<String, String> reported = first.environment();
        assertAll(
                "what the launched JVM reports about itself: " + reported,
                () -> assertEquals("UTF-8", reported.get("sun.jnu.encoding")),
                () -> assertEquals(installed.runtime().toString(), reported.get("java.home")),
                () ->
                        assertEquals(
                                installed.launcher().toString(), reported.get("jpackage.app-path")),
                () ->
                        assertEquals(
                                "org.cometgui.app.bootstrap.CometGuiLauncher",
                                reported.get("sun.java.command")),
                () ->
                        assertEquals(
                                Long.toString(first.endpoint().pid()),
                                reported.get("pid"),
                                "the port file's pid is the JVM's own"),
                () -> assertEquals(home.home().toString(), reported.get("HOME")),
                () -> assertEquals("C.UTF-8", reported.get("LC_ALL")),
                () -> assertEquals("/usr/bin:/bin", reported.get("PATH")),
                () -> assertFalse(reported.containsKey("JAVA_HOME"), "JAVA_HOME is unset"),
                () ->
                        assertEquals(
                                work.resolve("launch-first").resolve("cwd").toString(),
                                reported.get("user.dir")),
                () ->
                        assertFalse(
                                reported.get("JAVA_TOOL_OPTIONS")
                                        .contains(first.endpoint().token()),
                                "the token is not in the environment"),
                () ->
                        assertTrue(
                                reported.get("java.class.path")
                                        .startsWith(
                                                installed
                                                        .applicationDirectory()
                                                        .resolve("lib/app")
                                                        .toString()),
                                "the class path is the installed lib/app"));
    }

    @Test
    @Order(2)
    @DisplayName(
            "the application is ready: the Tool Manager lists the builds the shipped manifest"
                    + " names, counted by the test")
    void ready() throws IOException {
        Screen screen = new Screen(first.driver());
        screen.show("tool-manager");
        String summary =
                screen.await(
                        "tool-manager-summary",
                        "the Tool Manager to read its tool list",
                        said -> !NOT_READ_YET.equals(said),
                        READ);
        assertEquals(manifestSummary(), summary);
    }

    @Test
    @Order(3)
    @DisplayName(
            "remotable operations: navigation, texts, a disabled control, typing, a combo-box"
                    + " selection, and a driver failure with the driver's own message")
    void remotableOperations() {
        BridgedUiDriver ui = first.driver();
        Screen screen = new Screen(ui);
        screen.show("comet-parameters");
        ui.clickOn("param-mode-essentials");
        assertAll(
                "the Comet Parameters section, shown",
                () -> assertTrue(ui.isSelected("nav-comet-parameters")),
                () -> assertFalse(ui.isSelected("nav-tool-manager")),
                () -> assertTrue(ui.isVisible("ess-spectra-add")),
                () -> assertFalse(ui.isVisible("tool-manager-summary")),
                () -> assertTrue(ui.exists("tool-manager-summary"), "in the scene, not shown"),
                () -> assertFalse(ui.exists("no-such-control")),
                () -> assertEquals("No spectrum files chosen.", ui.textOf("ess-spectra-summary")),
                () -> assertEquals("Run state: Not started", ui.textOf("stage-stepper-run-state")),
                () -> assertTrue(ui.isDisabled("run-start"), "Run, with no Comet installed"),
                () -> assertFalse(ui.isDisabled("ess-spectra-add")),
                () -> assertEquals("-20.0", ui.textOf("ess-peptide_mass_tolerance_lower")),
                () -> assertEquals(DECOY_CHOICES, ui.itemsOf("ess-decoy_search")),
                () -> assertEquals("No internal decoys", ui.selectedTextOf("ess-decoy_search")));

        screen.enter("ess-peptide_mass_tolerance_lower", "-10.0");
        ui.select("ess-decoy_search", CONCATENATED_DECOYS);
        AssertionError refused =
                assertThrows(AssertionError.class, () -> ui.select("ess-decoy_search", "Nope"));
        assertAll(
                "what typing and choosing did",
                () -> assertEquals("-10.0", ui.textOf("ess-peptide_mass_tolerance_lower")),
                () ->
                        assertEquals(
                                "Value from: Set by you",
                                ui.textOf("ess-peptide_mass_tolerance_lower-origin")),
                () -> assertEquals(CONCATENATED_DECOYS, ui.selectedTextOf("ess-decoy_search")),
                () ->
                        assertEquals(
                                "#ess-decoy_search offers no item shown as \"Nope\"; it offers "
                                        + DECOY_CHOICES,
                                refused.getMessage()),
                () ->
                        assertEquals(
                                "no node with the stable identifier #no-such-control exists in the"
                                        + " running application (driver: JavaFX robot"
                                        + " (javafx.scene.robot.Robot, no TestFX))",
                                assertThrows(
                                                AssertionError.class,
                                                () -> ui.textOf("no-such-control"))
                                        .getMessage()));
    }

    @Test
    @Order(4)
    @DisplayName(
            "a native dialog answered: a spectrum file under the accented home is chosen and"
                    + " listed, the product set the dialog's title and filters, and an unanswered"
                    + " dialog is cancelled")
    void chooser() {
        BridgedUiDriver ui = first.driver();
        BridgedChooserScript chooser = first.chooser();
        new Screen(ui).show("comet-parameters");
        ui.clickOn("param-mode-essentials");
        String databaseBefore = ui.textOf("ess-database_name");

        chooser.nextSpectra(List.of(spectrum));
        ui.clickOn("ess-spectra-add");
        ui.clickOn("ess-database_name-choose");

        assertAll(
                "the dialogs",
                () -> assertEquals(List.of("spectra", "database"), chooser.asked()),
                () ->
                        assertEquals(
                                List.of(
                                        "openMultiple\tChoose spectrum files to search\t\t"
                                                + "Spectrum files (mzML, mzXML, MGF, MS2, Thermo"
                                                + " RAW)|All files",
                                        "open\tChoose the sequence database\t\t"
                                                + "FASTA files|Comet index files|All files"),
                                chooser.raw(),
                                "FxFileChooser's own titles and filters reached the dialog"),
                () ->
                        assertEquals(
                                List.of(
                                        "rewrote org.cometgui.app.config.FxFileChooser$1: [open,"
                                                + " openMultiple, save]"),
                                chooser.transformed()),
                () -> assertEquals("1 spectrum file chosen.", ui.textOf("ess-spectra-summary")),
                () -> assertEquals("Found and readable: " + spectrum, ui.textOf("ess-spectrum-0")),
                () -> assertFalse(ui.exists("ess-spectrum-1")),
                () ->
                        assertEquals(
                                databaseBefore,
                                ui.textOf("ess-database_name"),
                                "the cancelled dialog changed nothing"));
    }

    @Test
    @Order(5)
    @DisplayName(
            "refused: no token, a wrong token, a client that does not speak the protocol, and any"
                    + " connection to a non-loopback address")
    void refusals() throws IOException {
        BridgeEndpoint endpoint = first.endpoint();
        try (BridgeClient anonymous = BridgeClient.connect(endpoint)) {
            Response response =
                    anonymous.exchange(new Request("", "textOf", List.of("tool-manager-summary")));
            assertEquals(Status.REFUSED, response.status());
            assertEquals("refused: the request carries no token", response.message());
            assertTrue(anonymous.closedByTheBridge(), "and the connection is closed");
        }
        try (BridgeClient guessing = BridgeClient.connect(endpoint)) {
            Response response =
                    guessing.exchange(
                            new Request("00" + endpoint.token().substring(2), "tab", List.of()));
            assertEquals(Status.REFUSED, response.status());
            assertEquals("refused: the request's token is not this bridge's", response.message());
            assertTrue(guessing.closedByTheBridge());
        }
        try (Socket stranger =
                new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), endpoint.port())) {
            stranger.getOutputStream()
                    .write("GET / HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            stranger.setSoTimeout(30_000);
            assertEquals(-1, stranger.getInputStream().read(), "dropped without an answer");
        }
        List<InetAddress> outside = nonLoopbackAddresses();
        assertFalse(
                outside.isEmpty(),
                "this host has no non-loopback IPv4 address, so 'not reachable from outside' cannot"
                        + " be shown here");
        for (InetAddress address : outside) {
            try (Socket attempt = new Socket()) {
                assertThrows(
                        ConnectException.class,
                        () ->
                                attempt.connect(
                                        new InetSocketAddress(address, endpoint.port()), 5000),
                        "port " + endpoint.port() + " answered on " + address);
            }
        }
        assertEquals(
                LaunchedCometGui.WINDOW_TITLE,
                first.bridge().text("bridge.awaitApplication", 10),
                "and the test's own connection still works");
    }

    @Test
    @Order(6)
    @DisplayName(
            "a child JVM with the same JAVA_TOOL_OPTIONS (the installed runtime's java -jar of the"
                    + " converter) loads the agent's entry class only and starts no bridge")
    void childJvmIsUntouched() throws IOException {
        Path converter = RealSearch.repositoryRoot().resolve(CONVERTER);
        assertTrue(
                Files.isRegularFile(converter), converter + " (the pinned converter) is missing");
        Path child = Files.createDirectories(work.resolve("child"));
        Path port = child.resolve("bridge.port");
        Path log = child.resolve("class-load.log");
        ToolRunOutcome outcome =
                new ToolRunner(new ProcessService(Clock.systemUTC()), Duration.ofMinutes(2))
                        .run(
                                new ToolCommand(
                                        List.of(
                                                installed.runtimeJava().toString(),
                                                "-jar",
                                                converter.toString(),
                                                "--version"),
                                        child,
                                        Map.of(
                                                "LANG",
                                                "C.UTF-8",
                                                "JAVA_TOOL_OPTIONS",
                                                "-javaagent:"
                                                        + bridgeJar
                                                        + "=port-file="
                                                        + port
                                                        + " -Xlog:class+load=info:file="
                                                        + log)));
        List<String> fromTheJar = classesFromTheBridgeJar(log);
        List<String> fromTheApplication = classesFromTheBridgeJar(classLog("first"));
        assertAll(
                "the child JVM: " + outcome.joinedOutput(),
                () -> assertTrue(outcome.exitedZero()),
                () -> assertTrue(outcome.joinedOutput().contains(CONVERTER_BANNER)),
                () ->
                        assertTrue(
                                outcome.standardError().stream()
                                        .anyMatch(l -> l.startsWith("Picked up JAVA_TOOL_OPTIONS")),
                                "the agent option did reach it"),
                () -> assertFalse(Files.exists(port), "no bridge started: no port file"),
                () ->
                        assertEquals(
                                List.of("org.cometgui.app.e2e.bridge.BridgeAgent"),
                                fromTheJar,
                                "only the entry class was loaded from the bridge jar"),
                () ->
                        assertTrue(
                                fromTheApplication.contains(
                                        "org.cometgui.app.e2e.bridge.BridgeServer"),
                                "while the application's JVM did load the server (the control): "
                                        + fromTheApplication));
    }

    @Test
    @Order(7)
    @DisplayName(
            "closing the window ends the process with status 0, and it printed nothing but the"
                    + " JVM's notice and Prism's no-GLX lines")
    void closesCleanly() throws IOException {
        long pid = first.endpoint().pid();
        first.closeWindow();
        int status = first.awaitExit(STOP);
        List<String> unexpected = new ArrayList<>();
        for (String line : first.output()) {
            if (!ALLOWED_OUTPUT.matcher(line).matches()) {
                unexpected.add(line);
            }
        }
        assertAll(
                "the first launch's end",
                () -> assertEquals(0, status, "exit status"),
                () -> assertTrue(ProcessHandle.of(pid).isEmpty(), "pid " + pid + " is reaped"),
                () -> assertEquals(List.of(), unexpected, "unexpected output"));
    }

    @Test
    @Order(8)
    @DisplayName("a real restart: a second process over the same home is attached to again")
    void restarts() throws IOException {
        long firstPid = first.endpoint().pid();
        LaunchedCometGui relaunched = launch("second", List.of());
        second = relaunched;
        assertEquals(LaunchedCometGui.WINDOW_TITLE, relaunched.attach(START));
        relaunched.locale().assertUtf8();
        Map<String, String> reported = relaunched.environment();
        Screen screen = new Screen(relaunched.driver());
        screen.show("tool-manager");
        String summary =
                screen.await(
                        "tool-manager-summary",
                        "the relaunched Tool Manager to read its tool list",
                        said -> !NOT_READ_YET.equals(said),
                        READ);
        assertAll(
                "the second process",
                () -> assertNotEquals(Long.toString(firstPid), reported.get("pid")),
                () -> assertEquals(Long.toString(relaunched.endpoint().pid()), reported.get("pid")),
                () -> assertEquals(home.home().toString(), reported.get("HOME")),
                () -> assertNotEquals(first.endpoint().token(), relaunched.endpoint().token()),
                () -> assertEquals(manifestSummary(), summary, "the same list, read again"),
                () -> assertEquals(List.of(), relaunched.chooser().asked(), "a new bridge"));
        relaunched.closeWindow();
        assertEquals(0, relaunched.awaitExit(STOP));
    }

    /**
     * What the Tool Manager's summary says on any host, from the test's own reader of the shipped
     * manifest: one build per tool and version it names.
     */
    private static String manifestSummary() throws IOException {
        return ShippedManifest.read(RealSearch.repositoryRoot().resolve("manifests/tools.json"))
                        .toolBuilds()
                + " tool builds on this host.";
    }

    private List<String> classesFromTheBridgeJar(Path log) throws IOException {
        assertNotNull(log);
        assertTrue(Files.isRegularFile(log), "no class-loading log " + log);
        List<String> classes = new ArrayList<>();
        String jar = bridgeJar.toString();
        for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
            int source = line.indexOf(" source: ");
            if (source > 0 && line.substring(source).contains(jar)) {
                String head = line.substring(0, source);
                classes.add(head.substring(head.lastIndexOf(' ') + 1));
            }
        }
        return classes;
    }

    private static List<InetAddress> nonLoopbackAddresses() throws SocketException {
        List<InetAddress> found = new ArrayList<>();
        for (NetworkInterface face : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!face.isUp() || face.isLoopback()) {
                continue;
            }
            for (InetAddress address : Collections.list(face.getInetAddresses())) {
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    found.add(address);
                }
            }
        }
        return found;
    }
}
