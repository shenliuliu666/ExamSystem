package com.examsystem.course;

import com.examsystem.user.UserProfile;
import com.examsystem.user.UserProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClassService {
    private final ClassRepository repository;
    private final UserProfileRepository userProfileRepository;

    public ClassService(ClassRepository repository, UserProfileRepository userProfileRepository) {
        this.repository = repository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional
    public Classroom create(String name, String ownerUsername) {
        String inviteCode = generateInviteCode();
        return repository.create(name, ownerUsername, inviteCode);
    }

    public List<Classroom> listByOwner(String ownerUsername) {
        return repository.listByOwner(ownerUsername);
    }

    public Optional<Classroom> findById(long id) {
        return repository.findById(id);
    }

    @Transactional
    public Optional<Classroom> joinByInviteCode(String inviteCode, String username) {
        Optional<Classroom> classroom = repository.findByInviteCode(inviteCode);
        if (classroom.isEmpty()) {
            return Optional.empty();
        }
        Classroom c = classroom.get();
        if (!repository.isMember(c.getId(), username) && !c.getOwnerUsername().equals(username)) {
            repository.addMember(c.getId(), username);
        }
        return Optional.of(c);
    }

    public List<Classroom> listJoinedClasses(String username) {
        return repository.listJoinedClasses(username);
    }

    public List<ClassMember> listMembers(long classId) {
        List<ClassMember> members = repository.listMembers(classId);
        return members.stream().map(m -> {
            Optional<UserProfile> p = userProfileRepository.findByUsername(m.getUsername());
            String fullName = p.map(UserProfile::getFullName).orElse(null);
            String studentNo = p.map(UserProfile::getStudentNo).orElse(null);
            return new ClassMember(m.getId(), m.getClassId(), m.getUsername(), m.getJoinedAt(), fullName, studentNo);
        }).collect(Collectors.toList());
    }

    @Transactional
    public void addMember(long classId, String username) {
        String u = username == null ? "" : username.trim();
        if (u.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username required");
        }
        if (!repository.userExists(u)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "user_not_found");
        }
        if (!repository.isMember(classId, u)) {
            repository.addMember(classId, u);
        }
    }

    @Transactional
    public void removeMember(long classId, String username) {
        repository.removeMember(classId, username);
    }

    public boolean isMember(long classId, String username) {
        String u = username == null ? "" : username.trim();
        if (u.isEmpty()) {
            return false;
        }
        return repository.isMember(classId, u);
    }

    @Transactional
    public void deleteClass(long classId, boolean deleteMembers) {
        if (deleteMembers) {
            List<String> exclusiveMembers = repository.getMembersOnlyInClass(classId);
            repository.deleteUsers(exclusiveMembers);
        }
        repository.delete(classId);
    }

    @Transactional
    public void upsertMemberProfile(String username, String studentNo, String fullName, String operatorUsername) {
        Instant now = Instant.now();
        String createdBy = (operatorUsername == null || operatorUsername.isBlank()) ? "system" : operatorUsername;
        Optional<UserProfile> p = userProfileRepository.findByUsername(username);
        if (p.isPresent()) {
            userProfileRepository.update(username, studentNo, fullName, now);
        } else {
            userProfileRepository.insert(username, studentNo, fullName, createdBy, now);
        }
    }

    public List<String> getMembersOnlyInClass(long classId) {
        return repository.getMembersOnlyInClass(classId);
    }

    private String generateInviteCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
