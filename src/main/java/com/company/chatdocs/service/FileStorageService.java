package com.company.chatdocs.service;

import com.company.chatdocs.config.AppProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Saves, finds and deletes uploaded files on local disk under {@code app.storage-dir}.
 * Paths stored in the database are relative to that folder, so the folder can move without a data migration.
 */
@Service
public class FileStorageService {

	private final Path root;

	FileStorageService(AppProperties properties) {
		this.root = properties.storageDir().toAbsolutePath().normalize();
	}

	/** Saves the content as {@code {ownerId}/{random-uuid}.{extension}} and returns that relative path. */
	public String save(UUID ownerId, String extension, byte[] content) {
		String relativePath = ownerId + "/" + UUID.randomUUID() + "." + extension;
		Path target = resolve(relativePath);
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, content);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not save file " + relativePath, e);
		}
		return relativePath;
	}

	/** Returns the absolute path of a stored file. */
	public Path resolve(String relativePath) {
		Path path = root.resolve(relativePath).normalize();
		if (!path.startsWith(root)) {
			throw new IllegalArgumentException("Path is outside the storage folder: " + relativePath);
		}
		return path;
	}

	/** Deletes a stored file. Does nothing if it is already gone. */
	public void delete(String relativePath) {
		try {
			Files.deleteIfExists(resolve(relativePath));
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not delete file " + relativePath, e);
		}
	}

}
