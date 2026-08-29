#!/usr/bin/env python3
"""Validate a CycloneDX SBOM by its CONTENT, not by the generator's exit code.

Why this file exists
--------------------
``cyclonedx-maven-plugin`` exits 0 whether it wrote a document listing every
dependency in the reactor or one listing nothing at all: a misconfigured
``includeTestScope``, a reactor that failed to resolve, or a goal invoked on
the wrong project all produce a valid, well-formed, useless SBOM.  The
specification asks for "SBOM generation validation" in the pull-request
pipeline, and the only validation worth the name is of the document.

So this script refuses to take the SBOM's word for anything and cross-checks it
against the POMs it was generated from:

1. the JSON document parses, is CycloneDX, and has a metadata component;
2. every component carries a name, a version and a well-formed Maven purl,
   and no two share a bom-ref;
3. every module in ``<modules>`` appears as a component;
4. every third-party dependency declared in any module POM appears as a
   component -- so an SBOM that quietly dropped the test scope fails;
5. at least one component is present that no POM declares, i.e. the dependency
   graph really was resolved rather than the POMs echoed back;
6. the XML document and the JSON document describe the same component set;
7. every build plugin in every POM has a pinned, exact version, which the
   specification's Supply-Chain and Application Security section requires
   ("pin build-plugin versions") and which nothing else checks.

An empty ``components`` array fails at step 2 with exit 3, which is the case
the phase brief calls out by name.

Standard library only.  The project virtualenv holds Sphinx and nothing else,
and ``docs/requirements.txt`` is what Read the Docs installs, so it must not
grow a dependency for a build-time check.

Usage
-----
    python3 scripts/ci/sbom_verify.py --json PATH [--xml PATH] [--root DIR]
    python3 scripts/ci/sbom_verify.py --self-test [--root DIR]

``--self-test`` writes damaged copies of the real SBOM under ``_build/`` and
requires each to be rejected with the right exit code, then re-checks the real
document.  The real file is never touched.

Exit status
-----------
0  the SBOM describes the project the POMs describe
1  a content mismatch: a component the POMs require is missing, the JSON and
   XML disagree, or a build plugin has no pinned version
2  misuse, or a broken environment (no POMs, unreadable arguments)
3  the SBOM is missing, empty, unparseable, or lists no components
4  --self-test only: a damaged SBOM was ACCEPTED, i.e. this check is not
   falsifiable and must not be trusted
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

POM_NS = "http://maven.apache.org/POM/4.0.0"
NS = {"m": POM_NS}
CDX_NS_RE = re.compile(r"^\{http://cyclonedx\.org/schema/bom/[0-9.]+\}")

EXIT_OK, EXIT_CONTENT, EXIT_MISUSE, EXIT_DOCUMENT, EXIT_UNFALSIFIABLE = 0, 1, 2, 3, 4

PURL_RE = re.compile(r"^pkg:maven/([^/@]+)/([^@]+)@(.+)$")
UNPINNED_RE = re.compile(r"(LATEST|RELEASE|SNAPSHOT|[\[\](),])")


class Problem(Exception):
    def __init__(self, code: int, message: str) -> None:
        super().__init__(message)
        self.code = code


# --------------------------------------------------------------------------
# POMs -- the independent description of what the SBOM ought to contain
# --------------------------------------------------------------------------

def pom_files(root: Path) -> list[Path]:
    poms = [root / "pom.xml"]
    poms += sorted(root.glob("cometgui-*/pom.xml"))
    missing = [p for p in poms if not p.is_file()]
    if missing or len(poms) < 2:
        raise Problem(EXIT_MISUSE, f"expected a reactor of POMs under {root}; found {len(poms)}")
    return poms


def text(el, path, default=None):
    found = el.findtext(path, namespaces=NS)
    return default if found is None else found.strip()


def load_properties(poms: list[Path]) -> dict[str, str]:
    props: dict[str, str] = {}
    for pom in poms:
        root = ET.parse(pom).getroot()
        block = root.find("m:properties", NS)
        if block is None:
            continue
        for child in block:
            tag = child.tag.replace(f"{{{POM_NS}}}", "")
            props[tag] = (child.text or "").strip()
    return props


def resolve(value: str, props: dict[str, str]) -> str | None:
    """Resolve ${...} references. Returns None when something does not resolve."""
    seen = 0
    while "${" in value:
        seen += 1
        if seen > 10:
            return None
        match = re.search(r"\$\{([^}]+)\}", value)
        key = match.group(1)
        if key not in props:
            return None
        value = value[: match.start()] + props[key] + value[match.end():]
    return value


def reactor_modules(root: Path) -> list[str]:
    tree = ET.parse(root / "pom.xml").getroot()
    modules = [m.text.strip() for m in tree.findall("m:modules/m:module", NS)]
    if not modules:
        raise Problem(EXIT_MISUSE, "the root POM declares no <modules>")
    return modules


def declared_dependencies(poms: list[Path]) -> tuple[set[str], set[str]]:
    """Returns (third-party ga, reactor ga) from every POM's direct <dependencies>."""
    third, own = set(), set()
    for pom in poms:
        root = ET.parse(pom).getroot()
        block = root.find("m:dependencies", NS)
        if block is None:
            continue
        for dep in block.findall("m:dependency", NS):
            group = text(dep, "m:groupId", "")
            artifact = text(dep, "m:artifactId", "")
            dtype = text(dep, "m:type", "jar")
            scope = text(dep, "m:scope", "compile")
            if not group or not artifact:
                raise Problem(EXIT_MISUSE, f"{pom}: a <dependency> has no groupId/artifactId")
            if dtype == "pom" or scope == "import":
                continue  # a BOM import contributes no component of its own
            (own if group == "org.cometgui" else third).add(f"{group}:{artifact}")
    return third, own


