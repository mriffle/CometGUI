#!/usr/bin/env python3
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
"""The inspector behind scripts/ci/release-no-test-bridge.sh (Phase 14 unit 6;
design decision P14-12; specification R-TEST-06, AC-TST-12).

    python3 no_test_bridge.py --root REPO --jimage JDK/bin/jimage --image IMAGE [--label TEXT]

IMAGE is an unpacked CometGUI application image (the directory holding bin/,
lib/app/ and lib/runtime/).  Everything in it is read:

  * every file's NAME, against the names of test machinery's jars;
  * every JAR or ZIP, and every JAR or ZIP inside one, to any depth: each entry's
    name, each manifest (an agent's Premain-Class, Agent-Class,
    Launcher-Agent-Class or Boot-Class-Path), and each small text entry;
  * every loose .class file;
  * the bundled runtime's module image, lib/runtime/lib/modules, listed with the
    project JDK's jimage: each module's name and each class in it;
  * every other text file -- the launcher configuration lib/app/*.cfg first among
    them -- for the bridge's attach option and enabling flag.

A CLASS is forbidden when it is in a package the test bridge, the GUI test driver
or a test framework lives in (FORBIDDEN_PACKAGES), when it is a test class of
this project (any class compiled from a */src/test/java source of the
repository), or when it is the D-007 fake Limelight server.  TEXT is forbidden
when it carries one of FORBIDDEN_TEXT.

Exit status: 0 nothing found, and something of every kind was inspected; 1 found
(each finding printed with where it is and why it is forbidden); 2 misuse, or an
inspection that looked at nothing -- no JAR, no class, no launcher configuration
or no runtime module -- because "found nothing" must never mean "looked at
nothing".
"""

import argparse
import io
import os
import re
import subprocess
import sys
import zipfile

# Where a class must not be: (package prefix in internal form, why).
FORBIDDEN_PACKAGES = [
    ("org/cometgui/app/e2e/", "the end-to-end harness, which holds the test bridge (org.cometgui.app.e2e.bridge)"),
    ("org/cometgui/app/uidriver/", "the GUI test driver the bridge runs"),
    ("org/cometgui/app/testing/", "cometgui-app's test support"),
    ("org/testfx/", "TestFX"),
    ("com/sun/glass/ui/monocle/", "Monocle, the headless test platform"),
    ("org/junit/", "JUnit"),
    ("junit/", "JUnit 3/4"),
    ("org/opentest4j/", "opentest4j, JUnit's assertion errors"),
    ("org/apiguardian/", "apiguardian, JUnit's API annotations"),
    ("org/hamcrest/", "Hamcrest"),
    ("org/assertj/", "AssertJ"),
    ("org/mockito/", "Mockito"),
    ("com/tngtech/archunit/", "ArchUnit"),
    ("net/jqwik/", "jqwik"),
    ("org/pitest/", "PIT"),
    ("org/jacoco/", "JaCoCo"),
]

# The D-007 fake Limelight server, under whatever package a copy is in.
FAKE_SERVER = re.compile(r"(^|/)FakeLimelight[A-Za-z0-9_]*(\$[A-Za-z0-9_$]*)?\.class$")

# A file or nested archive named like test machinery.
FORBIDDEN_ARCHIVE_NAME = re.compile(
    r"(testfx|junit|hamcrest|opentest4j|apiguardian|monocle|assertj|mockito|archunit|jqwik|pitest"
    r"|jacoco|cometgui-e2e|e2e-bridge)", re.IGNORECASE)

# A runtime module named like test machinery.
FORBIDDEN_MODULE = re.compile(r"(testfx|junit|monocle|opentest4j|cometgui|e2e)", re.IGNORECASE)

# Text that attaches or enables the bridge: (literal, why).
FORBIDDEN_TEXT = [
    ("-javaagent", "a Java agent option: how the test bridge is attached"),
    ("-agentlib", "a native agent option"),
    ("-agentpath", "a native agent option"),
    ("-Xbootclasspath", "a boot class path option, which can inject classes"),
    ("JAVA_TOOL_OPTIONS", "the variable the test bridge is attached through"),
    ("cometgui.e2e", "the end-to-end flag (-Dcometgui.e2e=true) or the bridge's package"),
    ("cometgui-e2e-bridge", "the test bridge's jar"),
    ("org/cometgui/app/e2e", "the test bridge's package"),
    ("port-file=", "the test bridge's agent argument"),
]

