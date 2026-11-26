package vn.ptit.snapvocab.mnemonic.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.snapvocab.mnemonic.dto.LeechListDTO;
import vn.ptit.snapvocab.mnemonic.dto.MnemonicResultDTO;
import vn.ptit.snapvocab.mnemonic.service.MnemonicService;
import vn.ptit.snapvocab.security.SecurityUtils;

@RestController
@RequestMapping("/api/study")
@RequiredArgsConstructor
@Tag(name = "Study mnemonics")
public class MnemonicController {

	private final MnemonicService mnemonicService;

	@PostMapping("/items/{topicItemId}/mnemonic")
	@Operation(summary = "Create or regenerate the AI mnemonic of a word forgotten at least twice")
	public ResponseEntity<MnemonicResultDTO> generate(@PathVariable Long topicItemId) {
		return ResponseEntity.ok(mnemonicService.generate(currentUserId(), topicItemId));
	}

	@DeleteMapping("/items/{topicItemId}/mnemonic")
	@Operation(summary = "Remove the learner's mnemonic of a word")
	public ResponseEntity<Void> delete(@PathVariable Long topicItemId) {
		mnemonicService.delete(currentUserId(), topicItemId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/leeches")
	@Operation(summary = "Words the learner keeps forgetting, with their mnemonics and today's AI quota")
	public ResponseEntity<LeechListDTO> leeches(@RequestParam(defaultValue = "20") int limit) {
		return ResponseEntity.ok(mnemonicService.leeches(currentUserId(), limit));
	}

	private static Long currentUserId() {
		return SecurityUtils.getCurrentUserId().orElseThrow(() -> new AccessDeniedException("Authentication required"));
	}
}
