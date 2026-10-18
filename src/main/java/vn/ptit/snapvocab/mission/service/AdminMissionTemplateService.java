package vn.ptit.snapvocab.mission.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.mission.domain.MissionTemplate;
import vn.ptit.snapvocab.mission.domain.dto.MissionTemplateDTO;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionCategory;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionEligibilityRule;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionErrorCode;
import vn.ptit.snapvocab.mission.domain.enumeration.MissionTriggerEvent;
import vn.ptit.snapvocab.mission.exception.MissionException;
import vn.ptit.snapvocab.mission.repository.MissionTemplateRepository;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;
import vn.ptit.snapvocab.shop.repository.ShopItemRepository;

/**
 * Admin catalog management (daily_mission.md §8.3). Every write locks the whole catalog, applies the change and
 * re-checks the §4.2 invariant in one transaction, so two writers on different templates cannot jointly break it.
 */
@Service
@RequiredArgsConstructor
public class AdminMissionTemplateService {

	static final int CODE_MAX = 64;
	static final int NAME_MAX = 100;
	static final int DESCRIPTION_MAX = 255;
	static final Set<Integer> QUIZ_SIZES = Set.of(5, 10, 20);

	private static final Set<String> CREATE_FIELDS = Set.of("code", "name", "description", "category",
			"triggerEvent", "minQuizAccuracyPercent", "quizRequestedCount", "targetValue", "rewardCoin", "rewardXp",
			"rewardItemCode", "rewardItemFallbackCoin", "weight", "eligibilityRule");
	private static final Set<String> PATCH_FIELDS = Stream.concat(CREATE_FIELDS.stream(), Stream.of("isActive"))
			.collect(Collectors.toUnmodifiableSet());
	private static final Form NEW_TEMPLATE = new Form(null, null, null, null, null, null, null, null, null, null,
			null, null, 1, MissionEligibilityRule.NONE, false);

	private final MissionTemplateRepository repository;
	private final ShopItemRepository shopItemRepository;
	private final ObjectMapper objectMapper;

	/** Editable state of a template; JSON names match {@link MissionTemplateDTO}. */
	record Form(String code, String name, String description, MissionCategory category,
			MissionTriggerEvent triggerEvent, Integer minQuizAccuracyPercent, Integer quizRequestedCount,
			Integer targetValue, Integer rewardCoin, Integer rewardXp, String rewardItemCode,
			Integer rewardItemFallbackCoin, Integer weight, MissionEligibilityRule eligibilityRule,
			@JsonProperty("isActive") Boolean isActive) {
	}

	public record Violation(String field, MissionErrorCode code, String message) {
	}

	// ---- queries ----

	@Transactional(readOnly = true)
	public List<MissionTemplateDTO> list(Boolean active, String category) {
		MissionCategory filter = category == null || category.isBlank() ? null : parseCategory(category);
		// Catalog is small and unpaginated by contract; filter in memory.
		return repository.findAllByOrderById().stream()
				.filter(t -> active == null || active.equals(t.getActive()))
				.filter(t -> filter == null || filter == t.getCategory())
				.map(MissionTemplateDTO::of)
				.toList();
	}

	@Transactional(readOnly = true)
	public MissionTemplateDTO get(Long id) {
		return MissionTemplateDTO.of(repository.findById(id).orElseThrow(() -> notFound(id)));
	}

	// ---- commands ----

	/** Always created inactive; activation is a separate PATCH. */
	@Transactional
	public MissionTemplateDTO create(JsonNode body) {
		List<MissionTemplate> catalog = repository.lockCatalog();
		Form form = merge(NEW_TEMPLATE, body, CREATE_FIELDS);
		List<Violation> v = validate(form, true);
		if (form.code() != null && catalog.stream().anyMatch(t -> t.getCode().equals(form.code()))) {
			v.add(new Violation("code", MissionErrorCode.MISSION_TEMPLATE_CODE_EXISTS,
					"code " + form.code() + " is already used"));
		}
		throwIfAny(v);
		MissionTemplate template = MissionTemplate.builder().code(form.code()).active(false).build();
		apply(form, template);
		return MissionTemplateDTO.of(repository.saveAndFlush(template));
	}

