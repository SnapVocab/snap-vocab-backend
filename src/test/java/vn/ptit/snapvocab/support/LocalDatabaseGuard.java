package vn.ptit.snapvocab.support;

import java.net.URI;
import java.util.Set;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class LocalDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1");

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		String url = context.getEnvironment().getProperty("spring.datasource.url");
		String host = host(url);
		if (host == null || !LOCAL_HOSTS.contains(host.toLowerCase())) {
			throw new IllegalStateException(
					"ABORT: test datasource host must be localhost/127.0.0.1 but url is " + url);
		}
	}

	static String host(String jdbcUrl) {
		if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
			return null;
		}
		try {
			return URI.create(jdbcUrl.substring("jdbc:".length())).getHost();
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
