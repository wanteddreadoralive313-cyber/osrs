#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DREAMBOT_HOME="${DREAMBOT_HOME:-$HOME/DreamBot}"
SCRIPTS_DIR="${DREAMBOT_SCRIPTS_DIR:-$DREAMBOT_HOME/Scripts}"
JAR_NAME="roguesden-1.0-SNAPSHOT-jar-with-dependencies.jar"

if [[ ! -d "$DREAMBOT_HOME" ]]; then
  echo "Creating DreamBot directory at $DREAMBOT_HOME" >&2
  mkdir -p "$DREAMBOT_HOME"
fi
mkdir -p "$SCRIPTS_DIR"

"$ROOT_DIR/mvnw" -B -DskipTests package

JAR_PATH="$ROOT_DIR/target/$JAR_NAME"
if [[ ! -f "$JAR_PATH" ]]; then
  echo "Build succeeded but $JAR_PATH was not found." >&2
  exit 1
fi

cp "$JAR_PATH" "$SCRIPTS_DIR/"
echo "Copied $JAR_PATH to $SCRIPTS_DIR"
