#!/usr/bin/env python3
"""Render the Comet parameter reference (``R-DOC-04``) from the curated metadata.

Phase 06 unit 7. ``R-DOC-04``: "The Comet parameter schema shall generate
``reference/comet_parameters_generated.rst`` so that user documentation and GUI
metadata cannot silently diverge." The GUI's metadata is one JSON file in
``cometgui-params-comet`` (decision D6-1 in ``handoffs/PHASE-06-worklog.rst``),
read by ``org.cometgui.params.comet.schema.MetadataLoader``. This generator reads
**that same file** -- there is no second copy -- and renders one entry per
modelled parameter with every field ``R-DOC-04`` names: the Comet name, the GUI
display name, the category, the type, the default for the versioned schema,
the allowed values or range, the description, the serialisation form, version
availability, related parameters and preset effects.

It runs during the documentation build, exactly as ``scripts/toolmatrix.py``
does: ``docs/conf.py`` calls :func:`generate` from a ``builder-inited`` handler
and ``docs/reference/comet_parameters_generated.rst`` pulls the fragment in with
``.. include::``. The fragment is gitignored, so nothing generated is committed,
and a metadata file the generator refuses is a **documentation build failure**
rather than a page that is quietly wrong.

What it reads, all relative to the repository root:

* the metadata, ``cometgui-params-comet/src/main/resources/org/cometgui/params/
  comet/schema/comet-parameters.json``;
* the built-in presets beside it, ``comet-presets.json``, for preset effects;
* ``manifests/tools.json``, for the Comet versions CometGUI installs (the
  release matrix) -- the page documents those, and only those, as supported;
* for each of those versions, the real ``comet -q`` output checked in as a test
  fixture (``cometgui-params-comet/src/test/resources/fixtures/comet/<version>/
  linux-x86-64/comet-q.params``), checked against its ``SHA256SUMS``. It is the
  only complete list of what that Comet declares, so it is what proves that no
  parameter is missing from the metadata (and hence from this page), and it
  holds Comet's default ``[COMET_ENZYME_INFO]`` table, which the metadata does
  not curate and which the enzyme-reference parameters' allowed values are.

Run alone, it shows its reaction to an input::

    python3 scripts/cometparams.py --check
    python3 scripts/cometparams.py --metadata <an edited copy> --out-dir <a scratch dir>

Standard library only, deliberately: ``docs/requirements.txt`` is what Read the
Docs installs, and Read the Docs has no JDK (D6-1), so the generator can be
neither Java nor a Python package the gate does not pin.

Exit status of the command-line form:

==  =============================================================
0   the inputs validated, and (unless ``--check``) were rendered
1   an input was rejected -- the message names the parameter and field
2   misuse
==  =============================================================
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

#: The curated metadata, relative to the repository root. One file, read by Java and by this.
METADATA_RELATIVE_PATH = Path(
    "cometgui-params-comet/src/main/resources/org/cometgui/params/comet/schema/comet-parameters.json"
)

#: The built-in presets, beside the metadata.
PRESETS_RELATIVE_PATH = METADATA_RELATIVE_PATH.with_name("comet-presets.json")

#: The tool artefact manifest: which Comet versions CometGUI installs.
MANIFEST_RELATIVE_PATH = Path("manifests") / "tools.json"

#: The real ``comet -q`` output per version, captured by Phase 06 unit 1 from the
#: pinned binary and proved byte-equal to it by ``CometFixtureRealBinaryTest``.
FIXTURE_RELATIVE_DIR = Path("cometgui-params-comet/src/test/resources/fixtures/comet")

#: The one platform whose binary has been executed here (and so has fixtures).
FIXTURE_PLATFORM = "linux-x86-64"

#: Where the fragment is written, relative to the documentation source root. Gitignored.
OUTPUT_RELATIVE_DIR = Path("_generated")

#: The fragment ``docs/reference/comet_parameters_generated.rst`` includes.
FRAGMENT_FILE = "comet-parameters.rsti"

#: Every entry starts with this label, followed by the parameter's Comet name. The
#: coverage checks -- here, in ``docs/conf.py`` and in the Java agreement test --
#: count these lines, so it is the one marker of "an entry".
ENTRY_LABEL_PREFIX = ".. _comet-param-"

#: The label of the enzyme-table section.
ENZYME_LABEL = "comet-enzyme-table"

#: The label of the table of version-scoped validation rules.
RULE_SEVERITIES_LABEL = "comet-rule-severities"

#: The fields every parameter object must carry. Each one feeds a field of
#: ``R-DOC-04`` (or the serialisation form the canonical writer emits), so a
#: parameter missing one is refused **naming the field**: a blank cell reads as a
#: fact, and "the page came out short" is not a diagnosis.
REQUIRED_PARAMETER_FIELDS = (
    ("name", "the Comet parameter name"),
    ("displayName", "the GUI display name"),
    ("category", "the category"),
    ("kind", "the type"),
    ("visibility", "the editor level"),
    ("default", "the default for the versioned schema"),
    ("min", "the allowed range"),
    ("max", "the allowed range"),
    ("choices", "the allowed values"),
    ("shortHelp", "the scientific description"),
    ("inlineComment", "the serialisation form (the default line's comment)"),
    ("helpUrl", "the scientific description (its upstream reference)"),
    ("versions", "version availability"),
    ("serialization", "the serialisation form"),
    ("validators", "the validation rules"),
    ("aliases", "the search aliases"),
    ("related", "related parameters"),
)

#: The value kinds ``org.cometgui.params.comet.schema.ValueKind`` defines, with
#: the words this page uses for each. A kind the Java side gains and this
#: generator does not know is refused, never rendered as a bare constant.
KINDS = {
    "INTEGER": "whole number",
    "DECIMAL": "decimal number",
    "STRING": "text",
    "BOOLEAN_FLAG": "on/off flag",
    "INTEGER_ENUM": "one of a fixed set of numbers",
    "STRING_ENUM": "one of a fixed set of words",
    "FILE_PATH": "file path",
    "INTEGER_RANGE": "two whole numbers on one line",
    "DECIMAL_RANGE": "two decimal numbers on one line",
    "DECIMAL_LIST": "list of decimal numbers",
    "TOLERANCE_PAIR_MEMBER": "one bound of the signed precursor tolerance pair",
    "VARIABLE_MOD_TUPLE": "variable-modification tuple",
    "ENZYME_REFERENCE": "number of a row in the enzyme table",
    "ION_SERIES_FLAG": "on/off flag for one fragment-ion series",
}

#: ``org.cometgui.params.comet.schema.SerializationRule``, in words.
SERIALIZATIONS = {
    "SINGLE_VALUE": "one value",
    "EMPTY_ALLOWED": "one value, which may be empty (written ``name =``)",
    "TWO_VALUES": "two values separated by one space",
    "VALUE_LIST": "zero or more values separated by spaces; empty is a value",
    "TUPLE": "a tuple of space-separated fields",
}

#: ``org.cometgui.params.comet.schema.VisibilityLevel``, in words.
VISIBILITY = {
    "ESSENTIALS": "Essentials",
    "ADVANCED": "Advanced",
    "EXPERT": "Expert",
}

#: ``org.cometgui.params.comet.schema.VariableModField``, in Comet's words.
TUPLE_FIELDS = {
    "MASS": "mass difference",
    "RESIDUES": "residues and terminal codes",
    "BINARY_GROUP": "binary group",
    "COUNT": "count per peptide",
    "TERMINAL_DISTANCE": "distance from a terminus",
    "TERMINUS": "which terminus",
    "REQUIRED": "required / exclusive",
    "NEUTRAL_LOSS": "fragment neutral loss",
}

TUPLE_KINDS = {"DECIMAL": "decimal", "INTEGER": "whole number", "RESIDUES": "residue letters"}

#: ``org.cometgui.params.comet.schema.TerminalCode``: what each terminal code of a
#: residue token means. The vocabulary only -- which codes a release accepts is its
#: ``variableModTuple.residueAlphabet``, and an alphabet character that is neither
#: a letter A-Z nor one of these is refused.
TERMINAL_CODES = {
    "n": "N-terminus",
    "c": "C-terminus",
    "^": "protein N-terminus",
    "$": "protein C-terminus",
}

_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

_NUMERIC_KINDS = ("INTEGER", "DECIMAL", "INTEGER_RANGE", "DECIMAL_RANGE", "DECIMAL_LIST")

#: What a version record's ``overrides`` entry may replace of a parameter's curated
#: definition (``org.cometgui.params.comet.schema.ParameterOverride.FIELDS``). Each
#: entry also carries ``name`` and ``source``; any other field is refused.
OVERRIDE_FIELDS = ("default", "choices", "inlineComment", "shortHelp", "helpUrl")

#: A version record's ``ruleSeverities`` entry (``org.cometgui.params.comet.schema
#: .RuleSeverity``): the rule's stable identifier, what a finding of it is for that
#: release, and the ``https://`` reference to the behaviour that level encodes.
RULE_SEVERITY_FIELDS = ("rule", "severity", "source")

#: A version record's ``indexFormats`` (``org.cometgui.params.comet.schema.IndexFormats``):
#: the formats of existing ``.idx`` file the release can search -- each the N of a first
#: line ``Comet index database vN.`` -- and the ``https://`` reference to its reader.
INDEX_FORMAT_FIELDS = ("readable", "source")

#: ``RuleSeverity.Level``, with the words the reference uses for each.
RULE_LEVELS = {"ERROR": "error", "WARNING": "warning", "OFF": "not reported"}

#: ``RuleSeverity.RULE_ID``.
_RULE_ID = re.compile(r"[a-z][a-z_]*\.[a-z][a-z_]*")

_GENERATED_HEADER = (
    ".. Generated by scripts/cometparams.py during the documentation build (see\n"
    "   docs/conf.py). Not committed and never edited by hand: the parameter\n"
    "   metadata is one JSON file read by the Java schema and by this generator,\n"
    "   and a page typed beside it would be a second copy. To change what this\n"
    "   says, change the metadata or the generator.\n"
)


class CometParamsError(Exception):
    """An input was rejected, or the rendered text failed its own coverage check.

    Carried out of :func:`generate` so that ``docs/conf.py`` can turn it into a
    Sphinx ``ExtensionError`` and fail the documentation build with it.
    """


# --------------------------------------------------------------------------
# Reading
# --------------------------------------------------------------------------


def _read_json(path: Path, what: str):
    """Read one JSON file, returning ``(document, sha256)``."""
    try:
        raw = Path(path).read_bytes()
    except OSError as error:
        raise CometParamsError(f"cannot read {what} at {path}: {error}") from None
    try:
        document = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise CometParamsError(f"{path} ({what}) is not valid UTF-8 JSON: {error}") from None
    if not isinstance(document, dict):
        raise CometParamsError(f"{path} ({what}) must be a JSON object, not {_kind(document)}")
    return document, hashlib.sha256(raw).hexdigest()


def _kind(value) -> str:
    """Name a JSON value's type for a diagnostic."""
    if value is None:
        return "null"
    return {
        dict: "an object",
        list: "an array",
        str: "a string",
        bool: "a boolean",
        int: "a number",
        float: "a number",
    }.get(type(value), type(value).__name__)


