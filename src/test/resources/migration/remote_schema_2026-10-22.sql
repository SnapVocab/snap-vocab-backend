-- MySQL dump 10.13  Distrib 8.0.45, for Win64 (x86_64)
--
-- ------------------------------------------------------
-- Server version	5.5.5-10.11.14-MariaDB-0ubuntu0.24.04.1

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `authorities`
--

DROP TABLE IF EXISTS `authorities`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `authorities` (
  `name` varchar(50) NOT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `booster_activations`
--

DROP TABLE IF EXISTS `booster_activations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booster_activations` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `started_at` datetime(6) NOT NULL,
  `shop_item_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_booster_activation_key` (`user_id`,`idempotency_key`),
  KEY `idx_booster_activation_active` (`user_id`,`expires_at`),
  KEY `FKt5fiif0owbk2r17id7e6t49e1` (`shop_item_id`),
  CONSTRAINT `FK39yx2vppqqcdorb6260pbn524` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKt5fiif0owbk2r17id7e6t49e1` FOREIGN KEY (`shop_item_id`) REFERENCES `shop_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `coin_transactions`
--

DROP TABLE IF EXISTS `coin_transactions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `coin_transactions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `amount` bigint(20) NOT NULL,
  `balance_after` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `event_key` varchar(191) NOT NULL,
  `source_id` varchar(100) NOT NULL,
  `source_type` enum('ADMIN_ADJUSTMENT','DAILY_CHEST','MISSION_REWARD','SHOP_PURCHASE','WEEKLY_CHEST') NOT NULL,
  `grant_id` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `reference_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK751otfjq58m0c9aj3so83b6u3` (`event_key`),
  UNIQUE KEY `uq_coin_transactions_event_key` (`event_key`),
  UNIQUE KEY `UK1cqhhmq7bj7kv4n6s00lipwng` (`grant_id`),
  KEY `idx_coin_transactions_user_time` (`user_id`,`created_at`),
  CONSTRAINT `FK5d89p6hj7c11melqoyyy0kyni` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK5yx3to05if6dyl2dj7c0t8dq3` FOREIGN KEY (`grant_id`) REFERENCES `reward_grants` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `collections`
--

DROP TABLE IF EXISTS `collections`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collections` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `translation` varchar(255) DEFAULT NULL,
  `type` enum('SYSTEM','USER') NOT NULL,
  `owner_id` bigint(20) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKddet6fvs2nj65hg90ew34sgjy` (`name`),
  KEY `FKlmq95j898r2rw4qmxcqnq9uf7` (`owner_id`),
  CONSTRAINT `FKlmq95j898r2rw4qmxcqnq9uf7` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `definitions`
--

DROP TABLE IF EXISTS `definitions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `definitions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `definition` text NOT NULL,
  `definition_lang` varchar(10) DEFAULT NULL,
  `links` varchar(2048) DEFAULT NULL,
  `pos` varchar(50) DEFAULT NULL,
  `sub_pos` varchar(50) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=57893551 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `experience_logs`
--

DROP TABLE IF EXISTS `experience_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `experience_logs` (
  `id` varchar(36) NOT NULL,
  `amount` bigint(20) NOT NULL,
  `balance_after` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `event_key` varchar(191) NOT NULL,
  `source_id` varchar(100) NOT NULL,
  `source_type` enum('ADMIN_ADJUSTMENT','DAILY_CHEST','FLASHCARD_REVIEW','MISSION_REWARD','QUIZ','SAVE_WORD','WEEKLY_CHEST') NOT NULL,
  `grant_id` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) NOT NULL,
  `base_amount` int(11) NOT NULL,
  `bonus_amount` int(11) NOT NULL,
  `booster_activation_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5wtnys3u05p3qjxs3vry2tmo5` (`event_key`),
  UNIQUE KEY `uq_experience_logs_event_key` (`event_key`),
  UNIQUE KEY `UKdfkx4par99j2le9l90uhle0lk` (`grant_id`),
  KEY `idx_experience_logs_user_time` (`user_id`,`created_at`),
  CONSTRAINT `FK75ks5ajrfcmdkw5hgdl74g3f6` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK9bmcyax5jlx07qlkp93ix0052` FOREIGN KEY (`grant_id`) REFERENCES `reward_grants` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `fsrs_records`
--

DROP TABLE IF EXISTS `fsrs_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fsrs_records` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `difficulty` float NOT NULL,
  `due` datetime(6) NOT NULL,
  `lapses` int(11) NOT NULL,
  `reps` int(11) NOT NULL,
  `stability` float NOT NULL,
  `state` tinyint(4) NOT NULL CHECK (`state` between 0 and 4),
  `topic_item_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `last_review` datetime(6) DEFAULT NULL,
  `step` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_fsrs_user_topic_item` (`user_id`,`topic_item_id`),
  KEY `FKt9lt5t1ipjd7k4vu53tyh9yh2` (`topic_item_id`),
  KEY `idx_fsrs_user_due_state` (`user_id`,`due`,`state`),
  CONSTRAINT `FKjh1y193akkp995lqkud0t4m6n` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKt9lt5t1ipjd7k4vu53tyh9yh2` FOREIGN KEY (`topic_item_id`) REFERENCES `topic_items` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `learning_event_deliveries`
--

DROP TABLE IF EXISTS `learning_event_deliveries`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learning_event_deliveries` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `attempts` int(11) NOT NULL,
  `consumer` varchar(50) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `last_error` varchar(1000) DEFAULT NULL,
  `lease_token` varchar(100) DEFAULT NULL,
  `lease_until` datetime(6) DEFAULT NULL,
  `next_attempt_at` datetime(6) DEFAULT NULL,
  `status` enum('DONE','FAILED','PENDING','PROCESSING') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `event_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_learning_event_delivery` (`event_id`,`consumer`),
  CONSTRAINT `FKl77nnjxrq410923mnrsrp7yd7` FOREIGN KEY (`event_id`) REFERENCES `learning_events` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `learning_events`
--

DROP TABLE IF EXISTS `learning_events`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learning_events` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `event_key` varchar(150) NOT NULL,
  `event_type` enum('FLASHCARD_ANSWERED','FLASHCARD_SESSION_COMPLETED','QUIZ_COMPLETED','SRS_REVIEW_COMPLETED','WORD_SCANNED_AND_SAVED','WORD_TAGGED','WORD_UPDATED') NOT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `payload` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`payload`)),
  `schema_version` int(11) NOT NULL,
  `source_id` varchar(100) NOT NULL,
  `source_type` varchar(50) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `learning_date` date NOT NULL,
  `metadata` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`metadata`)),
  `source_key` varchar(191) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKltiglyy84qt9qoem8c95x1qox` (`event_key`),
  UNIQUE KEY `uq_learning_events_source_key` (`source_key`),
  KEY `idx_learning_event_user_occurred` (`user_id`,`occurred_at`),
  KEY `idx_learning_event_type_occurred` (`event_type`,`occurred_at`),
  KEY `idx_learning_events_user_date` (`user_id`,`learning_date`),
  CONSTRAINT `FKtq74cpxl51bm00fwel2upjulu` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `levels`
--

DROP TABLE IF EXISTS `levels`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `levels` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `level_number` int(11) NOT NULL,
  `required_exp` bigint(20) NOT NULL,
  `title` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKli0eoqvxan7eauih6cmj751i1` (`level_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mission_admin_audits`
--

DROP TABLE IF EXISTS `mission_admin_audits`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mission_admin_audits` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `action` varchar(30) NOT NULL,
  `after_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`after_json`)),
  `before_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`before_json`)),
  `created_at` datetime(6) NOT NULL,
  `reason` varchar(1000) NOT NULL,
  `target_id` varchar(100) NOT NULL,
  `target_type` enum('CONFIG','DELIVERY','TEMPLATE') NOT NULL,
  `version` int(11) DEFAULT NULL,
  `actor_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKo6i0rtv7pb955q9k878d0w2g4` (`actor_id`),
  CONSTRAINT `FKo6i0rtv7pb955q9k878d0w2g4` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mission_config_state`
