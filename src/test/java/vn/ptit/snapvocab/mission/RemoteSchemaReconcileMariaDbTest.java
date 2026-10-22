package vn.ptit.snapvocab.mission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/**
 * REL-DM-01 release rehearsal on MariaDB 10.11 (the remote's engine): the remote schema snapshot of 2026-10-22,
 * migration_remote_reconcile_v1.sql, the pending migrations in release order, Hibernate update, then a full
 * Hibernate validate. Opt-in: set SNAPVOCAB_MARIADB_URL, e.g. {@code jdbc:mariadb://localhost:3307/?user=root&password=123456}.
 */
class RemoteSchemaReconcileMariaDbTest {

	private static final String DB = "snapvocab_reconcile_it";
	private static final Path SNAPSHOT = Path.of("src/test/resources/migration/remote_schema_2026-10-22.sql");
	private static final Path RECONCILE = Path.of("migration/migration_remote_reconcile_v1.sql");
	private static final List<String> RELEASE = List.of("migration_remote_reconcile_v1.sql",
			"migration_notifications_v1.sql", "migration_notifications_v2.sql", "migration_admin_audit_logs_v1.sql",
			"migration_badges_v1.sql", "migration_badges_v2.sql", "migration_streak_freeze_v1.sql",
			"migration_badges_streak_copy_v2.sql", "migration_daily_mission_v1.sql",
			"migration_daily_mission_triggers_v2.sql");

	private SingleConnectionDataSource db;

	@BeforeEach
	void loadRemoteSnapshot() throws Exception {
		String url = System.getenv("SNAPVOCAB_MARIADB_URL");
		assumeTrue(url != null, "SNAPVOCAB_MARIADB_URL not set");
		SingleConnectionDataSource server = new SingleConnectionDataSource(url, true);
		new JdbcTemplate(server).execute("DROP DATABASE IF EXISTS " + DB);
		new JdbcTemplate(server).execute("CREATE DATABASE " + DB + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
		server.destroy();
		db = new SingleConnectionDataSource(url.replace("/?", "/" + DB + "?"), true);
		run(Files.readString(SNAPSHOT, StandardCharsets.UTF_8));
	}

	@AfterEach
	void close() {
		if (db != null) {
			db.destroy();
		}
	}

	@Test
	void releaseOrderTurnsTheRemoteSchemaIntoTheEntitySchema() throws Exception {
		for (String script : RELEASE) {
			run(Files.readString(Path.of("migration", script), StandardCharsets.UTF_8));
		}
		run(Files.readString(RECONCILE, StandardCharsets.UTF_8)); // idempotent: nothing left to drop
		assertThat(tables()).doesNotContain("mission_configs", "quiz_attempts", "reward_grants");

		hibernate("update");
		hibernate("validate");

		assertThat(new JdbcTemplate(db).queryForObject("SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE "
				+ "TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mission_templates' AND COLUMN_NAME = 'id'", String.class))
				.isEqualTo("bigint");
	}

	@Test
	void aTableWithRowsStopsTheScriptBeforeAnyDrop() throws Exception {
		JdbcTemplate jdbc = new JdbcTemplate(db);
		jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
		jdbc.update("INSERT INTO mission_config_state (id, updated_at, active_config_id) VALUES (1, NOW(6), 1)");
		jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
		List<String> before = tables();

		assertThatThrownBy(() -> run(Files.readString(RECONCILE, StandardCharsets.UTF_8)))
				.isInstanceOf(SQLException.class).hasMessageContaining("RR_NOT_EMPTY: mission_config_state");
		assertThat(tables()).isEqualTo(before);
	}

	private void hibernate(String mode) {
		LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
		emf.setDataSource(db);
		emf.setPackagesToScan("vn.ptit.snapvocab");
		emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
		emf.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", mode,
				"hibernate.physical_naming_strategy", CamelCaseToUnderscoresNamingStrategy.class.getName(),
				"hibernate.implicit_naming_strategy", SpringImplicitNamingStrategy.class.getName()));
		try {
			emf.afterPropertiesSet();
		} finally {
			emf.destroy();
		}
	}

	private List<String> tables() {
		return new JdbcTemplate(db).queryForList("SELECT TABLE_NAME FROM information_schema.TABLES "
				+ "WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME", String.class);
	}

	private void run(String script) throws SQLException {
		Connection connection = db.getConnection();
		try (Statement statement = connection.createStatement()) {
			for (String sql : MissionMigrationScriptTest.split(script)) {
				statement.execute(sql);
			}
		}
	}
}
