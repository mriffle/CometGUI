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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.cometgui.domain.ports.FileHashes;
import org.cometgui.domain.tools.HostPlatform;
import org.cometgui.domain.tools.ToolName;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.install.cache.FixupReport;
import org.cometgui.install.cache.PlatformFixups;
import org.cometgui.install.registry.ArtefactManifest;
import org.cometgui.install.registry.ArtefactManifestReader;
import org.cometgui.install.registry.ArtefactRecord;
import org.cometgui.install.registry.ArtefactSelection;
import org.cometgui.provenance.hashing.StreamingHashService;
import org.cometgui.tools.comet.CometBanner;

/**
 * The half of the macOS Gatekeeper check that has to be PRODUCT code, exposed to a shell driver.
 *
 * <h2>Why this file exists</h2>
 *
 * <p>{@code scripts/ci/macos-gatekeeper-verify.sh} answers one question: on a Mac, does a managed
 * tool carrying {@code com.apple.quarantine} get refused, does CometGUI's own fix-up remove the
 * attribute, and does the binary then run? Three of those four steps are things only the product
 * may do, because a check that used {@code xattr -d} to clear the attribute would prove that
 * <em>Apple's</em> tool works and nothing whatever about CometGUI. So every decision below is taken
 * by code compiled from the {@code cometgui-} modules' main sources in this checkout:
 *
 * <ul>
 *   <li>which platform this machine is -- {@link HostPlatform#of(String, String)};
 *   <li>which artefact this machine gets -- {@link ArtefactManifest#select(HostPlatform, ToolName)}
 *       over {@code manifests/tools.json};
 *   <li>whether the downloaded bytes are the pinned bytes -- {@link StreamingHashService};
 *   <li>whether the quarantine attribute is removed -- {@link PlatformFixups}, the class {@code
 *       R-PLAT-04} names, invoked exactly as the install pipeline's step 5 invokes it;
 *   <li>whether Comet's own code was reached -- {@link CometBanner}.
 * </ul>
 *
 * <p>The shell driver contributes what only a shell can: {@code /usr/bin/xattr}, which is an
 * INDEPENDENT witness to what Java's {@link UserDefinedFileAttributeView} claims, and the raw exit
 * status and signal of the binary under test.
 *
 * <h2>What this file is NOT</h2>
 *
 * <p>It is not the installer. {@code ArtefactInstaller} runs eight steps; this runs step 5 on a
 * directory the driver filled. The reason is the ordering the check needs: the attribute has to be
 * present, and seen to bite, BEFORE the fix-up runs, and a whole-pipeline install would have
 * cleared it in the same breath as creating the directory. The driver says so in its transcript.
 *
 * <h2>The host override, and why it cannot launder a result</h2>
 *
 * <p>{@code --os-name} and {@code --os-arch} supply the two strings {@link HostPlatform#of} parses,
 * instead of reading them from this JVM. They exist so the Linux self-test can exercise the macOS
 * BRANCH of the product code, which is what makes that self-test worth anything. They do not bypass
 * product code -- the product still does the parsing and still decides -- and every command prints
 * {@code probe.host.source=supplied} when they are used, {@code system-properties} when they are
 * not. The driver refuses to reach a verdict about a Mac on any transcript that does not say {@code
 * system-properties}, and its self-test has a control for exactly that.
 *
 * <p>Output is {@code key=value}, one per line, on standard output; diagnostics go to standard
 * error. Exit 0 means the command did what it was asked; 1 means it ran and the answer was no (a
 * checksum that did not match); 2 is misuse; 3 is a failure of this harness.
 */
public final class MacosGatekeeperProbe {

    /** Everything this program prints is prefixed, so a driver can grep a mixed log. */
    private static final String P = "probe.";

    private static final int OK = 0;
    private static final int NO = 1;
    private static final int MISUSE = 2;
    private static final int FAILED = 3;

    private MacosGatekeeperProbe() {}

