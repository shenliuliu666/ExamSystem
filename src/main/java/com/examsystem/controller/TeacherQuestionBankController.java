package com.examsystem.controller;

import com.examsystem.question.QuestionBank;
import com.examsystem.question.QuestionBankMember;
import com.examsystem.question.QuestionBankService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/question-banks")
public class TeacherQuestionBankController {
    private final QuestionBankService service;

    public TeacherQuestionBankController(QuestionBankService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BankResponse> create(@RequestBody CreateBankRequest request) {
        QuestionBank created = service.create(request.getName(), currentUsername(), request.getVisibility());
        return ResponseEntity.status(HttpStatus.CREATED).body(BankResponse.from(created));
    }

    @GetMapping
    public List<BankResponse> list() {
        String username = currentUsername();
        return service.listForUser(username).stream().map(BankResponse::from).collect(java.util.stream.Collectors.toList());
    }

    @GetMapping("/{id}/members")
    public List<MemberResponse> listMembers(@PathVariable("id") long bankId) {
        String username = currentUsername();
        List<QuestionBankMember> members = service.listMembers(bankId, username);
        return members.stream().map(MemberResponse::from).collect(java.util.stream.Collectors.toList());
    }

    @PostMapping("/{id}/members")
    public MemberResponse addMember(@PathVariable("id") long bankId, @RequestBody AddMemberRequest request) {
        QuestionBankMember member = service.addMember(bankId, currentUsername(), request.getUsername(), request.getRole());
        return MemberResponse.from(member);
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return String.valueOf(authentication.getPrincipal());
    }

    public static class CreateBankRequest {
        private String name;
        private String visibility;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVisibility() {
            return visibility;
        }

        public void setVisibility(String visibility) {
            this.visibility = visibility;
        }
    }

    public static class AddMemberRequest {
        private String username;
        private String role;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    public static class BankResponse {
        private final long id;
        private final String name;
        private final String ownerUsername;
        private final String visibility;
        private final String createdAt;
        private final String updatedAt;

        public BankResponse(long id, String name, String ownerUsername, String visibility, String createdAt, String updatedAt) {
            this.id = id;
            this.name = name;
            this.ownerUsername = ownerUsername;
            this.visibility = visibility;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public static BankResponse from(QuestionBank bank) {
            return new BankResponse(
                    bank.getId(),
                    bank.getName(),
                    bank.getOwnerUsername(),
                    bank.getVisibility(),
                    bank.getCreatedAt().toString(),
                    bank.getUpdatedAt().toString()
            );
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getOwnerUsername() {
            return ownerUsername;
        }

        public String getVisibility() {
            return visibility;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }
    }

    public static class MemberResponse {
        private final long id;
        private final long bankId;
        private final String username;
        private final String role;
        private final String joinedAt;

        public MemberResponse(long id, long bankId, String username, String role, String joinedAt) {
            this.id = id;
            this.bankId = bankId;
            this.username = username;
            this.role = role;
            this.joinedAt = joinedAt;
        }

        public static MemberResponse from(QuestionBankMember member) {
            return new MemberResponse(
                    member.getId(),
                    member.getBankId(),
                    member.getUsername(),
                    member.getRole(),
                    member.getJoinedAt().toString()
            );
        }

        public long getId() {
            return id;
        }

        public long getBankId() {
            return bankId;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }

        public String getJoinedAt() {
            return joinedAt;
        }
    }
}
