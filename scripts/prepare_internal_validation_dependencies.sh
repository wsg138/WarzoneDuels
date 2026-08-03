#!/usr/bin/env bash
set -euo pipefail

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
mkdir -p "$work_dir/teleport-src/org/enthusia/teleport/api"
mkdir -p "$work_dir/tags-src/org/enthusia/tags/api"
mkdir -p "$work_dir/teleport-classes" "$work_dir/tags-classes"

cat > "$work_dir/teleport-src/org/enthusia/teleport/api/CancelReason.java" <<'JAVA'
package org.enthusia.teleport.api;

public enum CancelReason {
    DUEL_SPECTATE
}
JAVA

cat > "$work_dir/teleport-src/org/enthusia/teleport/api/TeleportApi.java" <<'JAVA'
package org.enthusia.teleport.api;

import java.util.UUID;

public interface TeleportApi {
    int cancelAllRequestsInvolving(UUID playerId, CancelReason reason);
}
JAVA

cat > "$work_dir/tags-src/org/enthusia/tags/api/TagVisibilityService.java" <<'JAVA'
package org.enthusia.tags.api;

import java.util.UUID;

public interface TagVisibilityService {
    void suppress(UUID playerId, Object owner);
    void unsuppress(UUID playerId, Object owner);
    boolean isSuppressed(UUID playerId);
}
JAVA

javac -d "$work_dir/teleport-classes" $(find "$work_dir/teleport-src" -name '*.java' -print)
javac -d "$work_dir/tags-classes" $(find "$work_dir/tags-src" -name '*.java' -print)
jar --create --file "$work_dir/enthusia-teleport-1.2.6.jar" -C "$work_dir/teleport-classes" .
jar --create --file "$work_dir/EnthusiaTags-2.1.0.jar" -C "$work_dir/tags-classes" .

mvn -B -q install:install-file \
  -Dfile="$work_dir/enthusia-teleport-1.2.6.jar" \
  -DgroupId=org.enthusia \
  -DartifactId=enthusia-teleport \
  -Dversion=1.2.6 \
  -Dpackaging=jar \
  -DgeneratePom=true

mvn -B -q install:install-file \
  -Dfile="$work_dir/EnthusiaTags-2.1.0.jar" \
  -DgroupId=org.enthusia \
  -DartifactId=EnthusiaTags \
  -Dversion=2.1.0 \
  -Dpackaging=jar \
  -DgeneratePom=true
