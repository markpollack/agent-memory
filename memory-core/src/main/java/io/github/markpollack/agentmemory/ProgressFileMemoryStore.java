package io.github.markpollack.agentmemory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple append-only memory implementation using a single {@code progress.txt} file.
 *
 * <p>
 * Each append writes a separator, timestamp header, and content block. The
 * {@link #retrieve(int)} method ignores the token budget and always returns the entire
 * file — this is faithful to the original Ralph Wiggum pattern where the full memory dump
 * is included in every iteration prompt.
 */
public class ProgressFileMemoryStore implements MemoryStore {

	private static final String SEPARATOR = "\n---\n\n";

	private static final Pattern BLOCK_PATTERN = Pattern
		.compile("## Iteration (\\d+) — (\\S+)\n\n([\\s\\S]*?)(?=\n---\n|$)");

	private final Path progressFile;

	private final AtomicInteger counter = new AtomicInteger(0);

	public ProgressFileMemoryStore(Path progressFile) {
		this.progressFile = progressFile;
	}

	@Override
	public void append(String content, Map<String, String> metadata) {
		int iteration = this.counter.incrementAndGet();
		String iterationLabel = metadata.getOrDefault("iteration", String.valueOf(iteration));
		Instant timestamp = Instant.now();
		String block = "## Iteration " + iterationLabel + " — " + timestamp + "\n\n" + content + SEPARATOR;
		try {
			Files.writeString(this.progressFile, block, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to append to progress file: " + this.progressFile, ex);
		}
	}

	/**
	 * Returns the entire file contents, ignoring the token budget. This is the flat-file
	 * behavior: full dump every iteration.
	 */
	@Override
	public String retrieve(int tokenBudget) {
		if (!Files.exists(this.progressFile)) {
			return "";
		}
		try {
			return Files.readString(this.progressFile);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to read progress file: " + this.progressFile, ex);
		}
	}

	@Override
	public List<MemoryEntry> entries() {
		if (!Files.exists(this.progressFile)) {
			return List.of();
		}
		String content;
		try {
			content = Files.readString(this.progressFile);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to read progress file: " + this.progressFile, ex);
		}
		List<MemoryEntry> result = new ArrayList<>();
		Matcher matcher = BLOCK_PATTERN.matcher(content);
		while (matcher.find()) {
			String iterNum = matcher.group(1);
			String timestampStr = matcher.group(2);
			String body = matcher.group(3).trim();
			Instant timestamp;
			try {
				timestamp = Instant.parse(timestampStr);
			}
			catch (Exception ex) {
				timestamp = Instant.EPOCH;
			}
			result.add(new MemoryEntry("iter-" + iterNum, timestamp, null, body, Map.of("iteration", iterNum), false));
		}
		return Collections.unmodifiableList(result);
	}

}