--

DROP TABLE IF EXISTS `mission_config_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mission_config_state` (
  `id` tinyint(4) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `active_config_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKbv9d2di49yaw7wiurm3hskh7l` (`active_config_id`),
  CONSTRAINT `FKshtort148su4asoiclbyxue3t` FOREIGN KEY (`active_config_id`) REFERENCES `mission_configs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mission_configs`
--

DROP TABLE IF EXISTS `mission_configs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mission_configs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `daily_coin_cap` bigint(20) NOT NULL CHECK (`daily_coin_cap` >= 0),
  `daily_xp_cap` bigint(20) NOT NULL CHECK (`daily_xp_cap` >= 0),
  `reason` varchar(1000) NOT NULL,
  `required_mission_count` int(11) NOT NULL CHECK (`required_mission_count` <= 5 and `required_mission_count` >= 5),
  `timezone` varchar(50) NOT NULL,
  `version` int(11) NOT NULL CHECK (`version` >= 1),
  `created_by` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mission_configs_version` (`version`),
  KEY `FKqaiq7ah71hm9ksyywkl7n2ot0` (`created_by`),
  CONSTRAINT `FKqaiq7ah71hm9ksyywkl7n2ot0` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mission_daily_sets`
--

DROP TABLE IF EXISTS `mission_daily_sets`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mission_daily_sets` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `assigned_at` datetime(6) NOT NULL,
  `config_snapshot` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`config_snapshot`)),
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `mission_date` date NOT NULL,
  `config_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mission_daily_set` (`user_id`,`mission_date`),
  KEY `FKfi16y2b5igeh0m4rbl4n8154b` (`config_id`),
  CONSTRAINT `FK52qdrfn19l9bjt5fg3q37ajxy` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKfi16y2b5igeh0m4rbl4n8154b` FOREIGN KEY (`config_id`) REFERENCES `mission_configs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mission_notification_outbox`
--

DROP TABLE IF EXISTS `mission_notification_outbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mission_notification_outbox` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `attempts` int(11) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `dedupe_key` varchar(150) NOT NULL,
  `last_error` varchar(1000) DEFAULT NULL,
  `lease_token` varchar(100) DEFAULT NULL,
  `lease_until` datetime(6) DEFAULT NULL,
  `next_attempt_at` datetime(6) DEFAULT NULL,
  `notification_type` varchar(50) NOT NULL,
  `payload` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`payload`)),
  `sent_at` datetime(6) DEFAULT NULL,
  `status` enum('FAILED','PENDING','PROCESSING','SENT') NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5eh6c8yn0ox938ydbocckqnef` (`dedupe_key`),
  KEY `FK143458c6d7u0s608n64tbe4cf` (`user_id`),
  CONSTRAINT `FK143458c6d7u0s608n64tbe4cf` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mission_progress_events`
--

DROP TABLE IF EXISTS `mission_progress_events`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mission_progress_events` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `progress_delta` decimal(12,2) NOT NULL,
  `learning_event_id` binary(16) NOT NULL,
  `user_daily_mission_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mission_progress_event` (`user_daily_mission_id`,`learning_event_id`),
  KEY `FKns4h96m90d7ago9yt8cx3pueg` (`learning_event_id`),
  CONSTRAINT `FKkb9s6hwtlfy6uytwe585tmcs9` FOREIGN KEY (`user_daily_mission_id`) REFERENCES `user_daily_missions` (`id`),
  CONSTRAINT `FKns4h96m90d7ago9yt8cx3pueg` FOREIGN KEY (`learning_event_id`) REFERENCES `learning_events` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mission_templates`
--

DROP TABLE IF EXISTS `mission_templates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mission_templates` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `action_type` varchar(100) NOT NULL,
  `aggregation_type` enum('COUNT','MAX','PERCENTAGE','STREAK','SUM','UNIQUE_COUNT') NOT NULL,
  `archived_at` datetime(6) DEFAULT NULL,
  `category` varchar(20) NOT NULL,
  `code` varchar(64) NOT NULL,
  `completion_rule` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`completion_rule`)),
  `description` varchar(255) NOT NULL,
  `eligibility_rule` varchar(40) NOT NULL DEFAULT 'NONE',
  `is_active` bit(1) NOT NULL,
  `name` varchar(100) NOT NULL,
  `navigation_params` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`navigation_params`)),
  `required_capability` varchar(100) NOT NULL,
  `reward` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`reward`)),
  `target_value` int(11) NOT NULL,
  `trigger_event` varchar(40) NOT NULL,
  `unit` varchar(50) NOT NULL,
  `valid_from` datetime(6) DEFAULT NULL,
  `valid_to` datetime(6) DEFAULT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `weight` int(11) NOT NULL CHECK (`weight` <= 100 and `weight` >= 1),
  `min_quiz_accuracy_percent` int(11) DEFAULT NULL,
  `quiz_requested_count` int(11) DEFAULT NULL,
  `reward_coin` int(11) NOT NULL,
  `reward_item_code` varchar(64) DEFAULT NULL,
  `reward_item_fallback_coin` int(11) DEFAULT NULL,
  `reward_xp` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhcusbnxqtcvpwtokkl26tkwm5` (`code`),
  UNIQUE KEY `uq_mission_templates_code` (`code`),
  KEY `idx_mission_templates_category_active` (`category`,`is_active`),
  KEY `idx_mission_templates_trigger_active` (`trigger_event`,`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notifications`
--

DROP TABLE IF EXISTS `notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `content` tinytext NOT NULL,
  `title` varchar(255) NOT NULL,
  `type` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pronunciations`
--

DROP TABLE IF EXISTS `pronunciations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pronunciations` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `ipa` varchar(512) DEFAULT NULL,
  `region` varchar(100) DEFAULT NULL,
  `word_id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_pronunciation_unique` (`word_id`,`ipa`,`region`),
  UNIQUE KEY `idx_pronunciations_unique` (`word_id`,`ipa`,`region`),
  KEY `idx_pronunciation_word_id` (`word_id`),
  KEY `idx_pronunciations_word_id` (`word_id`),
  CONSTRAINT `FKdba3ivc7k81oam1etgve7jg4f` FOREIGN KEY (`word_id`) REFERENCES `words` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=108809601 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `quiz_attempt_answers`
--

DROP TABLE IF EXISTS `quiz_attempt_answers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_attempt_answers` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `answer_status` enum('CORRECT','INCORRECT','UNANSWERED') NOT NULL,
  `matched_tile_key` varchar(36) DEFAULT NULL,
  `normalized_text` varchar(500) DEFAULT NULL,
  `raw_text` varchar(500) DEFAULT NULL,
  `selected_option_key` varchar(36) DEFAULT NULL,
  `attempt_id` bigint(20) NOT NULL,
  `question_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quiz_attempt_answers_question` (`attempt_id`,`question_id`),
  KEY `FK59s9075rs7i5520p8awj6ri1` (`question_id`),
  CONSTRAINT `FK59s9075rs7i5520p8awj6ri1` FOREIGN KEY (`question_id`) REFERENCES `quiz_questions` (`id`),
  CONSTRAINT `FKqo1br76p5c2piffjoaiqm5yuu` FOREIGN KEY (`attempt_id`) REFERENCES `quiz_attempts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `quiz_attempts`
--

DROP TABLE IF EXISTS `quiz_attempts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_attempts` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `accuracy_bps` int(11) NOT NULL,
  `answered_count` int(11) NOT NULL,
  `best_correct_streak` int(11) NOT NULL,
  `business_date` date NOT NULL,
  `correct_count` int(11) NOT NULL,
  `duration_ms` bigint(20) NOT NULL,
  `incorrect_count` int(11) NOT NULL,
  `request_hash` varchar(64) NOT NULL,
  `reward_receipt` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`reward_receipt`)),
  `submitted_at` datetime(6) NOT NULL,
  `unanswered_count` int(11) NOT NULL,
  `quiz_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quiz_attempts_quiz` (`quiz_id`),
  KEY `idx_quiz_attempts_submitted` (`submitted_at`,`id`),
  CONSTRAINT `FKfwipvfipnnwsoacoyv5k7fbxc` FOREIGN KEY (`quiz_id`) REFERENCES `quizzes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `quiz_coverage_items`
--

DROP TABLE IF EXISTS `quiz_coverage_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_coverage_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `covered_at` datetime(6) DEFAULT NULL,
  `cycle_number` bigint(20) NOT NULL,
  `is_eligible` bit(1) NOT NULL,
  `position` int(11) NOT NULL,
  `covered_attempt_id` bigint(20) DEFAULT NULL,
  `selection_state_id` bigint(20) NOT NULL,
  `topic_item_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quiz_coverage_item_cycle_item` (`selection_state_id`,`cycle_number`,`topic_item_id`),
  UNIQUE KEY `uk_quiz_coverage_item_cycle_pos` (`selection_state_id`,`cycle_number`,`position`),
  KEY `idx_quiz_coverage_eligible_order` (`selection_state_id`,`cycle_number`,`is_eligible`,`covered_at`,`position`),
  KEY `FKp4kvk4htswxkrrhv4dduoxbdq` (`covered_attempt_id`),
  KEY `FKpxx6bmoa1uore6ctng6gpb1i2` (`topic_item_id`),
  CONSTRAINT `FKkctbt82rqsbl3hk95la2aubgg` FOREIGN KEY (`selection_state_id`) REFERENCES `quiz_selection_states` (`id`),
  CONSTRAINT `FKp4kvk4htswxkrrhv4dduoxbdq` FOREIGN KEY (`covered_attempt_id`) REFERENCES `quiz_attempts` (`id`),
  CONSTRAINT `FKpxx6bmoa1uore6ctng6gpb1i2` FOREIGN KEY (`topic_item_id`) REFERENCES `topic_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `quiz_daily_reward_usage`
--

DROP TABLE IF EXISTS `quiz_daily_reward_usage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_daily_reward_usage` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `coin_granted` bigint(20) NOT NULL,
  `reward_date` date NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `xp_granted` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quiz_daily_reward_usage` (`user_id`,`reward_date`),
  CONSTRAINT `FKr7blfybn8j4pajt7nml9wc59f` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `quiz_item_mode_stats`
--

DROP TABLE IF EXISTS `quiz_item_mode_stats`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_item_mode_stats` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `answered_count` bigint(20) NOT NULL,
  `correct_count` bigint(20) NOT NULL,
  `direction` enum('MEANING_TO_WORD','WORD_TO_MEANING') NOT NULL,
  `incorrect_count` bigint(20) NOT NULL,
  `last_answered_at` datetime(6) DEFAULT NULL,
  `last_presented_at` datetime(6) DEFAULT NULL,
  `last_result` enum('CORRECT','INCORRECT','UNANSWERED') DEFAULT NULL,
  `presented_count` bigint(20) NOT NULL,
  `question_type` enum('FILL_BLANK','MATCHING','MCQ') NOT NULL,
  `unanswered_count` bigint(20) NOT NULL,
  `last_attempt_id` bigint(20) DEFAULT NULL,
  `topic_id` bigint(20) NOT NULL,
  `topic_item_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quiz_item_mode_stats` (`user_id`,`topic_id`,`topic_item_id`,`question_type`,`direction`),
  KEY `FK3og80gk4lnoki80lgavsiumkd` (`last_attempt_id`),
  KEY `FK71xiu1p9jhopswu9jn3evla8v` (`topic_id`),
  KEY `FKqw0hw75la8lsxdi8hetq9wca2` (`topic_item_id`),
  CONSTRAINT `FK3og80gk4lnoki80lgavsiumkd` FOREIGN KEY (`last_attempt_id`) REFERENCES `quiz_attempts` (`id`),
  CONSTRAINT `FK71xiu1p9jhopswu9jn3evla8v` FOREIGN KEY (`topic_id`) REFERENCES `topics` (`id`),
  CONSTRAINT `FKja1ad6955t4bruxllhojjk78` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKqw0hw75la8lsxdi8hetq9wca2` FOREIGN KEY (`topic_item_id`) REFERENCES `topic_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `quiz_matching_attempts`
--

DROP TABLE IF EXISTS `quiz_matching_attempts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_matching_attempts` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `attempt_no` int(11) NOT NULL,
  `correct` bit(1) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `left_key` varchar(64) NOT NULL,
  `right_key` varchar(64) NOT NULL,
  `round_matched_count` int(11) NOT NULL,
  `round_no` int(11) NOT NULL,
  `round_total_pairs` int(11) NOT NULL,
  `question_id` bigint(20) NOT NULL,
  `quiz_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quiz_attempt_key` (`quiz_id`,`idempotency_key`),
  KEY `FKacs5maqaqclmun93oais67ybp` (`question_id`),
  CONSTRAINT `FKacs5maqaqclmun93oais67ybp` FOREIGN KEY (`question_id`) REFERENCES `quiz_questions` (`id`),
  CONSTRAINT `FKe35m3y025vi9vgl83rkv1vs7y` FOREIGN KEY (`quiz_id`) REFERENCES `quizzes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `quiz_matching_tiles`
--

DROP TABLE IF EXISTS `quiz_matching_tiles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_matching_tiles` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `label` text NOT NULL,
  `position` int(11) NOT NULL,
  `tile_key` varchar(64) NOT NULL,
  `quiz_id` bigint(20) NOT NULL,
  `source_question_id` bigint(20) NOT NULL,
  `display_order` int(11) NOT NULL,
  `matched` bit(1) NOT NULL,
  `round_no` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quiz_matching_tiles_key` (`quiz_id`,`tile_key`),
  UNIQUE KEY `uk_quiz_matching_tiles_pos` (`quiz_id`,`position`),
  UNIQUE KEY `uk_quiz_matching_tiles_question` (`quiz_id`,`source_question_id`),
  UNIQUE KEY `uk_quiz_tile_key` (`quiz_id`,`tile_key`),
  KEY `FKqig8e2cpqo8y3v94i2uqx13vu` (`source_question_id`),
  CONSTRAINT `FKqig8e2cpqo8y3v94i2uqx13vu` FOREIGN KEY (`source_question_id`) REFERENCES `quiz_questions` (`id`),
  CONSTRAINT `FKrcu4cey4k67721fsda1i651vw` FOREIGN KEY (`quiz_id`) REFERENCES `quizzes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `quiz_policy_state`
--

DROP TABLE IF EXISTS `quiz_policy_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_policy_state` (
  `id` tinyint(4) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `active_policy_version_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKs2btyj39fforlk35qetyer713` (`active_policy_version_id`),
  CONSTRAINT `FKs2btyj39fforlk35qetyer713` FOREIGN KEY (`active_policy_version_id`) REFERENCES `quiz_policy_versions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `quiz_policy_versions`
--

DROP TABLE IF EXISTS `quiz_policy_versions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_policy_versions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `config` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`config`)),
  `reason` varchar(1000) NOT NULL,
  `version` int(11) NOT NULL,
  `created_by` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quiz_policy_versions_version` (`version`),
  KEY `FK4hfwt2a9r41rp4j1n6s4j7drr` (`created_by`),
  CONSTRAINT `FK4hfwt2a9r41rp4j1n6s4j7drr` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `quiz_question_options`
--

DROP TABLE IF EXISTS `quiz_question_options`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_question_options` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `label` text NOT NULL,
  `option_key` varchar(64) NOT NULL,
  `position` int(11) NOT NULL,
  `question_id` bigint(20) NOT NULL,
  `display_order` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quiz_question_options_key` (`question_id`,`option_key`),
  UNIQUE KEY `uk_quiz_question_options_pos` (`question_id`,`position`),
  UNIQUE KEY `uk_quiz_option_key` (`question_id`,`option_key`),
  CONSTRAINT `FKkl6tbgdyegw1o9agywy1xje60` FOREIGN KEY (`question_id`) REFERENCES `quiz_questions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `quiz_questions`
--

DROP TABLE IF EXISTS `quiz_questions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_questions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `answer_key` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`answer_key`)),
  `explanation_snapshot` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`explanation_snapshot`)),
  `hint` text DEFAULT NULL,
  `position` int(11) NOT NULL,
  `prompt` text NOT NULL,
  `round_number` int(11) DEFAULT NULL,
  `source_snapshot` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`source_snapshot`)),
  `quiz_id` bigint(20) NOT NULL,
  `source_topic_item_id` bigint(20) DEFAULT NULL,
  `attempt_count` int(11) NOT NULL,
  `check_idempotency_key` varchar(128) DEFAULT NULL,
  `checked_at` datetime(6) DEFAULT NULL,
  `correct_answer` text NOT NULL,
  `correct_option_key` varchar(64) DEFAULT NULL,
  `correct_right_key` varchar(64) DEFAULT NULL,
  `learner_answer` text DEFAULT NULL,
  `left_display_order` int(11) DEFAULT NULL,
  `left_key` varchar(64) DEFAULT NULL,
  `outcome` enum('CORRECT','INCORRECT','UNCOMMITTED') NOT NULL,
  `resolved` bit(1) NOT NULL,
  `resolved_at` datetime(6) DEFAULT NULL,
  `round_no` int(11) DEFAULT NULL,
  `topic_item_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quiz_questions_quiz_position` (`quiz_id`,`position`),
  UNIQUE KEY `uk_quiz_question_position` (`quiz_id`,`position`),
  UNIQUE KEY `uk_quiz_question_left_key` (`quiz_id`,`left_key`),
  KEY `FKrwjq397csdf9xco3dtouqop14` (`source_topic_item_id`),
  CONSTRAINT `FKanfmgf6ksbdnv7ojb0pfve54q` FOREIGN KEY (`quiz_id`) REFERENCES `quizzes` (`id`),
  CONSTRAINT `FKrwjq397csdf9xco3dtouqop14` FOREIGN KEY (`source_topic_item_id`) REFERENCES `topic_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `quiz_selection_states`
--

DROP TABLE IF EXISTS `quiz_selection_states`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_selection_states` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `cycle_number` bigint(20) NOT NULL,
  `direction` enum('MEANING_TO_WORD','WORD_TO_MEANING') NOT NULL,
  `question_type` enum('FILL_BLANK','MATCHING','MCQ') NOT NULL,
  `active_quiz_id` bigint(20) DEFAULT NULL,
  `topic_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quiz_selection_state` (`user_id`,`topic_id`,`question_type`,`direction`),
  UNIQUE KEY `UKcnlfttq03m1h05yk6rkjlim5l` (`active_quiz_id`),
  KEY `FKr9y8ax6hongayjkqtxqqmtqj1` (`topic_id`),
  CONSTRAINT `FKabdq5nprue6hw4sfa3kemmnn` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKb1f4ulucwn4nykl9japcujwut` FOREIGN KEY (`active_quiz_id`) REFERENCES `quizzes` (`id`),
  CONSTRAINT `FKr9y8ax6hongayjkqtxqqmtqj1` FOREIGN KEY (`topic_id`) REFERENCES `topics` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `quiz_topic_item_practice_stats`
--

DROP TABLE IF EXISTS `quiz_topic_item_practice_stats`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_topic_item_practice_stats` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `last_presented_at` datetime(6) DEFAULT NULL,
  `presented_count` bigint(20) NOT NULL,
  `last_attempt_id` bigint(20) DEFAULT NULL,
  `topic_id` bigint(20) NOT NULL,
  `topic_item_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quiz_item_practice_stats` (`user_id`,`topic_id`,`topic_item_id`),
  KEY `FK2jk1lbg74ge31vpbqavdqae85` (`last_attempt_id`),
  KEY `FKnnekw6gvl6upmu91718b12nj5` (`topic_id`),
  KEY `FKcxcuxljnfpgklpy3ny7q9v7ry` (`topic_item_id`),
  CONSTRAINT `FK2jk1lbg74ge31vpbqavdqae85` FOREIGN KEY (`last_attempt_id`) REFERENCES `quiz_attempts` (`id`),
  CONSTRAINT `FK71i2u3kw340w7nv8b1e8yaoyw` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKcxcuxljnfpgklpy3ny7q9v7ry` FOREIGN KEY (`topic_item_id`) REFERENCES `topic_items` (`id`),
  CONSTRAINT `FKnnekw6gvl6upmu91718b12nj5` FOREIGN KEY (`topic_id`) REFERENCES `topics` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `quizzes`
--

DROP TABLE IF EXISTS `quizzes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quizzes` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `direction` enum('EN_VI','VI_EN') DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `generator_version` varchar(50) NOT NULL,
  `normalizer_version` varchar(50) DEFAULT NULL,
  `question_count` int(11) NOT NULL,
  `question_type` enum('FILL_BLANK','MATCHING','MCQ') NOT NULL,
  `reward_snapshot` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`reward_snapshot`)),
  `selection_snapshot` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`selection_snapshot`)),
  `source_snapshot` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`source_snapshot`)),
  `status` enum('CANCELLED','COMPLETED','EXPIRED','IN_PROGRESS') NOT NULL,
  `policy_version_id` bigint(20) NOT NULL,
  `selection_state_id` bigint(20) DEFAULT NULL,
  `source_topic_id` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `correct_count` int(11) DEFAULT NULL,
  `create_idempotency_key` varchar(128) NOT NULL,
  `incorrect_count` int(11) DEFAULT NULL,
  `mode` enum('FILL_BLANK','MATCHING','MCQ') NOT NULL,
  `requested_count` int(11) NOT NULL,
  `topic_id` bigint(20) NOT NULL,
  `topic_name` varchar(255) NOT NULL,
  `total_units` int(11) DEFAULT NULL,
  `version` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quiz_user_create_key` (`user_id`,`create_idempotency_key`),
  KEY `idx_quizzes_user_created` (`user_id`,`created_at`,`id`),
  KEY `idx_quizzes_status_expires` (`status`,`expires_at`,`id`),
  KEY `idx_quizzes_user_status` (`user_id`,`status`),
  KEY `FKa0n3umo1ilyqarlqs4x2ggita` (`policy_version_id`),
  KEY `FKs763oe7j4snhmg1cqgwf5fixs` (`selection_state_id`),
  KEY `FK36y4qnmq7y00748bskrycbbe2` (`source_topic_id`),
  KEY `idx_quiz_user_created` (`user_id`,`created_at`),
  CONSTRAINT `FK36y4qnmq7y00748bskrycbbe2` FOREIGN KEY (`source_topic_id`) REFERENCES `topics` (`id`),
  CONSTRAINT `FKa0n3umo1ilyqarlqs4x2ggita` FOREIGN KEY (`policy_version_id`) REFERENCES `quiz_policy_versions` (`id`),
  CONSTRAINT `FKa2rodablx8mhce3bdaj19eqhe` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKs763oe7j4snhmg1cqgwf5fixs` FOREIGN KEY (`selection_state_id`) REFERENCES `quiz_selection_states` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `request_idempotencies`
--

DROP TABLE IF EXISTS `request_idempotencies`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `request_idempotencies` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `operation` varchar(50) NOT NULL,
  `request_hash` varchar(64) NOT NULL,
  `request_key` varchar(150) NOT NULL,
  `response_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`response_json`)),
  `actor_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_request_idempotency` (`actor_id`,`operation`,`request_key`),
  CONSTRAINT `FKngysfk0hm9bosijag8454tdi7` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reward_grants`
--

DROP TABLE IF EXISTS `reward_grants`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reward_grants` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `accepted_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `grant_mode` enum('COMPENSATION','NORMAL') NOT NULL,
  `reward` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`reward`)),
  `source_date` date NOT NULL,
  `target_id` varchar(100) NOT NULL,
  `target_type` varchar(30) NOT NULL,
  `actor_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_reward_grant_target` (`target_type`,`target_id`),
  KEY `FKn8lq1jqw1ngs9px4jmu7asryc` (`actor_id`),
  KEY `FKoj0uno1yrl2txakvh6exdnsjc` (`user_id`),
  CONSTRAINT `FKn8lq1jqw1ngs9px4jmu7asryc` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKoj0uno1yrl2txakvh6exdnsjc` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `scan_requests`
