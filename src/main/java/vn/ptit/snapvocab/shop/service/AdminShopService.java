package vn.ptit.snapvocab.shop.service;

import static java.util.Map.entry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.common.dto.Meta;
import vn.ptit.snapvocab.common.dto.PaginationObject;
import vn.ptit.snapvocab.storage.domain.dto.PresignedUploadResponse;
import vn.ptit.snapvocab.storage.service.StorageService;
import vn.ptit.snapvocab.shop.domain.ShopItem;
import vn.ptit.snapvocab.shop.domain.dto.AdminShopItemDTO;
import vn.ptit.snapvocab.shop.domain.dto.ShopItemForm;
import vn.ptit.snapvocab.shop.domain.dto.ShopItemTypeMetadataDTO;
import vn.ptit.snapvocab.shop.domain.dto.ShopItemTypeMetadataDTO.FieldSpec;
import vn.ptit.snapvocab.shop.domain.dto.ShopItemTypeMetadataDTO.InputType;
import vn.ptit.snapvocab.shop.domain.dto.ShopItemTypeMetadataDTO.TypeSpec;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopErrorCode;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemStatus;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;
import vn.ptit.snapvocab.shop.domain.enumeration.ThemeKey;
import vn.ptit.snapvocab.shop.exception.ShopException;
import vn.ptit.snapvocab.shop.repository.ShopItemRepository;

/** Admin catalog management (shop.md §4.1, §8): DRAFT → PUBLISHED → ARCHIVED, field locks, validation. */
@Service
@RequiredArgsConstructor
public class AdminShopService {

	// Developer-defined limits (shop.md §2.3, §8.3).
	static final int MAX_PAGE_SIZE = 100;
	static final long PRICE_MIN = 1;
	static final long PRICE_MAX = 100_000;
	static final int DURATION_MIN = 5;
	static final int DURATION_MAX = 180;
	static final int DURATION_DEFAULT = 30;
	static final int MAX_QUANTITY_MIN = 1;
	static final int MAX_QUANTITY_MAX = 10;
	static final int MAX_QUANTITY_DEFAULT = 5;
	static final int NAME_MAX = 100;
	static final int DESCRIPTION_MAX = 500;
	static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{2,63}$");
	static final String ASSET_PREFIX = "public/shop/";
	static final Pattern ASSET_KEY = Pattern.compile("^public/shop/[A-Za-z0-9-]+\\.(png|webp)$");
	static final List<String> ASSET_FORMATS = List.of("png", "webp");
	static final long ICON_MAX_BYTES = 200 * 1024;
	static final long FRAME_MAX_BYTES = 300 * 1024;

	private static final Set<ShopItemStatus> DRAFT_ONLY = EnumSet.of(ShopItemStatus.DRAFT);
	private static final Set<ShopItemStatus> UNTIL_ARCHIVED = EnumSet.of(ShopItemStatus.DRAFT, ShopItemStatus.PUBLISHED);
	private static final Set<ShopItemStatus> ALWAYS = EnumSet.allOf(ShopItemStatus.class);

	/** shop.md §8.2 — statuses in which each form field may change. Drives FIELD_LOCKED and the form metadata. */
	static final Map<String, Set<ShopItemStatus>> EDITABLE_IN = Map.ofEntries(
			entry("code", DRAFT_ONLY), entry("type", DRAFT_ONLY),
			entry("themeKey", DRAFT_ONLY), entry("frameAssetKey", DRAFT_ONLY), entry("boostDurationMinutes", DRAFT_ONLY),
			entry("name", ALWAYS), entry("description", ALWAYS), entry("iconKey", ALWAYS), entry("sortOrder", ALWAYS),
			entry("price", UNTIL_ARCHIVED), entry("purchasable", UNTIL_ARCHIVED), entry("availableFrom", UNTIL_ARCHIVED),
			entry("availableUntil", UNTIL_ARCHIVED), entry("maxQuantity", UNTIL_ARCHIVED));

	/** Type-specific fields; a field owned by another type must be null. */
	private static final Map<ShopItemType, List<String>> TYPE_FIELDS = Map.of(
			ShopItemType.THEME, List.of("themeKey"),
			ShopItemType.AVATAR_FRAME, List.of("frameAssetKey"),
			ShopItemType.XP_BOOSTER, List.of("boostDurationMinutes", "maxQuantity"));

