package vn.ptit.snapvocab.badge.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.badge.domain.Badge;
import vn.ptit.snapvocab.badge.domain.dto.AdminBadgeDTO;
import vn.ptit.snapvocab.badge.repository.BadgeRepository;
import vn.ptit.snapvocab.badge.repository.UserBadgeRepository;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;

/** P1 only: reads the whole catalog, including inactive badges; never syncs or grants. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBadgeService {

	private final BadgeRepository badgeRepository;
	private final UserBadgeRepository userBadgeRepository;

	public List<AdminBadgeDTO> list() {
		List<Badge> catalog = badgeRepository.findAllByOrderBySortOrderAscIdAsc();
		if (catalog.isEmpty()) {
			return List.of();
		}
		Map<Long, Long> counts = userBadgeRepository.countEarnersByBadge().stream()
				.collect(Collectors.toMap(UserBadgeRepository.BadgeEarnerCount::getBadgeId,
						UserBadgeRepository.BadgeEarnerCount::getEarnerCount));
		return catalog.stream().map(b -> AdminBadgeDTO.of(b, counts.getOrDefault(b.getId(), 0L))).toList();
	}

	public AdminBadgeDTO get(Long id) {
		Badge badge = badgeRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("BADGE_NOT_FOUND"));
		return AdminBadgeDTO.of(badge, userBadgeRepository.countByBadgeId(id));
	}
}
