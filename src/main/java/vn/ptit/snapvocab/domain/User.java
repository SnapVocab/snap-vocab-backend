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
	@Size(min = 60, max = 60, message = "Password hash must be exactly 60 characters")
	@Column(name = "password_hash", length = 60, nullable = false)
	private String password;

	@Size(max = 50, message = "First name must not exceed 50 characters")
	@Column(name = "first_name", nullable = false)
	private String firstName;

	@Size(max = 50, message = "Last name must not exceed 50 characters")
	@Column(name = "last_name", length = 50)
	private String lastName;

	@Email(message = "Email must be a valid email address")
	@Size(min = 5, max = 254, message = "Email must be between 5 and 254 characters")
	@Column(length = 254, unique = true)
	private String email;

	@Column(name = "avatar_url", columnDefinition = "MEDIUMTEXT")
	private String avatar_url;

	@ManyToMany
	@JoinTable(
			name = "user_authority",
			joinColumns = {@JoinColumn(name = "user_id", referencedColumnName = "id")},
			inverseJoinColumns = {@JoinColumn(name = "authority_name", referencedColumnName = "name")}
	)
	private Set<Authority> authorities = new HashSet<>();

	@Size(max = 10, message = "Native language code must not exceed 10 characters")
	@Column(name = "native_language", length = 10)
	private String nativeLanguage;

	@Size(max = 10, message = "Learning language code must not exceed 10 characters")
	@Column(name = "learning_language", length = 10)
	private String learningLanguage;

	@Min(value = 0, message = "EXP must be greater than or equal to 0")
	@Column(name = "exp")
	private Long exp = 0L;

	@Min(value = 0, message = "Coin must be greater than or equal to 0")
	@Column(name = "coin")
	private Long coin = 0L;

	@Min(value = 0, message = "Streak days must be greater than or equal to 0")
	@Column(name = "streak_days")
	private Integer streakDays = 0;

	@Column(name = "last_studied_at")
	private Instant lastStudiedAt;

	@NotNull(message = "Activated status must not be null")
	@Column(nullable = false)
	private boolean activated = false;

	@Size(max = 200, message = "Bio must not exceed 200 characters")
	@Column(name = "bio", length = 200)
	private String bio;

}
