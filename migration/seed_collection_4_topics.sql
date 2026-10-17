START TRANSACTION;

SET @now = NOW(6);
SET @col_id = 4;

-- Root 1: Business & Office
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, NULL, 'Business & Office', 'Kinh doanh & Văn phòng', 'Core vocabulary for business environments, corporate management, and workplace operations.', 'Từ vựng cốt lõi cho môi trường kinh doanh, quản trị doanh nghiệp và vận hành công sở.', @now, @now);
SET @t_business = LAST_INSERT_ID();

-- 1.1 General Business
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, @t_business, 'General Business', 'Kinh doanh chung', 'Commercial agreements, marketing, and business planning.', 'Thỏa thuận thương mại, tiếp thị và lập kế hoạch kinh doanh.', @now, @now);
SET @t_gen_biz = LAST_INSERT_ID();

-- 1.1.1 Contracts & Agreements (empty schema for testing POST schema in Postman!)
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, @t_gen_biz, 'Contracts & Agreements', 'Hợp đồng & Thỏa thuận', 'Terms, clauses, negotiations, and legally binding agreements.', 'Điều khoản, đàm phán và thỏa thuận mang tính ràng buộc pháp lý.', @now, @now);
SET @t_contracts = LAST_INSERT_ID();

-- 1.1.2 Marketing & Advertising
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, @t_gen_biz, 'Marketing & Advertising', 'Tiếp thị & Quảng cáo', 'Market research, promotional campaigns, and brand awareness.', 'Nghiên cứu thị trường, chiến dịch quảng bá và nhận diện thương hiệu.', @now, @now);
SET @t_marketing = LAST_INSERT_ID();

-- 1.1.3 Warranties & Guarantees
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, @t_gen_biz, 'Warranties & Guarantees', 'Bảo hành & Cam kết', 'Product warranties, return policies, and service level agreements.', 'Bảo hành sản phẩm, chính sách đổi trả và cam kết chất lượng.', @now, @now);
SET @t_warranties = LAST_INSERT_ID();

-- 1.2 Office Operations
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, @t_business, 'Office Operations', 'Vận hành văn phòng', 'Daily office tasks, communications, and administrative procedures.', 'Công việc hằng ngày tại văn phòng, giao tiếp và quy trình hành chính.', @now, @now);
SET @t_office_ops = LAST_INSERT_ID();

-- 1.2.1 Office Technology & Equipment
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, @t_office_ops, 'Office Technology & Equipment', 'Công nghệ & Thiết bị văn phòng', 'Computers, printers, software systems, and office supplies.', 'Máy tính, máy in, hệ thống phần mềm và thiết bị văn phòng.', @now, @now);
SET @t_office_tech = LAST_INSERT_ID();

-- 1.2.2 Business Correspondence
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, @t_office_ops, 'Business Correspondence', 'Thư từ & Trao đổi công việc', 'Emails, formal memos, inquiry letters, and reports.', 'Email, biên bản thông báo, thư hỏi đáp và báo cáo công việc.', @now, @now);
SET @t_corresp = LAST_INSERT_ID();

-- 1.2.3 Meetings & Conferences
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, @t_office_ops, 'Meetings & Conferences', 'Hội họp & Hội nghị', 'Agendas, presentations, brainstorming sessions, and discussions.', 'Chương trình họp, thuyết trình, thảo luận và trao đổi ý kiến.', @now, @now);
SET @t_meetings = LAST_INSERT_ID();

-- Root 2: Finance & Banking
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, NULL, 'Finance & Banking', 'Tài chính & Ngân hàng', 'Financial transactions, banking services, and corporate accounting.', 'Giao dịch tài chính, dịch vụ ngân hàng và kế toán doanh nghiệp.', @now, @now);
SET @t_finance = LAST_INSERT_ID();

-- 2.1 Accounting & Bookkeeping
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, @t_finance, 'Accounting & Bookkeeping', 'Kế toán & Sổ sách', 'Balance sheets, income statements, invoices, and expense tracking.', 'Bảng cân đối kế toán, báo cáo thu nhập, hóa đơn và theo dõi chi phí.', @now, @now);
SET @t_accounting = LAST_INSERT_ID();