    /**
     * Runs one command.
     *
     * @param argv the command and its options
     */
    public static void main(String[] argv) {
        int status;
        try {
            status = run(argv);
        } catch (Misuse misuse) {
            System.err.println("MacosGatekeeperProbe: " + misuse.getMessage());
            status = MISUSE;
        } catch (IOException | RuntimeException failure) {
            System.err.println("MacosGatekeeperProbe: " + failure);
            failure.printStackTrace(System.err);
            status = FAILED;
        }
        System.out.flush();
        System.exit(status);
    }

    private static int run(String[] argv) throws IOException {
        if (argv.length == 0) {
            throw new Misuse("no command. Try: host | select | verify | attrs | set-attr | fixup"
                    + " | banner");
        }
        Options options = Options.parse(argv);
        switch (options.command) {
            case "host":
                return host(options);
            case "select":
                return select(options);
            case "verify":
                return verify(options);
            case "attrs":
                return attrs(options);
            case "set-attr":
                return setAttribute(options);
            case "fixup":
                return fixup(options);
            case "banner":
                return banner(options);
            default:
                throw new Misuse("unknown command: " + options.command);
        }
    }

    // ---------------------------------------------------------------- host --

    private static int host(Options options) {
        printHost(options);
        return options.platform().isPresent() ? OK : NO;
    }

    private static void printHost(Options options) {
        print("host.os.name", options.osName);
        print("host.os.arch", options.osArch);
        print("host.source", options.supplied ? "supplied" : "system-properties");
        print("host.java.version", System.getProperty("java.version", "?"));
        print("host.java.vendor", System.getProperty("java.vendor", "?"));
        Optional<HostPlatform> platform = options.platform();
        if (platform.isPresent()) {
            print("host.platform", platform.get().id());
            print("host.operatingSystem", platform.get().operatingSystem().id());
            print("host.architecture", platform.get().architecture().id());
        } else {
            // HostPlatform.of returned empty: the product does not recognise this machine.
            print("host.platform", "unrecognised");
        }
    }

    // -------------------------------------------------------------- select --

    private static int select(Options options) throws IOException {
        printHost(options);
        HostPlatform platform = options.requirePlatform();
        ArtefactManifest manifest = ArtefactManifestReader.readFrom(options.requireManifest());
        print("select.manifest", options.requireManifest().toString());
        print("select.manifest.schemaVersion", Integer.toString(manifest.schemaVersion()));
        print("select.manifest.artefacts", Integer.toString(manifest.artefacts().size()));
        print("select.tool", options.requireTool().id());
        List<ArtefactSelection> offers = offers(manifest, platform, options);
        print("select.count", Integer.toString(offers.size()));
        for (int index = 0; index < offers.size(); index++) {
            describe("select." + index + ".", offers.get(index));
        }
        if (offers.isEmpty()) {
            System.err.println("MacosGatekeeperProbe: the manifest offers this host no "
                    + options.requireTool().id() + " it can run");
            return NO;
        }
        print("select.chosen", "0");
        return OK;
    }

    private static List<ArtefactSelection> offers(
            ArtefactManifest manifest, HostPlatform platform, Options options) {
        if (options.version == null) {
            return manifest.select(platform, options.requireTool());
        }
        return manifest.select(platform, options.requireTool(), ToolVersion.parse(options.version));
    }

    private static void describe(String prefix, ArtefactSelection selection) {
        ArtefactRecord record = selection.artefact();
        print(prefix + "tool", record.tool().id());
        print(prefix + "version", record.version().text());
        print(prefix + "releaseTag", record.releaseTag());
        print(prefix + "platform", record.platform().id());
        print(prefix + "kind", record.kind().id());
        print(prefix + "executability", selection.executability().name());
        print(prefix + "translated", Boolean.toString(selection.isTranslated()));
        print(prefix + "url", record.url().toString());
        print(prefix + "sizeBytes", Long.toString(record.sizeBytes()));
        print(prefix + "sha256", record.hashes().sha256());
        print(prefix + "md5", record.hashes().md5());
        print(prefix + "executable", Boolean.toString(record.executable()));
        print(prefix + "executablePath", record.executablePath());
        print(prefix + "singleMemberExtraction", Boolean.toString(record.isSingleMemberExtraction()));
        print(prefix + "minimumMacos",
                record.minimumHostRequirements().minimumMacOsVersion().orElse("none"));
    }

