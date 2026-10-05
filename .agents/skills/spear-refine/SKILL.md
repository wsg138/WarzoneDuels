---
name: spear-refine
description: Finalize a SPEAR task — optional behavior-preserving refactor, full suite green, flip tasks.md to [x], and clear state to idle.
---

# spear:refine — Refactor & Complete Task

Final step of the SPEAR cycle. Enters from `arch-done` and exits to `idle`, completing one full red-green-refactor loop for exactly one task.

---

## Procedure

### Step 1 — Assert initial or resumable phase

Read the current phase from `.claude/spear-state.json`. Accept arch-done for an initial invocation, or `refine` when resuming this same task after a failed gate. Reject every other phase. Recheck the current task ID and its Evidence before resuming; do not reset state or skip remaining gates.

### Step 2 — Enter only on the initial invocation

If already in `refine`, do not call `state_set_phase` again. Otherwise use `node tools/spear/state.mjs state_set_phase refine`. The architecture entry phase is `engine-done` for TDD and `spec-done` for DOC/INFRA.

### Step 3 — Read state

Read `.claude/spear-state.json` and recover `currentTaskId`. This identifies which task entry in `docs/tasks.md` will be flipped to `[x]`.

### Step 4 — Refactor pass (optional, scoped)

Perform a behavior-preserving cleanup of code introduced during the engine phase. Rules:

- Tidy naming, extract helpers, remove dead code introduced during engine.
- Do NOT add features, expand the public API, or add new tests.
- Do NOT change behavior — the full test suite is the correctness oracle.
- If nothing needs tidying, skip this step entirely.

### Step 5 — Run the full test suite

Run all mandatory final gates before closing the task:

1. `node tools/spear/ears.mjs docs/requirements.md`
2. `node --test tools/spear/*.test.mjs`
3. Run the project's declared clean build and full verification from its manifest and tech-stack documentation. In this Maven repository use `mvn --batch-mode --no-transfer-progress clean verify`; do not run Maven for non-Maven projects. If no final check is declared, stop and establish it rather than inventing a pass.

Record Java and Node test counts separately. A test-only invocation does not replace clean package verification. If any gate fails:

- Remain in `phase=refine`. Do NOT advance state.
- Record the failure reason in the task's evidence log; do not hand-edit state.
- Fix or revert the refactor changes and retry from Step 4.

Do NOT proceed until the full suite is green.

### Step 6 — Mark task done and append Evidence

Before changing completion status or clearing state, inspect `git diff --cached --name-only`. If any staged path is unrelated to this task, stop in `refine`, retaining `currentTaskId` and leaving the index untouched. Resolve the staging decision with the user and resume this same task; never clear its state first.

In `docs/tasks.md`, locate the entry for `currentTaskId` and flip its checkbox:

- `[~]` → `[x]`
- `[ ]` → `[x]`

If new sources (libraries, docs, APIs) were consulted during the refactor that are not already cited, append a one-line Evidence entry to the task block.

### Step 7 — Clear state

Shell out to `node tools/spear/state.mjs state_clear`.

This resets `phase` to `idle` and clears `currentTaskId`, `reqId`, `testFile`, `testName`, `testStatus`, and `evidenceCited` from `.claude/spear-state.json`.

### Step 8 — Commit

Inspect `git diff --cached --name-only` before staging and again before committing. If unrelated staged paths exist, stop without unstaging or committing them. Stage only this task's files and `docs/tasks.md` as a single commit following the project commit convention. The task is now complete.

---

## Phase transitions

```
arch-done  →  [spear:refine]  →  idle  (cycle complete)
```

Enters from `arch-done`. On any test failure stays in `phase=refine`. On success exits to `idle`.

---

## Reference sources

- `docs/requirements.md` REQ-048
- `docs/implementation.md` §3.6 state helpers, §4.2 TDD cycle
- `node tools/spear/state.mjs`