-- 2.2 Banking & Investments
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, @t_finance, 'Banking & Investments', 'Ngân hàng & Đầu tư', 'Loans, interest rates, portfolio investments, and asset management.', 'Khoản vay, lãi suất, danh mục đầu tư và quản trị tài sản.', @now, @now);
SET @t_banking = LAST_INSERT_ID();

-- 2.3 Taxes & Auditing
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, @t_finance, 'Taxes & Auditing', 'Thuế & Kiểm toán', 'Tax returns, compliance regulations, audits, and fiscal policy.', 'Kê khai thuế, tuân thủ quy định pháp luật và kiểm toán tài chính.', @now, @now);
SET @t_taxes = LAST_INSERT_ID();

-- Root 3: Personnel & Human Resources
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, NULL, 'Personnel & Human Resources', 'Nhân sự & Tuyển dụng', 'Staffing, employee benefits, performance reviews, and workplace culture.', 'Tuyển dụng nhân sự, chế độ phúc lợi, đánh giá và văn hóa doanh nghiệp.', @now, @now);
SET @t_hr = LAST_INSERT_ID();

-- 3.1 Recruitment & Hiring
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, @t_hr, 'Recruitment & Hiring', 'Tuyển dụng & Phỏng vấn', 'Job postings, candidate screening, interviews, and onboarding.', 'Đăng tuyển công việc, sàng lọc ứng viên, phỏng vấn và nhận việc.', @now, @now);
SET @t_recruitment = LAST_INSERT_ID();

-- 3.2 Salaries & Benefits
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, @t_hr, 'Salaries & Benefits', 'Lương bổng & Phúc lợi', 'Compensation, health insurance, pensions, and paid leave.', 'Chế độ đãi ngộ, bảo hiểm y tế, lương hưu và ngày nghỉ phép.', @now, @now);
SET @t_salaries = LAST_INSERT_ID();

-- 3.3 Training & Performance Evaluation
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, @t_hr, 'Training & Performance Evaluation', 'Đào tạo & Đánh giá năng lực', 'Skill workshops, appraisals, promotions, and professional development.', 'Khóa đào tạo kỹ năng, đánh giá hiệu suất, thăng chức và phát triển sự nghiệp.', @now, @now);
SET @t_training = LAST_INSERT_ID();

-- Root 4: Travel & Hospitality
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, NULL, 'Travel & Hospitality', 'Du lịch & Dịch vụ khách sạn', 'Business trips, flight bookings, accommodation, and event dining.', 'Chuyến công tác, đặt vé máy bay, nơi lưu trú và ẩm thực dịch vụ.', @now, @now);
SET @t_travel = LAST_INSERT_ID();

-- 4.1 Airlines & Transit
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, @t_travel, 'Airlines & Transit', 'Hàng không & Đi lại', 'Flight itineraries, boarding passes, baggage claim, and airport transit.', 'Lịch trình bay, thẻ lên máy bay, nhận hành lý và di chuyển tại sân bay.', @now, @now);
SET @t_airlines = LAST_INSERT_ID();

-- 4.2 Hotels & Reservations
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, @t_travel, 'Hotels & Reservations', 'Khách sạn & Đặt phòng', 'Room bookings, amenities, concierge services, and check-out.', 'Đặt phòng, tiện nghi phòng, dịch vụ hỗ trợ khách và trả phòng.', @now, @now);
SET @t_hotels = LAST_INSERT_ID();

-- 4.3 Restaurants & Dining
INSERT INTO topics (collection_id, parent_id, name, translation, description, description_translation, created_at, updated_at)
VALUES (@col_id, @t_travel, 'Restaurants & Dining', 'Nhà hàng & Ăn uống', 'Catering, table reservations, business dinners, and menu selections.', 'Tiệc phục vụ, đặt bàn, bữa tối tiếp khách và thực đơn món ăn.', @now, @now);
SET @t_dining = LAST_INSERT_ID();

-- Insert TopicSchemas for all newly created topics
INSERT INTO topic_schemas (topic_id, created_at, updated_at)
SELECT t.id, @now, @now
FROM topics t
LEFT JOIN topic_schemas ts ON ts.topic_id = t.id
WHERE t.collection_id = @col_id AND ts.id IS NULL;