--

DROP TABLE IF EXISTS `scan_requests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `scan_requests` (
  `id` varchar(36) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `detection_count` int(11) DEFAULT NULL,
  `error_code` enum('AI_ERROR','AI_QUEUE_FULL','AI_TIMEOUT','AI_UNAVAILABLE','INTERRUPTED','INVALID_IMAGE','QUOTA_EXCEEDED') DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  `model_version` varchar(100) DEFAULT NULL,
  `object_key` varchar(512) DEFAULT NULL,
  `processing_time_ms` bigint(20) DEFAULT NULL,
  `result_json` longtext DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `status` enum('DONE','FAILED','PENDING','PROCESSING') NOT NULL,
  `version` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_scan_requests_user_created` (`user_id`,`created_at`),
  KEY `idx_scan_requests_status` (`status`),
  CONSTRAINT `FKip8wxppeaxpr4h3stkvf9k7h6` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `schema_attribute_groups`
--

DROP TABLE IF EXISTS `schema_attribute_groups`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_attribute_groups` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `schema_id` bigint(20) NOT NULL,
  `multiple` bit(1) NOT NULL,
  `name` varchar(255) NOT NULL,
  `position` smallint(6) NOT NULL,
  `label` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_schema_group_name` (`schema_id`,`name`),
  CONSTRAINT `FKmwvdy98v1u0ycguwmvk5u0yy` FOREIGN KEY (`schema_id`) REFERENCES `schemas` (`id`),
  CONSTRAINT `fk_schema_attribute_groups_schema` FOREIGN KEY (`schema_id`) REFERENCES `schemas` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=397 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `schema_attributes`
