package com.examsystem.proctor;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ExamProctorService {
    private final ProctorRepository repository;

    public ExamProctorService(ProctorRepository repository) {
        this.repository = repository;
    }

    public void recordEvent(long examId, long attemptId, String username, String type, String payloadJson, Instant now) {
        repository.insertEvent(examId, attemptId, username, type, payloadJson, now);
    }

    public void recordHeartbeat(long attemptId, String username, Instant ts, Instant now) {
        repository.insertHeartbeat(attemptId, username, ts, now);
    }

    public List<ProctorEvent> listRecentEvents(long examId, int limit) {
        return repository.listRecentEvents(examId, limit);
    }

    public List<HeartbeatRecord> listLatestHeartbeats(long examId) {
        List<HeartbeatRecord> all = repository.listHeartbeatsByExam(examId);
        Map<Long, HeartbeatRecord> latestByAttempt = all.stream()
                .collect(Collectors.toMap(
                        HeartbeatRecord::getAttemptId,
                        h -> h,
                        (a, b) -> a.getTs().isAfter(b.getTs()) ? a : b
                ));
        return latestByAttempt.values().stream()
                .sorted(Comparator.comparingLong(HeartbeatRecord::getAttemptId))
                .collect(Collectors.toList());
    }
}

