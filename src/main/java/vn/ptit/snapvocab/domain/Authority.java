package vn.ptit.snapvocab.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "authorities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Authority {

	@NotNull(message = "Authority name must not be null")
	@Size(max = 50, message = "Authority name must not exceed 50 characters")
	@Id
	@Column(name = "name", length = 50, nullable = false)
	private String name;

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Authority authority)) {
			return false;
		}
		return Objects.equals(name, authority.name);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(name);
	}
}
