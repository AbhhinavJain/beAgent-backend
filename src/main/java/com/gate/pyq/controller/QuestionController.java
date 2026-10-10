package com.gate.pyq.controller;

import com.gate.pyq.dto.QuestionDto;
import com.gate.pyq.service.QuizService;
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
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuizService quizService;

    @GetMapping
    public ResponseEntity<List<QuestionDto>> getQuestions(
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @RequestParam(required = false) String username,
            @RequestParam(required = false, defaultValue = "false") Boolean unattemptedOnly,
            @RequestParam(required = false, defaultValue = "false") Boolean attemptedOnly,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String questionType
    ) {
        List<QuestionDto> questions = quizService.getQuestions(subject, topic, limit, username, unattemptedOnly, attemptedOnly, search, questionType);
        return ResponseEntity.ok(questions);
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<QuestionDto>> createBulkQuestions(@RequestBody List<QuestionDto> questions) {
        List<QuestionDto> saved = quizService.saveBulkQuestions(questions);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