    // -------------------------------------------------------------- verify --

    /*
     * The project's own hasher, on the bytes that were just downloaded, against the digests the
     * manifest pins.  Not `shasum`: the question is whether CometGUI would have accepted this file.
     */
    private static int verify(Options options) throws IOException {
        printHost(options);
        HostPlatform platform = options.requirePlatform();
        ArtefactManifest manifest = ArtefactManifestReader.readFrom(options.requireManifest());
        List<ArtefactSelection> offers = offers(manifest, platform, options);
        if (offers.isEmpty()) {
            System.err.println("MacosGatekeeperProbe: nothing to verify against; the manifest "
                    + "offers this host no " + options.requireTool().id());
            return NO;
        }
        ArtefactRecord record = offers.get(0).artefact();
        Path file = options.requireFile();
        long observedSize = Files.size(file);
        FileHashes observed = new StreamingHashService().hash(file);
        FileHashes expected = record.hashes();
        print("verify.file", file.toString());
        print("verify.expected.sizeBytes", Long.toString(record.sizeBytes()));
        print("verify.observed.sizeBytes", Long.toString(observedSize));
        print("verify.expected.sha256", expected.sha256());
        print("verify.observed.sha256", observed.sha256());
        print("verify.expected.md5", expected.md5());
        print("verify.observed.md5", observed.md5());
        boolean match = expected.sha256().equalsIgnoreCase(observed.sha256())
                && expected.md5().equalsIgnoreCase(observed.md5())
                && record.sizeBytes() == observedSize;
        print("verify.match", Boolean.toString(match));
        if (!match) {
            System.err.println("MacosGatekeeperProbe: the downloaded bytes are not the bytes "
                    + "manifests/tools.json pins for " + record.tool().id() + " "
                    + record.version().text() + " on " + record.platform().id());
            return NO;
        }
        return OK;
    }

    // --------------------------------------------------------------- attrs --

    /*
     * WHAT JAVA CAN SEE.  On macOS this is the interesting half of the question: the product
     * removes the attribute through UserDefinedFileAttributeView, and whether that view reaches
     * macOS's own extended-attribute namespace at all has never been observed by this project.
     * Printing the names Java lists, beside what /usr/bin/xattr lists, is how the driver finds out.
     */
    private static int attrs(Options options) throws IOException {
        Path file = options.requireFile();
        print("attrs.file", file.toString());
        UserDefinedFileAttributeView view = Files.getFileAttributeView(
                file, UserDefinedFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            print("attrs.view", "absent");
            print("attrs.quarantine.present", "false");
            return OK;
        }
        print("attrs.view", "present");
        List<String> names = view.list();
        print("attrs.count", Integer.toString(names.size()));
        print("attrs.names", String.join(",", names));
        boolean present = names.contains(PlatformFixups.QUARANTINE_ATTRIBUTE);
        print("attrs.quarantine.name", PlatformFixups.QUARANTINE_ATTRIBUTE);
        print("attrs.quarantine.present", Boolean.toString(present));
        if (present) {
            int size = view.size(PlatformFixups.QUARANTINE_ATTRIBUTE);
            ByteBuffer buffer = ByteBuffer.allocate(size);
            view.read(PlatformFixups.QUARANTINE_ATTRIBUTE, buffer);
            buffer.flip();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            print("attrs.quarantine.sizeBytes", Integer.toString(size));
            print("attrs.quarantine.value", printable(bytes));
            print("attrs.quarantine.hex", hex(bytes));
        }
        return OK;
    }

