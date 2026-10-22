#!/usr/bin/env python3
"""The scanner behind scripts/e2e/check-no-fixed-sleep.sh (Phase 14 gate item 6).

"No test uses a fixed sleep; all waits are on observable state."  This reads the
COMPILED end-to-end classes and the driver classes they run -- with javap from the
project JDK, so a static import, a method reference or a helper class cannot hide
a call -- and the sources beside them, and reports every timed wait that is not
the one the harness permits: org.cometgui.app.uidriver.Await.pause, the poll of
the single wait primitive (design decision P14-8).  The permission is by class
AND method, never by file: a second sleep anywhere else in Await is reported.

Which classes: every class under org/cometgui/app/e2e/ and org/cometgui/app/
uidriver/ in the test-classes directory, plus -- transitively -- every class of
that same directory they reference (a helper under org/cometgui/app/testing/, for
example).  Product classes live in target/classes, not here, and are not scanned:
the gate is about how tests wait, not about how the product does.

Exit status: 0 nothing found; 1 found (each named: class, method, call); 2 misuse
or a scan that cannot be trusted (missing or stale classes, no javap, or the
permitted sleep itself not seen -- a scanner that cannot see the one call it knows
is there would pass anything).
"""

import argparse
import os
import re
import subprocess
import sys

ROOTS = ("org/cometgui/app/e2e", "org/cometgui/app/uidriver")
PERMITTED_CLASS = "org/cometgui/app/uidriver/Await"
PERMITTED_METHOD = "pause"
PERMITTED_SOURCE = "org/cometgui/app/uidriver/Await.java"

# --- what counts as a fixed wait, in bytecode: (owner pattern, name pattern, descriptor pattern)
FORBIDDEN_CALLS = [
    (r"java/lang/Thread", r"sleep", r".*", "Thread.sleep"),
    (r"java/util/concurrent/TimeUnit", r"sleep", r".*", "TimeUnit.sleep"),
    (r"java/lang/Thread", r"join", r"\(J\)V|\(JI\)V|\(Ljava/time/Duration;\)Z",
     "a timed Thread.join, which on the current thread is a sleep"),
    (r"java/util/concurrent/locks/LockSupport", r"park.*", r".*", "LockSupport.park*"),
    (r".*", r"wait", r"\(\)V|\(J\)V|\(JI\)V", "Object.wait"),
    (r"org/testfx/.*", r"sleep.*", r".*", "a TestFX sleep"),
    (r"org/testfx/util/WaitForAsyncUtils", r".*", r".*", "TestFX's WaitForAsyncUtils"),
    (r"org/testfx/robot/SleepRobot", r".*", r".*", "TestFX's SleepRobot"),
    (r"java/util/concurrent/CompletableFuture", r"delayedExecutor", r".*",
     "CompletableFuture.delayedExecutor"),
    (r"java/util/Timer", r"schedule.*", r".*", "Timer.schedule"),
    (r"java/util/concurrent/Scheduled(ExecutorService|ThreadPoolExecutor)", r"schedule.*",
     r".*", "a scheduled executor's delay"),
    (r"javafx/animation/PauseTransition", r"<init>", r".*", "a PauseTransition"),
]
FORBIDDEN_CALLS = [(re.compile(o), re.compile(n), re.compile(d), why)
                   for o, n, d, why in FORBIDDEN_CALLS]

# --- and in source text, after comments and string literals are blanked
FORBIDDEN_SOURCE = [
    (re.compile(r"\bsleep\s*\("), "a sleep(...) call"),
    (re.compile(r"::\s*sleep\b"), "a method reference to sleep"),
    (re.compile(r"\bTimeUnit\s*\.\s*\w+\s*\.\s*sleep\b"), "TimeUnit.sleep"),
    (re.compile(r"\bLockSupport\b"), "LockSupport"),
    (re.compile(r"\bpark(Nanos|Until)?\s*\("), "a park(...) call"),
    (re.compile(r"\bwait\s*\("), "a wait(...) call"),
    (re.compile(r"::\s*wait\b"), "a method reference to wait"),
    (re.compile(r"\bWaitForAsyncUtils\b"), "TestFX's WaitForAsyncUtils"),
    (re.compile(r"\bSleepRobot\b"), "TestFX's SleepRobot"),
    (re.compile(r"\bdelayedExecutor\b"), "CompletableFuture.delayedExecutor"),
    (re.compile(r"\bPauseTransition\b"), "a PauseTransition"),
    (re.compile(r"import\s+static\s+[\w.]+\.(sleep|park\w*)\s*;"), "a static import of a sleep"),
]


