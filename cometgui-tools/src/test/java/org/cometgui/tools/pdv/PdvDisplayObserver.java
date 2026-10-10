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

package org.cometgui.tools.pdv;

import java.awt.Frame;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

/**
 * TEST-SIDE ONLY: the main class of a JVM that runs PDV 2.7.0's own, unmodified {@code
 * PDVGUI.gui.PDVMainClass.main} with the arguments it was given -- the {@code denovo-gui} command
 * line -- and reports on standard output, one line per redraw, which spectrum PDV handed its plot
 * (design decision P11-16). After the spike's {@code
 * scripts/spikes/pdv_mztab_spike/PdvObserver.java}.
 *
 * <p>PDV's control API cannot say what PDV displays, and screen-coordinate automation is forbidden,
 * so this reads PDV's own state by reflection when PDV fires the {@code border} property change
 * inside {@code updateSpectrum}: {@code PDVMainClass.selectedPsmKey} (the 0-based mzTab row),
 * {@code SpectrumMainPanel.currentSpectrum} (its precursor and every peak) and {@code
 * SpectrumMainPanel.spectrumIdentificationAssumption} (the peptide, its charge and modifications).
 * Nothing in PDV is patched, subclassed or clicked, and PDV is not on the class path this class is
 * compiled against: every access is reflective.
 *
 * <p>Each report is {@value #PREFIX} then tab-separated {@code key=value} fields: {@code row},
 * {@code sequence}, {@code charge}, {@code modifications} ({@code site:mass} joined by {@code ;}),
 * {@code precursor}, {@code peaks}, {@code peaksSha256} -- SHA-256 over every (m/z, intensity) pair
 * sorted by m/z then intensity, each value as its eight IEEE 754 bytes, big-endian -- and {@code
 * error} if anything could not be read.
 */
public final class PdvDisplayObserver {

    /** What every report line starts with. */
    public static final String PREFIX = "PDVOBS displayed";

    /** What the observer reports once it is listening. */
    public static final String ATTACHED = "PDVOBS attached";

    private static final long ATTACH_DEADLINE_NANOS = 180_000_000_000L;

    private PdvDisplayObserver() {}

    /**
     * Starts the observer, then PDV's own main with the given arguments.
     *
     * @param args PDV's command line
     * @throws ReflectiveOperationException if PDV's main cannot be found or throws
     */
    public static void main(String[] args) throws ReflectiveOperationException {
        Thread watcher = new Thread(PdvDisplayObserver::attach, "pdv-display-observer");
        watcher.setDaemon(true);
        watcher.start();
        Class<?> pdv = Class.forName("PDVGUI.gui.PDVMainClass");
        pdv.getMethod("main", String[].class).invoke(null, (Object) args);
    }

    private static void attach() {
        long deadline = System.nanoTime() + ATTACH_DEADLINE_NANOS;
        Object frame = null;
        JComponent panel = null;
        while (panel == null && System.nanoTime() < deadline) {
            for (Frame candidate : Frame.getFrames()) {
                if (candidate.getClass().getName().equals("PDVGUI.gui.PDVMainClass")) {
                    frame = candidate;
                }
            }
            if (frame != null) {
                try {
                    panel = (JComponent) field(frame, "spectrumShowJPanel");
                } catch (ReflectiveOperationException | RuntimeException failed) {
                    emit("PDVOBS error\t" + clean(String.valueOf(failed)));
                    return;
                }
            }
            if (panel == null) {
                LockSupport.parkNanos(50_000_000L);
            }
        }
        if (panel == null) {
            emit("PDVOBS error\tno PDVMainClass spectrum panel within 180 s");
            return;
        }
        Object pdv = frame;
        panel.addPropertyChangeListener(
                "border", change -> SwingUtilities.invokeLater(() -> emit(snapshot(pdv))));
        emit(ATTACHED);
    }

    private static String snapshot(Object pdv) {
        StringBuilder line = new StringBuilder(PREFIX);
        try {
            line.append("\trow=").append(clean(String.valueOf(field(pdv, "selectedPsmKey"))));
            Object border = ((JComponent) field(pdv, "spectrumShowJPanel")).getBorder();
            line.append("\tborder=")
                    .append(
                            clean(
                                    border instanceof TitledBorder titled
                                            ? titled.getTitle()
                                            : String.valueOf(border)));
            Object main = field(pdv, "spectrumMainPanel");
            Object spectrum = field(main, "currentSpectrum");
            if (spectrum != null) {
                Object precursor = call(spectrum, "getPrecursor");
                line.append("\tprecursor=")
                        .append(precursor == null ? "null" : call(precursor, "getMz"));
                List<double[]> pairs = new ArrayList<>();
                for (Object peak : (Collection<?>) call(spectrum, "getPeakList")) {
                    pairs.add(
                            new double[] {
                                peak.getClass().getField("mz").getDouble(peak),
                                peak.getClass().getField("intensity").getDouble(peak)
                            });
                }
                line.append("\tpeaks=").append(pairs.size());
                line.append("\tpeaksSha256=").append(digest(pairs));
            }
            Object assumption = field(main, "spectrumIdentificationAssumption");
            if (assumption != null && hasMethod(assumption, "getPeptide")) {
                Object peptide = call(assumption, "getPeptide");
                Object charge = call(assumption, "getIdentificationCharge");
                line.append("\tsequence=").append(clean((String) call(peptide, "getSequence")));
                line.append("\tcharge=").append(charge.getClass().getField("value").getInt(charge));
                Object factory =
                        Class.forName("com.compomics.util.experiment.biology.PTMFactory")
                                .getMethod("getInstance")
                                .invoke(null);
                List<String> modifications = new ArrayList<>();
                List<?> matches = (List<?>) call(peptide, "getModificationMatches");
                if (matches != null) {
                    for (Object match : matches) {
                        String name = (String) call(match, "getTheoreticPtm");
                        Object ptm =
                                factory.getClass()
                                        .getMethod("getPTM", String.class)
                                        .invoke(factory, name);
                        modifications.add(
                                call(match, "getModificationSite")
                                        + ":"
                                        + (ptm == null ? "null" : call(ptm, "getMass")));
                    }
                }
                line.append("\tmodifications=").append(String.join(";", modifications));
            }
        } catch (ReflectiveOperationException | RuntimeException failed) {
            line.append("\terror=").append(clean(String.valueOf(failed)));
        }
        return line.toString();
    }

    /**
     * The digest of a peak list, as {@link PdvDisplayObserver} reports it: every (m/z, intensity)
     * pair sorted by m/z then intensity, each value's eight IEEE 754 bytes big-endian, SHA-256.
     *
     * @param pairs the peaks; sorted in place
     * @return the lower-case hex digest
     */
    public static String digest(List<double[]> pairs) {
        pairs.sort(
                Comparator.<double[]>comparingDouble(pair -> pair[0])
                        .thenComparingDouble(pair -> pair[1]));
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            ByteBuffer buffer = ByteBuffer.allocate(16);
            for (double[] pair : pairs) {
                buffer.clear();
                buffer.putDouble(pair[0]).putDouble(pair[1]);
                sha.update(buffer.array());
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field found = type.getDeclaredField(name);
                found.setAccessible(true);
                return found.get(target);
            } catch (NoSuchFieldException notHere) {
                // the superclass, then
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static boolean hasMethod(Object target, String name) {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static String clean(String text) {
        return text == null ? "null" : text.replace('\t', ' ').replace('\n', ' ');
    }

    private static synchronized void emit(String line) {
        System.out.println(line);
        System.out.flush();
    }
}
