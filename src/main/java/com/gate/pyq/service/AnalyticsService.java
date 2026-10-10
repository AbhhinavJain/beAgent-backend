package com.gate.pyq.service;

import com.gate.pyq.config.CacheConfig;
import com.gate.pyq.dto.TopicPerformanceDto;
import com.gate.pyq.repository.UserAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final UserAttemptRepository userAttemptRepository;

    @Cacheable(
            value = CacheConfig.TOPIC_PERFORMANCE_CACHE,
            key = "#username != null && !#username.isBlank() ? #username.trim().toLowerCase() : 'GLOBAL'"
    )
    @Transactional(readOnly = true)
    public List<TopicPerformanceDto> getTopicPerformance(String username) {
        List<UserAttemptRepository.TopicAggregationProjection> rows;
        if (username != null && !username.isBlank()) {
            rows = userAttemptRepository.aggregateTopicPerformanceByUsername(username.trim());
        } else {
            rows = userAttemptRepository.aggregateTopicPerformance();
        }

        return rows.stream()
                .map(row -> {
                    long totalAttempts = row.getTotalAttempts() != null ? row.getTotalAttempts() : 0L;
                    long correctAttempts = row.getCorrectAttempts() != null ? row.getCorrectAttempts() : 0L;
                    long totalQuestions = row.getTotalQuestions() != null ? row.getTotalQuestions() : 0L;
                    long attemptedQuestions = row.getAttemptedQuestions() != null ? row.getAttemptedQuestions() : 0L;

                    double accuracyPercentage = totalAttempts > 0
                            ? Math.round((correctAttempts * 10000.0) / totalAttempts) / 100.0
                            : 0.0;

                    boolean needsFocus = totalAttempts >= 3 && accuracyPercentage < 60.0;

                    return TopicPerformanceDto.builder()
                            .subject(row.getSubject())
                            .topic(row.getTopic())
                            .totalAttempts(totalAttempts)
                            .correctAttempts(correctAttempts)
                            .accuracyPercentage(accuracyPercentage)
                            .needsFocus(needsFocus)
                            .totalQuestions(totalQuestions)
                            .attemptedQuestions(attemptedQuestions)
                            .build();
                })
                .toList();
    }
}
