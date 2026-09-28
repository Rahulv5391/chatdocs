package com.company.chatdocs.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * One-time move of uploaded files from the local upload folder into {@code document_file} (created by V7), then drops
 * the old {@code document.storage_path} column. Files are copied one at a time, so memory use stays low.
 * <p>
 * A file that is already missing is skipped with a warning: its document keeps working for questions (the chunks are
 * in the database), only opening the original file fails. A fresh database has no documents, so this does nothing.
 * <p>
 * Spring Boot hands this bean to Flyway; the class name gives it version 8.
 */
@Component
public class V8__Move_uploaded_files_into_database extends BaseJavaMigration {

	private static final Logger log = LoggerFactory.getLogger(V8__Move_uploaded_files_into_database.class);

	private final Path uploadDir;

	public V8__Move_uploaded_files_into_database(@Value("${app.storage-dir:./data/uploads}") Path uploadDir) {
		this.uploadDir = uploadDir.toAbsolutePath().normalize();
	}

	@Override
	public void migrate(Context context) throws SQLException, IOException {
		Connection connection = context.getConnection();
		int copied = 0;
		int missing = 0;
		try (Statement select = connection.createStatement();
				ResultSet rows = select.executeQuery("SELECT id, storage_path FROM document");
				PreparedStatement insert = connection.prepareStatement(
						"INSERT INTO document_file (document_id, content) VALUES (?, ?)")) {
			while (rows.next()) {
				UUID id = rows.getObject("id", UUID.class);
				Path file = uploadDir.resolve(rows.getString("storage_path")).normalize();
				if (!file.startsWith(uploadDir) || !Files.isRegularFile(file)) {
					log.warn("File of document {} not found at {}, skipping it", id, file);
					missing++;
					continue;
				}
				insert.setObject(1, id);
				insert.setBytes(2, Files.readAllBytes(file));
				insert.executeUpdate();
				copied++;
			}
		}
		try (Statement drop = connection.createStatement()) {
			drop.execute("ALTER TABLE document DROP COLUMN storage_path");
		}
		if (copied + missing > 0) {
			log.info("Moved {} uploaded file(s) from {} into the database ({} missing). The folder can be deleted.",
					copied, uploadDir, missing);
		}
	}

}
