package vn.ptit.snapvocab.mission;

import java.util.Map;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/**
 * Explicit staging preflight: validates ALL entities against an existing schema. No Spring application context,
 * initializer, seed, migration or HTTP server is started, and Hibernate is only allowed to validate.
 * The target is provided through the same dedicated STAGING_DB_* variables as application-staging.yaml.
 */
@EnabledIfEnvironmentVariable(named = "STAGING_DB_URL", matches = "jdbc:(mysql|mariadb)://.+")
class StagingSchemaValidationTest {

	@Test
	void allEntitiesMatchTheExistingStagingSchema() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl(required("STAGING_DB_URL"));
		dataSource.setUsername(required("STAGING_DB_USERNAME"));
		dataSource.setPassword(required("STAGING_DB_PASSWORD"));
		LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
		emf.setDataSource(dataSource);
		emf.setPackagesToScan("vn.ptit.snapvocab");
		emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
		emf.setJpaPropertyMap(Map.of(
				"hibernate.hbm2ddl.auto", "validate",
				"hibernate.physical_naming_strategy", CamelCaseToUnderscoresNamingStrategy.class.getName(),
				"hibernate.implicit_naming_strategy", SpringImplicitNamingStrategy.class.getName()));
		try {
			emf.afterPropertiesSet();
		} finally {
			emf.destroy();
		}
	}

	private static String required(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(name + " is required for explicit staging validation");
		}
		return value;
	}
}
