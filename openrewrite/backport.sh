#!/usr/bin/env bash
#
# Generate the JDK17/javax variant of the Adyen plugin from the JDK21/jakarta source
# of truth by running the OpenRewrite recipe com.adyen.backport.Jdk21ToJdk17.
#
# Usage:
#   ./backport.sh --dry            # show the patch, change nothing (rewriteDryRun)
#   ./backport.sh --in-place       # rewrite the current plugin tree in place (CI mode)
#   ./backport.sh --out <dir>      # copy the plugin tree to <dir>, rewrite the copy
#
# Requires a JDK21 JVM and Gradle (./gradlew if present, else a `gradle` on PATH).
#
set -euo pipefail

SHIM_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLUGIN_ROOT="$(cd "$SHIM_DIR/.." && pwd)"

MODE="dry"
OUT=""
case "${1:-}" in
  --dry|"")    MODE="dry" ;;
  --in-place)  MODE="inplace" ;;
  --out)       MODE="out"; OUT="${2:?--out requires a target directory}" ;;
  *) echo "Unknown option: $1" >&2; sed -n '3,13p' "$0"; exit 2 ;;
esac

# --- JDK check -------------------------------------------------------------
JV="$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"' || true)"
if [ "${JV:-0}" != "21" ]; then
  echo "WARNING: backport must run on JDK21 (jakarta sources). Detected: ${JV:-unknown}." >&2
  echo "         Set JAVA_HOME to a 21 JDK before running." >&2
fi

# --- Pick gradle -----------------------------------------------------------
if [ -x "$SHIM_DIR/gradlew" ]; then
  GRADLE="$SHIM_DIR/gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE="gradle"
else
  echo "ERROR: no Gradle found. Install Gradle 8.x (e.g. 'sdk install gradle'), then optionally" >&2
  echo "       run 'gradle wrapper --gradle-version 8.8' in $SHIM_DIR to pin a wrapper." >&2
  exit 1
fi

# --- Choose the tree to operate on ----------------------------------------
TARGET_SHIM="$SHIM_DIR"
if [ "$MODE" = "out" ]; then
  echo ">> Copying plugin tree to $OUT (excluding .git, gensrc, build) ..."
  mkdir -p "$OUT"
  rsync -a --delete \
    --exclude '.git/' --exclude '**/gensrc/' --exclude '**/build/' --exclude '**/classes/' \
    "$PLUGIN_ROOT"/ "$OUT"/
  TARGET_SHIM="$OUT/openrewrite"
fi

# --- Run -------------------------------------------------------------------
if [ "$MODE" = "dry" ]; then
  echo ">> rewriteDryRun (no files changed). Patch -> build/reports/rewrite/rewrite.patch"
  ( cd "$TARGET_SHIM" && "$GRADLE" rewriteDryRun )
  echo ">> Review: $TARGET_SHIM/build/reports/rewrite/rewrite.patch"
else
  echo ">> rewriteRun — mutating $( [ "$MODE" = out ] && echo "$OUT" || echo "$PLUGIN_ROOT" )"
  ( cd "$TARGET_SHIM" && "$GRADLE" rewriteRun )
  echo ">> Done. Now build/test this tree against a JDK17 SAP Commerce platform."
  echo ">> Re-verify the category-B behaviours listed in openrewrite/README.md."
fi
