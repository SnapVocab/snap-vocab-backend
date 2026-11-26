package vn.ptit.snapvocab.mnemonic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.vocab.domain.TopicItem;

/**
 * A learner's own AI mnemonic for one card. Kept per user, not in the card's fields: system cards are shared, so a
 * mnemonic written into them would show up for everyone. Deleting the word, its topic or the user removes it.
 */
@Entity
@Table(name = "word_mnemonics", uniqueConstraints = @UniqueConstraint(
		name = "uq_word_mnemonics_user_item", columnNames = { "user_id", "topic_item_id" }))
@Getter
@Setter
@NoArgsConstructor
public class WordMnemonic extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "topic_item_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private TopicItem topicItem;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MnemonicType type;

	/** The Vietnamese hook the mnemonic hangs on, highlighted by the app; may be null. */
	@Column(length = 120)
	private String keyword;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(name = "provider_name", length = 100)
	private String providerName;

	@Column(name = "model_name", length = 150)
	private String modelName;
}