	private static final ShopItemForm NEW_ITEM = new ShopItemForm(null, null, null, null, null, false, null, null,
			null, 0, null, null, null, null);

	private final ShopItemRepository repository;
	private final StorageService storageService;
	private final ShopMapper mapper;
	private final ObjectMapper objectMapper;

	// ---- queries ----

	@Transactional(readOnly = true)
	public PaginationObject<List<AdminShopItemDTO>> list(String status, String type, String q, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw invalid("page", ShopErrorCode.VALIDATION_ERROR, "page >= 0 and 1 <= size <= " + MAX_PAGE_SIZE);
		}
		// ponytail: '%' and '_' in q act as LIKE wildcards; they only widen the match, escape if that ever matters.
		String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
		Page<ShopItem> result = repository.search(parseFilter(ShopItemStatus.class, "status", status),
				parseFilter(ShopItemType.class, "type", type), pattern,
				PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
		return PaginationObject.<List<AdminShopItemDTO>>builder()
				.data(result.getContent().stream().map(this::toDto).toList())
				.meta(Meta.builder()
						.page((long) page)
						.size((long) size)
						.total(result.getTotalElements())
						.pages((long) result.getTotalPages())
						.build())
				.build();
	}

	@Transactional(readOnly = true)
	public AdminShopItemDTO get(Long id) {
		return toDto(find(id));
	}

	@Transactional(readOnly = true)
	public ShopItemTypeMetadataDTO itemTypes() {
		Set<ThemeKey> used = EnumSet.noneOf(ThemeKey.class);
		used.addAll(repository.findUsedThemeKeys());
		List<String> freeThemeKeys = Arrays.stream(ThemeKey.values())
				.filter(k -> k != ThemeKey.DEFAULT && !used.contains(k))
				.map(Enum::name)
				.toList();

		List<FieldSpec> common = List.of(
				spec("code", InputType.TEXT).required(true).requiredToPublish(true).pattern(CODE.pattern()).build(),
				spec("type", InputType.SELECT).required(true).requiredToPublish(true)
						.options(Arrays.stream(ShopItemType.values()).map(Enum::name).toList()).build(),
				spec("name", InputType.TEXT).required(true).requiredToPublish(true).min(1L).max((long) NAME_MAX).build(),
				spec("description", InputType.TEXTAREA).max((long) DESCRIPTION_MAX).build(),
				spec("iconKey", InputType.IMAGE).requiredToPublish(true).formats(ASSET_FORMATS).maxBytes(ICON_MAX_BYTES)
						.dimensions("square, >= 256x256").build(),
				spec("purchasable", InputType.BOOLEAN).required(true).requiredToPublish(true).defaultValue(false).build(),
				spec("price", InputType.NUMBER).min(PRICE_MIN).max(PRICE_MAX).build(),
				spec("availableFrom", InputType.DATETIME).build(),
				spec("availableUntil", InputType.DATETIME).build(),
				spec("sortOrder", InputType.NUMBER).required(true).requiredToPublish(true).min(0L).defaultValue(0).build());

		List<TypeSpec> types = List.of(
				new TypeSpec(ShopItemType.THEME, ShopItemType.THEME.getBehavior(), ShopItemType.THEME.isStackable(),
						Map.of(), List.of(spec("themeKey", InputType.SELECT).required(true).requiredToPublish(true)
								.options(freeThemeKeys).build())),
				new TypeSpec(ShopItemType.AVATAR_FRAME, ShopItemType.AVATAR_FRAME.getBehavior(),
						ShopItemType.AVATAR_FRAME.isStackable(), Map.of(),
						List.of(spec("frameAssetKey", InputType.IMAGE).required(true).requiredToPublish(true)
								.formats(ASSET_FORMATS).maxBytes(FRAME_MAX_BYTES).dimensions("512x512, transparent")
								.build())),
				new TypeSpec(ShopItemType.XP_BOOSTER, ShopItemType.XP_BOOSTER.getBehavior(),
						ShopItemType.XP_BOOSTER.isStackable(),
						Map.of("multiplier", ShopItemType.XP_BOOSTER_MULTIPLIER),
						List.of(spec("boostDurationMinutes", InputType.NUMBER).required(true).requiredToPublish(true)
										.min((long) DURATION_MIN).max((long) DURATION_MAX).defaultValue(DURATION_DEFAULT).build(),
								spec("maxQuantity", InputType.NUMBER).required(true).requiredToPublish(true)
										.min((long) MAX_QUANTITY_MIN).max((long) MAX_QUANTITY_MAX)
										.defaultValue(MAX_QUANTITY_DEFAULT).build())));
		return new ShopItemTypeMetadataDTO(common, types);
	}

