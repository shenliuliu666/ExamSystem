package com.examsystem.exam;

import com.examsystem.course.ClassRepository;
import com.examsystem.paper.Paper;
import com.examsystem.paper.PaperService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExamService {
    private final InMemoryExamRepository repository;
    private final PaperService paperService;
    private final ClassRepository classRepository;

    public ExamService(InMemoryExamRepository repository, PaperService paperService, ClassRepository classRepository) {
        this.repository = repository;
        this.paperService = paperService;
        this.classRepository = classRepository;
    }

    public ExamArrangement create(ExamDraft draft) {
        validateDraft(draft);
        return repository.create(draft);
    }

    public void update(long id, ExamDraft draft) {
        validateDraft(draft);
        if (repository.findById(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "exam not found");
        }
        repository.update(id, draft);
    }

    public void delete(long id) {
        if (repository.findById(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "exam not found");
        }
        repository.delete(id);
    }

    public Optional<ExamArrangement> findById(long id) {
        return repository.findById(id);
    }

    public List<ExamArrangement> listAll() {
        return repository.listAll();
    }

    public List<ExamArrangement> listByClassIds(List<Long> classIds) {
        return repository.listByClassIds(classIds);
    }

    public ExamSettings getSettingsOrDefault(long examId) {
        return repository.findSettingsById(examId).orElseGet(ExamSettings::defaultSettings);
    }

    public ExamStatus statusOf(ExamArrangement exam, Instant now) {
        if (now.isBefore(exam.getStartAt())) {
            return ExamStatus.NOT_STARTED;
        }
        if (now.isBefore(exam.getEndAt())) {
            return ExamStatus.IN_PROGRESS;
        }
        return ExamStatus.ENDED;
    }

    private void validateDraft(ExamDraft draft) {
        if (draft == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (draft.getName() == null || draft.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (draft.getPaperId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paperId is required");
        }
        if (draft.getClassId() == null || draft.getClassId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "classId is required");
        }
        if (classRepository.findById(draft.getClassId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "class not found: " + draft.getClassId());
        }
        Optional<Paper> paper = paperService.findById(draft.getPaperId());
        if (paper.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paper not found: " + draft.getPaperId());
        }
        if (draft.getStartAt() == null || draft.getEndAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startAt and endAt are required");
        }
        if (!draft.getEndAt().isAfter(draft.getStartAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endAt must be after startAt");
        }
    }
}
