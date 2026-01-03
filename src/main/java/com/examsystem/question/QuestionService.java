package com.examsystem.question;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionService {
    private final InMemoryQuestionRepository repository;

    public QuestionService(InMemoryQuestionRepository repository) {
        this.repository = repository;
    }

    public Question create(QuestionDraft draft) {
        validateDraft(draft);
        return repository.create(normalizeDraft(draft));
    }

    public Optional<Question> findById(long id) {
        return repository.findById(id);
    }

    public Optional<Question> update(long id, QuestionDraft draft) {
        validateDraft(draft);
        return repository.update(id, normalizeDraft(draft));
    }

    public boolean delete(long id) {
        return repository.delete(id);
    }

    public PagedResult<Question> list(QuestionQuery query, int page, int size) {
        if (page < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 1");
        }
        if (size < 1 || size > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 200");
        }

        List<Question> all = repository.list(query);
        int total = all.size();
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<Question> items = all.subList(fromIndex, toIndex);
        return new PagedResult<>(items, total, page, size);
    }

    public List<Question> listAll(QuestionQuery query) {
        return repository.list(query);
    }

    private void validateDraft(QuestionDraft draft) {
        if (draft == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (draft.getType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type is required");
        }
        if (draft.getStem() == null || draft.getStem().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stem is required");
        }
        if (draft.getScore() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "score must be > 0");
        }
        if (draft.getCorrectAnswer() == null || draft.getCorrectAnswer().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "correctAnswer is required");
        }

        if (draft.getType() == QuestionType.SINGLE_CHOICE) {
            if (draft.getOptions() == null || draft.getOptions().size() < 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "options must have at least 2 items");
            }
            String upper = normalizeSingleChoiceAnswer(draft.getCorrectAnswer());
            if (!upper.matches("[A-Z]")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "correctAnswer must be A-Z for SINGLE_CHOICE");
            }
            int index = upper.charAt(0) - 'A';
            if (index < 0 || index >= draft.getOptions().size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "correctAnswer out of options range");
            }
        }

        if (draft.getType() == QuestionType.MULTIPLE_CHOICE) {
            if (draft.getOptions() == null || draft.getOptions().size() < 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "options must have at least 2 items");
            }
            List<String> answers = normalizeMultipleChoiceAnswers(draft.getCorrectAnswer());
            if (answers.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "correctAnswer is required for MULTIPLE_CHOICE");
            }
            for (String a : answers) {
                if (!a.matches("[A-Z]")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "correctAnswer must be A-Z for MULTIPLE_CHOICE");
                }
                int index = a.charAt(0) - 'A';
                if (index < 0 || index >= draft.getOptions().size()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "correctAnswer out of options range");
                }
            }
        }

        if (draft.getType() == QuestionType.TRUE_FALSE) {
            String normalized = normalizeTrueFalseAnswer(draft.getCorrectAnswer());
            if (!normalized.equals("true") && !normalized.equals("false")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "correctAnswer must be true/false for TRUE_FALSE");
            }
        }
    }

    private QuestionDraft normalizeDraft(QuestionDraft draft) {
        if (draft.getType() == QuestionType.SINGLE_CHOICE) {
            return new QuestionDraft(
                    draft.getBankId(),
                    draft.getType(),
                    draft.getStem(),
                    draft.getOptions(),
                    normalizeSingleChoiceAnswer(draft.getCorrectAnswer()),
                    draft.getAnalysis(),
                    draft.getScore(),
                    draft.getDifficulty(),
                    draft.getKnowledgePoint(),
                    draft.getTags(),
                    draft.isEnabled()
            );
        }
        if (draft.getType() == QuestionType.MULTIPLE_CHOICE) {
            List<String> answers = normalizeMultipleChoiceAnswers(draft.getCorrectAnswer());
            String normalized = String.join(",", answers);
            return new QuestionDraft(
                    draft.getBankId(),
                    draft.getType(),
                    draft.getStem(),
                    draft.getOptions(),
                    normalized,
                    draft.getAnalysis(),
                    draft.getScore(),
                    draft.getDifficulty(),
                    draft.getKnowledgePoint(),
                    draft.getTags(),
                    draft.isEnabled()
            );
        }
        if (draft.getType() == QuestionType.TRUE_FALSE) {
            return new QuestionDraft(
                    draft.getBankId(),
                    draft.getType(),
                    draft.getStem(),
                    List.of(),
                    normalizeTrueFalseAnswer(draft.getCorrectAnswer()),
                    draft.getAnalysis(),
                    draft.getScore(),
                    draft.getDifficulty(),
                    draft.getKnowledgePoint(),
                    draft.getTags(),
                    draft.isEnabled()
            );
        }
        return draft;
    }

    private String normalizeSingleChoiceAnswer(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase();
    }

    private String normalizeTrueFalseAnswer(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }

    private List<String> normalizeMultipleChoiceAnswers(String raw) {
        if (raw == null) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        String[] parts = trimmed.split(",");
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String p : parts) {
            String v = p == null ? "" : p.trim().toUpperCase();
            if (!v.isEmpty()) {
                set.add(v);
            }
        }
        List<String> list = new java.util.ArrayList<>(set);
        list.sort(String::compareTo);
        return list;
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
