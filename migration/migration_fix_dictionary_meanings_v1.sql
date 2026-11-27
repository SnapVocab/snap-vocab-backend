-- Dictionary meaning clean-up (2026-11-26). Safe to re-run.
-- Backup taken before the first run: E:\snapvocab-backups\snapvocab-pre-meaning-fix-20261126.sql
-- (tables word_definitions + translations). Helper tables fixm_* are dropped at the end of each step.
--
-- Why: a card gets the FIRST Vietnamese sense of the word, so e.g. "railing" became "hiện tại phân từ của rail"
-- although the dictionary also has "Hàng rào chắn song; tay vịn thang gác, bao lơn". Senses are ordered by
-- word_definitions.id, so the grammatical "form of" notes are moved behind the real senses by giving them new ids
-- (nothing references word_definitions.id).

SET NAMES utf8mb4;

-- 1. "Form of" notes after the real senses -----------------------------------------------------------------------
DROP TABLE IF EXISTS fixm_sense;
CREATE TABLE fixm_sense AS
SELECT w.id AS word_id, wd.id AS wd_id,
       (LOWER(d.definition) REGEXP
         '^(dạng |số nhiều|hiện tại phân từ|phân từ|quá khứ|ngôi thứ|biến tố|so sánh|viết tắt|cách viết|giống (đực|cái)|thì |danh động từ|động từ chia)[^.;]{0,60} của [^ ]+[.]?$'
       ) AS form_of
FROM words w
JOIN word_definitions wd ON wd.word_id = w.id
JOIN definitions d ON d.id = wd.definition_id
WHERE w.lang_code = 'en' AND d.definition_lang = 'vi';
ALTER TABLE fixm_sense ADD INDEX idx_fixm_sense (word_id, wd_id);

-- Words whose first sense is a note while a real sense exists (one pass, no correlated subquery).
DROP TABLE IF EXISTS fixm_word;
CREATE TABLE fixm_word AS
SELECT word_id
FROM fixm_sense
GROUP BY word_id
HAVING SUBSTRING_INDEX(GROUP_CONCAT(form_of ORDER BY wd_id), ',', 1) = '1'
   AND SUM(form_of = 0) > 0;
ALTER TABLE fixm_word ADD PRIMARY KEY (word_id);

-- Every note of those words, in its current order, gets an id above the current maximum.
DROP TABLE IF EXISTS fixm_move;
CREATE TABLE fixm_move AS
SELECT s.wd_id, ROW_NUMBER() OVER (ORDER BY s.word_id, s.wd_id) AS rn
FROM fixm_sense s JOIN fixm_word w ON w.word_id = s.word_id
WHERE s.form_of = 1;

SET @base := (SELECT MAX(id) FROM word_definitions);
UPDATE word_definitions wd JOIN fixm_move m ON m.wd_id = wd.id SET wd.id = @base + m.rn;
SET @next := (SELECT MAX(id) + 1 FROM word_definitions);
SET @sql := CONCAT('ALTER TABLE word_definitions AUTO_INCREMENT = ', @next);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

DROP TABLE fixm_move;
DROP TABLE fixm_word;
DROP TABLE fixm_sense;

-- 2. Example sentences stored as senses -------------------------------------------------------------------------
-- e.g. clientele: "An Asian clientele." followed by its translation "Một nhóm khách hàng châu á." as two "senses".
-- The English sentence becomes the example of the sense before it; both rows are removed as senses.
DROP TABLE IF EXISTS fixm_rows;
CREATE TABLE fixm_rows AS
SELECT w.id AS word_id, w.word, wd.id AS wd_id, d.definition AS def,
       ROW_NUMBER() OVER (PARTITION BY w.id ORDER BY wd.id) AS rn
FROM words w
JOIN word_definitions wd ON wd.word_id = w.id
JOIN definitions d ON d.id = wd.definition_id
WHERE w.lang_code = 'en' AND d.definition_lang = 'vi'
  AND w.word IN ('auspices', 'barracks', 'clientele', 'cowboy', 'dabbling', 'demimonde', 'eyeful', 'gabfest',
                 'grump', 'habitus', 'half-day', 'hoise', 'occurrence', 'p.o.w', 'sagging', 'savoir-faire',
                 'stockbroking', 'touch-down', 'under-breath');

DROP TABLE IF EXISTS fixm_example;
CREATE TABLE fixm_example AS
SELECT e.word_id, e.wd_id AS example_id, n.wd_id AS translation_id, p.wd_id AS sense_id,
       TRIM(TRAILING ' &.' FROM TRIM(TRAILING '.' FROM e.def)) AS sentence
