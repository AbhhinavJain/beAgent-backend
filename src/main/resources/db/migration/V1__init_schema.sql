-- GATE PYQ Quiz & Progress Tracker Schema (PostgreSQL / Supabase / H2)

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS questions (
    id BIGSERIAL PRIMARY KEY,
    subject VARCHAR(255) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    gate_year INT,
    question_text TEXT NOT NULL,
    options JSONB NOT NULL,
    correct_answer VARCHAR(255) NOT NULL,
    explanation TEXT,
    image_url TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_questions_subject_topic ON questions(subject, topic);

CREATE TABLE IF NOT EXISTS user_attempts (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) DEFAULT 'demo',
    question_id BIGINT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    selected_option VARCHAR(255) NOT NULL,
    is_correct BOOLEAN NOT NULL,
    attempted_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_attempts_question_id ON user_attempts(question_id);
CREATE INDEX IF NOT EXISTS idx_user_attempts_username ON user_attempts(username);
