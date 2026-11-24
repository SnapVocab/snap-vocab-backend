package vn.ptit.snapvocab.webauthn.domain;

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
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;
import vn.ptit.snapvocab.user.domain.User;

@Entity
@Table(
		name = "user_webauthn_credentials",
		indexes = {
				@Index(name = "idx_uwc_user_id", columnList = "user_id")
		}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebAuthnCredential extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** Base64URL-encoded credential ID (without padding). */
	@Column(name = "credential_id", length = 512, nullable = false, unique = true)
	private String credentialId;

	/** Base64URL-encoded WebAuthn user handle bound to this credential. */
	@Column(name = "user_handle", length = 128, nullable = false)
	private String userHandle;

	/** CBOR-serialized COSEKey public key extracted from the attestation object. */
	@Column(name = "public_key_cose", nullable = false, length = 2048)
	private byte[] publicKeyCose;

	@Column(name = "aaguid", length = 64)
	private String aaguid;

	@Column(name = "sign_count", nullable = false)
	@Builder.Default
	private long signCount = 0L;

	/** Comma-separated authenticator transports (e.g. "internal,hybrid"). */
	@Column(name = "transports", length = 255)
	private String transports;

	@Column(name = "device_name", length = 120)
	private String deviceName;

	@Column(name = "backup_eligible", nullable = false)
	@Builder.Default
	private boolean backupEligible = false;

	@Column(name = "backup_state", nullable = false)
	@Builder.Default
	private boolean backupState = false;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;
}

