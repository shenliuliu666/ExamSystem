package com.examsystem.paper;

import com.examsystem.question.Question;
import com.examsystem.question.QuestionService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaperService {
    private final InMemoryPaperRepository repository;
    private final QuestionService questionService;

    public PaperService(InMemoryPaperRepository repository, QuestionService questionService) {
        this.repository = repository;
        this.questionService = questionService;
    }

    public Paper create(PaperDraft draft) {
        List<PaperItem> items = validateAndBuildItems(draft);
        return repository.create(draft, items);
    }

    public Optional<Paper> findById(long id) {
        return repository.findById(id);
    }

    public Optional<Paper> update(long id, PaperDraft draft) {
        List<PaperItem> items = validateAndBuildItems(draft);
        return repository.update(id, draft, items);
    }

    public boolean delete(long id) {
        if (repository.isUsedByAnyExamOrAttempt(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "paper is used by exam/attempt, cannot delete");
        }
        return repository.delete(id);
    }

    public PagedResult<Paper> list(PaperQuery query, int page, int size) {
        if (page < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 1");
        }
        if (size < 1 || size > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 200");
        }

        List<Paper> all = repository.list(query);
        int total = all.size();
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<Paper> items = all.subList(fromIndex, toIndex);
        return new PagedResult<>(items, total, page, size);
    }

    private List<PaperItem> validateAndBuildItems(PaperDraft draft) {
        if (draft == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (draft.getName() == null || draft.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (draft.getQuestionIds() == null || draft.getQuestionIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "questionIds is required");
        }

        Set<Long> seen = new HashSet<>();
        List<PaperItem> items = new ArrayList<>();
        int order = 1;
        for (Long questionId : draft.getQuestionIds()) {
            if (questionId == null || questionId <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "questionIds contains invalid id");
            }
            if (!seen.add(questionId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "questionIds contains duplicates");
            }
            Optional<Question> q = questionService.findById(questionId);
            if (q.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question not found: " + questionId);
            }
            if (!q.get().isEnabled()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question disabled: " + questionId);
            }
            items.add(new PaperItem(questionId, order++));
        }
        return items;
    }

    public static class PagedResult<T> {
        private final List<T> items;
        private final int total;
        private final int page;
        private final int size;

        public PagedResult(List<T> items, int total, int page, int size) {
            this.items = items;
            this.total = total;
            this.page = page;
            this.size = size;
        }

        public List<T> getItems() {
            return items;
        }

        public int getTotal() {
            return total;
        }

        public int getPage() {
            return page;
        }

        public int getSize() {
            return size;
        }
    }
}
