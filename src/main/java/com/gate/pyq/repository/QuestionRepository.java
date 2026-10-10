package com.gate.pyq.repository;

import com.gate.pyq.entity.Question;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findBySubjectIgnoreCaseAndTopicIgnoreCase(String subject, String topic, Sort sort);

    List<Question> findBySubjectIgnoreCase(String subject, Sort sort);

    List<Question> findByTopicIgnoreCase(String topic, Sort sort);

    boolean existsByQuestionTextIgnoreCase(String questionText);
}