--

DROP TABLE IF EXISTS `schema_attributes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_attributes` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `data_type` varchar(50) NOT NULL,
  `name` varchar(255) NOT NULL,
  `position` smallint(6) NOT NULL,
  `required` bit(1) NOT NULL,
  `group_id` bigint(20) NOT NULL,
  `label` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_group_attribute_name` (`group_id`,`name`),
  CONSTRAINT `FKormlxe2ahmors20k3vcwt7smw` FOREIGN KEY (`group_id`) REFERENCES `schema_attribute_groups` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1576 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `schemas`
--

DROP TABLE IF EXISTS `schemas`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schemas` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `description` text DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `code` varchar(50) DEFAULT NULL,
  `is_system` bit(1) NOT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6qud6g8oougdorg54rnu0js3h` (`code`),
  KEY `fk_schemas_user` (`user_id`),
  CONSTRAINT `FK8t3dvvs1psjfdfxelywwo5696` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_schemas_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=279 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `shop_items`
--

DROP TABLE IF EXISTS `shop_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shop_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `is_active` bit(1) NOT NULL,
  `name` varchar(255) NOT NULL,
  `price` bigint(20) NOT NULL,
  `type` varchar(50) NOT NULL,
  `available_from` datetime(6) DEFAULT NULL,
  `available_until` datetime(6) DEFAULT NULL,
  `boost_duration_minutes` int(11) DEFAULT NULL,
  `code` varchar(64) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `frame_asset_key` varchar(255) DEFAULT NULL,
  `icon_key` varchar(255) DEFAULT NULL,
  `max_quantity` int(11) DEFAULT NULL,
  `published_at` datetime(6) DEFAULT NULL,
  `purchasable` bit(1) NOT NULL,
  `sort_order` int(11) NOT NULL,
  `status` enum('ARCHIVED','DRAFT','PUBLISHED') NOT NULL,
  `theme_key` enum('DEFAULT','FOREST_MINT','MIDNIGHT_NEON','SUNSET_AMBER') DEFAULT NULL,
  `version` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_shop_items_code` (`code`),
  UNIQUE KEY `uq_shop_items_id_type` (`id`,`type`),
  UNIQUE KEY `uq_shop_items_theme_key` (`theme_key`),
  KEY `idx_shop_items_catalog` (`status`,`purchasable`,`type`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `study_daily_cards`
--

DROP TABLE IF EXISTS `study_daily_cards`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `study_daily_cards` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `collection_id` bigint(20) NOT NULL,
  `study_date` date NOT NULL,
  `topic_item_id` bigint(20) NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_study_daily_card_user_collection_date_item` (`user_id`,`collection_id`,`study_date`,`topic_item_id`),
  KEY `fk_study_daily_card_collection` (`collection_id`),
  KEY `fk_study_daily_card_topic_item` (`topic_item_id`),
  CONSTRAINT `fk_study_daily_card_collection` FOREIGN KEY (`collection_id`) REFERENCES `collections` (`id`),
  CONSTRAINT `fk_study_daily_card_topic_item` FOREIGN KEY (`topic_item_id`) REFERENCES `topic_items` (`id`),
  CONSTRAINT `fk_study_daily_card_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `study_daily_progress`
--

DROP TABLE IF EXISTS `study_daily_progress`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `study_daily_progress` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `collection_id` bigint(20) NOT NULL,
  `study_date` date NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_study_daily_progress_user_collection_date` (`user_id`,`collection_id`,`study_date`),
  KEY `fk_study_daily_progress_collection` (`collection_id`),
  CONSTRAINT `fk_study_daily_progress_collection` FOREIGN KEY (`collection_id`) REFERENCES `collections` (`id`),
  CONSTRAINT `fk_study_daily_progress_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `template_elements`
--

DROP TABLE IF EXISTS `template_elements`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `template_elements` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `position` int(11) NOT NULL,
  `type` enum('COLUMN_BREAK','FIELD','SECTION_BREAK') NOT NULL,
  `template_id` bigint(20) NOT NULL,
  `repeatable` bit(1) DEFAULT NULL,
  `section_label` varchar(100) DEFAULT NULL,
  `side` enum('BACK','FRONT') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_element_position` (`template_id`,`position`),
  CONSTRAINT `FKluexrhlyd60hagpivgu9n7ge0` FOREIGN KEY (`template_id`) REFERENCES `templates` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=247 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `template_fields`
