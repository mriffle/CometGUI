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

package org.cometgui.workflow.pdv;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import javax.imageio.ImageIO;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.ports.ProcessListener;
import org.cometgui.domain.ports.ProcessRunner;
import org.cometgui.domain.ports.RunningProcess;
import org.cometgui.domain.ports.ToolCommand;
import org.cometgui.domain.project.ProjectId;
import org.cometgui.domain.run.ArchivedFile;
import org.cometgui.domain.run.DatabaseDelivery;
import org.cometgui.domain.run.IndexMode;
import org.cometgui.domain.run.RecordedInput;
import org.cometgui.domain.run.RunId;
import org.cometgui.domain.run.RunIdentity;
import org.cometgui.domain.run.RunLayout;
import org.cometgui.provenance.events.ProvenanceEvent;
import org.cometgui.provenance.events.ProvenanceEventLogReader;
import org.cometgui.provenance.events.ProvenanceEventType;

/**
 * What the PDV launch tests are built from, all of it made here: a JAR that is PDV by its manifest
 * and nothing else, a run directory with its recorded identity, an MGF whose titles carry scan
 * numbers, and a stand-in for PDV behind the process seam that writes what the real one writes.
 *
 * <p>Every expected value a test compares against is computed here by the test's own means --
 * {@link MessageDigest} for the JAR's checksums, {@link JarFile} for its manifest -- and never by
 * the classes under test.
 */
final class PdvFixtures {

    /** The PDV version the constructed JAR declares. */
    static final String VERSION = "2.7.0";

    /** The run identifier of a constructed run. */
    static final String RUN_ID = "pdv-test-run";

    /** A Thermo-style native id carrying scan 101. */
    static final String SCAN_101 = "controllerType=0 controllerNumber=1 scan=101";

    /** A Thermo-style native id carrying scan 102. */
    static final String SCAN_102 = "controllerType=0 controllerNumber=1 scan=102";

    private PdvFixtures() {}

