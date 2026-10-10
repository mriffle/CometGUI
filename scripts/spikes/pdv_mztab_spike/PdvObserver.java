/*
 * CometGUI -- Phase 11, work unit 1 (THROWAWAY spike code; not product code).
 *
 * A test-side observer that runs PDV 2.7.0's own, unmodified main class in this
 * JVM and reports -- on stdout, one line per change -- which spectrum PDV is
 * actually drawing.  PDV's control API cannot say this (see
 * docs/developer/pdv_mztab_spike.rst), so the spike reads PDV's own state by
 * reflection instead of looking at the screen:
 *
 *   PDVMainClass.spectrumMatch            the SpectrumMatch of the selected row
 *   PDVMainClass.selectedPsmKey           the row key (0-based mzTab data-row number)
 *   PDVMainClass.spectrumShowJPanel       its TitledBorder title
 *   SpectrumMainPanel.currentSpectrum     the MSnSpectrum handed to the plot (or null)
 *   SpectrumMainPanel.spectrumIdentificationAssumption
 *                                         the peptide, charge and modifications drawn
 *
 * It is event driven: it waits (with a deadline) for PDV's frame to exist, then
 * listens for the "border" property change PDV fires inside updateSpectrum() and
 * takes its snapshot on the EDT after that call has finished.
 *
 * It is compiled WITHOUT PDV on the classpath (PDV's lib/aspectjweaver-1.8.9.jar
 * has a corrupt central directory that javac refuses), so everything is
 * reflective.  No PDV class is modified, subclassed or patched.
 *
 * Also contains a java.net.spi.InetAddressResolverProvider that logs every host
 * name lookup the JVM performs (PDVOBS-DNS lines) before delegating to the
 * built-in resolver -- part of the spike's network/analytics check.
 */
import java.awt.Frame;
import java.beans.PropertyChangeEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

public final class PdvObserver {

    private PdvObserver() {
    }

    public static void main(String[] args) throws Exception {
        Thread watcher = new Thread(PdvObserver::attach, "pdv-observer");
        watcher.setDaemon(true);
        watcher.start();
        Class<?> pdvMain = Class.forName("PDVGUI.gui.PDVMainClass");
        pdvMain.getMethod("main", String[].class).invoke(null, (Object) args);
    }

    /**
     * Wait (deadline 180 s) for PDV's main frame AND its spectrum panel -- the frame object exists
     * before its constructor's initComponents() has built the panel -- then hook the border property.
     */
    private static void attach() {
        long deadline = System.nanoTime() + 180_000_000_000L;
        Object frame = null;
        JComponent found = null;
        while (found == null && System.nanoTime() < deadline) {
            for (Frame f : Frame.getFrames()) {
                if (f.getClass().getName().equals("PDVGUI.gui.PDVMainClass")) {
                    frame = f;
                }
            }
            if (frame != null) {
                try {
                    found = (JComponent) field(frame, "spectrumShowJPanel");
                } catch (ReflectiveOperationException | RuntimeException e) {
                    emit("{\"event\":\"observer-error\",\"reason\":" + q(String.valueOf(e)) + "}");
                    return;
                }
            }
            if (found == null) {
                java.util.concurrent.locks.LockSupport.parkNanos(50_000_000L);
            }
        }
        if (found == null) {
            emit("{\"event\":\"observer-error\",\"reason\":\"no PDVMainClass spectrum panel within 180 s\"}");
            return;
        }
        final Object pdv = frame;
        try {
            JComponent panel = found;
            panel.addPropertyChangeListener("border", (PropertyChangeEvent e) ->
                    // updateSpectrum() sets the border first and the spectrum after; snapshot once
                    // the whole EDT task has finished.
                    SwingUtilities.invokeLater(() -> emit(snapshot(pdv))));
            emit("{\"event\":\"observer-attached\"}");
        } catch (RuntimeException e) {
            emit("{\"event\":\"observer-error\",\"reason\":" + q(String.valueOf(e)) + "}");
        }
    }