def plugin_artifact_items(poms: list[Path], props: dict[str, str]) -> set[str]:
    """Coordinates a plugin fetches directly (maven-dependency-plugin artifactItems).

    These are real downloads that the reactor's dependency graph does not
    contain, so CycloneDX cannot see them and neither would a scanner reading
    only the SBOM.  openjfx-monocle is one today.
    """
    items = set()
    for pom in poms:
        root = ET.parse(pom).getroot()
        for item in root.iter(f"{{{POM_NS}}}artifactItem"):
            group = text(item, "m:groupId", "")
            artifact = text(item, "m:artifactId", "")
            version = text(item, "m:version", "")
            resolved = resolve(version, props) if version else None
            if not (group and artifact and resolved):
                raise Problem(
                    EXIT_MISUSE,
                    f"{pom}: <artifactItem> {group}:{artifact} has no resolvable version "
                    f"({version!r}); the dependency scan cannot cover what it cannot name",
                )
            items.add(f"{group}:{artifact}:{resolved}")
    return items


def check_plugin_pins(poms: list[Path], props: dict[str, str]) -> list[str]:
    """Every build plugin must carry an exact, pinned version."""
    managed: dict[str, str] = {}
    used: list[tuple[Path, str, str | None]] = []
    for pom in poms:
        root = ET.parse(pom).getroot()
        build = root.find("m:build", NS)
        if build is None:
            continue
        pm = build.find("m:pluginManagement/m:plugins", NS)
        if pm is not None:
            for plugin in pm.findall("m:plugin", NS):
                group = text(plugin, "m:groupId", "org.apache.maven.plugins")
                artifact = text(plugin, "m:artifactId", "")
                version = text(plugin, "m:version")
                if version:
                    managed[f"{group}:{artifact}"] = version
                used.append((pom, f"{group}:{artifact}", version))
        direct = build.find("m:plugins", NS)
        if direct is not None:
            for plugin in direct.findall("m:plugin", NS):
                group = text(plugin, "m:groupId", "org.apache.maven.plugins")
                artifact = text(plugin, "m:artifactId", "")
                used.append((pom, f"{group}:{artifact}", text(plugin, "m:version")))

    problems = []
    for pom, ga, version in used:
        effective = version or managed.get(ga)
        where = os.path.relpath(pom, pom.parents[len(pom.parents) - 1])
        if not effective:
            problems.append(f"{pom.name} [{ga}]: no <version> and no pluginManagement pin")
            continue
        resolved = resolve(effective, props)
        if resolved is None:
            problems.append(f"{pom.name} [{ga}]: version {effective!r} does not resolve to a value")
            continue
        if UNPINNED_RE.search(resolved):
            problems.append(f"{pom.name} [{ga}]: version {resolved!r} is not an exact pin")
    return problems


