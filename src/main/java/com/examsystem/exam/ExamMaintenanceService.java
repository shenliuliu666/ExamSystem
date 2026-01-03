package com.examsystem.exam;

import com.examsystem.attempt.ExamAttempt;
import com.examsystem.attempt.InMemoryExamAttemptRepository;
import com.examsystem.result.ExamResultService;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ExamMaintenanceService {
    private final ExamService examService;
    private final InMemoryExamAttemptRepository attemptRepository;
    private final ExamResultService resultService;

    public ExamMaintenanceService(
            ExamService examService,
            InMemoryExamAttemptRepository attemptRepository,
            ExamResultService resultService
    ) {
        this.examService = examService;
        this.attemptRepository = attemptRepository;
        this.resultService = resultService;
    }

    @Scheduled(fixedDelay = 60000)
    public void scheduledTick() {
        runOnce(Instant.now());
    }

    public void runOnce(Instant now) {
        List<ExamAttempt> inProgressAttempts = attemptRepository.listInProgressAttempts();
        for (ExamAttempt attempt : inProgressAttempts) {
            examService.findById(attempt.getExamId()).ifPresent(exam -> {
                if (examService.statusOf(exam, now) != ExamStatus.ENDED) {
                    return;
                }
                attemptRepository.autoSubmitIfInProgress(attempt.getId()).ifPresent(submitted -> {
                    resultService.ensureResultCreated(exam.getId(), submitted);
                });
            });
        }
    }
}
