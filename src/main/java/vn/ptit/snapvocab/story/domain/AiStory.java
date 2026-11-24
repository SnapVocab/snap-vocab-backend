package vn.ptit.snapvocab.story.domain;

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
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;
import vn.ptit.snapvocab.story.domain.enumeration.StoryGenre;
import vn.ptit.snapvocab.story.domain.enumeration.StoryLevel;
import vn.ptit.snapvocab.story.domain.enumeration.StoryMode;
import vn.ptit.snapvocab.user.domain.User;

@Entity
@Table(name = "ai_stories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiStory extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "topic_id")
	private Long topicId;

	@Column(nullable = false, length = 180)
	private String title;

	@Enumerated(EnumType.STRING)
	@Builder.Default
	@Column(name = "story_mode", nullable = false, length = 20)
	private StoryMode storyMode = StoryMode.BLENDED;

	@Enumerated(EnumType.STRING)
	@Builder.Default
	@Column(nullable = false, length = 10)
	private StoryLevel level = StoryLevel.A2;

	@Enumerated(EnumType.STRING)
	@Builder.Default
	@Column(nullable = false, length = 30)
	private StoryGenre genre = StoryGenre.DAILY;

	@Column(name = "provider_name", length = 120)
	private String providerName;

	@Column(name = "model_name", length = 150)
	private String modelName;

	@Column(name = "target_words_json", nullable = false, columnDefinition = "LONGTEXT")
	private String targetWordsJson;

	@Column(name = "sentences_json", nullable = false, columnDefinition = "LONGTEXT")
	private String sentencesJson;

	@Builder.Default
	@Column(name = "total_blanks", nullable = false)
	private Integer totalBlanks = 0;

	@Column(name = "best_score")
	private Integer bestScore;

	@Builder.Default
	@Column(name = "xp_awarded", nullable = false)
	private Integer xpAwarded = 0;

	@Column(name = "completed_at")
	private Instant completedAt;
}

