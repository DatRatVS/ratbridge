#!/usr/bin/env sh
set -eu

GRADLE_VERSION="8.8"
DIST_NAME="gradle-${GRADLE_VERSION}-bin"
DIST_URL="https://services.gradle.org/distributions/${DIST_NAME}.zip"
BASE_DIR="${GRADLE_USER_HOME:-"$HOME/.gradle"}/wrapper/dists/${DIST_NAME}"
GRADLE_HOME="${BASE_DIR}/gradle-${GRADLE_VERSION}"
ZIP_PATH="${BASE_DIR}/${DIST_NAME}.zip"

if [ ! -x "${GRADLE_HOME}/bin/gradle" ]; then
    mkdir -p "${BASE_DIR}"
    if [ ! -f "${ZIP_PATH}" ]; then
        curl -fsSL "${DIST_URL}" -o "${ZIP_PATH}"
    fi
    unzip -q "${ZIP_PATH}" -d "${BASE_DIR}"
fi

exec "${GRADLE_HOME}/bin/gradle" "$@"
