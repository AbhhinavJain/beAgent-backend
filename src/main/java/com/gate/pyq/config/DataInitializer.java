package com.gate.pyq.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gate.pyq.dto.QuestionDto;
import com.gate.pyq.entity.Question;
import com.gate.pyq.repository.QuestionRepository;
import com.gate.pyq.repository.UserAttemptRepository;
import com.gate.pyq.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final QuestionRepository questionRepository;
    private final UserAttemptRepository userAttemptRepository;
    private final UserRepository userRepository;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            String product = conn.getMetaData().getDatabaseProductName();

            try {
                jdbcTemplate.execute("ALTER TABLE questions ALTER COLUMN correct_answer SET DATA TYPE TEXT");
                jdbcTemplate.execute("ALTER TABLE user_attempts ALTER COLUMN selected_option SET DATA TYPE TEXT");
            } catch (Exception ignored) {
                // Ignored if dialect uses a different syntax or already TEXT
            }

            try {
                jdbcTemplate.execute("ALTER TABLE questions ADD COLUMN IF NOT EXISTS question_type VARCHAR(10) DEFAULT 'MCQ'");
            } catch (Exception ignored) {
            }
            try {
                jdbcTemplate.execute("ALTER TABLE questions ADD COLUMN IF NOT EXISTS has_image BOOLEAN DEFAULT FALSE");
            } catch (Exception ignored) {
            }
            try {
                jdbcTemplate.execute("UPDATE questions SET question_type = 'MCQ' WHERE question_type IS NULL");
                jdbcTemplate.execute("UPDATE questions SET has_image = FALSE WHERE has_image IS NULL");
            } catch (Exception ignored) {
            }

            // If database is empty on a fresh cloud deployment (e.g. Render), restore extracted PDF backup into DB
            if (questionRepository.count() == 0) {
                ClassPathResource backupResource = new ClassPathResource("questions_emergency_backup.json");
                if (backupResource.exists()) {
                    try (InputStream is = backupResource.getInputStream()) {
                        List<QuestionDto> backupDtos = objectMapper.readValue(is, new TypeReference<>() {});
                        java.util.Set<String> seenTexts = new java.util.HashSet<>();
                        List<Question> entities = backupDtos.stream()
                                .filter(dto -> dto.getQuestionText() != null && !dto.getQuestionText().isBlank())
                                .filter(dto -> seenTexts.add(dto.getQuestionText().trim().toLowerCase()))
                                .map(dto -> Question.builder()
                                        .subject(dto.getSubject())
                                        .topic(dto.getTopic())
                                        .gateYear(dto.getGateYear())
                                        .questionText(dto.getQuestionText().trim())
                                        .options(dto.getOptions())
                                        .correctAnswer(dto.getCorrectAnswer())
                                        .explanation(dto.getExplanation())
                                        .imageUrl(dto.getImageUrl())
                                        .questionType(dto.getQuestionType() != null ? dto.getQuestionType() : "MCQ")
                                        .hasImage(dto.getHasImage() != null ? dto.getHasImage() : false)
                                        .build())
                                .toList();
                        questionRepository.saveAll(entities);
                        log.info("Restored {} extracted PDF questions from emergency backup into DB.", entities.size());
                    } catch (Exception ex) {
                        log.warn("Could not restore emergency PDF backup on startup: {}", ex.getMessage());
                    }
                }
            }

            log.info("==================================================================");
            log.info("DATABASE CONNECTION VERIFIED: {} ({})", product, url);
            log.info("Questions in DB: {} | Attempts in DB: {} | Registered Users in DB: {}",
                    questionRepository.count(), userAttemptRepository.count(), userRepository.count());
            log.info("==================================================================");
        } catch (Exception e) {
            log.error("Database connection check failed: {}", e.getMessage());
        }
    }
}
