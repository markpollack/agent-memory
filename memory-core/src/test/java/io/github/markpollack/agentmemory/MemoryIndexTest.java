package io.github.markpollack.agentmemory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryIndexTest {

	@TempDir
	Path tempDir;

	@Test
	void emptyIndexReturnsEmptyList() {
		MemoryIndex index = new MemoryIndex(this.tempDir);
		assertThat(index.getEntries()).isEmpty();
		assertThat(index.size()).isZero();
	}

	@Test
	void addEntryPersistsAndReloads() {
		MemoryEntry entry = new MemoryEntry("e1", Instant.parse("2026-01-28T10:00:00Z"), "setup", "Created schema",
				Map.of("storyId", "US-001"), false);

		MemoryIndex index = new MemoryIndex(this.tempDir);
		index.addEntry(entry);

		// Reload from disk
		MemoryIndex reloaded = new MemoryIndex(this.tempDir);
		assertThat(reloaded.getEntries()).hasSize(1);
		assertThat(reloaded.getEntries().get(0).id()).isEqualTo("e1");
		assertThat(reloaded.getEntries().get(0).topic()).isEqualTo("setup");
		assertThat(reloaded.getEntries().get(0).content()).isEqualTo("Created schema");
	}

	@Test
	void getByTopicFilters() {
		MemoryIndex index = new MemoryIndex(this.tempDir);
		index.addEntry(new MemoryEntry("e1", Instant.now(), "setup", "Content A", Map.of(), false));
		index.addEntry(new MemoryEntry("e2", Instant.now(), "implementation", "Content B", Map.of(), false));
		index.addEntry(new MemoryEntry("e3", Instant.now(), "setup", "Content C", Map.of(), false));

		List<MemoryEntry> setupEntries = index.getByTopic("setup");
		assertThat(setupEntries).hasSize(2);
		assertThat(setupEntries).extracting(MemoryEntry::id).containsExactly("e1", "e3");
	}

	@Test
	void getByTopicNullReturnsUncategorized() {
		MemoryIndex index = new MemoryIndex(this.tempDir);
		index.addEntry(new MemoryEntry("e1", Instant.now(), null, "Uncategorized", Map.of(), false));
		index.addEntry(new MemoryEntry("e2", Instant.now(), "setup", "Categorized", Map.of(), false));

		List<MemoryEntry> nullEntries = index.getByTopic(null);
		assertThat(nullEntries).hasSize(1);
		assertThat(nullEntries.get(0).id()).isEqualTo("e1");
	}

	@Test
	void getUncompactedFilters() {
		MemoryIndex index = new MemoryIndex(this.tempDir);
		index.addEntry(new MemoryEntry("e1", Instant.now(), null, "Raw", Map.of(), false));
		index.addEntry(new MemoryEntry("e2", Instant.now(), null, "Compacted", Map.of(), true));
		index.addEntry(new MemoryEntry("e3", Instant.now(), null, "Also raw", Map.of(), false));

		List<MemoryEntry> uncompacted = index.getUncompacted();
		assertThat(uncompacted).hasSize(2);
		assertThat(uncompacted).extracting(MemoryEntry::id).containsExactly("e1", "e3");
	}

	@Test
	void markCompactedUpdatesAndPersists() {
		MemoryIndex index = new MemoryIndex(this.tempDir);
		index.addEntry(new MemoryEntry("e1", Instant.now(), null, "Content", Map.of(), false));

		index.markCompacted("e1");

		assertThat(index.getEntries().get(0).compacted()).isTrue();
		assertThat(index.getUncompacted()).isEmpty();

		// Verify persisted
		MemoryIndex reloaded = new MemoryIndex(this.tempDir);
		assertThat(reloaded.getEntries().get(0).compacted()).isTrue();
	}

	@Test
	void markCompactedWithUnknownIdIsNoOp() {
		MemoryIndex index = new MemoryIndex(this.tempDir);
		index.addEntry(new MemoryEntry("e1", Instant.now(), null, "Content", Map.of(), false));

		index.markCompacted("nonexistent");

		assertThat(index.getEntries().get(0).compacted()).isFalse();
	}

	@Test
	void multipleEntriesRoundTrip() {
		MemoryIndex index = new MemoryIndex(this.tempDir);
		for (int i = 0; i < 5; i++) {
			index.addEntry(new MemoryEntry("e" + i, Instant.now(), "topic-" + (i % 2), "Content " + i,
					Map.of("key", "val" + i), false));
		}

		MemoryIndex reloaded = new MemoryIndex(this.tempDir);
		assertThat(reloaded.size()).isEqualTo(5);
		assertThat(reloaded.getByTopic("topic-0")).hasSize(3);
		assertThat(reloaded.getByTopic("topic-1")).hasSize(2);
	}

}
