package vn.ptit.snapvocab.dictionary.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.common.domain.BaseCreatedAtEntity;

@Entity
@Table(name = "definitions")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Definition extends BaseCreatedAtEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Lob
	@Column(name = "definition", nullable = false, columnDefinition = "TEXT")
	private String definition;

	@Size(max = 50)
	@Column(name = "pos", length = 50)
	private String pos;

	@Size(max = 50)
	@Column(name = "sub_pos", length = 50)
	private String subPos;

	@Size(max = 10)
	@Column(name = "definition_lang", length = 10)
	private String definitionLang;

	@Size(max = 2048)
	@Column(name = "links", length = 2048)
	private String links;

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "definition")
	@JsonIgnoreProperties(value = {"word", "definition"}, allowSetters = true)
	@Builder.Default
	private Set<WordDefinition> wordDefinitions = new HashSet<>();
}

