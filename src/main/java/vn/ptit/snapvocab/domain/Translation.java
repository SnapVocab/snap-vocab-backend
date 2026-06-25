package vn.ptit.snapvocab.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "translation", indexes = @Index(name = "idx_translation_word_id", columnList = "word_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Translation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Size(max = 20)
	@Column(name = "lang_code", length = 20)
	private String langCode;

	@Size(max = 1024)
	@Column(name = "translation", length = 1024)
	private String translation;

	@ManyToOne(optional = false)
	@NotNull
	@JsonIgnoreProperties(
			value = {"pronunciations", "translations", "wordRelations", "wordDefinitions", "flashcards"},
			allowSetters = true
	)
	private Word word;


}
