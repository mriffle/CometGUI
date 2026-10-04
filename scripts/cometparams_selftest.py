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
            artefact["version"] = UNRECORDED_RELEASE


#: A Comet release no version record describes. (2026.03.0 played this part
#: until COMET-2026-03 unit 1 gave it a record.)
UNRECORDED_RELEASE = "2099.01.0"

#: The release whose record carries the overrides the override cases damage.
OVERRIDING_RELEASE = "2026.03.0"

#: The overridden parameter most override damages are applied to: its record
#: replaces its default, choices, inline comment, help and help reference.
OVERRIDDEN = "index_search_type"


def _record(document: dict, version: str) -> dict:
    for record in document["versions"]:
        if record["version"] == version:
            return record
    raise HarnessError(f"the metadata has no version record {version!r} to damage")


def _override(document: dict, name: str = OVERRIDDEN) -> dict:
    for override in _record(document, OVERRIDING_RELEASE)["overrides"]:
        if override["name"] == name:
            return override
    raise HarnessError(f"the {OVERRIDING_RELEASE} record has no override for {name!r} to damage")


def _override_set(field, value, name=OVERRIDDEN):
    def damage(document):
        _override(document, name)[field] = value
    return damage


def _override_same_as_curated(field, name):
    def damage(document):
        _override(document, name)[field] = copy.deepcopy(_parameter(document, name)[field])
    return damage


def _override_revalue_choice(value, replacement):
    # Re-valued rather than dropped: without -1 the choices would equal the
    # curated ones and be refused for repeating them, a different diagnostic.
    def damage(document):
        hits = [c for c in _override(document)["choices"] if c["value"] == value]
        if len(hits) != 1:
            raise HarnessError(f"the override has no single choice {value!r} to re-value")
        hits[0]["value"] = replacement
    return damage


def _override_strip(document):
    override = _override(document)
    for field in cometparams.OVERRIDE_FIELDS:
        override.pop(field, None)


def _override_duplicate(document):
    record = _record(document, OVERRIDING_RELEASE)
    record["overrides"].append(copy.deepcopy(_override(document)))


def _override_for_unclaimed_release(document):
    # index_search_type is curated from 2026.02.2: Comet 2024.01.0 does not declare it.
    _record(document, "2024.01.0")["overrides"].append(
        {"name": OVERRIDDEN, "source": "https://example.org/", "default": "0"})


def _override_unmodelled(document):
    _override(document)["name"] = "ms1_mass_range"


def _alphabet_set(field, value, version=OVERRIDING_RELEASE):
    """Damage one release's residue alphabet (``variableModTuple.residueAlphabet``);
    a value of ``None`` removes the field."""
    def damage(document):
        alphabet = _record(document, version)["variableModTuple"]["residueAlphabet"]
        if value is None:
            alphabet.pop(field)
        else:
            alphabet[field] = value
    return damage


