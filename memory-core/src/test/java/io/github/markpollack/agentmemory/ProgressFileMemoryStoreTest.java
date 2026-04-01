package io.github.markpollack.agentmemory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressFileMemoryStoreTest {

	@TempDir
	Path tempDir;

	private ProgressFileMemoryStore createStore() {
		return new ProgressFileMemoryStore(this.tempDir.resolve("progress.txt"));
	}

	@Test
	void appendSingleEntry() {
		ProgressFileMemoryStore store = createStore();

		store.append("Found a pattern", Map.of("iteration", "1"));

		String content = store.retrieve(0);
		assertThat(content).contains("## Iteration 1").contains("Found a pattern");
	}

	@Test
	void appendMultipleEntries() {
		ProgressFileMemoryStore store = createStore();

		store.append("First learning", Map.of("iteration", "1"));
		store.append("Second learning", Map.of("iteration", "2"));

		String content = store.retrieve(0);
		assertThat(content).contains("## Iteration 1")
			.contains("First learning")
			.contains("## Iteration 2")
			.contains("Second learning");
	}

	@Test
	void retrieveIgnoresTokenBudget() {
		ProgressFileMemoryStore store = createStore();
		store.append("Some content that is longer than a tiny budget", Map.of("iteration", "1"));

		// Budget of 1 token should still return everything
		String content = store.retrieve(1);
		assertThat(content).contains("Some content that is longer");
	}

	@Test
	void retrieveFromMissingFileReturnsEmpty() {
		ProgressFileMemoryStore store = new ProgressFileMemoryStore(this.tempDir.resolve("nonexistent.txt"));

		assertThat(store.retrieve(100)).isEmpty();
	}

	@Test
	void entriesFromMissingFileReturnsEmptyList() {
		ProgressFileMemoryStore store = new ProgressFileMemoryStore(this.tempDir.resolve("nonexistent.txt"));

		assertThat(store.entries()).isEmpty();
	}

	@Test
	void entriesParsesBlocks() {
		ProgressFileMemoryStore store = createStore();
		store.append("First", Map.of("iteration", "1"));
		store.append("Second", Map.of("iteration", "2"));

		List<MemoryEntry> entries = store.entries();

		assertThat(entries).hasSize(2);
		assertThat(entries.get(0).id()).isEqualTo("iter-1");
		assertThat(entries.get(0).content()).isEqualTo("First");
		assertThat(entries.get(1).id()).isEqualTo("iter-2");
		assertThat(entries.get(1).content()).isEqualTo("Second");
	}

	@Test
	void appendIsAppendOnly() {
		ProgressFileMemoryStore store = createStore();
		store.append("Original", Map.of("iteration", "1"));
		String afterFirst = store.retrieve(0);

		store.append("Added", Map.of("iteration", "2"));
		String afterSecond = store.retrieve(0);

		assertThat(afterSecond).startsWith(afterFirst);
	}

}
