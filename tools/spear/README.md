# SPEAR project tooling

Upstream: `BadgersMC/spear-plugin` revision `2c91bae`.

`ears.mjs` derives from the upstream EARS validator, with local missing-clause validation. Project-local skills under `.agents/skills/spear-*` derive from the upstream Codex snapshot and are adapted to local Node helpers and evidence-gate ordering. `state.mjs` is the Windows-compatible project adaptation used instead of Bash and `jq`. See UPSTREAM-NOTICE.md for the complete available upstream notice and its provenance.

Typical commands:

```text
node tools/spear/ears.mjs docs/requirements.md
node --test tools/spear/tooling.test.mjs
node tools/spear/state.mjs state_task TDD-002 REQ-004
node tools/spear/state.mjs state_assert_phase idle
node tools/spear/state.mjs state_set_phase spec
```

Transient state is stored under `.claude` and ignored. Durable requirements, task status, and evidence live under `docs`.
