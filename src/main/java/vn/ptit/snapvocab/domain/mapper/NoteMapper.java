package vn.ptit.snapvocab.domain.mapper;

import java.util.List;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import vn.ptit.snapvocab.domain.Deck;
import vn.ptit.snapvocab.domain.Note;
import vn.ptit.snapvocab.domain.NoteMeaning;
import vn.ptit.snapvocab.domain.NotePronunciation;
import vn.ptit.snapvocab.domain.dto.note.NoteDTO;

@Mapper(
		componentModel = "spring",
		uses = {NoteMeaningMapper.class, NotePronunciationMapper.class},
		builder = @org.mapstruct.Builder(disableBuilder = true)
)
public interface NoteMapper extends EntityMapper<NoteDTO, Note> {

	@Override
	@Mapping(target = "deck", source = "deckId")
	Note toEntity(NoteDTO dto);

	@Override
	@Mapping(target = "deckId", source = "deck.id")
	NoteDTO toDto(Note entity);

	@Override
	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
	@Mapping(target = "deck", source = "deckId")
	void partialUpdate(@MappingTarget Note entity, NoteDTO dto);

	default Deck toDeck(Long deckId) {
		return deckId == null ? null : Deck.builder().id(deckId).build();
	}

	@AfterMapping
	default void linkChildren(@MappingTarget Note note) {
		List<NoteMeaning> meanings = note.getNoteMeanings();
		if (meanings != null) {
			meanings.forEach(meaning -> meaning.setNote(note));
		}

		List<NotePronunciation> pronunciations = note.getNotePronunciations();
		if (pronunciations != null) {
			pronunciations.forEach(pronunciation -> pronunciation.setNote(note));
		}
	}
}
