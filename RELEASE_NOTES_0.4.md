# Agent Memory 0.4.0

First Business Source License 1.1 release. Versions 0.3.0 and earlier remain Apache License 2.0.

## Changes

- Relicense current development under BSL 1.1; retain the Apache 2.0 text as `LICENSE-APACHE.txt`.
- Stage LICENSE and LICENSE-APACHE.txt into published binary, source, and Javadoc archives.
- Raise governed Jackson 2 to 2.21.6 and Jackson 3 to 3.1.6 (portfolio safety floors).
- Declare Jackson 3 core and databind directly on `memory-core` so standalone consumers, without
  an AgentWorks BOM or their own Jackson management, resolve 3.1.6 by Maven nearest-wins. The
  parent Jackson BOMs still align the reactor; they are not sufficient for ordinary downstream
  consumers.
- Publish one root aggregate CycloneDX 1.6 JSON SBOM on the parent artifact (`classifier=cyclonedx`).
- Pin owned reusable workflows to build-tools `35297f1ade5f47c2925d6dab42a7e2d43bd734d0`.
- Remove hosted OWASP/NVD scanning from GitHub Actions. Vulnerability analysis is a local offline Trivy procedure.
- Add a repository-owned standalone-consumer Jackson-resolution gate and invoke it from CI after the clean build.
- Drop unused Spring milestone/snapshot dependency-resolution repositories now that Spring AI 2.0.0 GA resolves from Maven Central.

Tier-1 compaction behavior (`FileSystemMemoryStore`, `MemoryCompactor`, `CompactionMemoryAdvisor`) is unchanged. This is not a Jackson 3 source migration.
