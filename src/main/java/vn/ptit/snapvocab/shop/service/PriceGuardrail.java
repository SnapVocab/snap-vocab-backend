package vn.ptit.snapvocab.shop.service;

import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.ptit.snapvocab.shop.domain.ShopItem;
import vn.ptit.snapvocab.shop.domain.enumeration.ShopItemType;
import vn.ptit.snapvocab.shop.repository.ShopItemRepository;

/**
 * design.md §7, same rule as the admin's priceHint: ×1.5 band, at least 3 items on sale to compare. Never blocks a
 * save; past it the admin must give a reason (admin_audit_log_backend.md §4.5.1).
 */
@Component
@RequiredArgsConstructor
class PriceGuardrail {

	static final double FACTOR = 1.5;
	static final int MIN_SAMPLE = 3;
	private static final Map<ShopItemType, String> TYPE_LABEL = Map.of(ShopItemType.XP_BOOSTER, "XP Booster",
			ShopItemType.AVATAR_FRAME, "Khung avatar",
			ShopItemType.STREAK_FREEZE, "Đóng băng chuỗi");

	private final ShopItemRepository repository;

	/** Message when the price is far outside what {@code type} sells for right now; empty otherwise. */
	Optional<String> check(Long selfId, ShopItemType type, Boolean purchasable, Long price) {
		if (!Boolean.TRUE.equals(purchasable) || price == null) {
			return Optional.empty();
		}
		List<ShopItem> band = repository.findCatalog(Economy.now(), type).stream()
				.filter(i -> !Objects.equals(i.getId(), selfId) && i.getPrice() != null)
				.toList();
		if (band.size() < MIN_SAMPLE) {
			return Optional.empty();
		}
		ShopItem max = band.stream().max(Comparator.comparing(ShopItem::getPrice)).orElseThrow();
		ShopItem min = band.stream().min(Comparator.comparing(ShopItem::getPrice)).orElseThrow();
		String label = TYPE_LABEL.get(type);
		if (price > max.getPrice() * FACTOR) {
			return Optional.of("Giá %s Coin cao hơn 1,5 lần giá cao nhất của %s đang bán (%s, %s Coin)."
					.formatted(vi(price), label, max.getName(), vi(max.getPrice())));
		}
		if (price < min.getPrice() / FACTOR) {
			return Optional.of("Giá %s Coin thấp hơn 2/3 giá thấp nhất của %s đang bán (%s, %s Coin)."
					.formatted(vi(price), label, min.getName(), vi(min.getPrice())));
		}
		return Optional.empty();
	}

	/** 2400 → "2.400". */
	static String vi(long n) {
		return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(n);
	}
}
