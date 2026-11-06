-- Part 4 of the dictionary repair: after parts 1-3, delete English words that still have no
-- definition. On dev (2026-11-03) these were 76 entries no dictionary source covers: Latin given-name
-- abbreviations from old dictionaries (alex., benj., eliz., ...), misspellings, and two non-English
-- rows mislabelled lang_code 'en'. Run parts 1-3 first, and back up these rows before running this.

START TRANSACTION;

CREATE TEMPORARY TABLE unsourced_words AS
SELECT w.id
FROM words w
WHERE w.lang_code = 'en'
  AND NOT EXISTS (SELECT 1 FROM word_definitions wd WHERE wd.word_id = w.id);

-- Inspect before deleting.
SELECT w.id, w.word FROM words w JOIN unsourced_words u ON u.id = w.id ORDER BY w.word;

DELETE r FROM word_relations r JOIN unsourced_words u ON u.id = r.word_id;
DELETE t FROM translations t JOIN unsourced_words u ON u.id = t.word_id;
DELETE p FROM pronunciations p JOIN unsourced_words u ON u.id = p.word_id;
DELETE w FROM words w JOIN unsourced_words u ON u.id = w.id;

COMMIT;
