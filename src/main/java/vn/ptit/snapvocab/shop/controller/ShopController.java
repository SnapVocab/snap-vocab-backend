package vn.ptit.snapvocab.shop.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.security.SecurityUtils;
import vn.ptit.snapvocab.shop.domain.dto.ActivationResultDTO;
import vn.ptit.snapvocab.shop.domain.dto.EquipRequest;
import vn.ptit.snapvocab.shop.domain.dto.EquipmentDTO;
import vn.ptit.snapvocab.shop.domain.dto.InventoryDTO;
import vn.ptit.snapvocab.shop.domain.dto.PurchaseRequest;
import vn.ptit.snapvocab.shop.domain.dto.PurchaseResultDTO;
import vn.ptit.snapvocab.shop.domain.dto.ShopItemDTO;
import vn.ptit.snapvocab.shop.domain.dto.WalletDTO;
import vn.ptit.snapvocab.shop.service.InventoryService;
import vn.ptit.snapvocab.shop.service.ShopService;
import vn.ptit.snapvocab.shop.service.WalletService;

/** Learner Shop API (shop.md §10.1). The owner is always the JWT subject; no endpoint accepts a userId. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Shop")
public class ShopController {

	private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	private final ShopService shopService;
	private final InventoryService inventoryService;
	private final WalletService walletService;

	@GetMapping("/shop/items")
	@Operation(summary = "Items on sale with the caller's ownership and purchase eligibility")
	public ResponseEntity<List<ShopItemDTO>> catalog(@RequestParam(required = false) String type) {
		return ResponseEntity.ok(shopService.getCatalog(currentUserId(), type));
	}

	@PostMapping("/shop/items/{itemId}/purchase")
	@Operation(summary = "Buy one unit with coin; replay-safe via Idempotency-Key")
	public ResponseEntity<PurchaseResultDTO> purchase(@PathVariable Long itemId,
			@RequestBody(required = false) PurchaseRequest request,
			@RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
		Long expectedPrice = request == null ? null : request.expectedPrice();
		return ResponseEntity.ok(shopService.purchase(currentUserId(), itemId, expectedPrice, idempotencyKey));
	}

	@GetMapping("/me/items")
	@Operation(summary = "Inventory: owned items, equipped state, active booster and server time")
	public ResponseEntity<InventoryDTO> inventory() {
		return ResponseEntity.ok(inventoryService.getInventory(currentUserId()));
	}

	@PutMapping("/me/equipment/{slot}")
	@Operation(summary = "Equip an AVATAR_FRAME; replaces the item currently in the slot")
	public ResponseEntity<EquipmentDTO> equip(@PathVariable String slot,
			@RequestBody(required = false) EquipRequest request) {
		Long itemId = request == null ? null : request.itemId();
		return ResponseEntity.ok(inventoryService.equip(currentUserId(), slot, itemId));
	}

	@DeleteMapping("/me/equipment/{slot}")
	@Operation(summary = "Unequip a slot (back to default)")
	public ResponseEntity<EquipmentDTO> unequip(@PathVariable String slot) {
		return ResponseEntity.ok(inventoryService.unequip(currentUserId(), slot));
	}

	@PostMapping("/me/items/{itemId}/activate")
	@Operation(summary = "Activate one XP Booster; replay-safe via Idempotency-Key")
	public ResponseEntity<ActivationResultDTO> activate(@PathVariable Long itemId,
			@RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
		return ResponseEntity.ok(inventoryService.activate(currentUserId(), itemId, idempotencyKey));
	}

	@GetMapping("/me/wallet")
	@Operation(summary = "Coin balance and ledger history, newest first")
	public ResponseEntity<WalletDTO> wallet(@RequestParam(required = false) Long cursor,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(walletService.getWallet(currentUserId(), cursor, size));
	}

	private static Long currentUserId() {
		return SecurityUtils.getCurrentUserId().orElseThrow(() -> new AccessDeniedException("Authentication required"));
	}
}
