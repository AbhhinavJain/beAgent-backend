package com.gate.pyq.repository;

import com.gate.pyq.entity.UserAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserAttemptRepository extends JpaRepository<UserAttempt, Long> {

    interface TopicAggregationProjection {
        String getSubject();
        String getTopic();
        Long getTotalQuestions();
        Long getTotalAttempts();
        Long getCorrectAttempts();
        Long getAttemptedQuestions();
    }

    @Query("""
        SELECT q.subject AS subject,
               q.topic AS topic,
               COUNT(DISTINCT q.id) AS totalQuestions,
               COUNT(ua.id) AS totalAttempts,
               COALESCE(SUM(CASE WHEN ua.isCorrect = true THEN 1 ELSE 0 END), 0) AS correctAttempts,
               COUNT(DISTINCT ua.question.id) AS attemptedQuestions
        FROM Question q
        LEFT JOIN UserAttempt ua ON ua.question.id = q.id
        GROUP BY q.subject, q.topic
        ORDER BY q.subject ASC, q.topic ASC
    """)
    List<TopicAggregationProjection> aggregateTopicPerformance();

    @Query("""
        SELECT q.subject AS subject,
               q.topic AS topic,
               COUNT(DISTINCT q.id) AS totalQuestions,
               COUNT(ua.id) AS totalAttempts,
               COALESCE(SUM(CASE WHEN ua.isCorrect = true THEN 1 ELSE 0 END), 0) AS correctAttempts,
               COUNT(DISTINCT ua.question.id) AS attemptedQuestions
        FROM Question q
        LEFT JOIN UserAttempt ua ON ua.question.id = q.id AND LOWER(ua.username) = LOWER(:username)
        GROUP BY q.subject, q.topic
        ORDER BY q.subject ASC, q.topic ASC
    """)
    List<TopicAggregationProjection> aggregateTopicPerformanceByUsername(@Param("username") String username);

    @Query("SELECT DISTINCT ua.question.id FROM UserAttempt ua WHERE LOWER(ua.username) = LOWER(:username)")
    List<Long> findAttemptedQuestionIdsByUsername(@Param("username") String username);
}
