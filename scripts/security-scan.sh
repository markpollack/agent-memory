#!/usr/bin/env bash
# Local/offline security scan for Agent Memory.
# Accepts a caller-supplied validated Trivy cache directory.
# Does NOT acquire vulnerability databases remotely.
#
# Usage:
#   TRIVY_CACHE_DIR=/path/to/trivy-cache ./scripts/security-scan.sh
#
# If TRIVY_CACHE_DIR is not set, the script aborts (no default cache).
#
# This script performs TWO scans:
#   1. trivy sbom — against the root aggregate CycloneDX JSON
#   2. trivy rootfs — against the runtime JAR closure in an isolated directory
#
# The aggregate SBOM must exist first. Generate it with:
#   ./mvnw -pl . verify -DskipTests -Dmaven.repo.local=/path/to/isolated/m2
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${TRIVY_CACHE_DIR:-}" ]]; then
  echo "ERROR: TRIVY_CACHE_DIR is not set. Provide a validated Trivy cache directory." >&2
  exit 1
fi

if [[ ! -d "$TRIVY_CACHE_DIR" ]]; then
  echo "ERROR: TRIVY_CACHE_DIR does not exist: $TRIVY_CACHE_DIR" >&2
  exit 1
fi

SBOM="target/agent-memory-bom.json"

# --- Pre-scan: verify database hashes ---
echo "=== Verifying Trivy cache ==="
echo "Cache directory: $TRIVY_CACHE_DIR"
if [[ -f "$TRIVY_CACHE_DIR/db/trivy.db" ]]; then
  sha256sum "$TRIVY_CACHE_DIR/db/trivy.db"
fi
if [[ -f "$TRIVY_CACHE_DIR/java-db/trivy-java.db" ]]; then
  sha256sum "$TRIVY_CACHE_DIR/java-db/trivy-java.db"
fi

# --- Scan 1: SBOM scan ---
echo ""
echo "=== Scan 1: Trivy SBOM vulnerability scan ==="
if [[ ! -f "$SBOM" ]]; then
  echo "ERROR: Aggregate SBOM not found at $SBOM. Build first: ./mvnw verify -DskipTests" >&2
  exit 1
fi

trivy \
  --cache-dir "$TRIVY_CACHE_DIR" \
  sbom \
  --skip-db-update \
  --skip-java-db-update \
  --offline-scan \
  --disable-telemetry \
  --scanners vuln \
  --format json \
  "$SBOM"

# --- Scan 2: Runtime JAR closure (rootfs) ---
echo ""
echo "=== Scan 2: Trivy rootfs scan on runtime JAR closure ==="
JAR_DIR=$(mktemp -d)
trap 'rm -rf "$JAR_DIR"' EXIT

# Collect runtime JARs from both modules
for module in memory-core memory-advisor; do
  jar_dir="target/dependency-jars"
  mkdir -p "$jar_dir"
  # Copy runtime scope dependencies (exclude test)
  ./mvnw -pl "$module" dependency:copy-dependances \
    -DoutputDirectory="$jar_dir" \
    -DincludeScope=runtime \
    -DexcludeTransitive=false \
    -Dmaven.repo.local="${MAVEN_REPO_LOCAL:-}" \
    -q 2>/dev/null || true
  if ls "$jar_dir"/*.jar 1>/dev/null 2>&1; then
    cp "$jar_dir"/*.jar "$JAR_DIR/" 2>/dev/null || true
  fi
done

if ls "$JAR_DIR"/*.jar 1>/dev/null 2>&1; then
  trivy \
    --cache-dir "$TRIVY_CACHE_DIR" \
    rootfs \
    --skip-db-update \
    --skip-java-db-update \
    --offline-scan \
    --disable-telemetry \
    --scanners vuln \
    --format json \
    "$JAR_DIR"
else
  echo "WARNING: No runtime JARs found for rootfs scan."
fi

echo ""
echo "=== Post-scan: verify database hashes unchanged ==="
if [[ -f "$TRIVY_CACHE_DIR/db/trivy.db" ]]; then
  sha256sum "$TRIVY_CACHE_DIR/db/trivy.db"
fi
if [[ -f "$TRIVY_CACHE_DIR/java-db/trivy-java.db" ]]; then
  sha256sum "$TRIVY_CACHE_DIR/java-db/trivy-java.db"
fi
