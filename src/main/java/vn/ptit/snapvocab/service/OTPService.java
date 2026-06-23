package vn.ptit.snapvocab.service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OTPService {

	private static final String OTP_PREFIX = "otp:";
	private static final int OTP_TTL = 5;
	private final RedissonClient redissonClient;

	public String saveOtp(String email) {
		SecureRandom secureRandom = new SecureRandom();
		int otp = 100000 + secureRandom.nextInt(900000);
		String code = String.valueOf(otp);
		redissonClient.<String>getBucket(OTP_PREFIX + email).set(code, OTP_TTL, TimeUnit.MINUTES);
		return code;
	}

	public boolean verifyOtp(String email, String code) {
		String savedOtp = redissonClient.<String>getBucket(OTP_PREFIX + email).get();

		if (savedOtp == null) {
			return false;
		}

		if (!savedOtp.equals(code)) {
			return false;
		}

		redissonClient.<String>getBucket(OTP_PREFIX + email).delete();

		return true;
	}
}
