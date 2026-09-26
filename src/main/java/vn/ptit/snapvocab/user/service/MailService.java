package vn.ptit.snapvocab.user.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
public class MailService {

	public final TemplateEngine templateEngine;
	private final JavaMailSender javaMailSender;
	@Value("${spring.mail.username}")
	private String from;

	@Async
	public void sendVerificationEmail(
			String to,
			String code,
			Duration ttl
	) throws MessagingException {

		Context context = new Context();
		context.setVariable("code", code);
		context.setVariable("ttl", ttl.toMinutes() + " minutes");
		context.setVariable("year", Year.now().getValue());

		String html = templateEngine.process("activationEmail", context);

		MimeMessage message = javaMailSender.createMimeMessage();

		MimeMessageHelper helper = new MimeMessageHelper(
				message,
				MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
				StandardCharsets.UTF_8.name());

		helper.setFrom(from);
		helper.setTo(to);
		helper.setSubject("SnapVocab - Verify your email");
		helper.setText(html, true);

		javaMailSender.send(message);
	}

	@Async
	public void sendPasswordResetEmail(
			String to,
			String code,
			Duration ttl
	) throws MessagingException {

		Context context = new Context();
		context.setVariable("code", code);
		context.setVariable("ttl", ttl.toMinutes() + " minutes");
		context.setVariable("year", Year.now().getValue());

		String html = templateEngine.process("passwordResetEmail", context);

		MimeMessage message = javaMailSender.createMimeMessage();

		MimeMessageHelper helper = new MimeMessageHelper(
				message,
				MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
				StandardCharsets.UTF_8.name());

		helper.setFrom(from);
		helper.setTo(to);
		helper.setSubject("SnapVocab - Reset your password");
		helper.setText(html, true);

		javaMailSender.send(message);
	}
}

