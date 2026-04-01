package io.github.markpollack.agentmemory;

import java.util.List;
import java.util.Map;

/**
 * Interface for storing and retrieving iteration memories.
 *
 * <p>
 * Implementations may differ in how they handle storage format (flat file vs structured
 * directory), retrieval budgeting, and compaction. The interface is append-only: once
 * written, entries are never deleted (though they may be compacted into summaries).
 */
public interface MemoryStore {

	/**
	 * Append a new memory entry with the given content and metadata.
	 * @param content the learning text to store
	 * @param metadata arbitrary key-value metadata (e.g., iteration number, story ID)
	 */
	void append(String content, Map<String, String> metadata);

	/**
	 * Retrieve memory content within the given token budget.
	 *
	 * <p>
	 * Implementations that do not support budgeting (e.g.,
	 * {@code ProgressFileMemoryStore}) should ignore the budget parameter and return all
	 * content. Implementations with budgeting should return the most relevant content
	 * that fits within the budget, estimated at approximately 4 characters per token.
	 * @param tokenBudget the maximum number of tokens to return (approximate)
	 * @return the retrieved memory content as a single string
	 */
	String retrieve(int tokenBudget);

	/**
	 * Return all memory entries in chronological order.
	 * @return an unmodifiable list of all entries
	 */
	List<MemoryEntry> entries();

}
