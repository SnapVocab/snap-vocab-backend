package vn.ptit.snapvocab.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.domain.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

	@EntityGraph(attributePaths = "authorities")
	Optional<User> findOneWithAuthoritiesByEmailIgnoreCase(String email);

	Optional<User> findOneByEmailIgnoreCase(String email);
}
