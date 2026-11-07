package vn.ptit.snapvocab.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import vn.ptit.snapvocab.study.repository.*;
import vn.ptit.snapvocab.user.domain.User;
import vn.ptit.snapvocab.vocab.domain.Collection;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.vocab.domain.TopicItem;
import vn.ptit.snapvocab.vocab.domain.dto.topic.TopicItemData;
import vn.ptit.snapvocab.vocab.repository.*;
import vn.ptit.snapvocab.vocab.service.TopicService;

@ExtendWith(MockitoExtension.class)
class CollectionStudyQueueTest {
    @Mock StudyQueueRepository queue;
    @Mock StudyDailyCardRepository dailyCards;
    @Mock CollectionStudyPlanner planner;
    @Mock CollectionStudySettingsRepository settings;
    @Mock CollectionRepository collections;
    @Mock TopicRepository topics;
    @Mock TopicService topicService;
    @InjectMocks StudyService service;

    @Test
    void homeAddsCollectionBudgetsOnceAndKeepsDailyCountsSeparateFromSessionSize() {
        when(collections.findByOwnerId(7L)).thenReturn(List.of(
                Collection.builder().id(3L).build(), Collection.builder().id(4L).build()));
        when(planner.snapshot(eq(7L), eq(List.of(3L, 4L)), any())).thenReturn(
                new CollectionStudyPlanner.Snapshot(Map.of(
                        3L, new CollectionStudyPlanner.Budget(20, 200, 5, 198),
                        4L, new CollectionStudyPlanner.Budget(20, 0, 0, 0)),
                        Map.of(3L, new CollectionStudyPlanner.Totals(50, 100, 3, 150, 5),
                                4L, new CollectionStudyPlanner.Totals(50, 100, 0, 150, 0)), Map.of()));
        var today = service.getToday(7L);
        assertThat(today.newCount()).isEqualTo(35);
        assertThat(today.dueCount()).isEqualTo(5);
        assertThat(today.sessionCount()).isEqualTo(20);
        assertThat(today.totalWords()).isEqualTo(300);
        assertThat(today.backlog()).isFalse();
    }

    @Test
    void enteringChildTopicUsesTheRemainingCollectionBudgetAndCannotStartAnotherSessionPastIt() {
        var user = new User();
        user.setId(7L);
        var collection = Collection.builder().id(3L).owner(user).name("Collection").build();
        var child = Topic.builder().id(12L).collection(collection).build();
        var item = TopicItem.builder().id(21L).topic(child).build();
        var data = new TopicItemData();
        data.setTopicItemId(21L);
        when(topics.findById(12L)).thenReturn(Optional.of(child));
        when(topics.findDescendantIdsIncludingSelf(12L)).thenReturn(List.of(12L));
        when(planner.snapshot(eq(7L), eq(List.of(3L)), any())).thenReturn(
                new CollectionStudyPlanner.Snapshot(Map.of(3L,
                        new CollectionStudyPlanner.Budget(20, 0, 19, 0)), Map.of(), Map.of()));
        when(queue.findNewItems(eq(7L), eq(List.of(12L)), any())).thenReturn(List.of(item));
        when(topicService.getTopicItemsByIds(List.of(21L))).thenReturn(List.of(data));
        assertThat(service.getQueue(7L, 12L).cards()).hasSize(1);
        verify(queue).findNewItems(eq(7L), eq(List.of(12L)), argThat((Pageable p) -> p.getPageSize() == 1));
        verify(queue, never()).findQuotaReviews(any(), any(), any(), any(), any(), any(), any());
        when(planner.snapshot(eq(7L), eq(List.of(3L)), any())).thenReturn(
                new CollectionStudyPlanner.Snapshot(Map.of(3L,
                        new CollectionStudyPlanner.Budget(20, 0, 20, 0)), Map.of(), Map.of()));
        assertThat(service.getQueue(7L, 12L).cards()).isEmpty();
    }
}
