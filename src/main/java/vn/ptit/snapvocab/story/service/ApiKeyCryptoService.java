package vn.ptit.snapvocab.story.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import vn.ptit.snapvocab.story.config.StoryProperties;

@Service
public class ApiKeyCryptoService {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_LENGTH = 12;
	private static final int TAG_BITS = 128;

	private final SecretKeySpec keySpec;
	private final SecureRandom secureRandom = new SecureRandom();

	public ApiKeyCryptoService(StoryProperties properties) {
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			byte[] keyBytes = sha.digest(properties.getEncryptionSecret().getBytes(StandardCharsets.UTF_8));
			this.keySpec = new SecretKeySpec(keyBytes, "AES");
		} catch (Exception e) {
			throw new IllegalStateException("Failed to initialize API key crypto", e);
		}
	}

	public String encrypt(String plaintext) {
		if (plaintext == null || plaintext.isBlank()) {
			return null;
		}
		try {
			byte[] iv = new byte[IV_LENGTH];
			secureRandom.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plaintext.trim().getBytes(StandardCharsets.UTF_8));
			byte[] combined = new byte[iv.length + encrypted.length];
			System.arraycopy(iv, 0, combined, 0, iv.length);
			System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
			return Base64.getEncoder().encodeToString(combined);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to encrypt API key", e);
		}
	}

	public String decrypt(String ciphertext) {
		if (ciphertext == null || ciphertext.isBlank()) {
			return null;
		}
		try {
			byte[] combined = Base64.getDecoder().decode(ciphertext);
			if (combined.length <= IV_LENGTH) {
				return null;
			}
			byte[] iv = new byte[IV_LENGTH];
			byte[] encrypted = new byte[combined.length - IV_LENGTH];
			System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
			System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
			return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to decrypt API key", e);
		}
	}

	public String mask(String plaintext) {
		if (plaintext == null || plaintext.isBlank()) {
			return "";
		}
		String trimmed = plaintext.trim();
		if (trimmed.length() <= 8) {
			return "••••••••";
		}
		return trimmed.substring(0, 4) + "••••" + trimmed.substring(trimmed.length() - 4);
	}
}

