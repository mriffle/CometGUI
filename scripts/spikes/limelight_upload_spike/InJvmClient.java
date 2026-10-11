/*
 * CometGUI -- Phase 12, work unit 1: THE SPIKE.  Throwaway; NOT product code.
 * Copyright (C) 2026 The CometGUI authors.  GPL-3.0-or-later.
 *
 * A minimal in-JVM client of the Limelight submit-import protocol, written
 * against the wire format limelightSubmitImport.jar v8.3.1 speaks (no JAXB, no
 * dependency beyond the JDK: java.net.http + DOM).  It reads the submit key
 * from STANDARD INPUT, so the key is never on any process's argument array.
 *
 *   java InJvmClient.java --base <web-app-url> --project <id> --xml <file>
 *        [--search-name <s>] [--tag <t>]   < key
 *
 * Steps, exactly as the importer: auth-test (learns the server's program
 * version), initialize (receives uploadKey = the import tracking id), upload
 * the file (raw body, params in a Base64 XML header, SHA-256 declared), final
 * submit (receives importerSubDir).  Prints one line per step and a final
 * RESULT {json}.  Exit 0 = submitted; 3 = the server refused (flags printed);
 * 4 = transport error.
 */

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class InJvmClient {
    static final String P = "/d/rws/for-page/";
    static final int PGM_VERSION = 16;
    static HttpClient http;
    static String base;

    public static void main(String[] a) throws Exception {
        Map<String, String> o = new LinkedHashMap<>();
        for (int i = 0; i < a.length; i += 2) o.put(a[i], a[i + 1]);
        base = o.get("--base");
        String project = o.get("--project");
        Path xml = Path.of(o.get("--xml"));
        String key = new String(System.in.readAllBytes(), StandardCharsets.UTF_8).strip();
        if (key.isEmpty()) { System.out.println("no key on stdin"); System.exit(2); }
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // 1. auth test
            Map<String, String> r = post("project-upload-data-upload-submit-pgm-auth-test",
                el("submitImport_AuthTest_Request_PgmXML", "submitProgramVersionNumber", "" + PGM_VERSION,
                   "projectIdentifier", project, "userSubmitImportProgramKey", key));
            step("auth-test", r);
            if (!ok(r)) refuse(result, "auth-test", r);
            int serverVersion = Integer.parseInt(r.getOrDefault("submitProgramVersionNumberCurrentPerWebapp", "0"));
            result.put("serverProgramVersion", serverVersion);
            // 2. initialize
            r = post("project-upload-data-upload-initialize-from-submit-pgm",
                el("submitImport_Init_Request_PgmXML", "submitProgramVersionNumber", "" + PGM_VERSION,
                   "projectIdentifier", project, "userSubmitImportProgramKey", key, "submitterSameMachine", "true"));
            step("initialize", r);
            if (!ok(r)) refuse(result, "initialize", r);
            String trackingId = r.get("uploadKey");
            result.put("trackingId", trackingId);
            // 3. upload the file: raw body, params header
            String sha = sha256(xml);
            String params = el("submitImport_UploadFile_Request_Common", "projectIdentifier", project, "uploadKey", trackingId,
                "fileIndex", "1", "fileType", "1", "filename", xml.getFileName().toString(), "fileSHA256Hash", sha,
                "userSubmitImportProgramKey", key, "submitProgramVersion", "" + PGM_VERSION,
                "newHeaderParamsFormatVersion", "1");
            HttpRequest.Builder up = HttpRequest.newBuilder(URI.create(base + P + "project-upload-data-upload-file-from-submit-pgm"))
                .header("Content-Type", "application/xml").header("Accept", "application/xml")
                .POST(HttpRequest.BodyPublishers.ofFile(xml));
            if (serverVersion >= 16) up.header("limelight_upload_file_params_xml_base_64",
                Base64.getEncoder().encodeToString(params.getBytes(StandardCharsets.UTF_8)));
            else up.header("limelight_upload_file_params_xml", params.replace("\n", ""));
            HttpResponse<byte[]> resp = http.send(up.build(), HttpResponse.BodyHandlers.ofByteArray());
            r = attrs(resp);
            step("upload-file (HTTP " + resp.statusCode() + ", " + Files.size(xml) + " bytes, sha256 " + sha.substring(0, 12) + "...)", r);
            result.put("uploadedSha256", sha);
            if (!ok(r)) refuse(result, "upload-file", r);
            // 4. final submit
            String submit = "<submitImport_FinalSubmit_Request_PgmXML submitProgramVersionNumber=\"" + PGM_VERSION
                + "\" projectIdentifier=\"" + esc(project) + "\" uploadKey=\"" + esc(trackingId)
                + "\" searchName=\"" + esc(o.getOrDefault("--search-name", "")) + "\" userSubmitImportProgramKey=\"" + esc(key)
                + "\" submitterSameMachine=\"false\"><fileItems uploadedFilename=\"" + esc(xml.getFileName().toString())
                + "\" fileType=\"1\" fileIndex=\"1\" isLimelightXMLFile=\"true\"/>"
                + (o.containsKey("--tag") ? "<searchTagList>" + esc(o.get("--tag")) + "</searchTagList>" : "")
                + "</submitImport_FinalSubmit_Request_PgmXML>";
            r = post("project-upload-data-upload-submit-from-submit-pgm", submit);
            step("final-submit", r);
            if (!ok(r)) refuse(result, "final-submit", r);
            result.put("importerSubDir", r.get("importerSubDir"));
            result.put("projectPage", base + "/d/pg/project/" + project);
            result.put("outcome", "SUBMITTED");
            System.out.println("RESULT " + json(result));
            System.exit(0);
        } catch (Refused e) {
            System.out.println("RESULT " + json(result));
            System.exit(3);
        } catch (java.io.IOException e) {
            result.put("outcome", "TRANSPORT-ERROR");
            result.put("error", e.toString());
            System.out.println("RESULT " + json(result));
            System.exit(4);
        }
    }

    static class Refused extends Exception { }

    static void refuse(Map<String, Object> result, String step, Map<String, String> r) throws Refused {
        result.put("outcome", "REFUSED at " + step);
        Map<String, String> flags = new LinkedHashMap<>();
        r.forEach((k, v) -> { if ("true".equals(v) || k.equals("statusFailErrorMessage")) flags.put(k, v); });
        result.put("serverSaid", flags);
        throw new Refused();
    }

    static boolean ok(Map<String, String> r) { return "true".equals(r.get("statusSuccess")); }

    static void step(String name, Map<String, String> r) {
        System.out.println("STEP " + name + ": statusSuccess=" + r.get("statusSuccess")
            + (r.containsKey("statusFailErrorMessage") ? " message=" + r.get("statusFailErrorMessage") : ""));
    }

    static Map<String, String> post(String op, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + P + op))
            .header("Content-Type", "application/xml").header("Accept", "application/xml")
            .POST(HttpRequest.BodyPublishers.ofString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + body)).build();
        return attrs(http.send(req, HttpResponse.BodyHandlers.ofByteArray()));
    }

    /** The response root's attributes; a non-200 with an XML body is still a protocol answer (the 400 form). */
    static Map<String, String> attrs(HttpResponse<byte[]> resp) throws Exception {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("httpStatus", "" + resp.statusCode());
        if (resp.body().length == 0) { m.put("statusSuccess", "false"); return m; }
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Element root = f.newDocumentBuilder().parse(new ByteArrayInputStream(resp.body())).getDocumentElement();
        m.put("root", root.getTagName());
        NamedNodeMap nm = root.getAttributes();
        for (int i = 0; i < nm.getLength(); i++) m.put(nm.item(i).getNodeName(), nm.item(i).getNodeValue());
        return m;
    }

    static String el(String root, String... kv) {
        StringBuilder sb = new StringBuilder("<").append(root);
        for (int i = 0; i < kv.length; i += 2) sb.append(' ').append(kv[i]).append("=\"").append(esc(kv[i + 1])).append('"');
        return sb.append("/>").toString();
    }

    static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    static String sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(p)) {
            byte[] b = new byte[1 << 16]; int n;
            while ((n = in.read(b)) > 0) md.update(b, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    @SuppressWarnings("unchecked")
    static String json(Object o) {
        if (o == null) return "null";
        if (o instanceof Number || o instanceof Boolean) return o.toString();
        if (o instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{"); boolean f = true;
            for (Map.Entry<?, ?> e : m.entrySet()) { if (!f) sb.append(','); f = false; sb.append(json(String.valueOf(e.getKey()))).append(':').append(json(e.getValue())); }
            return sb.append('}').toString();
        }
        String s = o.toString();
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) { if (c == '"' || c == '\\') sb.append('\\').append(c); else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
        return sb.append('"').toString();
    }
}
