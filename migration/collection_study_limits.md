# Collection study limits

Each `(user_id, collection_id)` has independent daily new/review limits, including
system collections. No settings row means **20 new / 200 review**. Zero pauses that
category. Reset deletes only the current learner's row; collection deletion removes
all learners' settings and the collection's study records before deleting its cards.
Apply `migration_collection_study_settings.sql` before deploying to environments
that do not use Hibernate schema updates.

The collection is the quota scope for every topic/subtopic, even when entering a
child directly. A subtree displays `min(available in subtree, collection remaining)`.
Siblings compete for the same budget: their displayed counts must not be added to
produce their parent's count. Home sums collection counts once. Home includes owned
collections and system collections already studied or personally configured; the
system catalogue still shows counts for all available collections.

New introductions are counted using each learner/card FSRS record's creation time.
Review usage counts distinct existing cards studied that day, excluding today's new
introductions. Intraday learning/relearning repetitions do not consume another slot;
they appear in the displayed review count. Interday learning uses the review budget.
Study days reset at midnight in `Asia/Ho_Chi_Minh`; unused quota does not carry over.
Queues use an internal 20-card batch, not a user preference or daily cap. The legacy
user-wide `dailyGoal` and `studySessionCardLimit` fields have been removed. Apply
`migration_remove_user_study_limits.sql` with the backend stopped before starting
the updated version. Backlog/returning rules no longer limit new cards.

Counts, queues, and rating guards share `CollectionStudyPlanner`. Rating/settings
writes serialize on the user row. Rating transactions use READ_COMMITTED so quota
reads see the previous writer after waiting for that lock, rather than an earlier
MariaDB REPEATABLE_READ snapshot. Stale queues cannot bypass the rating guard.

Endpoints:

- `GET /api/study/counts?type=USER|SYSTEM`: batched collection counts.
- `GET /api/collections/{id}/study/today`: collection and subtree counts.
- `GET|PUT|DELETE /api/collections/{id}/study/settings`: own settings/defaults/reset.
- Existing collection, topic and library queue/rating endpoints enforce these limits.

Anki references:

- https://docs.ankiweb.net/deck-options.html#daily-limits
- https://docs.ankiweb.net/deck-options.html#limits-start-from-top
- https://docs.ankiweb.net/deck-options.html#new-cards-ignore-review-limit

This product deliberately uses separate new/review budgets as requested, equivalent
to always starting limits from the collection and allowing new cards independently
of the review limit.
