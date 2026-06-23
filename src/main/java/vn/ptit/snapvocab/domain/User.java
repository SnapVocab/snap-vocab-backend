package vn.ptit.snapvocab.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

@Entity
@Table(name = "user")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@JsonIgnore
	@NonNull
	@Size(min = 60, max = 60)
	@Column(name = "password_hash", length = 60, nullable = false)
	private String password;

	@Size(max = 50)
	@Column(name = "first_name", nullable = false)
	private String firstName;

	@Size(max = 50)
	@Column(name = "last_name", length = 50)
	private String lastName;

	@Email
	@Size(min = 5, max = 254)
	@Column(length = 254, unique = true)
	private String email;

	@Size(max = 256)
	@Column(name = "image_url", length = 256)
	private String imageUrl;

	@ManyToMany
	@JoinTable(
			name = "user_authority",
			joinColumns = {@JoinColumn(name = "user_id", referencedColumnName = "id")},
			inverseJoinColumns = {@JoinColumn(name = "authority_name", referencedColumnName = "name")}
	)
	private Set<Authority> authorities = new HashSet<>();

	@Size(max = 10)
	@Column(name = "native_language", length = 10)
	private String nativeLanguage;

	@Size(max = 10)
	@Column(name = "learning_language", length = 10)
	private String learningLanguage;

	@Min(value = 0)
	@Column(name = "exp")
	private Long exp;

	@Min(value = 0)
	@Column(name = "coin")
	private Long coin;

	@Min(value = 0)
	@Column(name = "streak_days")
	private Integer streakDays;

	@Column(name = "last_studied_at")
	private Instant lastStudiedAt;

	@NotNull
	@Column(nullable = false)
	private boolean activated = false;

}
