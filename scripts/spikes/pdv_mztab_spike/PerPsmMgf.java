/*
 * CometGUI -- Phase 11, work unit 2 (spike code for experiment R12; not product code).
 *
 * Runs the PRODUCT MGF writer, org.cometgui.tools.pdv.PdvMgfWriter.writeOnePerRow, unchanged, so
 * that the spike's R12 experiment proves what PDV does with the product's own output rather than
 * with a re-implementation of it. Compiled by pdv_mztab_spike.py against cometgui-tools'
 * and cometgui-domain's compiled classes (build them with mvn -o -pl cometgui-tools -am install).
 *
 *   java -cp <this>:<tools classes>:<domain classes> PerPsmMgf <source> <ids file> <dir> <name>
 *
 * <ids file> holds one native id per line, one line per mzTab PSM row, in row order. The source is
 * checked by the product's PdvSpectrumSupport for PDV 2.7.0 first, as the product path does. Prints
 * one line: "PERPSMMGF <entries> <file>".
 */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.cometgui.domain.tools.ToolVersion;
import org.cometgui.tools.pdv.MgfFile;
import org.cometgui.tools.pdv.PdvMgfWriter;
import org.cometgui.tools.pdv.PdvSpectrumSupport;
import org.cometgui.tools.pdv.SpectrumFileFormat;

public final class PerPsmMgf {

    private PerPsmMgf() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("usage: PerPsmMgf <source> <ids file> <directory> <file name>");
            System.exit(2);
        }
        Path source = Path.of(args[0]);
        SpectrumFileFormat format =
                PdvSpectrumSupport.forVersion(ToolVersion.parse("2.7.0")).check(source);
        List<String> rows = Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8);
        MgfFile written =
                PdvMgfWriter.writeOnePerRow(source, format, rows, Path.of(args[2]), args[3]);
        System.out.println("PERPSMMGF " + written.entries() + " " + written.file());
    }
}
