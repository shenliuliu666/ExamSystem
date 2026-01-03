package com.examsystem.proctor;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProctorRepository {
    private final JdbcTemplate jdbcTemplate;

    public ProctorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertEvent(long examId, long attemptId, String username, String type, String payloadJson, Instant now) {
        jdbcTemplate.update(
                "INSERT INTO proctor_events(exam_id, attempt_id, username, type, payload_json, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                examId,
                attemptId,
                username,
                type,
                payloadJson,
                java.sql.Timestamp.from(now)
        );
    }

    public void insertHeartbeat(long attemptId, String username, Instant ts, Instant now) {
        jdbcTemplate.update(
                "INSERT INTO attempt_heartbeats(attempt_id, username, ts, created_at) VALUES (?, ?, ?, ?)",
                attemptId,
                username,
                java.sql.Timestamp.from(ts),
                java.sql.Timestamp.from(now)
        );
    }

    public List<ProctorEvent> listRecentEvents(long examId, int limit) {
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, 200);
        return jdbcTemplate.query(
                "SELECT id, exam_id, attempt_id, username, type, payload_json, created_at FROM proctor_events WHERE exam_id = ? ORDER BY created_at DESC, id DESC LIMIT ?",
                (rs, rowNum) -> new ProctorEvent(
                        rs.getLong("id"),
                        rs.getLong("exam_id"),
                        rs.getLong("attempt_id"),
                        rs.getString("username"),
                        rs.getString("type"),
                        rs.getString("payload_json"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                examId,
                safeLimit
        );
    }

    public List<HeartbeatRecord> listHeartbeatsByExam(long examId) {
        return jdbcTemplate.query(
                "SELECT h.id, h.attempt_id, h.username, h.ts, h.created_at " +
                        "FROM attempt_heartbeats h " +
                        "JOIN exam_attempts a ON h.attempt_id = a.id " +
                        "WHERE a.exam_id = ? " +
                        "ORDER BY h.attempt_id ASC, h.ts ASC, h.id ASC",
                (rs, rowNum) -> new HeartbeatRecord(
                        rs.getLong("id"),
                        rs.getLong("attempt_id"),
                        rs.getString("username"),
                        rs.getTimestamp("ts").toInstant(),
                        rs.getTimestamp("created_at").toInstant()
                ),
                examId
        );
    }
}

