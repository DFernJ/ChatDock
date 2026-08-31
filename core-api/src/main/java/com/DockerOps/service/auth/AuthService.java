package com.DockerOps.service.auth;

import com.DockerOps.dto.request.RegisterRequest;
import com.DockerOps.exception.ConflictException;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CodeRepository codeRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public User authUser(String email, String password) {
        return userRepository.findByEmail(email)
                .filter(user -> user.isEnabled() &&
                        passwordEncoder.matches(password, user.getPassword_hash())).orElse(null);
    }

    @Transactional
    public User registerUser(RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.username()) ||
                userRepository.existsByEmail(registerRequest.email())) {
            throw new ConflictException("Username or email already in use.");
        }

        Code code = codeRepository.findByCode(registerRequest.code())
                .filter(c -> c.getCodeType() == CodeType.REGISTER && c.getRemainUses() > 0)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired registration code."));

        User user = User.builder()
                .username(registerRequest.username())
                .email(registerRequest.email())
                .password_hash(passwordEncoder.encode(registerRequest.password()))
                .authRole(UserAuthRole.USER)
                .permissions(UserPermissions.VIEWER)
                .enabled(true)
                .build();
        user.setCreatedBy(code.getUser().getId());
        userRepository.save(user);

        code.setRemainUses(code.getRemainUses() - 1);
        codeRepository.save(code);

        return user;
    }

    public User authContext(UUID userId) {
        return userRepository.findById(userId).orElse(null);
    }
}
