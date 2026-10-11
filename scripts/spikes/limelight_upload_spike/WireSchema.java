/*
 * CometGUI -- Phase 12, work unit 1: THE SPIKE.  Throwaway; NOT product code.
 * Copyright (C) 2026 The CometGUI authors.  GPL-3.0-or-later.
 *
 * Asks JAXB -- with the importer JAR's OWN request/response classes on the
 * class path -- for the XML schema of the submit-import wire format, so the
 * fake and the throwaway client use the element and attribute names the real
 * client and server actually exchange (JAXB drops underscores: the Java field
 * statusFail_ErrorMessage is the attribute statusFailErrorMessage).
 *
 *   java -cp limelightSubmitImport.jar WireSchema.java <out.xsd>
 */
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.SchemaOutputResolver;
import java.io.File;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;

public class WireSchema {
    public static void main(String[] a) throws Exception {
        String pkg = "org.yeastrc.limelight.limelight_submit_import_client_connector.request_response_objects.";
        String[] names = {
            "SubmitImport_AuthTest_Request_PgmXML", "SubmitImport_AuthTest_Response_PgmXML",
            "SubmitImport_Get_FASTAFileUploadAccepted_Request_PgmXML", "SubmitImport_Get_FASTAFileUploadAccepted_Response_PgmXML",
            "SubmitImport_Init_Request_PgmXML", "SubmitImport_Init_Response_PgmXML",
            "SubmitImport_UploadFile_Request_Common", "SubmitImport_UploadFile_Response_PgmXML",
            "SubmitImport_FinalSubmit_Request_PgmXML", "SubmitImport_FinalSubmit_Response_PgmXML"};
        Class<?>[] cs = new Class<?>[names.length];
        for (int i = 0; i < names.length; i++) cs[i] = Class.forName(pkg + names[i]);
        JAXBContext c = JAXBContext.newInstance(cs);
        File out = new File(a[0]);
        c.generateSchema(new SchemaOutputResolver() {
            public Result createOutput(String ns, String suggested) {
                StreamResult r = new StreamResult(out);
                r.setSystemId(out.toURI().toString());
                return r;
            }
        });
        System.out.println("WIRE-SCHEMA " + out + " " + out.length());
    }
}