FROM fixm_rows e
JOIN fixm_rows p ON p.word_id = e.word_id AND p.rn = e.rn - 1
LEFT JOIN fixm_rows n ON n.word_id = e.word_id AND n.rn = e.rn + 1
WHERE e.def REGEXP BINARY '^[ -~]+$'
  AND LOWER(e.def) REGEXP '(^|[^a-z])(the|an|a|of|to|on|in|his)[^a-z]'
  AND p.def NOT REGEXP BINARY '^[ -~]+$';

UPDATE word_definitions wd JOIN fixm_example x ON x.sense_id = wd.id
SET wd.example = CONCAT(x.sentence, '.')
WHERE wd.example IS NULL OR wd.example = '';

DELETE wd FROM word_definitions wd JOIN fixm_example x ON x.example_id = wd.id;
DELETE wd FROM word_definitions wd JOIN fixm_example x ON x.translation_id = wd.id;
DROP TABLE fixm_example;
DROP TABLE fixm_rows;

-- "jar": two fragments of the idiom "on the jar / on a jar / on jar: hé mở (cửa)".
DELETE wd FROM word_definitions wd
JOIN words w ON w.id = wd.word_id AND w.lang_code = 'en' AND w.word = 'jar'
JOIN definitions d ON d.id = wd.definition_id
WHERE d.definition IN (') on the jar', 'on a jar');

-- 3. Translations split word by word or wrong --------------------------------------------------------------------
DROP TABLE IF EXISTS fixm_fix;
CREATE TABLE fixm_fix (word VARCHAR(191), translation VARCHAR(1024))
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_uca1400_ai_ci;
INSERT INTO fixm_fix (word, translation) VALUES
  ('lan', 'mạng cục bộ (LAN)'),
  ('wan', 'mạng diện rộng (WAN)'),
  ('wlan', 'mạng cục bộ không dây (WLAN)'),
  ('pda', 'máy tính cầm tay (PDA)'),
  ('video camera', 'máy quay video'),
  ('digital camera', 'máy ảnh kỹ thuật số'),
  ('video conference', 'hội nghị truyền hình'),
  ('video conference', 'hội thảo trực tuyến'),
  ('plug and play', 'cắm và chạy'),
  ('hot-pluggable', 'cắm nóng được'),
  ('permalink', 'liên kết thường trực'),
  ('permalink', 'liên kết tĩnh');

-- Words that are rewritten as a whole.
DELETE t FROM translations t
JOIN words w ON w.id = t.word_id AND w.lang_code = 'en'
WHERE t.lang_code = 'vi' AND w.word IN (SELECT DISTINCT word FROM fixm_fix)
  AND t.translation NOT IN (SELECT translation FROM fixm_fix f WHERE f.word = w.word);

INSERT INTO translations (lang_code, translation, word_id, created_at)
SELECT 'vi', f.translation, w.id, NOW(6)
FROM fixm_fix f JOIN words w ON w.word = f.word AND w.lang_code = 'en'
WHERE NOT EXISTS (SELECT 1 FROM translations t WHERE t.word_id = w.id AND t.lang_code = 'vi' AND t.translation = f.translation);
DROP TABLE fixm_fix;

-- Single wrong entries: English words or fragments of a longer phrase.
DELETE t FROM translations t
JOIN words w ON w.id = t.word_id AND w.lang_code = 'en'
WHERE t.lang_code = 'vi' AND (
     (w.word = 'widget' AND t.translation = 'button')
  OR (w.word = 'driver' AND t.translation IN ('device', 'thiết bị', 'printer', 'máy in'))
  OR (w.word = 'raster' AND t.translation IN ('raster image', 'raster graphics'))
  OR (w.word = 'theme' AND t.translation IN ('ngôn ngữ học', 'máy tính'))
  OR (w.word = 'setting' AND t.translation = 'máy tính'));

-- 4. System cards with a wrong Vietnamese translation ------------------------------------------------------------
UPDATE topic_item_attribute_values v
JOIN schema_attributes sa ON sa.id = v.schema_attribute_id AND sa.name = 'translation'
JOIN topic_item_attribute_groups g ON g.id = v.group_instance_id
JOIN topic_item_attribute_values wv ON wv.group_instance_id = g.id
JOIN schema_attributes ws ON ws.id = wv.schema_attribute_id AND ws.name = 'word'
SET v.value = CASE wv.value WHEN 'rape' THEN 'cưỡng hiếp' WHEN 'in' THEN 'khi (làm việc gì đó)' END,
    v.updated_at = NOW(6)
WHERE (wv.value = 'rape' AND v.value = 'nhanh') OR (wv.value = 'in' AND v.value = 'do');
