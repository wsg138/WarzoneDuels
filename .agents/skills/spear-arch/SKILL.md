---
name: spear-arch
description: Enforce layer dependency rules and the domain-annotation denylist on changed files, gate on Evidence, and advance state to arch-done.
---

# spear:arch — Layer Boundary Enforcement

The architectural gate. Runs after `engine-done` on TDD tasks and after `spec-done` on DOC / INFRA tasks. Refuses to advance to `refine` while any layer violation or forbidden annotation is present.

---

## Procedure

### Step 1 — Assert initial or resumable phase

Read the current phase from `.claude/spear-state.json`. Accept engine-done or spec-done for an initial invocation, or `arch` when resuming this same task after a failed gate. Reject every other phase. Recheck the current task ID and its Evidence before resuming; do not reset state or skip remaining gates.

### Step 2 — Enter only on the initial invocation

If already in `arch`, do not call `state_set_phase` again. Otherwise use `node tools/spear/state.mjs state_set_phase arch`. The architecture entry phase is `engine-done` for TDD and `spec-done` for DOC/INFRA.

### Step 3 — Read layer rules

Parse the consumer project's `docs/implementation.md` section `## Layer Dependency Rules`. In WarzoneDuels, paths are relative to `src/main/java/dev/minecraft/warzoneduels/`; `app/**` is application and `adapter/**` is infrastructure. Apply the documented brownfield exceptions to unchanged coupling, never to new coupling. Three conceptual layers:

- `domain/**` — may depend on nothing beyond itself + stdlib.
- `application/**` — may depend only on `domain/**`, project `port/**` + stdlib.
- `infrastructure/**` — unconstrained.

Also parse `## Forbidden Domain Annotations` and extract the `forbidden: [...]` list.

### Step 4 — Enumerate changed files

Run `git diff --name-only` against the arch baseline (fall back to working tree). Normalize repository-relative paths by removing `src/main/java/dev/minecraft/warzoneduels/` before classification: `domain/**` is domain, `app/**` is application, and `adapter/**` is infrastructure. Files outside that source root are not gameplay layer files. Apply the documented brownfield exceptions only to existing coupling.

### Step 5 — Validate import direction

For every changed file, scan imports and apply its layer's rule:

- `domain/**`: imports outside domain + stdlib, including application, ports, infrastructure, Bukkit, Paper or Adventure → FAIL `file:line:symbol`, except unchanged coupling explicitly recorded in the project's brownfield exceptions.
- `application/**`: packages outside domain, project ports + stdlib → FAIL `file:line:symbol`, subject only to those documented existing-coupling exceptions.
- `infrastructure/**`: allow.

Collect all violations — do not early-exit.

### Step 6 — Annotation denylist

For every file under `domain/**`, scan annotations against the union of:

- Defaults: `org.springframework.*`, `jakarta.persistence.*`, `javax.persistence.*`, `com.fasterxml.jackson.*`, `io.micronaut.*`, `lombok.*`.
- Project `forbidden:` patterns from Step 3.

Each match → FAIL `file:line:annotation`.

### Step 7 — Import-diff evidence gate

Compute new import paths introduced since the task baseline. Each must appear as a substring of some line in the task's `Evidence:` block in `docs/tasks.md`. On any miss, print:

```
Add evidence for: <import>, <import> …
```

The gate is hard: do NOT advance phase. Record the diagnostic in task evidence, update `Evidence:`, and re-invoke without hand-editing state.

### Step 8 — On violation

If Steps 5–7 produced findings, emit a report grouped by file. Stay in `phase=arch` and record the failure reason in task evidence. Suggest fixes:

- Layer break: move the type, or introduce a port interface in `domain/` with the adapter in `infrastructure/`.
- Forbidden annotation: extract framework wiring to an `infrastructure/` adapter; keep domain annotation-free.
- Missing evidence: add a citation to the task's `Evidence:` block.

### Step 9 — On clean scan

`node tools/spear/state.mjs state_set_phase arch-done`.

---

## Phase transitions

TDD path: `engine-done → [spear:arch] → arch-done → spear:refine`.

DOC / INFRA path: `spec-done → [spear:arch] → arch-done → spear:refine`.

---

## Reference sources

- `docs/requirements.md` REQ-031, REQ-032, REQ-060, REQ-061, REQ-062, REQ-063, REQ-064, REQ-065, REQ-067
- `docs/implementation.md` §2 Layer Dependency Rules, `## Forbidden Domain Annotations`, §3.6 state helpers
- `node tools/spear/state.mjs`
