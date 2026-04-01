package io.github.markpollack.agentmemory;

import java.time.Instant;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * A single memory entry representing a learning or observation from an iteration.
 *
 * @param id unique entry identifier (e.g., "iter-001")
 * @param timestamp when the entry was created
 * @param topic topic category, or {@code null} for uncategorized
 * @param content the learning text
 * @param metadata arbitrary key-value metadata
 * @param compacted whether this entry has been compacted
 */
public record MemoryEntry(String id, Instant timestamp, @Nullable String topic, String content,
		Map<String, String> metadata, boolean compacted) {

	public MemoryEntry {
		metadata = Map.copyOf(metadata);
	}

}
