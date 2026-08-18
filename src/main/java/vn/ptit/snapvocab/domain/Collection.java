package vn.ptit.snapvocab.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Entity
@Table(name = "collections")
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Collection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String name;

	@Column(length = 255)
	private String translation;

	@OneToMany(
			mappedBy = "collection",
			cascade = CascadeType.ALL,
			orphanRemoval = true
	)
	private List<Topic> topics = new ArrayList<>();
}
