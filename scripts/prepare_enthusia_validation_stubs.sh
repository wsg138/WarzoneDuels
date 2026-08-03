#!/usr/bin/env bash
set -euo pipefail

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
mkdir -p "$work_dir/src/org/enthusia/teleport/api"
mkdir -p "$work_dir/src/org/enthusia/tags/api"
mkdir -p "$work_dir/classes"

cat > "$work_dir/src/org/enthusia/teleport/api/CancelReason.java" <<'JAVA'
package org.enthusia.teleport.api;

public enum CancelReason {
    DUEL_SPECTATE
}
JAVA
cat > "$work_dir/src/org/enthusia/teleport/api/TeleportApi.java" <<'JAVA'
package org.enthusia.teleport.api;

import java.util.UUID;

public interface TeleportApi {
    void cancelAllRequestsInvolving(UUID playerId, CancelReason reason);
}
JAVA
cat > "$work_dir/src/org/enthusia/tags/api/TagVisibilityService.java" <<'JAVA'
package org.enthusia.tags.api;

import java.util.UUID;

public interface TagVisibilityService {
    void suppress(UUID playerId, String owner);
    void unsuppress(UUID playerId, String owner);
}
JAVA

javac -d "$work_dir/classes" $(find "$work_dir/src" -name '*.java' -print)
plan_stub="$HOME/.m2/repository/com/github/plan-player-analytics/Plan/5.6.2965/Plan-5.6.2965.jar"
test -f "$plan_stub"
jar --update --file "$plan_stub" -C "$work_dir/classes" .
