CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(100) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS user_roles (
    username VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (username, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_profiles (
    username VARCHAR(100) PRIMARY KEY,
    full_name VARCHAR(255),
    student_no VARCHAR(100),
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS question_banks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    owner_username VARCHAR(100) NOT NULL,
    visibility VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_question_banks_owner FOREIGN KEY (owner_username) REFERENCES users(username)
);

CREATE TABLE IF NOT EXISTS question_bank_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bank_id BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL,
    joined_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_question_bank_member UNIQUE (bank_id, username),
    CONSTRAINT fk_question_bank_members_bank FOREIGN KEY (bank_id) REFERENCES question_banks(id) ON DELETE CASCADE,
    CONSTRAINT fk_question_bank_members_user FOREIGN KEY (username) REFERENCES users(username)
);

CREATE TABLE IF NOT EXISTS questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bank_id BIGINT,
    type VARCHAR(50) NOT NULL,
    stem TEXT NOT NULL,
    options_json TEXT,
    tags_json TEXT,
    correct_answer VARCHAR(255) NOT NULL,
    analysis TEXT,
    score INT NOT NULL,
    difficulty VARCHAR(50),
    knowledge_point VARCHAR(255),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_questions_bank FOREIGN KEY (bank_id) REFERENCES question_banks(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS papers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS paper_items (
    paper_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    order_index INT NOT NULL,
    PRIMARY KEY (paper_id, order_index),
    CONSTRAINT fk_paper_items_paper FOREIGN KEY (paper_id) REFERENCES papers(id) ON DELETE CASCADE,
    CONSTRAINT fk_paper_items_question FOREIGN KEY (question_id) REFERENCES questions(id)
);

CREATE TABLE IF NOT EXISTS classes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    invite_code VARCHAR(50) NOT NULL UNIQUE,
    owner_username VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_classes_owner FOREIGN KEY (owner_username) REFERENCES users(username)
);

CREATE TABLE IF NOT EXISTS class_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL,
    joined_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_class_member UNIQUE (class_id, username),
    CONSTRAINT fk_class_members_class FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE,
    CONSTRAINT fk_class_members_user FOREIGN KEY (username) REFERENCES users(username)
);

CREATE TABLE IF NOT EXISTS exams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    paper_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PUBLISHED',
    settings_json TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_exams_paper FOREIGN KEY (paper_id) REFERENCES papers(id)
);

CREATE TABLE IF NOT EXISTS exam_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    paper_id BIGINT NOT NULL,
    student_username VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    submitted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_attempts_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
    CONSTRAINT fk_attempts_paper FOREIGN KEY (paper_id) REFERENCES papers(id)
);

CREATE TABLE IF NOT EXISTS exam_attempt_questions (
    attempt_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    question_type VARCHAR(50) NOT NULL,
    stem TEXT NOT NULL,
    options_json TEXT,
    score INT NOT NULL,
    order_index INT NOT NULL,
    PRIMARY KEY (attempt_id, order_index),
    CONSTRAINT fk_attempt_questions_attempt FOREIGN KEY (attempt_id) REFERENCES exam_attempts(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS exam_attempt_answers (
    attempt_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer TEXT,
    PRIMARY KEY (attempt_id, question_id),
    CONSTRAINT fk_attempt_answers_attempt FOREIGN KEY (attempt_id) REFERENCES exam_attempts(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS exam_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    attempt_id BIGINT NOT NULL,
    student_username VARCHAR(100) NOT NULL,
    total_score INT NOT NULL,
    max_score INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_exam_attempt UNIQUE (exam_id, attempt_id),
    CONSTRAINT fk_results_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
    CONSTRAINT fk_results_attempt FOREIGN KEY (attempt_id) REFERENCES exam_attempts(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS exam_result_items (
    result_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    question_type VARCHAR(50) NOT NULL,
    answer TEXT,
    correct_answer TEXT,
    max_score INT NOT NULL,
    earned_score INT NOT NULL,
    correct BOOLEAN NOT NULL,
    PRIMARY KEY (result_id, question_id),
    CONSTRAINT fk_result_items_result FOREIGN KEY (result_id) REFERENCES exam_results(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS proctor_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    attempt_id BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL,
    type VARCHAR(100) NOT NULL,
    payload_json TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_proctor_events_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
    CONSTRAINT fk_proctor_events_attempt FOREIGN KEY (attempt_id) REFERENCES exam_attempts(id) ON DELETE CASCADE,
    CONSTRAINT fk_proctor_events_user FOREIGN KEY (username) REFERENCES users(username)
);

CREATE TABLE IF NOT EXISTS attempt_heartbeats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    attempt_id BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL,
    ts TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_attempt_heartbeats_attempt FOREIGN KEY (attempt_id) REFERENCES exam_attempts(id) ON DELETE CASCADE,
    CONSTRAINT fk_attempt_heartbeats_user FOREIGN KEY (username) REFERENCES users(username)
);
