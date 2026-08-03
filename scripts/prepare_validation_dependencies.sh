#!/usr/bin/env bash
set -euo pipefail

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
mkdir -p "$work_dir/src" "$work_dir/classes" "$work_dir/empty"

cat > "$work_dir/minimal-pom.xml" <<'POM'
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>validation</groupId>
  <artifactId>dependency-stubs</artifactId>
  <version>1</version>
</project>
POM

mkdir -p "$work_dir/src/net/milkbowl/vault/economy"
cat > "$work_dir/src/net/milkbowl/vault/economy/Economy.java" <<'JAVA'
package net.milkbowl.vault.economy;

public interface Economy {
    boolean has(Object player, double amount);
    EconomyResponse withdrawPlayer(Object player, double amount);
    EconomyResponse depositPlayer(Object player, double amount);
}
JAVA
cat > "$work_dir/src/net/milkbowl/vault/economy/EconomyResponse.java" <<'JAVA'
package net.milkbowl.vault.economy;

public class EconomyResponse {
    public boolean transactionSuccess() {
        return true;
    }
}
JAVA

mkdir -p "$work_dir/src/com/github/sirblobman/combatlogx/api/manager"
mkdir -p "$work_dir/src/com/github/sirblobman/combatlogx/api/object"
mkdir -p "$work_dir/src/com/github/sirblobman/combatlogx/api/event"
mkdir -p "$work_dir/src/com/github/sirblobman/combatlogx/api"
cat > "$work_dir/src/com/github/sirblobman/combatlogx/api/ICombatLogX.java" <<'JAVA'
package com.github.sirblobman.combatlogx.api;

import com.github.sirblobman.combatlogx.api.manager.ICombatManager;

public interface ICombatLogX {
    ICombatManager getCombatManager();
}
JAVA
cat > "$work_dir/src/com/github/sirblobman/combatlogx/api/manager/ICombatManager.java" <<'JAVA'
package com.github.sirblobman.combatlogx.api.manager;

import com.github.sirblobman.combatlogx.api.object.UntagReason;

public interface ICombatManager {
    boolean isInCombat(Object player);
    void untag(Object player, UntagReason reason);
}
JAVA
cat > "$work_dir/src/com/github/sirblobman/combatlogx/api/object/UntagReason.java" <<'JAVA'
package com.github.sirblobman.combatlogx.api.object;

public enum UntagReason {
    EXPIRE
}
JAVA
cat > "$work_dir/src/com/github/sirblobman/combatlogx/api/event/PlayerPreTagEvent.java" <<'JAVA'
package com.github.sirblobman.combatlogx.api.event;

import java.util.UUID;

public class PlayerPreTagEvent {
    public CombatPlayer getPlayer() {
        return null;
    }

    public void setCancelled(boolean cancelled) {
    }

    public interface CombatPlayer {
        UUID getUniqueId();
    }
}
JAVA

javac -d "$work_dir/classes" $(find "$work_dir/src" -name '*.java' -print)
jar --create --file "$work_dir/validation-apis.jar" -C "$work_dir/classes" .
jar --create --file "$work_dir/empty.jar" -C "$work_dir/empty" .

install_stub() {
  local file="$1"
  local group="$2"
  local artifact="$3"
  local version="$4"
  mvn -q -ntp -f "$work_dir/minimal-pom.xml" \
    org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="$file" \
    -DgroupId="$group" \
    -DartifactId="$artifact" \
    -Dversion="$version" \
    -Dpackaging=jar \
    -DgeneratePom=true
}

install_stub "$work_dir/validation-apis.jar" com.github.MilkBowl VaultAPI 1.7.1
install_stub "$work_dir/empty.jar" com.github.sirblobman.api core 2.9-20251215.173206-57
install_stub "$work_dir/validation-apis.jar" com.github.sirblobman.combatlogx api 11.6-SNAPSHOT
install_stub "$work_dir/empty.jar" org.enthusia enthusia-teleport 1.2.6
install_stub "$work_dir/empty.jar" org.enthusia EnthusiaTags 2.1.0

python3 <<'PY'
from pathlib import Path

source = Path('pom.xml').read_text()
jitpack = '''        <repository>
            <id>jitpack</id>
            <url>https://jitpack.io</url>
        </repository>
'''
if source.count(jitpack) != 1:
    raise SystemExit('expected exactly one JitPack repository block')
Path('pom.validation.xml').write_text(source.replace(jitpack, '', 1))
PY
