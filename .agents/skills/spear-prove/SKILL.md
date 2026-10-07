---
name: spear-prove
description: Write a failing test that proves the current REQ is unsatisfied, confirm red, and gate on Evidence before advancing state.
---

# spear:prove — Write Failing Test (Red)

Enters from phase `spec-done` and exits to phase `prove-done`. It is the red half of the red-green-refactor cycle. For DOC and INFRA tasks this skill is skipped entirely — those tasks flow `spec-done → arch` directly.

---

## Procedure

### Step 1 — SPEAR-project detection

Check whether both `docs/requirements.md` and `docs/tasks.md` exist in the project root.

If either file is absent, print:

```
not a SPEAR project; use test-driven-development
```

Stop immediately. Do NOT mutate any state.

### Step 2 — Assert predecessor phase

Read the current task and phase. Accept `spec-done` initially or `prove` when resuming the same task after a failed evidence gate. Reject every other phase. Recheck the task and evidence on resume; do not reset state.

### Step 3 — Evidence gate

Read the `Evidence:` block for the current task in `docs/tasks.md`. If the block contains only whitespace or a single placeholder space, refuse to proceed:

```
Evidence block is empty for task <taskId>. Populate Evidence: before invoking spear:prove.
```

Do NOT change phase. Remain in `spec-done`, populate the evidence, and retry from Step 2.

### Step 4 — Set phase to `prove`

Only on the initial `spec-done` invocation, after the Evidence gate succeeds, run `node tools/spear/state.mjs state_set_phase prove`. When already in `prove`, skip this transition, reuse the existing failing test, rerun it as needed and retry the import-evidence gate. Never record red unless the behavior assertion still fails meaningfully.

### Step 5 — Write the failing test

Identify the REQ-ID referenced by the current task. Write a test file that:

- Targets the exact behaviour the requirement specifies (not an approximation).
- Uses the idiomatic test framework for the detected language:
  - **JVM (Kotlin):** Kotest or JUnit 5
  - **Node:** `node --test`, Vitest, or Jest (match the project's existing choice)
  - **Python:** pytest
  - Other stacks: use the project's declared test framework from `docs/tech-stack.md`.
- Asserts the described behaviour so that it fails today because the implementation does not exist.
- Contains no stub implementations that make it pass trivially.

### Step 6 — Run the test; confirm red

Execute the test and capture its output. The test MUST fail because the behaviour under test does not yet exist — not for an unrelated reason.

If the test passes, fix it and re-run. Do NOT advance state with a bogus red. If it errors for an unrelated reason (e.g. a compile error), fix that first so the failure is meaningful.

### Step 7 — Import-diff gate

Compute the set of new third-party and internal import paths introduced by the test file relative to the project baseline (files that existed before this task began).

For each new import, check whether it appears as a substring in any line of the current task's `Evidence:` block. If any import is not covered, print:

```
Add evidence for: <import>, <import> …
```

Do NOT call `state_record_test` or `state_set_phase prove-done`. The agent must update `Evidence:` in `docs/tasks.md` and then re-invoke `spear:prove` from Step 7.

### Step 8 — Record red

Shell out to:

```
node tools/spear/state.mjs state_record_test <testFile> <testName> red
```

Where `<testFile>` is the path to the test file and `<testName>` is the individual test or spec name that is failing.

### Step 9 — Set phase to `prove-done`

Shell out to `node tools/spear/state.mjs state_set_phase prove-done`.

---

## Phase transitions

```
spec-done  →  [spear:prove]  →  prove-done
                                    ↓
                             spear:engine
```

DOC / INFRA tasks skip prove entirely:

```
spec-done  →  spear:arch  (no prove/engine)
```

---

## Reference sources

- `docs/requirements.md` REQ-030, REQ-031, REQ-032, REQ-046, REQ-091, REQ-092
- `docs/implementation.md` §3.6 (state helpers), §4.2 (TDD cycle), §5 (briefing contract)
- `node tools/spear/state.mjs`