# --------------------------------------------------------------------------
# The SBOM itself
# --------------------------------------------------------------------------

def load_json_bom(path: Path) -> dict:
    if not path.is_file():
        raise Problem(EXIT_DOCUMENT, f"no SBOM at {path} -- the generator wrote nothing")
    if path.stat().st_size == 0:
        raise Problem(EXIT_DOCUMENT, f"the SBOM at {path} is zero bytes")
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise Problem(EXIT_DOCUMENT, f"the SBOM at {path} is not valid JSON: {exc}") from exc
    if not isinstance(doc, dict):
        raise Problem(EXIT_DOCUMENT, f"the SBOM at {path} is not a JSON object")
    if doc.get("bomFormat") != "CycloneDX":
        raise Problem(EXIT_DOCUMENT, f"{path}: bomFormat is {doc.get('bomFormat')!r}, expected 'CycloneDX'")
    if not doc.get("specVersion"):
        raise Problem(EXIT_DOCUMENT, f"{path}: no specVersion")
    if not isinstance(doc.get("components"), list):
        raise Problem(EXIT_DOCUMENT, f"{path}: no components array at all")
    if not doc["components"]:
        raise Problem(
            EXIT_DOCUMENT,
            f"{path}: the components array is EMPTY. The generator exited 0 and "
            f"produced a valid document describing nothing. That is not an SBOM.",
        )
    return doc


def json_components(doc: dict, path: Path) -> list[dict]:
    seen_refs = set()
    out = []
    for index, comp in enumerate(doc["components"]):
        if not isinstance(comp, dict):
            raise Problem(EXIT_DOCUMENT, f"{path}: component {index} is not an object")
        name, version, purl = comp.get("name"), comp.get("version"), comp.get("purl")
        if not name or not version:
            raise Problem(EXIT_DOCUMENT, f"{path}: component {index} has no name or no version: {comp!r}")
        if not purl or not PURL_RE.match(purl):
            raise Problem(EXIT_DOCUMENT, f"{path}: component {name} has no well-formed Maven purl: {purl!r}")
        ref = comp.get("bom-ref")
        if ref:
            if ref in seen_refs:
                raise Problem(EXIT_DOCUMENT, f"{path}: duplicate bom-ref {ref}")
            seen_refs.add(ref)
        out.append(comp)
    return out


def xml_purls(path: Path) -> set[str]:
    if not path.is_file() or path.stat().st_size == 0:
        raise Problem(EXIT_DOCUMENT, f"no usable XML SBOM at {path}")
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        raise Problem(EXIT_DOCUMENT, f"{path} is not valid XML: {exc}") from exc
    # Only the top-level <components> block. <metadata><component> describes
    # the project itself, not a dependency, and the JSON document keeps it in
    # metadata rather than in the components array -- counting it here would
    # make the two documents look as if they disagreed.
    components_block = None
    for child in root:
        if CDX_NS_RE.sub("", child.tag) == "components":
            components_block = child
            break
    if components_block is None:
        raise Problem(EXIT_DOCUMENT, f"{path}: the XML SBOM has no <components> block")
    purls = set()
    for component in components_block.iter():
        if CDX_NS_RE.sub("", component.tag) != "component":
            continue
        for child in component:
            if CDX_NS_RE.sub("", child.tag) == "purl" and child.text:
                purls.add(child.text.strip())
    if not purls:
        raise Problem(EXIT_DOCUMENT, f"{path}: the XML SBOM lists no component purls")
    return purls