def _alphabet_removed(document):
    _record(document, OVERRIDING_RELEASE)["variableModTuple"].pop("residueAlphabet")


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
         f"installs Comet {UNRECORDED_RELEASE}, but the metadata has no version record"),
    ]
    over = f'versions[0] ({OVERRIDING_RELEASE}) overrides'
    cases += [
        ("override-unknown-field", "metadata", _override_set("since", "2026.03.0"),
         f'("{OVERRIDDEN}") has the field "since", which an override does not have'),
        ("override-unmodelled", "metadata", _override_unmodelled,
         "names 'ms1_mass_range', which is not a modelled parameter"),
        ("override-unclaimed-release", "metadata", _override_for_unclaimed_release,
         f"overrides {OVERRIDDEN} for Comet 2024.01.0, whose range (2026.02.2 to open) does not "
         "claim that release"),
        ("override-twice", "metadata", _override_duplicate,
         f'("{OVERRIDDEN}") overrides {OVERRIDDEN} a second time'),
        ("override-source-not-https", "metadata", _override_set("source", "Comet.cpp line 939"),
         "has \"source\" = 'Comet.cpp line 939'"),
        ("override-replaces-nothing", "metadata", _override_strip,
         f'("{OVERRIDDEN}") replaces no field'),
        ("override-repeats-curated-comment", "metadata",
         _override_same_as_curated("inlineComment", "decoy_search"),
         'repeats the curated "inlineComment" of decoy_search'),
        ("override-repeats-curated-choices", "metadata",
         _override_same_as_curated("choices", OVERRIDDEN),
         f'repeats the curated "choices" of {OVERRIDDEN}'),
        ("override-default-not-a-choice", "metadata", _override_set("default", "7"),
         f"leaves Comet {OVERRIDING_RELEASE} with the default '7', which is not one of that "
         "release's choices"),
        ("override-choices-drop-default", "metadata", _override_revalue_choice("-1", "2"),
         f"leaves Comet {OVERRIDING_RELEASE} with the default '-1', which is not one of that "
         "release's choices (2, 0, 1)"),
        ("override-choices-on-non-enum", "metadata",
         _override_set("choices", [{"value": "1", "label": "a"}, {"value": "2", "label": "b"}],
                       "add_U_selenocysteine"),
         "gives choices to DECIMAL, which is not an enumerated kind"),
        ("override-help-not-https", "metadata", _override_set("helpUrl", "http://example.org/"),
         "has \"helpUrl\" = 'http://example.org/'"),
        ("override-blank-help", "metadata", _override_set("shortHelp", "  "),
         f'{over}[2] ("{OVERRIDDEN}") has "shortHelp" = '),
    ]
    alphabet = f"versions[0] ({OVERRIDING_RELEASE}) variableModTuple.residueAlphabet"
    letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    cases += [
        ("alphabet-missing", "metadata", _alphabet_removed,
         f"{alphabet} is null; it must be an object"),
        ("alphabet-unknown-field", "metadata", _alphabet_set("since", "2026.03.0"),
         f"{alphabet} has the field 'since', which it does not have"),
        ("alphabet-empty", "metadata", _alphabet_set("characters", ""),
         f"{alphabet} has \"characters\" = ''; it must be a non-empty string"),
        ("alphabet-no-characters", "metadata", _alphabet_set("characters", None),
         f"{alphabet} has \"characters\" = None; it must be a non-empty string"),
        ("alphabet-unknown-character", "metadata", _alphabet_set("characters", letters + "nc#"),
         f"{alphabet} holds '#', which is neither a residue letter A-Z nor a terminal code"),
        ("alphabet-lower-case-letter", "metadata",
         _alphabet_set("characters", letters + "ncm"),
         f"{alphabet} holds 'm', which is neither a residue letter A-Z nor a terminal code"),
        ("alphabet-character-twice", "metadata", _alphabet_set("characters", letters + "nc^$^"),
         f"{alphabet} lists '^' twice"),
        ("alphabet-source-not-https", "metadata",
         _alphabet_set("source", "CometSearchManager.cpp line 1540"),
         f"{alphabet} has \"source\" = 'CometSearchManager.cpp line 1540'; it must be an "
         "https:// reference"),
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


def per_release_control(root: Path, clean: Path, files: dict) -> None:
    """A release's override reaches the page: rendered for both releases, the
    overridden entry carries each release's own default, choices and comment.

    Not a damage but a positive control: a generator that ignored the version
    records' overrides would render 2026.03.0 with 2026.02.2's facts and fail it.
    """
    manifest = json.loads(files["manifest"].read_text(encoding="utf-8"))
    linux = [a for a in manifest["artefacts"] if a.get("tool") == "comet"
             and a.get("os") == "linux" and a.get("arch") == "x86-64"]
    if not linux:
        raise HarnessError("per-release control: the manifest has no linux/x86-64 Comet row")
    if OVERRIDING_RELEASE not in {a["version"] for a in linux}:
        added = dict(linux[0], version=OVERRIDING_RELEASE, releaseTag=f"v{OVERRIDING_RELEASE}")
        manifest["artefacts"].append(added)
    control = clean.parent / "per-release"
    control.mkdir(parents=True)
    control_files = dict(files, manifest=control / files["manifest"].name)
    _write_json(control_files["manifest"], manifest)
    try:
        summary = _generate(root, control / "out", control_files)
    except cometparams.CometParamsError as error:
        raise HarnessError(f"per-release control: rejected: {error}") from None
    text = (control / "out" / cometparams.FRAGMENT_FILE).read_text(encoding="utf-8")
    start = text.index(f"{cometparams.ENTRY_LABEL_PREFIX}{OVERRIDDEN}:")
    entry = text[start:text.index(cometparams.ENTRY_LABEL_PREFIX, start + 1)]
    expected = [
        ":Default, Comet 2026.02.2: ``1``",
        f":Default, Comet {OVERRIDING_RELEASE}: ``-1``",
        ":Allowed values, Comet 2026.02.2:",
        f":Allowed values, Comet {OVERRIDING_RELEASE}:",
        f"{OVERRIDDEN} = -1                 # 0=create peptide index, 1=create fragment ion index",
        f"{OVERRIDDEN} = 1                  # 0=peptide index (PI_DB), 1=fragment ion index",
        "parameters_202603/index_search_type.html",
    ]
    missing = [needle for needle in expected if needle not in entry]
    allowed_2602 = "" if missing else entry[
        entry.index(":Allowed values, Comet 2026.02.2:"):
        entry.index(f":Allowed values, Comet {OVERRIDING_RELEASE}:")]
    if missing or "``-1``" in allowed_2602:
        raise HarnessError(
            f"per-release control: the {OVERRIDDEN} entry rendered for "
            f"{', '.join(summary['releases'])} does not carry each release's own facts; "
            f"missing {missing}, or -1 offered for 2026.02.2:\n{entry}"
        )
    print(f"    control   rendered for {', '.join(summary['releases'])}: {OVERRIDDEN} carries "
          f"each release's own default, choices and comment")

    # The residue alphabet is per release too: ^ and $ for 2026.03.0, never for 2026.02.2.
    start = text.index(f"{cometparams.ENTRY_LABEL_PREFIX}variable_mod01:")
    tuple_entry = text[start:text.index(cometparams.ENTRY_LABEL_PREFIX, start + 1)]
    older = "Residues: any combination of ``A``-``Z``, ``n`` (N-terminus), ``c`` (C-terminus) ("
    newer = ("Residues: any combination of ``A``-``Z``, ``n`` (N-terminus), ``c`` (C-terminus), "
             "``^`` (protein N-terminus), ``$`` (protein C-terminus) (")
    at_older = tuple_entry.find("Comet 2026.02.2: 8 space-separated fields")
    at_newer = tuple_entry.find(f"Comet {OVERRIDING_RELEASE}: 8 space-separated fields")
    if at_older < 0 or at_newer < 0 or older not in tuple_entry[at_older:at_newer] \
            or newer not in tuple_entry[at_newer:] or "``^``" in tuple_entry[at_older:at_newer]:
        raise HarnessError(
            "per-release control: variable_mod01 does not state each release's own residue "
            f"alphabet (^ and $ for {OVERRIDING_RELEASE} only):\n{tuple_entry}"
        )
    print(f"    control   variable_mod01 states each release's residue alphabet: ^ and $ for "
          f"{OVERRIDING_RELEASE} only")


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
    per_release_control(root, clean, files)

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
