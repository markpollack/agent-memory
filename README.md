# Agent Memory

Token-budgeted conversational memory for [Spring AI](https://docs.spring.io/spring-ai/reference/).
The library stores accumulated learnings on the filesystem, injects a budgeted subset into each
`ChatClient` request, and optionally summarizes older entries with a cheaper model when the
uncompacted set crosses a configurable threshold.

See the [Agent Memory documentation](https://lab.pollack.ai/projects/agent-memory) for the
roadmap, module notes, and the originating research.

Latest public artifacts are **0.3.0** (Apache License 2.0). The intended next line is **0.4.0**
(Business Source License 1.1; not yet published). Requires Java 17+ and Spring AI 2.0.0.
Published modules are `memory-core` and `memory-advisor`.

```xml
<dependency>
    <groupId>io.github.markpollack</groupId>
    <artifactId>memory-advisor</artifactId>
    <version>0.3.0</version>
</dependency>
```

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

## Build

```bash
./mvnw clean verify
```

Standalone consumer Jackson resolution (no AgentWorks BOM):

```bash
./scripts/check-consumer-resolution.sh
```

This repository has no live-model benchmark suite. Research measurements belong in the
[canonical documentation](https://lab.pollack.ai/projects/agent-memory).

## License

Current development and the intended 0.4.0 line are licensed under the
[Business Source License 1.1](LICENSE). Versions 0.3.0 and earlier remain available under the
historical [Apache License 2.0](LICENSE-APACHE.txt); those tags and Maven Central artifacts are
unchanged.
