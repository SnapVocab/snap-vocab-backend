-- Part 2 of restoring dictionary entries left empty by the original import.
-- Sources: English Wiktionary via kaikki.org (relation senses only, in the DB's existing
-- Vietnamese phrasing: "Số nhiều của X", "Viết tắt của X"), and Vietnamese Wiktionary verbatim.
-- Wiktionary content is CC BY-SA 4.0. Nothing here is a free translation.
-- Idempotent: a word that already has definitions is skipped, a pronunciation is added once.
START TRANSACTION;
-- edrus (vi.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'edrus' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Edwardus', 'X', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- gul. (vi.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'gul.' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Gulielmus', 'X', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- ioh. (vi.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'ioh.' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Iohannes', 'X', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- jabus (vi.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'jabus' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Iacobus', 'X', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- jac. (vi.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'jac.' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Iacobus', 'X', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- kampot (vi.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'kampot' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Tỉnh cực Nam Kâmpŭchéa.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- kratié (vi.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'kratié' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Tỉnh Đông Bắc Kâmpŭchéa.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- micls (vi.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'micls' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Michaelus', 'X', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- mondulkiri (vi.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'mondulkiri' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Tỉnh cực Đông Kâmpŭchéa.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- paiilin (vi.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'paiilin' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Tỉnh cực Tây Kâmpŭchéa.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- pursat (vi.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'pursat' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Tỉnh cực Tây Kâmpŭchéa.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- takoéo (vi.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'takoéo' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Tỉnh cực Nam Kâmpŭchéa.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- wipo (vi.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'wipo' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của World Intellectual Property Organization (Tổ chức Sở hữu Trí tuệ Thế giới)', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- 4 (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = '4' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của for', 'E', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- afaik (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'afaik' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Cách viết khác của AFAIK.', 'D', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của as far as I know', 'D', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- aids (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'aids' AND lang_code = 'en' ORDER BY id LIMIT 1);
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/eɪdz/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/eɪdz/');
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của aid', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của acquired immunodeficiency syndrome', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- aka (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'aka' AND lang_code = 'en' ORDER BY id LIMIT 1);
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/ˌeɪ.keɪˈeɪ/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/ˌeɪ.keɪˈeɪ/');
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/ˈækə/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/ˈækə/');
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của also known as', 'E', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- amphioxi (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'amphioxi' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của amphioxus', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- asap (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'asap' AND lang_code = 'en' ORDER BY id LIMIT 1);
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/ˈeɪ.sæp/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/ˈeɪ.sæp/');
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của as soon as possible', 'D', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- bdc (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'bdc' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của bullet drop compensation', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của bottom dead centre/bottom dead center', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của business development company', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của backing diagonally to the center', 'D', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- chan (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'chan' AND lang_code = 'en' ORDER BY id LIMIT 1);
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/t͡ʃæn/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/t͡ʃæn/');
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/tʃæn/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/tʃæn/');
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Cách viết khác của Zhan.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Cách viết khác của Chanambam surname of Meitei origin.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Cách viết khác của Chandam surname of Meitei origin.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- cnc (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'cnc' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của computer numerical control or computerized numerical control', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của cellulose nanocrystal', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Chinese National Currency', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- cso (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'cso' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của chief security officer', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của chief sustainability officer', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của civil society organisation', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của combined sewer overflow', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của community service officer', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của conditional sentencing order', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- cuv (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'cuv' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của compact sport utility vehicle', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của compact crossover sport utility vehicle or compact crossover utility vehicle', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Cách viết khác của XUV.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của clitourethrovaginal', 'A', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- dwt (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'dwt' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của deadweight ton', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Cách viết khác của dwt.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của discrete wavelet transform', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- fyi (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'fyi' AND lang_code = 'en' ORDER BY id LIMIT 1);
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/ˌɛfwaɪˈaɪ/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/ˌɛfwaɪˈaɪ/');
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của for your information', 'X', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- gps (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'gps' AND lang_code = 'en' ORDER BY id LIMIT 1);
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/d͡ʒiˈpi.es/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/d͡ʒiˈpi.es/');
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của gp', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Global Positioning System', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của GPS receiver', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Goodpasture syndrome', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của geometric product specifications', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- idk (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'idk' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của I don''t know', 'X', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- iff (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'iff' AND lang_code = 'en' ORDER BY id LIMIT 1);
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/ɪf ən(d)ˈəʊn.li ɪf/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/ɪf ən(d)ˈəʊn.li ɪf/');
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/ɪf ən(d)ˈoʊn.li ɪf/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/ɪf ən(d)ˈoʊn.li ɪf/');
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của if and only if', 'C', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Interchange File Format', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của International Flavors & Fragrances', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của identification friend or foe', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- imho (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'imho' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của in my humble opinion', 'X', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của in my honest opinion', 'X', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- lan (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'lan' AND lang_code = 'en' ORDER BY id LIMIT 1);
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/læn/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/læn/');
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/ˌɛl ˌeɪ ˈɛn/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/ˌɛl ˌeɪ ˈɛn/');
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của local area network', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- macrosporangia (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'macrosporangia' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của macrosporangium', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- manubria (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'manubria' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của manubrium', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- meatuses (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'meatuses' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của meatus', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- mediastina (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'mediastina' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của mediastinum', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- mediums (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'mediums' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của medium', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- menta (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'menta' AND lang_code = 'en' ORDER BY id LIMIT 1);
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/ˈmɛn.tə/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/ˈmɛn.tə/');
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của mentum', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- mesdames (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'mesdames' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của madam', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của madame', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- mesdemoiselles (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'mesdemoiselles' AND lang_code = 'en' ORDER BY id LIMIT 1);
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/ˌmeɪdəmwəˈzɛl(z)/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/ˌmeɪdəmwəˈzɛl(z)/');
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của mademoiselle', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- mesotheliomas (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'mesotheliomas' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của mesothelioma', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- mesotheliomata (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'mesotheliomata' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của mesothelioma', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- moduli (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'moduli' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của modulus', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- mp (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'mp' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của market price', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của masorah parva', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Member of Parliament', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của military police or military policeman', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của mana points or magic points', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của megapixel', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của machine pistol', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của morning prayer', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- narcoses (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'narcoses' AND lang_code = 'en' ORDER BY id LIMIT 1);
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/nɑɹˈkoʊ.siːz/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/nɑɹˈkoʊ.siːz/');
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/nɑːˈkəʊ.siːz/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/nɑːˈkəʊ.siːz/');
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của narcosis', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- nucleoli (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'nucleoli' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của nucleolus', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- omg (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'omg' AND lang_code = 'en' ORDER BY id LIMIT 1);
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/ˌəʊ.ɛmˈd͡ʒiː/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/ˌəʊ.ɛmˈd͡ʒiː/');
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/ˌoʊ.ɛmˈd͡ʒiː/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/ˌoʊ.ɛmˈd͡ʒiː/');
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Cách viết khác của OMG.', 'X', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Object Management Group', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Orkney Manifesto Group', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của outlaw motorcycle gang', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- pda (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'pda' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của personal digital assistant', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của public display of affection', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của pushdown automaton', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của property damage accident', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của pathological demand avoidance', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của patent ductus arteriosus', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của production data acquisition', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của peridural anesthesia', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- ri (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'ri' AND lang_code = 'en' ORDER BY id LIMIT 1);
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/ɹiː/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/ɹiː/');
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Cách viết khác của Yi.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Rhode Island', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Riau', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Raffles Institution', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của radioisotope', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của radio interference', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- sdk (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'sdk' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của software development kit', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của single digit kyu', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- selves (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'selves' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của self', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- stfu (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'stfu' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Southern Tenant Farmers Union', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- tabulae (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'tabulae' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của tabula', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- termites (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'termites' AND lang_code = 'en' ORDER BY id LIMIT 1);
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/ˈtɜːmaɪts/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/ˈtɜːmaɪts/');
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/ˈtɜːmɪtiːz/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/ˈtɜːmɪtiːz/');
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của termite', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của termes', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- testae (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'testae' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của testa', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- thrombi (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'thrombi' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của thrombus', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- thromboses (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'thromboses' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của thrombosis', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- tintinnabula (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'tintinnabula' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của tintinnabulum', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- tmeses (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'tmeses' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của tmesis', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- vagi (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'vagi' AND lang_code = 'en' ORDER BY id LIMIT 1);
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/ˈveɪˌɡaɪ/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/ˈveɪˌɡaɪ/');
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/ˈveɪˌd͡ʒaɪ/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/ˈveɪˌd͡ʒaɪ/');
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của vagus', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- valleculae (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'valleculae' AND lang_code = 'en' ORDER BY id LIMIT 1);
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/væˈlɛk.jəˌli/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/væˈlɛk.jəˌli/');
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/vəˈlɛk.jəˌli/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/vəˈlɛk.jəˌli/');
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của vallecula', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- virtuose (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'virtuose' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Số nhiều của virtuosa', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- wbs (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'wbs' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của work breakdown structure', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- wdm (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'wdm' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của Windows Driver Model', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của warm dark matter', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của wavelength-division multiplexing', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
-- wlan (en.wiktionary)
SET @wid := (SELECT id FROM words WHERE word = 'wlan' AND lang_code = 'en' ORDER BY id LIMIT 1);
INSERT INTO pronunciations (word_id, ipa, region) SELECT @wid, '/ˈdʌbəlju.læn/', NULL FROM DUAL WHERE @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pronunciations WHERE word_id = @wid AND ipa = '/ˈdʌbəlju.læn/');
SET @restore := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions WHERE word_id = @wid);
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của wireless local area network', 'N', NULL, 'vi', NULL FROM DUAL WHERE @restore;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @restore;
COMMIT;
