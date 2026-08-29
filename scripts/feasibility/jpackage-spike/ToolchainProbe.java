/*
 * CometGUI -- Phase 00, work unit 5.
 *
 * THROWAWAY FEASIBILITY SPIKE. This is not product code and must not be
 * carried into the product: Phase 00 writes no product Java (see
 * phases/PHASE-00-feasibility.rst, "In scope" / "Out of scope"). Its only job
 * is to make the jpackage proof falsifiable -- a launcher that exits 0 proves
 * nothing, so this class asserts the runtime it is running on and exits
 * non-zero when the assertion fails.
 *
 * It reports, and checks, that:
 *   1. java.version is exactly the pinned toolchain version;
 *   2. the runtime is the pinned vendor's build (java.vendor);
 *   3. java.home is the runtime INSIDE the jpackage app-image, not an
 *      external JDK;
 *   4. the class that is running was loaded from a jar inside that same
 *      app-image (so the image is self-contained);
 *   5. the JavaFX modules are resolvable from that bundled runtime.
 */
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ToolchainProbe {

    /** Pinned by scripts/feasibility/install-toolchain.sh. */
    private static final String EXPECTED_JAVA_VERSION = "25.0.4.1";
    private static final String EXPECTED_VENDOR = "BellSoft";
    private static final String[] EXPECTED_JAVAFX_MODULES = {
        "javafx.base", "javafx.controls", "javafx.fxml", "javafx.graphics"
    };

    public static void main(String[] args) throws Exception {
        String wantVersion = args.length > 0 ? args[0] : EXPECTED_JAVA_VERSION;

        String javaVersion = System.getProperty("java.version");
        String javaHome = System.getProperty("java.home");
        String vendor = System.getProperty("java.vendor");
        String vendorVersion = String.valueOf(System.getProperty("java.vendor.version"));
        String runtimeName = System.getProperty("java.runtime.name");
        String vmName = System.getProperty("java.vm.name");

        Path home = Path.of(javaHome).toAbsolutePath().normalize();
        Path codeSource = Path.of(URI.create(ToolchainProbe.class.getProtectionDomain()
                .getCodeSource().getLocation().toString())).toAbsolutePath().normalize();

        // jpackage app-image layout on Linux:
        //   <AppDir>/bin/<launcher>
        //   <AppDir>/lib/app/<app jar>
        //   <AppDir>/lib/runtime/            <-- java.home
        boolean bundledLayout = home.getNameCount() >= 2
                && home.getFileName().toString().equals("runtime")
                && home.getParent().getFileName().toString().equals("lib");
        Path appRoot = bundledLayout ? home.getParent().getParent() : null;
        boolean selfContained = appRoot != null && codeSource.startsWith(appRoot);

        List<String> missingFx = new ArrayList<>();
        for (String m : EXPECTED_JAVAFX_MODULES) {
            if (ModuleLayer.boot().findModule(m).isEmpty()) {
                missingFx.add(m);
            }
        }

        System.out.println("=== CometGUI toolchain probe (throwaway Phase 00 spike) ===");
        System.out.println("java.version        = " + javaVersion);
        System.out.println("java.vendor         = " + vendor);
        System.out.println("java.vendor.version = " + vendorVersion);
        System.out.println("java.runtime.name   = " + runtimeName);
        System.out.println("java.vm.name        = " + vmName);
        System.out.println("java.home           = " + home);
        System.out.println("code source         = " + codeSource);
        System.out.println("app image root      = " + (appRoot == null ? "<not a jpackage app-image>" : appRoot));
        System.out.println("bundled layout      = " + bundledLayout);
        System.out.println("self contained      = " + selfContained);
        System.out.println("env JAVA_HOME       = " + String.valueOf(System.getenv("JAVA_HOME")));
        System.out.println("env PATH            = " + String.valueOf(System.getenv("PATH")));
        System.out.println("javafx modules      = " + (missingFx.isEmpty() ? "all present" : "MISSING " + missingFx));
        System.out.println("os                  = " + System.getProperty("os.name")
                + " " + System.getProperty("os.arch"));

        List<String> failures = new ArrayList<>();
        if (!wantVersion.equals(javaVersion)) {
            failures.add("java.version is '" + javaVersion + "', expected '" + wantVersion + "'");
        }
        if (!EXPECTED_VENDOR.equals(vendor)) {
            failures.add("java.vendor is '" + vendor + "', expected '" + EXPECTED_VENDOR
                    + "' (the pinned Liberica build reports its implementor as BellSoft;"
                    + " java.vendor.version is null on this build)");
        }
        if (!missingFx.isEmpty()) {
            failures.add("JavaFX modules missing from this runtime: " + missingFx);
        }
        boolean requireBundle = !"false".equals(System.getProperty("probe.requireBundle"));
        if (requireBundle && !bundledLayout) {
            failures.add("java.home " + home + " is not the runtime of a jpackage app-image");
        }
        if (requireBundle && !selfContained) {
            failures.add("code source " + codeSource + " is outside the app image " + appRoot);
        }

        if (failures.isEmpty()) {
            System.out.println("PROBE RESULT        = PASS");
            System.out.println("=== end probe ===");
        } else {
            System.out.println("PROBE RESULT        = FAIL");
            for (String f : failures) {
                System.out.println("  ! " + f);
            }
            System.out.println("=== end probe ===");
            System.exit(3);
        }
    }
}
