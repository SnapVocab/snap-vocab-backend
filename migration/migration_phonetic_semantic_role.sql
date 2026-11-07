-- Run before deploying PHONETIC on environments using ddl-auto: validate.
-- VARCHAR columns already accept the new role. Extend native ENUM columns only
-- when needed, retaining every existing enum value.
SET @extend_phonetic_role = (
    SELECT IF(DATA_TYPE = 'enum' AND LOCATE('''PHONETIC''', COLUMN_TYPE) = 0,
        CONCAT('ALTER TABLE `template_fields` MODIFY COLUMN `semantic_role` ',
            LEFT(COLUMN_TYPE, CHAR_LENGTH(COLUMN_TYPE) - 1), ',''PHONETIC'') NULL'),
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'template_fields' AND COLUMN_NAME = 'semantic_role'
);
PREPARE phonetic_role_stmt FROM @extend_phonetic_role;
EXECUTE phonetic_role_stmt;
DEALLOCATE PREPARE phonetic_role_stmt;

-- Existing DEFAULT_ENGLISH visible phonetic fields are bound in place by
-- SemanticBindingMigration on startup. Derived user schemas remain report-only
-- unless snapvocab.semantic-binding.fork-mode=APPLY is explicitly configured.
