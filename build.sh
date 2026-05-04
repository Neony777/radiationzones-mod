#!/usr/bin/env bash
# Local build helper for RadiationZones mod.
# Requires JDK 21 (https://adoptium.net) on PATH or in JAVA_HOME.
# Will download a portable Gradle distribution into ./.gradle-local if not found.
set -euo pipefail

cd "$(dirname "$0")"

GRADLE_VERSION="8.10.2"
GRADLE_HOME=".gradle-local/gradle-${GRADLE_VERSION}"

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: 'java' not found. Install JDK 21 from https://adoptium.net and re-run." >&2
  exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
if [ "${JAVA_VER:-0}" -lt 21 ]; then
  echo "WARNING: Java ${JAVA_VER} detected, but NeoForge 1.21.1 requires Java 21." >&2
  echo "         Gradle's toolchain plugin will try to auto-provision JDK 21." >&2
fi

if [ ! -x "${GRADLE_HOME}/bin/gradle" ]; then
  echo "==> Downloading Gradle ${GRADLE_VERSION}..."
  mkdir -p .gradle-local
  curl -fsSL -o ".gradle-local/gradle.zip" \
    "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  ( cd .gradle-local && unzip -q -o gradle.zip && rm gradle.zip )
fi

echo "==> Building..."
"${GRADLE_HOME}/bin/gradle" --no-daemon --console=plain build -x test

echo
echo "==> Built JAR(s):"
ls -lh build/libs/
