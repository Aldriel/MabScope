#!/usr/bin/env bash
#
# Installs in the local Maven repository the version of pride-core required by the pom: the
# calculations shared with ProteoSpectra-Explorer, built from its release tag on GitHub.
#
# Usage, from the root of the repository: deploy/install-pride-core.sh [work directory]

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${1:-$(mktemp -d)}"
VERSION="$(sed -n 's:.*<pride.version>\(.*\)</pride.version>.*:\1:p' "$ROOT/pom.xml")"

rm -rf "$WORK/pride"
git clone -q --depth 1 --branch "v$VERSION" \
  https://github.com/Aldriel/ProteoSpectra-Explorer.git "$WORK/pride"
mvn -B -q -f "$WORK/pride/pom.xml" -pl core -am install \
  -DskipTests -Dcheckstyle.skip -Djacoco.skip
echo "pride-core $VERSION installed."
