package vn.ptit.snapvocab.audit.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ptit.snapvocab.audit.domain.AdminAuditLog;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditAction;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditArea;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditTargetType;

/** §5.5. "Base set" = the period plus the target, if any; the summary queries see only that. */
@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

	String BASE = "a.vnDay BETWEEN :from AND :to "
			+ "AND (:targetType IS NULL OR (a.targetType = :targetType AND a.targetId = :targetId)) ";

	@Query("SELECT a FROM AdminAuditLog a WHERE " + BASE
			+ "AND (:day IS NULL OR a.vnDay = :day) "
			+ "AND (:area IS NULL OR a.area = :area) "
			+ "AND (:anyActor = true OR (:actorId IS NULL AND a.actorId IS NULL) OR a.actorId = :actorId) "
			+ "AND (:kind IS NULL "
			+ "  OR (:kind = 'PRICE_REWARD' AND a.economyChange = true) "
			+ "  OR (:kind = 'LIFECYCLE' AND a.action IN :lifecycle) "
			+ "  OR (:kind = 'LEARNER_ACCOUNT' AND a.area = vn.ptit.snapvocab.audit.domain.enumeration.AuditArea.LEARNERS) "
			+ "  OR (:kind = 'GUARDRAIL' AND a.guardrail IS NOT NULL)) "
			+ "AND (:q IS NULL OR a.searchText LIKE :q ESCAPE '!')")
	Page<AdminAuditLog> search(@Param("from") LocalDate from, @Param("to") LocalDate to,
			@Param("targetType") AuditTargetType targetType, @Param("targetId") String targetId,
			@Param("day") LocalDate day, @Param("area") AuditArea area,
			@Param("anyActor") boolean anyActor, @Param("actorId") Long actorId,
			@Param("kind") String kind, @Param("lifecycle") Collection<AuditAction> lifecycle,
			@Param("q") String q, Pageable pageable);

	@Query("SELECT new vn.ptit.snapvocab.audit.repository.ActionCount(a.action, COUNT(a), "
			+ "SUM(CASE WHEN a.economyChange = true THEN 1 ELSE 0 END), "
			+ "SUM(CASE WHEN a.guardrail IS NOT NULL THEN 1 ELSE 0 END)) "
			+ "FROM AdminAuditLog a WHERE " + BASE + "GROUP BY a.action")
	List<ActionCount> countByAction(@Param("from") LocalDate from, @Param("to") LocalDate to,
			@Param("targetType") AuditTargetType targetType, @Param("targetId") String targetId);

	@Query("SELECT new vn.ptit.snapvocab.audit.repository.DayCount(a.vnDay, COUNT(a)) "
			+ "FROM AdminAuditLog a WHERE " + BASE + "GROUP BY a.vnDay")
	List<DayCount> countByDay(@Param("from") LocalDate from, @Param("to") LocalDate to,
			@Param("targetType") AuditTargetType targetType, @Param("targetId") String targetId);

	@Query("SELECT new vn.ptit.snapvocab.audit.repository.ActorCount(a.actorId, MAX(a.actorEmail), COUNT(a)) "
			+ "FROM AdminAuditLog a WHERE " + BASE + "GROUP BY a.actorId ORDER BY COUNT(a) DESC")
	List<ActorCount> countByActor(@Param("from") LocalDate from, @Param("to") LocalDate to,
			@Param("targetType") AuditTargetType targetType, @Param("targetId") String targetId);
}
