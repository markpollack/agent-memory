#!/usr/bin/env bash
set -euo pipefail

# Determine cache dir
CACHE_DIR="${TRIVY_CACHE_DIR:-}"
if [ -z "${CACHE_DIR}" ]; then
  echo "Error: TRIVY_CACHE_DIR environment variable is not set." >&2
  echo "Usage: TRIVY_CACHE_DIR=/path/to/trivy-cache $0" >&2
  exit 1
fi

if [ ! -d "${CACHE_DIR}" ]; then
  echo "Error: Cache directory ${CACHE_DIR} does not exist." >&2
  exit 1
fi

echo "Using Trivy cache: ${CACHE_DIR}"

# 1. Scan aggregate CycloneDX SBOM if it exists
SBOM_PATH="target/agent-memory-bom.json"
if [ -f "${SBOM_PATH}" ]; then
  echo "Scanning aggregate SBOM: ${SBOM_PATH}"
  trivy sbom \
    --cache-dir "${CACHE_DIR}" \
    --skip-db-update \
    --skip-java-db-update \
    --offline-scan \
    --disable-telemetry \
    --scanners vuln \
    --format table \
    "${SBOM_PATH}"
else
  echo "Warning: SBOM file ${SBOM_PATH} not found. Run './mvnw verify' first to generate it." >&2
fi

# 2. Assemble isolated runtime closure and run trivy rootfs
echo "Assembling isolated runtime JAR closure..."
CLOSURE_DIR="target/runtime-closure"
rm -rf "${CLOSURE_DIR}"
mkdir -p "${CLOSURE_DIR}"

# Copy project binary JARs (excluding sources/javadoc)
find memory-core/target memory-advisor/target -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" -exec cp {} "${CLOSURE_DIR}/" \;

# Copy runtime dependencies via Maven
./mvnw dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory="$(pwd)/${CLOSURE_DIR}" -Dmaven.repo.local="${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}"

echo "Scanning runtime JAR closure with trivy rootfs..."
trivy rootfs \
  --cache-dir "${CACHE_DIR}" \
  --skip-db-update \
  --skip-java-db-update \
  --offline-scan \
  --disable-telemetry \
  --scanners vuln \
  --format table \
  "${CLOSURE_DIR}"