--

DROP TABLE IF EXISTS `template_fields`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `template_fields` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `alignment` enum('CENTER','JUSTIFY','LEFT','RIGHT') DEFAULT NULL,
  `audio_action` bit(1) NOT NULL,
  `color` varchar(255) DEFAULT NULL,
  `field_label` varchar(255) DEFAULT NULL,
  `font_size` int(11) DEFAULT NULL,
  `hide_if_empty` bit(1) NOT NULL,
  `semantic_role` enum('AUDIO','DEFINITION','EXAMPLE_SENTENCE','IMAGE','NATIVE_TRANSLATION','TARGET_WORD') DEFAULT NULL,
  `element_id` bigint(20) NOT NULL,
  `schema_attribute_id` bigint(20) NOT NULL,
  `hidden` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK21j7kbprqqj7ky99t9w428ya4` (`element_id`),
  KEY `fk_template_fields_schema_attribute` (`schema_attribute_id`),
  CONSTRAINT `FKcpfx2fghgclo1r8r55aqi0jyf` FOREIGN KEY (`element_id`) REFERENCES `template_elements` (`id`),
  CONSTRAINT `fk_template_fields_schema_attribute` FOREIGN KEY (`schema_attribute_id`) REFERENCES `schema_attributes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=184 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `templates`