def _version_key(text: str):
    """``2026.02.2`` -> ``(2026, 2, 2)``, for ordering curated versions."""
    if not isinstance(text, str) or not re.fullmatch(r"\d+(\.\d+)*", text):
        return None
    return tuple(int(part) for part in text.split("."))


def release_versions(manifest_file: Path):
    """The Comet versions ``manifests/tools.json`` installs, oldest first."""
    manifest, digest = _read_json(manifest_file, "the tool artefact manifest")
    artefacts = manifest.get("artefacts")
    if not isinstance(artefacts, list):
        raise CometParamsError(f"{manifest_file}: no \"artefacts\" array")
    versions = []
    for index, artefact in enumerate(artefacts):
        if isinstance(artefact, dict) and artefact.get("tool") == "comet":
            version = artefact.get("version")
            if _version_key(version) is None:
                raise CometParamsError(
                    f"{manifest_file}: artefacts[{index}] is a Comet record with the version "
                    f"{version!r}, which is not a dotted version number"
                )
            if version not in versions:
                versions.append(version)
    if not versions:
        raise CometParamsError(
            f"{manifest_file} names no Comet version, so there is no release to document. A "
            "parameter reference for no Comet at all would be a claim, not an absence."
        )
    return sorted(versions, key=_version_key), digest


def read_dump(root: Path, version: str, marker: str):
    """Read the real ``comet -q`` fixture of one version: declared names and enzyme rows.

    The fixture's SHA-256 must equal the one its ``SHA256SUMS`` records (the
    file the real-binary test proves equal to the binary's output), and its
    first line must be the version marker the metadata records for that version.
    """
    directory = Path(root) / FIXTURE_RELATIVE_DIR / version / FIXTURE_PLATFORM
    dump = directory / "comet-q.params"
    sums = directory / "SHA256SUMS"
    try:
        raw = dump.read_bytes()
        sums_text = sums.read_text(encoding="utf-8")
    except OSError as error:
        raise CometParamsError(
            f"Comet {version} is in the release matrix, but its real comet -q output cannot be "
            f"read ({error}). Phase 06 unit 1 captures it from the pinned binary; without it "
            "nothing can show that the metadata covers every parameter that Comet declares."
        ) from None
    recorded = None
    for line in sums_text.splitlines():
        parts = line.split()
        if len(parts) == 2 and parts[1].lstrip("*") == dump.name:
            recorded = parts[0]
    digest = hashlib.sha256(raw).hexdigest()
    if recorded != digest:
        raise CometParamsError(
            f"{dump} has SHA-256 {digest}, but {sums} records {recorded!r} for it. The "
            "fixture is not the captured output of the binary, so it proves nothing about "
            "which parameters Comet declares."
        )
    text = raw.decode("utf-8")
    lines = text.split("\n")
    expected_marker = f"# comet_version {marker}"
    if not lines or lines[0].rstrip("\r") != expected_marker:
        raise CometParamsError(
            f"{dump} starts {lines[0]!r}, not {expected_marker!r}, the marker the metadata "
            f"records for Comet {version}"
        )
    declared = []
    rows = []
    in_table = False
    for line in lines[1:]:
        line = line.rstrip("\r")
        if in_table:
            if line.strip():
                rows.append(line)
            continue
        if line.strip() == "[COMET_ENZYME_INFO]":
            in_table = True
            continue
        match = re.match(r"^([A-Za-z0-9_]+)\s*=", line)
        if match:
            declared.append(match.group(1))
    if not declared or not rows:
        raise CometParamsError(
            f"{dump} holds {len(declared)} declaration(s) and {len(rows)} enzyme row(s); a real "
            "comet -q output holds both"
        )
    parsed_rows = []
    for row in rows:
        fields = row.split()
        if len(fields) != 5 or not re.fullmatch(r"\d+\.", fields[0]) or fields[2] not in ("0", "1"):
            raise CometParamsError(
                f"{dump}: the enzyme row {row!r} is not 'number. name sense cut no-cut'"
            )
        parsed_rows.append(
            {
                "line": row,
                "number": fields[0][:-1],
                "name": fields[1],
                "sense": fields[2],
                "cut": fields[3],
                "nocut": fields[4],
            }
        )
    return {"declared": declared, "rows": parsed_rows, "path": dump, "sha256": digest}


# --------------------------------------------------------------------------
# Validating
# --------------------------------------------------------------------------


