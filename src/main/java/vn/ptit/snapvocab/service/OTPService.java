package vn.ptit.snapvocab.service;

import java.security.SecureRandom;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import vn.ptit.snapvocab.util.constant.OtpPurpose;

@Service
@RequiredArgsConstructor
public class OTPService {

	private static final String OTP_PREFIX = "otp:";
	private static final int OTP_TTL = 5;
	private final RedissonClient redissonClient;

	public String saveOtp(String email, OtpPurpose otpPurpose) {
		String key = buildKey(email, otpPurpose);
		SecureRandom secureRandom = new SecureRandom();
		int otp = 100000 + secureRandom.nextInt(900000);
		String code = String.valueOf(otp);
		redissonClient.<String>getBucket(key).set(code, Duration.ofMinutes(OTP_TTL));
		return code;
	}

	private String buildKey(String email, OtpPurpose otpPurpose) {
		//key format: otp:SIGNUP_VERIFY:email,
		return OTP_PREFIX + otpPurpose.toString() + ":" + email;
	}

	public boolean verifyOtp(String email, String code, OtpPurpose otpPurpose) {
		String key = buildKey(email, otpPurpose);
		String savedOtp = redissonClient.<String>getBucket(key).get();

		if (savedOtp == null) {
			return false;
		}

		if (!savedOtp.equals(code)) {
			return false;
		}

		redissonClient.<String>getBucket(key).delete();

		return true;
	}
}
