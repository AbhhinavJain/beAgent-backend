package com.gate.pyq.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gate.pyq.dto.AttemptRequest;
import com.gate.pyq.dto.AttemptResponse;
import com.gate.pyq.dto.QuestionDto;
import com.gate.pyq.entity.Question;
import com.gate.pyq.entity.UserAttempt;
import com.gate.pyq.repository.QuestionRepository;
import com.gate.pyq.repository.UserAttemptRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizService {

    private final QuestionRepository questionRepository;
    private final UserAttemptRepository userAttemptRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.backup.file-path:../backups/questions_emergency_backup.json}")
    private String backupFilePath;

    @Transactional(readOnly = true)
    public List<QuestionDto> getQuestions(String subject, String topic, Integer limit) {
        int pageSize = (limit != null && limit > 0) ? Math.min(limit, 2000) : 50;
        Pageable pageable = PageRequest.of(0, pageSize, Sort.by(Sort.Direction.ASC, "id"));

        boolean hasSubject = subject != null && !subject.isBlank() && !"all".equalsIgnoreCase(subject.trim());
        boolean hasTopic = topic != null && !topic.isBlank() && !"all".equalsIgnoreCase(topic.trim());

        List<Question> questions;
        if (hasSubject && hasTopic) {
            questions = questionRepository.findBySubjectIgnoreCaseAndTopicIgnoreCase(
                    subject.trim(), topic.trim(), pageable);
        } else if (hasSubject) {
            questions = questionRepository.findBySubjectIgnoreCase(subject.trim(), pageable);
        } else if (hasTopic) {
            questions = questionRepository.findByTopicIgnoreCase(topic.trim(), pageable);
        } else {
            questions = questionRepository.findAll(pageable).getContent();
        }

        return questions.stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public List<QuestionDto> saveBulkQuestions(List<QuestionDto> dtos) {
        List<Question> newEntities = dtos.stream()
                .filter(dto -> dto.getQuestionText() != null && !dto.getQuestionText().isBlank())
                .filter(dto -> !questionRepository.existsByQuestionTextIgnoreCase(dto.getQuestionText().trim()))
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

        List<QuestionDto> savedDtos = questionRepository.saveAll(newEntities).stream()
                .map(this::toDto)
                .toList();

        writeEmergencyLocalBackup();
        return savedDtos;
    }

    public void writeEmergencyLocalBackup() {
        try {
            File backupFile = new File(backupFilePath);
            if (backupFile.getParentFile() != null) {
                backupFile.getParentFile().mkdirs();
            }
            List<QuestionDto> allQuestions = questionRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                    .stream()
                    .map(this::toDto)
                    .toList();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(backupFile, allQuestions);
            log.info("Synced {} questions to local emergency backup at {}", allQuestions.size(), backupFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("Could not write local emergency backup: {}", e.getMessage());
        }
    }

    @Transactional
    public AttemptResponse recordAttempt(AttemptRequest request) {
        Question question = questionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Question not found with id: " + request.getQuestionId()));

        String selected = request.getSelectedOption().trim();
        boolean isCorrect = request.getIsCorrect() != null
                ? request.getIsCorrect()
                : evaluateOption(question, selected);

        String username = (request.getUsername() != null && !request.getUsername().isBlank())
                ? request.getUsername().trim()
                : "demo";

        UserAttempt attempt = UserAttempt.builder()
                .username(username)
                .question(question)
                .selectedOption(selected)
                .isCorrect(isCorrect)
                .build();

        UserAttempt saved = userAttemptRepository.save(attempt);

        return AttemptResponse.builder()
                .attemptId(saved.getId())
                .questionId(question.getId())
                .selectedOption(saved.getSelectedOption())
                .isCorrect(saved.getIsCorrect())
                .correctAnswer(question.getCorrectAnswer())
                .explanation(question.getExplanation())
                .attemptedAt(saved.getAttemptedAt())
                .build();
    }

    private boolean evaluateOption(Question question, String selectedOption) {
        String correct = question.getCorrectAnswer() != null ? question.getCorrectAnswer().trim() : "";
        if (correct.equalsIgnoreCase(selectedOption)) {
            return true;
        }

        List<String> options = question.getOptions();
        if (options != null && !options.isEmpty()) {
            if (correct.length() == 1 && Character.isLetter(correct.charAt(0))) {
                int idx = Character.toUpperCase(correct.charAt(0)) - 'A';
                if (idx >= 0 && idx < options.size() && options.get(idx).trim().equalsIgnoreCase(selectedOption)) {
                    return true;
                }
            }
            if (selectedOption.length() == 1 && Character.isLetter(selectedOption.charAt(0))) {
                int idx = Character.toUpperCase(selectedOption.charAt(0)) - 'A';
                if (idx >= 0 && idx < options.size() && options.get(idx).trim().equalsIgnoreCase(correct)) {
                    return true;
                }
            }
        }
        return false;
    }

    private QuestionDto toDto(Question q) {
        return QuestionDto.builder()
                .id(q.getId())
                .subject(q.getSubject())
                .topic(q.getTopic())
                .gateYear(q.getGateYear())
                .questionText(q.getQuestionText())
                .options(q.getOptions())
                .correctAnswer(q.getCorrectAnswer())
                .explanation(q.getExplanation())
                .imageUrl(q.getImageUrl())
                .createdAt(q.getCreatedAt())
                .build();
    }
}
