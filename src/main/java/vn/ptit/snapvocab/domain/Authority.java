package vn.ptit.snapvocab.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "authority")
@Getter
@Setter
public class Authority {

	@NotNull
	@Size(max = 50)
	@Id
	@Column(name = "name", length = 50, nullable = false)
	private String name;


}
