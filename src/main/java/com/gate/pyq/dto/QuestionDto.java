package com.gate.pyq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionDto {
    private Long id;
    private String subject;
    private String topic;
    private Integer gateYear;
    private String questionText;
    private List<String> options;
    private String correctAnswer;
    private String explanation;
    private String imageUrl;
    private String questionType;
    private Boolean hasImage;
    private LocalDateTime createdAt;
}
