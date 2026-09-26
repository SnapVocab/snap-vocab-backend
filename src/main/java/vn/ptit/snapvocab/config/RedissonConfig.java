package vn.ptit.snapvocab.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class RedissonConfig {

	@Bean(destroyMethod = "shutdown")
	public RedissonClient redissonClient(RedisProperties redisProperties) {
		Config config = new Config();

		JsonJacksonCodec codec = new JsonJacksonCodec();
		codec.getObjectMapper().registerModule(new JavaTimeModule());
		codec.getObjectMapper().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		config.setCodec(codec);

		boolean isSsl = redisProperties.getSsl() != null && redisProperties.getSsl().isEnabled();
		String prefix = isSsl ? "rediss://" : "redis://";

		var singleServerConfig = config.useSingleServer()
				.setAddress(prefix + redisProperties.getHost() + ":" + redisProperties.getPort())
				.setDatabase(redisProperties.getDatabase());

		if (redisProperties.getUsername() != null && !redisProperties.getUsername().isBlank()) {
			singleServerConfig.setUsername(redisProperties.getUsername());
		}

		if (redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank()) {
			singleServerConfig.setPassword(redisProperties.getPassword());
		}

		return Redisson.create(config);
	}

	@Bean
	public CacheManager cacheManager(
			RedissonClient redissonClient
	) {
		return new RedissonSpringCacheManager(
				redissonClient
		);
	}
}

