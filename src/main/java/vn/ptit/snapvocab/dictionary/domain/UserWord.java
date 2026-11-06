package vn.ptit.snapvocab.dictionary.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.snapvocab.common.domain.BaseCreatedAtEntity;
import vn.ptit.snapvocab.user.domain.User;

/** A dictionary word explicitly saved to a user's personal vocabulary. */
@Entity
@Table(name = "user_words", uniqueConstraints =
        @UniqueConstraint(name = "uk_user_words_user_word", columnNames = {"user_id", "word_id"}))
@Getter
@Setter
@NoArgsConstructor
public class UserWord extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;
}
