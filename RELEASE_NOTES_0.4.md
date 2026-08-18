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
- Pin owned reusable workflows to build-tools `35297f1ade5f47c2925d6dab42a7e2d43bd734d0`, and
  pin the external actions referenced directly by `ci.yml` to full-length commit SHAs
  (`actions/checkout` v7.0.1, `actions/setup-java` v5.7.0). The pinned build-tools workflows
  still resolve some actions internally by moving reference, so the complete executed CI path
  is not yet immutable; that residue is owned by build-tools.
- Remove hosted OWASP/NVD scanning from GitHub Actions. Vulnerability analysis is a local offline Trivy procedure.
- Add a repository-owned standalone-consumer Jackson-resolution gate and invoke it from CI after the clean build.
- Drop unused Spring milestone/snapshot dependency-resolution repositories now that Spring AI 2.0.0 GA resolves from Maven Central.

## Operating boundary

The 0.4.0 filesystem store is local, plaintext, and single-writer.

- Memory is written to the local filesystem in clear text. It is not encrypted and is not shared
  storage.
- There is no locking, no atomic index replacement, and no crash recovery. A second concurrent
  writer, or a crash during an index write, can corrupt or truncate `_index.json`, which is the
  only source of truth for the index.
- Stored memory is injected verbatim into the model prompt, so only trusted content should be
  written to it.
- Multi-process or concurrent-writer use requires external coordination.

Locking, atomic index replacement, crash recovery, and concurrency tests are backlog items. They
are not shipped in 0.4.0 and are disclosed here rather than claimed as completed functionality.

Tier-1 compaction behavior (`FileSystemMemoryStore`, `MemoryCompactor`, `CompactionMemoryAdvisor`) is unchanged. This is not a Jackson 3 source migration.
