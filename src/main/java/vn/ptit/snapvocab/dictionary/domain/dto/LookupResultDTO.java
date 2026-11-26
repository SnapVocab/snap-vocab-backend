package vn.ptit.snapvocab.dictionary.domain.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LookupResultDTO {

    private Long wordId;

    private boolean inMyVocabulary;

    private boolean exists;

    private String word;

    private String langCode;

    private String langName;

    private List<PronunciationLookupDTO> pronunciations;

    private List<MeaningDTO> meanings;

    private List<TranslationLookupDTO> translations;

    private List<RelationLookupDTO> relations;

    private Instant createdAt;
    /** Set when an admin soft-deleted the word; only reachable by id then (saved words, Admin). */
    private Instant deletedAt;
    /** Admin editor's optimistic lock (PUT /api/admin/words/{id}). */
    private long version;
}

