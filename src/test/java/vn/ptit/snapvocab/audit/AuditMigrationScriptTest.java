package vn.ptit.snapvocab.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 * admin_audit_log_backend.md §9.3 on LOCAL MySQL, run with the mysql client like every migration here: the script is
 * idempotent, passes Hibernate validate for AdminAuditLog, and its CHECKs reject an unknown action and invalid JSON.
 */
@LocalMySqlJpaTest
class AuditMigrationScriptTest {

	private static final Path SCRIPT = Path.of("migration/migration_admin_audit_logs_v1.sql");
	private static final String ROW = "INSERT INTO admin_audit_logs (created_at, vn_day, area, action, target_type,"
			+ " target_id, target_name, changes, economy_change, search_text) VALUES (NOW(6), CURDATE(), 'SHOP', ?,"
			+ " 'SHOP_ITEM', '1', 'x', ?, 0, 'x')";

	@Autowired
	DataSource dataSource;
	@Autowired
	Environment env;

	private String scratch;
	private SingleConnectionDataSource scratchDb;

	@BeforeEach
	void createScratchDatabase() throws SQLException {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		scratch = jdbc.queryForObject("SELECT DATABASE()", String.class) + "_aal_migration";
		jdbc.execute("DROP DATABASE IF EXISTS `" + scratch + "`");
		jdbc.execute("CREATE DATABASE `" + scratch + "`");
		Connection connection = dataSource.getConnection();
		connection.setCatalog(scratch);
		scratchDb = new SingleConnectionDataSource(connection, true);
	}

	@AfterEach
	void dropScratchDatabase() throws SQLException {
		// close() is suppressed: return the pooled connection ourselves, back on the test schema (Hikari keeps
		// the catalog, and the scratch one is dropped below).
		scratchDb.getConnection().setCatalog(scratch.substring(0, scratch.length() - "_aal_migration".length()));
		scratchDb.destroy();
		new JdbcTemplate(dataSource).execute("DROP DATABASE IF EXISTS `" + scratch + "`");
	}

	@Test
	void scriptIsIdempotentValidatesAndChecksBite() throws Exception {
		Path client = mysqlClient();
		assumeTrue(client != null, "mysql client not found (SNAPVOCAB_MYSQL_CLIENT / PATH)");

		assertThat(mysql(client, Files.readString(SCRIPT, StandardCharsets.UTF_8))).as("first run").startsWith("exit=0");
		assertThat(mysql(client, Files.readString(SCRIPT, StandardCharsets.UTF_8))).as("re-run").startsWith("exit=0");
		hibernateValidate();

		JdbcTemplate db = new JdbcTemplate(scratchDb);
		db.update(ROW, "SHOP_ITEM_UPDATED", "[{\"field\":\"price\",\"before\":900,\"after\":950}]");
		assertThatThrownBy(() -> db.update(ROW, "X", "[]")).hasMessageContaining("chk_aal_action");
		assertThatThrownBy(() -> db.update(ROW, "SHOP_ITEM_UPDATED", "not json")).hasMessageContaining("chk_aal_changes_json");
		assertThat(db.queryForObject("SELECT COUNT(*) FROM admin_audit_logs", Integer.class)).isOne();
	}

	// --- helpers (same mechanics as StreakFreezeMigrationScriptTest) ----------------------------------

	private void hibernateValidate() {
		LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
		emf.setDataSource(scratchDb);
		emf.setPackagesToScan("vn.ptit.snapvocab");
		emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
		emf.setJpaPropertyMap(Map.of(
				"hibernate.hbm2ddl.auto", "validate",
				"hibernate.physical_naming_strategy", CamelCaseToUnderscoresNamingStrategy.class.getName(),
				"hibernate.implicit_naming_strategy", SpringImplicitNamingStrategy.class.getName(),
				"hibernate.hbm2ddl.schema_filter_provider", AuditTableOnly.class.getName()));
		try {
			emf.afterPropertiesSet();
		} finally {
			emf.destroy();
		}
	}

	public static class AuditTableOnly implements SchemaFilterProvider, SchemaFilter {

		@Override
		public boolean includeNamespace(Namespace namespace) {
			return true;
		}

		@Override
		public boolean includeTable(Table table) {
			return table.getName().equals("admin_audit_logs");
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
		Path options = Files.createTempFile("aal-mysql", ".cnf");
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
}
