package com.DockerOps.service.admin;

import com.DockerOps.dto.request.CreateUserRequest;
import com.DockerOps.dto.request.GenerateCodeRequest;
import com.DockerOps.dto.request.UpdateUserRequest;
import com.DockerOps.model.users.Code;
import com.DockerOps.model.users.User;
import com.DockerOps.model.users.enums.CodeType;
import com.DockerOps.model.users.enums.UserAuthRole;
import com.DockerOps.model.users.enums.UserPermissions;
import com.DockerOps.repository.apps.AppRepository;
import com.DockerOps.repository.apps.AppStackRepository;
import com.DockerOps.repository.users.CodeRepository;
import com.DockerOps.repository.users.UserRepository;
import com.DockerOps.service.users.CodeGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CodeRepository codeRepository;
    @Autowired
    private AppRepository appRepository;
    @Autowired
    private AppStackRepository appStackRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CodeGeneratorService codeGeneratorService;

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

    @Transactional
    public boolean deleteUser(UUID id, User newOwner) {
        if (!userRepository.existsById(id)) {
            return false;
        }
        User user = userRepository.getReferenceById(id);
        appRepository.reassignOwner(user, newOwner);
        appStackRepository.reassignOwner(user, newOwner);
        codeRepository.deleteByUser(user);
        userRepository.deleteById(id);
        return true;
    }

    public List<Code> listCodes() {
        return codeRepository.findAllByOrderByCreatedAtDesc();
    }

    public Code generateCode(GenerateCodeRequest request, User creator) {
        if (request.uses() == null || request.uses() < 1) {
            throw new IllegalArgumentException("uses must be at least 1");
        }
        CodeType codeType = CodeType.valueOf(request.codeType().toUpperCase());
        if (codeType != CodeType.REGISTER) {
            throw new IllegalArgumentException("Only registration codes can be generated from the admin panel.");
        }
        Code code = Code.builder()
                .id(UUID.randomUUID())
                .code(codeGeneratorService.generateUniqueCode())
                .user(creator)
                .remainUses(request.uses())
                .codeType(codeType)
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
}
