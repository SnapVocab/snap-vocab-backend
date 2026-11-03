package vn.ptit.snapvocab.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.Length;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditAction;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditArea;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditTargetType;

/**
 * One admin write (admin_audit_log_backend.md §3). Inserted in the write's own transaction, never updated or deleted.
 * No FK on actor_id: the row must outlive the admin account. DDL: migration/migration_admin_audit_logs_v1.sql.
 */
@Entity
@Immutable
@Table(name = "admin_audit_logs", indexes = {
		@Index(name = "idx_aal_day", columnList = "vn_day"),
		@Index(name = "idx_aal_target", columnList = "target_type, target_id, vn_day"),
		@Index(name = "idx_aal_actor", columnList = "actor_id, vn_day"),
		@Index(name = "idx_aal_area", columnList = "area, vn_day")})
@Check(name = "chk_aal_changes_json", constraints = "JSON_VALID(changes)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AdminAuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Set by AdminAuditService, not by @CreatedDate: vn_day is derived from the same instant. */
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "vn_day", nullable = false)
	private LocalDate vnDay;

	@Column(name = "actor_id")
	private Long actorId;

	@Column(name = "actor_email")
	private String actorEmail;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private AuditArea area;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 40)
	private AuditAction action;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "target_type", nullable = false, length = 30)
	private AuditTargetType targetType;

	@Column(name = "target_id", nullable = false)
	private String targetId;

	@Column(name = "target_name", nullable = false)
	private String targetName;

	@Column(name = "target_code", length = 120)
	private String targetCode;

	/** Topics only: "collectionId" or "collectionId,rootTopicId". */
	@Column(name = "target_parents", length = 100)
	private String targetParents;

	/** JSON array of AuditChange (§4.3). */
	@Lob
	@Column(nullable = false, length = Length.LONG32)
	private String changes;

	@Column(length = 500)
	private String reason;

	@Column(length = 500)
	private String guardrail;

	@Column(name = "economy_change", nullable = false)
	private Boolean economyChange;

	@Column(name = "object_version")
	private Long objectVersion;

	/** Folded (no diacritics, lower case) name, code, id, actor email and reason, for q (§5.4). */
	@Column(name = "search_text", nullable = false, length = 1500)
	private String searchText;
}