	private static FieldSpec.FieldSpecBuilder spec(String name, InputType inputType) {
		return FieldSpec.builder().name(name).inputType(inputType).editableIn(EDITABLE_IN.get(name));
	}

	// ---- commands ----

	@Transactional
	public AdminShopItemDTO create(JsonNode body) {
		ShopItemForm form = merge(NEW_ITEM, body, false);
		validate(form, null, false);
		ShopItem item = ShopItem.builder().status(ShopItemStatus.DRAFT).build();
		apply(form, item);
		return toDto(save(item));
	}

	@Transactional
	public AdminShopItemDTO update(Long id, JsonNode body) {
		ShopItem item = find(id);
		JsonNode version = body == null ? null : body.get("version");
		if (version == null || !version.isIntegralNumber()) {
			throw invalid("version", ShopErrorCode.VALIDATION_ERROR, "version is required");
		}
		if (version.asLong() != item.getVersion()) {
			throw new ShopException(ShopErrorCode.VERSION_CONFLICT, "Item was modified by someone else; reload it",
					Map.of("currentVersion", item.getVersion()));
		}
		ShopItemForm current = formOf(item);
		ShopItemForm next = merge(current, body, true);

		ObjectNode before = objectMapper.valueToTree(current);
		ObjectNode after = objectMapper.valueToTree(next);
		List<String> locked = EDITABLE_IN.keySet().stream()
				.filter(field -> !before.get(field).equals(after.get(field)))
				.filter(field -> !EDITABLE_IN.get(field).contains(item.getStatus()))
				.sorted()
				.toList();
		if (!locked.isEmpty()) {
			throw new ShopException(ShopErrorCode.FIELD_LOCKED, "Fields locked while " + item.getStatus() + ": " + locked,
					Map.of("fields", locked, "status", item.getStatus()));
		}
		validate(next, item.getId(), false);
		apply(next, item);
		return toDto(save(item));
	}

	@Transactional
	public AdminShopItemDTO publish(Long id) {
		ShopItem item = find(id);
		requireStatus(item, ShopItemStatus.DRAFT, "publish");
		validate(formOf(item), item.getId(), true);
		item.setStatus(ShopItemStatus.PUBLISHED);
		item.setPublishedAt(Economy.now());
		return toDto(save(item));
	}

	/** Stops sales for good; ownership, equip and activation of owned units are untouched (shop.md §4.1). */
	@Transactional
	public AdminShopItemDTO archive(Long id) {
		ShopItem item = find(id);
		requireStatus(item, ShopItemStatus.PUBLISHED, "archive");
		item.setStatus(ShopItemStatus.ARCHIVED);
		item.setPurchasable(false);
		return toDto(save(item));
	}

	/** Hard delete, DRAFT only: a draft was never sellable or grantable, so nobody can own it. */
	@Transactional
	public void delete(Long id) {
		ShopItem item = find(id);
		if (item.getStatus() != ShopItemStatus.DRAFT) {
			throw new ShopException(ShopErrorCode.ITEM_CANNOT_BE_DELETED,
					"Only DRAFT items can be deleted; archive a " + item.getStatus() + " item instead",
					Map.of("status", item.getStatus()));
		}
		try {
			repository.delete(item);
			repository.flush();
		} catch (ObjectOptimisticLockingFailureException e) {
			throw concurrentModification(id);
		}
	}

