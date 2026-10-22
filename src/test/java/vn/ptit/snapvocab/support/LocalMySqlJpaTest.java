package vn.ptit.snapvocab.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.audit.service.AdminAuditService;
import vn.ptit.snapvocab.config.AuditConfig;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(initializers = LocalDatabaseGuard.class)
// AdminAuditService: every admin write in the imported services records its audit row (admin_audit_log_backend.md).
@Import({AuditConfig.class, AdminAuditService.class})
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public @interface LocalMySqlJpaTest {
}

