#!/usr/bin/env bash
# Verify standalone consumers of the published modules resolve the accepted
# Jackson floors without importing agentworks-bom or managing Jackson.
#
# The script derives the current root Maven project.version, installs the
# current checkout into an isolated Maven repository, builds throwaway
# consumers for memory-core and memory-advisor, inspects the runtime graph,
# and fails if any affected Jackson artifact is below the floor.
# Generated consumer files are not written into the repository.
#
# Usage:
#   ./scripts/check-consumer-resolution.sh
#   MAVEN_REPO_LOCAL=/path/to/isolated-m2 ./scripts/check-consumer-resolution.sh
#   CONSUMER_VERSION=0.5.0 ./scripts/check-consumer-resolution.sh
#
# CONSUMER_VERSION is an optional fixture override. The normal invocation
# derives project.version with Maven and requires no version argument.
set -euo pipefail

usage() {
  sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

JACKSON2_FLOOR="${JACKSON2_FLOOR:-2.22.2}"
JACKSON3_FLOOR="${JACKSON3_FLOOR:-3.2.2}"

WORK="$(mktemp -d "${TMPDIR:-/tmp}/agent-memory-consumer-resolution.XXXXXX")"
cleanup() {
  rm -rf "${WORK}"
}
trap cleanup EXIT

if [[ -n "${MAVEN_REPO_LOCAL:-}" ]]; then
  REPO="${MAVEN_REPO_LOCAL}"
  mkdir -p "${REPO}"
else
  REPO="${WORK}/m2"
  mkdir -p "${REPO}"
fi

validate_maven_version() {
  local value="$1"
  local source="$2"
  if [[ -z "${value}" ]]; then
    echo "ERROR: ${source} is empty." >&2
    return 1
  fi
  if [[ "${value}" == *$'\n'* || "${value}" == *$'\r'* || "${value}" == *$'\t'* || "${value}" == *' '* ]]; then
    echo "ERROR: ${source} is unresolved or malformed (whitespace): '${value}'" >&2
    return 1
  fi
  if [[ "${value}" == *'$'* || "${value}" == *'{'* || "${value}" == *'}'* ]]; then
    echo "ERROR: ${source} looks like an unresolved Maven expression: '${value}'" >&2
    return 1
  fi
  if [[ ! "${value}" =~ ^[0-9][A-Za-z0-9._-]*$ ]]; then
    echo "ERROR: ${source} is not a usable Maven version: '${value}'" >&2
    return 1
  fi
}

if [[ -n "${CONSUMER_VERSION:-}" ]]; then
  PROJECT_VERSION="${CONSUMER_VERSION}"
  validate_maven_version "${PROJECT_VERSION}" "CONSUMER_VERSION"
  echo "Using CONSUMER_VERSION override: ${PROJECT_VERSION}"
else
  echo "Deriving root project.version with Maven help:evaluate"
  PROJECT_VERSION="$(
    ./mvnw -B -N -q -DforceStdout help:evaluate -Dexpression=project.version \
      "-Dmaven.repo.local=${REPO}"
  )"
  PROJECT_VERSION="${PROJECT_VERSION//$'\r'/}"
  validate_maven_version "${PROJECT_VERSION}" "Maven project.version"
  echo "Derived project.version: ${PROJECT_VERSION}"
fi

echo "Installing current checkout (${PROJECT_VERSION}) into ${REPO}"
./mvnw -B -DskipTests install "-Dmaven.repo.local=${REPO}"

