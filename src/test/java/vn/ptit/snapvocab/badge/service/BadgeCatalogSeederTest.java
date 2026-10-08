package vn.ptit.snapvocab.badge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.ptit.snapvocab.badge.domain.Badge;
import vn.ptit.snapvocab.badge.repository.BadgeRepository;

class BadgeCatalogSeederTest {

	@Test
	void mvpCatalogMatchesSpec() {
		List<Badge> catalog = BadgeCatalogSeeder.mvpCatalog();
		assertThat(catalog).hasSize(12);
		assertThat(catalog).extracting(Badge::getCode).doesNotHaveDuplicates();
		assertThat(catalog).extracting(b -> b.getMetric() + ":" + b.getThreshold()).doesNotHaveDuplicates();
		assertThat(catalog).extracting(Badge::getSortOrder).isSorted().doesNotHaveDuplicates();
		assertThat(catalog).allMatch(b -> b.getThreshold() >= 1 && b.getActive());
	}

	@Test
	void seedInsertsOnlyMissingCodesAndNeverUpdates() {
		BadgeRepository repository = mock(BadgeRepository.class);
		List<Badge> catalog = BadgeCatalogSeeder.mvpCatalog();
		when(repository.findByCode(any())).thenReturn(Optional.empty());
		when(repository.findByCode("STREAK_7")).thenReturn(Optional.of(catalog.get(0)));

		new BadgeCatalogSeeder(repository).seed(catalog);

		verify(repository, times(11)).save(any());
		verify(repository, never()).save(catalog.get(0));
	}
}
