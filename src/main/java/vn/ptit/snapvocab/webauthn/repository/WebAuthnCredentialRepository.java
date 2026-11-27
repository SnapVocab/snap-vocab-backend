package vn.ptit.snapvocab.webauthn.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.webauthn.domain.WebAuthnCredential;

@Repository
public interface WebAuthnCredentialRepository extends JpaRepository<WebAuthnCredential, Long> {

	List<WebAuthnCredential> findAllByUserIdOrderByCreatedAtDesc(Long userId);

	/** Revokes every credential of one kind (e.g. fingerprint device tokens) for a learner. */
	long deleteByUserIdAndTransports(Long userId, String transports);

	Optional<WebAuthnCredential> findByCredentialId(String credentialId);

	@Query("""
			select c from WebAuthnCredential c
			join fetch c.user u
			left join fetch u.authorities
			where c.credentialId = :credentialId
			""")
	Optional<WebAuthnCredential> findWithUserAndAuthoritiesByCredentialId(
			@Param("credentialId") String credentialId);

	boolean existsByCredentialId(String credentialId);

	Optional<WebAuthnCredential> findByIdAndUserId(Long id, Long userId);
}