def _where(index: int, parameter) -> str:
    """Name a parameter for a diagnostic, by its Comet name where it has one."""
    if isinstance(parameter, dict) and isinstance(parameter.get("name"), str):
        return f"parameters[{index}] (\"{parameter['name']}\")"
    return f"parameters[{index}]"


def _text(where: str, field: str, value, path: Path) -> str:
    if not isinstance(value, str) or not value.strip():
        raise CometParamsError(
            f"{path}: {where} has \"{field}\" = {value!r}; it must be non-blank text, because "
            "the reference prints it"
        )
    return value


def validate(metadata: dict, presets: dict, metadata_path: Path, presets_path: Path,
             releases, dumps) -> None:
    """Refuse metadata or presets this page could not render completely and truthfully.

    Every message names the file, the parameter (or section) and the field.
    """
    path = metadata_path
    if metadata.get("schemaVersion") != 1:
        raise CometParamsError(
            f"{path}: \"schemaVersion\" is {metadata.get('schemaVersion')!r}; this generator "
            "renders version 1 only"
        )
    for section, kind in (("versions", list), ("categories", list), ("enzymeTable", dict),
                          ("internal", list), ("parameters", list)):
        if not isinstance(metadata.get(section), kind):
            raise CometParamsError(
                f"{path}: the section \"{section}\" must be {'an array' if kind is list else 'an object'}, "
                f"not {_kind(metadata.get(section))}"
            )

    # Versions: every release version needs a record, every record a marker.
    records = {}
    for index, record in enumerate(metadata["versions"]):
        if not isinstance(record, dict) or _version_key(record.get("version")) is None:
            raise CometParamsError(f"{path}: versions[{index}] has no dotted \"version\"")
        _text(f"versions[{index}] ({record['version']})", "marker", record.get("marker"), path)
        if not isinstance(record.get("overrides"), list):
            raise CometParamsError(
                f"{path}: versions[{index}] ({record['version']}) has \"overrides\" = "
                f"{_kind(record.get('overrides'))}; it is the list of what that release says "
                "differently about a parameter"
            )
        layout = record.get("variableModTuple")
        if not isinstance(layout, dict) or not isinstance(layout.get("fields"), list):
            raise CometParamsError(
                f"{path}: versions[{index}] ({record['version']}) has no variableModTuple.fields"
            )
        for position, field in enumerate(layout["fields"]):
            if not isinstance(field, dict) or field.get("field") not in TUPLE_FIELDS \
                    or field.get("kind") not in TUPLE_KINDS or not isinstance(field.get("pair"), bool):
                raise CometParamsError(
                    f"{path}: versions[{index}] ({record['version']}) variableModTuple.fields"
                    f"[{position}] = {field!r} is not a tuple field this generator knows "
                    f"({', '.join(TUPLE_FIELDS)}) with a known kind and a boolean pair"
                )
        _validate_alphabet(path, index, record)
        _validate_rule_severities(path, index, record)
        _validate_index_formats(path, index, record)
        records[record["version"]] = record
    stated = {version: sorted(entry["rule"] for entry in record["ruleSeverities"])
              for version, record in records.items()}
    for version, rules in stated.items():
        first, first_rules = next(iter(stated.items()))
        if rules != first_rules:
            raise CometParamsError(
                f"{path}: Comet {version}'s version record states the version-scoped rules "
                f"{rules}, and Comet {first}'s states {first_rules}; every release states every "
                "version-scoped rule, so the reference can give each rule's severity for each "
                "release"
            )
    for version in releases:
        if version not in records:
            raise CometParamsError(
                f"{path}: manifests/tools.json installs Comet {version}, but the metadata has no "
                f"version record for it (it has {', '.join(records)}). The reference cannot state "
                "a default, a tuple layout or availability for a release nobody curated."
            )

    categories = {}
    for index, category in enumerate(metadata["categories"]):
        if not isinstance(category, dict):
            raise CometParamsError(f"{path}: categories[{index}] must be an object")
        identifier = _text(f"categories[{index}]", "id", category.get("id"), path)
        _text(f"categories[{index}] ({identifier})", "displayName", category.get("displayName"), path)
        if identifier in categories:
            raise CometParamsError(f"{path}: categories[{index}] repeats the id \"{identifier}\"")
        categories[identifier] = category

    # Each parameter, on its own.
    names = []
    seen_ids = {}
    for index, parameter in enumerate(metadata["parameters"]):
        where = _where(index, parameter)
        if not isinstance(parameter, dict):
            raise CometParamsError(f"{path}: {where} must be an object, not {_kind(parameter)}")
        for field, meaning in REQUIRED_PARAMETER_FIELDS:
            if field not in parameter:
                raise CometParamsError(
                    f"{path}: {where} is missing the field \"{field}\" ({meaning}). R-DOC-04 "
                    "requires every field for every parameter, so the reference refuses to "
                    "print an entry without it."
                )
        name = _text(where, "name", parameter["name"], path)
        if not re.fullmatch(r"[A-Za-z0-9_]+", name):
            raise CometParamsError(f"{path}: {where} has the name {name!r}, which Comet could not declare")
        if name in names:
            raise CometParamsError(
                f"{path}: {where} is the second parameter named \"{name}\"; the reference would "
                "carry two entries for one parameter"
            )
        # Sphinx turns a label into an HTML id by lower-casing it and replacing
        # anything not alphanumeric with '-'; two names that collide there would
        # share one anchor.
        anchor = re.sub(r"[^a-z0-9]+", "-", name.lower())
        if anchor in seen_ids:
            raise CometParamsError(
                f"{path}: {where} and \"{seen_ids[anchor]}\" would share the anchor \"{anchor}\""
            )
        seen_ids[anchor] = name
        names.append(name)
        _text(where, "displayName", parameter["displayName"], path)
        _text(where, "shortHelp", parameter["shortHelp"], path)
        category = parameter["category"]
        if category not in categories:
            raise CometParamsError(
                f"{path}: {where} has the category {category!r}, which is not one of the "
                f"metadata's categories ({', '.join(categories)}). The reference groups entries "
                "by category, so an entry with an unknown one would have nowhere to go."
            )
        kind = parameter["kind"]
        if kind not in KINDS:
            raise CometParamsError(
                f"{path}: {where} has the kind {kind!r}, which this generator does not know "
                f"({', '.join(KINDS)}). Add it here, with its words, when the Java ValueKind "
                "gains it."
            )
        if parameter["serialization"] not in SERIALIZATIONS:
            raise CometParamsError(
                f"{path}: {where} has the serialization {parameter['serialization']!r}, which "
                f"this generator does not know ({', '.join(SERIALIZATIONS)})"
            )
        if parameter["visibility"] not in VISIBILITY:
            raise CometParamsError(
                f"{path}: {where} has the visibility {parameter['visibility']!r}, which this "
                f"generator does not know ({', '.join(VISIBILITY)})"
            )
        if not isinstance(parameter["default"], str):
            raise CometParamsError(
                f"{path}: {where} has \"default\" = {_kind(parameter['default'])}; every default "
                "is the text comet.params carries, a JSON string"
            )
        for bound in ("min", "max"):
            if parameter[bound] is not None and not isinstance(parameter[bound], str):
                raise CometParamsError(f"{path}: {where} has \"{bound}\" = {parameter[bound]!r}; "
                                       "a bound is a string or null")
        if parameter["inlineComment"] is not None and not isinstance(parameter["inlineComment"], str):
            raise CometParamsError(f"{path}: {where} has a non-string \"inlineComment\"")
        choices = parameter["choices"]
        if not isinstance(choices, list):
            raise CometParamsError(f"{path}: {where} has \"choices\" = {_kind(choices)}; it is an array")
        for position, choice in enumerate(choices):
            if not isinstance(choice, dict) or not isinstance(choice.get("value"), str):
                raise CometParamsError(f"{path}: {where} choices[{position}] has no \"value\"")
            label = choice.get("label")
            if not isinstance(label, str) or not label.strip():
                raise CometParamsError(
                    f"{path}: {where} choices[{position}] (value \"{choice['value']}\") has no "
                    "label. The reference prints what each allowed value means; a bare number "
                    "is not an allowed value a scientist can choose."
                )
        if kind.endswith("_ENUM") and len(choices) < 2:
            raise CometParamsError(f"{path}: {where} is {kind} with {len(choices)} choice(s)")
        help_url = parameter["helpUrl"]
        if not isinstance(help_url, str) or not help_url.startswith("https://"):
            raise CometParamsError(
                f"{path}: {where} has \"helpUrl\" = {help_url!r}; the description cites its "
                "upstream source by https:// reference"
            )
        versions = parameter["versions"]
        if not isinstance(versions, dict) or set(versions) != {"from", "through"}:
            raise CometParamsError(
                f"{path}: {where} has \"versions\" = {versions!r}; it is {{\"from\", \"through\"}}"
            )
        if versions["from"] not in records:
            raise CometParamsError(
                f"{path}: {where} is available \"from\" {versions['from']!r}, which is not a "
                f"curated version ({', '.join(records)})"
            )
        if versions["through"] is not None and versions["through"] not in records:
            raise CometParamsError(
                f"{path}: {where} is available \"through\" {versions['through']!r}, which is not "
                "a curated version"
            )
        for list_field in ("validators", "aliases", "related"):
            value = parameter[list_field]
            if not isinstance(value, list) or not all(isinstance(item, str) for item in value):
                raise CometParamsError(
                    f"{path}: {where} has \"{list_field}\" = {value!r}; it is an array of strings"
                )

    # The whole set: Comet's own declarations of each release are the complete
    # list, so a parameter missing from the metadata is named here rather than
    # quietly missing from the page.
    internal = {}
    for index, entry in enumerate(metadata["internal"]):
        if not isinstance(entry, dict):
            raise CometParamsError(f"{path}: internal[{index}] must be an object")
        name = _text(f"internal[{index}]", "name", entry.get("name"), path)
        _text(f"internal[{index}] ({name})", "reason", entry.get("reason"), path)
        if name in names:
            raise CometParamsError(f"{path}: internal[{index}] ({name}) is also modelled")
        internal[name] = entry
    by_name = {parameter["name"]: parameter for parameter in metadata["parameters"]}
    for version in releases:
        dump = dumps[version]
        for name in dump["declared"]:
            if name not in by_name and name not in internal:
                raise CometParamsError(
                    f"{path}: comet -q of Comet {version} ({dump['path']}) declares \"{name}\", "
                    "which the metadata neither models nor allow-lists as internal. Every "
                    "parameter the release declares must have an entry in this reference "
                    "(R-DOC-04) or a recorded reason for not having one."
                )
        for name, parameter in by_name.items():
            if _available(parameter, version) and name not in dump["declared"]:
                raise CometParamsError(
                    f"{path}: {name} claims to be available in Comet {version}, but that "
                    f"release's comet -q output ({dump['path']}) does not declare it"
                )

    # Cross-references.
    for index, parameter in enumerate(metadata["parameters"]):
        where = _where(index, parameter)
        for related in parameter["related"]:
            if related not in by_name or related == parameter["name"]:
                raise CometParamsError(
                    f"{path}: {where} lists the related parameter \"{related}\", which is "
                    + ("the parameter itself" if related == parameter["name"]
                       else "not a modelled parameter")
                    + ". The reference links every related parameter to its entry."
                )
    for index, record in enumerate(metadata["versions"]):
        _validate_overrides(path, index, record, by_name)
    table = metadata["enzymeTable"]
    referenced = table.get("referencedBy")
    if not isinstance(referenced, list) or not referenced:
        raise CometParamsError(f"{path}: enzymeTable has no \"referencedBy\" list")
    for name in referenced:
        if name not in by_name or by_name[name]["kind"] != "ENZYME_REFERENCE":
            raise CometParamsError(
                f"{path}: enzymeTable.referencedBy names \"{name}\", which is not a modelled "
                "ENZYME_REFERENCE parameter"
            )
    for field in ("header", "rowFormat", "helpUrl"):
        _text("enzymeTable", field, table.get(field), path)
    senses = table.get("senseChoices")
    if not isinstance(senses, list) or not senses:
        raise CometParamsError(f"{path}: enzymeTable has no \"senseChoices\"")
    for position, choice in enumerate(senses):
        if not isinstance(choice, dict) or not isinstance(choice.get("value"), str) \
                or not isinstance(choice.get("label"), str) or not choice["label"].strip():
            raise CometParamsError(
                f"{path}: enzymeTable.senseChoices[{position}] has no value or no label"
            )

    # Presets: a delta that names no parameter would be a preset effect on nothing.
    preset_list = presets.get("presets")
    if not isinstance(preset_list, list):
        raise CometParamsError(f"{presets_path}: no \"presets\" array")
    for index, preset in enumerate(preset_list):
        if not isinstance(preset, dict):
            raise CometParamsError(f"{presets_path}: presets[{index}] must be an object")
        identifier = _text(f"presets[{index}]", "id", preset.get("id"), presets_path)
        _text(f"presets[{index}] ({identifier})", "displayName", preset.get("displayName"),
              presets_path)
        if preset.get("cometVersion") not in records:
            raise CometParamsError(
                f"{presets_path}: presets[{index}] ({identifier}) targets Comet "
                f"{preset.get('cometVersion')!r}, which the metadata has no record of"
            )
        deltas = preset.get("deltas")
        if not isinstance(deltas, list) or not deltas:
            raise CometParamsError(f"{presets_path}: presets[{index}] ({identifier}) has no deltas")
        for position, delta in enumerate(deltas):
            if not isinstance(delta, dict) or delta.get("parameter") not in by_name \
                    or not isinstance(delta.get("value"), str):
                raise CometParamsError(
                    f"{presets_path}: presets[{index}] ({identifier}) deltas[{position}] sets "
                    f"{delta.get('parameter') if isinstance(delta, dict) else delta!r}, which is "
                    "not a modelled parameter (or the value is not text). A preset effect is "
                    "rendered on the parameter's entry, so it must name one."
                )