# --------------------------------------------------------------------------
# The check
# --------------------------------------------------------------------------

def verify(root: Path, json_path: Path, xml_path: Path | None, quiet: bool = False) -> int:
    def say(*args):
        if not quiet:
            print(*args)

    poms = pom_files(root)
    props = load_properties(poms)
    modules = reactor_modules(root)
    third_party, _own = declared_dependencies(poms)
    extras = plugin_artifact_items(poms, props)

    doc = load_json_bom(json_path)
    components = json_components(doc, json_path)

    metadata_component = (doc.get("metadata") or {}).get("component") or {}
    if not metadata_component.get("name"):
        raise Problem(EXIT_DOCUMENT, f"{json_path}: metadata.component has no name")

    by_ga = {}
    for comp in components:
        group, name, version = PURL_RE.match(comp["purl"]).groups()
        by_ga[f"{group}/{name}".replace("/", ":")] = version

    say(f"sbom-verify: {json_path}")
    say(f"sbom-verify:   CycloneDX {doc['specVersion']}, serial {doc.get('serialNumber', '(none)')}")
    say(f"sbom-verify:   metadata component: {metadata_component.get('name')} "
        f"{metadata_component.get('version', '')}")
    say(f"sbom-verify:   {len(components)} component(s)")
    for comp in components:
        say(f"      {comp['purl']}")

    problems: list[str] = []

    missing_modules = [m for m in modules if f"org.cometgui:{m}" not in by_ga]
    for module in missing_modules:
        problems.append(f"reactor module {module} is declared in <modules> but absent from the SBOM")

    missing_deps = sorted(ga for ga in third_party if ga not in by_ga)
    for ga in missing_deps:
        problems.append(
            f"{ga} is a declared dependency of this reactor but absent from the SBOM "
            f"(a dropped test scope looks exactly like this)"
        )

    declared = {f"org.cometgui:{m}" for m in modules} | third_party
    transitive = sorted(ga for ga in by_ga if ga not in declared)
    if not transitive:
        problems.append(
            "the SBOM contains no component that a POM does not declare: the dependency "
            "graph was not resolved, the POMs were merely echoed back"
        )
    say(f"sbom-verify:   {len(modules)} reactor module(s), {len(third_party)} declared "
        f"third-party dependency(ies), {len(transitive)} resolved transitive component(s)")

    if xml_path is not None:
        xml_set = xml_purls(xml_path)
        json_set = {c["purl"] for c in components}
        only_json = sorted(json_set - xml_set)
        only_xml = sorted(xml_set - json_set)
        if only_json or only_xml:
            problems.append(
                f"the JSON and XML SBOMs disagree: {len(only_json)} only in JSON "
                f"{only_json[:3]}, {len(only_xml)} only in XML {only_xml[:3]}"
            )
        else:
            say(f"sbom-verify:   XML and JSON agree on all {len(xml_set)} purl(s)")

    pin_problems = check_plugin_pins(poms, props)
    problems.extend(pin_problems)
    if not pin_problems:
        say("sbom-verify:   every build plugin in every POM carries an exact pinned version")

    if extras:
        say("sbom-verify:   coordinates the reactor downloads that the SBOM cannot see "
            "(plugin artifactItems), covered separately by dependency-scan.py:")
        for item in sorted(extras):
            say(f"      {item}")

    if problems:
        print("\nsbom-verify: FAILED", file=sys.stderr)
        for problem in problems:
            print(f"  * {problem}", file=sys.stderr)
        return EXIT_CONTENT

    say("\nsbom-verify: PASSED -- the SBOM describes the project the POMs describe.")
    return EXIT_OK


# --------------------------------------------------------------------------
# --self-test: damaged copies must be rejected
# --------------------------------------------------------------------------