# Manifest attributes that make a JAR an agent or extend the boot class path.
AGENT_ATTRIBUTES = ("Premain-Class", "Agent-Class", "Launcher-Agent-Class", "Boot-Class-Path")

ARCHIVE_SUFFIXES = (".jar", ".zip", ".war", ".ear", ".jmod")
TEXT_LIMIT = 32 * 1024 * 1024
MODULES_IMAGE = os.path.join("lib", "runtime", "lib", "modules")


class Inspection:
    def __init__(self, test_classes):
        self.test_classes = test_classes
        self.findings = []
        self.jars = 0
        self.nested = 0
        self.classes = 0
        self.cfgs = 0
        self.texts = 0
        self.modules = 0
        self.module_classes = 0
        self.files = 0

    def found(self, where, what, why):
        self.findings.append(f"{where}: {what} -- {why}")

    # -- classes ---------------------------------------------------------------------
    def check_class(self, where, entry):
        name = entry
        versioned = re.match(r"META-INF/versions/[0-9]+/(.*)$", name)
        if versioned:
            name = versioned.group(1)
        for prefix, why in FORBIDDEN_PACKAGES:
            if name.startswith(prefix):
                self.found(where, name, why)
                return True
        if FAKE_SERVER.search(name):
            self.found(where, name, "the D-007 fake Limelight server, a test fixture")
            return True
        base = name[: -len(".class")]
        top = base.split("$", 1)[0]
        if top in self.test_classes:
            self.found(where, name, "a test class of this project (" + self.test_classes[top] + ")")
            return True
        return False

    def check_loose_class(self, relative):
        """A .class file outside any archive: its package is not known, so every suffix of its
        path is tried as one."""
        segments = relative.replace(os.sep, "/").split("/")
        for start in range(len(segments)):
            if self.check_class(relative, "/".join(segments[start:])):
                return

    # -- text ------------------------------------------------------------------------
    def check_text(self, where, data, is_cfg=False):
        text = data.decode("utf-8", errors="replace")
        for literal, why in FORBIDDEN_TEXT:
            index = text.find(literal)
            while index >= 0:
                line_start = text.rfind("\n", 0, index) + 1
                line_end = text.find("\n", index)
                line = text[line_start: line_end if line_end >= 0 else len(text)].strip()
                number = text.count("\n", 0, index) + 1
                kind = "launcher configuration" if is_cfg else "text"
                self.found(f"{where}:{number}", f"{kind} '{line[:160]}'", why + f" ({literal})")
                index = text.find(literal, line_end if line_end >= 0 else len(text))

    # -- archives --------------------------------------------------------------------
    def check_archive(self, where, opener, depth=0):
        if depth == 0:
            self.jars += 1
        else:
            self.nested += 1
        try:
            archive = zipfile.ZipFile(opener)
        except zipfile.BadZipFile as bad:
            self.found(where, "an archive that cannot be read", f"{bad}; its content cannot be shown clean")
            return
        with archive:
            for info in archive.infolist():
                if info.is_dir():
                    continue
                entry = info.filename
                inner = f"{where}!{entry}"
                lower = entry.lower()
                if lower.endswith(".class"):
                    self.classes += 1
                    self.check_class(where, entry)
                elif lower.endswith(ARCHIVE_SUFFIXES):
                    if FORBIDDEN_ARCHIVE_NAME.search(os.path.basename(entry)):
                        self.found(inner, "a nested archive named like test machinery", "its name")
                    self.check_archive(inner, io.BytesIO(archive.read(info)), depth + 1)
                elif entry.upper() == "META-INF/MANIFEST.MF":
                    data = archive.read(info)
                    self.texts += 1
                    for line in data.decode("utf-8", errors="replace").splitlines():
                        key = line.split(":", 1)[0].strip()
                        if key in AGENT_ATTRIBUTES:
                            self.found(inner, f"manifest '{line.strip()}'",
                                       "a Java agent's manifest: this JAR can be attached as an agent")
                    self.check_text(inner, data)
                elif info.file_size <= TEXT_LIMIT:
                    data = archive.read(info)
                    if b"\0" not in data[:65536]:
                        self.texts += 1
                        self.check_text(inner, data)

    # -- the runtime's module image --------------------------------------------------
    def check_modules(self, path, jimage, where):
        result = subprocess.run([jimage, "list", path], capture_output=True, text=True)
        if result.returncode != 0:
            self.found(where, "a module image jimage cannot list",
                       (result.stderr or result.stdout).strip()[:300])
            return
        module = None
        for line in result.stdout.splitlines():
            heading = re.match(r"^Module:\s*(\S+)\s*$", line)
            if heading:
                module = heading.group(1)
                self.modules += 1
                if FORBIDDEN_MODULE.search(module):
                    self.found(where, f"module {module}", "a runtime module named like test machinery")
                continue
            entry = line.strip()
            if module and entry.endswith(".class"):
                self.module_classes += 1
                self.check_class(f"{where} (module {module})", entry)

    # -- the image -------------------------------------------------------------------
    def inspect(self, image, jimage):
        for directory, subdirectories, files in os.walk(image):
            subdirectories.sort()
            for name in sorted(files):
                path = os.path.join(directory, name)
                relative = os.path.relpath(path, image)
                if os.path.islink(path) or not os.path.isfile(path):
                    continue
                self.files += 1
                lower = name.lower()
                if relative == MODULES_IMAGE:
                    self.check_modules(path, jimage, relative)
                    continue
                if FORBIDDEN_ARCHIVE_NAME.search(name):
                    self.found(relative, "a file named like test machinery", "its name")
                if lower.endswith(ARCHIVE_SUFFIXES):
                    self.check_archive(relative, path)
                elif lower.endswith(".class"):
                    self.classes += 1
                    self.check_loose_class(relative)
                elif os.path.getsize(path) <= TEXT_LIMIT:
                    with open(path, "rb") as handle:
                        data = handle.read()
                    if b"\0" in data[:65536]:
                        continue
                    is_cfg = lower.endswith(".cfg") and os.path.dirname(relative) == os.path.join("lib", "app")
                    if is_cfg:
                        self.cfgs += 1
                    self.texts += 1
                    self.check_text(relative, data, is_cfg)


