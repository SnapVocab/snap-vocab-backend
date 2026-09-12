package vn.ptit.snapvocab.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.domain.common.BaseCreatedAtEntity;

@Entity
@Table(name = "words", indexes = {
		@Index(name = "idx_words_en", columnList = "word, lang_code")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Word extends BaseCreatedAtEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@Size(max = 191)
	@Column(name = "word", length = 191, nullable = false)
	private String word;

	@NotNull
	@Size(min = 2, max = 20)
	@Column(name = "lang_code", length = 20, nullable = false)
	private String langCode;

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "word")
	@JsonIgnoreProperties(value = {"word"}, allowSetters = true)
	private Set<Pronunciation> pronunciations = new HashSet<>();

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "word")
	@JsonIgnoreProperties(value = {"word"}, allowSetters = true)
	private Set<Translation> translations = new HashSet<>();

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "word")
	@JsonIgnoreProperties(value = {"word", "definition"}, allowSetters = true)
	private Set<WordDefinition> wordDefinitions = new HashSet<>();

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "word")
	@JsonIgnoreProperties(value = {"word"}, allowSetters = true)
	private Set<WordRelation> wordRelations = new HashSet<>();
}
