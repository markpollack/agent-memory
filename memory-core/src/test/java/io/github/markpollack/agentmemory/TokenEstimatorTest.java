package io.github.markpollack.agentmemory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenEstimatorTest {

	private final TokenEstimator estimator = new TokenEstimator();

	@Test
	void emptyStringReturnsZero() {
		assertThat(this.estimator.estimate("")).isZero();
	}

	@Test
	void nullReturnsZero() {
		assertThat(this.estimator.estimate(null)).isZero();
	}

	@Test
	void knownStringReturnsExpectedEstimate() {
		// 20 chars / 4 = 5 tokens
		assertThat(this.estimator.estimate("12345678901234567890")).isEqualTo(5);
	}

	@Test
	void shortStringRoundsDown() {
		// 3 chars / 4 = 0 (integer division)
		assertThat(this.estimator.estimate("abc")).isZero();
	}

	@Test
	void estimateAllSumsEntries() {
		List<MemoryEntry> entries = List.of(new MemoryEntry("1", Instant.now(), null, "12345678", Map.of(), false),
				new MemoryEntry("2", Instant.now(), null, "1234567890123456", Map.of(), false));
		// 8/4 + 16/4 = 2 + 4 = 6
		assertThat(this.estimator.estimateAll(entries)).isEqualTo(6);
	}

	@Test
	void estimateAllEmptyListReturnsZero() {
		assertThat(this.estimator.estimateAll(List.of())).isZero();
	}

}