	@Transactional
	public MissionTemplateDTO patch(Long id, JsonNode body) {
		List<MissionTemplate> catalog = repository.lockCatalog();
		MissionTemplate template = catalog.stream().filter(t -> t.getId().equals(id)).findFirst()
				.orElseThrow(() -> notFound(id));
		if (body == null || !body.isObject()) {
			throw invalid("body", "JSON object required");
		}
		JsonNode version = body.get("version");
		if (version == null || !version.isIntegralNumber()) {
			throw invalid("version", "version is required");
		}
		if (body.has("code")) {
			throw new MissionException(MissionErrorCode.FIELD_LOCKED, MissionErrorCode.FIELD_LOCKED.getStatus(),
					"code is immutable", Map.of("fields", List.of("code")));
		}
		if (version.asLong() != template.getVersion()) {
			throw new MissionException(MissionErrorCode.VERSION_CONFLICT, MissionErrorCode.VERSION_CONFLICT.getStatus(),
					"Template was modified by someone else; reload it", Map.of("currentVersion", template.getVersion()));
		}
		ObjectNode patch = body.deepCopy();
		patch.remove("version");
		Form form = merge(formOf(template), patch, PATCH_FIELDS);
		List<Violation> v = validate(form, patch.has("rewardItemCode") || activating(template, form));
		throwIfAny(v);
		apply(form, template);
		template.setActive(form.isActive());
		requireInvariant(catalog);
		try {
			return MissionTemplateDTO.of(repository.saveAndFlush(template));
		} catch (ObjectOptimisticLockingFailureException e) {
			throw new MissionException(MissionErrorCode.VERSION_CONFLICT, MissionErrorCode.VERSION_CONFLICT.getStatus(),
					"Template " + id + " was modified concurrently; reload it", null);
		}
	}

	// ---- validation ----

	/**
	 * Rules of daily_mission.md §7.1/§8.3 on the state after the change. The reward item is checked against the Shop
	 * only when it is set or changed, or the template is being switched on, so a later archive of the item does not
	 * block unrelated edits or switching the template off.
	 */
	private List<Violation> validate(Form f, boolean checkRewardItem) {
		List<Violation> v = new ArrayList<>();
		text(v, "code", f.code(), CODE_MAX);
		text(v, "name", f.name(), NAME_MAX);
		text(v, "description", f.description(), DESCRIPTION_MAX);
		required(v, "category", f.category());
		required(v, "triggerEvent", f.triggerEvent());
		required(v, "eligibilityRule", f.eligibilityRule());
		required(v, "isActive", f.isActive());
		min(v, "targetValue", f.targetValue(), 1);
		min(v, "weight", f.weight(), 1);
		min(v, "rewardCoin", f.rewardCoin(), 0);
		min(v, "rewardXp", f.rewardXp(), 0);
		if (f.minQuizAccuracyPercent() != null && (f.minQuizAccuracyPercent() < 1 || f.minQuizAccuracyPercent() > 100)) {
			v.add(violation("minQuizAccuracyPercent", "1..100 or null"));
		}
		if (f.quizRequestedCount() != null && !QUIZ_SIZES.contains(f.quizRequestedCount())) {
			v.add(violation("quizRequestedCount", "5, 10, 20 or null"));
		}
		if (f.triggerEvent() != null && f.triggerEvent() != MissionTriggerEvent.QUIZ_COMPLETED
				&& (f.minQuizAccuracyPercent() != null || f.quizRequestedCount() != null)) {
			v.add(violation(f.quizRequestedCount() != null ? "quizRequestedCount" : "minQuizAccuracyPercent",
					"quiz filters require triggerEvent QUIZ_COMPLETED"));
		}
		String item = f.rewardItemCode();
		if ((item == null) != (f.rewardItemFallbackCoin() == null)) {
			v.add(violation("rewardItemFallbackCoin", "rewardItemCode and rewardItemFallbackCoin go together"));
		} else if (f.rewardItemFallbackCoin() != null && f.rewardItemFallbackCoin() < 0) {
			v.add(violation("rewardItemFallbackCoin", ">= 0"));
		}
		if (item == null && (f.rewardCoin() == null || f.rewardCoin() <= 0) && (f.rewardXp() == null || f.rewardXp() <= 0)) {
			v.add(violation("rewardCoin", "needs Coin, XP or a reward item"));
		}
		if (item != null && checkRewardItem && shopItemRepository.findByCode(item)
				.filter(i -> i.getStatus() == ShopItemStatus.PUBLISHED).isEmpty()) {
			v.add(new Violation("rewardItemCode", MissionErrorCode.REWARD_ITEM_NOT_ELIGIBLE,
					"shop item " + item + " does not exist or is not PUBLISHED"));
		}
		return v;
	}

