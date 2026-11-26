package vn.ptit.snapvocab.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import vn.ptit.snapvocab.common.constant.OtpPurpose;

/** A 6-digit code must not be guessable within its lifetime: 5 wrong tries burn it. */
@ExtendWith(MockitoExtension.class)
class OTPServiceTest {

	private static final String EMAIL = "learner@example.com";

	@Mock
	private RedissonClient redisson;
	@Mock
	private RBucket<Object> bucket;
	@Mock
	private RAtomicLong attempts;

	private OTPService service;
	private String stored;
	private final AtomicLong wrong = new AtomicLong();

	@BeforeEach
	void setUp() {
		service = new OTPService(redisson);
		stored = "123456";
		lenient().when(redisson.getBucket(anyString())).thenReturn(bucket);
		lenient().when(redisson.getAtomicLong(anyString())).thenReturn(attempts);
		lenient().when(bucket.get()).thenAnswer(inv -> stored);
		lenient().when(bucket.delete()).thenAnswer(inv -> {
			stored = null;
			return true;
		});
		lenient().when(attempts.incrementAndGet()).thenAnswer(inv -> wrong.incrementAndGet());
		lenient().when(attempts.delete()).thenAnswer(inv -> {
			wrong.set(0);
			return true;
		});
	}

	@Test
	void checkingARightCodeKeepsItForTheReset() {
		assertThat(service.checkOtp(EMAIL, "123456", OtpPurpose.FORGOT_PASSWORD)).isTrue();
		verify(bucket, never()).delete();
		assertThat(service.verifyOtp(EMAIL, "123456", OtpPurpose.FORGOT_PASSWORD)).isTrue();
		assertThat(stored).isNull();
	}

	@Test
	void fiveWrongGuessesBurnTheCodeEvenIfTheSixthIsRight() {
		for (int i = 0; i < OTPService.MAX_WRONG_ATTEMPTS; i++) {
			assertThat(service.checkOtp(EMAIL, "000000", OtpPurpose.FORGOT_PASSWORD)).isFalse();
		}
		assertThat(service.verifyOtp(EMAIL, "123456", OtpPurpose.FORGOT_PASSWORD)).isFalse();
	}

	@Test
	void aNewCodeStartsWithACleanCount() {
		service.checkOtp(EMAIL, "000000", OtpPurpose.SIGNUP_VERIFY);
		when(redisson.getBucket(anyString())).thenReturn(bucket);
		service.saveOtp(EMAIL, OtpPurpose.SIGNUP_VERIFY);
		verify(bucket).set(any(), any(java.time.Duration.class));
		assertThat(wrong.get()).isZero();
	}
}
