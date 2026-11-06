-- Part 3 of restoring dictionary entries: senses that needed translating.
-- Vietnamese translated from English Wiktionary glosses (CC BY-SA 4.0), reviewed in
-- dictionary_part3_review.md and applied to dev on 2026-11-03. Idempotent per sense, so it also adds to words that
-- already received relation senses in part 2.
START TRANSACTION;
-- ’em
SET @wid := (SELECT id FROM words WHERE word = '’em' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Họ, chúng (dạng rút gọn của them, chỉ dùng ở vị trí không nhấn).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Họ, chúng (dạng rút gọn của them, chỉ dùng ở vị trí không nhấn).', 'P', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- ’ll
SET @wid := (SELECT id FROM words WHERE word = '’ll' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Sẽ (dạng rút gọn của will, shall; chỉ thì tương lai hoặc ý tình thái).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Sẽ (dạng rút gọn của will, shall; chỉ thì tương lai hoặc ý tình thái).', 'V', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- ’ve
SET @wid := (SELECT id FROM words WHERE word = '’ve' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Đã (dạng rút gọn của trợ động từ have, chỉ thì hoàn thành).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Đã (dạng rút gọn của trợ động từ have, chỉ thì hoàn thành).', 'V', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Có (dạng rút gọn của have trong các nghĩa khác).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Có (dạng rút gọn của have trong các nghĩa khác).', 'V', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- aids
SET @wid := (SELECT id FROM words WHERE word = 'aids' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Ngôi thứ ba số ít thì hiện tại đơn của aid.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Ngôi thứ ba số ít thì hiện tại đơn của aid.', 'V', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Cách viết khác của AIDS (hội chứng suy giảm miễn dịch mắc phải).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Cách viết khác của AIDS (hội chứng suy giảm miễn dịch mắc phải).', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- aka
SET @wid := (SELECT id FROM words WHERE word = 'aka' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Loài dây leo Metrosideros fulgens ở New Zealand.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Loài dây leo Metrosideros fulgens ở New Zealand.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Cách viết khác của AKA (còn được gọi là).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Cách viết khác của AKA (còn được gọi là).', 'E', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Tên của hai hồ ở Alaska.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Tên của hai hồ ở Alaska.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Người Aka, thành viên của một dân tộc du mục Mbenga.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Người Aka, thành viên của một dân tộc du mục Mbenga.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Nói cách khác, tức là (dùng để giải thích hoặc làm rõ).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Nói cách khác, tức là (dùng để giải thích hoặc làm rõ).', 'D', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- altaic
SET @wid := (SELECT id FROM words WHERE word = 'altaic' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = '(thuộc) nhóm ngôn ngữ Altai (gồm các ngôn ngữ Turk, Mông Cổ và Tungus).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT '(thuộc) nhóm ngôn ngữ Altai (gồm các ngôn ngữ Turk, Mông Cổ và Tungus).', 'A', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- asap
SET @wid := (SELECT id FROM words WHERE word = 'asap' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Cách viết khác của ASAP (càng sớm càng tốt).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Cách viết khác của ASAP (càng sớm càng tốt).', 'D', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- battambang
SET @wid := (SELECT id FROM words WHERE word = 'battambang' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Thành phố Battambang ở Campuchia.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Thành phố Battambang ở Campuchia.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Tỉnh Battambang của Campuchia, nơi có thành phố cùng tên.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Tỉnh Battambang của Campuchia, nơi có thành phố cùng tên.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- bhavana
SET @wid := (SELECT id FROM words WHERE word = 'bhavana' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Tên riêng nữ gốc tiếng Phạn, dùng ở Ấn Độ.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Tên riêng nữ gốc tiếng Phạn, dùng ở Ấn Độ.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- chan
SET @wid := (SELECT id FROM words WHERE word = 'chan' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Kênh trò chuyện trên IRC.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Kênh trò chuyện trên IRC.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Diễn đàn đăng hình ảnh (imageboard).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Diễn đàn đăng hình ảnh (imageboard).', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Họ người Hoa (gốc Quảng Đông).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Họ người Hoa (gốc Quảng Đông).', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Họ người Hoa (gốc Phúc Kiến).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Họ người Hoa (gốc Phúc Kiến).', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Thiền tông, tông phái lớn của Phật giáo Đại thừa ở Trung Hoa, chủ trương đốn ngộ, phát triển ở Nhật Bản thành Zen.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Thiền tông, tông phái lớn của Phật giáo Đại thừa ở Trung Hoa, chủ trương đốn ngộ, phát triển ở Nhật Bản thành Zen.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Họ người Khmer.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Họ người Khmer.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- digital camera
SET @wid := (SELECT id FROM words WHERE word = 'digital camera' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Máy ảnh kỹ thuật số.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Máy ảnh kỹ thuật số.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- gps
SET @wid := (SELECT id FROM words WHERE word = 'gps' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Thiết bị định vị vệ tinh (nói chung).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Thiết bị định vị vệ tinh (nói chung).', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Định vị (vật gì) bằng hệ thống GPS.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Định vị (vật gì) bằng hệ thống GPS.', 'V', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- hot-pluggable
SET @wid := (SELECT id FROM words WHERE word = 'hot-pluggable' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Có thể cắm nóng (cắm vào an toàn khi hệ thống đang bật và hoạt động).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Có thể cắm nóng (cắm vào an toàn khi hệ thống đang bật và hoạt động).', 'A', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- lan
SET @wid := (SELECT id FROM words WHERE word = 'lan' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Họ người Hoa.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Họ người Hoa.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Huyện Lam (Lan), thuộc thành phố Lữ Lương, tỉnh Sơn Tây, Trung Quốc.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Huyện Lam (Lan), thuộc thành phố Lữ Lương, tỉnh Sơn Tây, Trung Quốc.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Giải đấu tổ chức trực tiếp, dùng mạng LAN.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Giải đấu tổ chức trực tiếp, dùng mạng LAN.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- maitri
SET @wid := (SELECT id FROM words WHERE word = 'maitri' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Lòng từ, lòng nhân ái (đặc biệt khi được vun bồi qua thiền định hoặc chánh niệm).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Lòng từ, lòng nhân ái (đặc biệt khi được vun bồi qua thiền định hoặc chánh niệm).', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- marchese
SET @wid := (SELECT id FROM words WHERE word = 'marchese' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Hầu tước (ở Ý).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Hầu tước (ở Ý).', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- mashup
SET @wid := (SELECT id FROM words WHERE word = 'mashup' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Tác phẩm nghệ thuật chủ yếu ghép từ các phần vay mượn của tác phẩm khác, hoặc pha trộn nhiều yếu tố khác nhau.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Tác phẩm nghệ thuật chủ yếu ghép từ các phần vay mượn của tác phẩm khác, hoặc pha trộn nhiều yếu tố khác nhau.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Bản phối ghép hai hay nhiều bài hát của các nghệ sĩ khác nhau thành một bản nhạc.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Bản phối ghép hai hay nhiều bài hát của các nghệ sĩ khác nhau thành một bản nhạc.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Sản phẩm phái sinh ghép từ hai hay nhiều nội dung (thường là kỹ thuật số).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Sản phẩm phái sinh ghép từ hai hay nhiều nội dung (thường là kỹ thuật số).', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Ứng dụng web kết hợp dữ liệu hoặc chức năng từ nhiều nguồn.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Ứng dụng web kết hợp dữ liệu hoặc chức năng từ nhiều nguồn.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- metacognition
SET @wid := (SELECT id FROM words WHERE word = 'metacognition' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Siêu nhận thức (việc suy nghĩ về chính quá trình suy nghĩ của mình).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Siêu nhận thức (việc suy nghĩ về chính quá trình suy nghĩ của mình).', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- omg
SET @wid := (SELECT id FROM words WHERE word = 'omg' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Cách viết khác của OMG (Oh my God: ôi Chúa ơi, ôi trời ơi).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Cách viết khác của OMG (Oh my God: ôi Chúa ơi, ôi trời ơi).', 'X', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Ôi trời ơi! (thán từ biểu lộ sự phấn khích, ngạc nhiên, sửng sốt).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Ôi trời ơi! (thán từ biểu lộ sự phấn khích, ngạc nhiên, sửng sốt).', 'O', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- plug and play
SET @wid := (SELECT id FROM words WHERE word = 'plug and play' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Cắm là chạy: tính năng cho phép phần cứng mới gắn vào máy tính được tự động nhận diện và cấu hình.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Cắm là chạy: tính năng cho phép phần cứng mới gắn vào máy tính được tự động nhận diện và cấu hình.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = '(y học) Nền tảng vắc-xin có thể thay đổi bằng DNA hoặc RNA của các mầm bệnh khác nhau để tạo ra các vắc-xin khác nhau.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT '(y học) Nền tảng vắc-xin có thể thay đổi bằng DNA hoặc RNA của các mầm bệnh khác nhau để tạo ra các vắc-xin khác nhau.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Việc dùng nguyên các tệp dự án đã được tinh chỉnh sẵn.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Việc dùng nguyên các tệp dự án đã được tinh chỉnh sẵn.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Kết nối thiết bị vào hệ thống để dùng được ngay, không cần cài đặt hay cấu hình thêm.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Kết nối thiết bị vào hệ thống để dùng được ngay, không cần cài đặt hay cấu hình thêm.', 'V', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- pluggable
SET @wid := (SELECT id FROM words WHERE word = 'pluggable' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Có thể cắm vào được (thường nói về các thành phần tùy chọn của phần mềm).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Có thể cắm vào được (thường nói về các thành phần tùy chọn của phần mềm).', 'A', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- prajna
SET @wid := (SELECT id FROM words WHERE word = 'prajna' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Bát nhã; trí tuệ, sự thấu hiểu.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Bát nhã; trí tuệ, sự thấu hiểu.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- ratanakiri
SET @wid := (SELECT id FROM words WHERE word = 'ratanakiri' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Tỉnh Ratanakiri của Campuchia, tỉnh lỵ là Banlung.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Tỉnh Ratanakiri của Campuchia, tỉnh lỵ là Banlung.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- ri
SET @wid := (SELECT id FROM words WHERE word = 'ri' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Lý, đơn vị đo khoảng cách của Triều Tiên, khoảng 393 m.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Lý, đơn vị đo khoảng cách của Triều Tiên, khoảng 393 m.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = '(âm nhạc) Âm tiết solfeggio chỉ nốt thăng của bậc hai trong gam trưởng.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT '(âm nhạc) Âm tiết solfeggio chỉ nốt thăng của bậc hai trong gam trưởng.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Viết tắt của rex imperator (vua kiêm hoàng đế) hoặc regina imperatrix (nữ vương kiêm nữ hoàng), chữ ký của quân chủ Anh.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Viết tắt của rex imperator (vua kiêm hoàng đế) hoặc regina imperatrix (nữ vương kiêm nữ hoàng), chữ ký của quân chủ Anh.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- saturated fat
SET @wid := (SELECT id FROM words WHERE word = 'saturated fat' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Chất béo bão hòa.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Chất béo bão hòa.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- thromboses
SET @wid := (SELECT id FROM words WHERE word = 'thromboses' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Ngôi thứ ba số ít thì hiện tại đơn của thrombose.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Ngôi thứ ba số ít thì hiện tại đơn của thrombose.', 'V', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- video camera
SET @wid := (SELECT id FROM words WHERE word = 'video camera' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Máy quay video.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Máy quay video.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- video conference
SET @wid := (SELECT id FROM words WHERE word = 'video conference' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Hội nghị truyền hình.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Hội nghị truyền hình.', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Tổ chức hoặc tham gia hội nghị truyền hình.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Tổ chức hoặc tham gia hội nghị truyền hình.', 'V', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Phát qua hội nghị truyền hình.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Phát qua hội nghị truyền hình.', 'V', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- virtuose
SET @wid := (SELECT id FROM words WHERE word = 'virtuose' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Điêu luyện, có tài nghệ bậc thầy.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Điêu luyện, có tài nghệ bậc thầy.', 'A', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
-- whitelist
SET @wid := (SELECT id FROM words WHERE word = 'whitelist' AND lang_code = 'en' ORDER BY id LIMIT 1);
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Danh sách trắng (danh sách người hoặc đối tượng được tin cậy hoặc được cho phép).');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Danh sách trắng (danh sách người hoặc đối tượng được tin cậy hoặc được cho phép).', 'N', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
SET @add := @wid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM word_definitions wd JOIN definitions d ON d.id = wd.definition_id WHERE wd.word_id = @wid AND d.definition = 'Đưa vào danh sách trắng.');
INSERT INTO definitions (definition, pos, sub_pos, definition_lang, links) SELECT 'Đưa vào danh sách trắng.', 'V', NULL, 'vi', NULL FROM DUAL WHERE @add;
INSERT INTO word_definitions (definition_id, word_id, example) SELECT LAST_INSERT_ID(), @wid, NULL FROM DUAL WHERE @add;
COMMIT;
