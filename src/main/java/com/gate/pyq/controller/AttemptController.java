package com.gate.pyq.controller;

import com.gate.pyq.dto.AttemptRequest;
import com.gate.pyq.dto.AttemptResponse;
import com.gate.pyq.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/attempts")
@RequiredArgsConstructor
public class AttemptController {

    private final QuizService quizService;

    @PostMapping
    public ResponseEntity<AttemptResponse> submitAttempt(@Valid @RequestBody AttemptRequest request) {
        AttemptResponse response = quizService.recordAttempt(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/user-question-ids")
    public ResponseEntity<List<Long>> getAttemptedQuestionIds(@RequestParam String username) {
        List<Long> ids = quizService.getAttemptedQuestionIds(username);
        return ResponseEntity.ok(ids);
    }
}
