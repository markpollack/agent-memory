package io.github.markpollack.agentmemory;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryEntryTest {

	@Test
	void constructionWithAllFields() {
		Instant now = Instant.now();
		MemoryEntry entry = new MemoryEntry("iter-001", now, "patterns", "Found a useful pattern",
				Map.of("iteration", "1"), false);

		assertThat(entry.id()).isEqualTo("iter-001");
		assertThat(entry.timestamp()).isEqualTo(now);
		assertThat(entry.topic()).isEqualTo("patterns");
		assertThat(entry.content()).isEqualTo("Found a useful pattern");
		assertThat(entry.metadata()).containsEntry("iteration", "1");
		assertThat(entry.compacted()).isFalse();
	}

	@Test
	void constructionWithNullTopic() {
		MemoryEntry entry = new MemoryEntry("iter-002", Instant.now(), null, "Uncategorized learning", Map.of(), false);

		assertThat(entry.topic()).isNull();
	}

	@Test
	void metadataIsImmutable() {
		MemoryEntry entry = new MemoryEntry("iter-003", Instant.now(), null, "content", Map.of("k", "v"), false);

		assertThat(entry.metadata()).isUnmodifiable();
	}

}
