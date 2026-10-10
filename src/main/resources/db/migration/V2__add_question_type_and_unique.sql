-- Add question_type column and has_image flag, plus unique constraint on question_text

-- 1. Add question_type column (MCQ, MSQ, NAT) defaulting to MCQ
ALTER TABLE questions ADD COLUMN IF NOT EXISTS question_type VARCHAR(10) NOT NULL DEFAULT 'MCQ';

-- 2. Add has_image flag to mark questions that reference visual content (tables, figures, diagrams)
ALTER TABLE questions ADD COLUMN IF NOT EXISTS has_image BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Add unique constraint on question_text to prevent duplicates at DB level
--    Using a unique index on the trimmed lowercase text for case-insensitive matching.
--    MD5 hash is used because question_text is TEXT and direct indexing on large TEXT is inefficient.
CREATE UNIQUE INDEX IF NOT EXISTS idx_questions_unique_text
    ON questions (MD5(LOWER(TRIM(question_text))));