def die(message):
    print("check-no-fixed-sleep: " + message, file=sys.stderr)
    sys.exit(2)


def class_files(classes, roots):
    found = []
    for root in roots:
        top = os.path.join(classes, root)
        for directory, _, names in os.walk(top):
            for name in names:
                if name.endswith(".class"):
                    found.append(os.path.relpath(os.path.join(directory, name), classes)[:-6])
    return sorted(found)


def run_javap(javap, classes, names):
    """javap -v -p -c over many classes; returns {internal name: text}."""
    texts = {}
    for start in range(0, len(names), 200):
        batch = names[start:start + 200]
        files = [os.path.join(classes, name + ".class") for name in batch]
        result = subprocess.run([javap, "-v", "-p", "-c"] + files,
                                capture_output=True, text=True, encoding="utf-8")
        if result.returncode != 0:
            die("javap failed (exit %d): %s" % (result.returncode, result.stderr.strip()[:500]))
        chunks = re.split(r"^Classfile ", result.stdout, flags=re.M)[1:]
        if len(chunks) != len(batch):
            die("javap printed %d classes for %d files" % (len(chunks), len(batch)))
        for chunk in chunks:
            this = re.search(r"^\s*this_class: #\d+\s+// (\S+)", chunk, flags=re.M)
            if not this:
                die("javap output without this_class:\n" + chunk[:300])
            texts[this.group(1)] = chunk
    return texts


def referenced_classes(text):
    return set(re.findall(r"^\s*#\d+ = Class\s+#\d+\s+// \"?([\w/$]+)\"?", text, flags=re.M))


def bootstrap_handles(text):
    """{bootstrap index: [handle text, ...]} from the BootstrapMethods table."""
    table = {}
    section = text.split("\nBootstrapMethods:\n", 1)
    if len(section) < 2:
        return table
    current = None
    for line in section[1].splitlines():
        head = re.match(r"^\s{2}(\d+): #\d+ ", line)
        if head:
            current = int(head.group(1))
            table[current] = []
            continue
        if current is not None:
            handle = re.match(r"^\s+#\d+ (REF_\w+ \S+)", line)
            if handle:
                table[current].append(handle.group(1))
            elif re.match(r"^\S", line):
                break
    return table


def method_name(header, this_class):
    header = header.strip().rstrip(";")
    if header == "static {}":
        return "<clinit>"
    before = header.split("(", 1)[0].split()
    if not before:
        return header
    name = before[-1]
    if name.replace("/", ".") == this_class.replace("/", "."):
        return "<init>"
    return name


def forbidden(target, this_class):
    """target like java/lang/Thread.sleep:(J)V -> the reason, or None.

    javap leaves the owner out when it is the class itself (``wait:(J)V``), so an owner-less
    target is read as a call on this_class."""
    match = re.match(r'^(?:"?([\w/$\[;]+)"?\.)?"?([\w<>$]+)"?:(\S+)$', target)
    if not match:
        return None
    owner, name, descriptor = match.groups()
    owner = owner or this_class
    for o, n, d, why in FORBIDDEN_CALLS:
        if o.fullmatch(owner) and n.fullmatch(name) and d.fullmatch(descriptor):
            return why
    return None


