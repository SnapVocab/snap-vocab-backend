package vn.ptit.snapvocab.adminstats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import vn.ptit.snapvocab.support.LocalMySqlJpaTest;

/**
 * migration_admin_stats_v1.sql on LOCAL MySQL (admin_overview_backend.md BE-STATS-01): starting from today's tables
 * without the stats additions, the script adds scan_detections, topic_items.source_scan_id and the 8 indexes, re-runs
 * cleanly, and the entities validate. Same mechanics as StreakFreezeMigrationScriptTest.
 */
@LocalMySqlJpaTest
class AdminStatsMigrationScriptTest {

	private static final Path SCRIPT = Path.of("migration/migration_admin_stats_v1.sql");
	private static final String SUFFIX = "_stats_migration";
	/** Table → index the script adds. */
	private static final Map<String, List<String>> INDEXES = Map.of(
			"topic_items", List.of("idx_topic_items_source_scan", "idx_topic_items_created"),
			"learning_events", List.of("idx_learning_events_date_type"),
			"scan_requests", List.of("idx_scan_requests_created"),
			"coin_transactions", List.of("idx_coin_transactions_source_time"),
			"user_daily_missions", List.of("idx_udm_date_status"),
			"quizzes", List.of("idx_quizzes_status_completed"),
			"fsrs_records", List.of("idx_fsrs_state_stability"));
	private static final List<String> TABLES = List.of("topic_items", "learning_events", "scan_requests",
			"coin_transactions", "user_daily_missions", "quizzes", "fsrs_records", "scan_detections");

	@Autowired
	DataSource dataSource;
	@Autowired
	Environment env;

	private String source;
	private String scratch;
	private SingleConnectionDataSource scratchDb;

	@BeforeEach
	void createPreStatsSchema() throws SQLException {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		source = jdbc.queryForObject("SELECT DATABASE()", String.class);
		scratch = source + SUFFIX;
		jdbc.execute("DROP DATABASE IF EXISTS `" + scratch + "`");
		jdbc.execute("CREATE DATABASE `" + scratch + "`");
		Connection connection = dataSource.getConnection();
		connection.setCatalog(scratch);
		scratchDb = new SingleConnectionDataSource(connection, true);
		// The entity schema minus what this script adds = the deployed schema before it.
		INDEXES.forEach((table, indexes) -> {
			db().execute("CREATE TABLE `" + table + "` LIKE `" + source + "`.`" + table + "`");
			indexes.forEach(index -> db().execute("ALTER TABLE `" + table + "` DROP INDEX `" + index + "`"));
		});
		db().execute("ALTER TABLE topic_items DROP COLUMN source_scan_id");
	}

	@AfterEach
	void dropScratchDatabase() throws SQLException {
		scratchDb.getConnection().setCatalog(source);
		scratchDb.destroy();
		new JdbcTemplate(dataSource).execute("DROP DATABASE IF EXISTS `" + scratch + "`");
	}

	@Test
	void scriptAddsTableColumnAndIndexesReRunsAndValidates() throws Exception {
		Path client = mysqlClient();
		assumeTrue(client != null, "mysql client not found (SNAPVOCAB_MYSQL_CLIENT / PATH)");
		String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

		assertThat(mysql(client, script)).as("first run").startsWith("exit=0");
		assertThat(mysql(client, script)).as("re-run").startsWith("exit=0");

		INDEXES.forEach((table, indexes) -> assertThat(db().queryForList("SELECT DISTINCT INDEX_NAME FROM"
				+ " information_schema.STATISTICS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?", String.class, scratch,
				table)).as(table).containsAll(indexes));
		assertThat(db().queryForObject("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ?"
				+ " AND TABLE_NAME = 'scan_detections' AND COLUMN_NAME = 'has_word'", String.class, scratch))
				.isEqualTo("bit(1)");
		hibernateValidate();
	}

	/**
	 * The remote's topic_items has another collation than scan_requests: the new column, the new table and a column
	 * Hibernate already created with the table default all end up with scan_requests.id's collation.
	 */
	@Test
	void joinColumnsTakeTheCollationOfScanRequestsId() throws Exception {
		Path client = mysqlClient();
		assumeTrue(client != null, "mysql client not found (SNAPVOCAB_MYSQL_CLIENT / PATH)");
		String other = collation("scan_requests", "id").startsWith("utf8mb4_general") ? "utf8mb4_bin" : "utf8mb4_general_ci";
		db().execute("ALTER TABLE topic_items DEFAULT CHARSET = utf8mb4 COLLATE = " + other);
		String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

		assertThat(mysql(client, script)).startsWith("exit=0");
		assertThat(collation("topic_items", "source_scan_id")).isEqualTo(collation("scan_requests", "id"));
		assertThat(collation("scan_detections", "scan_request_id")).isEqualTo(collation("scan_requests", "id"));

		db().execute("ALTER TABLE topic_items MODIFY source_scan_id VARCHAR(36) COLLATE " + other + " NULL");
		assertThat(mysql(client, script)).startsWith("exit=0");
		assertThat(collation("topic_items", "source_scan_id")).isEqualTo(collation("scan_requests", "id"));
	}

	private String collation(String table, String column) {
		return db().queryForObject("SELECT COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ?"
				+ " AND TABLE_NAME = ? AND COLUMN_NAME = ?", String.class, scratch, table, column);
	}

	/** On the remote, the reconcile drops these and Hibernate update recreates them with the indexes. */
	@Test
	void scriptSkipsTablesNotCreatedYet() throws Exception {
		Path client = mysqlClient();
		assumeTrue(client != null, "mysql client not found (SNAPVOCAB_MYSQL_CLIENT / PATH)");
		db().execute("DROP TABLE learning_events");
		db().execute("DROP TABLE quizzes");

		assertThat(mysql(client, Files.readString(SCRIPT, StandardCharsets.UTF_8))).startsWith("exit=0");

		assertThat(db().queryForList("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?",
				String.class, scratch)).doesNotContain("learning_events", "quizzes").contains("scan_detections");
		assertThat(db().queryForList("SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS WHERE"
				+ " TABLE_SCHEMA = ?", String.class, scratch)).contains("idx_scan_requests_created", "idx_udm_date_status");
	}

	private void hibernateValidate() {
		LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
		emf.setDataSource(scratchDb);
		emf.setPackagesToScan("vn.ptit.snapvocab");
		emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
		emf.setJpaPropertyMap(Map.of(
				"hibernate.hbm2ddl.auto", "validate",
				"hibernate.physical_naming_strategy", CamelCaseToUnderscoresNamingStrategy.class.getName(),
				"hibernate.implicit_naming_strategy", SpringImplicitNamingStrategy.class.getName(),
				"hibernate.hbm2ddl.schema_filter_provider", MigratedTablesOnly.class.getName()));
		try {
			emf.afterPropertiesSet();
		} finally {
			emf.destroy();
		}
	}

	/** Validate only the tables this script touches; the scratch database has no other application table. */
	public static class MigratedTablesOnly implements SchemaFilterProvider, SchemaFilter {

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

	private String mysql(Path client, String script) throws Exception {
		URI uri = URI.create(env.getProperty("spring.datasource.url").substring("jdbc:".length()));
		Path options = Files.createTempFile("stats-mysql", ".cnf");
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

	private JdbcTemplate db() {
		return new JdbcTemplate(scratchDb);
	}
}
