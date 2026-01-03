package com.examsystem.question;

import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionBankService {
    private final QuestionBankRepository repository;

    public QuestionBankService(QuestionBankRepository repository) {
        this.repository = repository;
    }

    public QuestionBank create(String name, String ownerUsername, String visibility) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        String v = (visibility == null || visibility.isBlank()) ? "PRIVATE" : visibility.toUpperCase();
        return repository.create(name.trim(), ownerUsername, v);
    }

    public List<QuestionBank> listForUser(String username) {
        return repository.listForUser(username);
    }

    public Optional<QuestionBank> findById(long id) {
        return repository.findById(id);
    }

    public List<QuestionBankMember> listMembers(long bankId, String username) {
        if (!repository.isOwner(bankId, username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only owner can view members");
        }
        return repository.listMembers(bankId);
    }

    public QuestionBankMember addMember(long bankId, String operatorUsername, String username, String role) {
        if (!repository.isOwner(bankId, operatorUsername)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only owner can manage members");
        }
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username is required");
        }
        if (repository.isMember(bankId, username)) {
            return repository.listMembers(bankId).stream()
                    .filter(m -> m.getUsername().equals(username))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "member already exists"));
        }
        String r = (role == null || role.isBlank()) ? "EDITOR" : role.toUpperCase();
        repository.addMember(bankId, username.trim(), r);
        return repository.listMembers(bankId).stream()
                .filter(m -> m.getUsername().equals(username))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "member not found after insert"));
    }

    public boolean canAccessBank(long bankId, String username) {
        return repository.isOwner(bankId, username) || repository.isMember(bankId, username);
    }

    public boolean isOwner(long bankId, String username) {
        return repository.isOwner(bankId, username);
    }
}