def scan_class(name, text):
    """[(method, call text, why)] for every forbidden call in one class."""
    handles = bootstrap_handles(text)
    body = text.split("\n{\n", 1)
    if len(body) < 2:
        return []
    found = []
    method = None
    lines = body[1].splitlines()
    for index, line in enumerate(lines):
        if re.match(r"^  \S", line) and index + 1 < len(lines) \
                and lines[index + 1].strip().startswith("descriptor:"):
            method = method_name(line, name)
            continue
        call = re.match(r"^\s+\d+: (invoke\w+|ldc\w*)\s+#\d+(?:,\s*\d+)?\s+// (\w+) (.+)$", line)
        if not call or method is None:
            continue
        kind, what, rest = call.groups()
        if what in ("Method", "InterfaceMethod"):
            why = forbidden(rest.strip(), name)
            if why:
                found.append((method, "%s %s" % (kind, rest.strip()), why))
        elif what == "InvokeDynamic":
            index_match = re.match(r"#(\d+):", rest)
            if index_match:
                for handle in handles.get(int(index_match.group(1)), []):
                    why = forbidden(handle.split(" ", 1)[1], name)
                    if why:
                        found.append((method, "invokedynamic (method reference) " + handle,
                                      why))
        elif what == "MethodHandle":
            target = rest.strip().split(" ", 1)
            why = forbidden(target[1], name) if len(target) == 2 else None
            if why:
                found.append((method, "%s %s" % (kind, rest.strip()), why))
    return found


def blank(source):
    """The source with comments and string/char literals replaced by spaces, newlines kept."""
    out = []
    i = 0
    n = len(source)
    while i < n:
        c = source[i]
        if source.startswith("//", i):
            while i < n and source[i] != "\n":
                out.append(" ")
                i += 1
        elif source.startswith("/*", i):
            end = source.find("*/", i + 2)
            end = n if end < 0 else end + 2
            out.append(re.sub(r"[^\n]", " ", source[i:end]))
            i = end
        elif source.startswith('"""', i):
            end = source.find('"""', i + 3)
            end = n if end < 0 else end + 3
            out.append(re.sub(r"[^\n]", " ", source[i:end]))
            i = end
        elif c in "\"'":
            j = i + 1
            while j < n and source[j] != c and source[j] != "\n":
                j += 2 if source[j] == "\\" else 1
            out.append(" " * (min(j + 1, n) - i))
            i = j + 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


def permitted_span(blanked):
    """The [start, end) of Await.pause's body in the blanked Await source, or None."""
    head = re.search(r"\b" + PERMITTED_METHOD + r"\s*\([^)]*\)\s*(throws[^{]*)?\{", blanked)
    if not head:
        return None
    depth = 0
    for position in range(head.end() - 1, len(blanked)):
        if blanked[position] == "{":
            depth += 1
        elif blanked[position] == "}":
            depth -= 1
            if depth == 0:
                return (head.start(), position + 1)
    return None


def scan_source(path, relative):
    with open(path, encoding="utf-8") as handle:
        blanked = blank(handle.read())
    span = permitted_span(blanked) if relative == PERMITTED_SOURCE else None
    found = []
    permitted = 0
    for pattern, why in FORBIDDEN_SOURCE:
        for match in pattern.finditer(blanked):
            if span and span[0] <= match.start() < span[1]:
                permitted += 1
                continue
            line = blanked.count("\n", 0, match.start()) + 1
            found.append((line, why, match.group(0).strip()))
    return found, permitted


