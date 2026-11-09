package vn.ptit.snapvocab.badge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.ptit.snapvocab.audit.service.AdminAuditService;
import vn.ptit.snapvocab.badge.domain.Badge;
import vn.ptit.snapvocab.badge.domain.dto.AdminBadgeDTO;
import vn.ptit.snapvocab.badge.domain.enumeration.BadgeMetric;
import vn.ptit.snapvocab.badge.repository.BadgeRepository;
import vn.ptit.snapvocab.badge.repository.UserBadgeRepository;
import vn.ptit.snapvocab.badge.repository.UserBadgeRepository.BadgeEarnerCount;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.shop.repository.ShopItemRepository;
import vn.ptit.snapvocab.shop.service.ShopMapper;
import vn.ptit.snapvocab.storage.service.StorageService;

class AdminBadgeServiceTest {

	private final BadgeRepository badges = mock(BadgeRepository.class);
	private final UserBadgeRepository earned = mock(UserBadgeRepository.class);
	private final AdminBadgeService service = new AdminBadgeService(badges, earned, new ObjectMapper(),
			mock(AdminAuditService.class), mock(ShopItemRepository.class), mock(ShopMapper.class), mock(StorageService.class));

	@Test
	void listIncludesInactiveRowsWithOneAggregateAndARealZeroForUnseenBadges() {
		Badge inactive = badge(1L, false);
		Badge active = badge(2L, true);
		BadgeEarnerCount count = mock(BadgeEarnerCount.class);
		when(count.getBadgeId()).thenReturn(1L);
		when(count.getEarnerCount()).thenReturn(3L);
		when(badges.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of(inactive, active));
		when(earned.countEarnersByBadge()).thenReturn(List.of(count));

		List<AdminBadgeDTO> result = service.list();
		assertThat(result).extracting(AdminBadgeDTO::id).containsExactly(1L, 2L);
		assertThat(result).extracting(AdminBadgeDTO::isActive).containsExactly(false, true);
		assertThat(result).extracting(AdminBadgeDTO::earnerCount).containsExactly(3L, 0L);
		assertThat(result).allSatisfy(dto -> assertThat(dto.version()).isZero());
		verify(badges).findAllByOrderBySortOrderAscIdAsc();
		verify(earned).countEarnersByBadge();
		verifyNoMoreInteractions(badges, earned);
	}

	@Test
	void emptyCatalogIsEmptyWithoutGrantingOrCountingLearners() {
		when(badges.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of());
		assertThat(service.list()).isEmpty();
		verify(badges).findAllByOrderBySortOrderAscIdAsc();
		verifyNoMoreInteractions(badges, earned);
	}

	@Test
	void detailReadsTheCurrentCatalogAndCountEvenWhenInactive() {
		Badge badge = badge(1L, false);
		badge.setName("Current name");
		when(badges.findById(1L)).thenReturn(Optional.of(badge));
		when(earned.countByBadgeId(1L)).thenReturn(7L);
		AdminBadgeDTO result = service.get(1L);
		assertThat(result.name()).isEqualTo("Current name");
		assertThat(result.isActive()).isFalse();
		assertThat(result.earnerCount()).isEqualTo(7);
		assertThat(result.version()).isZero();
		verify(badges).findById(1L);
		verify(earned).countByBadgeId(1L);
		verifyNoMoreInteractions(badges, earned);
	}

	@Test
	void missingDetailHasTheContractErrorCode() {
		when(badges.findById(99L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("BADGE_NOT_FOUND");
		verify(badges).findById(99L);
		verifyNoMoreInteractions(badges, earned);
	}

	private static Badge badge(Long id, boolean active) {
		return Badge.builder().id(id).code("QUIZ_" + id).name("Quiz").description("Complete quiz")
				.metric(BadgeMetric.QUIZ_COMPLETED).threshold(id.intValue()).imageKey("public/badges/quiz_1.png")
				.sortOrder(id.intValue()).active(active).build();
	}
}
