# Agent Memory 0.4.0

First Business Source License 1.1 release. Versions 0.3.0 and earlier remain Apache License 2.0.

## Changes

- Relicense current development under BSL 1.1; retain the Apache 2.0 text as `LICENSE-APACHE.txt`.
- Raise governed Jackson 2 to 2.21.6 and Jackson 3 to 3.1.6 (portfolio safety floors).
- Publish one root aggregate CycloneDX 1.6 JSON SBOM on the parent artifact (`classifier=cyclonedx`).
- Pin owned reusable workflows to build-tools `35297f1ade5f47c2925d6dab42a7e2d43bd734d0`.
- Remove hosted OWASP/NVD scanning from GitHub Actions. Vulnerability analysis is a local offline Trivy procedure.
- Drop unused Spring milestone/snapshot dependency-resolution repositories now that Spring AI 2.0.0 GA resolves from Maven Central.
- Qualify research and Loopy token-reduction claims in the README; depend on released 0.3.0 until this version is published.

Tier-1 compaction behavior (`FileSystemMemoryStore`, `MemoryCompactor`, `CompactionMemoryAdvisor`) is unchanged.
