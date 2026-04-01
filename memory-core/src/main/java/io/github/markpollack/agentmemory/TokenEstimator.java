package io.github.markpollack.agentmemory;

import java.util.List;

/**
 * Estimates token count using the chars/4 heuristic.
 *
 * <p>
 * This is a rough approximation sufficient for memory budgeting. Actual token counts vary
 * by model and tokenizer. For Claude, the ratio is typically between 3.5-4.5 characters
 * per token for English text.
 */
public class TokenEstimator {

	private static final int CHARS_PER_TOKEN = 4;

	/**
	 * Estimate token count for a string.
	 * @param text the text to estimate (may be {@code null})
	 * @return estimated token count, or 0 for null/empty text
	 */
	public int estimate(String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		return text.length() / CHARS_PER_TOKEN;
	}

	/**
	 * Estimate total token count across all entries.
	 * @param entries the memory entries to estimate
	 * @return sum of individual estimates
	 */
	public int estimateAll(List<MemoryEntry> entries) {
		int total = 0;
		for (MemoryEntry entry : entries) {
			total += estimate(entry.content());
		}
		return total;
	}

}
