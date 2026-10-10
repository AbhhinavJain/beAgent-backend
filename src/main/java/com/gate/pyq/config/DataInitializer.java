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

            // If database is empty on a fresh cloud deployment (e.g. Render), restore extracted PDF backup into DB
            if (questionRepository.count() == 0) {
                ClassPathResource backupResource = new ClassPathResource("questions_emergency_backup.json");
                if (backupResource.exists()) {
                    try (InputStream is = backupResource.getInputStream()) {
                        List<QuestionDto> backupDtos = objectMapper.readValue(is, new TypeReference<>() {});
                        List<Question> entities = backupDtos.stream()
                                .filter(dto -> dto.getQuestionText() != null && !dto.getQuestionText().isBlank())
                                .map(dto -> Question.builder()
                                        .subject(dto.getSubject())
                                        .topic(dto.getTopic())
                                        .gateYear(dto.getGateYear())
                                        .questionText(dto.getQuestionText().trim())
                                        .options(dto.getOptions())
                                        .correctAnswer(dto.getCorrectAnswer())
                                        .explanation(dto.getExplanation())
                                        .imageUrl(dto.getImageUrl())
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
