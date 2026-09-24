#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STATE_FILE="$ROOT/.local-version"
LOCAL_PROPS="$ROOT/local.properties"
OUT_DIR="$ROOT/build-local"

usage() {
  echo "Usage: tools/build-local-aab.sh v-<versionName>"
  echo "Example: tools/build-local-aab.sh v-1.0.7"
  echo "versionName must match MAJOR.MINOR or MAJOR.MINOR.PATCH (e.g. 1.0, 1.0.7)."
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

if [ "$#" -ne 1 ]; then
  usage >&2
  exit 1
fi

RAW="$1"
case "$RAW" in
  v-*) VERSION_NAME="${RAW#v-}" ;;
  *) echo "error: argument must start with v- (got '$RAW')" >&2; usage >&2; exit 1 ;;
esac

case "$VERSION_NAME" in
  *[!0-9.]*|"") echo "error: invalid version '$VERSION_NAME'" >&2; usage >&2; exit 1 ;;
esac

DOT_COUNT="$(printf '%s' "$VERSION_NAME" | tr -cd '.' | wc -c | tr -d ' ')"
if [ "$DOT_COUNT" -lt 1 ] || [ "$DOT_COUNT" -gt 2 ]; then
  echo "error: version must be MAJOR.MINOR or MAJOR.MINOR.PATCH (got '$VERSION_NAME')" >&2
  exit 1
fi

for part in $(printf '%s' "$VERSION_NAME" | tr '.' ' '); do
  case "$part" in
    ""|*[!0-9]*) echo "error: invalid version segment in '$VERSION_NAME'" >&2; exit 1 ;;
  esac
done

prop_from_file() {
  local key="$1"
  local file="$2"
  [ -f "$file" ] || return 0
  grep -E "^${key}=" "$file" 2>/dev/null | tail -n 1 | cut -d'=' -f2- | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's/\r$//' || true
}

if ! command -v java >/dev/null 2>&1 || ! java -version >/dev/null 2>&1; then
  echo "error: no functional Java runtime. Install Temurin 17: brew install --cask temurin@17, then reopen the terminal." >&2
  exit 1
fi

if [ "$(uname)" = "Darwin" ] && ! /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
  echo "error: JDK 17 not found (macOS stub has no runtime). Install Temurin 17: brew install --cask temurin@17, then reopen the terminal." >&2
  exit 1
fi

if ! command -v jarsigner >/dev/null 2>&1; then
  echo "error: jarsigner not found. Install Temurin 17: brew install --cask temurin@17, then reopen the terminal." >&2
  exit 1
fi

JARSIGNER_PROBE="$(jarsigner 2>&1 || true)"
case "$JARSIGNER_PROBE" in
  *"Unable to locate a Java Runtime"*|*"Unable to locate Java"*)
    echo "error: jarsigner has no functional JDK (macOS stub has no runtime). Install Temurin 17: brew install --cask temurin@17, then reopen the terminal." >&2
    exit 1
    ;;
esac

if [ ! -x "$ROOT/gradlew" ]; then
  echo "error: gradlew not found or not executable at $ROOT/gradlew" >&2
  exit 1
fi

CODE=0
if [ -f "$STATE_FILE" ]; then
  SAVED="$(tr -d ' \t\r\n' < "$STATE_FILE")"
  case "$SAVED" in
    ""|*[!0-9]*) echo "error: $STATE_FILE must contain a single integer (got '$SAVED')" >&2; exit 1 ;;
    *) CODE="$SAVED" ;;
  esac
fi
CODE=$((CODE + 1))
if [ "$CODE" -gt 2100000000 ]; then
  echo "error: versionCode $CODE exceeds Google Play maximum 2100000000" >&2
  exit 1
fi
printf '%s' "$CODE" > "$STATE_FILE"

KEYSTORE_PATH="${GRACE_KEYSTORE_PATH:-$(prop_from_file GRACE_KEYSTORE_PATH "$LOCAL_PROPS")}"
KEY_ALIAS="${GRACE_KEY_ALIAS:-$(prop_from_file GRACE_KEY_ALIAS "$LOCAL_PROPS")}"
STORE_PASS="${GRACE_KEYSTORE_PASSWORD:-$(prop_from_file GRACE_KEYSTORE_PASSWORD "$LOCAL_PROPS")}"
KEY_PASS="${GRACE_KEY_PASSWORD:-$(prop_from_file GRACE_KEY_PASSWORD "$LOCAL_PROPS")}"

if [ -z "${KEYSTORE_PATH:-}" ]; then
  KEYSTORE_PATH="$ROOT/keystore_grace.jks"
fi
case "$KEYSTORE_PATH" in
  /*) ;;
  *) KEYSTORE_PATH="$ROOT/$KEYSTORE_PATH" ;;
esac

if [ -z "${KEY_ALIAS:-}" ]; then
  KEY_ALIAS="grace"
fi

if [ ! -f "$KEYSTORE_PATH" ]; then
  echo "error: keystore not found at $KEYSTORE_PATH" >&2
  echo "hint: set GRACE_KEYSTORE_PATH or GRACE_KEYSTORE_PATH= in local.properties" >&2
  exit 1
fi

if [ -z "${STORE_PASS:-}" ]; then
  printf 'Enter keystore password (GRACE_KEYSTORE_PASSWORD): '
  stty -echo
  read -r STORE_PASS
  stty echo
  printf '\n'
fi

if [ -z "${KEY_PASS:-}" ]; then
  printf 'Enter key password (GRACE_KEY_PASSWORD): '
  stty -echo
  read -r KEY_PASS
  stty echo
  printf '\n'
fi

if [ -z "$STORE_PASS" ] || [ -z "$KEY_PASS" ]; then
  echo "error: store and key passwords are required" >&2
  exit 1
fi

export GRACE_KEYSTORE_PATH="$KEYSTORE_PATH"
export GRACE_KEYSTORE_PASSWORD="$STORE_PASS"
export GRACE_KEY_ALIAS="$KEY_ALIAS"
export GRACE_KEY_PASSWORD="$KEY_PASS"

"$ROOT/gradlew" :composeApp:bundleRelease --no-daemon "-PandroidVersionCode=$CODE" "-PandroidVersionName=$VERSION_NAME"

SRC_AAB="$ROOT/composeApp/build/outputs/bundle/release/composeApp-release.aab"
if [ ! -f "$SRC_AAB" ]; then
  echo "error: expected AAB not found at $SRC_AAB" >&2
  exit 1
fi

jarsigner -verify "$SRC_AAB"

mkdir -p "$OUT_DIR"
DEST_AAB="$OUT_DIR/Grace_${VERSION_NAME}.aab"
cp "$SRC_AAB" "$DEST_AAB"

echo "Built $DEST_AAB (versionCode=$CODE versionName=$VERSION_NAME)"