package com.gate.pyq.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gate.pyq.config.CacheConfig;
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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizService {

    private static final String ALL_CATALOG_KEY = "ALL_QUESTIONS";

    private final QuestionRepository questionRepository;
    private final UserAttemptRepository userAttemptRepository;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    @Value("${app.backup.file-path:../backups/questions_emergency_backup.json}")
    private String backupFilePath;

    private Cache getCache(String name) {
        return cacheManager != null ? cacheManager.getCache(name) : null;
    }

    /**
     * Returns the full sorted catalog of questions from cache, querying the DB only on cache miss.
     */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<QuestionDto> getAllCachedQuestions() {
        Cache cache = getCache(CacheConfig.QUESTIONS_CATALOG_CACHE);
        if (cache != null) {
            List<QuestionDto> cached = cache.get(ALL_CATALOG_KEY, List.class);
            if (cached != null) {
                return cached;
            }
        }

        List<QuestionDto> loaded = questionRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::toDto)
                .toList();

        if (cache != null && !loaded.isEmpty()) {
            cache.put(ALL_CATALOG_KEY, loaded);
        }
        return loaded;
    }

    @Transactional(readOnly = true)
    public List<QuestionDto> getQuestions(String subject, String topic, Integer limit) {
        return getQuestions(subject, topic, limit, null, false, false, null, null);
    }

    @Transactional(readOnly = true)
    public List<QuestionDto> getQuestions(
            String subject,
            String topic,
            Integer limit,
            String username,
            Boolean unattemptedOnly,
            Boolean attemptedOnly
    ) {
        return getQuestions(subject, topic, limit, username, unattemptedOnly, attemptedOnly, null, null);
    }

    @Transactional(readOnly = true)
    public List<QuestionDto> getQuestions(
            String subject,
            String topic,
            Integer limit,
            String username,
            Boolean unattemptedOnly,
            Boolean attemptedOnly,
            String search
    ) {
        return getQuestions(subject, topic, limit, username, unattemptedOnly, attemptedOnly, search, null);
    }

    @Transactional(readOnly = true)
    public List<QuestionDto> getQuestions(
            String subject,
            String topic,
            Integer limit,
            String username,
            Boolean unattemptedOnly,
            Boolean attemptedOnly,
            String search,
            String questionType
    ) {
        int pageSize = (limit != null && limit > 0) ? Math.min(limit, 2500) : 50;

        boolean hasSubject = subject != null && !subject.isBlank() && !"all".equalsIgnoreCase(subject.trim());
        boolean hasTopic = topic != null && !topic.isBlank() && !"all".equalsIgnoreCase(topic.trim());
        boolean hasUser = username != null && !username.isBlank();
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasType = questionType != null && !questionType.isBlank() && !"all".equalsIgnoreCase(questionType.trim());

        String cleanSubject = hasSubject ? subject.trim() : "";
        String cleanTopic = hasTopic ? topic.trim() : "";
        String cleanUser = hasUser ? username.trim() : "";
        String cleanSearch = hasSearch ? search.trim().toLowerCase(Locale.ROOT) : "";
        String cleanType = hasType ? questionType.trim() : "";

        // Read from in-memory cached catalog (0 DB queries on cache hit)
        List<QuestionDto> catalog = getAllCachedQuestions();

        Set<Long> attemptedIds = null;
        if (hasUser && (Boolean.TRUE.equals(unattemptedOnly) || Boolean.TRUE.equals(attemptedOnly))) {
            attemptedIds = new HashSet<>(getAttemptedQuestionIds(cleanUser));
        }

        final Set<Long> finalAttemptedIds = attemptedIds;
        return catalog.stream()
                .filter(q -> {
                    if (hasSubject && (q.getSubject() == null || !q.getSubject().equalsIgnoreCase(cleanSubject))) {
                        return false;
                    }
                    if (hasTopic && (q.getTopic() == null || !q.getTopic().equalsIgnoreCase(cleanTopic))) {
                        return false;
                    }
                    if (finalAttemptedIds != null) {
                        if (Boolean.TRUE.equals(unattemptedOnly) && finalAttemptedIds.contains(q.getId())) {
                            return false;
                        }
                        if (Boolean.TRUE.equals(attemptedOnly) && !finalAttemptedIds.contains(q.getId())) {
                            return false;
                        }
                    }
                    if (hasType) {
                        String qType = q.getQuestionType() != null ? q.getQuestionType() : "MCQ";
                        if (!qType.equalsIgnoreCase(cleanType)) {
                            return false;
                        }
                    }
                    if (hasSearch) {
                        String qText = q.getQuestionText() != null ? q.getQuestionText().toLowerCase(Locale.ROOT) : "";
                        String qTopic = q.getTopic() != null ? q.getTopic().toLowerCase(Locale.ROOT) : "";
                        String qSubj = q.getSubject() != null ? q.getSubject().toLowerCase(Locale.ROOT) : "";
                        String qYear = q.getGateYear() != null ? String.valueOf(q.getGateYear()) : "";
                        boolean matchesOption = q.getOptions() != null && q.getOptions().stream()
                                .anyMatch(opt -> opt != null && opt.toLowerCase(Locale.ROOT).contains(cleanSearch));
                        if (!qText.contains(cleanSearch) && !qTopic.contains(cleanSearch) && !qSubj.contains(cleanSearch) && !qYear.contains(cleanSearch) && !matchesOption) {
                            return false;
                        }
                    }
                    return true;
                })
                .limit(pageSize)
                .toList();
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<Long> getAttemptedQuestionIds(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }
        String cacheKey = username.trim().toLowerCase(Locale.ROOT);
        Cache cache = getCache(CacheConfig.USER_ATTEMPTED_IDS_CACHE);
        if (cache != null) {
            List<Long> cached = cache.get(cacheKey, List.class);
            if (cached != null) {
                return cached;
            }
        }

        List<Long> ids = userAttemptRepository.findAttemptedQuestionIdsByUsername(username.trim());
        if (cache != null) {
            cache.put(cacheKey, ids);
        }
        return ids;
    }

    @Transactional
    public List<QuestionDto> saveBulkQuestions(List<QuestionDto> dtos) {
        Set<String> seenTexts = getAllCachedQuestions().stream()
                .map(QuestionDto::getQuestionText)
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));

        List<Question> newEntities = dtos.stream()
                .filter(dto -> dto.getQuestionText() != null && !dto.getQuestionText().isBlank())
                // Deduplicate against both existing DB records and current batch in O(1) memory
                .filter(dto -> seenTexts.add(dto.getQuestionText().trim().toLowerCase(Locale.ROOT)))
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

        List<QuestionDto> savedDtos = questionRepository.saveAll(newEntities).stream()
                .map(this::toDto)
                .toList();

        evictQuestionAndAnalyticsCaches();
        writeEmergencyLocalBackup();
        return savedDtos;
    }

    public void evictQuestionAndAnalyticsCaches() {
        Cache catalogCache = getCache(CacheConfig.QUESTIONS_CATALOG_CACHE);
        if (catalogCache != null) {
            catalogCache.clear();
        }
        Cache perfCache = getCache(CacheConfig.TOPIC_PERFORMANCE_CACHE);
        if (perfCache != null) {
            perfCache.clear();
        }
        Cache statusCache = getCache(CacheConfig.DB_STATUS_CACHE);
        if (statusCache != null) {
            statusCache.clear();
        }
    }

    private void evictUserCaches(String username) {
        String userKey = username.trim().toLowerCase(Locale.ROOT);
        Cache attemptedCache = getCache(CacheConfig.USER_ATTEMPTED_IDS_CACHE);
        if (attemptedCache != null) {
            attemptedCache.evict(userKey);
        }
        Cache perfCache = getCache(CacheConfig.TOPIC_PERFORMANCE_CACHE);
        if (perfCache != null) {
            perfCache.evict(userKey);
            perfCache.evict("GLOBAL");
        }
        Cache statusCache = getCache(CacheConfig.DB_STATUS_CACHE);
        if (statusCache != null) {
            statusCache.clear();
        }
    }

    public void writeEmergencyLocalBackup() {
        try {
            File backupFile = new File(backupFilePath);
            if (backupFile.getParentFile() != null) {
                backupFile.getParentFile().mkdirs();
            }
            List<QuestionDto> allQuestions = getAllCachedQuestions();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(backupFile, allQuestions);
            log.info("Synced {} questions to local emergency backup at {}", allQuestions.size(), backupFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("Could not write local emergency backup: {}", e.getMessage());
        }
    }

    @Transactional
    public AttemptResponse recordAttempt(AttemptRequest request) {
        QuestionDto cachedQuestion = getAllCachedQuestions().stream()
                .filter(q -> q.getId() != null && q.getId().equals(request.getQuestionId()))
                .findFirst()
                .orElse(null);

        Question questionRef;
        String correctAnswer;
        String explanation;

        if (cachedQuestion != null) {
            questionRef = questionRepository.getReferenceById(cachedQuestion.getId());
            correctAnswer = cachedQuestion.getCorrectAnswer();
            explanation = cachedQuestion.getExplanation();
        } else {
            Question dbQuestion = questionRepository.findById(request.getQuestionId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Question not found with id: " + request.getQuestionId()));
            questionRef = dbQuestion;
            correctAnswer = dbQuestion.getCorrectAnswer();
            explanation = dbQuestion.getExplanation();
            cachedQuestion = toDto(dbQuestion);
        }

        String selected = request.getSelectedOption().trim();
        boolean isCorrect = request.getIsCorrect() != null
                ? request.getIsCorrect()
                : evaluateOptionDto(cachedQuestion, selected);

        String username = (request.getUsername() != null && !request.getUsername().isBlank())
                ? request.getUsername().trim()
                : "demo";

        UserAttempt attempt = UserAttempt.builder()
                .username(username)
                .question(questionRef)
                .selectedOption(selected)
                .isCorrect(isCorrect)
                .build();

        UserAttempt saved = userAttemptRepository.save(attempt);
        evictUserCaches(username);

        return AttemptResponse.builder()
                .attemptId(saved.getId())
                .questionId(cachedQuestion.getId())
                .selectedOption(saved.getSelectedOption())
                .isCorrect(saved.getIsCorrect())
                .correctAnswer(correctAnswer)
                .explanation(explanation)
                .attemptedAt(saved.getAttemptedAt())
                .build();
    }

    private boolean evaluateOptionDto(QuestionDto question, String selectedOption) {
        if (selectedOption == null || selectedOption.isBlank()) {
            return false;
        }
        String correct = question.getCorrectAnswer() != null ? question.getCorrectAnswer().trim() : "";
        String selected = selectedOption.trim();
        String qType = question.getQuestionType() != null ? question.getQuestionType().toUpperCase() : "MCQ";

        if (correct.equalsIgnoreCase(selected)) {
            return true;
        }

        if ("NAT".equals(qType)) {
            return evaluateNatAnswer(correct, selected);
        } else if ("MSQ".equals(qType)) {
            return evaluateMsqAnswer(correct, selected);
        }

        List<String> options = question.getOptions();
        if (options != null && !options.isEmpty()) {
            if (correct.length() == 1 && Character.isLetter(correct.charAt(0))) {
                int idx = Character.toUpperCase(correct.charAt(0)) - 'A';
                if (idx >= 0 && idx < options.size() && options.get(idx).trim().equalsIgnoreCase(selected)) {
                    return true;
                }
            }
            if (selected.length() == 1 && Character.isLetter(selected.charAt(0))) {
                int idx = Character.toUpperCase(selected.charAt(0)) - 'A';
                if (idx >= 0 && idx < options.size() && options.get(idx).trim().equalsIgnoreCase(correct)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean evaluateNatAnswer(String correct, String selected) {
        try {
            double selectedVal = Double.parseDouble(selected);
            if (correct.contains("to") || correct.contains("-")) {
                String[] parts = correct.split("to|-");
                if (parts.length == 2) {
                    double min = Double.parseDouble(parts[0].trim());
                    double max = Double.parseDouble(parts[1].trim());
                    return selectedVal >= (min - 1e-4) && selectedVal <= (max + 1e-4);
                }
            }
            double correctVal = Double.parseDouble(correct);
            return Math.abs(selectedVal - correctVal) < 1e-3;
        } catch (Exception e) {
            return correct.equalsIgnoreCase(selected);
        }
    }

    private boolean evaluateMsqAnswer(String correct, String selected) {
        java.util.Set<String> correctSet = parseOptionLetters(correct);
        java.util.Set<String> selectedSet = parseOptionLetters(selected);
        return !correctSet.isEmpty() && correctSet.equals(selectedSet);
    }

    private java.util.Set<String> parseOptionLetters(String text) {
        java.util.Set<String> set = new java.util.TreeSet<>();
        if (text == null) return set;
        String[] tokens = text.split("[,\\s]+");
        for (String t : tokens) {
            String clean = t.trim().toUpperCase();
            if (!clean.isEmpty()) {
                set.add(clean);
            }
        }
        return set;
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
                .questionType(q.getQuestionType() != null ? q.getQuestionType() : "MCQ")
                .hasImage(q.getHasImage() != null ? q.getHasImage() : false)
                .createdAt(q.getCreatedAt())
                .build();
    }
}