check_module() {
  local module="$1"
  local consumer="${WORK}/${module}-consumer"
  mkdir -p "${consumer}"
  cat > "${consumer}/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example.diligence</groupId>
    <artifactId>${module}-standalone-consumer</artifactId>
    <version>1.0.0</version>
    <dependencies>
        <dependency>
            <groupId>io.github.markpollack</groupId>
            <artifactId>${module}</artifactId>
            <version>${PROJECT_VERSION}</version>
        </dependency>
    </dependencies>
</project>
EOF

  local tree="${WORK}/${module}-runtime-tree.txt"
  local list="${WORK}/${module}-runtime-list.txt"
  ./mvnw -B -f "${consumer}/pom.xml" \
    "-Dmaven.repo.local=${REPO}" \
    dependency:tree \
    "-DoutputFile=${tree}"
  ./mvnw -B -f "${consumer}/pom.xml" \
    "-Dmaven.repo.local=${REPO}" \
    dependency:list \
    -DincludeScope=runtime \
    -Dsort=true \
    "-DoutputFile=${list}"

  echo
  echo "=== ${module} standalone consumer runtime Jackson coordinates ==="
  grep -E 'jackson-(core|databind|annotations|datatype-jsr310)' "${list}" || true
  echo

  python3 - "${module}" "${list}" "${JACKSON2_FLOOR}" "${JACKSON3_FLOOR}" <<'PY'
import re
import sys

module, list_path, jackson2_floor, jackson3_floor = sys.argv[1:]

def parse_version(value):
    parts = []
    for token in re.split(r"[.-]", value):
        if token.isdigit():
            parts.append(int(token))
        else:
            break
    return tuple(parts)

def minor_line(value):
    parts = parse_version(value)
    if len(parts) < 2:
        raise ValueError(f"version has no minor component: {value}")
    return (parts[0], parts[1])


def cmp(a, b):
    n = max(len(a), len(b))
    a = a + (0,) * (n - len(a))
    b = b + (0,) * (n - len(b))
    return (a > b) - (a < b)

coord_re = re.compile(
    r"(?P<group>[\w.-]+):(?P<artifact>[\w.-]+):(?P<type>[\w.-]+):(?P<version>[^:\s]+):(?P<scope>\w+)"
)

resolved = {}
for line in open(list_path, encoding="utf-8"):
    match = coord_re.search(line)
    if not match:
        continue
    group = match.group("group")
    artifact = match.group("artifact")
    version = match.group("version")
    if group.startswith("com.fasterxml.jackson") or group.startswith("tools.jackson"):
        resolved[(group, artifact)] = version

errors = []
required = [
    ("com.fasterxml.jackson.core", "jackson-core", jackson2_floor, "jackson2"),
    ("com.fasterxml.jackson.core", "jackson-databind", jackson2_floor, "jackson2"),
    ("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", jackson2_floor, "jackson2"),
    ("tools.jackson.core", "jackson-core", jackson3_floor, "jackson3"),
    ("tools.jackson.core", "jackson-databind", jackson3_floor, "jackson3"),
]

# `jackson-annotations` is versioned independently of the rest of Jackson 2 and has carried no
# patch component since 2.20, so jackson-bom 2.22.2 manages jackson-annotations 2.22. The rule is a
# line rule, not a floor: annotations must belong to the same major.minor line as the Jackson 2 it
# is compiled against. Derive that line from the closure under test rather than hardcoding it, so a
# dependency refresh cannot leave this gate behind.
jackson2_lines = {
    minor_line(version)
    for (group, artifact), version in resolved.items()
    if group.startswith("com.fasterxml.jackson") and artifact != "jackson-annotations"
}
annotations = resolved.get(("com.fasterxml.jackson.core", "jackson-annotations"))
if annotations is None:
    errors.append("missing com.fasterxml.jackson.core:jackson-annotations")
elif len(jackson2_lines) > 1:
    lines = ", ".join(".".join(str(part) for part in line) for line in sorted(jackson2_lines))
    errors.append(
        f"com.fasterxml.jackson.core:jackson-annotations:{annotations} cannot be anchored: "
        f"Jackson 2 line is split across this closure ({lines})"
    )
elif jackson2_lines:
    expected = next(iter(jackson2_lines))
    if minor_line(annotations) != expected:
        shown = ".".join(str(part) for part in expected)
        errors.append(
            f"com.fasterxml.jackson.core:jackson-annotations:{annotations} is not on the "
            f"{shown} line used by the rest of Jackson 2 in this closure"
        )
elif cmp(minor_line(annotations), minor_line(jackson2_floor)) < 0:
    # Nothing to be consistent with, so only the floor applies. Compare lines rather than full
    # versions: annotations 2.21 legitimately satisfies a 2.21.6 floor.
    shown = ".".join(str(part) for part in minor_line(jackson2_floor))
    errors.append(
        f"com.fasterxml.jackson.core:jackson-annotations:{annotations} is below the {shown} line"
    )

for group, artifact, floor, kind in required:
    version = resolved.get((group, artifact))
    if version is None:
        errors.append(f"missing {group}:{artifact} (expected {floor})")
        continue
    if kind == "annotations":
        if not (version == floor or version.startswith(floor + ".")):
            errors.append(
                f"{group}:{artifact}:{version} is not on the {floor} line"
            )
        continue
    if cmp(parse_version(version), parse_version(floor)) < 0:
        errors.append(f"{group}:{artifact}:{version} is below floor {floor}")

for (group, artifact), version in sorted(resolved.items()):
    if (group, artifact) in {(g, a) for g, a, _, _ in required}:
        continue
    if group.startswith("tools.jackson"):
        if cmp(parse_version(version), parse_version(jackson3_floor)) < 0:
            errors.append(
                f"{group}:{artifact}:{version} is below Jackson 3 floor {jackson3_floor}"
            )
    elif group.startswith("com.fasterxml.jackson") and artifact != "jackson-annotations":
        if cmp(parse_version(version), parse_version(jackson2_floor)) < 0:
            errors.append(
                f"{group}:{artifact}:{version} is below Jackson 2 floor {jackson2_floor}"
            )

if errors:
    print(f"FAIL {module} standalone consumer:", file=sys.stderr)
    for error in errors:
        print(f"  {error}", file=sys.stderr)
    sys.exit(1)

annotations_shown = ".".join(str(part) for part in minor_line(annotations)) if annotations else "absent"
print(f"OK {module}: Jackson 2 {jackson2_floor} (annotations {annotations_shown} line, derived), Jackson 3 {jackson3_floor}")
PY
}

failures=0
for module in memory-core memory-advisor; do
  if ! check_module "${module}"; then
    failures=$((failures + 1))
  fi
done

if [[ "${failures}" -ne 0 ]]; then
  echo "Standalone consumer Jackson resolution failed for ${failures} module(s)." >&2
  exit 1
fi

echo "Standalone consumer Jackson resolution passed for memory-core and memory-advisor at ${PROJECT_VERSION}."
