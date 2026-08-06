package vn.ptit.snapvocab.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "word_definition", indexes = {
		@Index(name = "idx_word_definition_word_id", columnList = "word_id"),
		@Index(name = "idx_word_definition_definition_id", columnList = "definition_id"),
})
public class WordDefinition {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Lob
	@Column(name = "example", columnDefinition = "TEXT")
	private String example;

	@ManyToOne(optional = false)
	@NotNull
	@JsonIgnoreProperties(
			value = {"pronunciations", "translations", "wordRelations", "wordDefinitions", "flashcards"},
			allowSetters = true
	)
	private Word word;

	@ManyToOne(optional = false)
	@NotNull
	@JsonIgnoreProperties(value = {"wordDefinitions"}, allowSetters = true)
	private Definition definition;
}
