package vn.ptit.snapvocab.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.ptit.snapvocab.study.domain.CollectionStudySettings;
import vn.ptit.snapvocab.study.repository.*;
import vn.ptit.snapvocab.vocab.domain.Collection;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.vocab.repository.*;

@ExtendWith(MockitoExtension.class)
class CollectionStudyPlannerTest {
    @Mock StudyQueueRepository queue;
    @Mock StudyDailyCardRepository cards;
    @Mock CollectionStudySettingsRepository settings;
    @Mock CollectionStudySettingsService access;
    @Mock CollectionRepository collections;
    @Mock TopicRepository topics;
    @InjectMocks CollectionStudyPlanner planner;
    private static final Instant NOW = Instant.parse("2026-11-07T18:00:00Z");

    @Test
    void defaultsAndQuotasAreIndependentForEachCollectionAndResetOnVietnamDayBoundary() {
        var rows = List.of(stats(11, 3, 80, 400, 0, 5), stats(22, 4, 80, 400, 0, 1));
        when(queue.topicStudyStats(eq(7L), anyList(), any(), any(), any(), any(), anyList(), anyList()))
                .thenReturn(rows);
        var used = mock(StudyDailyCardRepository.ReviewUsage.class);
        when(used.getCollectionId()).thenReturn(3L);
        when(used.getReviewCount()).thenReturn(198L);
        when(cards.reviewUsage(eq(7L), anyList(), any(), any())).thenReturn(List.of(used));
        var snapshot = planner.snapshot(7L, List.of(3L, 4L), NOW);
        assertThat(snapshot.budgets().get(3L).newRemaining()).isEqualTo(15);
        assertThat(snapshot.budgets().get(3L).reviewRemaining()).isEqualTo(2);
        assertThat(snapshot.budgets().get(4L).newRemaining()).isEqualTo(19);
        assertThat(snapshot.budgets().get(4L).reviewRemaining()).isEqualTo(200);
        verify(cards).reviewUsage(7L, List.of(3L, 4L), LocalDate.of(2026, 11, 8),
                Instant.parse("2026-11-07T17:00:00Z"));
    }

    @Test
    void reviewLimitDoesNotBlockNewCardsOrIntradayLearning() {
        var preference = new CollectionStudySettings();
        preference.setCollection(Collection.builder().id(3L).build());
        preference.setNewCardsPerDay(2);
        preference.setReviewCardsPerDay(0);
        when(settings.findByUserIdAndCollectionIdIn(7L, List.of(3L))).thenReturn(List.of(preference));
        var rows = List.of(stats(11, 3, 50, 50, 3, 0));
        when(queue.topicStudyStats(eq(7L), anyList(), any(), any(), any(), any(), anyList(), anyList()))
                .thenReturn(rows);
        var snapshot = planner.snapshot(7L, List.of(3L), NOW);
        var counts = snapshot.budgets().get(3L).counts(3L, null, snapshot.collections().get(3L));
        assertThat(counts.newCount()).isEqualTo(2);
        assertThat(counts.reviewCount()).isZero();
        assertThat(counts.learningCount()).isEqualTo(3);
        assertThat(counts.getDueCount()).isEqualTo(3);
    }

    @Test
    void topicSubtreesShareOneCollectionBudgetInsteadOfMultiplyingIt() {
        var root = Topic.builder().id(10L).build();
        var a = Topic.builder().id(11L).parent(root).build();
        var b = Topic.builder().id(12L).parent(root).build();
        var grandchild = Topic.builder().id(13L).parent(a).build();
        when(topics.findByCollectionId(3L)).thenReturn(List.of(root, a, b, grandchild));
        var rows = List.of(stats(11, 3, 2, 0, 0, 18), stats(12, 3, 5, 0, 0, 0), stats(13, 3, 5, 0, 0, 0));
        when(queue.topicStudyStats(eq(7L), anyList(), any(), any(), any(), any(), anyList(), anyList()))
                .thenReturn(rows);
        var overview = planner.overview(7L, 3L);
        assertThat(overview.collection().newCount()).isEqualTo(2);
        assertThat(overview.topics()).hasSize(4).allSatisfy(count -> assertThat(count.newCount()).isEqualTo(2));
        verify(access).accessibleCollection(7L, 3L);
    }

    private static StudyQueueRepository.TopicStudyStats stats(long topic, long collection, long fresh,
            long due, long learning, long introduced) {
        var row = mock(StudyQueueRepository.TopicStudyStats.class);
        when(row.getTopicId()).thenReturn(topic);
        when(row.getCollectionId()).thenReturn(collection);
        when(row.getNewCount()).thenReturn(fresh);
        when(row.getDueCount()).thenReturn(due);
        when(row.getLearningCount()).thenReturn(learning);
        when(row.getNewStarted()).thenReturn(introduced);
        when(row.getWordCount()).thenReturn(fresh + due);
        return row;
    }
}
