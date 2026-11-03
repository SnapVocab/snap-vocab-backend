package vn.ptit.snapvocab.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** notification.md §2.2 and AC-01, AC-06. */
class SrsReminderTemplateTest {

	@Test
	void vietnameseWithAndWithoutName() {
		SrsReminderTemplate.Rendered r = SrsReminderTemplate.render("vi", " Lan ", 12);
		assertThat(r.title()).isEqualTo("Đến giờ ôn tập rồi!");
		assertThat(r.body()).isEqualTo("Lan ơi, bạn có 12 từ cần ôn. Ôn ngay để nhớ lâu hơn nhé!");
		assertThat(SrsReminderTemplate.render("vi", "", 150).body())
				.isEqualTo("Bạn có 99+ từ cần ôn. Ôn ngay để nhớ lâu hơn nhé!");
		assertThat(SrsReminderTemplate.render(null, null, 1).body()).startsWith("Bạn có 1 từ");
		// Longer than 20 characters: the nameless sentence, never a cut name.
		assertThat(SrsReminderTemplate.render("vi", "Nguyễn Thị Thanh Hương Lan", 3).body()).startsWith("Bạn có 3 từ");
		assertThat(SrsReminderTemplate.render("fr", "Lan", 3).title()).isEqualTo("Đến giờ ôn tập rồi!");
	}

	@Test
	void englishPluralizesIncludingTheCap() {
		assertThat(SrsReminderTemplate.render("en", "Lan", 1).body())
				.isEqualTo("Lan, you have 1 word to review. Review now to remember them longer!");
		assertThat(SrsReminderTemplate.render("en", null, 2).body())
				.isEqualTo("You have 2 words to review. Review now to remember them longer!");
		assertThat(SrsReminderTemplate.render("en", "Lan", 100).body()).contains("99+ words");
		assertThat(SrsReminderTemplate.render("en", "Lan", 5).title()).isEqualTo("Time to review!");
	}
}
