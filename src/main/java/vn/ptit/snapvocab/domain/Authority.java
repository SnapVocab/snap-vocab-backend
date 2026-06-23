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

	@NotNull(message = "Authority name must not be null")
	@Size(max = 50, message = "Authority name must not exceed 50 characters")
	@Id
	@Column(name = "name", length = 50, nullable = false)
	private String name;


}
