package io.github.markpollack.agentmemory.advisor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.github.markpollack.agentmemory.FileSystemMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompactionMemoryAdvisorTest {

	@TempDir
	Path tempDir;

	@Mock
	ChatModel compactionChatModel;

	@Mock
	AdvisorChain advisorChain;

	private FileSystemMemoryStore memoryStore;

	private ChatClient compactionClient;

	@BeforeEach
	void setUp() {
		this.memoryStore = new FileSystemMemoryStore(this.tempDir.resolve("memory"));
		this.compactionClient = ChatClient.builder(this.compactionChatModel).build();
	}

	@Test
	void beforeAugmentsSystemMessageWithMemory() {
		this.memoryStore.append("Database uses PostgreSQL with connection pooling.", Map.of());

		CompactionMemoryAdvisor advisor = CompactionMemoryAdvisor.builder(this.memoryStore).build();

		Prompt prompt = new Prompt(List.of(new SystemMessage("You are a helpful assistant."),
				new UserMessage("Create the API endpoints.")));
		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		ChatClientRequest result = advisor.before(request, this.advisorChain);

		String systemText = result.prompt().getSystemMessage().getText();
		assertThat(systemText).contains("PostgreSQL");
		assertThat(systemText).contains("MEMORY");
	}

	@Test
	void beforeWithEmptyMemoryRendersNoneMarker() {
		CompactionMemoryAdvisor advisor = CompactionMemoryAdvisor.builder(this.memoryStore).build();

		Prompt prompt = new Prompt(
				List.of(new SystemMessage("You are a helpful assistant."), new UserMessage("Hello.")));
		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		ChatClientRequest result = advisor.before(request, this.advisorChain);

		String systemText = result.prompt().getSystemMessage().getText();
		assertThat(systemText).contains("(none)");
	}

	@Test
	void afterAppendsResponseToMemory() {
		CompactionMemoryAdvisor advisor = CompactionMemoryAdvisor.builder(this.memoryStore).build();

		ChatClientResponse response = buildResponse("Created REST API with GET and POST endpoints.");

		advisor.after(response, this.advisorChain);

		assertThat(this.memoryStore.entries()).hasSize(1);
		assertThat(this.memoryStore.entries().get(0).content()).contains("REST API");
	}

	@Test
	void afterTriggersCompactionWhenThresholdMet() {
		mockCompactionResponse("## Status\nCompleted.\n## Learnings\nCompacted insight.");

		CompactionMemoryAdvisor advisor = CompactionMemoryAdvisor.builder(this.memoryStore)
			.compactionChatClient(this.compactionClient)
			.memoryTokenBudget(30)
			.compactionRatio(0.5)
			.build();

		// Add enough content to exceed threshold (30 * 0.5 = 15 tokens)
		advisor.after(buildResponse("A".repeat(100)), this.advisorChain); // ~25 tokens
		advisor.after(buildResponse("B".repeat(100)), this.advisorChain); // ~25 more
																			// tokens

		// Compaction should have occurred — originals marked compacted
		assertThat(this.memoryStore.getIndex().getUncompacted()).isEmpty();
	}

	@Test
	void afterDoesNothingForNullResponse() {
		CompactionMemoryAdvisor advisor = CompactionMemoryAdvisor.builder(this.memoryStore).build();

		ChatClientResponse response = ChatClientResponse.builder().build();
		advisor.after(response, this.advisorChain);

		assertThat(this.memoryStore.entries()).isEmpty();
	}

	@Test
	void builderRequiresMemoryStore() {
		assertThatThrownBy(() -> CompactionMemoryAdvisor.builder(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void builderDefaultValues() {
		CompactionMemoryAdvisor advisor = CompactionMemoryAdvisor.builder(this.memoryStore).build();

		assertThat(advisor.getOrder()).isEqualTo(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER);
	}

	@Test
	void customOrderIsRespected() {
		CompactionMemoryAdvisor advisor = CompactionMemoryAdvisor.builder(this.memoryStore).order(42).build();

		assertThat(advisor.getOrder()).isEqualTo(42);
	}

	private ChatClientResponse buildResponse(String content) {
		AssistantMessage message = new AssistantMessage(content);
		Generation generation = mock(Generation.class);
		when(generation.getOutput()).thenReturn(message);
		ChatResponse chatResponse = ChatResponse.builder().generations(List.of(generation)).build();
		return ChatClientResponse.builder().chatResponse(chatResponse).build();
	}

	private void mockCompactionResponse(String content) {
		AssistantMessage message = new AssistantMessage(content);
		Generation generation = mock(Generation.class);
		when(generation.getOutput()).thenReturn(message);
		ChatResponse response = ChatResponse.builder().generations(List.of(generation)).build();
		when(this.compactionChatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response);
	}

}