def self_test(root: Path, json_path: Path, xml_path: Path) -> int:
    work = root / "_build" / "sbom-selftest"
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    print(f"=== sbom-verify --self-test: damaged copies under {work} ===")
    print("The real SBOM is never touched.\n")

    real = json.loads(json_path.read_text(encoding="utf-8"))

    def damaged(label, mutate, expected_code, expect_in_message):
        doc = json.loads(json.dumps(real))
        mutate(doc)
        target = work / f"{label}.json"
        target.write_text(json.dumps(doc, indent=2), encoding="utf-8")
        try:
            code = verify(root, target, None, quiet=True)
            message = ""
        except Problem as exc:
            code, message = exc.code, str(exc)
        ok = code == expected_code and expect_in_message.lower() in message.lower()
        print(f"  {'ok  ' if ok else 'FAIL'} {label:<28} exit {code} (expected {expected_code})")
        if message:
            print(f"       {message.splitlines()[0][:150]}")
        return ok

    results = []
    results.append(damaged("empty-components", lambda d: d.update(components=[]), EXIT_DOCUMENT, "EMPTY"))
    results.append(damaged("no-components-key", lambda d: d.pop("components"), EXIT_DOCUMENT, "no components array"))
    results.append(damaged("wrong-format", lambda d: d.update(bomFormat="SPDX"), EXIT_DOCUMENT, "bomFormat"))
    results.append(damaged(
        "junit-dropped",
        lambda d: d.update(components=[c for c in d["components"] if "junit-jupiter@" not in c.get("purl", "")]),
        EXIT_CONTENT, ""))
    results.append(damaged(
        "modules-only",
        lambda d: d.update(components=[c for c in d["components"] if "/org.cometgui/" in c.get("purl", "")]),
        EXIT_CONTENT, ""))
    results.append(damaged(
        "purl-mangled",
        lambda d: d["components"][0].update(purl="not-a-purl"),
        EXIT_DOCUMENT, "purl"))

    # A missing file and a zero-byte file.
    for label, make in (("missing-file", lambda p: None), ("zero-bytes", lambda p: p.write_bytes(b""))):
        target = work / f"{label}.json"
        make(target)
        try:
            code = verify(root, target, None, quiet=True)
            message = ""
        except Problem as exc:
            code, message = exc.code, str(exc)
        ok = code == EXIT_DOCUMENT
        results.append(ok)
        print(f"  {'ok  ' if ok else 'FAIL'} {label:<28} exit {code} (expected {EXIT_DOCUMENT})")
        if message:
            print(f"       {message.splitlines()[0][:150]}")

    # A control: the real document must still pass, or the harness proves nothing.
    try:
        control = verify(root, json_path, xml_path, quiet=True)
    except Problem as exc:
        control = exc.code
        print(f"       {exc}")
    control_ok = control == EXIT_OK
    results.append(control_ok)
    print(f"  {'ok  ' if control_ok else 'FAIL'} {'control-real-sbom':<28} exit {control} (expected 0)")

    if all(results):
        print(f"\nsbom-verify: self-test OK -- {len(results) - 1} damaged SBOM(s) rejected, "
              f"the real one accepted.")
        return EXIT_OK
    print("\nsbom-verify: SELF-TEST FAILED -- a damaged SBOM was accepted, or the real one "
          "was rejected. This check is not falsifiable and must not be trusted.", file=sys.stderr)
    return EXIT_UNFALSIFIABLE


def main(argv: list[str]) -> int:
    here = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", default=str(here.parent.parent), help="repository root")
    parser.add_argument("--json", default=None, help="the CycloneDX JSON SBOM")
    parser.add_argument("--xml", default=None, help="the CycloneDX XML SBOM (cross-check)")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)

    root = Path(args.root).resolve()
    json_path = Path(args.json).resolve() if args.json else root / "_build" / "sbom" / "cometgui-sbom.json"
    xml_path = Path(args.xml).resolve() if args.xml else None

    try:
        if args.self_test:
            if xml_path is None:
                xml_path = json_path.with_suffix(".xml")
            return self_test(root, json_path, xml_path)
        return verify(root, json_path, xml_path)
    except Problem as exc:
        print(f"sbom-verify: {exc}", file=sys.stderr)
        return exc.code


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
