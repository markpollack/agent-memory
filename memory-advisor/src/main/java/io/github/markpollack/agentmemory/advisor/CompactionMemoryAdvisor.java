package io.github.markpollack.agentmemory.advisor;

import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;

import io.github.markpollack.agentmemory.FileSystemMemoryStore;
import io.github.markpollack.agentmemory.MemoryCompactor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Spring AI advisor that wraps compaction-based memory management. Augments the system
 * message with token-budgeted memory retrieval on each request, and appends assistant
 * responses to memory after each call. Triggers LLM-powered compaction when the
 * uncompacted memory exceeds the configured threshold.
 *
 * <p>
 * Unlike {@code BaseChatMemoryAdvisor}, this does not store conversation history — it
 * stores accumulated learnings in a filesystem memory format.
 *
 * @see FileSystemMemoryStore
 * @see MemoryCompactor
 */
public final class CompactionMemoryAdvisor implements BaseAdvisor {

	private static final Logger log = LoggerFactory.getLogger(CompactionMemoryAdvisor.class);

	private static final PromptTemplate DEFAULT_SYSTEM_PROMPT_TEMPLATE = new PromptTemplate("""
			{instructions}

			Use the accumulated project knowledge from the MEMORY section to inform your response.
			Build on previous learnings and avoid repeating past mistakes.

			---------------------
			MEMORY:
			{memory}
			---------------------

			""");

	private final FileSystemMemoryStore memoryStore;

	private final @Nullable ChatClient compactionChatClient;

	private final MemoryCompactor compactor;

	private final int memoryTokenBudget;

	private final PromptTemplate systemPromptTemplate;

	private final int order;

	private final Scheduler scheduler;

	private CompactionMemoryAdvisor(FileSystemMemoryStore memoryStore, @Nullable ChatClient compactionChatClient,
			int memoryTokenBudget, double compactionRatio, PromptTemplate systemPromptTemplate, int order,
			Scheduler scheduler) {
		this.memoryStore = memoryStore;
		this.compactionChatClient = compactionChatClient;
		this.memoryTokenBudget = memoryTokenBudget;
		this.compactor = new MemoryCompactor(memoryTokenBudget, compactionRatio);
		this.systemPromptTemplate = systemPromptTemplate;
		this.order = order;
		this.scheduler = scheduler;
	}

	public static Builder builder(FileSystemMemoryStore memoryStore) {
		return new Builder(memoryStore);
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public Scheduler getScheduler() {
		return this.scheduler;
	}

	@Override
	public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
		String memory = this.memoryStore.retrieve(this.memoryTokenBudget);

		SystemMessage systemMessage = chatClientRequest.prompt().getSystemMessage();
		String instructions = systemMessage != null ? systemMessage.getText() : "";

		String augmentedSystemText = this.systemPromptTemplate
			.render(Map.of("instructions", instructions, "memory", memory.isEmpty() ? "(none)" : memory));

		return chatClientRequest.mutate()
			.prompt(chatClientRequest.prompt().augmentSystemMessage(augmentedSystemText))
			.build();
	}

	@Override
	public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
		String assistantText = extractAssistantText(chatClientResponse);
		if (assistantText != null && !assistantText.isBlank()) {
			this.memoryStore.append(assistantText, Map.of());

			if (this.compactionChatClient != null) {
				boolean compacted = this.compactor.compact(this.memoryStore, this.compactionChatClient);
				if (compacted) {
					log.debug("Memory compaction triggered after advisor response");
				}
			}
		}
		return chatClientResponse;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		return Mono.just(chatClientRequest)
			.publishOn(getScheduler())
			.map(request -> this.before(request, streamAdvisorChain))
			.flatMapMany(streamAdvisorChain::nextStream)
			.transform(flux -> new ChatClientMessageAggregator().aggregateChatClientResponse(flux,
					response -> this.after(response, streamAdvisorChain)));
	}

	private static @Nullable String extractAssistantText(@Nullable ChatClientResponse response) {
		if (response == null || response.chatResponse() == null) {
			return null;
		}
		var results = response.chatResponse().getResults();
		if (results == null || results.isEmpty()) {
			return null;
		}
		var output = results.get(0).getOutput();
		return output != null ? output.getText() : null;
	}

	public static final class Builder {

		private final FileSystemMemoryStore memoryStore;

		private @Nullable ChatClient compactionChatClient;

		private int memoryTokenBudget = 8192;

		private double compactionRatio = 0.75;

		private PromptTemplate systemPromptTemplate = DEFAULT_SYSTEM_PROMPT_TEMPLATE;

		private int order = DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER;

		private Scheduler scheduler = DEFAULT_SCHEDULER;

		private Builder(FileSystemMemoryStore memoryStore) {
			this.memoryStore = Objects.requireNonNull(memoryStore, "memoryStore");
		}

		public Builder compactionChatClient(ChatClient compactionChatClient) {
			this.compactionChatClient = compactionChatClient;
			return this;
		}

		public Builder memoryTokenBudget(int memoryTokenBudget) {
			this.memoryTokenBudget = memoryTokenBudget;
			return this;
		}

		public Builder compactionRatio(double compactionRatio) {
			this.compactionRatio = compactionRatio;
			return this;
		}

		public Builder systemPromptTemplate(PromptTemplate systemPromptTemplate) {
			this.systemPromptTemplate = Objects.requireNonNull(systemPromptTemplate, "systemPromptTemplate");
			return this;
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public Builder scheduler(Scheduler scheduler) {
			this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
			return this;
		}

		public CompactionMemoryAdvisor build() {
			return new CompactionMemoryAdvisor(this.memoryStore, this.compactionChatClient, this.memoryTokenBudget,
					this.compactionRatio, this.systemPromptTemplate, this.order, this.scheduler);
		}

	}

}
