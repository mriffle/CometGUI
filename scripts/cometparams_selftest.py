#!/usr/bin/env python3
"""Falsifiability harness for ``scripts/cometparams.py`` (``R-DOC-04``, Phase 06 gate item 8).

CONTRIBUTING.rst, *Gate conventions*: a gate that has never been seen to fail
has not been shown to work; injure a copy, never the real file; and a control
whose defect was not actually injected is a harness failure, not a pass. This
is the shape of Phase 05's control G (``scripts/verify-install-gates.sh``) for
the Comet parameter reference.

Part 1 -- the generator, on damaged COPIES of its inputs (``--work``/generator):

* a clean copy (re-serialised, so formatting alone is shown not to matter)
  renders one entry per modelled parameter;
* every damage below is rejected with **its own** diagnostic -- the
  ``CometParamsError`` text naming the parameter and the field -- and writes no
  fragment. A damage that did not change the copy is a harness failure.

Part 2 -- the real Sphinx ``builder-inited`` hook, in a copy of the project made
by ``scripts/traceability/selftest.py``'s ``copy_project`` (so this also proves
that sandbox carries the generator's inputs):

* the clean copy builds under ``-n -W`` and its log carries the hook's count
  line with this metadata's SHA-256, and the HTML has one section per parameter;
* a parameter missing a field, and a parameter's entry removed, each fail the
  documentation build with the generator's own diagnostic, carried by the hook;
* a generator that returns without writing (with yesterday's fragment still on
  disk) fails the build;
* a generator that drops an entry after its own coverage check fails the build
  on the hook's independent count;
* every damaged file is restored and proved byte-identical by SHA-256, and the
  restored copy builds clean again.

The working tree is never touched. Run through ``scripts/ci/docs-build.sh
--self-test``, or alone::

    python3 scripts/cometparams_selftest.py --root . --work _build/cometparams-selftest

Exit status: 0 every case behaved; 2 misuse or no Sphinx; 4 a case was not
caught as expected, or the harness could not prove its own injection.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import cometparams  # noqa: E402  (the module under test, from this same scripts/)

#: The parameter most damages are applied to: an integer enum with labelled
#: choices, related parameters, an inline comment and a preset effect.
VICTIM = "isotope_error"

#: The parameter whose entry is removed. spectrum_batch_size lists it as related,
#: so a removal is refused even without the check against Comet's own
#: declarations -- which is why the case requires that check's own diagnostic.
REMOVED = "num_threads"


class HarnessError(Exception):
    """The harness could not do its job -- never reported as a pass."""


def _sha256(path: Path) -> str:
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def _parameter(document: dict, name: str) -> dict:
    for parameter in document["parameters"]:
        if parameter["name"] == name:
            return parameter
    raise HarnessError(f"the metadata has no parameter {name!r} to damage")


def _drop_field(field):
    def damage(document):
        del _parameter(document, VICTIM)[field]
    return damage


def _remove_entry(document):
    before = len(document["parameters"])
    document["parameters"] = [p for p in document["parameters"] if p["name"] != REMOVED]
    if len(document["parameters"]) != before - 1:
        raise HarnessError(f"removing {REMOVED} did not remove exactly one entry")


def _set(field, value):
    def damage(document):
        _parameter(document, VICTIM)[field] = value
    return damage


def _choice_label(value):
    def damage(document):
        choice = _parameter(document, VICTIM)["choices"][-1]
        if value is None:
            del choice["label"]
        else:
            choice["label"] = value
    return damage


def _related(document):
    _parameter(document, VICTIM)["related"].append("ms1_mass_range")


def _duplicate(document):
    document["parameters"].append(copy.deepcopy(_parameter(document, VICTIM)))


def _preset_unknown(document):
    document["presets"][0]["deltas"][0]["parameter"] = "ms1_mass_range"


def _unknown_release(document):
    for artefact in document["artefacts"]:
        if artefact.get("tool") == "comet":
            artefact["version"] = "2026.03.0"


def generator_cases():
    """(id, input, damage, expected inner diagnostic) for part 1."""
    where = f'("{VICTIM}")'
    cases = [
        ("entry-removed", "metadata", _remove_entry,
         f'declares "{REMOVED}", which the metadata neither models nor allow-lists'),
    ]
    for field, meaning in cometparams.REQUIRED_PARAMETER_FIELDS:
        # Without its name a parameter is named by its index alone.
        named = f'is missing the field "{field}" ({meaning})' if field == "name" \
            else f'{where} is missing the field "{field}"'
        cases.append((f"field-missing-{field}", "metadata", _drop_field(field), named))
    cases += [
        ("unknown-category", "metadata", _set("category", "spectral_magic"),
         f"{where} has the category 'spectral_magic', which is not one of the metadata's "
         "categories"),
        ("unknown-kind", "metadata", _set("kind", "COLOUR"),
         f"{where} has the kind 'COLOUR', which this generator does not know"),
        ("choice-without-label", "metadata", _choice_label(None),
         f'{where} choices[7] (value "7") has no label'),
        ("choice-with-blank-label", "metadata", _choice_label("   "),
         f'{where} choices[7] (value "7") has no label'),
        ("related-not-a-parameter", "metadata", _related,
         f'{where} lists the related parameter "ms1_mass_range", which is not a modelled '
         "parameter"),
        ("duplicate-name", "metadata", _duplicate,
         f'is the second parameter named "{VICTIM}"'),
        ("help-not-https", "metadata", _set("helpUrl", "http://example.org/"),
         f"{where} has \"helpUrl\" = 'http://example.org/'"),
        ("preset-names-no-parameter", "presets", _preset_unknown,
         "deltas[0] sets 'ms1_mass_range', which is not a modelled parameter"),
        ("release-without-version-record", "manifest", _unknown_release,
         "installs Comet 2026.03.0, but the metadata has no version record"),
    ]
    return cases


def _inputs(root: Path):
    return {
        "metadata": root / cometparams.METADATA_RELATIVE_PATH,
        "presets": root / cometparams.PRESETS_RELATIVE_PATH,
        "manifest": root / cometparams.MANIFEST_RELATIVE_PATH,
    }


def _write_json(path: Path, document) -> None:
    path.write_text(json.dumps(document, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def _generate(root, out_dir, files):
    return cometparams.generate(root, out_dir, metadata_file=files["metadata"],
                                presets_file=files["presets"], manifest_file=files["manifest"])


def part_one(root: Path, work: Path) -> int:
    """The generator over damaged copies. Returns the number of damages caught."""
    print("\n=== part 1: the generator, on damaged copies of its inputs ===")
    originals = _inputs(root)
    base = work / "generator"
    shutil.rmtree(base, ignore_errors=True)

    # Control: every input copied and re-serialised, untouched otherwise.
    clean = base / "clean"
    clean.mkdir(parents=True)
    files = {}
    for key, source in originals.items():
        files[key] = clean / source.name
        _write_json(files[key], json.loads(source.read_text(encoding="utf-8")))
    try:
        summary = _generate(root, clean / "out", files)
    except cometparams.CometParamsError as error:
        raise HarnessError(f"control: the clean copy was rejected: {error}") from None
    expected = len(json.loads(originals["metadata"].read_text(encoding="utf-8"))["parameters"])
    if summary["entries"] != expected or summary["parameters"] != expected:
        raise HarnessError(f"control: {summary['entries']} entries for {expected} parameters")
    print(f"    control   the clean (re-serialised) copy renders: {cometparams.describe(summary)}")

    caught = 0
    for case_id, target, damage, diagnostic in generator_cases():
        case_dir = base / case_id
        case_dir.mkdir(parents=True)
        case_files = {}
        for key, source in files.items():
            case_files[key] = case_dir / source.name
            shutil.copy2(source, case_files[key])
        document = json.loads(case_files[target].read_text(encoding="utf-8"))
        pristine = copy.deepcopy(document)
        damage(document)
        if document == pristine:
            raise HarnessError(f"{case_id}: the damage changed nothing")
        _write_json(case_files[target], document)
        if _sha256(case_files[target]) == _sha256(files[target]):
            raise HarnessError(f"{case_id}: the damaged copy is byte-identical to the clean one")
        out_dir = case_dir / "out"
        try:
            _generate(root, out_dir, case_files)
        except cometparams.CometParamsError as error:
            message = str(error)
        else:
            raise HarnessError(f"{case_id}: THE GENERATOR ACCEPTED THE DAMAGE ({target})")
        if diagnostic not in message:
            raise HarnessError(
                f"{case_id}: rejected, but not with its own diagnostic.\n"
                f"    expected: {diagnostic}\n    got:      {message}"
            )
        if (out_dir / cometparams.FRAGMENT_FILE).exists():
            raise HarnessError(f"{case_id}: the input was rejected AND a fragment was written")
        caught += 1
        shown = message.replace(str(case_dir), "<copy>").replace(str(root), "<root>")
        print(f"    {case_id:32} rejected: {shown}")
    return caught


# --------------------------------------------------------------------------
# Part 2: through the Sphinx hook
# --------------------------------------------------------------------------


def _sphinx(sphinx: Path, sandbox: Path, log: Path) -> int:
    shutil.rmtree(sandbox / "docs" / "_build", ignore_errors=True)
    with log.open("w", encoding="utf-8") as handle:
        result = subprocess.run(
            [str(sphinx), "-n", "-W", "-b", "html", str(sandbox / "docs"),
             str(sandbox / "docs" / "_build" / "html")],
            stdout=handle, stderr=subprocess.STDOUT, check=False,
        )
    return result.returncode


def _replace_once(path: Path, anchor: str, replacement: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if text.count(anchor) != 1:
        raise HarnessError(f"{label}: the anchor occurs {text.count(anchor)} time(s) in {path}, not once")
    if replacement == anchor:
        raise HarnessError(f"{label}: the replacement equals the anchor")
    path.write_text(text.replace(anchor, replacement, 1), encoding="utf-8")


def _expect_red(case_id, rc, log: Path, needles, sandbox: Path) -> None:
    text = log.read_text(encoding="utf-8")
    if rc == 0:
        raise HarnessError(f"{case_id}: THE DOCUMENTATION BUILD PASSED (log {log})")
    for needle in needles:
        if needle not in text:
            raise HarnessError(
                f"{case_id}: the build failed (exit {rc}), but its log {log} does not carry "
                f"{needle!r} -- it failed for some other reason"
            )
    line = next((l for l in text.splitlines() if needles[-1] in l), needles[-1])
    print(f"    {case_id:32} build failed (exit {rc}): "
          f"{line.strip().replace(str(sandbox), '<sandbox>')}")


def part_two(root: Path, work: Path, sphinx: Path) -> int:
    """The real builder-inited hook, in a traceability-style project copy."""
    from traceability.selftest import copy_project  # the sandbox the docs gate also uses

    print("\n=== part 2: the Sphinx builder-inited hook, in a copy made by "
          "traceability.selftest.copy_project ===")
    sandbox = copy_project(root, work / "sphinx-sandbox")
    logs = work / "sphinx-logs"
    shutil.rmtree(logs, ignore_errors=True)
    logs.mkdir(parents=True)
    pristine = work / "sphinx-pristine"
    shutil.rmtree(pristine, ignore_errors=True)
    pristine.mkdir(parents=True)
    metadata = sandbox / cometparams.METADATA_RELATIVE_PATH
    generator = sandbox / "scripts" / "cometparams.py"
    if not metadata.is_file():
        raise HarnessError(
            f"copy_project did not carry {cometparams.METADATA_RELATIVE_PATH}; the traceability "
            "sandbox lacks the generator's input, so every case below would fail for that reason"
        )
    for path in (metadata, generator):
        shutil.copy2(path, pristine / path.name)
    digest = _sha256(metadata)
    if digest != _sha256(root / cometparams.METADATA_RELATIVE_PATH):
        raise HarnessError("the sandbox metadata is not the working tree's")
    expected = len(json.loads(metadata.read_text(encoding="utf-8"))["parameters"])

    def restore(path):
        shutil.copy2(pristine / path.name, path)
        if _sha256(path) != _sha256(pristine / path.name):
            raise HarnessError(f"{path} is not byte-identical to its pristine copy after restoring")

    def clean_build(case_id):
        log = logs / f"{case_id}.log"
        rc = _sphinx(sphinx, sandbox, log)
        text = log.read_text(encoding="utf-8")
        if rc != 0:
            raise HarnessError(f"{case_id}: the clean sandbox does not build (exit {rc}; {log})")
        count_line = (f"[cometparams] wrote _generated/comet-parameters.rsti: {expected} parameter "
                      f"entries = {expected} modelled parameters")
        if count_line not in text or f"metadata sha256 {digest}" not in text:
            raise HarnessError(f"{case_id}: the build passed without the hook's count line ({log})")
        page = sandbox / "docs" / "_build" / "html" / "reference" / "comet_parameters_generated.html"
        sections = len(re.findall(r'<span id="comet-param-[a-z0-9-]+"></span><h3>',
                                  page.read_text(encoding="utf-8")))
        if sections != expected:
            raise HarnessError(f"{case_id}: the HTML has {sections} parameter sections, not {expected}")
        print(f"    {case_id:32} builds clean; hook logged {expected} entries, "
              f"sha256 {digest[:12]}...; {sections} parameter sections in the HTML")

    clean_build("hook-clean")
    stale = sandbox / "docs" / cometparams.OUTPUT_RELATIVE_DIR / cometparams.FRAGMENT_FILE
    shutil.copy2(stale, pristine / "yesterday.rsti")
    caught = 0

    # A field missing, through the hook.
    case_id = "hook-field-missing"
    document = json.loads(metadata.read_text(encoding="utf-8"))
    del _parameter(document, VICTIM)["displayName"]
    _write_json(metadata, document)
    if _sha256(metadata) == digest:
        raise HarnessError(f"{case_id}: the damage did not change the file")
    log = logs / f"{case_id}.log"
    _expect_red(case_id, _sphinx(sphinx, sandbox, log), log, [
        "cometparams: the Comet parameter metadata was rejected, so the documentation build fails",
        f'("{VICTIM}") is missing the field "displayName"',
    ], sandbox)
    restore(metadata)
    caught += 1

    # A parameter's entry removed, through the hook.
    case_id = "hook-entry-removed"
    document = json.loads(metadata.read_text(encoding="utf-8"))
    _remove_entry(document)
    _write_json(metadata, document)
    log = logs / f"{case_id}.log"
    _expect_red(case_id, _sphinx(sphinx, sandbox, log), log, [
        "cometparams: the Comet parameter metadata was rejected, so the documentation build fails",
        f'declares "{REMOVED}", which the metadata neither models nor allow-lists',
    ], sandbox)
    restore(metadata)
    caught += 1

    # A generator that writes nothing, with the previous build's fragment on disk.
    case_id = "hook-generator-writes-nothing"
    shutil.copy2(pristine / "yesterday.rsti", stale)
    if not stale.is_file() or stale.stat().st_size == 0:
        raise HarnessError(f"{case_id}: no fragment from the clean build is on disk to be stale")
    _replace_once(generator, "    inputs = load_all(root, metadata_file, presets_file, manifest_file)\n",
                  "    return {}\n    inputs = load_all(root, metadata_file, presets_file, manifest_file)\n",
                  case_id)
    log = logs / f"{case_id}.log"
    _expect_red(case_id, _sphinx(sphinx, sandbox, log), log, [
        "cometparams: the generator returned without writing",
    ], sandbox)
    restore(generator)
    caught += 1

    # A generator that drops an entry after its own check: the hook's count.
    case_id = "hook-entry-dropped-after-check"
    anchor = '    with open(target, "w", encoding="utf-8", newline="\\n") as handle:\n'
    _replace_once(generator, anchor,
                  f'    text = text.replace("{cometparams.ENTRY_LABEL_PREFIX}{REMOVED}:\\n", "", 1)\n'
                  + anchor, case_id)
    log = logs / f"{case_id}.log"
    _expect_red(case_id, _sphinx(sphinx, sandbox, log), log, [
        f"parameter entries, but the metadata models {expected} parameters: missing ['{REMOVED}']",
    ], sandbox)
    restore(generator)
    caught += 1

    clean_build("hook-clean-after-restore")
    return caught


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog="cometparams_selftest.py", description=__doc__.split("\n")[0])
    parser.add_argument("--root", required=True, type=Path, help="the project to copy from")
    parser.add_argument("--work", required=True, type=Path, help="directory for the copies")
    parser.add_argument("--sphinx", type=Path, default=None,
                        help="sphinx-build (default: <root>/.venv/bin/sphinx-build)")
    args = parser.parse_args(argv)
    root = args.root.resolve()
    work = args.work.resolve()
    sphinx = args.sphinx or root / ".venv" / "bin" / "sphinx-build"
    if not sphinx.is_file():
        print(f"cometparams-selftest: no sphinx-build at {sphinx}; part 2 cannot run, and a "
              "self-test with half its cases missing is not a pass", file=sys.stderr)
        return 2
    work.mkdir(parents=True, exist_ok=True)
    print(f"cometparams-selftest: project {root}")
    print(f"cometparams-selftest: copies  {work} (the working tree is never touched)")
    try:
        generator = part_one(root, work)
        hook = part_two(root, work, sphinx)
    except HarnessError as error:
        print(f"\ncometparams-selftest: HARNESS FAILURE -- {error}", file=sys.stderr)
        return 4
    print(f"\ncometparams-selftest: OK -- {generator} damaged input(s) rejected by the generator "
          f"and {hook} defect(s) failing the documentation build, each with its own diagnostic; "
          "the clean inputs accepted before and after.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
