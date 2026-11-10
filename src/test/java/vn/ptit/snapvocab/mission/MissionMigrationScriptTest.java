package vn.ptit.snapvocab.mission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.model.relational.Sequence;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.SchemaFilter;
import org.hibernate.tool.schema.spi.SchemaFilterProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import vn.ptit.snapvocab.support.LocalMySqlJpaTest;

/**
 * REL-DM-01 checks on LOCAL MySQL: migration_daily_mission_v1.sql builds the same 5 tables Hibernate builds from the
 * entities and passes Hibernate's own {@code validate}; its CHECKs (which validate ignores) bite; a catalog migration
 * that breaks the §4.2 invariant rolls back as a whole — both through JDBC and through the mysql client.
 * The script runs in a throw-away database on the same local server; the test schema is only read.
 */
@LocalMySqlJpaTest
class MissionMigrationScriptTest {

	private static final List<String> TABLES = List.of("mission_templates", "user_daily_missions",
			"user_daily_mission_progress_logs", "user_daily_mission_claims", "user_word_credits");
	private static final Path SCRIPT = Path.of("migration/migration_daily_mission_v1.sql");
	private static final Path CATALOG_TEMPLATE = Path.of("migration/migration_daily_mission_catalog_template.sql");
	private static final Path TRIGGERS_SCRIPT = Path.of("migration/migration_daily_mission_triggers_v2.sql");

	@Autowired
	DataSource dataSource;
	@Autowired
	Environment env;

	private String entitySchema;
	private String scratch;
	private SingleConnectionDataSource scratchDb;

	@BeforeEach
	void createScratchDatabase() throws SQLException {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		entitySchema = jdbc.queryForObject("SELECT DATABASE()", String.class);
		scratch = entitySchema + "_dm_migration";
		jdbc.execute("DROP DATABASE IF EXISTS `" + scratch + "`");
		jdbc.execute("CREATE DATABASE `" + scratch + "`");
		Connection connection = dataSource.getConnection();
		connection.setCatalog(scratch);
		scratchDb = new SingleConnectionDataSource(connection, true);
		scratch().execute("CREATE TABLE users (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB");
	}

	@AfterEach
	void dropScratchDatabase() throws SQLException {
		// close() is suppressed: return the pooled connection ourselves, back on the test schema (Hikari keeps
		// the catalog, and the scratch one is dropped below).
		scratchDb.getConnection().setCatalog(entitySchema);
		scratchDb.destroy();

		new JdbcTemplate(dataSource).execute("DROP DATABASE IF EXISTS `" + scratch + "`");
	}

	/**
	 * The one user_daily_missions index migration_admin_stats_v1.sql adds (tested whole in
	 * AdminStatsMigrationScriptTest; it also alters tables this scratch schema does not have).
	 */
	private void addAdminStatsIndex() {
		scratch().execute("ALTER TABLE user_daily_missions ADD INDEX idx_udm_date_status (mission_date, status)");
	}

	@Test
	void scriptIsIdempotentAndMatchesTheEntitySchema() throws Exception {
		run(read(SCRIPT));
		run(read(SCRIPT));
		addAdminStatsIndex();

		JdbcTemplate info = new JdbcTemplate(dataSource);
		for (String table : TABLES) {
			assertThat(columns(info, scratch, table)).as(table + " columns").isEqualTo(columns(info, entitySchema, table));
			assertThat(constraints(info, scratch, table)).as(table + " UNIQUE/CHECK")
					.isEqualTo(constraints(info, entitySchema, table));
		}
	}

