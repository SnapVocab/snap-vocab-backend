package vn.ptit.snapvocab.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import vn.ptit.snapvocab.mission.MissionMigrationScriptTest;
import vn.ptit.snapvocab.support.LocalMySqlJpaTest;

/**
 * M-01 (leaderboard_backend.md §11.4): migration_leaderboard_v1.sql adds the covering index in the entity's column
 * order, twice without error; the rollback drops it, twice without error. Runs in a throw-away database.
 */
@LocalMySqlJpaTest
class LeaderboardMigrationScriptTest {

	private static final Path SCRIPT = Path.of("migration/migration_leaderboard_v1.sql");
	private static final Path ROLLBACK = Path.of("migration/migration_leaderboard_v1_rollback.sql");

	@Autowired
	DataSource dataSource;

	private String entitySchema;
	private String scratch;
	private SingleConnectionDataSource scratchDb;

	@BeforeEach
	void createScratchDatabase() throws SQLException {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		entitySchema = jdbc.queryForObject("SELECT DATABASE()", String.class);
		scratch = entitySchema + "_lb_migration";
		jdbc.execute("DROP DATABASE IF EXISTS `" + scratch + "`");
		jdbc.execute("CREATE DATABASE `" + scratch + "`");
		Connection connection = dataSource.getConnection();
		connection.setCatalog(scratch);
		scratchDb = new SingleConnectionDataSource(connection, true);
		new JdbcTemplate(scratchDb).execute("CREATE TABLE experience_logs (id VARCHAR(36) NOT NULL PRIMARY KEY, "
				+ "user_id BIGINT NOT NULL, amount INT NOT NULL, base_amount INT NOT NULL, bonus_amount INT NOT NULL, "
				+ "source_type VARCHAR(30) NOT NULL, event_key VARCHAR(191) NOT NULL, created_at DATETIME(6) NOT NULL) "
				+ "ENGINE=InnoDB");
	}

	@AfterEach
	void dropScratchDatabase() throws SQLException {
		scratchDb.getConnection().setCatalog(entitySchema); // hand the pooled connection back on the test schema
		scratchDb.destroy();
		new JdbcTemplate(dataSource).execute("DROP DATABASE IF EXISTS `" + scratch + "`");
	}

	@Test
	void scriptIsIdempotentMatchesTheEntityAndRollsBack() throws Exception {
		run(SCRIPT);
		run(SCRIPT);
		List<String> expected = List.of("created_at", "source_type", "user_id", "amount", "bonus_amount");
		assertThat(indexColumns(scratch)).isEqualTo(expected);
		assertThat(indexColumns(entitySchema)).isEqualTo(expected); // Hibernate built the same index from @Index

		run(ROLLBACK);
		run(ROLLBACK);
		assertThat(indexColumns(scratch)).isEmpty();
	}

	private List<String> indexColumns(String schema) {
		return new JdbcTemplate(dataSource).queryForList("SELECT COLUMN_NAME FROM information_schema.STATISTICS "
				+ "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'experience_logs' "
				+ "AND INDEX_NAME = 'idx_experience_logs_leaderboard' ORDER BY SEQ_IN_INDEX", String.class, schema);
	}

	private void run(Path file) throws Exception {
		try (Statement statement = scratchDb.getConnection().createStatement()) {
			for (String sql : MissionMigrationScriptTest.split(Files.readString(file, StandardCharsets.UTF_8))) {
				statement.execute(sql);
			}
		}
	}
}
