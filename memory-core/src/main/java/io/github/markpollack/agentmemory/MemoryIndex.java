package io.github.markpollack.agentmemory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jspecify.annotations.Nullable;

/**
 * File-backed index of memory entries, persisted as {@code _index.json}.
 *
 * <p>
 * Provides query methods for retrieving entries by topic, compaction status, and ID. All
 * mutations are immediately persisted to disk.
 */
public class MemoryIndex {

	private final Path indexFile;

	private final ObjectMapper objectMapper;

	private final List<MemoryEntry> entries;

	public MemoryIndex(Path memoryDirectory) {
		this.indexFile = memoryDirectory.resolve("_index.json");
		this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
			.enable(SerializationFeature.INDENT_OUTPUT);
		this.entries = new ArrayList<>();
		loadIfExists();
	}

	/**
	 * Add an entry to the index and persist.
	 */
	public void addEntry(MemoryEntry entry) {
		this.entries.add(entry);
		persist();
	}

	/**
	 * Return all indexed entries.
	 */
	public List<MemoryEntry> getEntries() {
		return Collections.unmodifiableList(this.entries);
	}

	/**
	 * Return entries matching the given topic.
	 */
	public List<MemoryEntry> getByTopic(@Nullable String topic) {
		if (topic == null) {
			return this.entries.stream().filter(e -> e.topic() == null).collect(Collectors.toUnmodifiableList());
		}
		return this.entries.stream().filter(e -> topic.equals(e.topic())).collect(Collectors.toUnmodifiableList());
	}

	/**
	 * Return entries where {@code compacted} is {@code false}.
	 */
	public List<MemoryEntry> getUncompacted() {
		return this.entries.stream().filter(e -> !e.compacted()).collect(Collectors.toUnmodifiableList());
	}

	/**
	 * Mark the entry with the given ID as compacted and persist.
	 */
	public void markCompacted(String entryId) {
		for (int i = 0; i < this.entries.size(); i++) {
			MemoryEntry entry = this.entries.get(i);
			if (entry.id().equals(entryId)) {
				this.entries.set(i, new MemoryEntry(entry.id(), entry.timestamp(), entry.topic(), entry.content(),
						entry.metadata(), true));
				persist();
				return;
			}
		}
	}

	/**
	 * Return the number of entries.
	 */
	public int size() {
		return this.entries.size();
	}

	private void loadIfExists() {
		if (!Files.exists(this.indexFile)) {
			return;
		}
		try {
			String json = Files.readString(this.indexFile);
			List<MemoryEntry> loaded = this.objectMapper.readValue(json, new TypeReference<List<MemoryEntry>>() {
			});
			this.entries.addAll(loaded);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to load memory index: " + this.indexFile, ex);
		}
	}

	private void persist() {
		try {
			Files.createDirectories(this.indexFile.getParent());
			String json = this.objectMapper.writeValueAsString(this.entries);
			Files.writeString(this.indexFile, json);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to persist memory index: " + this.indexFile, ex);
		}
	}

}
