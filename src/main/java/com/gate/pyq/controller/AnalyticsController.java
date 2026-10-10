package com.gate.pyq.controller;

import com.gate.pyq.dto.TopicPerformanceDto;
import com.gate.pyq.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/topic-performance")
    public ResponseEntity<List<TopicPerformanceDto>> getTopicPerformance(
            @RequestParam(required = false) String username
    ) {
        List<TopicPerformanceDto> stats = analyticsService.getTopicPerformance(username);
        return ResponseEntity.ok(stats);
    }
}
