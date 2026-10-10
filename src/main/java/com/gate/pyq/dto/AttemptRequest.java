package com.gate.pyq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttemptRequest {

    @NotNull(message = "questionId is required")
    private Long questionId;

    @NotBlank(message = "selectedOption is required")
    private String selectedOption;

    private Boolean isCorrect;

    private String username;
}
