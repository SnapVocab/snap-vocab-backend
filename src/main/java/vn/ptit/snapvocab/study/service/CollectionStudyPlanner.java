package vn.ptit.snapvocab.study.service;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.snapvocab.study.domain.dto.CollectionStudyOverviewDTO;
import vn.ptit.snapvocab.study.domain.dto.StudyCountsDTO;
import vn.ptit.snapvocab.study.repository.CollectionStudySettingsRepository;
import vn.ptit.snapvocab.study.repository.StudyDailyCardRepository;
import vn.ptit.snapvocab.study.repository.StudyQueueRepository;
import vn.ptit.snapvocab.vocab.domain.Topic;
import vn.ptit.snapvocab.vocab.domain.enumeration.CardState;
import vn.ptit.snapvocab.vocab.domain.enumeration.CollectionType;
import vn.ptit.snapvocab.vocab.repository.CollectionRepository;
import vn.ptit.snapvocab.vocab.repository.TopicRepository;

/** Shared calculation for list counts, Home, queues and the transactional rating guard. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionStudyPlanner {
    public static final ZoneId STUDY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final List<CardState> LEARNING_STATES = List.of(CardState.LEARNING, CardState.RELEARNING);
    public static final List<CardState> DUE_STATES = List.of(CardState.NEW, CardState.LEARNING, CardState.REVIEW, CardState.RELEARNING);
    private final StudyQueueRepository queueRepository;
    private final StudyDailyCardRepository dailyCardRepository;
    private final CollectionStudySettingsRepository settingsRepository;
    private final CollectionStudySettingsService settingsService;
    private final CollectionRepository collectionRepository;
    private final TopicRepository topicRepository;

    public record Totals(long fresh, long reviews, long learning, long words, long introduced) {
        static final Totals EMPTY = new Totals(0, 0, 0, 0, 0);
        Totals plus(Totals other) {
            return new Totals(fresh + other.fresh, reviews + other.reviews, learning + other.learning,
                    words + other.words, introduced + other.introduced);
        }
    }

    public record Budget(int newLimit, int reviewLimit, long newUsed, long reviewUsed) {
        public int newRemaining() { return (int) Math.max(0, newLimit - newUsed); }
        public int reviewRemaining() { return (int) Math.max(0, reviewLimit - reviewUsed); }
        public StudyCountsDTO counts(Long collectionId, Long topicId, Totals totals) {
            return new StudyCountsDTO(collectionId, topicId, Math.min(totals.fresh, newRemaining()),
                    Math.min(totals.reviews, reviewRemaining()), totals.learning);
        }
    }

    public record Snapshot(Map<Long, Budget> budgets, Map<Long, Totals> collections, Map<Long, Totals> topics) {}

    public Snapshot snapshot(Long userId, List<Long> collectionIds, Instant now) {
        if (collectionIds.isEmpty()) return new Snapshot(Map.of(), Map.of(), Map.of());
        LocalDate today = LocalDate.ofInstant(now, STUDY_ZONE);
        Instant start = today.atStartOfDay(STUDY_ZONE).toInstant();
        Map<Long, Totals> collections = new HashMap<>();
        Map<Long, Totals> topics = new HashMap<>();
        for (var row : queueRepository.topicStudyStats(userId, collectionIds,
                LocalDateTime.ofInstant(now, ZoneOffset.UTC), LocalDateTime.ofInstant(start, ZoneOffset.UTC),
                start, today.plusDays(1).atStartOfDay(STUDY_ZONE).toInstant(), DUE_STATES, LEARNING_STATES)) {
            Totals totals = new Totals(n(row.getNewCount()), n(row.getDueCount()) - n(row.getLearningCount()),
                    n(row.getLearningCount()), n(row.getWordCount()), n(row.getNewStarted()));
            topics.put(row.getTopicId(), totals);
            collections.merge(row.getCollectionId(), totals, Totals::plus);
        }
        var settings = settingsRepository.findByUserIdAndCollectionIdIn(userId, collectionIds).stream()
                .collect(Collectors.toMap(s -> s.getCollection().getId(), Function.identity()));
        var usage = dailyCardRepository.reviewUsage(userId, collectionIds, today, start).stream()
                .collect(Collectors.toMap(StudyDailyCardRepository.ReviewUsage::getCollectionId,
                        r -> n(r.getReviewCount())));
        Map<Long, Budget> budgets = new HashMap<>();
        for (Long id : collectionIds) {
            var setting = settings.get(id);
            budgets.put(id, new Budget(setting == null ? CollectionStudySettingsService.DEFAULT_NEW_PER_DAY : setting.getNewCardsPerDay(),
                    setting == null ? CollectionStudySettingsService.DEFAULT_REVIEW_PER_DAY : setting.getReviewCardsPerDay(),
                    collections.getOrDefault(id, Totals.EMPTY).introduced, usage.getOrDefault(id, 0L)));
        }
        return new Snapshot(budgets, collections, topics);
    }

    public List<StudyCountsDTO> allCounts(Long userId, CollectionType type) {
        var collections = collectionRepository.findByTypeOrOwnerId(CollectionType.SYSTEM, userId).stream()
                .filter(c -> type == null || c.getType() == type).toList();
        List<Long> ids = collections.stream().map(c -> c.getId()).toList();
        Snapshot snapshot = snapshot(userId, ids, Instant.now());
        return ids.stream().map(id -> snapshot.budgets.get(id).counts(id, null,
                snapshot.collections.getOrDefault(id, Totals.EMPTY))).toList();
    }

    public CollectionStudyOverviewDTO overview(Long userId, Long collectionId) {
        settingsService.accessibleCollection(userId, collectionId);
        Snapshot snapshot = snapshot(userId, List.of(collectionId), Instant.now());
        Budget budget = snapshot.budgets.get(collectionId);
        // Hidden topics (OQ-CNT-01) leave the per-topic list; their due reviews still count in the collection total.
        List<Topic> topics = topicRepository.findByCollectionId(collectionId).stream()
                .filter(topic -> topic.getHiddenAt() == null).toList();
        Map<Long, List<Long>> children = new HashMap<>();
        for (Topic topic : topics) {
            if (topic.getParent() != null) {
                children.computeIfAbsent(topic.getParent().getId(), k -> new ArrayList<>()).add(topic.getId());
            }
        }
        Map<Long, Totals> subtrees = new HashMap<>();
        List<StudyCountsDTO> counts = topics.stream().map(topic -> budget.counts(collectionId, topic.getId(),
                subtree(topic.getId(), snapshot.topics, children, subtrees, new HashSet<>()))).toList();
        return new CollectionStudyOverviewDTO(budget.counts(collectionId, null,
                snapshot.collections.getOrDefault(collectionId, Totals.EMPTY)), counts);
    }

    private static Totals subtree(Long id, Map<Long, Totals> direct, Map<Long, List<Long>> children,
            Map<Long, Totals> memo, Set<Long> path) {
        if (memo.containsKey(id)) return memo.get(id);
        if (!path.add(id)) throw new IllegalStateException("Cycle in topic tree");
        Totals total = direct.getOrDefault(id, Totals.EMPTY);
        for (Long child : children.getOrDefault(id, List.of())) {
            total = total.plus(subtree(child, direct, children, memo, path));
        }
        path.remove(id);
        memo.put(id, total);
        return total;
    }

    private static long n(Long value) { return value == null ? 0L : value; }
}
