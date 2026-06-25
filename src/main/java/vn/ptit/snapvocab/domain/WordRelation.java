package vn.ptit.snapvocab.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Index;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "word_relation", indexes = {
		@Index(name = "idx_word_relation_word_id", columnList = "word_id"),
		@Index(name = "idx_word_relation_unique", columnList = "word_id, related_word, relation_type", unique = true)
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WordRelation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Size(max = 255)
	@Column(name = "related_word", length = 255)
	private String relatedWord;

	@Size(max = 50)
	@Column(name = "relation_type", length = 50)
	private String relationType;

	@ManyToOne(optional = false)
	@NotNull
	@JsonIgnoreProperties(
			value = {"pronunciations", "translations", "wordRelations", "wordDefinitions", "flashcards"},
			allowSetters = true
	)
	private Word word;
}
