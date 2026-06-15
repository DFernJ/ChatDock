package com.DockerOps.service.admin;

import com.DockerOps.dto.request.CreateUserRequest;
import com.DockerOps.dto.request.GenerateCodeRequest;
import com.DockerOps.dto.request.UpdateUserRequest;
import com.DockerOps.model.users.Code;
import com.DockerOps.model.users.User;
import com.DockerOps.model.users.enums.CodeType;
import com.DockerOps.model.users.enums.UserAuthRole;
import com.DockerOps.model.users.enums.UserPermissions;
import com.DockerOps.repository.users.CodeRepository;
import com.DockerOps.repository.users.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
public class AdminService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 12;

    private final SecureRandom random = new SecureRandom();

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CodeRepository codeRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public List<User> listUsers() {
        return userRepository.findAll();
    }

    public User createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username()) || userRepository.existsByEmail(request.email())) {
            return null;
        }
        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password_hash(passwordEncoder.encode(request.password()))
                .authRole(UserAuthRole.valueOf(request.authRole().toUpperCase()))
                .permissions(UserPermissions.valueOf(request.permissionRole().toUpperCase()))
                .enabled(true)
                .build();
        return userRepository.save(user);
    }

    public User updateUser(UUID id, UpdateUserRequest request) {
        return userRepository.findById(id).map(user -> {
            if (request.enabled() != null) {
                user.setEnabled(request.enabled());
            }
            if (request.authRole() != null) {
                user.setAuthRole(UserAuthRole.valueOf(request.authRole().toUpperCase()));
            }
            if (request.permissionRole() != null) {
                user.setPermissions(UserPermissions.valueOf(request.permissionRole().toUpperCase()));
            }
            return userRepository.save(user);
        }).orElse(null);
    }

    public boolean deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            return false;
        }
        userRepository.deleteById(id);
        return true;
    }

    public List<Code> listCodes() {
        return codeRepository.findAll();
    }

    public Code generateCode(GenerateCodeRequest request, User creator) {
        if (request.uses() == null || request.uses() < 1) {
            throw new IllegalArgumentException("uses must be at least 1");
        }
        Code code = Code.builder()
                .id(UUID.randomUUID())
                .code(generateUniqueCode())
                .user(creator)
                .remainUses(request.uses())
                .codeType(CodeType.valueOf(request.codeType().toUpperCase()))
                .build();
        return codeRepository.save(code);
    }

    public boolean deleteCode(UUID id) {
        if (!codeRepository.existsById(id)) {
            return false;
        }
        codeRepository.deleteById(id);
        return true;
    }

    private String generateUniqueCode() {
        String candidate;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            candidate = sb.toString();
        } while (codeRepository.existsByCode(candidate));
        return candidate;
    }
}