    /*
     * SELF-TEST ONLY, and the driver never calls it on macOS: there /usr/bin/xattr writes the
     * attribute, so the writer and the reader are different programs.  On Linux there is no xattr
     * command on this project's host, so the self-test writes through the same API the product
     * reads -- which is a weaker witness, and the driver's transcript says so in those words.
     */
    private static int setAttribute(Options options) throws IOException {
        Path file = options.requireFile();
        UserDefinedFileAttributeView view = Files.getFileAttributeView(
                file, UserDefinedFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            System.err.println("MacosGatekeeperProbe: this file system publishes no "
                    + "user-defined attribute view, so the attribute cannot be set: " + file);
            return FAILED;
        }
        String value = options.value == null ? "0081;00000000;CometGUI-self-test;" : options.value;
        view.write(PlatformFixups.QUARANTINE_ATTRIBUTE,
                ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8)));
        print("setattr.file", file.toString());
        print("setattr.name", PlatformFixups.QUARANTINE_ATTRIBUTE);
        print("setattr.value", value);
        print("setattr.readBack", Boolean.toString(
                view.list().contains(PlatformFixups.QUARANTINE_ATTRIBUTE)));
        return view.list().contains(PlatformFixups.QUARANTINE_ATTRIBUTE) ? OK : FAILED;
    }

    // --------------------------------------------------------------- fixup --

    /*
     * THE STEP UNDER TEST.  PlatformFixups.forHost(host).apply(directory, record) is exactly what
     * ArtefactInstaller's step 5 does, on a record the product itself selected from the manifest.
     * The report is printed in full, because "the attribute is gone" and "the product removed it"
     * are different claims and only the second one is the one R-PLAT-04 makes.
     */
    private static int fixup(Options options) throws IOException {
        printHost(options);
        HostPlatform platform = options.requirePlatform();
        ArtefactManifest manifest = ArtefactManifestReader.readFrom(options.requireManifest());
        List<ArtefactSelection> offers = offers(manifest, platform, options);
        if (offers.isEmpty()) {
            System.err.println("MacosGatekeeperProbe: no artefact to fix up; the manifest offers "
                    + "this host no " + options.requireTool().id());
            return NO;
        }
        ArtefactRecord record = offers.get(0).artefact();
        Path directory = options.requireDirectory();
        PlatformFixups fixups = PlatformFixups.forHost(platform);
        print("fixup.class", fixups.getClass().getName());
        print("fixup.host", fixups.host().id());
        print("fixup.directory", directory.toString());
        print("fixup.record", record.tool().id() + " " + record.version().text() + " "
                + record.platform().id());
        print("fixup.record.executablePath", record.executablePath());
        FixupReport report = fixups.apply(directory, record);
        print("fixup.changedNothing", Boolean.toString(report.changedNothing()));
        print("fixup.madeExecutable.count", Integer.toString(report.madeExecutable().size()));
        for (int index = 0; index < report.madeExecutable().size(); index++) {
            print("fixup.madeExecutable." + index, report.madeExecutable().get(index));
        }
        print("fixup.quarantineCleared.count",
                Integer.toString(report.quarantineCleared().size()));
        for (int index = 0; index < report.quarantineCleared().size(); index++) {
            print("fixup.quarantineCleared." + index, report.quarantineCleared().get(index));
        }
        return OK;
    }

    // -------------------------------------------------------------- banner --

    /*
     * "Did Comet's own code run?" is the product's question and the product's answer: CometBanner
     * is what CometCapabilityProbe uses to separate "it answered no" from "it never started", and
     * that is precisely the distinction a Gatekeeper refusal has to be told apart from.
     */
    private static int banner(Options options) throws IOException {
        Path file = options.requireFile();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        print("banner.file", file.toString());
        print("banner.lines", Integer.toString(lines.size()));
        print("banner.pattern", CometBanner.PATTERN.pattern());
        boolean present = CometBanner.isPresentIn(lines);
        print("banner.present", Boolean.toString(present));
        if (present) {
            for (String line : lines) {
                if (line != null && CometBanner.PATTERN.matcher(line).find()) {
                    print("banner.line", line.trim());
                    break;
                }
            }
        }
        return OK;
    }

    // ------------------------------------------------------------ plumbing --

    private static void print(String key, String value) {
        System.out.println(P + key + "=" + value);
    }

    private static String printable(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length);
        for (byte value : bytes) {
            char character = (char) (value & 0xFF);
            text.append(character >= 0x20 && character < 0x7F ? character : '.');
        }
        return text.toString();
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            text.append(String.format(Locale.ROOT, "%02x", value));
        }
        return text.toString();
    }

    /** A usage error, kept apart from a failure of the thing being probed. */
    private static final class Misuse extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Misuse(String message) {
            super(message);
        }
    }

    /** The parsed command line. */
    private static final class Options {

        private final String command;
        private final String osName;
        private final String osArch;
        private final boolean supplied;
        private final String manifest;
        private final String tool;
        private final String version;
        private final String file;
        private final String directory;
        private final String value;

        private Options(String command, String osName, String osArch, boolean supplied,
                String manifest, String tool, String version, String file, String directory,
                String value) {
            this.command = command;
            this.osName = osName;
            this.osArch = osArch;
            this.supplied = supplied;
            this.manifest = manifest;
            this.tool = tool;
            this.version = version;
            this.file = file;
            this.directory = directory;
            this.value = value;
        }

        static Options parse(String[] argv) {
            String command = argv[0];
            String osName = null;
            String osArch = null;
            String manifest = null;
            String tool = null;
            String version = null;
            String file = null;
            String directory = null;
            String value = null;
            List<String> rest = new ArrayList<>();
            for (int index = 1; index < argv.length; index++) {
                rest.add(argv[index]);
            }
            for (int index = 0; index < rest.size(); index++) {
                String flag = rest.get(index);
                if (!flag.startsWith("--")) {
                    throw new Misuse("unexpected argument: " + flag);
                }
                if (index + 1 >= rest.size()) {
                    throw new Misuse(flag + " needs a value");
                }
                String argument = rest.get(++index);
                switch (flag) {
                    case "--os-name": osName = argument; break;
                    case "--os-arch": osArch = argument; break;
                    case "--manifest": manifest = argument; break;
                    case "--tool": tool = argument; break;
                    case "--version": version = argument; break;
                    case "--file": file = argument; break;
                    case "--dir": directory = argument; break;
                    case "--value": value = argument; break;
                    default: throw new Misuse("unknown option: " + flag);
                }
            }
            boolean supplied = osName != null || osArch != null;
            if (supplied && (osName == null || osArch == null)) {
                throw new Misuse("--os-name and --os-arch are supplied together or not at all; a "
                        + "half-supplied host would mix this machine with another one");
            }
            if (!supplied) {
                osName = System.getProperty("os.name", "");
                osArch = System.getProperty("os.arch", "");
            }
            return new Options(command, osName, osArch, supplied, manifest, tool, version, file,
                    directory, value);
        }

        Optional<HostPlatform> platform() {
            return HostPlatform.of(osName, osArch);
        }

        HostPlatform requirePlatform() {
            return platform().orElseThrow(() -> new Misuse(
                    "the product does not recognise os.name=\"" + osName + "\" os.arch=\""
                            + osArch + "\" as a supported platform"));
        }

        Path requireManifest() {
            if (manifest == null) {
                throw new Misuse("--manifest is required for " + command);
            }
            return Paths.get(manifest);
        }

        ToolName requireTool() {
            if (tool == null) {
                throw new Misuse("--tool is required for " + command);
            }
            return ToolName.fromId(tool);
        }

        Path requireFile() {
            if (file == null) {
                throw new Misuse("--file is required for " + command);
            }
            return Paths.get(file);
        }

        Path requireDirectory() {
            if (directory == null) {
                throw new Misuse("--dir is required for " + command);
            }
            return Paths.get(directory);
        }
    }
}
