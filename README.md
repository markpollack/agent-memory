# Agent Memory

Progressive memory management for [Spring AI](https://docs.spring.io/spring-ai/reference/). Gives AI agents the ability to manage conversational context intelligently — starting with proven context compaction, with a roadmap toward autonomous memory control.

For full documentation, architecture narratives, and examples, visit the canonical documentation at [lab.pollack.ai/projects/agent-memory](https://lab.pollack.ai/projects/agent-memory).

## The Problem

Most AI agent loops accumulate conversation history on every turn. Tool results, file contents, error messages — all re-sent to the model with each request. On short tasks this is fine. On longer ones the context fills with stale information, costs climb, and the model loses focus in noise.

**Without memory management**: 18M input tokens for a single code-coverage task.
**With compaction**: 854K tokens. **21x reduction, same quality.**

## Quick Start

Add the dependency:

```xml
<dependency>
    <groupId>io.github.markpollack</groupId>
    <artifactId>memory-advisor</artifactId>
    <version>0.3.0</version>
</dependency>
```

Wire it into any Spring AI `ChatClient`:

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

On each request, the advisor retrieves accumulated learnings (within the token budget) and injects them into the system message. After each response, it appends the assistant's output to the store. When uncompacted entries exceed `budget * ratio`, compaction summarizes them via a cheap model and replaces them with dense summaries.

## How It Works

**Compaction** (Tier 1 — shipping now): When accumulated context exceeds a token budget, older entries are summarized by a cheap model (e.g., Haiku) and replaced with a compact summary. The agent continues with dense, relevant context instead of an ever-growing prompt.

Two parameters control it:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `memoryTokenBudget` | 8,192 | Max tokens of memory included in each prompt |
| `compactionRatio` | 0.75 | Fraction of budget that triggers compaction |

## Benchmark Results

Real LLM benchmarks from the [wiggum-memory](https://github.com/markpollack/wiggum-memory) research project, run against Anthropic Haiku 4.5 on a 12-story e-commerce PRD:

| Metric | Without Compaction | With Compaction |
|--------|-------------------|-----------------|
| Stories passed | 9/12 | **11/12** |
| Total tokens | 56,876 | **40,152** |
| Total cost | $0.34 | **$0.24** |

Token growth without compaction is linear and unbounded (~800 tokens/story). With compaction it plateaus around 4,600 tokens after the first compaction cycle.

### Budget Sensitivity

| Budget | Result | Notes |
|--------|--------|-------|
| 2,048 | 7/12 stories | Too aggressive — destroys critical details |
| **4,096** | **11/12 stories** | Sweet spot for structured tasks |
| 8,192 | Good | Best for unstructured conversations |

### Production Validation

In a code-coverage experiment with [Loopy](https://github.com/markpollack/loopy) (Spring AI agent CLI):

| Configuration | Compaction | Input Tokens | Cost | Outcome |
|---------------|-----------|-------------|------|---------|
| No compaction | none | 18,336,594 | $2.55 | Failed |
| Threshold 0.5 | late | — | $5.06 | Cost cap |
| **Threshold 0.3** | **early** | **854,353** | **$2.72** | **Passed** |

## Modules

| Module | Description |
|--------|-------------|
| `memory-core` | `MemoryStore` interface, `FileSystemMemoryStore`, `MemoryCompactor`, `TokenEstimator` |
| `memory-advisor` | `CompactionMemoryAdvisor` — Spring AI `BaseAdvisor` for `ChatClient` integration |

## Recommended Settings

| Use Case | Budget | Ratio | Rationale |
|----------|--------|-------|-----------|
| Long-running chatbot | 8,192 | 0.75 | Unstructured conversations need generous budget |
| Structured task execution | 4,096 | 0.5 | Tighter budget is safe with structured outputs |
| Short conversations (< 10 turns) | skip | — | Compaction overhead not worth it |

## Roadmap

| Tier | Name | Status | Description |
|------|------|--------|-------------|
| 1 | **Compaction** | Shipping | Token-budgeted retrieval + LLM summarization |
| 2 | Structured | Planned | Categorized memory with selective retrieval and per-category retention policies |
| 3 | Reflective | Planned | Importance scoring + periodic reflection synthesis (Generative Agents pattern) |
| 4 | Autonomous | Planned | Agent-controlled memory via tools — virtual context management (MemGPT pattern) |

## Part of AgentWorks

Agent Memory works standalone with any Spring AI `ChatClient`, but it's designed to complement:

- **agent-workflow** — Agentic loop patterns with judge-based evaluation
- **agent-journal** — Record what the agent learned
- **agent-judge** — Evaluate memory quality

## Build

Requires Java 17+. Uses Spring AI 2.0.0-M3.

```bash
./mvnw compile       # Compile
./mvnw test          # Unit tests
./mvnw verify        # Full verification including integration tests
```
## License

This project is licensed under the [Business Source License 1.1](LICENSE) starting with version 0.4.0.

Earlier releases (v0.3.0 and prior) remain distributed under the [Apache License 2.0](LICENSE-APACHE.txt).
