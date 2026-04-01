package io.github.markpollack.agentmemory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;

/**
 * LLM-powered memory compaction.
 *
 * <p>
 * When the estimated token count of uncompacted entries reaches a configured fraction of
 * the memory token budget, the compactor summarizes them into a single compacted entry
 * using a ChatClient call. The compacted summary is stored in the {@code patterns/}
 * directory, and original entries are marked as compacted in the index.
 *
 * <p>
 * The compaction prompt produces two sections:
 * <ul>
 * <li><strong>Status</strong> — what has been accomplished</li>
 * <li><strong>Learnings</strong> — patterns, gotchas, and insights discovered</li>
 * </ul>
 */
public class MemoryCompactor {

	private static final String COMPACTION_PROMPT = """
			Summarize the following iteration logs into two sections:

			## Status
			What has been accomplished so far. List completed work items.

			## Learnings
			Patterns, gotchas, and insights discovered. Focus on knowledge that would be
			useful for future iterations.

			Be concise. Preserve critical details but eliminate redundancy.

			---

			%s
			""";

	private final int memoryTokenBudget;

	private final double compactionRatio;

	private final TokenEstimator tokenEstimator = new TokenEstimator();

	public MemoryCompactor(int memoryTokenBudget, double compactionRatio) {
		this.memoryTokenBudget = memoryTokenBudget;
		this.compactionRatio = compactionRatio;
	}

	/**
	 * Run compaction on the given store if the estimated token count of uncompacted
	 * entries reaches the compaction ratio of the memory token budget.
	 * @param store the file system memory store to compact
	 * @param chatClient the ChatClient used for LLM summarization
	 * @return {@code true} if compaction was performed
	 */
	public boolean compact(FileSystemMemoryStore store, ChatClient chatClient) {
		MemoryIndex index = store.getIndex();
		List<MemoryEntry> uncompacted = index.getUncompacted();

		int estimatedTokens = this.tokenEstimator.estimateAll(uncompacted);
		if (estimatedTokens < (int) (this.memoryTokenBudget * this.compactionRatio)) {
			return false;
		}

		// Build content to summarize
		String combinedContent = uncompacted.stream()
			.map(MemoryEntry::content)
			.collect(Collectors.joining("\n\n---\n\n"));

		// Call LLM for summarization
		String prompt = String.format(COMPACTION_PROMPT, combinedContent);
		String summary = chatClient.prompt().user(prompt).call().content();

		// Write compacted summary to patterns directory
		String compactId = "compact-" + UUID.randomUUID().toString().substring(0, 8);
		Path compactFile = store.getPatternsDir().resolve(compactId + ".md");
		try {
			Files.writeString(compactFile, summary);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to write compacted summary: " + compactFile, ex);
		}

		// Add compacted entry to index
		MemoryEntry compactedEntry = new MemoryEntry(compactId, Instant.now(), "compacted", summary, Map.of(), true);
		index.addEntry(compactedEntry);

		// Mark originals as compacted
		for (MemoryEntry entry : uncompacted) {
			index.markCompacted(entry.id());
		}

		return true;
	}

}