def _validate_alphabet(path: Path, index: int, record: dict) -> None:
    """Refuse a release's residue alphabet the page could not state truthfully -- the
    rules ``MetadataLoader`` and ``ResidueAlphabet`` apply."""
    where = f"{path}: versions[{index}] ({record['version']}) variableModTuple.residueAlphabet"
    alphabet = record["variableModTuple"].get("residueAlphabet")
    if not isinstance(alphabet, dict):
        raise CometParamsError(
            f"{where} is {_kind(alphabet)}; it must be an object naming the characters the "
            "release accepts in a residue token"
        )
    unknown = sorted(set(alphabet) - {"characters", "source"})
    if unknown:
        raise CometParamsError(f"{where} has the field {unknown[0]!r}, which it does not have")
    characters = alphabet.get("characters")
    if not isinstance(characters, str) or not characters:
        raise CometParamsError(f"{where} has \"characters\" = {characters!r}; it must be a "
                               "non-empty string")
    for position, character in enumerate(characters):
        if character not in _LETTERS and character not in TERMINAL_CODES:
            raise CometParamsError(
                f"{where} holds {character!r}, which is neither a residue letter A-Z nor a "
                f"terminal code ({', '.join(TERMINAL_CODES)})"
            )
        if characters.index(character) != position:
            raise CometParamsError(f"{where} lists {character!r} twice")
    source = alphabet.get("source")
    if not isinstance(source, str) or not source.startswith("https://"):
        raise CometParamsError(f"{where} has \"source\" = {source!r}; it must be an https:// "
                               "reference")


