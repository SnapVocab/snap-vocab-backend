package vn.ptit.snapvocab.user.service;

import java.security.SecureRandom;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import vn.ptit.snapvocab.common.constant.OtpPurpose;

@Service
@RequiredArgsConstructor
public class OTPService {

	private static final String OTP_PREFIX = "otp:";
	private static final String ATTEMPTS_PREFIX = "otp-attempts:";
	private static final int OTP_TTL = 5;
	/**
	 * Wrong guesses one code survives. A 6-digit code without a limit can be brute-forced within its 5 minutes,
	 * and checking a reset code before the new password is typed adds a second guessing endpoint.
	 */
	static final int MAX_WRONG_ATTEMPTS = 5;
	private final RedissonClient redissonClient;

	public String saveOtp(String email, OtpPurpose otpPurpose) {
		String key = buildKey(email, otpPurpose);
		SecureRandom secureRandom = new SecureRandom();
		int otp = 100000 + secureRandom.nextInt(900000);
		String code = String.valueOf(otp);
		redissonClient.<String>getBucket(key).set(code, Duration.ofMinutes(OTP_TTL));
		// A new code starts with a clean count.
		redissonClient.getAtomicLong(attemptsKey(email, otpPurpose)).delete();
		return code;
	}

	private String buildKey(String email, OtpPurpose otpPurpose) {
		// key format: otp:SIGNUP_VERIFY:email,
		return OTP_PREFIX + otpPurpose.toString() + ":" + email;
	}

	private String attemptsKey(String email, OtpPurpose otpPurpose) {
		return ATTEMPTS_PREFIX + otpPurpose.toString() + ":" + email;
	}

	/** Checks and consumes the code. */
	public boolean verifyOtp(String email, String code, OtpPurpose otpPurpose) {
		if (!matches(email, code, otpPurpose)) {
			return false;
		}
		redissonClient.<String>getBucket(buildKey(email, otpPurpose)).delete();
		redissonClient.getAtomicLong(attemptsKey(email, otpPurpose)).delete();
		return true;
	}

	/**
	 * Checks the code without consuming it, so the reset screen can say "wrong code" before the learner types a new
	 * password. Wrong guesses count against the same limit as {@link #verifyOtp}.
	 */
	public boolean checkOtp(String email, String code, OtpPurpose otpPurpose) {
		return matches(email, code, otpPurpose);
	}

	private boolean matches(String email, String code, OtpPurpose otpPurpose) {
		String key = buildKey(email, otpPurpose);
		String savedOtp = redissonClient.<String>getBucket(key).get();
		if (savedOtp == null) {
			return false;
		}
		if (savedOtp.equals(code)) {
			return true;
		}
		RAtomicLong attempts = redissonClient.getAtomicLong(attemptsKey(email, otpPurpose));
		long wrong = attempts.incrementAndGet();
		if (wrong == 1) {
			attempts.expire(Duration.ofMinutes(OTP_TTL));
		}
		if (wrong >= MAX_WRONG_ATTEMPTS) {
			// Burnt: the learner asks for a new code.
			redissonClient.<String>getBucket(key).delete();
			attempts.delete();
		}
		return false;
	}
}