	/**
	 * §4.2 on the locked catalog after the change. An empty active set is the "not activated" state of §7.7 and is
	 * allowed: it is the only way a catalog without seed can be built up before it goes live.
	 */
	private static void requireInvariant(List<MissionTemplate> catalog) {
		List<MissionTemplate> active = catalog.stream().filter(MissionTemplate::getActive).toList();
		if (!active.isEmpty() && !MissionTracker.satisfiesInvariant(active)) {
			throw new MissionException(MissionErrorCode.MISSION_POOL_INSUFFICIENT, HttpStatus.CONFLICT,
					"Change would leave the active catalog below the §4.2 invariant", null);
		}
	}

	private static boolean activating(MissionTemplate t, Form f) {
		return !t.getActive() && Boolean.TRUE.equals(f.isActive());
	}

	private static void text(List<Violation> v, String field, String value, int max) {
		if (value == null || value.isBlank() || value.length() > max) {
			v.add(violation(field, "1.." + max + " characters, not blank"));
		}
	}

	private static void required(List<Violation> v, String field, Object value) {
		if (value == null) {
			v.add(violation(field, "required"));
		}
	}

	private static void min(List<Violation> v, String field, Integer value, int min) {
		if (value == null || value < min) {
			v.add(violation(field, "required, >= " + min));
		}
	}

	private static void throwIfAny(List<Violation> v) {
		if (!v.isEmpty()) {
			Violation first = v.get(0);
			throw new MissionException(first.code(), first.code().getStatus(), first.field() + ": " + first.message(),
					Map.of("errors", v));
		}
	}

	// ---- helpers ----

	/** Overlays the JSON body onto {@code base}; unknown or server-managed fields are rejected, never ignored. */
	private Form merge(Form base, JsonNode body, Set<String> allowed) {
		if (body == null || !body.isObject()) {
			throw invalid("body", "JSON object required");
		}
		List<Violation> unknown = new ArrayList<>();
		body.properties().forEach(e -> {
			if (!allowed.contains(e.getKey())) {
				unknown.add(violation(e.getKey(), "not an editable field"));
			}
		});
		throwIfAny(unknown);
		ObjectNode merged = objectMapper.valueToTree(base);
		merged.setAll((ObjectNode) body);
		try {
			return objectMapper.treeToValue(merged, Form.class);
		} catch (JsonProcessingException e) {
			String field = e instanceof JsonMappingException m && !m.getPath().isEmpty()
					? m.getPath().get(0).getFieldName() : "body";
			throw invalid(field, "wrong value type");
		}
	}

	private static Form formOf(MissionTemplate t) {
		return new Form(t.getCode(), t.getName(), t.getDescription(), t.getCategory(), t.getTriggerEvent(),
				t.getMinQuizAccuracyPercent(), t.getQuizRequestedCount(), t.getTargetValue(), t.getRewardCoin(),
				t.getRewardXp(), t.getRewardItemCode(), t.getRewardItemFallbackCoin(), t.getWeight(),
				t.getEligibilityRule(), t.getActive());
	}

	/** Only called after validation, so every value is well-formed. Code and active state are set by the caller. */
	private static void apply(Form f, MissionTemplate t) {
		t.setName(f.name().trim());
		t.setDescription(f.description().trim());
		t.setCategory(f.category());
		t.setTriggerEvent(f.triggerEvent());
		t.setMinQuizAccuracyPercent(f.minQuizAccuracyPercent());
		t.setQuizRequestedCount(f.quizRequestedCount());
		t.setTargetValue(f.targetValue());
		t.setRewardCoin(f.rewardCoin());
		t.setRewardXp(f.rewardXp());
		t.setRewardItemCode(f.rewardItemCode());
		t.setRewardItemFallbackCoin(f.rewardItemFallbackCoin());
		t.setWeight(f.weight());
		t.setEligibilityRule(f.eligibilityRule());
	}

	private static MissionCategory parseCategory(String value) {
		return Arrays.stream(MissionCategory.values()).filter(c -> c.name().equals(value)).findFirst()
				.orElseThrow(() -> invalid("category", "must be one of " + Arrays.toString(MissionCategory.values())));
	}

	private static Violation violation(String field, String message) {
		return new Violation(field, MissionErrorCode.VALIDATION_ERROR, message);
	}

	private static MissionException invalid(String field, String message) {
		return new MissionException(MissionErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, field + ": " + message,
				Map.of("errors", List.of(violation(field, message))));
	}

	private static MissionException notFound(Long id) {
		return new MissionException(MissionErrorCode.MISSION_TEMPLATE_NOT_FOUND, HttpStatus.NOT_FOUND,
				"Mission template " + id + " not found", null);
	}
}