	/** Presigned PUT for an icon/frame under {@code public/shop/} (shop.md §10.2, purpose SHOP_ASSET). */
	public PresignedUploadResponse assetUploadUrl(String fileName) {
		String ext = fileName == null ? "" : fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
		String contentType = switch (ext) {
			case "png" -> "image/png";
			case "webp" -> "image/webp";
			default -> throw invalid("fileName", ShopErrorCode.VALIDATION_ERROR, "Only PNG or WebP assets are allowed");
		};
		return storageService.createUploadUrl(ASSET_PREFIX + UUID.randomUUID() + "." + ext, contentType);
	}

	// ---- validation ----

	public record Violation(String field, ShopErrorCode code, String message) {
	}

	/**
	 * Full rule set of shop.md §8.3. Every save enforces structure, limits and type config; {@code publishing}
	 * additionally requires the icon and checks uploaded assets in storage. All violations are reported at once;
	 * the first one decides the top-level error code.
	 */
	private void validate(ShopItemForm f, Long id, boolean publishing) {
		List<Violation> v = new ArrayList<>();
		long selfId = id == null ? -1L : id;

		if (f.code() == null || !CODE.matcher(f.code()).matches()) {
			v.add(new Violation("code", ShopErrorCode.VALIDATION_ERROR, "must match " + CODE.pattern()));
		} else if (repository.existsByCodeAndIdNot(f.code(), selfId)) {
			v.add(new Violation("code", ShopErrorCode.SHOP_ITEM_CODE_EXISTS, "code " + f.code() + " is already used"));
		}
		ShopItemType type = parseEnum(ShopItemType.class, f.type());
		if (type == null) {
			v.add(new Violation("type", ShopErrorCode.VALIDATION_ERROR,
					"must be one of " + Arrays.toString(ShopItemType.values())));
		}
		if (f.name() == null || f.name().isBlank() || f.name().length() > NAME_MAX) {
			v.add(new Violation("name", ShopErrorCode.VALIDATION_ERROR, "1.." + NAME_MAX + " characters, not blank"));
		}
		if (f.description() != null && f.description().length() > DESCRIPTION_MAX) {
			v.add(new Violation("description", ShopErrorCode.VALIDATION_ERROR, "at most " + DESCRIPTION_MAX + " characters"));
		}
		checkAsset(v, "iconKey", f.iconKey(), ICON_MAX_BYTES, publishing, publishing, ShopErrorCode.VALIDATION_ERROR);
		if (f.purchasable() == null) {
			v.add(new Violation("purchasable", ShopErrorCode.VALIDATION_ERROR, "required"));
		}
		if (f.price() != null && (f.price() < PRICE_MIN || f.price() > PRICE_MAX)) {
			v.add(new Violation("price", ShopErrorCode.INVALID_PRICE, PRICE_MIN + ".." + PRICE_MAX + " coin"));
		} else if (f.price() == null && Boolean.TRUE.equals(f.purchasable())) {
			v.add(new Violation("price", ShopErrorCode.INVALID_PRICE, "required when purchasable"));
		}
		if (f.availableFrom() != null && f.availableUntil() != null && !f.availableFrom().isBefore(f.availableUntil())) {
			v.add(new Violation("availableUntil", ShopErrorCode.INVALID_AVAILABILITY_WINDOW,
					"must be after availableFrom"));
		}
		if (f.sortOrder() == null || f.sortOrder() < 0) {
			v.add(new Violation("sortOrder", ShopErrorCode.VALIDATION_ERROR, "required, >= 0"));
		}
		if (type != null) {
			validateTypeConfig(v, type, f, selfId, publishing);
		}

		if (!v.isEmpty()) {
			Violation first = v.get(0);
			throw new ShopException(first.code(), first.field() + ": " + first.message(), Map.of("errors", v));
		}
	}

