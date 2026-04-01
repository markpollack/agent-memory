package io.github.markpollack.agentmemory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemMemoryStoreTest {

	@TempDir
	Path tempDir;

	private FileSystemMemoryStore store;

	@BeforeEach
	void setUp() {
		this.store = new FileSystemMemoryStore(this.tempDir.resolve("memory"));
	}

	@Test
	void appendCreatesFileAndIndexEntry() {
		this.store.append("First iteration learnings", Map.of("storyId", "US-001"));

		assertThat(this.store.entries()).hasSize(1);
		MemoryEntry entry = this.store.entries().get(0);
		assertThat(entry.content()).isEqualTo("First iteration learnings");
		assertThat(entry.metadata()).containsEntry("storyId", "US-001");
		assertThat(entry.compacted()).isFalse();
	}

	@Test
	void appendMultipleEntries() {
		this.store.append("Learning A", Map.of());
		this.store.append("Learning B", Map.of());
		this.store.append("Learning C", Map.of());

		assertThat(this.store.entries()).hasSize(3);
	}

	@Test
	void retrieveReturnsAllWithinBudget() {
		this.store.append("Content of entry number 1", Map.of());
		this.store.append("Content of entry number 2", Map.of());

		String result = this.store.retrieve(Integer.MAX_VALUE);
		assertThat(result).contains("Content of entry number 1").contains("Content of entry number 2");
	}

	@Test
	void retrieveRespectsTokenBudget() {
		this.store.append("A longer entry that should fill the budget", Map.of());
		this.store.append("Short", Map.of());

		// Budget of 5 tokens — should only fit "Short" (1 token) since entries are
		// newest-first
		String result = this.store.retrieve(5);
		assertThat(result).contains("Short");
		assertThat(result).doesNotContain("longer entry");
	}

	@Test
	void retrievePrioritizesCompactedOverRaw() {
		this.store.append("Raw learning 1", Map.of());
		this.store.append("Raw learning 2", Map.of());

		// Manually add a compacted entry via the index
		MemoryEntry compacted = new MemoryEntry("compact-1", Instant.now(), null, "Compacted summary of prior work",
				Map.of(), true);
		this.store.getIndex().addEntry(compacted);

		// Budget big enough for all
		String result = this.store.retrieve(Integer.MAX_VALUE);
		// Compacted should appear first
		int compactedIdx = result.indexOf("Compacted summary");
		int rawIdx = result.indexOf("Raw learning");
		assertThat(compactedIdx).isGreaterThanOrEqualTo(0);
		assertThat(rawIdx).isGreaterThan(compactedIdx);
	}

	@Test
	void retrieveWithSmallBudgetReturnsEmpty() {
		this.store.append("Content that is too large for budget", Map.of());

		String result = this.store.retrieve(0);
		assertThat(result).isEmpty();
	}

	@Test
	void retrieveWithLargeBudgetReturnsEverything() {
		for (int i = 0; i < 10; i++) {
			this.store.append("Entry number " + i + " with some content", Map.of());
		}

		String result = this.store.retrieve(Integer.MAX_VALUE);
		for (int i = 0; i < 10; i++) {
			assertThat(result).contains("Entry number " + i);
		}
	}

	@Test
	void emptyStoreRetrievesEmpty() {
		assertThat(this.store.retrieve(Integer.MAX_VALUE)).isEmpty();
		assertThat(this.store.entries()).isEmpty();
	}

	@Test
	void directoryStructureCreated() {
		Path memoryDir = this.tempDir.resolve("memory");
		assertThat(memoryDir.resolve("iterations")).isDirectory();
		assertThat(memoryDir.resolve("patterns")).isDirectory();
	}

	@Test
	void appendWithTopicMetadata() {
		this.store.append("Setup findings", Map.of("topic", "setup"));

		MemoryEntry entry = this.store.entries().get(0);
		assertThat(entry.topic()).isEqualTo("setup");
	}

}
