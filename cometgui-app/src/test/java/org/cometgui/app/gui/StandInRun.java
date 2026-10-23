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

package org.cometgui.app.gui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import org.cometgui.tools.percolator.SyntheticPin;

/**
 * Stand-in tools for the Run section's failure paths that happen while a run is under way (Phase 14
 * unit 8b): a Comet whose outputs pass every check the real workflow makes, so the run reaches
 * Percolator, and a Percolator that writes its files and then, if asked, keeps running.
 *
 * <p>POSIX shell scripts, run through the real process service, whose tool environment is exactly
 * {@code LANG=C.UTF-8} with no {@code PATH}: everything is a shell built-in except the one
 * long-running child, {@code /bin/sleep}, named by absolute path and bounded at {@value
 * #CHILD_SECONDS} seconds so a test killed midway cannot strand it for ever. A test never waits on
 * it -- it waits on the lines the stand-in prints.
 *
 * <ul>
 *   <li><strong>Comet</strong> reads its {@code -N} base and spectrum file, writes {@code
 *       <base>.pep.xml} naming them and {@code <base>.pin} -- CometGUI's own synthetic 64 + 64 PIN
 *       ({@link SyntheticPin}) with its decoy proteins under Comet's default prefix {@code DECOY_}
 *       -- and exits 0.
 *   <li><strong>Percolator</strong> writes a small table to every table option, a weights file and
 *       a pout XML, as the workflow module's {@code FakePercolator} does; if it {@code waits}, it
 *       then starts {@code /bin/sleep} in the background, prints {@code child <pid>} and {@code pid
 *       <pid>}, and exits with that child's status -- so only a cancellation, or a crash of the
 *       application, ends its stage.
 * </ul>
 *
 * <p>The same shape as {@code org.cometgui.workflow.steps.StandInTools}, written again here because
 * one module's test classes are not on another's class path.
 */
final class StandInRun {

    /** How long the waiting Percolator's child runs at most. A watchdog, never a wait. */
    static final int CHILD_SECONDS = 300;

    /** The release the stand-in Comet is offered as. */
    static final String COMET_RELEASE = "2026.03.0";

    /** The FASTA: two target records and no decoys -- Comet's own decoys are searched. */
    static final String FASTA = ">sp|P00001|ONE first\nPEPTIDEK\n>sp|P00002|TWO second\nSAMPLERK\n";

    private static final String TABLE =
            "PSMId\\tscore\\tq-value\\tposterior_error_prob\\tpeptide\\tproteinIds\\n"
                    + "p1\\t2.5\\t0.001\\t0.0001\\tK.PEPTIDEK.R\\tsp|P1|A\\n"
                    + "p2\\t1.0\\tNaN\\t0.5\\tK.KEDITPEP.R\\tsp|P2|B\\n";

    private static final String WEIGHTS =
            "# a stand-in weights file\\n"
                    + "feat1\\tfeat2\\tm0\\n0.1\\t0.2\\t-1.0\\n0.3\\t0.4\\t-2.0\\n"
                    + "feat1\\tfeat2\\tm0\\n0.15\\t0.25\\t-1.5\\n0.35\\t0.45\\t-2.5\\n";

    private static final String XML =
            "<?xml version=\"1.0\"?>\\n<percolator_output"
                    + " xmlns=\"http://per-colator.com/percolator_out/15\"><psms><psm/><psm/></psms>"
                    + "<peptides><peptide/></peptides></percolator_output>\\n";

    private StandInRun() {}

