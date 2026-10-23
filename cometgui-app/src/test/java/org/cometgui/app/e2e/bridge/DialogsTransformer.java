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

package org.cometgui.app.e2e.bridge;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Replaces the native file dialog, and only it, inside the packaged application.
 *
 * <p>The product opens every file dialog through one object: {@code FxFileChooser.JAVAFX}, an
 * implementation of {@code FxFileChooser.Dialogs} whose three methods call {@code
 * FileChooser.showOpenMultipleDialog}, {@code showOpenDialog} and {@code showSaveDialog}. When that
 * class is loaded this transformer rewrites exactly those three method bodies to pass the same two
 * arguments -- the configured {@code FileChooser} and the owner window -- to {@link BridgeDialogs}
 * and return what it returns. {@code FxFileChooser} itself is not touched: its titles, filters,
 * suggested names and its handling of a cancelled dialog run as shipped.
 *
 * <p><strong>Why that class and not {@code javafx.stage.FileChooser}.</strong> In the packaged
 * runtime {@code javafx.graphics} is defined to the platform class loader (measured on the
 * installed image on 2026-10-23), which cannot see a class on the class path, so a rewritten {@code
 * FileChooser} could not call this jar. {@code FxFileChooser}'s dialogs object is on the class
 * path, in the same loader and module as this agent.
 *
 * <p>It is found by what it is, not by its compiler-given name: a class nested in {@code
 * FxFileChooser} that implements {@code FxFileChooser$Dialogs}. Every rewrite, and every failure --
 * which the JVM would otherwise swallow -- is recorded and reported over the bridge ({@code
 * bridge.chooser.transformed}), so a test can require the rewrite to have happened before it relies
 * on it; a native dialog opening on the test display would hang the run instead.
 *
 * <p>Uses the JDK's {@code java.lang.classfile} API (final in JDK 24), so the agent needs no
 * bytecode library of its own.
 */
final class DialogsTransformer implements ClassFileTransformer {

    /** The product's chooser, in internal form. */
    static final String CHOOSER = "org/cometgui/app/config/FxFileChooser";

    /** The dialogs seam the rewritten class implements. */
    static final String DIALOGS = CHOOSER + "$Dialogs";

    /** Where the rewritten methods go instead. */
    static final ClassDesc HOOK = ClassDesc.of(BridgeDialogs.class.getName());

    /** The three methods, by name, with their descriptors. */
    static final Map<String, String> METHODS =
            Map.of(
                    "openMultiple",
                            "(Ljavafx/stage/FileChooser;Ljavafx/stage/Window;)Ljava/util/List;",
                    "open", "(Ljavafx/stage/FileChooser;Ljavafx/stage/Window;)Ljava/io/File;",
                    "save", "(Ljavafx/stage/FileChooser;Ljavafx/stage/Window;)Ljava/io/File;");

    private static final List<String> REPORT = new ArrayList<>();

    /**
     * {@inheritDoc}
     *
     * <p>The JDK's contract for this method is that {@code null} means "not transformed" -- an
     * empty array would be a class file with no content -- so the decision is made by {@link
     * #rewritten(String, byte[])}, which says it with an {@code Optional}, and only the contract's
     * own spelling is returned from here.
     */
    @Override
    public byte[] transform(
            Module module,
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
        return rewritten(className, classfileBuffer).orElse(null);
    }

    /**
     * The rewritten class, if this is the product's dialogs object.
     *
     * @param className the class's internal name, or {@code null} for a hidden class
     * @param classfileBuffer the class as loaded
     * @return the rewritten bytes, or empty for every other class and on failure (recorded)
     */
    static Optional<byte[]> rewritten(String className, byte[] classfileBuffer) {
        if (className == null || !className.startsWith(CHOOSER + "$")) {
            return Optional.empty();
        }
        try {
            ClassModel model = ClassFile.of().parse(classfileBuffer);
            boolean isDialogs =
                    model.interfaces().stream()
                            .anyMatch(implemented -> implemented.asInternalName().equals(DIALOGS));
            if (!isDialogs) {
                return Optional.empty();
            }
            byte[] rewritten = rewrite(model);
            record("rewrote " + className.replace('/', '.') + ": " + rewrittenIn(rewritten));
            return Optional.of(rewritten);
        } catch (RuntimeException | LinkageError failed) {
            record("FAILED to rewrite " + className.replace('/', '.') + ": " + failed);
            return Optional.empty();
        }
    }

    /**
     * The class with its three dialog methods calling {@link BridgeDialogs}.
     *
     * @param model the class as compiled
     * @return the rewritten class
     * @throws IllegalStateException if the class does not have exactly the three methods
     */
    static byte[] rewrite(ClassModel model) {
        TreeSet<String> found = new TreeSet<>();
        for (MethodModel method : model.methods()) {
            String name = method.methodName().stringValue();
            if (method.methodType().stringValue().equals(METHODS.get(name))) {
                found.add(name);
            }
        }
        if (!found.equals(new TreeSet<>(METHODS.keySet()))) {
            throw new IllegalStateException(
                    model.thisClass().asInternalName()
                            + " implements "
                            + DIALOGS
                            + " but has the dialog methods "
                            + found
                            + ", not "
                            + new TreeSet<>(METHODS.keySet()));
        }
        return ClassFile.of()
                .transformClass(
                        model,
                        (builder, element) -> {
                            if (element instanceof MethodModel method && isDialogMethod(method)) {
                                builder.withMethodBody(
                                        method.methodName(),
                                        method.methodType(),
                                        method.flags().flagsMask(),
                                        code ->
                                                code.aload(1)
                                                        .aload(2)
                                                        .invokestatic(
                                                                HOOK,
                                                                method.methodName().stringValue(),
                                                                method.methodTypeSymbol())
                                                        .areturn());
                            } else {
                                builder.with(element);
                            }
                        });
    }

    /**
     * Which of the three methods of a class call {@link BridgeDialogs}: read back from the bytes,
     * so the report says what the class now does rather than what was intended.
     *
     * @param bytes a class file
     * @return the methods, by name, in order
     */
    static List<String> rewrittenIn(byte[] bytes) {
        TreeSet<String> calling = new TreeSet<>();
        for (MethodModel method : ClassFile.of().parse(bytes).methods()) {
            method.code()
                    .ifPresent(
                            code ->
                                    code.forEach(
                                            element -> {
                                                if (element instanceof InvokeInstruction call
                                                        && call.owner().asSymbol().equals(HOOK)) {
                                                    calling.add(method.methodName().stringValue());
                                                }
                                            }));
        }
        return List.copyOf(calling);
    }

    private static boolean isDialogMethod(MethodModel method) {
        return method.methodType()
                .stringValue()
                .equals(METHODS.get(method.methodName().stringValue()));
    }

    private static void record(String line) {
        synchronized (REPORT) {
            REPORT.add(line);
        }
    }

    /**
     * Every rewrite and every failure so far.
     *
     * @return one line each
     */
    static List<String> report() {
        synchronized (REPORT) {
            return List.copyOf(REPORT);
        }
    }
}
