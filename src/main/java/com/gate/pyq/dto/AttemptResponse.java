package com.gate.pyq.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttemptResponse {
    private Long attemptId;
    private Long questionId;
    private String selectedOption;

    @JsonProperty("isCorrect")
    private boolean isCorrect;

    private String correctAnswer;
    private String explanation;
    private LocalDateTime attemptedAt;
}