--

DROP TABLE IF EXISTS `templates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `templates` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `name` varchar(255) NOT NULL,
  `topic_id` bigint(20) DEFAULT NULL,
  `code` varchar(50) DEFAULT NULL,
  `is_default` bit(1) NOT NULL,
  `schema_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKaptls6au78yaa6ivsho5w2mc1` (`topic_id`),
  KEY `FK38axylijjmc4y0cmeyf8gsrqf` (`schema_id`),
  CONSTRAINT `FK38axylijjmc4y0cmeyf8gsrqf` FOREIGN KEY (`schema_id`) REFERENCES `schemas` (`id`),
  CONSTRAINT `FKaptls6au78yaa6ivsho5w2mc1` FOREIGN KEY (`topic_id`) REFERENCES `topics` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `topic_item_attribute_groups`
--

DROP TABLE IF EXISTS `topic_item_attribute_groups`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `topic_item_attribute_groups` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `position` smallint(6) NOT NULL,
  `group_definition_id` bigint(20) NOT NULL,
  `topic_item_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_item_group_position` (`topic_item_id`,`group_definition_id`,`position`),
  KEY `FK1iq6pntqroatsfk4fe98pkwwv` (`group_definition_id`),
  CONSTRAINT `FK1iq6pntqroatsfk4fe98pkwwv` FOREIGN KEY (`group_definition_id`) REFERENCES `schema_attribute_groups` (`id`),
  CONSTRAINT `FKffesb79va3nud4qrgk7cotuif` FOREIGN KEY (`topic_item_id`) REFERENCES `topic_items` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=56599 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `topic_item_attribute_values`
--

DROP TABLE IF EXISTS `topic_item_attribute_values`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `topic_item_attribute_values` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `value` text NOT NULL,
  `schema_attribute_id` bigint(20) NOT NULL,
  `group_instance_id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_group_instance_attribute` (`group_instance_id`,`schema_attribute_id`),
  KEY `FK4pld595129inv6d2jd73qxox5` (`schema_attribute_id`),
  CONSTRAINT `FKl342e1b7vbsvw5fi2hl44gtpu` FOREIGN KEY (`group_instance_id`) REFERENCES `topic_item_attribute_groups` (`id`),
  CONSTRAINT `fk_tiav_schema_attribute` FOREIGN KEY (`schema_attribute_id`) REFERENCES `schema_attributes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=175438 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `topic_items`
