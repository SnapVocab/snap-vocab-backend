package vn.ptit.snapvocab.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.domain.common.BaseCreatedAtEntity;

@Entity
@Table(name = "pronunciations", indexes = {
		@Index(name = "idx_pronunciations_word_id", columnList = "word_id"),
		@Index(name = "idx_pronunciations_unique", columnList = "word_id, ipa, region", unique = true)
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Pronunciation extends BaseCreatedAtEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Size(max = 512)
	@Column(name = "ipa", length = 512)
	private String ipa;

	@Size(max = 100)
	@Column(name = "region", length = 100)
	private String region;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "word_id", nullable = false)
	@NotNull
	@JsonIgnoreProperties(
			value = {"pronunciations", "translations", "wordRelations", "wordDefinitions"},
			allowSetters = true
	)
	private Word word;
}