def project_test_classes(root):
    """Every top-level class name compiled from a */src/test/java source, internal form."""
    found = {}
    for module in sorted(os.listdir(root)):
        tree = os.path.join(root, module, "src", "test", "java")
        if not os.path.isdir(tree):
            continue
        for directory, _, files in os.walk(tree):
            for name in files:
                if not name.endswith(".java") or name in ("package-info.java", "module-info.java"):
                    continue
                relative = os.path.relpath(os.path.join(directory, name), tree)
                found[relative[: -len(".java")].replace(os.sep, "/")] = module + "/src/test/java"
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", required=True)
    parser.add_argument("--jimage", required=True)
    parser.add_argument("--image", required=True)
    parser.add_argument("--label", default=None)
    args = parser.parse_args()

    image = os.path.abspath(args.image)
    if not os.path.isdir(image):
        print(f"no-test-bridge: {image} is not a directory", file=sys.stderr)
        return 2
    test_classes = project_test_classes(args.root)
    if not test_classes:
        print(f"no-test-bridge: no */src/test/java source under {args.root}, so the project's test"
              " classes could not be looked for", file=sys.stderr)
        return 2
    inspection = Inspection(test_classes)
    inspection.inspect(image, args.jimage)

    label = args.label or image
    print(f"inspected {label}:")
    print(f"  {inspection.files} files; {inspection.jars} JAR(s) and {inspection.nested} nested"
          f" archive(s); {inspection.classes} class entries in them and loose;"
          f" {inspection.cfgs} launcher configuration(s) (lib/app/*.cfg); {inspection.texts} text"
          f" files and entries; {inspection.modules} runtime modules holding"
          f" {inspection.module_classes} classes; checked against {len(test_classes)} project test"
          f" classes, {len(FORBIDDEN_PACKAGES)} forbidden packages and {len(FORBIDDEN_TEXT)}"
          " forbidden texts")

    empty = [what for what, count in (("JAR", inspection.jars), ("class", inspection.classes),
                                      ("launcher configuration", inspection.cfgs),
                                      ("runtime module", inspection.modules)) if count == 0]
    if inspection.findings:
        for finding in inspection.findings[:400]:
            print(f"  FOUND {finding}")
        if len(inspection.findings) > 400:
            print(f"  ... and {len(inspection.findings) - 400} more")
        print(f"release-no-test-bridge: FAILED -- {len(inspection.findings)} finding(s): test-only"
              f" machinery or the bridge's enabling option is in {label} (R-TEST-06)")
        return 1
    if empty:
        print(f"release-no-test-bridge: NOTHING INSPECTED -- no {', no '.join(empty)} found in"
              f" {label}; an inspection that looked at nothing cannot say the artefact is clean")
        return 2
    print(f"release-no-test-bridge: PASSED -- no test bridge, test class, test framework or"
          f" enabling option in {label}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
