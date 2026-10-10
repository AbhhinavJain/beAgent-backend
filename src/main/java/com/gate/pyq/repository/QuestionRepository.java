package com.gate.pyq.repository;

import com.gate.pyq.entity.Question;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findBySubjectIgnoreCaseAndTopicIgnoreCase(String subject, String topic, Pageable pageable);

    List<Question> findBySubjectIgnoreCase(String subject, Pageable pageable);

    List<Question> findByTopicIgnoreCase(String topic, Pageable pageable);

    boolean existsByQuestionTextIgnoreCase(String questionText);
}