def _validate_rule_severities(path: Path, index: int, record: dict) -> None:
    """Refuse a release's ``ruleSeverities`` the page could not state truthfully -- the
    rules ``MetadataLoader`` and ``RuleSeverity`` apply."""
    where = f"{path}: versions[{index}] ({record['version']}) ruleSeverities"
    entries = record.get("ruleSeverities")
    if not isinstance(entries, list):
        raise CometParamsError(
            f"{where} is {_kind(entries)}; it must be the list of the release's severity for "
            "each version-scoped validation rule"
        )
    seen = set()
    for position, entry in enumerate(entries):
        at = f"{where}[{position}]"
        if not isinstance(entry, dict):
            raise CometParamsError(f"{at} must be an object, not {_kind(entry)}")
        unknown = sorted(set(entry) - set(RULE_SEVERITY_FIELDS))
        if unknown:
            raise CometParamsError(f"{at} has the field {unknown[0]!r}, which it does not have")
        rule = entry.get("rule")
        if not isinstance(rule, str) or not _RULE_ID.fullmatch(rule):
            raise CometParamsError(
                f"{at} has \"rule\" = {rule!r}, which is not a rule identifier such as "
                "family.what_it_checks"
            )
        if rule in seen:
            raise CometParamsError(f"{at} states {rule} a second time")
        seen.add(rule)
        if entry.get("severity") not in RULE_LEVELS:
            raise CometParamsError(
                f"{at} ({rule}) has \"severity\" = {entry.get('severity')!r}; it must be one of "
                f"{', '.join(RULE_LEVELS)}"
            )
        source = entry.get("source")
        if not isinstance(source, str) or not source.startswith("https://"):
            raise CometParamsError(
                f"{at} ({rule}) has \"source\" = {source!r}; it must be an https:// reference"
            )


def _validate_index_formats(path: Path, index: int, record: dict) -> None:
    """Refuse a release's ``indexFormats`` -- the rules ``MetadataLoader`` and
    ``IndexFormats`` apply: an object with exactly ``readable`` (distinct whole numbers of 1
    or more; empty for a release that reads none of the versioned formats) and an
    ``https://`` ``source``."""
    where = f"{path}: versions[{index}] ({record['version']}) indexFormats"
    formats = record.get("indexFormats")
    if not isinstance(formats, dict):
        raise CometParamsError(
            f"{where} is {_kind(formats)}; it must be an object naming the index formats "
            "(\"Comet index database vN\") the release can search"
        )
    unknown = sorted(set(formats) - set(INDEX_FORMAT_FIELDS))
    if unknown:
        raise CometParamsError(f"{where} has the field {unknown[0]!r}, which it does not have")
    readable = formats.get("readable")
    if not isinstance(readable, list):
        raise CometParamsError(
            f"{where} has \"readable\" = {readable!r}; it must be a list of index format numbers"
        )
    for position, number in enumerate(readable):
        if isinstance(number, bool) or not isinstance(number, int) or number < 1:
            raise CometParamsError(
                f"{where} readable[{position}] = {number!r} is not a whole number of 1 or more"
            )
        if readable.index(number) != position:
            raise CometParamsError(f"{where} lists format {number} twice")
    source = formats.get("source")
    if not isinstance(source, str) or not source.startswith("https://"):
        raise CometParamsError(f"{where} has \"source\" = {source!r}; it must be an https:// "
                               "reference")


def describe_alphabet(characters: str) -> str:
    """A release's residue alphabet in words, as ``ResidueAlphabet.describe`` puts it,
    with each character as a literal."""
    letters = "".join(letter for letter in _LETTERS if letter in characters)
    words = []
    if letters == _LETTERS:
        words.append("``A``-``Z``")
    elif letters:
        words.append(_literal(letters))
    for code, meaning in TERMINAL_CODES.items():
        if code in characters:
            words.append(f"{_literal(code)} ({meaning})")
    return ", ".join(words)


def _choice_pairs(choices):
    return [(choice["value"], choice["label"]) for choice in choices]


def _validate_overrides(path: Path, index: int, record: dict, by_name: dict) -> None:
    """Refuse a version record's override this page could not render truthfully.

    The same rules ``MetadataLoader`` enforces, so that a document the Java side
    refuses is never rendered here: a known parameter the release's range claims,
    once; an ``https://`` source; at least one replaced field and no unknown one;
    each replaced field well formed and different from the curated field; and the
    release's resulting default one of its resulting choices.
    """
    version = record["version"]
    seen = set()
    for position, override in enumerate(record["overrides"]):
        where = f"versions[{index}] ({version}) overrides[{position}]"
        if not isinstance(override, dict):
            raise CometParamsError(f"{path}: {where} must be an object, not {_kind(override)}")
        name = override.get("name")
        if isinstance(name, str):
            where += f" (\"{name}\")"
        unknown = sorted(set(override) - {"name", "source", *OVERRIDE_FIELDS})
        if unknown:
            raise CometParamsError(
                f"{path}: {where} has the field \"{unknown[0]}\", which an override does not "
                f"have; it carries \"name\", \"source\" and any of {', '.join(OVERRIDE_FIELDS)}"
            )
        if name not in by_name:
            raise CometParamsError(
                f"{path}: {where} names {name!r}, which is not a modelled parameter. An override "
                "replaces part of a parameter's entry, so it must name one."
            )
        parameter = by_name[name]
        if not _available(parameter, version):
            raise CometParamsError(
                f"{path}: {where} overrides {name} for Comet {version}, whose range "
                f"({parameter['versions']['from']} to {parameter['versions']['through'] or 'open'}) "
                "does not claim that release"
            )
        if name in seen:
            raise CometParamsError(f"{path}: {where} overrides {name} a second time")
        seen.add(name)
        source = override.get("source")
        if not isinstance(source, str) or not source.startswith("https://"):
            raise CometParamsError(
                f"{path}: {where} has \"source\" = {source!r}; an override cites where the "
                "release shows the difference by https:// reference"
            )
        replaced = [field for field in OVERRIDE_FIELDS if field in override]
        if not replaced:
            raise CometParamsError(
                f"{path}: {where} replaces no field; it names at least one of "
                f"{', '.join(OVERRIDE_FIELDS)}"
            )
        if "default" in override and not isinstance(override["default"], str):
            raise CometParamsError(f"{path}: {where} has a \"default\" that is not text")
        if "choices" in override:
            choices = override["choices"]
            if not parameter["kind"].endswith("_ENUM"):
                raise CometParamsError(
                    f"{path}: {where} gives choices to {parameter['kind']}, which is not an "
                    "enumerated kind"
                )
            if not isinstance(choices, list) or len(choices) < 2:
                raise CometParamsError(
                    f"{path}: {where} has \"choices\" = {choices!r}; an enumerated kind needs at "
                    "least two labelled choices"
                )
            for spot, choice in enumerate(choices):
                if not isinstance(choice, dict) or not isinstance(choice.get("value"), str) \
                        or not isinstance(choice.get("label"), str) or not choice["label"].strip():
                    raise CometParamsError(
                        f"{path}: {where} choices[{spot}] has no value or no label. The reference "
                        "prints what each allowed value means."
                    )
            values = [choice["value"] for choice in choices]
            if len(set(values)) != len(values):
                raise CometParamsError(f"{path}: {where} lists a choice value twice: {values}")
        if "inlineComment" in override:
            comment = override["inlineComment"]
            if comment is not None and (not isinstance(comment, str) or not comment.strip()
                                        or "\n" in comment or "\r" in comment
                                        or comment != comment.strip()):
                raise CometParamsError(
                    f"{path}: {where} has \"inlineComment\" = {comment!r}; it is null or one "
                    "unpadded, non-blank line, written after the value"
                )
        if "shortHelp" in override:
            _text(where, "shortHelp", override["shortHelp"], path)
        if "helpUrl" in override and (not isinstance(override["helpUrl"], str)
                                      or not override["helpUrl"].startswith("https://")):
            raise CometParamsError(
                f"{path}: {where} has \"helpUrl\" = {override['helpUrl']!r}; the description "
                "cites its upstream source by https:// reference"
            )
        for field in replaced:
            same = (_choice_pairs(override[field]) == _choice_pairs(parameter[field])
                    if field == "choices" else override[field] == parameter[field])
            if same:
                raise CometParamsError(
                    f"{path}: {where} repeats the curated \"{field}\" of {name}; an override "
                    "records only a difference"
                )
        definition = definition_for_record(parameter, override)
        if definition["kind"].endswith("_ENUM") and definition["default"] != "" and \
                definition["default"] not in [choice["value"] for choice in definition["choices"]]:
            raise CometParamsError(
                f"{path}: {where} leaves Comet {version} with the default "
                f"{definition['default']!r}, which is not one of that release's choices "
                f"({', '.join(choice['value'] for choice in definition['choices'])})"
            )