--

DROP TABLE IF EXISTS `topic_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `topic_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `topic_id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKa94m7l7ux3ln5xqsopw1m8slr` (`topic_id`),
  CONSTRAINT `FKa94m7l7ux3ln5xqsopw1m8slr` FOREIGN KEY (`topic_id`) REFERENCES `topics` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=15665 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `topic_schemas`
--

DROP TABLE IF EXISTS `topic_schemas`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `topic_schemas` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `schema_id` bigint(20) NOT NULL,
  `topic_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKkaymy5g9iqpk2qk5t889hc9db` (`topic_id`),
  KEY `FK78d8slrkepekk38xtg1qoxlum` (`schema_id`),
  CONSTRAINT `FK1d3vsn5t11h1f912levga7mt4` FOREIGN KEY (`topic_id`) REFERENCES `topics` (`id`),
  CONSTRAINT `FK78d8slrkepekk38xtg1qoxlum` FOREIGN KEY (`schema_id`) REFERENCES `schemas` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `topics`
--

DROP TABLE IF EXISTS `topics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `topics` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `description` text DEFAULT NULL,
  `description_translation` text DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `translation` varchar(255) DEFAULT NULL,
  `collection_id` bigint(20) NOT NULL,
  `parent_id` bigint(20) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `active_template_id` bigint(20) DEFAULT NULL,
  `schema_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK2ndhvllsblve2ld7cvmokulmj` (`collection_id`),
  KEY `FKs8uyr0jt9yx324u0bl1b8jea` (`parent_id`),
  KEY `FK1t1fa7hmxvhaq58pqmj6kbi7v` (`active_template_id`),
  KEY `idx_topics_schema_id` (`schema_id`),
  CONSTRAINT `FK1t1fa7hmxvhaq58pqmj6kbi7v` FOREIGN KEY (`active_template_id`) REFERENCES `templates` (`id`),
  CONSTRAINT `FK2ndhvllsblve2ld7cvmokulmj` FOREIGN KEY (`collection_id`) REFERENCES `collections` (`id`),
  CONSTRAINT `FKs8uyr0jt9yx324u0bl1b8jea` FOREIGN KEY (`parent_id`) REFERENCES `topics` (`id`),
  CONSTRAINT `fk_topics_schema` FOREIGN KEY (`schema_id`) REFERENCES `schemas` (`id`) ON DELETE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=226 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `translations`
--

DROP TABLE IF EXISTS `translations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `translations` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `lang_code` varchar(20) DEFAULT NULL,
  `translation` varchar(1024) DEFAULT NULL,
  `word_id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  PRIMARY KEY (`id`),
  KEY `idx_translation_word_id` (`word_id`),
  KEY `idx_translations_word_id` (`word_id`),
  CONSTRAINT `FKkobtf2ub1uhh53rtmi9q1h6h2` FOREIGN KEY (`word_id`) REFERENCES `words` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=110374501 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_authorities`
--

DROP TABLE IF EXISTS `user_authorities`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_authorities` (
  `user_id` bigint(20) NOT NULL,
  `authority_name` varchar(50) NOT NULL,
  PRIMARY KEY (`user_id`,`authority_name`),
  KEY `FK6ktglpl5mjosa283rvken2py5` (`authority_name`),
  CONSTRAINT `FK6ktglpl5mjosa283rvken2py5` FOREIGN KEY (`authority_name`) REFERENCES `authorities` (`name`),
  CONSTRAINT `FKpqlsjpkybgos9w2svcri7j8xy` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_daily_mission_claims`
--

DROP TABLE IF EXISTS `user_daily_mission_claims`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_daily_mission_claims` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `fallback_coin` bigint(20) NOT NULL DEFAULT 0,
  `granted_item_quantity` int(11) NOT NULL DEFAULT 0,
  `idempotency_key` varchar(128) NOT NULL,
  `reward_coin` int(11) NOT NULL,
  `reward_item_code` varchar(64) DEFAULT NULL,
  `reward_xp` int(11) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `user_daily_mission_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mission_claims_mission` (`user_daily_mission_id`),
  UNIQUE KEY `uq_mission_claims_key` (`user_id`,`idempotency_key`),
  CONSTRAINT `FKkyv9pe4lvqwiydg39r9dcnx4f` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `chk_mission_claims_amounts` CHECK (`reward_coin` >= 0 and `reward_xp` >= 0 and `granted_item_quantity` >= 0 and `fallback_coin` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_daily_mission_progress_logs`
--

DROP TABLE IF EXISTS `user_daily_mission_progress_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_daily_mission_progress_logs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `source_key` varchar(191) NOT NULL,
  `user_daily_mission_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mission_progress_logs_source` (`user_daily_mission_id`,`source_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_daily_missions`
--

DROP TABLE IF EXISTS `user_daily_missions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_daily_missions` (
  `id` binary(16) NOT NULL,
  `assigned_at` datetime(6) NOT NULL,
  `claimed_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `current_progress` int(11) NOT NULL DEFAULT 0,
  `expires_at` datetime(6) NOT NULL,
  `mission_date` date NOT NULL,
  `progress_state` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`progress_state`)),
  `reward_snapshot` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`reward_snapshot`)),
  `slot` tinyint(4) NOT NULL,
  `snapshot_data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`snapshot_data`)),
  `status` varchar(20) NOT NULL,
  `target_value` int(11) NOT NULL,
  `template_version` int(11) NOT NULL,
  `trigger_event` varchar(40) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `daily_set_id` bigint(20) NOT NULL,
  `mission_template_id` binary(16) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `category` varchar(20) NOT NULL CHECK (`category` in ('SCAN','VOCABULARY','FLASHCARD','SRS','QUIZ','RETENTION','SHOP')),
  `code` varchar(64) NOT NULL,
  `description` varchar(255) NOT NULL,
  `min_quiz_accuracy_percent` int(11) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `quiz_requested_count` int(11) DEFAULT NULL,
  `reward_coin` int(11) NOT NULL,
  `reward_item_code` varchar(64) DEFAULT NULL,
  `reward_item_fallback_coin` int(11) DEFAULT NULL,
  `reward_xp` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_daily_missions_slot` (`user_id`,`mission_date`,`slot`),
  UNIQUE KEY `uq_user_daily_missions_template` (`user_id`,`mission_date`,`mission_template_id`),
  KEY `idx_user_daily_mission_event` (`user_id`,`mission_date`,`trigger_event`,`status`),
  KEY `idx_user_daily_mission_expiry` (`status`,`expires_at`,`user_id`),
  KEY `FKbr18x799h5c0bhfnrrwg53rrb` (`daily_set_id`),
  KEY `idx_user_daily_missions_template` (`mission_template_id`),
  CONSTRAINT `FKbr18x799h5c0bhfnrrwg53rrb` FOREIGN KEY (`daily_set_id`) REFERENCES `mission_daily_sets` (`id`),
  CONSTRAINT `FKfkwvysv185yvy3chpwgnscgh9` FOREIGN KEY (`mission_template_id`) REFERENCES `mission_templates` (`id`),
  CONSTRAINT `FKkeka4bm5nw3misd4jvvvsqcd8` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_inventories`
--

DROP TABLE IF EXISTS `user_inventories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_inventories` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `acquired_at` datetime(6) NOT NULL,
  `quantity` int(11) NOT NULL,
  `item_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_inventory_item` (`user_id`,`item_id`),
  KEY `FK8jgm63ubnmqnou0sbya5lk5p0` (`item_id`),
  CONSTRAINT `FK8jgm63ubnmqnou0sbya5lk5p0` FOREIGN KEY (`item_id`) REFERENCES `shop_items` (`id`),
  CONSTRAINT `FKaeddc2bil18e4iwv4w9m26bl7` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_items`
