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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pronunciation", indexes = {
		@Index(name = "idx_pronunciation_word_id", columnList = "word_id"),
		@Index(name = "idx_pronunciation_unique", columnList = "word_id, ipa, region", unique = true)
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Pronunciation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Size(max = 512)
	@Column(name = "ipa", length = 512)
	private String ipa;

	@Size(max = 100)
	@Column(name = "region", length = 100)
	private String region;

	@ManyToOne(optional = false)
	@NotNull
	@JsonIgnoreProperties(
			value = {"pronunciations", "translations", "wordRelations", "wordDefinitions", "flashcards"},
			allowSetters = true
	)
	private Word word;
	
}
