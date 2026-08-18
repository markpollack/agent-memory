#!/usr/bin/env bash
# Repository-local offline Trivy analysis.
#
# Hosted CI must not run this script and must not download NVD/OWASP data.
# The caller supplies a validated Trivy cache; this script never updates it.
#
# Required:
#   TRIVY_CACHE_DIR   Absolute path to a validated Trivy cache (contains db/ and java-db/)
#
# Usage:
#   TRIVY_CACHE_DIR=/path/to/cache ./scripts/security-scan.sh sbom <cyclonedx.json> [output.json]
#   TRIVY_CACHE_DIR=/path/to/cache ./scripts/security-scan.sh rootfs <directory> [output.json]
#   TRIVY_CACHE_DIR=/path/to/cache ./scripts/security-scan.sh secrets [path] [output.json]
#
# Optional:
#   TRIVY_FORMAT   json (default) or table
set -euo pipefail

usage() {
  sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ -z "${TRIVY_CACHE_DIR:-}" ]]; then
  echo "ERROR: TRIVY_CACHE_DIR is required and must point at a validated Trivy cache." >&2
  usage >&2
  exit 2
fi

if [[ ! -d "${TRIVY_CACHE_DIR}" ]]; then
  echo "ERROR: TRIVY_CACHE_DIR does not exist: ${TRIVY_CACHE_DIR}" >&2
  exit 2
fi

if [[ ! -f "${TRIVY_CACHE_DIR}/db/trivy.db" || ! -f "${TRIVY_CACHE_DIR}/java-db/trivy-java.db" ]]; then
  echo "ERROR: TRIVY_CACHE_DIR is not a Trivy cache (missing db/trivy.db or java-db/trivy-java.db)." >&2
  exit 2
fi

MODE="${1:-}"
shift || true

FORMAT="${TRIVY_FORMAT:-json}"
COMMON=(
  --cache-dir "${TRIVY_CACHE_DIR}"
  --skip-db-update
  --skip-java-db-update
  --offline-scan
  --disable-telemetry
)

case "${MODE}" in
  sbom)
    TARGET="${1:-}"
    OUTPUT="${2:-}"
    if [[ -z "${TARGET}" || ! -f "${TARGET}" ]]; then
      echo "ERROR: sbom mode requires an existing CycloneDX JSON file." >&2
      exit 2
    fi
    ARGS=(sbom --scanners vuln --format "${FORMAT}")
    if [[ -n "${OUTPUT}" ]]; then
      ARGS+=(--output "${OUTPUT}")
    fi
    ARGS+=("${TARGET}")
    ;;
  rootfs)
    TARGET="${1:-}"
    OUTPUT="${2:-}"
    if [[ -z "${TARGET}" || ! -d "${TARGET}" ]]; then
      echo "ERROR: rootfs mode requires an existing directory (runtime JAR closure)." >&2
      exit 2
    fi
    ARGS=(rootfs --scanners vuln --format "${FORMAT}")
    if [[ -n "${OUTPUT}" ]]; then
      ARGS+=(--output "${OUTPUT}")
    fi
    ARGS+=("${TARGET}")
    ;;
  secrets)
    TARGET="${1:-.}"
    OUTPUT="${2:-}"
    if [[ ! -e "${TARGET}" ]]; then
      echo "ERROR: secrets mode requires an existing path." >&2
      exit 2
    fi
    ARGS=(fs --scanners secret --format "${FORMAT}")
    if [[ -n "${OUTPUT}" ]]; then
      ARGS+=(--output "${OUTPUT}")
    fi
    ARGS+=("${TARGET}")
    ;;
  *)
    echo "ERROR: unknown mode '${MODE}'. Expected sbom, rootfs, or secrets." >&2
    usage >&2
    exit 2
    ;;
esac

exec trivy "${COMMON[@]}" "${ARGS[@]}"
