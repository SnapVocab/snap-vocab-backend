package vn.ptit.snapvocab.config;

import java.util.Set;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.domain.Authority;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.repository.AuthorityRepository;
import vn.ptit.snapvocab.repository.UserRepository;
import vn.ptit.snapvocab.security.AuthoritiesConstants;

@Component
public class DataInitializer implements CommandLineRunner {

	private final AuthorityRepository authorityRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public DataInitializer(AuthorityRepository authorityRepository,
			UserRepository userRepository,
			PasswordEncoder passwordEncoder) {
		this.authorityRepository = authorityRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(String... args) {
		createAuthorityIfNotFound(AuthoritiesConstants.ADMIN);
		createAuthorityIfNotFound(AuthoritiesConstants.USER);
		createAuthorityIfNotFound(AuthoritiesConstants.ANONYMOUS);

		createAdminUserIfNotFound();
	}

	private void createAuthorityIfNotFound(String name) {
		if (!authorityRepository.existsById(name)) {
			Authority authority = new Authority();
			authority.setName(name);
			authorityRepository.save(authority);
		}
	}

	private void createAdminUserIfNotFound() {
		String adminEmail = "admin@snapvocab.com";
		if (userRepository.findOneByEmailIgnoreCase(adminEmail).isEmpty()) {
			Authority adminAuth = authorityRepository.findById(AuthoritiesConstants.ADMIN).orElseThrow();
			Authority userAuth = authorityRepository.findById(AuthoritiesConstants.USER).orElseThrow();

			User admin = User.builder()
					.email(adminEmail)
					.password(passwordEncoder.encode("admin"))
					.firstName("Admin")
					.activated(true)
					.authorities(Set.of(adminAuth, userAuth))
					.build();

			userRepository.save(admin);
		}
	}
}
