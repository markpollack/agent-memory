package io.github.markpollack.agentmemory.advisor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.github.markpollack.agentmemory.FileSystemMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Regression coverage for stored memory and caller instructions that contain brace
 * characters.
 *
 * <p>
 * {@code CompactionMemoryAdvisor.before} renders its system prompt through Spring AI's
 * {@link org.springframework.ai.chat.prompt.PromptTemplate}, whose default delimiter is
 * the brace. Memory holds arbitrary assistant output — source code, JSON, and text that
 * merely looks like a placeholder. These tests pin the requirement that such values are
 * substituted atomically: they must reach the rendered system message byte-for-byte, they
 * must not be re-parsed as further placeholders, and they must not raise.
 */
class CompactionMemoryAdvisorBracesTest {

	private static final String JAVA_BRACES = "if (ready) { run(); }";

	private static final String UNKNOWN_PLACEHOLDER = "{value}";

	private static final String LITERAL_INSTRUCTIONS = "{instructions}";

	private static final String LITERAL_MEMORY = "{memory}";

	private static final String MIXED_CODE_AND_JSON = """
			Retry handler shipped:

			```java
			for (Attempt a : attempts) { if (a.failed()) { backoff(a); } }
			```

			Config emitted by the run:

			```json
			{"retries": 3, "backoff": {"initialMs": 250, "factor": 2.0}, "tags": ["{env}"]}
			```
			""";

	@TempDir
	Path tempDir;

	private FileSystemMemoryStore memoryStore;

	private AdvisorChain advisorChain;

	@BeforeEach
	void setUp() {
		this.memoryStore = new FileSystemMemoryStore(this.tempDir.resolve("memory"));
		this.advisorChain = mock(AdvisorChain.class);
	}

	@Test
	void memoryContainingJavaBracesSurvivesRenderingLiterally() {
		assertThat(renderWithMemory(JAVA_BRACES)).contains(JAVA_BRACES);
	}

	@Test
	void memoryContainingUnknownPlaceholderSurvivesRenderingLiterally() {
		assertThat(renderWithMemory("Prefer " + UNKNOWN_PLACEHOLDER + " over null.")).contains(UNKNOWN_PLACEHOLDER);
	}

	@Test
	void memoryContainingTemplateVariableNamesSurvivesRenderingLiterally() {
		String stored = "The advisor template uses " + LITERAL_INSTRUCTIONS + " and " + LITERAL_MEMORY + ".";

		String rendered = renderWithMemory(stored);

		assertThat(rendered).contains(stored);
		// The stored text must be inert, not a second round of substitution.
		assertThat(rendered).doesNotContain(LITERAL_INSTRUCTIONS + " and " + LITERAL_INSTRUCTIONS);
	}

	@Test
	void memoryContainingMixedCodeAndJsonSurvivesRenderingLiterally() {
		String rendered = renderWithMemory(MIXED_CODE_AND_JSON);

		assertThat(rendered).contains("for (Attempt a : attempts) { if (a.failed()) { backoff(a); } }");
		assertThat(rendered)
			.contains("{\"retries\": 3, \"backoff\": {\"initialMs\": 250, \"factor\": 2.0}, \"tags\": [\"{env}\"]}");
	}

	@Test
	void systemInstructionsContainingBracesSurviveRenderingLiterally() {
		String stored = "Ordinary stored learning.";
		this.memoryStore.append(stored, Map.of());
		String instructions = "Emit " + UNKNOWN_PLACEHOLDER + " when unset. Example: " + JAVA_BRACES + " Never echo "
				+ LITERAL_MEMORY + ".";

		String rendered = render(instructions);

		assertThat(rendered).contains(instructions);
		assertThat(rendered).contains(stored);
		// Each supplied value is substituted once, into its own slot only. A renderer
		// that re-parsed substituted values would expand the literal "{memory}" carried
		// inside the instructions and emit the stored learning a second time.
		assertThat(rendered).containsOnlyOnce(stored);
	}

	@Test
	void bracesInMemoryAndInstructionsDoNotRaise() {
		this.memoryStore.append(MIXED_CODE_AND_JSON, Map.of());
		this.memoryStore.append(LITERAL_MEMORY + " " + UNKNOWN_PLACEHOLDER, Map.of());

		assertThatCode(() -> render("Follow " + LITERAL_INSTRUCTIONS + " strictly: " + JAVA_BRACES))
			.doesNotThrowAnyException();
	}

	private String renderWithMemory(String storedContent) {
		this.memoryStore.append(storedContent, Map.of());
		return render("You are a helpful assistant.");
	}

	private String render(String instructions) {
		CompactionMemoryAdvisor advisor = CompactionMemoryAdvisor.builder(this.memoryStore).build();

		Prompt prompt = new Prompt(List.of(new SystemMessage(instructions), new UserMessage("Continue the task.")));
		ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

		return advisor.before(request, this.advisorChain).prompt().getSystemMessage().getText();
	}

}
