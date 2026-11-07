package vn.ptit.snapvocab.study.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import vn.ptit.snapvocab.common.domain.BaseTimeEntity;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.vocab.domain.Collection;

/** Personal limits, including for shared system collections. Absence means 20 new / 200 review. */
@Entity
@Table(name = "collection_study_settings", uniqueConstraints = @UniqueConstraint(
        name = "uq_collection_study_settings_user_collection", columnNames = {"user_id", "collection_id"}))
@Getter
@Setter
@NoArgsConstructor
public class CollectionStudySettings extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection collection;
    @Column(name = "new_cards_per_day", nullable = false)
    private int newCardsPerDay = 20;
    @Column(name = "review_cards_per_day", nullable = false)
    private int reviewCardsPerDay = 200;
}
