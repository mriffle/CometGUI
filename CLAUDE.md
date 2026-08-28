# CometGUI

**Read `ONBOARDING.rst` first, then `STATUS.rst`.** Do not start work from this
file — it exists only because the coding harness reads it.

- `ONBOARDING.rst` — what the project is, how phases are run, what finished means
- `STATUS.rst` — authoritative current state; update at every gate
- `DECISIONS.rst` — `D-001`..`D-008`; an agent must never answer these alone
- `specification.rst` — the requirements (revision 2)
- `phases/index.rst` — phases 00–16 and their dependency order

Hard rules:

1. All project documentation is reStructuredText and must pass
   `sphinx-build -n -W`. This file is the only Markdown in the repository and
   holds pointers only.
2. Nothing is installed on the host — no `apt`, no `sudo`, no host-level pip.
   Tools go under `tools/<name>-<version>/`, Python into a project virtualenv.
3. Never weaken an exit gate, a checksum verification, a validation rule or a
   coverage threshold to make something pass.
4. Commit at every milestone with an explicit pathspec. Never `git add -A` —
   other agents may be live in the same tree.
5. There is no git remote. Do not create one (`D-008`).
6. Exit code 0 proves nothing. Verify that output exists and is correct.
