package com.examsystem.result;

import com.examsystem.attempt.AnswerRecord;
import com.examsystem.attempt.ExamAttempt;
import com.examsystem.attempt.QuestionSnapshot;
import com.examsystem.question.Question;
import com.examsystem.question.QuestionService;
import com.examsystem.question.QuestionType;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExamResultService {
    private final InMemoryExamResultRepository repository;
    private final QuestionService questionService;

    public ExamResultService(InMemoryExamResultRepository repository, QuestionService questionService) {
        this.repository = repository;
        this.questionService = questionService;
    }

    public ExamResult ensureResultCreated(long examId, ExamAttempt attempt) {
        Optional<ExamResult> existing = repository.findByExamAndAttempt(examId, attempt.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        ExamResultDraft draft = gradeAttempt(attempt);
        return repository.saveIfAbsent(examId, attempt.getId(), draft);
    }

    public Optional<ExamResult> findByExamAndAttempt(long examId, long attemptId) {
        return repository.findByExamAndAttempt(examId, attemptId);
    }

    public List<ExamResult> listByStudent(String username) {
        return repository.listByStudent(username);
    }

    public List<ExamResult> listByExamId(long examId) {
        return repository.listByExamId(examId);
    }

    private ExamResultDraft gradeAttempt(ExamAttempt attempt) {
        Map<Long, String> answerByQuestionId = attempt.getAnswers().stream()
                .collect(Collectors.toMap(AnswerRecord::getQuestionId, AnswerRecord::getAnswer, (a, b) -> b));

        List<QuestionSnapshot> questions = attempt.getQuestions().stream()
                .sorted(Comparator.comparingLong(QuestionSnapshot::getId))
                .collect(Collectors.toList());

        List<Question> questionEntities = questions.stream()
                .map(q -> questionService.findById(q.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "question not found: " + q.getId())))
                .collect(Collectors.toList());

        Map<Long, Question> questionById = questionEntities.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        List<ExamResultItem> items = questions.stream()
                .map(q -> {
                    Question entity = questionById.get(q.getId());
                    String answer = answerByQuestionId.getOrDefault(q.getId(), "");
                    String correctAnswer = entity.getCorrectAnswer();
                    boolean correct = isCorrect(entity.getType(), answer, correctAnswer);
                    int maxScore = q.getScore();
                    int earnedScore = correct ? maxScore : 0;
                    return new ExamResultItem(
                            q.getId(),
                            q.getType(),
                            answer,
                            correctAnswer,
                            maxScore,
                            earnedScore,
                            correct
                    );
                })
                .collect(Collectors.toList());

        int total = items.stream().mapToInt(ExamResultItem::getEarnedScore).sum();
        int max = items.stream().mapToInt(ExamResultItem::getMaxScore).sum();
        return new ExamResultDraft(attempt.getStudentUsername(), total, max, items);
    }

    private boolean isCorrect(QuestionType type, String answer, String correctAnswer) {
        if (type == QuestionType.SINGLE_CHOICE) {
            String a = answer == null ? "" : answer.trim().toUpperCase();
            String c = correctAnswer == null ? "" : correctAnswer.trim().toUpperCase();
            return a.equals(c);
        }
        if (type == QuestionType.MULTIPLE_CHOICE) {
            String a = normalizeMultipleChoiceAnswer(answer);
            String c = normalizeMultipleChoiceAnswer(correctAnswer);
            return !a.isEmpty() && a.equals(c);
        }
        if (type == QuestionType.TRUE_FALSE) {
            String a = answer == null ? "" : answer.trim().toLowerCase();
            String c = correctAnswer == null ? "" : correctAnswer.trim().toLowerCase();
            return a.equals(c);
        }
        return false;
    }

    private String normalizeMultipleChoiceAnswer(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] parts = trimmed.split(",");
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String p : parts) {
            String v = p == null ? "" : p.trim().toUpperCase();
            if (v.isEmpty()) {
                continue;
            }
            if (v.matches("[A-Z]")) {
                set.add(v);
            }
        }
        List<String> list = new java.util.ArrayList<>(set);
        list.sort(String::compareTo);
        return String.join(",", list);
    }
}
