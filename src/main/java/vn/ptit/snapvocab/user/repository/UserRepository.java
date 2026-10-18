package vn.ptit.snapvocab.user.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.user.domain.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

	@EntityGraph(attributePaths = "authorities")
	Optional<User> findOneWithAuthoritiesByEmailIgnoreCase(String email);

	Optional<User> findOneByEmailIgnoreCase(String email);

	boolean existsByEmail(String email);

	/** Economy operations lock the user row first (shop.md §7.1) to serialize coin/item changes per user. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT u FROM User u WHERE u.id = :id")
	Optional<User> findByIdForUpdate(@Param("id") Long id);
}

