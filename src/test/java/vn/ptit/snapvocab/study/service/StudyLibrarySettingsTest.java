package vn.ptit.snapvocab.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ptit.snapvocab.study.repository.CollectionStudySettingsRepository;
import vn.ptit.snapvocab.study.repository.StudyQueueRepository;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.vocab.repository.CollectionRepository;

/** Settings → "Giới hạn học theo bộ" (UX audit #12): every library collection with its limits, in one call. */
@ExtendWith(MockitoExtension.class)
class StudyLibrarySettingsTest {

    @Mock private StudyQueueRepository studyQueueRepository;
    @Mock private CollectionRepository collectionRepository;
    @Mock private CollectionStudySettingsRepository collectionSettingsRepository;
    @InjectMocks private StudyService service;

    @Test
    void librarySettingsListOwnCollectionsFirstWithDefaultsWhereNothingWasChanged() {
        User owner = User.builder().id(7L).email("o@test.local").firstName("O").password("x").build();
        vn.ptit.snapvocab.vocab.domain.Collection mine = vn.ptit.snapvocab.vocab.domain.Collection.builder().id(1L).name("Mine")
                .type(vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType.USER).owner(owner).build();
        vn.ptit.snapvocab.vocab.domain.Collection system = vn.ptit.snapvocab.vocab.domain.Collection.builder().id(2L).name("A system set")
                .type(vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType.SYSTEM).build();
        when(studyQueueRepository.findStudiedCollections(eq(7L), any(), any())).thenReturn(List.of());
        when(collectionSettingsRepository.findCollectionIdsByUserId(7L)).thenReturn(List.of(2L));
        when(collectionRepository.findByOwnerId(7L)).thenReturn(List.of(mine));
        when(collectionRepository.findAllById(any())).thenReturn(List.of(system));
        vn.ptit.snapvocab.study.domain.CollectionStudySettings custom = new vn.ptit.snapvocab.study.domain.CollectionStudySettings();
        custom.setCollection(system);
        custom.setNewCardsPerDay(5);
        custom.setReviewCardsPerDay(50);
        when(collectionSettingsRepository.findByUserIdAndCollectionIdIn(eq(7L), any())).thenReturn(List.of(custom));

        var rows = service.getLibrarySettings(7L);

        assertThat(rows).extracting(row -> row.collectionId()).containsExactly(1L, 2L);
        assertThat(rows.get(0).newCardsPerDay()).isEqualTo(CollectionStudySettingsService.DEFAULT_NEW_PER_DAY);
        assertThat(rows.get(0).customized()).isFalse();
        assertThat(rows.get(1).newCardsPerDay()).isEqualTo(5);
        assertThat(rows.get(1).reviewCardsPerDay()).isEqualTo(50);
        assertThat(rows.get(1).customized()).isTrue();
    }
}
