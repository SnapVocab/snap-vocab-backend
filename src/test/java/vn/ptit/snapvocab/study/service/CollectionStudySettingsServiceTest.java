package vn.ptit.snapvocab.study.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import vn.ptit.snapvocab.study.domain.CollectionStudySettings;
import vn.ptit.snapvocab.study.domain.dto.UpdateCollectionStudySettingsRequest;
import vn.ptit.snapvocab.study.repository.CollectionStudySettingsRepository;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.user.repository.UserRepository;
import vn.ptit.snapvocab.vocab.domain.Collection;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.vocab.repository.CollectionRepository;

@ExtendWith(MockitoExtension.class)
class CollectionStudySettingsServiceTest {
    @Mock CollectionStudySettingsRepository settings;
    @Mock CollectionRepository collections;
    @Mock UserRepository users;
    @InjectMocks CollectionStudySettingsService service;

    @Test
    void missingSettingsReturnDefaultsWithoutWritingAndArePrivatePerUser() {
        when(collections.findById(3L)).thenReturn(Optional.of(Collection.builder().id(3L).type(CollectionType.SYSTEM).build()));
        var existing = new CollectionStudySettings();
        existing.setNewCardsPerDay(5);
        existing.setReviewCardsPerDay(50);
        when(settings.findByUserIdAndCollectionId(7L, 3L)).thenReturn(Optional.of(existing));
        assertThat(service.get(7L, 3L).newCardsPerDay()).isEqualTo(5);
        var defaults = service.get(8L, 3L);
        assertThat(defaults.newCardsPerDay()).isEqualTo(20);
        assertThat(defaults.reviewCardsPerDay()).isEqualTo(200);
        assertThat(defaults.customized()).isFalse();
        verify(settings, never()).save(any());
    }

    @Test
    void learnersCanUpdateTheirOwnSystemCollectionSettingsAndResetOnlyTheirRow() {
        var collection = Collection.builder().id(3L).type(CollectionType.SYSTEM).build();
        var user = User.builder().id(7L).password("test").build();
        when(collections.findById(3L)).thenReturn(Optional.of(collection));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(users.getReferenceById(7L)).thenReturn(user);
        when(settings.save(any())).thenAnswer(call -> call.getArgument(0));
        var saved = service.update(7L, 3L, new UpdateCollectionStudySettingsRequest(0, 17));
        assertThat(saved.newCardsPerDay()).isZero();
        assertThat(saved.reviewCardsPerDay()).isEqualTo(17);
        ArgumentCaptor<CollectionStudySettings> row = ArgumentCaptor.forClass(CollectionStudySettings.class);
        verify(settings).save(row.capture());
        assertThat(row.getValue().getUser().getId()).isEqualTo(7L);
        assertThat(row.getValue().getCollection().getId()).isEqualTo(3L);
        when(settings.findByUserIdAndCollectionId(7L, 3L)).thenReturn(Optional.of(row.getValue()));
        service.reset(7L, 3L);
        verify(settings).delete(row.getValue());
    }

    @Test
    void anotherLearnersPrivateCollectionIsNotReadableOrWritable() {
        when(collections.findById(3L)).thenReturn(Optional.of(Collection.builder().id(3L).type(CollectionType.USER)
                .owner(User.builder().id(8L).password("test").build()).build()));
        assertThatThrownBy(() -> service.get(7L, 3L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update(7L, 3L, new UpdateCollectionStudySettingsRequest(5, 50)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.reset(7L, 3L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(settings);
    }

    @Test
    void requestRejectsMissingNegativeAndOutOfRangeLimits() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new UpdateCollectionStudySettingsRequest(null, 200))).isNotEmpty();
            assertThat(validator.validate(new UpdateCollectionStudySettingsRequest(-1, 200))).isNotEmpty();
            assertThat(validator.validate(new UpdateCollectionStudySettingsRequest(20, 100000))).isNotEmpty();
            assertThat(validator.validate(new UpdateCollectionStudySettingsRequest(0, 0))).isEmpty();
        }
    }
}
