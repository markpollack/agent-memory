# Agent Memory 0.5.0

Maintenance release on the existing Java 17 and Spring AI 2.x compatibility line.

## Changes

- Update Spring AI to 2.0.1 and align the standalone-consumer Jackson floors to Jackson 2.22.2
  and Jackson 3.2.2.
- Refresh compatible stable runtime, test, build, SBOM, and Central publishing dependencies.
- Retain the repository-owned standalone-consumer resolution gate and aggregate CycloneDX SBOM.
- Preserve the 0.4.0 filesystem-store operating boundary: local, plaintext, single-writer storage
  with external coordination required for concurrent writers.

This release does not perform a Jackson 3 source migration and does not change the library's BSL
1.1 licensing terms.