def definition_for_record(parameter: dict, override) -> dict:
    """A parameter's entry with one override's replaced fields applied (``None``: none)."""
    if override is None:
        return parameter
    definition = dict(parameter)
    for field in OVERRIDE_FIELDS:
        if field in override:
            definition[field] = override[field]
    return definition


def definition_for(metadata: dict, parameter: dict, version: str) -> dict:
    """A parameter's entry as one Comet release has it: the curated entry, with that
    release's override applied where its version record carries one -- the same
    rule as ``CuratedMetadata.parameter(name, version)``."""
    for record in metadata["versions"]:
        if record["version"] == version:
            for override in record["overrides"]:
                if override["name"] == parameter["name"]:
                    return definition_for_record(parameter, override)
    return parameter


def _available(parameter: dict, version: str) -> bool:
    """Whether a parameter's ``versions`` range contains ``version``."""
    key = _version_key(version)
    start = _version_key(parameter["versions"]["from"])
    end = parameter["versions"]["through"]
    return start <= key and (end is None or key <= _version_key(end))


def default_for(metadata: dict, parameter: dict, version: str) -> str:
    """A parameter's default in one version: the version's override, else the curated default."""
    return definition_for(metadata, parameter, version)["default"]


# --------------------------------------------------------------------------
# Rendering
# --------------------------------------------------------------------------

#: Column of an inline comment's ``#``: ``CanonicalParamsWriter.COMMENT_COLUMN``
#: (0-based 39, so the ``#`` is the 40th character), as in Comet's own output.
COMMENT_COLUMN = 39


def canonical_line(name: str, value: str, comment) -> str:
    """The ``name = value`` line ``CanonicalParamsWriter`` writes for one declaration.

    The same rule as its ``declaration`` method: ``name =`` for an empty value;
    the inline comment's ``#`` at column 40, or one space after a longer
    declaration; ``#`` alone for an empty comment. The Java agreement test
    (``GeneratedReferenceTest``) checks every line rendered here against the
    writer's own output for the default model.
    """
    line = f"{name} =" if value == "" else f"{name} = {value}"
    if comment is None:
        return line
    padding = " " * max(1, COMMENT_COLUMN - len(line))
    return line + padding + ("#" if comment == "" else f"# {comment}")


_ESCAPE = re.compile(r"([\\*`|_\[\]<>])")


def _escape(text: str) -> str:
    """Escape free text so reStructuredText reads it as text, never as markup."""
    return _ESCAPE.sub(r"\\\1", " ".join(text.split()))


def _literal(text: str) -> str:
    """Inline literal; the empty string is shown as such."""
    if text == "":
        return "(empty)"
    return f"``{text}``"


def _ref(name: str) -> str:
    return f":ref:`{name} <comet-param-{name}>`"


def _allowed(metadata: dict, parameter: dict, releases) -> str:
    """The allowed values or range of one parameter, in words."""
    kind = parameter["kind"]
    choices = parameter["choices"]
    if choices:
        return "One of:\n\n" + "\n".join(
            f"* {_literal(choice['value'])} -- {_escape(choice['label'])}" for choice in choices
        )
    lower, upper = parameter["min"], parameter["max"]
    if kind in ("BOOLEAN_FLAG", "ION_SERIES_FLAG"):
        text = "``0`` (off) or ``1`` (on)."
    elif kind == "ENZYME_REFERENCE":
        text = (
            "The number of a row of the ``[COMET_ENZYME_INFO]`` table written at the end of the "
            f"same file. Comet's default rows are in :ref:`{ENZYME_LABEL}`."
        )
    elif kind == "VARIABLE_MOD_TUPLE":
        parts = []
        for version in releases:
            record = next(r for r in metadata["versions"] if r["version"] == version)
            fields = record["variableModTuple"]["fields"]
            items = []
            for field in fields:
                words = f"{TUPLE_FIELDS[field['field']]} ({TUPLE_KINDS[field['kind']]}"
                words += ", or two as ``a,b``)" if field["pair"] else ")"
                items.append(words)
            alphabet = record["variableModTuple"]["residueAlphabet"]
            parts.append(
                f"Comet {version}: {len(fields)} space-separated fields, in this order: "
                + "; ".join(items) + ". Residues: any combination of "
                + describe_alphabet(alphabet["characters"])
                + f" (`source <{alphabet['source']}>`__)."
            )
        text = " ".join(parts) + " A mass difference of ``0.0`` leaves the slot unused."
    elif kind == "TOLERANCE_PAIR_MEMBER":
        text = (
            "A decimal number. With its partner it should satisfy lower <= 0 <= upper; a "
            "reversed pair is an error and a window not containing 0 is a warning, under the "
            "pair's own rule rather than the generic ordering rule (R-PARAM-04)."
        )
    elif kind in ("FILE_PATH", "STRING", "STRING_ENUM"):
        text = "A file path." if kind == "FILE_PATH" else "Text."
    elif kind == "DECIMAL_LIST":
        text = "Zero or more decimal numbers."
    else:
        text = {
            "INTEGER": "A whole number.",
            "DECIMAL": "A decimal number.",
            "INTEGER_RANGE": "Two whole numbers separated by one space.",
            "DECIMAL_RANGE": "Two decimal numbers separated by one space.",
        }[kind]
    if kind in _NUMERIC_KINDS or kind == "TOLERANCE_PAIR_MEMBER":
        each = ", each value" if kind in ("INTEGER_RANGE", "DECIMAL_RANGE", "DECIMAL_LIST") else ""
        if lower is not None and upper is not None:
            text += f" Range{each}: {_literal(lower)} to {_literal(upper)}."
        elif lower is not None:
            text += f" Minimum{each}: {_literal(lower)}."
        elif upper is not None:
            text += f" Maximum{each}: {_literal(upper)}."
        else:
            text += " No curated bounds."
    if parameter["serialization"] == "EMPTY_ALLOWED":
        text += " An empty value is allowed and is a value."
    return text


def _availability(metadata: dict, parameter: dict, releases) -> str:
    """Version availability, without presenting a curated-only version as supported."""
    in_release = [version for version in releases if _available(parameter, version)]
    out_release = [version for version in releases if version not in in_release]
    parts = []
    if in_release:
        parts.append("Declared by Comet " + ", ".join(in_release) + ", which CometGUI installs.")
    if out_release:
        parts.append("**Not** declared by Comet " + ", ".join(out_release) + ".")
    start = parameter["versions"]["from"]
    end = parameter["versions"]["through"]
    curated_only = [r["version"] for r in metadata["versions"] if r["version"] not in releases]
    since = f"Curated from Comet {start}"
    if start in curated_only:
        since += " (a release curated only so that older files can be migrated; CometGUI does not install it)"
    since += "; " + ("no curated release removes it." if end is None else f"last declared by Comet {end}.")
    parts.append(since)
    return " ".join(parts)


