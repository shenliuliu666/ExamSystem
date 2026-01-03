package com.examsystem.attempt;

import com.examsystem.exam.ExamArrangement;
import com.examsystem.exam.ExamService;
import com.examsystem.exam.ExamStatus;
import com.examsystem.paper.Paper;
import com.examsystem.paper.PaperItem;
import com.examsystem.paper.PaperService;
import com.examsystem.question.Question;
import com.examsystem.question.QuestionService;
import com.examsystem.result.ExamResultService;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExamAttemptService {
    private final InMemoryExamAttemptRepository repository;
    private final ExamService examService;
    private final PaperService paperService;
    private final QuestionService questionService;
    private final ExamResultService resultService;

    public ExamAttemptService(
            InMemoryExamAttemptRepository repository,
            ExamService examService,
            PaperService paperService,
            QuestionService questionService,
            ExamResultService resultService
    ) {
        this.repository = repository;
        this.examService = examService;
        this.paperService = paperService;
        this.questionService = questionService;
        this.resultService = resultService;
    }

    public List<ExamAttempt> listByStudent(String username) {
        return repository.listByStudent(username);
    }

    public Optional<ExamAttempt> findById(long id) {
        return repository.findById(id);
    }

    public ExamAttempt start(long examId, String studentUsername) {
        ExamArrangement exam = examService.findById(examId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "exam not found"));

        ExamStatus status = examService.statusOf(exam, Instant.now());
        if (status != ExamStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "exam not in progress");
        }

        Optional<ExamAttempt> existing = repository.findActiveAttempt(examId, studentUsername);
        if (existing.isPresent()) {
            return existing.get();
        }

        Paper paper = paperService.findById(exam.getPaperId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "paper not found"));

        List<QuestionSnapshot> questions = paper.getItems().stream()
                .sorted(java.util.Comparator.comparingInt(PaperItem::getOrderIndex))
                .map(item -> {
                    Question q = questionService.findById(item.getQuestionId())
                            .orElseThrow(() -> new ResponseStatusException(
                                    HttpStatus.CONFLICT,
                                    "question not found: " + item.getQuestionId()
                            ));
                    return new QuestionSnapshot(
                            q.getId(),
                            q.getType().name(),
                            q.getStem(),
                            q.getOptions(),
                            q.getScore()
                    );
                })
                .collect(Collectors.toList());

        return repository.create(exam.getId(), paper.getId(), studentUsername, questions);
    }

    public ExamAttempt submit(long examId, long attemptId, String studentUsername, List<AnswerRecord> answers) {
        ExamAttempt attempt = repository.findById(attemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "attempt not found"));

        if (attempt.getExamId() != examId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "attempt does not belong to exam");
        }
        if (!attempt.getStudentUsername().equals(studentUsername)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "attempt does not belong to current user");
        }
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "attempt already submitted");
        }

        ExamArrangement exam = examService.findById(examId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "exam not found"));
        ExamStatus status = examService.statusOf(exam, Instant.now());
        if (status != ExamStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "exam not in progress");
        }

        Set<Long> allowedQuestionIds = attempt.getQuestions().stream().map(QuestionSnapshot::getId).collect(Collectors.toSet());
        Set<Long> seen = new HashSet<>();
        for (AnswerRecord answer : answers) {
            if (answer.getQuestionId() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid questionId in answers");
            }
            if (!allowedQuestionIds.contains(answer.getQuestionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "answer contains question not in paper");
            }
            if (!seen.add(answer.getQuestionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate questionId in answers");
            }
        }

        ExamAttempt submitted = repository.submit(attemptId, answers)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "attempt not found"));
        resultService.ensureResultCreated(examId, submitted);
        return submitted;
    }
}
