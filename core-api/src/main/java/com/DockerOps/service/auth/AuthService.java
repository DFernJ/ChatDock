package com.DockerOps.service.auth;

import com.DockerOps.dto.request.RegisterRequest;
import com.DockerOps.model.users.User;
import com.DockerOps.model.users.enums.UserAuthRole;
import com.DockerOps.model.users.enums.UserPermissions;
import com.DockerOps.repository.users.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public User authUser(String email, String password) {
        return userRepository.findByEmail(email)
                .filter(user -> user.isEnabled() &&
                        passwordEncoder.matches(password, user.getPassword_hash())).orElse(null);
    }

    public User registerUser(RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.username()) ||
                userRepository.existsByEmail(registerRequest.email())) {
            return null;
        }

        User user = User.builder()
                .username(registerRequest.username())
                .email(registerRequest.email())
                .password_hash(passwordEncoder.encode(registerRequest.password()))
                .authRole(registerRequest.userAuthRole() != null ? registerRequest.userAuthRole() : UserAuthRole.USER)
                .permissions(registerRequest.userPermissions() != null ? registerRequest.userPermissions() : UserPermissions.VIEWER)
                .enabled(true)
                .build();
        userRepository.save(user);
        return user;
    }

    public User authContext(UUID userId) {
        return userRepository.findById(userId).orElse(null);
    }
}
