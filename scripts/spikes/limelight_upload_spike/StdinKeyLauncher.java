/*
 * CometGUI -- Phase 12, work unit 1: THE SPIKE.  Throwaway; NOT product code.
 * Copyright (C) 2026 The CometGUI authors.  GPL-3.0-or-later.
 *
 * Can the UNMODIFIED limelightSubmitImport.jar be driven without its submit
 * key on any process's argument array?  The JAR reads the key only from
 * --user-submit-import-key= (SubmitImportProgram.java:200-201, 280), and the
 * java launcher does not expand @argfiles after -jar.  This shim, started in
 * the child JVM in place of -jar, reads the key from standard input and calls
 * the JAR's own Main-Class with it appended -- so the key exists only in the
 * child's heap, never in /proc/<pid>/cmdline or the environment.
 *
 *   java -cp limelightSubmitImport.jar StdinKeyLauncher.java <importer args>  < key
 */

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class StdinKeyLauncher {
    public static void main(String[] args) throws Throwable {
        String key = new String(System.in.readAllBytes(), StandardCharsets.UTF_8).strip();
        String[] all = Arrays.copyOf(args, args.length + 1);
        all[args.length] = "--user-submit-import-key=" + key;
        Class<?> main = Class.forName("org.yeastrc.limelight.limelight_submit_import.program.SubmitImportProgram");
        Method m = main.getMethod("main", String[].class);
        m.invoke(null, (Object) all);
    }
}