    /**
     * A JAR whose manifest names it PDV of a version, with one class-file-shaped entry.
     *
     * @param file where to write it
     * @param title the {@code Implementation-Title}
     * @param version the {@code Implementation-Version}
     * @return the file
     * @throws IOException if it cannot be written
     */
    static Path jar(Path file, String title, String version) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_TITLE, title);
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, version);
        Files.createDirectories(Objects.requireNonNull(file.getParent(), "a JAR's directory"));
        try (OutputStream out = Files.newOutputStream(file);
                JarOutputStream jar = new JarOutputStream(out, manifest)) {
            jar.putNextEntry(new ZipEntry("PDVGUI/Stand.class"));
            jar.write(("stand-in for " + title + " " + version).getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return file;
    }

    /**
     * The {@code Implementation-Version} a JAR's manifest declares, read with {@link JarFile}.
     *
     * @param jar the JAR
     * @return the declared version
     * @throws IOException if it cannot be read
     */
    static String manifestVersion(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.getManifest()
                    .getMainAttributes()
                    .getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        }
    }

    /**
     * A file's digest, computed here.
     *
     * @param file the file
     * @param algorithm {@code SHA-256} or {@code MD5}
     * @return lower-case hex
     * @throws IOException if it cannot be read
     */
    static String digest(Path file, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException(algorithm, absent);
        }
    }

    /**
     * An MGF of MS2 spectra, one per native id, with two peaks each.
     *
     * @param file where to write it
     * @param titles each entry's title
     * @return the file
     * @throws IOException if it cannot be written
     */
    static Path mgf(Path file, String... titles) throws IOException {
        StringBuilder text = new StringBuilder();
        int number = 0;
        for (String title : titles) {
            number++;
            text.append("BEGIN IONS\n")
                    .append("TITLE=")
                    .append(title)
                    .append('\n')
                    .append("PEPMASS=500.25")
                    .append(number)
                    .append('\n')
                    .append("CHARGE=2+\n")
                    .append("RTINSECONDS=6")
                    .append(number)
                    .append(".5\n")
                    .append("100.125 10.5\n")
                    .append("200.25 20.25\n")
                    .append("END IONS\n");
        }
        Files.createDirectories(Objects.requireNonNull(file.getParent(), "an MGF's directory"));
        return Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
    }

    /**
     * A run directory with its provenance directory, as a completed run has.
     *
     * @param project the project directory
     * @param id the run's identifier
     * @return the run's layout
     * @throws IOException if it cannot be made
     */
    static RunLayout run(Path project, String id) throws IOException {
        RunLayout layout = new RunLayout(project.resolve("runs").resolve(id).toAbsolutePath());
        Files.createDirectories(layout.provenanceDirectory());
        Files.createDirectories(layout.cometOutputDirectory());
        return layout;
    }

    /**
     * A run's recorded identity over spectrum files.
     *
     * @param id the run's identifier
     * @param spectra its spectrum files
     * @return the identity, each file's base derived as a run derives it
     */
    static RunIdentity identity(String id, Path... spectra) {
        FileHashes zero = new FileHashes("0".repeat(32), "0".repeat(64));
        Instant created = Instant.parse("2026-10-10T08:00:00Z");
        List<RecordedInput> inputs = new ArrayList<>();
        for (Path spectrum : spectra) {
            inputs.add(new RecordedInput(spectrum, 1024, created, zero));
        }
        return new RunIdentity(
                new RunId(id),
                new ProjectId("cometgui-default"),
                created,
                "2026.03.0",
                RunIdentity.spectraOf(inputs),
                new RecordedInput(spectra[0].resolveSibling("db.fasta"), 10, created, zero),
                new ArchivedFile(RunLayout.cometParamsRelativePath(), 10, zero),
                IndexMode.NONE,
                DatabaseDelivery.PARAMETER_FILE);
    }

    /**
     * The {@code pdv.launched} events in a run's event log, read with the provenance reader.
     *
     * @param run the run
     * @return the events, oldest first; empty if there is no log
     * @throws IOException if it cannot be read
     */
    static List<ProvenanceEvent> launches(RunLayout run) throws IOException {
        if (!Files.exists(run.eventLogFile())) {
            return List.of();
        }
        return ProvenanceEventLogReader.recover(run.eventLogFile()).events().stream()
                .filter(event -> event.type() == ProvenanceEventType.PDV_LAUNCHED)
                .toList();
    }

    /**
     * The argument array an event records, rebuilt from its numbered keys.
     *
     * @param event the event
     * @return the array
     */
    static List<String> argv(ProvenanceEvent event) {
        Map<String, String> payload = event.payload();
        int count = Integer.parseInt(payload.get("argv.count"));
        List<String> argv = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            argv.add(payload.get("argv." + index));
        }
        return argv;
    }

    /** What the stand-in for PDV does when it is started. */
    enum Behaviour {
        /** Writes one annotated figure per requested id and exits 0. */
        DRAWS,
        /** Writes nothing and exits 1. */
        EXITS_NON_ZERO,
        /** Cannot be started at all. */
        CANNOT_START
    }

    /**
     * PDV behind the process seam: what the run's event log held at each start, and the figures the
     * real PDV would write. It is the recording process runner of gate item 4: every start it sees,
     * it checks against the event log as it stands at that moment.
     */
    static final class FakePdv implements ProcessRunner {

        private final RunLayout run;

        private final Behaviour behaviour;

        private final String peptide;

        private final List<ToolCommand> started = Collections.synchronizedList(new ArrayList<>());

        private final List<Integer> eventsAtStart = Collections.synchronizedList(new ArrayList<>());

        private final List<Boolean> lastEventWasThisCommand =
                Collections.synchronizedList(new ArrayList<>());

        FakePdv(RunLayout run, Behaviour behaviour, String peptide) {
            this.run = run;
            this.behaviour = behaviour;
            this.peptide = peptide;
        }

        @Override
        public RunningProcess start(ToolCommand command, ProcessListener listener)
                throws IOException {
            List<ProvenanceEvent> now = launches(run);
            started.add(command);
            eventsAtStart.add(now.size());
            lastEventWasThisCommand.add(
                    !now.isEmpty() && argv(now.get(now.size() - 1)).equals(command.argv()));
            if (behaviour == Behaviour.CANNOT_START) {
                throw new IOException("the stand-in for PDV cannot be started");
            }
            int exit = 1;
            if (behaviour == Behaviour.DRAWS) {
                Path figures = Path.of(after(command.argv(), "-o"));
                for (String id :
                        Files.readAllLines(
                                Path.of(after(command.argv(), "-i")), StandardCharsets.UTF_8)) {
                    if (!id.isBlank()) {
                        figure(figures.resolve(peptide + id + ".png"));
                    }
                }
                Files.writeString(figures.resolve("error.txt"), "", StandardCharsets.UTF_8);
                exit = 0;
            }
            listener.onExit(exit);
            int code = exit;
            return new RunningProcess() {
                @Override
                public boolean isAlive() {
                    return false;
                }

                @Override
                public int waitForExit() {
                    return code;
                }

                @Override
                public void requestCancellation() {
                    // already exited
                }
            };
        }

        List<ToolCommand> started() {
            return List.copyOf(started);
        }

        List<Integer> eventsAtStart() {
            return List.copyOf(eventsAtStart);
        }

        List<Boolean> lastEventWasThisCommand() {
            return List.copyOf(lastEventWasThisCommand);
        }

        private static String after(List<String> argv, String option) {
            int at = argv.indexOf(option);
            if (at < 0 || at + 1 >= argv.size()) {
                throw new IllegalStateException(option + " is not in " + argv);
            }
            return argv.get(at + 1);
        }

        /** An 800 x 400 truecolour PNG with a drawn spectrum's worth of ink on it. */
        private static void figure(Path file) throws IOException {
            BufferedImage image = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
            Graphics2D pen = image.createGraphics();
            try {
                pen.setColor(Color.WHITE);
                pen.fillRect(0, 0, 800, 400);
                pen.setColor(new Color(0xFF6600));
                for (int x = 40; x < 760; x += 12) {
                    pen.fillRect(x, 100, 3, 280);
                }
            } finally {
                pen.dispose();
            }
            if (!ImageIO.write(image, "png", file.toFile())) {
                throw new IOException("no PNG writer");
            }
        }
    }
}
