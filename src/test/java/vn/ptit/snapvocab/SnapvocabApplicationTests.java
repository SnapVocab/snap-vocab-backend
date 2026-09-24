package vn.ptit.snapvocab;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.ptit.snapvocab.support.LocalDatabaseGuard;

/** Full context on the LOCAL test database only; never the default (remote) dev profile. */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
class SnapvocabApplicationTests {

	@MockitoBean
	private MinioClient minioClient;

	@MockitoBean
	private RedissonClient redissonClient;

	@MockitoBean
	private CacheManager cacheManager;

	@Test
	void contextLoads() {
	}

}
