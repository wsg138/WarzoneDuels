# Disposable Paper cart-placement check

Use Java 25 and an isolated Paper 26.2 server with WorldEdit 7.4.6, WorldGuard 7.0.19 and the candidate WarzoneDuels JAR. The test uses the shipped arena bounds and footprint. It directly stages a two-participant match and exercises Paper item use with a server-side player. The setup menu, detonation and damage require separate gameplay acceptance.

1. Bind the disposable server to 127.0.0.1. In its empty world, configure a cuboid covering the arena and the outside control rail at (-140,171,-376) with WorldGuard vehicle placement denied. Inside that region, give the duel arena higher priority, allow block placement, build, interaction and TNT, and leave vehicle placement unset.
2. Enable `worldguard.allow-duel-tnt-carts` in the disposable WarzoneDuels configuration. The plugin reads this setting at startup.
3. Compile CartPlacementProbe.java with the extracted Paper server, its library JARs, and WarzoneDuels on the classpath. Package the resulting class with the accompanying plugin.yml in a test-only JAR.
4. Place the probe in the disposable server plugins directory and launch with `-Dwarzoneduels.cart-probe=true`. The probe stops the disposable server after its checks. Verify the log contains `Cart placement checks passed`. Running the probe against the unmodified plugin should fail on the first placement with zero carts.

The probe sets blocks and stages a match in memory. Use a fresh world for each run.