    private static String snapshot(Object pdv) {
        StringBuilder sb = new StringBuilder("{\"event\":\"displayed\"");
        try {
            Object match = field(pdv, "spectrumMatch");
            sb.append(",\"matchKey\":").append(q(match == null ? null : (String) call(match, "getKey")));
            sb.append(",\"rowKey\":").append(q((String) field(pdv, "selectedPsmKey")));
            Object border = ((JComponent) field(pdv, "spectrumShowJPanel")).getBorder();
            sb.append(",\"border\":").append(q(border instanceof TitledBorder
                    ? ((TitledBorder) border).getTitle() : String.valueOf((Border) border)));

            Object mainPanel = field(pdv, "spectrumMainPanel");
            Object spectrum = field(mainPanel, "currentSpectrum");
            if (spectrum == null) {
                sb.append(",\"spectrum\":null");
            } else {
                Object precursor = call(spectrum, "getPrecursor");
                Collection<?> peaks = (Collection<?>) call(spectrum, "getPeakList");
                double total = 0;
                double maxIntensity = -1;
                double maxMz = -1;
                java.util.List<double[]> pairs = new java.util.ArrayList<>();
                for (Object p : peaks) {
                    double mz = p.getClass().getField("mz").getDouble(p);
                    double in = p.getClass().getField("intensity").getDouble(p);
                    pairs.add(new double[] {mz, in});
                    total += in;
                    if (in > maxIntensity) {
                        maxIntensity = in;
                        maxMz = mz;
                    }
                }
                sb.append(",\"spectrum\":{\"title\":").append(q((String) call(spectrum, "getSpectrumTitle")))
                        .append(",\"file\":").append(q((String) call(spectrum, "getFileName")))
                        .append(",\"precursorMz\":").append(precursor == null ? "null"
                                : String.valueOf(call(precursor, "getMz")))
                        .append(",\"peakCount\":").append(peaks.size())
                        .append(",\"totalIntensity\":").append(total)
                        .append(",\"basePeakMz\":").append(maxMz)
                        .append(",\"peaksSha256\":").append(q(peakDigest(pairs)))
                        .append("}");
            }

            Object assumption = field(mainPanel, "spectrumIdentificationAssumption");
            if (assumption == null || !hasMethod(assumption, "getPeptide")) {
                sb.append(",\"peptide\":null");
            } else {
                Object peptide = call(assumption, "getPeptide");
                Object charge = call(assumption, "getIdentificationCharge");
                Object ptmFactory = Class.forName("com.compomics.util.experiment.biology.PTMFactory")
                        .getMethod("getInstance").invoke(null);
                StringBuilder mods = new StringBuilder("[");
                List<?> matches = (List<?>) call(peptide, "getModificationMatches");
                if (matches != null) {
                    for (Object m : matches) {
                        String name = (String) call(m, "getTheoreticPtm");
                        Object ptm = ptmFactory.getClass().getMethod("getPTM", String.class).invoke(ptmFactory, name);
                        if (mods.length() > 1) {
                            mods.append(',');
                        }
                        mods.append("{\"name\":").append(q(name))
                                .append(",\"site\":").append(call(m, "getModificationSite"))
                                .append(",\"mass\":").append(ptm == null ? "null" : String.valueOf(call(ptm, "getMass")))
                                .append('}');
                    }
                }
                mods.append(']');
                sb.append(",\"peptide\":{\"sequence\":").append(q((String) call(peptide, "getSequence")))
                        .append(",\"charge\":").append(charge.getClass().getField("value").getInt(charge))
                        .append(",\"theoreticMz\":").append(call(assumption, "getTheoreticMz"))
                        .append(",\"modifications\":").append(mods)
                        .append('}');
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            sb.append(",\"observerError\":").append(q(String.valueOf(e)));
        }
        return sb.append('}').toString();
    }

    /**
     * Unit 2 (R12): an exact digest of the drawn peaks -- SHA-256 over every (m/z, intensity) pair,
     * sorted by m/z then intensity, each value as its 8 IEEE 754 bytes, big-endian. The spike
     * computes the same digest from the mzML's decoded doubles, so equality means PDV holds every
     * peak bit for bit.
     */
    private static String peakDigest(java.util.List<double[]> pairs) {
        pairs.sort((a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : Double.compare(a[1], b[1]));
        try {
            java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(16);
            for (double[] pair : pairs) {
                buffer.clear();
                buffer.putDouble(pair[0]).putDouble(pair[1]);
                sha.update(buffer.array());
            }
            return java.util.HexFormat.of().formatHex(sha.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            return "no-sha256";
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                // try the superclass
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static boolean hasMethod(Object target, String name) {
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        Method m = target.getClass().getMethod(name);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private static String q(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    static synchronized void emit(String json) {
        System.out.println("PDVOBS " + json);
        System.out.flush();
    }

    /** Logs every host-name lookup the JVM makes, then delegates to the built-in resolver. */
    public static final class LoggingResolverProvider extends InetAddressResolverProvider {
        @Override
        public InetAddressResolver get(Configuration configuration) {
            InetAddressResolver builtin = configuration.builtinResolver();
            return new InetAddressResolver() {
                @Override
                public Stream<InetAddress> lookupByName(String host, LookupPolicy policy) throws UnknownHostException {
                    StringBuilder who = new StringBuilder();
                    for (StackTraceElement e : new Throwable().getStackTrace()) {
                        String c = e.getClassName();
                        if (!c.startsWith("java.") && !c.startsWith("jdk.") && !c.startsWith("sun.")
                                && !c.startsWith("PdvObserver")) {
                            who.append(who.length() == 0 ? "" : " <- ").append(c).append('.').append(e.getMethodName());
                            if (who.length() > 300) {
                                break;
                            }
                        }
                    }
                    emit("{\"event\":\"dns-lookup\",\"host\":" + q(host) + ",\"caller\":" + q(who.toString()) + "}");
                    return builtin.lookupByName(host, policy);
                }

                @Override
                public String lookupByAddress(byte[] addr) throws UnknownHostException {
                    emit("{\"event\":\"dns-reverse\",\"bytes\":" + addr.length + "}");
                    return builtin.lookupByAddress(addr);
                }
            };
        }

        @Override
        public String name() {
            return "cometgui-spike-logging-resolver";
        }
    }
}
