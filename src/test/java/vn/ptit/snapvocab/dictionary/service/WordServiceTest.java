package vn.ptit.snapvocab.dictionary.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import vn.ptit.snapvocab.common.exception.ResourceNotFoundException;
import vn.ptit.snapvocab.dictionary.domain.Word;
import vn.ptit.snapvocab.dictionary.domain.dto.BatchLookupRequest;
import vn.ptit.snapvocab.dictionary.domain.dto.WordSuggestDTO;
import vn.ptit.snapvocab.dictionary.domain.mapper.LookupResultMapper;
import vn.ptit.snapvocab.dictionary.repository.UserWordRepository;
import vn.ptit.snapvocab.dictionary.repository.WordRepository;
import vn.ptit.snapvocab.user.service.UserService;
import org.mapstruct.factory.Mappers;

@ExtendWith(MockitoExtension.class)
class WordServiceTest {
    @Mock private WordRepository words;
    @Mock private UserWordRepository saved;
    @Mock private UserService users;
    private WordService service;

    @BeforeEach
    void setUp() {
        service = new WordService(words, Mappers.getMapper(LookupResultMapper.class), saved, users);
    }

    @Test
    void singleLookupIncludesWordIdAndCurrentUsersSavedStatus() {
        when(users.getCurrentUserId()).thenReturn(Optional.of(7L));
        when(words.findByWordAndLangCodeEn("dog")).thenReturn(Optional.of(word(10L, "dog")));
        when(saved.existsByUser_IdAndWord_Id(7L, 10L)).thenReturn(true);
        var result = service.getWordByEnglish("dog");
        assertThat(result.getWordId()).isEqualTo(10L);
        assertThat(result.isInMyVocabulary()).isTrue();
        assertThat(result.isExists()).isTrue();
    }

    @Test
    void lookupByIdReflectsRemovalOnNextRequest() {
        when(users.getCurrentUserId()).thenReturn(Optional.of(7L));
        when(words.findWordById(10L)).thenReturn(Optional.of(word(10L, "dog")));
        when(saved.existsByUser_IdAndWord_Id(7L, 10L)).thenReturn(true, false);
        assertThat(service.getWordById(10L).isInMyVocabulary()).isTrue();
        service.removeMyWord(10L);
        assertThat(service.getWordById(10L).isInMyVocabulary()).isFalse();
        verify(saved).removeWord(7L, 10L);
    }

    @Test
    void batchLookupChecksMembershipInOneQueryForCurrentUser() {
        when(users.getCurrentUserId()).thenReturn(Optional.of(8L));
        when(words.findWordsByLangCodeEn(Set.of("dog", "cat")))
                .thenReturn(List.of(word(10L, "dog"), word(11L, "cat")));
        when(saved.findSavedWordIds(8L, List.of(10L, 11L))).thenReturn(Set.of(11L));
        var results = service.getWordsByEnglish(new BatchLookupRequest(Set.of("dog", "cat")));
        assertThat(results).extracting(r -> r.isInMyVocabulary()).containsExactly(false, true);
        assertThat(results).extracting(r -> r.getWordId()).containsExactly(10L, 11L);
        verify(saved).findSavedWordIds(8L, List.of(10L, 11L));
        verifyNoMoreInteractions(saved);
    }

    @Test
    void addUsesAuthenticatedUserAndRejectsMissingDictionaryWord() {
        when(users.getCurrentUserId()).thenReturn(Optional.of(7L));
        when(words.existsById(10L)).thenReturn(true);
        service.addMyWord(10L);
        verify(saved).addWord(7L, 10L);
        assertThatThrownBy(() -> service.addMyWord(99L)).isInstanceOf(ResourceNotFoundException.class);
        verify(saved, never()).addWord(7L, 99L);
    }

    @Test
    void personalVocabularyRequiresAuthentication() {
        when(users.getCurrentUserId()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addMyWord(10L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.removeMyWord(10L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getMyWords(PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(saved, words);
    }

    @Test
    void listIsScopedToCurrentUserAndKeepsPagination() {
        when(users.getCurrentUserId()).thenReturn(Optional.of(7L));
        var pageable = PageRequest.of(0, 1);
        when(saved.findMyWords(7L, pageable))
                .thenReturn(new PageImpl<>(List.of(new WordSuggestDTO(10L, "dog")), pageable, 3));
        var result = service.getMyWords(pageable);
        assertThat(result.getData()).extracting(WordSuggestDTO::getId).containsExactly(10L);
        assertThat(result.getMeta().getTotal()).isEqualTo(3);
        assertThat(result.getMeta().getPages()).isEqualTo(3);
    }

    @Test
    void noMatchingWordsSkipsMembershipQuery() {
        when(words.findWordsByLangCodeEn(Set.of("unknown"))).thenReturn(List.of());
        assertThat(service.getWordsByEnglish(new BatchLookupRequest(Set.of("unknown")))).isEmpty();
        verifyNoInteractions(saved);
    }

    private Word word(Long id, String text) {
        Word word = new Word();
        word.setId(id);
        word.setWord(text);
        word.setLangCode("en");
        return word;
    }
}