    /**
     * Writes the stand-in Comet.
     *
     * @param file where it goes
     * @return the executable
     */
    static Path comet(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("#!/bin/sh");
        lines.add("base=''");
        lines.add("input=''");
        lines.add("for argument in \"$@\"; do");
        lines.add("  case \"$argument\" in");
        lines.add("    -N*) base=\"${argument#-N}\" ;;");
        lines.add("    -*) : ;;");
        lines.add("    *) input=\"$argument\" ;;");
        lines.add("  esac");
        lines.add("done");
        lines.add("[ -n \"$base\" ] && [ -n \"$input\" ] || exit 9");
        lines.add("stem=\"${input%.*}\"");
        lines.add("extension=\".${input##*.}\"");
        lines.add(
                "printf '<?xml version=\"1.0\"?>\\n<msms_pipeline_analysis"
                        + " xmlns=\"http://regis-web.systemsbiology.net/pepXML\"><msms_run_summary"
                        + " base_name=\"%s\" raw_data=\"%s\"><search_summary base_name=\"%s\"/>"
                        + "</msms_run_summary></msms_pipeline_analysis>\\n'"
                        + " \"$stem\" \"$extension\" \"$base\" > \"$base.pep.xml\"");
        lines.add(
                "while IFS= read -r line; do printf '%s\\n' \"$line\"; done > \"$base.pin\""
                        + " <<'PIN'");
        lines.addAll(
                SyntheticPin.forCapabilityProbe()
                        .replace(SyntheticPin.DECOY_PROTEIN_PREFIX, "DECOY_")
                        .lines()
                        .toList());
        lines.add("PIN");
        lines.add("exit 0");
        return executable(file, lines);
    }

    /**
     * Writes the stand-in Percolator.
     *
     * @param file where it goes
     * @param waits whether, its files written, it keeps running until it is killed
     * @return the executable
     */
    static Path percolator(Path file, boolean waits) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("#!/bin/sh");
        lines.add("table='" + TABLE + "'");
        lines.add("weights='" + WEIGHTS + "'");
        lines.add("xml='" + XML + "'");
        lines.add("while [ $# -gt 1 ]; do");
        lines.add("  case \"$1\" in");
        lines.add("    --no-analytics) shift; continue ;;");
        lines.add(
                "    --results-psms|--results-peptides|--decoy-results-psms"
                        + "|--decoy-results-peptides) printf \"$table\" > \"$2\" ;;");
        lines.add("    --weights) printf \"$weights\" > \"$2\" ;;");
        lines.add("    -X) printf \"$xml\" > \"$2\" ;;");
        lines.add("  esac");
        lines.add("  shift 2");
        lines.add("done");
        if (waits) {
            lines.add("/bin/sleep " + CHILD_SECONDS + " &");
            lines.add("child=$!");
            lines.add("echo \"child $child\"");
            lines.add("echo \"pid $$\"");
            lines.add("wait \"$child\"");
            lines.add("exit $?");
        } else {
            lines.add("exit 0");
        }
        return executable(file, lines);
    }

    /**
     * Writes the search's inputs: one spectrum file (named and hashed, never parsed) and the FASTA.
     *
     * @param directory where they go, created
     * @return the spectrum file, then the FASTA
     */
    static List<Path> inputs(Path directory) throws IOException {
        Path inputs = Files.createDirectories(directory);
        Path spectrum =
                Files.writeString(inputs.resolve("a.mzML"), "spectra a", StandardCharsets.US_ASCII);
        Path fasta =
                Files.writeString(
                        inputs.resolve("targets.fasta"), FASTA, StandardCharsets.US_ASCII);
        return List.of(spectrum, fasta);
    }

    /**
     * Whether a process is still running. A process that has exited but was never reaped -- a
     * zombie, state {@code Z} in {@code /proc/<pid>/stat} -- is not: when a tool's shell and its
     * child die together the child is re-parented to process 1, and process 1 in this project's
     * containers reaps nothing, so {@link ProcessHandle#isAlive} reports the dead child alive for
     * ever (measured 2026-10-23: {@code (sleep) Z 1 ...}).
     *
     * @param pid the process
     * @return {@code true} if it exists and is not a zombie
     */
    static boolean running(long pid) {
        if (!ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
            return false;
        }
        Path stat = Path.of("/proc", Long.toString(pid), "stat");
        try {
            String text = Files.readString(stat, StandardCharsets.US_ASCII);
            String state = text.substring(text.lastIndexOf(')') + 1).trim();
            return !(state.startsWith("Z") || state.startsWith("X"));
        } catch (IOException gone) {
            return !Files.isDirectory(Path.of("/proc", "self"));
        }
    }

    /** Kills a process if it is still there; for clean-up only. */
    static void kill(long pid) {
        if (pid > 0) {
            ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
        }
    }

    private static Path executable(Path file, List<String> lines) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IOException("no directory to write " + file + " into");
        }
        Files.createDirectories(parent);
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.US_ASCII);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwx------"));
        return file;
    }
}