	private void validateTypeConfig(List<Violation> v, ShopItemType type, ShopItemForm f, long selfId,
			boolean publishing) {
		Map<String, Object> config = new HashMap<>();
		config.put("themeKey", f.themeKey());
		config.put("frameAssetKey", f.frameAssetKey());
		config.put("boostDurationMinutes", f.boostDurationMinutes());
		config.put("maxQuantity", f.maxQuantity());
		TYPE_FIELDS.forEach((owner, fields) -> {
			if (owner != type) {
				fields.stream().filter(field -> config.get(field) != null).forEach(field -> v.add(new Violation(field,
						ShopErrorCode.INVALID_SHOP_ITEM_CONFIG, "must be null for " + type)));
			}
		});

		switch (type) {
			case THEME -> {
				if (f.themeKey() == null) {
					v.add(new Violation("themeKey", ShopErrorCode.INVALID_SHOP_ITEM_CONFIG, "required for THEME"));
					return;
				}
				ThemeKey key = parseEnum(ThemeKey.class, f.themeKey());
				if (key == null || key == ThemeKey.DEFAULT) {
					v.add(new Violation("themeKey", ShopErrorCode.INVALID_THEME_KEY,
							"not a sellable theme of the registry"));
				} else if (repository.existsByThemeKeyAndIdNot(key, selfId)) {
					v.add(new Violation("themeKey", ShopErrorCode.INVALID_SHOP_ITEM_CONFIG,
							"theme " + key + " is already sold by another item"));
				}
			}
			case AVATAR_FRAME -> checkAsset(v, "frameAssetKey", f.frameAssetKey(), FRAME_MAX_BYTES, true, publishing,
					ShopErrorCode.INVALID_SHOP_ITEM_CONFIG);
			case XP_BOOSTER -> {
				checkRange(v, "boostDurationMinutes", f.boostDurationMinutes(), DURATION_MIN, DURATION_MAX);
				checkRange(v, "maxQuantity", f.maxQuantity(), MAX_QUANTITY_MIN, MAX_QUANTITY_MAX);
			}
		}
	}

	private static void checkRange(List<Violation> v, String field, Integer value, int min, int max) {
		if (value == null || value < min || value > max) {
			v.add(new Violation(field, ShopErrorCode.INVALID_SHOP_ITEM_CONFIG, "required, " + min + ".." + max));
		}
	}

	/**
	 * Keys must come from the admin upload flow. Existence and size are checked at publish only, so a draft
	 * can be saved while the upload is still in flight.
	 */
	private void checkAsset(List<Violation> v, String field, String key, long maxBytes, boolean required,
			boolean publishing, ShopErrorCode code) {
		if (key == null) {
			if (required) {
				v.add(new Violation(field, code, "required"));
			}
			return;
		}
		if (!ASSET_KEY.matcher(key).matches()) {
			v.add(new Violation(field, code, "must be a key issued by /api/admin/shop/assets/upload-url"));
			return;
		}
		if (publishing) {
			Optional<Long> size = storageService.sizeOf(key);
			if (size.isEmpty()) {
				v.add(new Violation(field, code, "asset has not been uploaded"));
			} else if (size.get() > maxBytes) {
				v.add(new Violation(field, code, "asset exceeds " + maxBytes / 1024 + " KB"));
			}
		}
	}

	// ---- helpers ----

	/** Overlays the JSON body onto {@code base}; unknown or server-managed fields are rejected, never ignored. */
	private ShopItemForm merge(ShopItemForm base, JsonNode body, boolean allowVersion) {
		if (body == null || !body.isObject()) {
			throw invalid("body", ShopErrorCode.VALIDATION_ERROR, "JSON object required");
		}
		ObjectNode merged = objectMapper.valueToTree(base);
		List<Violation> unknown = new ArrayList<>();
		body.properties().forEach(e -> {
			if (EDITABLE_IN.containsKey(e.getKey())) {
				merged.set(e.getKey(), e.getValue());
			} else if (!(allowVersion && e.getKey().equals("version"))) {
				unknown.add(new Violation(e.getKey(), ShopErrorCode.VALIDATION_ERROR, "not an editable field"));
			}
		});
		if (!unknown.isEmpty()) {
			throw new ShopException(ShopErrorCode.VALIDATION_ERROR, "Unknown or server-managed fields",
					Map.of("errors", unknown));
		}
		try {
			return objectMapper.treeToValue(merged, ShopItemForm.class);
		} catch (JsonProcessingException e) {
			String field = e instanceof JsonMappingException m && !m.getPath().isEmpty()
					? m.getPath().get(0).getFieldName() : "body";
			throw invalid(field, ShopErrorCode.VALIDATION_ERROR, "wrong value type");
		}
	}

