-- Theme and interface language are local to each app installation (MMKV).
-- Run once on existing databases, with the old backend stopped, before starting
-- the new backend. Hibernate ddl-auto=update does not drop obsolete columns.
-- Existing account-level values are intentionally discarded; no learning data changes.
-- Conditional DDL supports both MySQL and MariaDB and can be run again safely.

SET @drop_ui_language = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'user_settings'
              AND column_name = 'ui_language'),
    'ALTER TABLE `user_settings` DROP COLUMN `ui_language`',
    'SELECT 1'
);
PREPARE drop_device_preference FROM @drop_ui_language;
EXECUTE drop_device_preference;
DEALLOCATE PREPARE drop_device_preference;

SET @drop_theme = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'user_settings'
              AND column_name = 'theme'),
    'ALTER TABLE `user_settings` DROP COLUMN `theme`',
    'SELECT 1'
);
PREPARE drop_device_preference FROM @drop_theme;
EXECUTE drop_device_preference;
DEALLOCATE PREPARE drop_device_preference;
