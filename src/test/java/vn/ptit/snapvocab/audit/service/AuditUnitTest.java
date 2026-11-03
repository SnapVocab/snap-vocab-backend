package vn.ptit.snapvocab.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.ptit.snapvocab.audit.domain.dto.AuditChange;
import vn.ptit.snapvocab.audit.domain.enumeration.AuditErrorCode;
import vn.ptit.snapvocab.audit.exception.AuditException;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;

/** admin_audit_log_backend.md §9.1: AuditDiff, AuditText, AuditReason, vnDay. */
class AuditUnitTest {

	/** Like Spring Boot's mapper: Instant as ISO-8601. */
	private final ObjectMapper om = JsonMapper.builder().addModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

	record Item(String name, Long price, Instant availableFrom, ShopItemStatus status) {
	}

	private static final List<String> FIELDS = List.of("status", "name", "price", "availableFrom");

	@Test
	void diffOfCreationSkipsNullsAndKeepsFieldOrder() {
		List<AuditChange> c = AuditDiff.of(om, null, new Item("Neon", null, Instant.parse("2026-10-06T07:33:50Z"),
				ShopItemStatus.DRAFT), FIELDS);
		assertThat(c).extracting(AuditChange::field).containsExactly("status", "name", "availableFrom");
		assertThat(c.get(0).after().asText()).isEqualTo("DRAFT");
		assertThat(c.get(2).after().asText()).isEqualTo("2026-10-06T07:33:50Z");
		assertThat(c).allMatch(x -> x.before().isNull());
	}

	@Test
	void diffOfUpdateListsOnlyChangedFieldsAndDeletionIsASnapshot() {
		Item before = new Item("Neon", 900L, null, ShopItemStatus.PUBLISHED);
		List<AuditChange> c = AuditDiff.of(om, before, new Item("Neon", 950L, null, ShopItemStatus.PUBLISHED), FIELDS);
		assertThat(c).hasSize(1);
		assertThat(c.get(0).field()).isEqualTo("price");
		assertThat(c.get(0).before().asLong()).isEqualTo(900);
		assertThat(c.get(0).after().asLong()).isEqualTo(950);
		assertThat(AuditDiff.of(om, before, before, FIELDS)).isEmpty();

		List<AuditChange> deleted = AuditDiff.of(om, before, null, FIELDS);
		assertThat(deleted).extracting(AuditChange::field).containsExactly("status", "name", "price");
		assertThat(deleted).allMatch(x -> x.after().isNull());
	}

	@Test
	void foldRemovesVietnameseMarksAndLikeEscapesWildcards() {
		assertThat(AuditText.fold("Bậc Thầy Hoàng Kim")).isEqualTo("bac thay hoang kim");
		assertThat(AuditText.fold("Đặng")).isEqualTo("dang");
		assertThat(AuditText.likePattern(" 50%_off! ")).isEqualTo("%50!%!_off!!%");
		assertThat(AuditText.cut("abcdef", 3)).isEqualTo("abc");
		assertThat(AuditText.cut(null, 3)).isNull();
	}

	@Test
	void reasonIsTrimmedOptionalOrRequiredAndBounded() {
		assertThat(AuditReason.check("   ", false, null, null)).isNull();
		assertThat(AuditReason.check("  Đủ mười ký tự rồi  ", true, AuditReason.ARCHIVE, null))
				.isEqualTo("Đủ mười ký tự rồi");

		assertThatThrownBy(() -> AuditReason.check(null, true, AuditReason.ARCHIVE, null))
				.isInstanceOfSatisfying(AuditException.class, e -> {
					assertThat(e.getCode()).isEqualTo(AuditErrorCode.AUDIT_REASON_REQUIRED);
					@SuppressWarnings("unchecked")
					Map<String, Object> details = (Map<String, Object>) e.getDetails();
					assertThat(details).containsEntry("rule", "ARCHIVE").containsEntry("guardrail", null);
				});
		assertThatThrownBy(() -> AuditReason.check("123456789", false, null, null))
				.isInstanceOfSatisfying(AuditException.class,
						e -> assertThat(e.getCode()).isEqualTo(AuditErrorCode.AUDIT_REASON_INVALID));
		assertThatThrownBy(() -> AuditReason.check("x".repeat(501), false, null, null))
				.isInstanceOfSatisfying(AuditException.class,
						e -> assertThat(e.getCode()).isEqualTo(AuditErrorCode.AUDIT_REASON_INVALID));
	}

	@Test
	void takeRemovesReasonAndRejectsNonText() {
		ObjectNode body = om.createObjectNode().put("price", 950).put("reason", "Giá mùa xuân mới");
		assertThat(AuditReason.take(body)).isEqualTo("Giá mùa xuân mới");
		assertThat(body.has("reason")).isFalse();
		assertThat(AuditReason.take(body)).isNull();
		assertThatThrownBy(() -> AuditReason.take(om.createObjectNode().put("reason", 42)))
				.isInstanceOf(AuditException.class);
	}

	@Test
	void vnDayTurnsAt17Utc() {
		assertThat(AdminAuditService.vnDay(Instant.parse("2026-10-18T16:59:59.999Z"))).isEqualTo(LocalDate.of(2026, 10, 18));
		assertThat(AdminAuditService.vnDay(Instant.parse("2026-10-18T17:00:00Z"))).isEqualTo(LocalDate.of(2026, 10, 19));
	}
}
