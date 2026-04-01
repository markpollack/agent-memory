package io.github.markpollack.agentmemory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Structured filesystem memory implementation with token-budgeted retrieval.
 *
 * <p>
 * Each append creates a file under {@code iterations/} and updates the
 * {@link MemoryIndex}. Retrieval prioritizes compacted summaries (from {@code patterns/})
 * first, then recent uncompacted entries, fitting within the given token budget.
 *
 * <p>
 * Directory structure:
 *
 * <pre>
 * memory/
 * ├── _index.json             ← MemoryIndex
 * ├── iterations/             ← raw per-entry outputs
 * │   ├── iter-001.md
 * │   └── iter-002.md
 * └── patterns/               ← compacted summaries
 *     └── compact-001.md
 * </pre>
 */
public class FileSystemMemoryStore implements MemoryStore {

	private final Path memoryDir;

	private final Path iterationsDir;

	private final Path patternsDir;

	private final MemoryIndex index;

	private final TokenEstimator tokenEstimator;

	public FileSystemMemoryStore(Path memoryDir) {
		this.memoryDir = memoryDir;
		this.iterationsDir = memoryDir.resolve("iterations");
		this.patternsDir = memoryDir.resolve("patterns");
		this.tokenEstimator = new TokenEstimator();
		ensureDirectories();
		this.index = new MemoryIndex(memoryDir);
	}

	@Override
	public void append(String content, Map<String, String> metadata) {
		String id = "iter-" + UUID.randomUUID().toString().substring(0, 8);
		Path file = this.iterationsDir.resolve(id + ".md");
		try {
			Files.writeString(file, content);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to write memory entry: " + file, ex);
		}

		String topic = metadata.get("topic");
		MemoryEntry entry = new MemoryEntry(id, Instant.now(), topic, content, metadata, false);
		this.index.addEntry(entry);
	}

	/**
	 * Retrieve memory content within the given token budget.
	 *
	 * <p>
	 * Priority order:
	 * <ol>
	 * <li>Compacted summaries (newest first) — these are distilled knowledge</li>
	 * <li>Recent uncompacted entries (newest first) — raw but recent</li>
	 * </ol>
	 * Entries are added until the budget is exhausted.
	 */
	@Override
	public String retrieve(int tokenBudget) {
		List<MemoryEntry> compacted = new ArrayList<>();
		List<MemoryEntry> uncompacted = new ArrayList<>();

		for (MemoryEntry entry : this.index.getEntries()) {
			if (entry.compacted()) {
				compacted.add(entry);
			}
			else {
				uncompacted.add(entry);
			}
		}

		// Sort newest first
		Comparator<MemoryEntry> newestFirst = Comparator.comparing(MemoryEntry::timestamp).reversed();
		compacted.sort(newestFirst);
		uncompacted.sort(newestFirst);

		StringBuilder result = new StringBuilder();
		int tokensUsed = 0;

		// Compacted summaries first
		for (MemoryEntry entry : compacted) {
			int entryTokens = this.tokenEstimator.estimate(entry.content());
			if (tokensUsed + entryTokens > tokenBudget) {
				break;
			}
			if (result.length() > 0) {
				result.append("\n\n---\n\n");
			}
			result.append(entry.content());
			tokensUsed += entryTokens;
		}

		// Then recent uncompacted entries
		for (MemoryEntry entry : uncompacted) {
			int entryTokens = this.tokenEstimator.estimate(entry.content());
			if (tokensUsed + entryTokens > tokenBudget) {
				break;
			}
			if (result.length() > 0) {
				result.append("\n\n---\n\n");
			}
			result.append(entry.content());
			tokensUsed += entryTokens;
		}

		return result.toString();
	}

	@Override
	public List<MemoryEntry> entries() {
		return this.index.getEntries();
	}

	/**
	 * Return the index for compaction and query operations.
	 */
	public MemoryIndex getIndex() {
		return this.index;
	}

	/**
	 * Return the path to the patterns directory (for compacted summaries).
	 */
	public Path getPatternsDir() {
		return this.patternsDir;
	}

	private void ensureDirectories() {
		try {
			Files.createDirectories(this.iterationsDir);
			Files.createDirectories(this.patternsDir);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to create memory directories: " + this.memoryDir, ex);
		}
	}

}
