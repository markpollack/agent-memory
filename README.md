# Agent Memory

Token-budgeted conversational memory for [Spring AI](https://docs.spring.io/spring-ai/reference/).
The library stores accumulated learnings on the filesystem, injects a budgeted subset into each
`ChatClient` request, and optionally summarizes older entries with a cheaper model when the
uncompacted set crosses a configurable threshold.

See the [Agent Memory documentation](https://lab.pollack.ai/projects/agent-memory) for the
roadmap, module notes, and the originating research.

## Status

| Item | Value |
|------|-------|
| Latest released artifacts | **0.3.0** (Apache License 2.0) |
| Intended next line | **0.4.0** (Business Source License 1.1; not yet published) |
| Java | 17+ |
| Spring AI | 2.0.0 GA |
| Modules | `memory-core`, `memory-advisor` |

Until 0.4.0 is on Maven Central, depend on the current public stable version:

```xml
<dependency>
    <groupId>io.github.markpollack</groupId>
    <artifactId>memory-advisor</artifactId>
    <version>0.3.0</version>
</dependency>
```

`memory-core` is the store, index, and compaction engine. `memory-advisor` wraps it as a Spring AI
`BaseAdvisor`.

```java
var memoryStore = new FileSystemMemoryStore(Path.of(".memory"));

var advisor = CompactionMemoryAdvisor.builder(memoryStore)
    .compactionChatClient(ChatClient.create(haikuModel))
    .memoryTokenBudget(8192)
    .compactionRatio(0.75)
    .build();

ChatClient agent = ChatClient.builder(chatModel)
    .defaultAdvisors(advisor)
    .build();
```

On each request the advisor retrieves stored learnings within `memoryTokenBudget` and appends them
to the system message. After each response it appends the assistant text. When uncompacted entries
exceed `budget × compactionRatio`, `MemoryCompactor` asks the configured chat client for a summary
and replaces those entries with the compacted form.

| Parameter | Default | Role |
|-----------|---------|------|
| `memoryTokenBudget` | 8,192 | Maximum estimated tokens of memory injected per request |
| `compactionRatio` | 0.75 | Fraction of the budget that triggers summarization |

Token counts use a characters/4 estimate, not a model tokenizer.

## Build

```bash
./mvnw clean verify
```

Vulnerability analysis is a local, offline procedure. It is not part of hosted CI. Supply a
validated Trivy cache and do not point the script at a live database download:

```bash
TRIVY_CACHE_DIR=/path/to/validated/trivy-cache \
  ./scripts/security-scan.sh sbom target/agent-memory-bom.json
```

## Origin and measured claims

Extracted from the [wiggum-memory](https://github.com/markpollack/wiggum-memory) research project.
This repository does not re-run those experiments, and Agent Memory 0.3.0 / 0.4.0 has no live-model
benchmark suite.

The 12-story e-commerce PRD comparison (Anthropic Haiku 4.5, Enhanced Ralph at a 4,096-token
budget and 0.5 ratio vs unbounded Pure Ralph) is a single writeup in that research checkout:
11/12 vs 9/12 self-reported stories, 40,152 vs 56,876 API tokens, about $0.24 vs $0.34 at a
flat $6/MTok estimate. A 2,048-token budget on the same PRD dropped Enhanced to 7/12. Pure
was 11/12 on the prior run of the same harness. Those figures describe that date, model, and
self-pass rubric — not a product SLA, and not a re-run of this library.

The library defaults are 8,192 tokens and ratio 0.75. That configuration was recommended for
chat, not measured on the 12-story PRD.

The “21× / 18.3M → 854K tokens” code-coverage numbers come from a **Loopy** message-compaction
experiment documented in the same research README. That table compares Loopy+Haiku with no
compaction against Loopy+Sonnet at threshold 0.3 — different models, different outcomes
(failed vs passed), and a different class (`AgentLoopAdvisor`). It is not a measurement of
`CompactionMemoryAdvisor`.

## License

Current development and the intended 0.4.0 line are licensed under the
[Business Source License 1.1](LICENSE). Versions 0.3.0 and earlier remain available under the
historical [Apache License 2.0](LICENSE-APACHE.txt); those tags and Maven Central artifacts are
unchanged.
