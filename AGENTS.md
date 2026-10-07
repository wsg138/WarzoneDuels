# WarzoneDuels development

Use the SPEAR workflow from `.agents/skills/spear-using-spear/SKILL.md`.
Read `docs/requirements.md`, `docs/implementation.md`, `docs/tasks.md`, and `.claude/spear-state.json` before changing behavior.
Use `tools/spear/state.mjs` for phase tracking and validate requirements with `tools/spear/ears.mjs`.
Follow `spec -> prove -> engine -> arch -> refine` for behavioral work. Documentation and infrastructure tasks skip prove and engine.
Require a meaningful failing test before implementing new behavior. Do not fabricate red/green evidence for the brownfield baseline.
Run the EARS validator and `mvn -B -ntp clean verify` before closing a task.

Keep the framework-free competitive core in `domain` and Bukkit/Paper interaction in `adapter`. Existing framework-coupled domain files are recorded brownfield debt; do not add new coupling, and remove it when touching those types substantially.
WarzoneDuels owns duel parties, challenges, matches, and results. Guilds and wars remain outside this repository until an explicit integration task is approved.
Do not push, deploy, release, or open a pull request without explicit user authorization.