	private static ShopItemForm formOf(ShopItem i) {
		return new ShopItemForm(i.getCode(), i.getType().name(), i.getName(), i.getDescription(), i.getIconKey(),
				i.getPurchasable(), i.getPrice(), i.getAvailableFrom(), i.getAvailableUntil(), i.getSortOrder(),
				i.getThemeKey() == null ? null : i.getThemeKey().name(), i.getFrameAssetKey(),
				i.getBoostDurationMinutes(), i.getMaxQuantity());
	}

	/** Only called after {@link #validate}, so every value is well-formed. */
	private static void apply(ShopItemForm f, ShopItem item) {
		item.setCode(f.code());
		item.setType(ShopItemType.valueOf(f.type()));
		item.setName(f.name());
		item.setDescription(f.description());
		item.setIconKey(f.iconKey());
		item.setPurchasable(f.purchasable());
		item.setPrice(f.price());
		item.setAvailableFrom(f.availableFrom());
		item.setAvailableUntil(f.availableUntil());
		item.setSortOrder(f.sortOrder());
		item.setThemeKey(f.themeKey() == null ? null : ThemeKey.valueOf(f.themeKey()));
		item.setFrameAssetKey(f.frameAssetKey());
		item.setBoostDurationMinutes(f.boostDurationMinutes());
		item.setMaxQuantity(f.maxQuantity());
	}

	/** JPA {@code @Version} catches a writer that slipped in after our read. */
	private ShopItem save(ShopItem item) {
		try {
			return repository.saveAndFlush(item);
		} catch (ObjectOptimisticLockingFailureException e) {
			throw concurrentModification(item.getId());
		}
	}

	private ShopItem find(Long id) {
		return repository.findById(id).orElseThrow(
				() -> new ShopException(ShopErrorCode.SHOP_ITEM_NOT_FOUND, "Shop item " + id + " not found"));
	}

	private static void requireStatus(ShopItem item, ShopItemStatus required, String action) {
		if (item.getStatus() != required) {
			throw new ShopException(ShopErrorCode.INVALID_STATUS_TRANSITION,
					"Cannot " + action + " a " + item.getStatus() + " item",
					Map.of("status", item.getStatus(), "requiredStatus", required));
		}
	}

	private static ShopException concurrentModification(Long id) {
		return new ShopException(ShopErrorCode.VERSION_CONFLICT, "Item " + id + " was modified concurrently; reload it");
	}

	private static ShopException invalid(String field, ShopErrorCode code, String message) {
		return new ShopException(code, field + ": " + message, Map.of("errors", List.of(new Violation(field, code, message))));
	}

	private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
		return Arrays.stream(type.getEnumConstants()).filter(c -> c.name().equals(value)).findFirst().orElse(null);
	}

	private static <E extends Enum<E>> E parseFilter(Class<E> type, String field, String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		E parsed = parseEnum(type, value);
		if (parsed == null) {
			throw invalid(field, ShopErrorCode.VALIDATION_ERROR,
					"must be one of " + Arrays.toString(type.getEnumConstants()));
		}
		return parsed;
	}

	private AdminShopItemDTO toDto(ShopItem i) {
		return new AdminShopItemDTO(i.getId(), i.getVersion(), i.getCode(), i.getType(), i.getType().getBehavior(),
				i.getName(), i.getDescription(), i.getIconKey(), mapper.url(i.getIconKey()), i.getStatus(),
				Boolean.TRUE.equals(i.getPurchasable()), i.getPrice(), i.getAvailableFrom(), i.getAvailableUntil(),
				i.getSortOrder(), i.getThemeKey(), i.getFrameAssetKey(), mapper.url(i.getFrameAssetKey()),
				i.getBoostDurationMinutes(), i.getMaxQuantity(),
				i.getType() == ShopItemType.XP_BOOSTER ? ShopItemType.XP_BOOSTER_MULTIPLIER : null,
				i.getPublishedAt(), i.getCreatedAt(), i.getUpdatedAt());
	}
}
