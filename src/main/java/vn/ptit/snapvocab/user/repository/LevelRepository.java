package vn.ptit.snapvocab.user.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.user.domain.Level;

@Repository
public interface LevelRepository extends JpaRepository<Level, Integer> {

	Optional<Level> findFirstByRequiredExpLessThanEqualOrderByRequiredExpDesc(Long exp);

	Optional<Level> findFirstByRequiredExpGreaterThanOrderByRequiredExpAsc(Long exp);
}
