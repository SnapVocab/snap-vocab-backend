package vn.ptit.snapvocab.badge.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.badge.domain.Badge;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, Long> {

	Optional<Badge> findByCode(String code);

	List<Badge> findAllByOrderBySortOrderAscIdAsc();
}
