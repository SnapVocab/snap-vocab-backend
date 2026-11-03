package vn.ptit.snapvocab.shop.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.audit.domain.dto.AuditReasonRequest;
import vn.ptit.snapvocab.common.dto.PaginationObject;
import vn.ptit.snapvocab.storage.domain.dto.PresignedUploadResponse;
import vn.ptit.snapvocab.storage.domain.dto.UploadUrlRequest;
import vn.ptit.snapvocab.shop.domain.dto.AdminShopItemDTO;
import vn.ptit.snapvocab.shop.domain.dto.ShopItemForm;
import vn.ptit.snapvocab.shop.domain.dto.ShopItemTypeMetadataDTO;
import vn.ptit.snapvocab.shop.service.AdminShopService;

@RestController
@RequestMapping("/api/admin/shop")
@RequiredArgsConstructor
@Tag(name = "Admin Shop")
public class AdminShopController {

	private final AdminShopService adminShopService;

	@GetMapping("/item-types")
	@Operation(summary = "Dynamic-form metadata: fields, limits, defaults, lock rules")
	public ResponseEntity<ShopItemTypeMetadataDTO> itemTypes() {
		return ResponseEntity.ok(adminShopService.itemTypes());
	}

	@GetMapping("/items")
	@Operation(summary = "Search items by status/type/q (code or name), newest update first")
	public ResponseEntity<PaginationObject<List<AdminShopItemDTO>>> list(
			@RequestParam(required = false) String status, @RequestParam(required = false) String type,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(adminShopService.list(status, type, q, page, size));
	}

	@GetMapping("/items/{itemId}")
	@Operation(summary = "Item detail for the editor")
	public ResponseEntity<AdminShopItemDTO> get(@PathVariable Long itemId) {
		return ResponseEntity.ok(adminShopService.get(itemId));
	}

	@PostMapping("/items")
	@Operation(summary = "Create a DRAFT item")
	public ResponseEntity<AdminShopItemDTO> create(@io.swagger.v3.oas.annotations.parameters.RequestBody(
			content = @Content(schema = @Schema(implementation = ShopItemForm.class))) @RequestBody JsonNode body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(adminShopService.create(body));
	}

	@PatchMapping("/items/{itemId}")
	@Operation(summary = "Partial update; body = any ShopItemForm fields + required version")
	public ResponseEntity<AdminShopItemDTO> update(@PathVariable Long itemId, @io.swagger.v3.oas.annotations.parameters.RequestBody(
			content = @Content(schema = @Schema(implementation = ShopItemForm.class))) @RequestBody JsonNode body) {
		return ResponseEntity.ok(adminShopService.update(itemId, body));
	}

	@PostMapping("/items/{itemId}/publish")
	@Operation(summary = "DRAFT → PUBLISHED after full revalidation; reason required when the price is past the guardrail")
	public ResponseEntity<AdminShopItemDTO> publish(@PathVariable Long itemId,
			@RequestBody(required = false) AuditReasonRequest body) {
		return ResponseEntity.ok(adminShopService.publish(itemId, body == null ? null : body.reason()));
	}

	@PostMapping("/items/{itemId}/archive")
	@Operation(summary = "PUBLISHED → ARCHIVED (terminal, stops sales); body { reason } required")
	public ResponseEntity<AdminShopItemDTO> archive(@PathVariable Long itemId,
			@RequestBody(required = false) AuditReasonRequest body) {
		return ResponseEntity.ok(adminShopService.archive(itemId, body == null ? null : body.reason()));
	}

	@DeleteMapping("/items/{itemId}")
	@Operation(summary = "Hard delete, DRAFT only")
	public ResponseEntity<Void> delete(@PathVariable Long itemId) {
		adminShopService.delete(itemId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/assets/upload-url")
	@Operation(summary = "Presigned PUT for a PNG/WebP icon or frame; store the returned objectName as iconKey/frameAssetKey")
	public ResponseEntity<PresignedUploadResponse> assetUploadUrl(@RequestBody UploadUrlRequest request) {
		return ResponseEntity.ok(adminShopService.assetUploadUrl(request.fileName()));
	}
}