def _preset_effects(parameter: dict, presets: dict) -> str:
    effects = []
    for preset in presets["presets"]:
        for delta in preset["deltas"]:
            if delta["parameter"] == parameter["name"]:
                effects.append(
                    f"* ``{preset['id']}`` ({_escape(preset['displayName'])}, Comet "
                    f"{preset['cometVersion']}) sets {_literal(delta['value'])}"
                )
    if not effects:
        return "No built-in preset sets it."
    return "\n" + "\n".join(effects)


def _field(name: str, body: str) -> list:
    """One field-list item; multi-line bodies are indented under the field."""
    lines = body.split("\n")
    if len(lines) == 1:
        return [f":{name}: {body}"]
    # A multi-line body starts on the next line, so that every line of it --
    # a literal block's "::" paragraph included -- sets the same indentation.
    out = [f":{name}:"]
    for line in lines:
        out.append(("   " + line) if line else "")
    return out


def _per_release(label: str, metadata: dict, parameter: dict, releases, body) -> list:
    """One field, or -- where the releases this page documents say different things
    (a version record's override) -- one field per release, named for it."""
    available = [version for version in releases if _available(parameter, version)]
    texts = [body(definition_for(metadata, parameter, version)) for version in available]
    if not available or len(set(texts)) == 1:
        return _field(label, texts[0] if texts else body(parameter))
    out = []
    for version, text in zip(available, texts):
        out += _field(f"{label}, Comet {version}", text)
    return out


def render_entry(metadata: dict, presets: dict, parameter: dict, releases, categories) -> list:
    """The reStructuredText lines of one parameter's entry."""
    name = parameter["name"]
    title = f"``{name}``"
    out = [f"{ENTRY_LABEL_PREFIX}{name}:", "", title, "-" * len(title), ""]
    out.append(f"**{_escape(parameter['displayName'])}**")
    out.append("")
    kind_text = f"{KINDS[parameter['kind']]} (``{parameter['kind']}``)"
    out += _field("Comet name", f"``{name}``")
    out += _field("Display name", _escape(parameter["displayName"]))
    out += _field("Category", _escape(categories[parameter["category"]]["displayName"]))
    out += _field("Type", kind_text)
    for version in releases:
        if _available(parameter, version):
            out += _field(f"Default, Comet {version}",
                          _literal(default_for(metadata, parameter, version)))
    out += _per_release("Allowed values", metadata, parameter, releases,
                        lambda d: _allowed(metadata, d, releases))
    out += _per_release(
        "Description", metadata, parameter, releases,
        lambda d: f"{_escape(d['shortHelp'])} Upstream documentation: {d['helpUrl']}",
    )
    written = []
    for version in releases:
        if _available(parameter, version):
            definition = definition_for(metadata, parameter, version)
            line = canonical_line(name, definition["default"], definition["inlineComment"])
            written.append(f"Comet {version}, the default as the canonical writer emits it"
                           f" -- {SERIALIZATIONS[parameter['serialization']]}::\n\n   {line}\n")
    out += _field("Serialisation", "\n".join(written) if written
                  else f"{SERIALIZATIONS[parameter['serialization']]}.")
    out.append("")
    out += _field("Availability", _availability(metadata, parameter, releases))
    related = parameter["related"]
    out += _field("Related", ", ".join(_ref(item) for item in related) if related else "None.")
    out += _field("Presets", _preset_effects(parameter, presets))
    out += _field("Editor level", VISIBILITY[parameter["visibility"]])
    validators = parameter["validators"]
    out += _field("Checked by", ", ".join(f"``{item}``" for item in validators)
                  if validators else "No rule beyond its type.")
    aliases = parameter["aliases"]
    if aliases:
        out += _field("Also found by", ", ".join(_escape(item) for item in aliases))
    out.append("")
    return out


def _list_table(caption: str, widths: str, header, rows) -> list:
    out = [f".. list-table:: {caption}", "   :header-rows: 1", f"   :widths: {widths}", ""]
    for row in (header, *rows):
        out.append("   * - " + row[0])
        for cell in row[1:]:
            out.append("     - " + cell)
    out.append("")
    return out


def render(metadata: dict, presets: dict, releases, dumps, digests) -> str:
    """Render the whole fragment."""
    categories = {category["id"]: category for category in metadata["categories"]}
    parameters = metadata["parameters"]
    out = [_GENERATED_HEADER, ""]
    out.append(
        f"Rendered from ``{METADATA_RELATIVE_PATH.as_posix()}`` (SHA-256 "
        f"``{digests['metadata']}``) and ``{PRESETS_RELATIVE_PATH.name}`` (SHA-256 "
        f"``{digests['presets']}``), for the Comet release(s) ``manifests/tools.json`` "
        f"installs: {', '.join(releases)}. **{len(parameters)} parameters** are modelled and "
        f"have an entry below; {len(metadata['internal'])} are allow-listed as internal."
    )
    out.append("")
    for version in releases:
        dump = dumps[version]
        out.append(
            f"Comet {version}'s own ``comet -q`` output declares {len(dump['declared'])} "
            f"parameters (the checked-in capture, SHA-256 ``{dump['sha256']}``); every one is "
            "modelled or allow-listed, which this generator checks on every build."
        )
        out.append("")
    out += _render_rule_severities(metadata, releases)
    for category in metadata["categories"]:
        members = [p for p in parameters if p["category"] == category["id"]]
        if not members:
            continue
        heading = _escape(category["displayName"])
        out += [f".. _comet-category-{category['id']}:", "", heading, "=" * len(heading), ""]
        out.append(f"{len(members)} parameter(s).")
        out.append("")
        for parameter in members:
            out += render_entry(metadata, presets, parameter, releases, categories)

    # The enzyme table.
    table = metadata["enzymeTable"]
    heading = "The enzyme table"
    out += [f".. _{ENZYME_LABEL}:", "", heading, "=" * len(heading), ""]
    out.append(
        f"``{table['header']}`` is the last section of ``comet.params``; every line after it "
        f"is a row, ``{table['rowFormat']}``. Referenced by "
        + ", ".join(_ref(name) for name in table["referencedBy"])
        + f". Upstream documentation: {table['helpUrl']}"
    )
    out.append("")
    out.append("The sense column:")
    out.append("")
    for choice in table["senseChoices"]:
        out.append(f"* {_literal(choice['value'])} -- {_escape(choice['label'])}")
    out.append("")
    for version in releases:
        rows = dumps[version]["rows"]
        out += _list_table(
            f"The {len(rows)} rows Comet {version} writes by default (from its own ``comet -q`` "
            "output), exactly as the canonical writer writes them back.",
            "10 30 10 25 25",
            ["Number", "Name", "Sense", "Cut residues", "No-cut residues"],
            [[row["number"], f"``{row['name']}``", row["sense"], f"``{row['cut']}``",
              f"``{row['nocut']}``"] for row in rows],
        )
        out.append("As written::")
        out.append("")
        out.append(f"   {table['header']}")
        for row in rows:
            out.append(f"   {row['line']}")
        out.append("")

    # The internal allow-list.
    heading = "Parameters deliberately not modelled"
    out += [".. _comet-internal-allow-list:", "", heading, "=" * len(heading), ""]
    internal = metadata["internal"]
    if not internal:
        out.append(
            "None. The internal allow-list is empty: every parameter Comet "
            + ", ".join(releases)
            + " declares is modelled and has an entry above."
        )
        out.append("")
    else:
        out += _list_table(
            "Parameters Comet declares that CometGUI deliberately does not model, each with "
            "its reason.",
            "30 70",
            ["Parameter", "Reason"],
            [[f"``{entry['name']}``", _escape(entry["reason"])] for entry in internal],
        )
    return "\n".join(out).rstrip("\n") + "\n"