-- Add standard Schema Attribute Groups and Schema Attributes to Marketing & Advertising, Accounting, Recruitment, Airlines
INSERT INTO schema_attribute_groups (schema_id, name, label, multiple, position, created_at, updated_at)
SELECT ts.id, 'main', 'Main', 0, 0, @now, @now
FROM topics t
JOIN topic_schemas ts ON ts.topic_id = t.id
WHERE t.id IN (@t_marketing, @t_accounting, @t_recruitment, @t_airlines);

INSERT INTO schema_attribute_groups (schema_id, name, label, multiple, position, created_at, updated_at)
SELECT ts.id, 'examples', 'Examples', 1, 1, @now, @now
FROM topics t
JOIN topic_schemas ts ON ts.topic_id = t.id
WHERE t.id IN (@t_marketing, @t_accounting, @t_recruitment, @t_airlines);

-- Attributes for main group
INSERT INTO schema_attributes (group_id, name, label, data_type, required, position, created_at, updated_at)
SELECT sag.id, 'base_word', 'Base Word', 'text', 1, 1, @now, @now
FROM schema_attribute_groups sag
JOIN topic_schemas ts ON ts.id = sag.schema_id
WHERE sag.name = 'main' AND ts.topic_id IN (@t_marketing, @t_accounting, @t_recruitment, @t_airlines);

INSERT INTO schema_attributes (group_id, name, label, data_type, required, position, created_at, updated_at)
SELECT sag.id, 'translation', 'Translation', 'text', 1, 2, @now, @now
FROM schema_attribute_groups sag
JOIN topic_schemas ts ON ts.id = sag.schema_id
WHERE sag.name = 'main' AND ts.topic_id IN (@t_marketing, @t_accounting, @t_recruitment, @t_airlines);

INSERT INTO schema_attributes (group_id, name, label, data_type, required, position, created_at, updated_at)
SELECT sag.id, 'head', 'Head', 'text', 1, 3, @now, @now
FROM schema_attribute_groups sag
JOIN topic_schemas ts ON ts.id = sag.schema_id
WHERE sag.name = 'main' AND ts.topic_id IN (@t_marketing, @t_accounting, @t_recruitment, @t_airlines);

INSERT INTO schema_attributes (group_id, name, label, data_type, required, position, created_at, updated_at)
SELECT sag.id, 'level', 'Level', 'text', 0, 4, @now, @now
FROM schema_attribute_groups sag
JOIN topic_schemas ts ON ts.id = sag.schema_id
WHERE sag.name = 'main' AND ts.topic_id IN (@t_marketing, @t_accounting, @t_recruitment, @t_airlines);

INSERT INTO schema_attributes (group_id, name, label, data_type, required, position, created_at, updated_at)
SELECT sag.id, 'definition', 'Definition', 'text', 0, 5, @now, @now
FROM schema_attribute_groups sag
JOIN topic_schemas ts ON ts.id = sag.schema_id
WHERE sag.name = 'main' AND ts.topic_id IN (@t_marketing, @t_accounting, @t_recruitment, @t_airlines);

INSERT INTO schema_attributes (group_id, name, label, data_type, required, position, created_at, updated_at)
SELECT sag.id, 'definition_translation', 'Definition Translation', 'text', 0, 6, @now, @now
FROM schema_attribute_groups sag
JOIN topic_schemas ts ON ts.id = sag.schema_id
WHERE sag.name = 'main' AND ts.topic_id IN (@t_marketing, @t_accounting, @t_recruitment, @t_airlines);

-- Attributes for examples group
INSERT INTO schema_attributes (group_id, name, label, data_type, required, position, created_at, updated_at)
SELECT sag.id, 'content', 'Content', 'text', 1, 1, @now, @now
FROM schema_attribute_groups sag
JOIN topic_schemas ts ON ts.id = sag.schema_id
WHERE sag.name = 'examples' AND ts.topic_id IN (@t_marketing, @t_accounting, @t_recruitment, @t_airlines);

INSERT INTO schema_attributes (group_id, name, label, data_type, required, position, created_at, updated_at)
SELECT sag.id, 'translation', 'Translation', 'text', 0, 2, @now, @now
FROM schema_attribute_groups sag
JOIN topic_schemas ts ON ts.id = sag.schema_id
WHERE sag.name = 'examples' AND ts.topic_id IN (@t_marketing, @t_accounting, @t_recruitment, @t_airlines);

COMMIT;
