package vn.ptit.snapvocab.progress;

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
 * migration_streak_freeze_v1.sql on LOCAL MySQL, run with the mysql client like every migration here (see
 * MissionMigrationScriptTest): the deployed ENUM schema (cloned from remote 2026-10-21) becomes VARCHAR with its data
 * and generated column intact, gains streak_freeze_days, passes Hibernate validate, and re-runs cleanly.
 */
@LocalMySqlJpaTest
class StreakFreezeMigrationScriptTest {

	private static final Path SCRIPT = Path.of("migration/migration_streak_freeze_v1.sql");
	private static final List<String> TABLES = List.of("shop_items", "user_items", "streak_freeze_days", "badges");

	/** As deployed: Hibernate ddl-auto made the type columns MySQL ENUMs. */
	private static final List<String> DEPLOYED = List.of(
			"CREATE TABLE users (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB",
			"""
			CREATE TABLE shop_items (
			  id bigint NOT NULL AUTO_INCREMENT, created_at datetime(6) NOT NULL, updated_at datetime(6) NOT NULL,
			  is_active bit(1) DEFAULT NULL, name varchar(100) NOT NULL, price bigint DEFAULT NULL,
			  type enum('AVATAR_FRAME','THEME','XP_BOOSTER') NOT NULL, available_from datetime(6) DEFAULT NULL,
			  available_until datetime(6) DEFAULT NULL, boost_duration_minutes int DEFAULT NULL,
			  code varchar(64) NOT NULL, description varchar(500) DEFAULT NULL, frame_asset_key varchar(255) DEFAULT NULL,
			  icon_key varchar(255) DEFAULT NULL, max_quantity int DEFAULT NULL, published_at datetime(6) DEFAULT NULL,
			  purchasable bit(1) NOT NULL, sort_order int NOT NULL, status enum('ARCHIVED','DRAFT','PUBLISHED') NOT NULL,
			  theme_key enum('DEFAULT','FOREST_MINT','MIDNIGHT_NEON','SUNSET_AMBER') DEFAULT NULL, version bigint NOT NULL,
			  PRIMARY KEY (id), UNIQUE KEY uq_shop_items_code (code), UNIQUE KEY uq_shop_items_id_type (id, type),
			  UNIQUE KEY uq_shop_items_theme_key (theme_key),
			  KEY idx_shop_items_catalog (status, purchasable, type, sort_order)
			) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci""",
			"""
			CREATE TABLE user_items (
			  id bigint NOT NULL AUTO_INCREMENT, acquired_at datetime(6) NOT NULL, equipped bit(1) NOT NULL,
			  equipped_slot varchar(30) GENERATED ALWAYS AS (if((equipped = 1), item_type, NULL)) STORED,
			  item_type enum('AVATAR_FRAME','THEME','XP_BOOSTER') NOT NULL, quantity int NOT NULL,
			  updated_at datetime(6) NOT NULL, shop_item_id bigint NOT NULL, user_id bigint NOT NULL,
			  PRIMARY KEY (id), UNIQUE KEY uq_user_items_owner (user_id, shop_item_id),
			  UNIQUE KEY uq_user_items_equipped (user_id, equipped_slot), KEY idx_user_items_user (user_id),
			  CONSTRAINT fk_ui_user FOREIGN KEY (user_id) REFERENCES users (id),
			  CONSTRAINT fk_ui_item FOREIGN KEY (shop_item_id) REFERENCES shop_items (id)
			) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci""",
			"""
			CREATE TABLE badges (
			  id bigint NOT NULL AUTO_INCREMENT, created_at datetime(6) NOT NULL, updated_at datetime(6) NOT NULL,
			  is_active bit(1) NOT NULL DEFAULT b'1', code varchar(64) NOT NULL, description varchar(255) NOT NULL,
			  icon_key varchar(50) NOT NULL, metric varchar(30) NOT NULL, name varchar(100) NOT NULL,
			  sort_order int NOT NULL, threshold int NOT NULL, PRIMARY KEY (id), UNIQUE KEY uq_badges_code (code),
			  UNIQUE KEY uq_badges_metric_threshold (metric, threshold)
			) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

	@Autowired
	DataSource dataSource;
	@Autowired
	Environment env;

	private String scratch;
	private SingleConnectionDataSource scratchDb;

	@BeforeEach
	void createDeployedSchema() throws SQLException {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		scratch = jdbc.queryForObject("SELECT DATABASE()", String.class) + "_sf_migration";
		jdbc.execute("DROP DATABASE IF EXISTS `" + scratch + "`");
		jdbc.execute("CREATE DATABASE `" + scratch + "`");
		Connection connection = dataSource.getConnection();
		connection.setCatalog(scratch);
		scratchDb = new SingleConnectionDataSource(connection, true);
		DEPLOYED.forEach(db()::execute);
		db().update("INSERT INTO users (id) VALUES (1)");
		db().update("INSERT INTO shop_items (id, created_at, updated_at, name, price, type, code, purchasable, sort_order,"
				+ " status, theme_key, version) VALUES (1, NOW(6), NOW(6), 'Neon', 150, 'THEME', 'THEME_MIDNIGHT_NEON',"
				+ " 1, 0, 'PUBLISHED', 'MIDNIGHT_NEON', 0)");
		db().update("INSERT INTO user_items (acquired_at, equipped, item_type, quantity, updated_at, shop_item_id, user_id)"
				+ " VALUES (NOW(6), 1, 'THEME', 1, NOW(6), 1, 1)");
		db().update("INSERT INTO badges (created_at, updated_at, code, description, icon_key, metric, name, sort_order,"
				+ " threshold) VALUES (NOW(6), NOW(6), 'STREAK_7', 'Làm quiz hoặc ôn thẻ mỗi ngày, 7 ngày liên tiếp',"
				+ " 'flame', 'LONGEST_STREAK_DAYS', 'Một Tuần Bền Bỉ', 10, 7), (NOW(6), NOW(6), 'STREAK_30',"
				+ " 'Admin đã sửa', 'flame', 'LONGEST_STREAK_DAYS', 'Tháng Không Nghỉ', 11, 30)");
	}

	@AfterEach
	void dropScratchDatabase() throws SQLException {
		// close() is suppressed: return the pooled connection ourselves, back on the test schema (Hikari keeps
		// the catalog, and the scratch one is dropped below).
		scratchDb.getConnection().setCatalog(scratch.substring(0, scratch.length() - "_sf_migration".length()));
		scratchDb.destroy();
		new JdbcTemplate(dataSource).execute("DROP DATABASE IF EXISTS `" + scratch + "`");
	}

	@Test
	void deployedEnumSchemaBecomesVarcharKeepsItsDataAndValidates() throws Exception {
		Path client = mysqlClient();
		assumeTrue(client != null, "mysql client not found (SNAPVOCAB_MYSQL_CLIENT / PATH)");

		assertThat(mysql(client, Files.readString(SCRIPT, StandardCharsets.UTF_8))).as("first run").startsWith("exit=0");
		assertThat(mysql(client, Files.readString(SCRIPT, StandardCharsets.UTF_8))).as("re-run").startsWith("exit=0");

		assertThat(columnType("shop_items", "type")).isEqualTo("varchar(30)");
		assertThat(columnType("shop_items", "theme_key")).isEqualTo("varchar(50)");
		assertThat(columnType("user_items", "item_type")).isEqualTo("varchar(30)");
		// Data and the generated equip slot survive; the new type is now storable.
		assertThat(db().queryForMap("SELECT item_type, equipped_slot FROM user_items"))
				.containsEntry("item_type", "THEME").containsEntry("equipped_slot", "THEME");
		db().update("INSERT INTO shop_items (id, created_at, updated_at, name, type, code, purchasable, sort_order,"
				+ " status, version) VALUES (2, NOW(6), NOW(6), 'Freeze', 'STREAK_FREEZE', 'STREAK_FREEZE', 0, 0, 'DRAFT', 0)");
		db().update("INSERT INTO streak_freeze_days (user_id, frozen_date, shop_item_id, created_at)"
				+ " VALUES (1, '2026-10-20', 2, NOW(6))");
		// Seeded copy is rewritten; an Admin edit is left alone (PD-SF-06).
		assertThat(db().queryForList("SELECT description FROM badges ORDER BY sort_order", String.class))
				.containsExactly("Làm quiz hoặc ôn thẻ 7 ngày trong một chuỗi không đứt", "Admin đã sửa");

		// Badges Admin P2 ships next: its version column, then the entity schema validates (badges.md §7.1).
		String badgesV2 = Files.readString(Path.of("migration/migration_badges_v2.sql"), StandardCharsets.UTF_8);
		assertThat(mysql(client, badgesV2)).as("badges v2").startsWith("exit=0");
		assertThat(mysql(client, badgesV2)).as("badges v2 re-run").startsWith("exit=0");
		assertThat(db().queryForList("SELECT version FROM badges", Long.class)).containsExactly(0L, 0L);

		// THEME is removed next (2026-11-02): its owned and catalog rows, then theme_key itself.
		String removeTheme = Files.readString(Path.of("migration/migration_remove_theme_v1.sql"), StandardCharsets.UTF_8);
		assertThat(mysql(client, removeTheme)).as("remove theme").startsWith("exit=0");
		assertThat(mysql(client, removeTheme)).as("remove theme re-run").startsWith("exit=0");
		assertThat(db().queryForObject("SELECT COUNT(*) FROM user_items", Integer.class)).isZero();
		assertThat(db().queryForList("SELECT type FROM shop_items", String.class)).containsExactly("STREAK_FREEZE");
		assertThat(db().queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ?"
				+ " AND TABLE_NAME = 'shop_items' AND COLUMN_NAME = 'theme_key'", Integer.class, scratch)).isZero();

		// Then badge rewards (PD-BDG-14) and badge art on MinIO (image_key replaces icon_key). Rewards also alter
		// user_badges, which this scratch schema lacks (and does not validate): a bare one is enough.
		db().execute("CREATE TABLE user_badges (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,"
				+ " badge_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL)");
		for (String next : List.of("migration_badges_rewards_v3.sql", "migration_badges_image_v4.sql")) {
			String sql = Files.readString(Path.of("migration", next), StandardCharsets.UTF_8);
			assertThat(mysql(client, sql)).as(next).startsWith("exit=0");
			assertThat(mysql(client, sql)).as(next + " re-run").startsWith("exit=0");
		}

		hibernateValidate();
	}

	// --- helpers (same mechanics as MissionMigrationScriptTest) ----------------------------------

	private String columnType(String table, String column) {
		return db().queryForObject("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ?"
				+ " AND TABLE_NAME = ? AND COLUMN_NAME = ?", String.class, scratch, table, column);
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
		Path options = Files.createTempFile("sf-mysql", ".cnf");
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
