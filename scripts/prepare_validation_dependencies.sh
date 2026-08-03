#!/usr/bin/env bash
set -euo pipefail

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
mkdir -p "$work_dir/src" "$work_dir/classes" "$work_dir/empty"

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

mkdir -p "$work_dir/src/com/djrapitops/plan/capability"
mkdir -p "$work_dir/src/com/djrapitops/plan/delivery/web/resolver/exception"
mkdir -p "$work_dir/src/com/djrapitops/plan/delivery/web/resolver/request"
mkdir -p "$work_dir/src/com/djrapitops/plan/delivery/web/resolver"
mkdir -p "$work_dir/src/com/djrapitops/plan/delivery/web"
mkdir -p "$work_dir/src/com/djrapitops/plan/extension/annotation"
mkdir -p "$work_dir/src/com/djrapitops/plan/extension/icon"
mkdir -p "$work_dir/src/com/djrapitops/plan/extension"
cat > "$work_dir/src/com/djrapitops/plan/capability/CapabilityService.java" <<'JAVA'
package com.djrapitops.plan.capability;

import java.util.function.Consumer;

public final class CapabilityService {
    private static final CapabilityService INSTANCE = new CapabilityService();

    public static CapabilityService getInstance() {
        return INSTANCE;
    }

    public boolean hasCapability(String capability) {
        return true;
    }

    public void registerEnableListener(Consumer<Boolean> listener) {
    }
}
JAVA
cat > "$work_dir/src/com/djrapitops/plan/delivery/web/ResolverService.java" <<'JAVA'
package com.djrapitops.plan.delivery.web;

import com.djrapitops.plan.delivery.web.resolver.Resolver;
import java.util.Optional;

public final class ResolverService {
    private static final ResolverService INSTANCE = new ResolverService();

    public static ResolverService getInstance() {
        return INSTANCE;
    }

    public Optional<Resolver> getResolver(String path) {
        return Optional.empty();
    }

    public void registerResolver(String name, String path, Resolver resolver) {
    }
}
JAVA
cat > "$work_dir/src/com/djrapitops/plan/extension/ExtensionService.java" <<'JAVA'
package com.djrapitops.plan.extension;

public final class ExtensionService {
    private static final ExtensionService INSTANCE = new ExtensionService();

    public static ExtensionService getInstance() {
        return INSTANCE;
    }

    public void register(DataExtension extension) {
    }
}
JAVA
cat > "$work_dir/src/com/djrapitops/plan/extension/CallEvents.java" <<'JAVA'
package com.djrapitops.plan.extension;

public enum CallEvents {
    PLAYER_JOIN,
    PLAYER_LEAVE
}
JAVA
cat > "$work_dir/src/com/djrapitops/plan/extension/DataExtension.java" <<'JAVA'
package com.djrapitops.plan.extension;

public interface DataExtension {
    CallEvents[] callExtensionMethodsOn();
}
JAVA
cat > "$work_dir/src/com/djrapitops/plan/extension/icon/Color.java" <<'JAVA'
package com.djrapitops.plan.extension.icon;

public enum Color {
    RED,
    AMBER,
    YELLOW,
    LIGHT_BLUE,
    GREEN,
    GREY
}
JAVA
cat > "$work_dir/src/com/djrapitops/plan/extension/icon/Family.java" <<'JAVA'
package com.djrapitops.plan.extension.icon;

public enum Family {
    SOLID
}
JAVA
cat > "$work_dir/src/com/djrapitops/plan/extension/annotation/NumberProvider.java" <<'JAVA'
package com.djrapitops.plan.extension.annotation;

import com.djrapitops.plan.extension.icon.Color;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NumberProvider {
    String text();
    String description();
    String iconName();
    Color iconColor();
    int priority();
    boolean showInPlayerTable();
}
JAVA
cat > "$work_dir/src/com/djrapitops/plan/extension/annotation/PluginInfo.java" <<'JAVA'
package com.djrapitops.plan.extension.annotation;

import com.djrapitops.plan.extension.icon.Color;
import com.djrapitops.plan.extension.icon.Family;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PluginInfo {
    String name();
    String iconName();
    Family iconFamily();
    Color color();
}
JAVA
cat > "$work_dir/src/com/djrapitops/plan/delivery/web/resolver/MimeType.java" <<'JAVA'
package com.djrapitops.plan.delivery.web.resolver;

public enum MimeType {
    JSON,
    HTML
}
JAVA
cat > "$work_dir/src/com/djrapitops/plan/delivery/web/resolver/Resolver.java" <<'JAVA'
package com.djrapitops.plan.delivery.web.resolver;

import com.djrapitops.plan.delivery.web.resolver.request.Request;
import java.util.Optional;

public interface Resolver {
    boolean canAccess(Request request);
    Optional<Response> resolve(Request request);
}
JAVA
cat > "$work_dir/src/com/djrapitops/plan/delivery/web/resolver/Response.java" <<'JAVA'
package com.djrapitops.plan.delivery.web.resolver;

public final class Response {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        public Builder setMimeType(MimeType mimeType) {
            return this;
        }

        public Builder setJSONContent(Object content) {
            return this;
        }

        public Builder setContent(String content) {
            return this;
        }

        public Response build() {
            return new Response();
        }
    }
}
JAVA
cat > "$work_dir/src/com/djrapitops/plan/delivery/web/resolver/exception/NotFoundException.java" <<'JAVA'
package com.djrapitops.plan.delivery.web.resolver.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
JAVA
cat > "$work_dir/src/com/djrapitops/plan/delivery/web/resolver/request/URIPath.java" <<'JAVA'
package com.djrapitops.plan.delivery.web.resolver.request;

public final class URIPath {
    public String asString() {
        return "";
    }
}
JAVA
cat > "$work_dir/src/com/djrapitops/plan/delivery/web/resolver/request/URIQuery.java" <<'JAVA'
package com.djrapitops.plan.delivery.web.resolver.request;

import java.util.Optional;

public final class URIQuery {
    public Optional<String> get(String key) {
        return Optional.empty();
    }
}
JAVA
cat > "$work_dir/src/com/djrapitops/plan/delivery/web/resolver/request/WebUser.java" <<'JAVA'
package com.djrapitops.plan.delivery.web.resolver.request;

public final class WebUser {
    public WebUser(String name) {
    }

    public boolean hasPermission(String permission) {
        return true;
    }
}
JAVA
cat > "$work_dir/src/com/djrapitops/plan/delivery/web/resolver/request/Request.java" <<'JAVA'
package com.djrapitops.plan.delivery.web.resolver.request;

import java.util.Optional;

public interface Request {
    Optional<WebUser> getUser();
    String getMethod();
    URIPath getPath();
    URIQuery getQuery();
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
  local group_path="${group//./\/}"
  local destination="$HOME/.m2/repository/$group_path/$artifact/$version"
  mkdir -p "$destination"
  cp "$file" "$destination/$artifact-$version.jar"
  cat > "$destination/$artifact-$version.pom" <<POM
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$group</groupId>
  <artifactId>$artifact</artifactId>
  <version>$version</version>
  <packaging>jar</packaging>
</project>
POM
}

install_stub "$work_dir/validation-apis.jar" com.github.MilkBowl VaultAPI 1.7.1
install_stub "$work_dir/empty.jar" com.github.sirblobman.api core 2.9-20251215.173206-57
install_stub "$work_dir/validation-apis.jar" com.github.sirblobman.combatlogx api 11.6-SNAPSHOT
install_stub "$work_dir/validation-apis.jar" com.github.plan-player-analytics Plan 5.6.2965
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