def main():
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--classes", required=True)
    parser.add_argument("--sources", required=True)
    parser.add_argument("--javap", required=True)
    args = parser.parse_args()
    classes = os.path.abspath(args.classes)
    sources = os.path.abspath(args.sources)
    if not os.path.isdir(classes):
        die("no compiled classes at %s -- run: mvn -B -o -pl cometgui-app test-compile" % classes)
    if not os.path.isdir(sources):
        die("no sources at %s" % sources)
    if not os.access(args.javap, os.X_OK):
        die("javap is not executable at %s -- source tools/env.sh" % args.javap)

    # every root source must have a class at least as new as itself: a stale or missing class is
    # a scan of something other than the code on disk
    root_sources = []
    for root in ROOTS:
        for directory, _, names in os.walk(os.path.join(sources, root)):
            for source_name in names:
                if source_name.endswith(".java"):
                    root_sources.append(os.path.join(directory, source_name))
    if not any(os.sep + "e2e" + os.sep in path for path in root_sources):
        die("no end-to-end sources under %s/org/cometgui/app/e2e -- nothing to check" % sources)
    for path in root_sources:
        relative = os.path.relpath(path, sources)[:-5]
        compiled = os.path.join(classes, relative + ".class")
        if not os.path.isfile(compiled):
            die("%s has no compiled class %s -- run: mvn -B -o -pl cometgui-app test-compile"
                % (path, compiled))
        if os.path.getmtime(compiled) < os.path.getmtime(path):
            die("%s is older than its source %s -- run: mvn -B -o -pl cometgui-app test-compile"
                % (compiled, path))

    # the classes: the roots, then everything of this directory they reference, to a fixed point
    pending = class_files(classes, ROOTS)
    texts = {}
    while pending:
        texts.update(run_javap(args.javap, classes, pending))
        seen = set(texts)
        wanted = set()
        for text in list(texts.values()):
            for reference in referenced_classes(text):
                if reference not in seen and os.path.isfile(
                        os.path.join(classes, reference + ".class")):
                    wanted.add(reference)
        pending = sorted(wanted)

    findings = []
    permitted_bytecode = 0
    methods = 0
    for name in sorted(texts):
        methods += len(re.findall(r"^  \S.*(\(.*\)|static \{\});\n\s+descriptor: ",
                                  texts[name].split("\n{\n", 1)[-1], flags=re.M))
        for method, call, why in scan_class(name, texts[name]):
            if name == PERMITTED_CLASS and method == PERMITTED_METHOD:
                permitted_bytecode += 1
                continue
            findings.append("FOUND in bytecode: %s.%s calls %s -- %s"
                            % (name.replace("/", "."), method, call, why))

    # the sources: the roots' and those of every class in the closure that has one here
    scanned_sources = set(root_sources)
    for name in texts:
        outer = name.split("$", 1)[0]
        candidate = os.path.join(sources, outer + ".java")
        if os.path.isfile(candidate):
            scanned_sources.add(candidate)
    permitted_source = 0
    for path in sorted(scanned_sources):
        relative = os.path.relpath(path, sources).replace(os.sep, "/")
        found, permitted = scan_source(path, relative)
        permitted_source += permitted
        for line, why, text in found:
            findings.append("FOUND in source: %s:%d: %s -- %s" % (relative, line, text, why))

    if findings:
        for finding in findings:
            print(finding)
        print("check-no-fixed-sleep: FAILED -- %d fixed wait(s) outside %s.%s"
              % (len(findings), PERMITTED_CLASS.replace("/", "."), PERMITTED_METHOD))
        sys.exit(1)
    if permitted_bytecode == 0 or permitted_source == 0:
        die("the scan did not see the permitted wait in %s.%s (bytecode %d, source %d): either the"
            " primitive changed or this scanner is broken, and a scanner that cannot see the one"
            " sleep it knows is there would pass anything"
            % (PERMITTED_CLASS.replace("/", "."), PERMITTED_METHOD, permitted_bytecode,
               permitted_source))
    print("check-no-fixed-sleep: PASSED -- %d classes (%d under %s and their %d referenced test"
          " classes), %d methods and %d source files scanned; the only timed wait is the"
          " permitted one in %s.%s (%d call in bytecode, %d in source)"
          % (len(texts), len(class_files(classes, ROOTS)), " and ".join(ROOTS),
             len(texts) - len(class_files(classes, ROOTS)), methods, len(scanned_sources),
             PERMITTED_CLASS.replace("/", "."), PERMITTED_METHOD, permitted_bytecode,
             permitted_source))
    sys.exit(0)


if __name__ == "__main__":
    main()
