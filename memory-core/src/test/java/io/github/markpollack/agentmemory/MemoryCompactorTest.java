package io.github.markpollack.agentmemory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemoryCompactorTest {

	@TempDir
	Path tempDir;

	@Mock
	ChatModel chatModel;

	private FileSystemMemoryStore store;

	private ChatClient chatClient;

	@BeforeEach
	void setUp() {
		this.store = new FileSystemMemoryStore(this.tempDir.resolve("memory"));
		// Spring AI 2.0 GA derives request options via chatModel.getOptions().mutate();
		// mock returns null by default.
		when(this.chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
		this.chatClient = ChatClient.builder(this.chatModel).build();
	}

	@Test
	void compactionTriggersWhenTokensExceedRatio() {
		// Budget 100 tokens, ratio 0.75 → triggers at 75 tokens
		// Each entry: 400 chars = 100 tokens. Two entries = 200 tokens > 75.
		MemoryCompactor compactor = new MemoryCompactor(100, 0.75);
		mockCompactionResponse("## Status\nCompleted stories.\n\n## Learnings\nUse OAuth2.");

		this.store.append("X".repeat(400), Map.of());
		this.store.append("Y".repeat(400), Map.of());

		boolean result = compactor.compact(this.store, this.chatClient);

		assertThat(result).isTrue();
		assertThat(this.store.getIndex().getUncompacted()).isEmpty();
		long compactedCount = this.store.entries()
			.stream()
			.filter(e -> e.topic() != null && e.topic().equals("compacted"))
			.count();
		assertThat(compactedCount).isEqualTo(1);
	}

	@Test
	void compactionDoesNotTriggerBelowRatio() {
		// Budget 1000 tokens, ratio 0.75 → triggers at 750 tokens
		// Two entries of 100 chars = 50 tokens total, well below 750.
		MemoryCompactor compactor = new MemoryCompactor(1000, 0.75);

		this.store.append("A".repeat(100), Map.of());
		this.store.append("B".repeat(100), Map.of());

		boolean result = compactor.compact(this.store, this.chatClient);

		assertThat(result).isFalse();
		assertThat(this.store.getIndex().getUncompacted()).hasSize(2);
	}

	@Test
	void compactedSummaryIsRetrievable() {
		// Budget 40 tokens, ratio 0.5 → triggers at 20 tokens
		// Two entries of 100 chars = 50 tokens > 20.
		MemoryCompactor compactor = new MemoryCompactor(40, 0.5);
		mockCompactionResponse("## Status\nDone.\n\n## Learnings\nKey insight.");

		this.store.append("A".repeat(100), Map.of());
		this.store.append("B".repeat(100), Map.of());

		compactor.compact(this.store, this.chatClient);

		String retrieved = this.store.retrieve(Integer.MAX_VALUE);
		assertThat(retrieved).contains("Key insight");
	}

	@Test
	void compactedSummaryWrittenToPatternsDir() {
		MemoryCompactor compactor = new MemoryCompactor(40, 0.5);
		mockCompactionResponse("Summary content");

		this.store.append("A".repeat(100), Map.of());
		this.store.append("B".repeat(100), Map.of());

		compactor.compact(this.store, this.chatClient);

		Path patternsDir = this.store.getPatternsDir();
		assertThat(patternsDir).isDirectory();
		assertThat(patternsDir.toFile().listFiles()).hasSize(1);
	}

	@Test
	void originalEntriesPreservedAfterCompaction() {
		MemoryCompactor compactor = new MemoryCompactor(40, 0.5);
		mockCompactionResponse("Summary");

		this.store.append("A".repeat(100), Map.of());
		this.store.append("B".repeat(100), Map.of());

		compactor.compact(this.store, this.chatClient);

		// All entries still in index (originals + compacted summary)
		assertThat(this.store.entries()).hasSize(3);
		// Originals marked compacted but not deleted
		long compactedOriginals = this.store.entries()
			.stream()
			.filter(e -> e.compacted() && !"compacted".equals(e.topic()))
			.count();
		assertThat(compactedOriginals).isEqualTo(2);
	}

	private void mockCompactionResponse(String content) {
		AssistantMessage message = new AssistantMessage(content);
		Generation generation = mock(Generation.class);
		when(generation.getOutput()).thenReturn(message);

		ChatResponse response = ChatResponse.builder().generations(List.of(generation)).build();

		when(this.chatModel.call(any(Prompt.class))).thenReturn(response);
	}

}
