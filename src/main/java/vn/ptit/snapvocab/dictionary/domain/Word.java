package vn.ptit.snapvocab.dictionary.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import vn.ptit.snapvocab.common.domain.BaseCreatedAtEntity;

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

	/** Soft delete (FR-13): hidden from suggest, lookup and scan; a learner's saved word still opens by id. */
	@Column(name = "deleted_at")
	private Instant deletedAt;

	/** Admin editor's optimistic lock, compared and bumped by AdminWordService (migration_admin_content_v1). */
	@ColumnDefault("0")
	@Column(name = "version", nullable = false)
	private long version;

	// Ordered by id (import order) so "first meaning/pronunciation" is stable
	// across
	// requests; HashSet iteration order made it change between calls.
	@OneToMany(fetch = FetchType.LAZY, mappedBy = "word")
	@JsonIgnoreProperties(value = { "word" }, allowSetters = true)
	@OrderBy("id ASC")
	private Set<Pronunciation> pronunciations = new HashSet<>();

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "word")
	@JsonIgnoreProperties(value = { "word" }, allowSetters = true)
	@OrderBy("id ASC")
	private Set<Translation> translations = new HashSet<>();

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "word")
	@JsonIgnoreProperties(value = { "word", "definition" }, allowSetters = true)
	@OrderBy("id ASC")
	private Set<WordDefinition> wordDefinitions = new HashSet<>();

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "word")
	@JsonIgnoreProperties(value = { "word" }, allowSetters = true)
	@OrderBy("id ASC")
	private Set<WordRelation> wordRelations = new HashSet<>();
}