	/** A mission table from another schema version (the team's remote DB had UUID ids) stops the script untouched. */
	@Test
	void scriptRefusesAnIncompatibleExistingMissionTable() throws Exception {
		scratch().execute("CREATE TABLE mission_templates (id BINARY(16) NOT NULL PRIMARY KEY, code VARCHAR(64)) ENGINE=InnoDB");

		assertThatThrownBy(() -> run(read(SCRIPT))).isInstanceOf(SQLException.class)
				.hasMessageContaining("DM_SCHEMA_CONFLICT").hasMessageContaining("mission_templates");
		assertThat(new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM information_schema.TABLES "
				+ "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'user_daily_missions'", Integer.class, scratch)).isZero();
	}

	@Test
	void triggersMigrationWidensADeployedSchemaAndIsIdempotent() throws Exception { // PD-DM-07
		run(read(SCRIPT));
		JdbcTemplate db = scratch();
		String old = "('QUIZ_COMPLETED', 'LEARNING_ACTIVITY_RECORDED', 'TOPIC_ITEM_CREATED', 'SHOP_ITEM_PURCHASED', 'BOOSTER_USED')";
		for (String table : List.of("mission_templates", "user_daily_missions")) { // the pre-SRS deployed CHECK
			String chk = "chk_" + table + "_trigger_event_values"; // explicit names since master 2026-10-24
			db.execute("ALTER TABLE " + table + " DROP CHECK " + chk);
			db.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + chk + " CHECK (trigger_event IN " + old + ")");
		}
		db.execute("ALTER TABLE mission_templates DROP CHECK chk_mission_templates_eligibility_rule_values");
		db.execute("ALTER TABLE mission_templates ADD CONSTRAINT chk_mission_templates_eligibility_rule_values CHECK (eligibility_rule IN "
				+ "('NONE', 'HAS_IDLE_XP_BOOSTER', 'HAS_PURCHASABLE_ITEM'))");
		assertRejected(db, template("SRS", "'SRS'", "'SRS_REVIEW_COMPLETED'", "NULL", "NULL", 1, 1, "NULL", "NULL"));

		run(read(TRIGGERS_SCRIPT));
		run(read(TRIGGERS_SCRIPT));
		addAdminStatsIndex();

		JdbcTemplate info = new JdbcTemplate(dataSource);
		for (String table : TABLES) {
			assertThat(constraints(info, scratch, table)).as(table + " UNIQUE/CHECK")
					.isEqualTo(constraints(info, entitySchema, table));
		}
		db.update(template("SRS", "'SRS'", "'SRS_REVIEW_COMPLETED'", "NULL", "NULL", 1, 1, "NULL", "NULL"));
		db.update(template("SCAN", "'SCAN'", "'SCAN_WORD_SAVED'", "NULL", "NULL", 1, 1, "NULL", "NULL"));
		db.update("UPDATE mission_templates SET eligibility_rule = 'HAS_STUDY_CARDS_FOR_TARGET' WHERE code = 'SRS'");
	}

	@Test
	void checksRejectInvalidRowsOnTheMigratedAndTheEntitySchema() throws Exception {
		run(read(SCRIPT));
		for (JdbcTemplate db : List.of(scratch(), new JdbcTemplate(dataSource))) {
			assertRejected(db, template("QUIZ_ON_SHOP", "'SHOP'", "'SHOP_ITEM_PURCHASED'", "80", "NULL", 1, 1, "NULL", "NULL"));
			assertRejected(db, template("NO_REWARD", "'QUIZ'", "'QUIZ_COMPLETED'", "NULL", "NULL", 0, 0, "NULL", "NULL"));
			assertRejected(db, template("ITEM_NO_FALLBACK", "'QUIZ'", "'QUIZ_COMPLETED'", "NULL", "NULL", 1, 1, "'X'", "NULL"));
			assertRejected(db, template("BAD_SIZE", "'QUIZ'", "'QUIZ_COMPLETED'", "NULL", "7", 1, 1, "NULL", "NULL"));
			db.update(template("OK_TEMPLATE", "'QUIZ'", "'QUIZ_COMPLETED'", "80", "5", 1, 1, "NULL", "NULL"));
			long templateId = db.queryForObject("SELECT id FROM mission_templates WHERE code = 'OK_TEMPLATE'", Long.class);
			db.update(db.getDataSource() == scratchDb ? "INSERT INTO users () VALUES ()"
					: "INSERT INTO users (activated, first_name, password_hash, email, created_at, updated_at) "
							+ "VALUES (1, 'DM', REPEAT('x', 60), CONCAT('dm-it-mig-', UUID(), '@test.local'), NOW(6), NOW(6))");
			long userId = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

			assertRejected(db, mission(userId, templateId, 6, "'IN_PROGRESS'", 0, "NULL", "NULL"));
			assertRejected(db, mission(userId, templateId, 1, "'IN_PROGRESS'", 2, "NULL", "NULL"));
			assertRejected(db, mission(userId, templateId, 1, "'COMPLETED'", 1, "NULL", "NULL"));
			assertRejected(db, mission(userId, templateId, 1, "'CLAIMED'", 1, "NOW(6)", "NULL"));
			assertRejected(db, mission(userId, templateId, 1, "'EXPIRED'", 0, "NULL", "NULL"));
			db.update(mission(userId, templateId, 1, "'CLAIMED'", 1, "NOW(6)", "NOW(6)"));

			db.update("DELETE FROM user_daily_missions WHERE user_id = ?", userId);
			db.update("DELETE FROM mission_templates WHERE code = 'OK_TEMPLATE'");
			db.update("DELETE FROM users WHERE id = ?", userId);
		}
	}

	@Test
	void catalogMigrationThatBreaksTheInvariantRollsBackEveryChange() throws Exception {
		run(read(SCRIPT));
		JdbcTemplate db = scratch();
		for (String code : List.of("C1", "C2", "C3", "C4")) {
			db.update(template(code, "'QUIZ'", "'QUIZ_COMPLETED'", "NULL", "NULL", 1, 1, "NULL", "NULL"));
		}
		db.update(template("KEEP", "'RETENTION'", "'LEARNING_ACTIVITY_RECORDED'", "NULL", "NULL", 1, 1, "NULL", "NULL"));
		db.execute("CALL dm_assert_catalog_invariant()");

		String breaking = catalogChange("""
				UPDATE `mission_templates` SET `weight` = 5 WHERE `code` = 'C1';
				UPDATE `mission_templates` SET `is_active` = 0 WHERE `code` = 'KEEP';
				""");
		assertThatThrownBy(() -> run(breaking)).isInstanceOf(SQLException.class)
				.hasMessageContaining("MISSION_POOL_INSUFFICIENT")
				.satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("45000"));
		assertThat(db.queryForObject("SELECT weight FROM mission_templates WHERE code = 'C1'", Integer.class)).isEqualTo(1);
		assertThat(db.queryForObject("SELECT is_active FROM mission_templates WHERE code = 'KEEP'", Boolean.class)).isTrue();

		run(catalogChange("UPDATE `mission_templates` SET `weight` = 3 WHERE `code` = 'C2';"));
		assertThat(db.queryForObject("SELECT weight FROM mission_templates WHERE code = 'C2'", Integer.class)).isEqualTo(3);
		assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = ? "
				+ "AND ROUTINE_NAME = 'dm_tmp_catalog_change'", Integer.class, scratch)).isZero();
	}

	/** Hibernate's own schema validation (ddl-auto: validate) against the schema the script built, mission tables only. */
	@Test
	void hibernateValidateAcceptsTheMigratedSchemaAndDetectsDrift() throws Exception {
		run(read(SCRIPT));

		hibernateValidate();

		scratch().execute("ALTER TABLE user_daily_missions DROP COLUMN description");
		assertThatThrownBy(this::hibernateValidate).hasStackTraceContaining("description");
	}

	/**
	 * The repo's migration convention is a {@code migration_*.sql} file run with the mysql client (no Flyway/Liquibase).
	 * Runs both scripts exactly that way. Skipped when no client is found: set SNAPVOCAB_MYSQL_CLIENT or put mysql on PATH.
	 */
	@Test
	void scriptsBehaveTheSameWhenRunWithTheMysqlClient() throws Exception {
		Path client = mysqlClient();
		assumeTrue(client != null, "mysql client not found (SNAPVOCAB_MYSQL_CLIENT / PATH)");

		assertThat(mysql(client, read(SCRIPT))).as("first run").startsWith("exit=0");
		assertThat(mysql(client, read(SCRIPT))).as("idempotent re-run").startsWith("exit=0");
		hibernateValidate();
		JdbcTemplate db = scratch();
		for (String code : List.of("C1", "C2", "C3", "C4")) {
			db.update(template(code, "'QUIZ'", "'QUIZ_COMPLETED'", "NULL", "NULL", 1, 1, "NULL", "NULL"));
		}
		db.update(template("KEEP", "'RETENTION'", "'LEARNING_ACTIVITY_RECORDED'", "NULL", "NULL", 1, 1, "NULL", "NULL"));

		String broken = mysql(client, catalogChange("""
				UPDATE `mission_templates` SET `weight` = 5 WHERE `code` = 'C1';
				UPDATE `mission_templates` SET `is_active` = 0 WHERE `code` = 'KEEP';
				"""));
		assertThat(broken).doesNotStartWith("exit=0").contains("45000").contains("MISSION_POOL_INSUFFICIENT");
		assertThat(db.queryForObject("SELECT weight FROM mission_templates WHERE code = 'C1'", Integer.class)).isEqualTo(1);
		assertThat(db.queryForObject("SELECT is_active FROM mission_templates WHERE code = 'KEEP'", Boolean.class)).isTrue();

		assertThat(mysql(client, catalogChange("UPDATE `mission_templates` SET `weight` = 3 WHERE `code` = 'C2';")))
				.startsWith("exit=0");
		assertThat(db.queryForObject("SELECT weight FROM mission_templates WHERE code = 'C2'", Integer.class)).isEqualTo(3);
	}

	// --- helpers ---------------------------------------------------------------------------------

	/** Local rehearsal only: clone the existing non-mission schema, apply real SQL, then validate EVERY entity. */
	@Test
	void migrationOnACompleteBaselineSchemaPassesFullValidateAndDetectsNonMissionDrift() throws Exception {
		JdbcTemplate source = new JdbcTemplate(dataSource);
		scratch().execute("DROP TABLE users");
		scratch().execute("SET FOREIGN_KEY_CHECKS = 0");
		try {
			for (String table : source.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES "
					+ "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'", String.class, entitySchema)) {
				if (TABLES.contains(table)) {
					continue;
				}
				String quoted = "`" + table.replace("`", "``") + "`";
				String ddl = (String) source.queryForMap("SHOW CREATE TABLE " + quoted).get("Create Table");
				scratch().execute(ddl);
			}
		} finally {
			scratch().execute("SET FOREIGN_KEY_CHECKS = 1");
		}
		Path client = mysqlClient();
		assumeTrue(client != null, "mysql client required for full-schema migration rehearsal");
		assertThat(mysql(client, read(SCRIPT))).startsWith("exit=0");
		// Databases created before JsonMapConverter still have a json column; it must keep validating.
		scratch().execute("ALTER TABLE learning_events MODIFY metadata JSON");
		hibernateValidate(false);
		scratch().execute("ALTER TABLE shop_items MODIFY purchasable INT NOT NULL");
		assertThatThrownBy(() -> hibernateValidate(false)).hasStackTraceContaining("purchasable");
	}

	private void hibernateValidate() {
		hibernateValidate(true);
	}

	private void hibernateValidate(boolean missionOnly) {
		LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
		emf.setDataSource(scratchDb);
		emf.setPackagesToScan("vn.ptit.snapvocab");
		emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
		Map<String, Object> properties = new HashMap<>(Map.of(
				"hibernate.hbm2ddl.auto", "validate",
				"hibernate.physical_naming_strategy", CamelCaseToUnderscoresNamingStrategy.class.getName(),
				"hibernate.implicit_naming_strategy", SpringImplicitNamingStrategy.class.getName()));
		if (missionOnly) {
			properties.put("hibernate.hbm2ddl.schema_filter_provider", MissionTablesOnly.class.getName());
		}
		emf.setJpaPropertyMap(properties);
		try {
			emf.afterPropertiesSet();
		} finally {
			emf.destroy();
		}
	}

	/** Validate only the 5 mission tables; the scratch database has no other application table. */
	public static class MissionTablesOnly implements SchemaFilterProvider, SchemaFilter {

		@Override
		public boolean includeNamespace(Namespace namespace) {
			return true;
		}

		@Override
		public boolean includeTable(Table table) {
			return TABLES.contains(table.getName());
		}

		@Override
		public boolean includeSequence(Sequence sequence) {
			return false;
		}

		@Override
		public SchemaFilter getCreateFilter() {
			return this;
		}

		@Override
		public SchemaFilter getDropFilter() {
			return this;
		}

		@Override
		public SchemaFilter getTruncatorFilter() {
			return this;
		}

		@Override
		public SchemaFilter getMigrateFilter() {
			return this;
		}

		@Override
		public SchemaFilter getValidateFilter() {
			return this;
		}
	}

	private static Path mysqlClient() {
		String configured = System.getenv("SNAPVOCAB_MYSQL_CLIENT");
		if (configured != null && Files.isExecutable(Path.of(configured))) {
			return Path.of(configured);
		}
		for (String dir : System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator)) {
			for (String name : List.of("mysql", "mysql.exe")) {
				Path candidate = Path.of(dir, name);
				if (Files.isExecutable(candidate)) {
					return candidate;
				}
			}
		}
		return null;
	}

	/** Pipes {@code script} into the mysql client against the scratch database; returns "exit=N" plus its output. */
	private String mysql(Path client, String script) throws Exception {
		URI uri = URI.create(env.getProperty("spring.datasource.url").substring("jdbc:".length()));
		Path options = Files.createTempFile("dm-mysql", ".cnf");
		try {
			Files.writeString(options, "[client]\npassword=" + env.getProperty("spring.datasource.password") + "\n");
			Process process = new ProcessBuilder(client.toString(), "--defaults-extra-file=" + options,
					"--host=" + uri.getHost(), "--port=" + (uri.getPort() < 0 ? 3306 : uri.getPort()),
					"--user=" + env.getProperty("spring.datasource.username"), "--default-character-set=utf8mb4",
					"--database=" + scratch).redirectErrorStream(true).start();
			try (var stdin = process.getOutputStream()) {
				stdin.write(script.getBytes(StandardCharsets.UTF_8));
			}
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
			return "exit=" + process.exitValue() + "\n" + output;
		} finally {
			Files.deleteIfExists(options);
		}
	}

	private JdbcTemplate scratch() {
		return new JdbcTemplate(scratchDb);
	}

	private static String read(Path file) throws Exception {
		return Files.readString(file, StandardCharsets.UTF_8);
	}

	/** The catalog template with {@code dml} in place of its example block. */
	private static String catalogChange(String dml) throws Exception {
		String template = read(CATALOG_TEMPLATE);
		int start = template.indexOf("-- >>>");
		int end = template.indexOf("-- <<<");
		return template.substring(0, start) + dml + template.substring(template.indexOf('\n', end) + 1);
	}

	/** Runs a mysql-client style script: {@code --} comments, {@code ;} terminators and {@code DELIMITER} switches. */
	private void run(String script) throws SQLException {
		try (Statement statement = scratchDb.getConnection().createStatement()) {
			for (String sql : split(script)) {
				statement.execute(sql);
			}
		}
	}

	static List<String> split(String script) {
		List<String> statements = new ArrayList<>();
		String delimiter = ";";
		StringBuilder current = new StringBuilder();
		for (String line : script.split("\\R")) {
			String trimmed = line.trim();
			if (current.isEmpty() && (trimmed.isEmpty() || trimmed.startsWith("--"))) {
				continue;
			}
			if (trimmed.toUpperCase().startsWith("DELIMITER ")) {
				delimiter = trimmed.substring("DELIMITER ".length()).trim();
				continue;
			}
			current.append(line).append('\n');
			if (trimmed.endsWith(delimiter)) {
				String sql = current.toString().trim();
				statements.add(sql.substring(0, sql.length() - delimiter.length()).trim());
				current.setLength(0);
			}
		}
		return statements;
	}

	private static List<Map<String, Object>> columns(JdbcTemplate info, String schema, String table) {
		return info.queryForList("SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM "
				+ "information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY COLUMN_NAME", schema, table);
	}

	/**
	 * Compare index names/columns and CHECK expressions. Hibernate-generated CHECK names can differ from the explicit
	 * names in migration scripts without changing the constraints enforced by the database.
	 */
	private static List<Map<String, Object>> constraints(JdbcTemplate info, String schema, String table) {
		List<Map<String, Object>> result = new ArrayList<>(info.queryForList("SELECT INDEX_NAME, NON_UNIQUE, "
				+ "GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols FROM information_schema.STATISTICS "
				+ "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND (NON_UNIQUE = 0 OR INDEX_NAME LIKE 'idx\\_%') "
				+ "GROUP BY INDEX_NAME, NON_UNIQUE ORDER BY INDEX_NAME", schema, table));
		result.addAll(info.queryForList("SELECT c.CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS c "
				+ "JOIN information_schema.TABLE_CONSTRAINTS t ON t.CONSTRAINT_SCHEMA = c.CONSTRAINT_SCHEMA "
				+ "AND t.CONSTRAINT_NAME = c.CONSTRAINT_NAME WHERE t.TABLE_SCHEMA = ? AND t.TABLE_NAME = ? "
				+ "AND t.CONSTRAINT_TYPE = 'CHECK' ORDER BY c.CHECK_CLAUSE", schema, table));
		return result;
	}

	private static void assertRejected(JdbcTemplate db, String insert) {
		assertThatThrownBy(() -> db.update(insert)).as(insert).isInstanceOf(DataAccessException.class)
				.hasMessageContaining("onstraint");
	}

	private static String template(String code, String category, String trigger, String minAccuracy, String size,
			int coin, int xp, String itemCode, String fallback) {
		return "INSERT INTO mission_templates (code, name, description, category, trigger_event, "
				+ "min_quiz_accuracy_percent, quiz_requested_count, target_value, reward_coin, reward_xp, reward_item_code, "
				+ "reward_item_fallback_coin, weight, eligibility_rule, is_active, version, created_at, updated_at) VALUES ('"
				+ code + "', 'n', 'd', " + category + ", " + trigger + ", " + minAccuracy + ", " + size + ", 1, " + coin
				+ ", " + xp + ", " + itemCode + ", " + fallback + ", 1, 'NONE', 1, 0, NOW(6), NOW(6))";
	}

	private static String mission(long userId, long templateId, int slot, String status, int progress, String completedAt,
			String claimedAt) {
		return "INSERT INTO user_daily_missions (user_id, mission_template_id, mission_date, slot, code, name, description, "
				+ "category, trigger_event, target_value, reward_coin, reward_xp, current_progress, status, completed_at, "
				+ "claimed_at, created_at, updated_at) VALUES (" + userId + ", " + templateId + ", CURDATE(), " + slot
				+ ", 'OK_TEMPLATE', 'n', 'd', 'QUIZ', 'QUIZ_COMPLETED', 1, 1, 1, " + progress + ", " + status + ", "
				+ completedAt + ", " + claimedAt + ", NOW(6), NOW(6))";
	}
}
