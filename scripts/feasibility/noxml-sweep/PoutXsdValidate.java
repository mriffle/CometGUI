// Percolator pout-XML validator -- CometGUI Phase 00, work unit 10.
//
// Validates one or more Percolator output ("pout") XML documents against
// percolator_out.xsd using the JDK's own javax.xml.validation, with no
// third-party dependency and nothing installed on the host.
//
// Usage:  java PoutXsdValidate.java <percolator_out.xsd> <file.xml> [more.xml ...]
//
// Exit status is 0 only when EVERY document validated; 1 when any document
// failed.  A per-document verdict line is printed either way, because exit
// code 0 on its own proves nothing (ONBOARDING.rst, "Working conventions").
//
// External entity resolution is switched off: the schema and the instance
// documents are read from local files only.
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

public class PoutXsdValidate {

    /** Collects every error and fatal error rather than stopping at the first. */
    static final class Collector implements ErrorHandler {
        final List<String> problems = new ArrayList<>();
        public void warning(SAXParseException e) { /* warnings are not failures */ }
        public void error(SAXParseException e) { problems.add(fmt("error", e)); }
        public void fatalError(SAXParseException e) { problems.add(fmt("fatal", e)); }
        private static String fmt(String kind, SAXParseException e) {
            return kind + " at line " + e.getLineNumber()
                    + " col " + e.getColumnNumber() + ": " + e.getMessage();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: PoutXsdValidate <percolator_out.xsd> <file.xml> [...]");
            System.exit(2);
        }
        File xsd = new File(args[0]);
        if (!xsd.isFile()) {
            System.err.println("schema not found: " + xsd);
            System.exit(2);
        }

        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
        Schema schema = sf.newSchema(xsd);

        System.out.println("schema: " + xsd.getAbsolutePath() + " (" + xsd.length() + " bytes)");

        int failed = 0;
        for (int i = 1; i < args.length; i++) {
            File xml = new File(args[i]);
            Collector collector = new Collector();
            Validator v = schema.newValidator();
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            v.setErrorHandler(collector);
            String verdict;
            try {
                v.validate(new StreamSource(xml));
                verdict = collector.problems.isEmpty() ? "VALID" : "INVALID";
            } catch (Exception e) {
                collector.problems.add("exception: " + e.getClass().getSimpleName()
                        + ": " + e.getMessage());
                verdict = "INVALID";
            }
            System.out.println(verdict + "  " + xml.getPath()
                    + "  (" + xml.length() + " bytes, "
                    + collector.problems.size() + " problem(s))");
            for (String p : collector.problems) {
                System.out.println("        " + p);
            }
            if (!"VALID".equals(verdict)) {
                failed++;
            }
        }
        System.out.println("documents=" + (args.length - 1) + " failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
