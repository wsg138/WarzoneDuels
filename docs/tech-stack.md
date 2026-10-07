# WarzoneDuels technology stack

- Language and runtime baseline: Java 25 (`maven.compiler.release=25`)
- Build: Maven
- Default server API: Paper `26.2.build.123-stable`, provided scope
- Compatibility profile: `paper-26.3` pins Paper `26.3.build.8-alpha`
- Analytics integration: Plan API 5.7-R0.2, provided scope
- Persistence: H2 2.2.224 plus YAML stores
- Tests: JUnit Jupiter 5.11.4
- Packaging: Maven Shade plugin with H2 embedded
- Optional runtime integrations: Vault, EnthusiaTeleport, EnthusiaTags, NotBounties, CombatLogX, and Plan

Local verification uses JDK 25.0.3 and Maven 3.9.9. Run `mvn -B -ntp clean verify` and repeat with `-Ppaper-26.3`; run the stable build last for the testing JAR. SPEAR tools additionally require Node.js and are tested with `node --test tools/spear/tooling.test.mjs`.

Compilation against both pinned APIs is not live server/client approval. Keep the staging checklist in MANUAL_TESTING.md.