def _render_rule_severities(metadata: dict, releases) -> list:
    """The version-scoped validation rules and each installed release's severity for them."""
    records = {record["version"]: record for record in metadata["versions"]}
    rules = sorted(entry["rule"] for entry in records[releases[0]]["ruleSeverities"])
    heading = "Validation that differs by release"
    out = [f".. _{RULE_SEVERITIES_LABEL}:", "", heading, "=" * len(heading), ""]
    out.append(
        "A version-scoped validation rule takes its severity from the release the parameters "
        "are for: each release's version record states it, with the source of the behaviour it "
        "encodes. Every other rule has one severity in every release."
    )
    out.append("")
    rows = []
    for rule in rules:
        row = [f"``{rule}``"]
        for version in releases:
            entry = next(e for e in records[version]["ruleSeverities"] if e["rule"] == rule)
            row.append(f"{RULE_LEVELS[entry['severity']]} ({entry['source']})")
        rows.append(row)
    width = 70 // len(releases)
    out += _list_table(
        "Version-scoped rules, per installed release.",
        " ".join(["30"] + [str(width)] * len(releases)),
        ["Rule"] + [f"Comet {version}" for version in releases],
        rows,
    )
    return out


def count_entries(text: str):
    """The Comet names of the entries in a rendered fragment, in order (one per entry label)."""
    return re.findall(r"^" + re.escape(ENTRY_LABEL_PREFIX) + r"([A-Za-z0-9_]+):$", text, re.M)


def check_coverage(text: str, metadata: dict) -> None:
    """Refuse a fragment that does not hold exactly one entry per modelled parameter."""
    rendered = count_entries(text)
    expected = [parameter["name"] for parameter in metadata["parameters"]]
    missing = [name for name in expected if name not in rendered]
    duplicated = sorted({name for name in rendered if rendered.count(name) > 1})
    extra = sorted(set(rendered) - set(expected))
    if missing or duplicated or extra or len(rendered) != len(expected):
        raise CometParamsError(
            f"the rendered reference holds {len(rendered)} entries for {len(expected)} modelled "
            f"parameters; missing {missing}, duplicated {duplicated}, not modelled {extra}. "
            "R-DOC-04 requires exactly one entry per parameter."
        )


# --------------------------------------------------------------------------
# The entry point docs/conf.py calls
# --------------------------------------------------------------------------


def load_all(root: Path, metadata_file: Path = None, presets_file: Path = None,
             manifest_file: Path = None):
    """Read and validate every input; return what :func:`render` needs."""
    root = Path(root)
    metadata_path = Path(metadata_file) if metadata_file else root / METADATA_RELATIVE_PATH
    presets_path = Path(presets_file) if presets_file else root / PRESETS_RELATIVE_PATH
    manifest_path = Path(manifest_file) if manifest_file else root / MANIFEST_RELATIVE_PATH
    metadata, metadata_digest = _read_json(metadata_path, "the Comet parameter metadata")
    presets, presets_digest = _read_json(presets_path, "the built-in Comet presets")
    releases, _ = release_versions(manifest_path)
    markers = {
        record.get("version"): record.get("marker")
        for record in metadata.get("versions") or []
        if isinstance(record, dict)
    }
    dumps = {}
    for version in releases:
        if not isinstance(markers.get(version), str):
            raise CometParamsError(
                f"{metadata_path}: manifests/tools.json installs Comet {version}, but the "
                "metadata has no version record (with a marker) for it"
            )
        dumps[version] = read_dump(root, version, markers[version])
    validate(metadata, presets, metadata_path, presets_path, releases, dumps)
    return {
        "metadata": metadata,
        "presets": presets,
        "releases": releases,
        "dumps": dumps,
        "digests": {"metadata": metadata_digest, "presets": presets_digest},
        "metadata_path": metadata_path,
    }


def render_checked(inputs: dict) -> str:
    """Render, then prove the rendered text covers every parameter exactly once."""
    text = render(inputs["metadata"], inputs["presets"], inputs["releases"], inputs["dumps"],
                  inputs["digests"])
    check_coverage(text, inputs["metadata"])
    return text


def generate(root: Path, out_dir: Path, metadata_file: Path = None, presets_file: Path = None,
             manifest_file: Path = None) -> dict:
    """Validate the inputs and write the fragment.

    :returns: a summary -- the file written, the counts and the digests.
    :raises CometParamsError: if an input is rejected, if the rendered text does
        not cover every parameter exactly once, or if the written file does not
        read back as what was rendered.
    """
    inputs = load_all(root, metadata_file, presets_file, manifest_file)
    text = render_checked(inputs)
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    target = out_dir / FRAGMENT_FILE
    with open(target, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
    if not target.is_file() or target.read_text(encoding="utf-8") != text:
        raise CometParamsError(f"the generator wrote {target} but it does not read back as rendered")
    return _summary(inputs, text, target)


def _summary(inputs: dict, text: str, target) -> dict:
    return {
        "written": target,
        "entries": len(count_entries(text)),
        "parameters": len(inputs["metadata"]["parameters"]),
        "internal": len(inputs["metadata"]["internal"]),
        "releases": inputs["releases"],
        "enzyme_rows": {v: len(inputs["dumps"][v]["rows"]) for v in inputs["releases"]},
        "presets": len(inputs["presets"]["presets"]),
        "metadata_sha256": inputs["digests"]["metadata"],
        "presets_sha256": inputs["digests"]["presets"],
        "metadata_path": inputs["metadata_path"],
        "bytes": len(text.encode("utf-8")),
    }


def describe(summary: dict) -> str:
    """The one count line, shared by the command line and the build log."""
    return (
        f"{summary['entries']} parameter entries for {summary['parameters']} modelled "
        f"parameters, {summary['internal']} internal, Comet {', '.join(summary['releases'])}, "
        f"{summary['presets']} preset(s), metadata sha256 {summary['metadata_sha256']}"
    )


def _main(argv) -> int:
    parser = argparse.ArgumentParser(
        prog="cometparams.py",
        description=(
            "Render the Comet parameter reference fragment from the curated metadata. The "
            "documentation build calls generate() directly; this form exists to run the "
            "generator over an edited copy and see the reaction."
        ),
    )
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent,
                        help="repository root (default: the one holding this script)")
    parser.add_argument("--metadata", type=Path, default=None,
                        help="an explicit metadata file instead of the module's")
    parser.add_argument("--presets", type=Path, default=None,
                        help="an explicit presets file instead of the module's")
    parser.add_argument("--manifest", type=Path, default=None,
                        help="an explicit tool manifest instead of <root>/manifests/tools.json")
    parser.add_argument("--out-dir", type=Path, default=None,
                        help="where to write the fragment (default: <root>/docs/_generated)")
    parser.add_argument("--check", action="store_true",
                        help="validate and render, but write nothing")
    args = parser.parse_args(argv)
    out_dir = args.out_dir or (args.root / "docs" / OUTPUT_RELATIVE_DIR)
    try:
        if args.check:
            inputs = load_all(args.root, args.metadata, args.presets, args.manifest)
            text = render_checked(inputs)
            print(f"cometparams: {inputs['metadata_path']} validates -- "
                  f"{describe(_summary(inputs, text, None))}")
            return 0
        summary = generate(args.root, out_dir, args.metadata, args.presets, args.manifest)
    except CometParamsError as error:
        print(f"cometparams: {error}", file=sys.stderr)
        return 1
    print(f"cometparams: wrote {summary['written']} ({summary['bytes']} bytes)")
    print(f"cometparams: from {summary['metadata_path']} -- {describe(summary)}")
    return 0


if __name__ == "__main__":  # pragma: no cover - command-line form
    sys.exit(_main(sys.argv[1:]))
