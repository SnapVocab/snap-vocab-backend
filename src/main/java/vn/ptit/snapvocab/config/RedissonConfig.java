package vn.ptit.snapvocab.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

	@Bean(destroyMethod = "shutdown")
	public RedissonClient redissonClient(RedisProperties redisProperties) {
		Config config = new Config();

		config.setCodec(new JsonJacksonCodec());

		config.useSingleServer()
				.setAddress("rediss://" + redisProperties.getHost() + ":" + redisProperties.getPort())
				.setUsername(redisProperties.getUsername())
				.setPassword(redisProperties.getPassword())
				.setDatabase(redisProperties.getDatabase());

		if (redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank()) {
			config.useSingleServer().setPassword(redisProperties.getPassword());
		}

		return Redisson.create(config);
	}
}
