# CometGUI

**Read `ONBOARDING.rst` first, then `STATUS.rst`.** Do not start work from this
file — it exists only because the coding harness reads it.

- `ONBOARDING.rst` — what the project is, how phases are run, what finished means
- `STATUS.rst` — authoritative current state; update at every gate
- `DECISIONS.rst` — `D-001`..`D-008`; an agent must never answer these alone
- `specification.rst` — the requirements (revision 2)
- `phases/index.rst` — phases 00–16 and their dependency order

Work runs in three tiers: the **main orchestrator** spawns one fresh **phase
orchestrator** per phase, which spawns one fresh **phase agent** per work unit.
Each tier signs off the tier below — by running the checks itself, not by
reading a report — before moving on. See "Roles" and "Sign-off" in
`ONBOARDING.rst`.

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
5. The remote is `https://github.com/mriffle/CometGUI.git` (`D-008`, decided
   2026-08-30). It may move before release; keep the URL in one place. Never
   force-push and never rewrite published history.
6. Exit code 0 proves nothing. Verify that output exists and is correct.
