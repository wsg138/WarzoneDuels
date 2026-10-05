---
name: spear-init
description: Bootstrap SPEAR docs in a greenfield project — detects stack, drafts the four docs, drops Konsist template on JVM, commits.
---

# spear:init — Greenfield SPEAR Scaffolding

Invoked when the user wants to initialize a new SPEAR project. Runs on a clean project (no SPEAR docs yet). Init has no predecessor phase — it sets context so later `spear:spec` can run from `idle`. Init itself is idempotent and does NOT require a prior phase.

Templates live under `${CLAUDE_PLUGIN_ROOT}/templates/`. The four templates are `tech-stack.md`, `requirements.md`, `implementation.md`, `tasks.md`; the JVM-only Konsist drop is `LayerRulesTest.kt`.

## Procedure

### 1. Detect language/framework

Probe the project root for these files, in order, and record which exist:

- `build.gradle.kts`, `build.gradle`, `pom.xml`  -> JVM
- `package.json`                                  -> Node
- `pyproject.toml`                                -> Python
- `Cargo.toml`                                    -> Rust
- `go.mod`                                        -> Go

For each detected manifest, parse dependency coordinates and pre-fill `docs/tech-stack.md` (language, framework, versions, build tool). If none match, emit a one-line notice and proceed with a blank `tech-stack.md`.

### 2. Interactive drafting — requirements

Ask the user (in this order): project purpose, primary users, top 3–5 goals. Draft `docs/requirements.md` with EARS-formatted entries.

**Validate every REQ entry BEFORE writing** by shelling out to the EARS validator (takes a file path — write the candidate to a temp file first):

```
node tools/spear/ears.mjs <tmpfile>
```

Exit 0 = valid. Non-zero = reject and re-draft. REQ-IDs are the next free integer above the max existing (padded to three digits, e.g. `REQ-001`); never re-use or renumber.

### 3. Architectural constraints

Draft `docs/implementation.md` from the template. It MUST contain, verbatim in shape:

- A `## Layer Dependency Rules` section listing domain <- application <- infrastructure precedence (exact heading is required by `spear:arch`).
- An empty `## Forbidden Domain Annotations` section whose YAML body is `forbidden: []` (exact heading + key required by `spear:arch`).

These two sections are load-bearing. Do not rename, merge, or reorder them.

### 4. Derive tasks.md

Generate `docs/tasks.md`. Every initial task MUST:

- Carry exactly one tag: `TDD`, `DOC`, or `INFRA`.
- Include a `References:` line listing the REQ-IDs and doc sections it implements.
- Include an empty `Evidence:` block (the task owner fills it during execution).

### 5. JVM Konsist drop

IF JVM was detected in step 1 AND the build already configures Kotlin test compilation and the Konsist test dependency:

Copy `${CLAUDE_PLUGIN_ROOT}/templates/LayerRulesTest.kt` to `src/test/kotlin/architecture/LayerRulesTest.kt`. Substitute `__BASE_PACKAGE__` with the detected top-level package (read from Gradle/Maven config, e.g. `group` + main source-set package). Create parent directories as needed.

### 6. Non-JVM notice

Otherwise emit `Skipping Konsist template (Kotlin test compilation and Konsist are not configured).` Java-only Maven projects must use their existing Java architecture checks; file existence alone is not validation. Do not silently add Kotlin or dependencies.

### 7. Commit

Stage exactly the four generated docs plus the Konsist file when emitted:

```
git add docs/tech-stack.md docs/requirements.md docs/implementation.md docs/tasks.md
# plus src/test/kotlin/architecture/LayerRulesTest.kt on JVM
git commit -m "chore(spear): initialize SPEAR docs"
```

Inspect `git diff --cached --name-only` before and after staging. If unrelated staged paths exist, stop without unstaging or committing them. Do not include any other paths in this commit.

## Acceptance

- Four docs exist under `docs/`.
- Where Kotlin test compilation and Konsist are configured, `LayerRulesTest.kt` exists with `__BASE_PACKAGE__` substituted and actually compiles/runs. Otherwise record the explicit skip and existing architecture gate.
- Every REQ in `requirements.md` passes the EARS validator.
- Every task in `tasks.md` has a tag, `References:`, and an empty `Evidence:`.
- A single commit with the required subject contains exactly the listed files.