--

DROP TABLE IF EXISTS `user_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `acquired_at` datetime(6) NOT NULL,
  `equipped` bit(1) NOT NULL,
  `equipped_slot` varchar(30) GENERATED ALWAYS AS (if(`equipped` = 1,`item_type`,NULL)) STORED,
  `item_type` enum('AVATAR_FRAME','THEME','XP_BOOSTER') NOT NULL,
  `quantity` int(11) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `shop_item_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_items_owner` (`user_id`,`shop_item_id`),
  UNIQUE KEY `uq_user_items_equipped` (`user_id`,`equipped_slot`),
  KEY `idx_user_items_user` (`user_id`),
  KEY `FKlmfiw28j27j7p93edb61upsr5` (`shop_item_id`),
  CONSTRAINT `FK55mpmb46vtbmw6xljldr2wvvf` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKlmfiw28j27j7p93edb61upsr5` FOREIGN KEY (`shop_item_id`) REFERENCES `shop_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_notifications`
--

DROP TABLE IF EXISTS `user_notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_notifications` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `is_read` bit(1) NOT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `notification_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_notification` (`user_id`,`notification_id`),
  KEY `FKovvx0ab3h8s9lrm6fppuadn7d` (`notification_id`),
  CONSTRAINT `FK9f86wonnl11hos1cuf5fibutl` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKovvx0ab3h8s9lrm6fppuadn7d` FOREIGN KEY (`notification_id`) REFERENCES `notifications` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_settings`
--

DROP TABLE IF EXISTS `user_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_settings` (
  `user_id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `daily_goal` int(11) NOT NULL,
  `push_enabled` bit(1) NOT NULL,
  `srs_reminder_at` time(6) DEFAULT NULL,
  `theme` enum('DARK','LIGHT','SYSTEM') NOT NULL,
  `ui_language` varchar(10) NOT NULL,
  `study_session_card_limit` int(11) NOT NULL,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `FK8v82nj88rmai0nyck19f873dw` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_word_credits`
--

DROP TABLE IF EXISTS `user_word_credits`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_word_credits` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `word_hash` binary(32) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `user_daily_mission_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_word_credits_word` (`user_id`,`word_hash`),
  KEY `idx_user_word_credits_mission` (`user_daily_mission_id`),
  CONSTRAINT `FK5u2bfylyf57x0mv1fj54k8vb1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `activated` bit(1) NOT NULL,
  `avatar_url` mediumtext DEFAULT NULL,
  `bio` varchar(200) DEFAULT NULL,
  `coin` bigint(20) DEFAULT NULL CHECK (`coin` >= 0),
  `email` varchar(254) DEFAULT NULL,
  `exp` bigint(20) DEFAULT NULL CHECK (`exp` >= 0),
  `first_name` varchar(50) NOT NULL,
  `last_name` varchar(50) DEFAULT NULL,
  `last_studied_at` datetime(6) DEFAULT NULL,
  `learning_language` varchar(10) DEFAULT NULL,
  `native_language` varchar(10) DEFAULT NULL,
  `password_hash` varchar(60) NOT NULL,
  `streak_days` int(11) DEFAULT NULL CHECK (`streak_days` >= 0),
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),
  `longest_streak_days` int(11) NOT NULL DEFAULT 0 CHECK (`longest_streak_days` >= 0),
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKob8kqyqqgmefl0aco34akdtpe` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `word_definitions`
--

DROP TABLE IF EXISTS `word_definitions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `word_definitions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `example` text DEFAULT NULL,
  `definition_id` bigint(20) NOT NULL,
  `word_id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  PRIMARY KEY (`id`),
  KEY `idx_word_definition_word_id` (`word_id`),
  KEY `idx_word_definition_definition_id` (`definition_id`),
  KEY `idx_word_definitions_word_id` (`word_id`),
  KEY `idx_word_definitions_definition_id` (`definition_id`),
  CONSTRAINT `FKbywudcff9m1lmo0p65crablca` FOREIGN KEY (`word_id`) REFERENCES `words` (`id`),
  CONSTRAINT `FKor0b5rp5lvwi94ryiq6tfa7pp` FOREIGN KEY (`definition_id`) REFERENCES `definitions` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=87630251 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `word_relations`
--

DROP TABLE IF EXISTS `word_relations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `word_relations` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `related_word` varchar(255) DEFAULT NULL,
  `relation_type` varchar(50) DEFAULT NULL,
  `word_id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_word_relation_unique` (`word_id`,`related_word`,`relation_type`),
  UNIQUE KEY `idx_word_relations_unique` (`word_id`,`related_word`,`relation_type`),
  KEY `idx_word_relation_word_id` (`word_id`),
  KEY `idx_word_relations_word_id` (`word_id`),
  CONSTRAINT `FK2dhnq0scn74wm85omk5j4by4v` FOREIGN KEY (`word_id`) REFERENCES `words` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=119501851 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `words`
--

DROP TABLE IF EXISTS `words`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `words` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `lang_code` varchar(20) NOT NULL,
  `word` varchar(191) NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  PRIMARY KEY (`id`),
  KEY `idx_word_en` (`word`,`lang_code`),
  KEY `idx_words_en` (`word`,`lang_code`)
) ENGINE=InnoDB AUTO_INCREMENT=33172301 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-22 20:12:54
