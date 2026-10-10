package com.gate.pyq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gate.pyq.dto.AttemptRequest;
import com.gate.pyq.entity.Question;
import com.gate.pyq.entity.UserAttempt;
import com.gate.pyq.repository.QuestionRepository;
import com.gate.pyq.repository.UserAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
class GatePyqApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private UserAttemptRepository userAttemptRepository;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    private Question savedQuestion;

    @BeforeEach
    void setUp() {
        userAttemptRepository.deleteAll();
        questionRepository.deleteAll();
        if (cacheManager != null) {
            cacheManager.getCacheNames().forEach(name -> {
                var cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            });
        }

        savedQuestion = questionRepository.save(Question.builder()
                .subject("Operating Systems")
                .topic("Banker's Algorithm")
                .gateYear(2023)
                .questionText("Which of the following represents a safe sequence?")
                .options(List.of("<P1, P2, P0>", "<P0, P1, P2>", "<P2, P1, P0>", "None"))
                .correctAnswer("<P1, P2, P0>")
                .explanation("Need <= Available check.")
                .build());

        userAttemptRepository.saveAll(List.of(
                UserAttempt.builder().username("testuser").question(savedQuestion).selectedOption("<P0, P1, P2>").isCorrect(false).build(),
                UserAttempt.builder().username("testuser").question(savedQuestion).selectedOption("<P2, P1, P0>").isCorrect(false).build(),
                UserAttempt.builder().username("testuser").question(savedQuestion).selectedOption("<P1, P2, P0>").isCorrect(true).build()
        ));
    }

    @Test
    void shouldFetchFilteredQuestions() throws Exception {
        mockMvc.perform(get("/api/v1/questions")
                        .param("subject", "Operating Systems")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].subject", is("Operating Systems")))
                .andExpect(jsonPath("$[0].options", hasSize(4)));
    }

    @Test
    void shouldFetchUnattemptedQuestions() throws Exception {
        mockMvc.perform(get("/api/v1/questions")
                        .param("limit", "10")
                        .param("username", "dev")
                        .param("unattemptedOnly", "true")
                        .param("attemptedOnly", "false"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRecordAttemptAndEvaluateResult() throws Exception {
        AttemptRequest req = AttemptRequest.builder()
                .questionId(savedQuestion.getId())
                .selectedOption("<P1, P2, P0>")
                .username("testuser")
                .build();

        mockMvc.perform(post("/api/v1/attempts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.questionId", is(savedQuestion.getId().intValue())))
                .andExpect(jsonPath("$.isCorrect", is(true)))
                .andExpect(jsonPath("$.correctAnswer", is("<P1, P2, P0>")))
                .andExpect(jsonPath("$.explanation", notNullValue()));
    }

    @Test
    void shouldReturnTopicPerformanceAnalytics() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/topic-performance")
                        .param("username", "testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].topic", is("Banker's Algorithm")))
                .andExpect(jsonPath("$[0].needsFocus", is(true)));
    }
}
