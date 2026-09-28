package com.company.chatdocs.migration;

import com.company.chatdocs.TestcontainersConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs V8 against a scratch database that is migrated only up to V7, with rows in the old layout (files on disk,
 * paths in {@code document.storage_path}).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
class MoveUploadedFilesMigrationTests {

	private static final String DATABASE = "migration_check";

	@Autowired
	HikariDataSource appDataSource;

	@TempDir
	Path uploadDir;

	JdbcTemplate admin;
	DriverManagerDataSource scratch;

	@BeforeEach
	void createScratchDatabase() {
		admin = new JdbcTemplate(appDataSource);
		admin.execute("DROP DATABASE IF EXISTS " + DATABASE);
		admin.execute("CREATE DATABASE " + DATABASE);
		String url = appDataSource.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + DATABASE + "$1");
		scratch = new DriverManagerDataSource(url, appDataSource.getUsername(), appDataSource.getPassword());
	}

	@AfterEach
	void dropScratchDatabase() {
		admin.execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
	}

	@Test
	void copiesExistingFilesIntoTheDatabaseAndDropsThePathColumn() throws Exception {
		Flyway.configure().dataSource(scratch).target("7").load().migrate();
		JdbcTemplate db = new JdbcTemplate(scratch);
		UUID owner = db.queryForObject("""
				INSERT INTO app_user (username, password_hash, display_name, role)
				VALUES ('old', 'x', 'Old User', 'USER') RETURNING id""", UUID.class);
		Files.createDirectories(uploadDir.resolve(owner.toString()));
		Files.writeString(uploadDir.resolve(owner + "/kept.txt"), "original bytes");
		UUID kept = insertDocument(db, owner, owner + "/kept.txt", "a");
		UUID lost = insertDocument(db, owner, owner + "/lost.txt", "b");

		Flyway.configure().dataSource(scratch)
				.javaMigrations(new V8__Move_uploaded_files_into_database(uploadDir))
				.load().migrate();

		byte[] content = db.queryForObject("SELECT content FROM document_file WHERE document_id = ?", byte[].class,
				kept);
		assertThat(content).asString().isEqualTo("original bytes");
		assertThat(db.queryForObject("SELECT count(*) FROM document_file WHERE document_id = ?", Integer.class, lost))
				.isZero();
		assertThat(db.queryForObject("SELECT count(*) FROM document", Integer.class)).isEqualTo(2);
		assertThat(db.queryForObject("""
				SELECT count(*) FROM information_schema.columns
				WHERE table_name = 'document' AND column_name = 'storage_path'""", Integer.class)).isZero();
	}

	private static UUID insertDocument(JdbcTemplate db, UUID owner, String storagePath, String checksumChar) {
		return db.queryForObject("""
				INSERT INTO document (owner_id, file_name, content_type, size_bytes, storage_path, checksum_sha256)
				VALUES (?, 'file.txt', 'text/plain', 1, ?, ?) RETURNING id""", UUID.class,
				owner, storagePath, checksumChar.repeat(64));
	}

}
