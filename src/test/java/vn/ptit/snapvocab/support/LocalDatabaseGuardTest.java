package vn.ptit.snapvocab.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class LocalDatabaseGuardTest {

	private static GenericApplicationContext contextWithUrl(String url) {
		GenericApplicationContext context = new GenericApplicationContext();
		MockEnvironment env = new MockEnvironment();
		if (url != null) {
			env.setProperty("spring.datasource.url", url);
		}
		context.setEnvironment(env);
		return context;
	}

	@Test
	void extractsHostFromJdbcUrls() {
		assertThat(LocalDatabaseGuard.host("jdbc:mysql://127.0.0.1:3306/snapvocab_it")).isEqualTo("127.0.0.1");
		assertThat(LocalDatabaseGuard.host("jdbc:mariadb://localhost/x")).isEqualTo("localhost");
		assertThat(LocalDatabaseGuard.host(null)).isNull();
	}

	@Test
	void abortsForTheRemoteDevDatabase() {
		assertThatThrownBy(() -> new LocalDatabaseGuard()
				.initialize(contextWithUrl("jdbc:mariadb://140.245.123.111:3306/snapvocab")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ABORT");
	}

	@Test
	void abortsWhenNoDatasourceIsConfigured() {
		assertThatThrownBy(() -> new LocalDatabaseGuard().initialize(contextWithUrl(null)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void allowsLocalDatabases() {
		assertThatCode(() -> new LocalDatabaseGuard()
				.initialize(contextWithUrl("jdbc:mysql://localhost:3306/snapvocab_it"))).doesNotThrowAnyException();
	}
}

