package vn.ptit.snapvocab.security;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.internal.constraintvalidators.bv.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.domain.Authority;
import vn.ptit.snapvocab.domain.User;
import vn.ptit.snapvocab.repository.UserRepository;
import vn.ptit.snapvocab.util.exception.UserNotActivatedException;

@Component("userDetailsService")
@RequiredArgsConstructor
public class DomainUserDetailsService implements UserDetailsService {

	private static final Logger LOG = LoggerFactory.getLogger(DomainUserDetailsService.class);

	private final UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
		LOG.debug("Authenticating {}", username);

		if (new EmailValidator().isValid(username, null)) {
			return (UserDetails) userRepository
					.findOneWithAuthoritiesByEmailIgnoreCase(username.toLowerCase())
					.map(user -> createSpringSecurityUser(username, user))
					.orElseThrow(() -> new UsernameNotFoundException(
							"User with email " + username + " was not found in the database"));
		}
		return (UserDetails) userRepository.findOneWithAuthoritiesByEmailIgnoreCase(
						username.toLowerCase())
				.map(user -> createSpringSecurityUser(username.toLowerCase(), user))
				.orElseThrow(() -> new UsernameNotFoundException(
						"User " + username.toLowerCase() + "was not found in the database"));
	}

	private Object createSpringSecurityUser(String lowerCaseUsername, User user) {
		if (!user.isActivated()) {
			throw new UserNotActivatedException("USER_NOT_ACTIVATED");
		}
		return UserWithId.fromUser(user);
	}

	public static class UserWithId extends org.springframework.security.core.userdetails.User {

		private final Long id;

		public UserWithId(String username, String password,
				Collection<? extends GrantedAuthority> authorities, Long id) {
			super(username, password, authorities);
			this.id = id;
		}

		public static UserWithId fromUser(User user) {
			return new UserWithId(
					user.getEmail(),
					user.getPassword(),
					user.getAuthorities().stream().map(Authority::getName).map(SimpleGrantedAuthority::new)
							.toList(),
					user.getId()
			);
		}

		public Long getId() {
			return id;
		}

		@Override
		public boolean equals(Object obj) {
			return super.equals(obj);
		}

		@Override
		public int hashCode() {
			return super.hashCode();
		}
	}
}
