# Agent Memory

Token-budgeted conversational memory for [Spring AI](https://docs.spring.io/spring-ai/reference/).
The library stores accumulated learnings on the filesystem, injects a budgeted subset into each
`ChatClient` request, and optionally summarizes older entries with a cheaper model when the
uncompacted set crosses a configurable threshold.

See the [Agent Memory documentation](https://lab.pollack.ai/projects/agent-memory) for the
roadmap, module notes, and the originating research.

Current artifacts are **0.4.0** (`memory-core` and `memory-advisor`). Requires Java 17+ and
Spring AI 2.0.0. This is a pre-1.0 library.

```xml
<dependency>
    <groupId>io.github.markpollack</groupId>
    <artifactId>memory-advisor</artifactId>
    <version>0.4.0</version>
</dependency>
```

## Build

```bash
./mvnw clean verify
```

Standalone consumer Jackson resolution (no AgentWorks BOM):

```bash
./scripts/check-consumer-resolution.sh
```

## License

Licensed under the [Business Source License 1.1](LICENSE). Versions 0.3.0 and earlier remain
available under the historical [Apache License 2.0](LICENSE-APACHE.txt).
