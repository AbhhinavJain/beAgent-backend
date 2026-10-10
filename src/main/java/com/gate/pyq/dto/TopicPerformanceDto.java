package com.gate.pyq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicPerformanceDto {
    private String subject;
    private String topic;
    private long totalAttempts;
    private long correctAttempts;
    private double accuracyPercentage;
    private boolean needsFocus;
    private long totalQuestions;
    private long attemptedQuestions;
}
