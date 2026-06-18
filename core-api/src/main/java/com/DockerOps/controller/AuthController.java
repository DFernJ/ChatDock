package com.DockerOps.controller;

import com.DockerOps.dto.request.LoginRequest;
import com.DockerOps.dto.request.RegisterRequest;
import com.DockerOps.dto.response.UserResponse;
import com.DockerOps.model.users.User;
import com.DockerOps.service.auth.AuthService;
import com.DockerOps.service.auth.CookieService;
import com.DockerOps.service.auth.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String AUTH_COOKIE = "auth_cookie";
    private static final int MIN_PASSWORD_LENGTH = 8;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthService authService;

    @Autowired
    private CookieService cookieService;

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest request, HttpServletResponse response) {
        User user = authService.authUser(request.email(), request.password());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email, password or disabled account.");
        }
        cookieService.setAuthCookie(response, jwtService.generateToken(user));
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest request, HttpServletResponse response) {
        if (request.password() == null || request.password().length() < MIN_PASSWORD_LENGTH) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (request.code() == null || request.code().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Registration code is required.");
        }
        User user = authService.registerUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        if (!user.isEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        cookieService.setAuthCookie(response, jwtService.generateToken(user));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(HttpServletResponse response) {
        cookieService.clearAuthCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> getAuthContext(@CookieValue(value = AUTH_COOKIE, required = false) String token) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            User user = authService.authContext(jwtService.parseUserId(token));
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            return ResponseEntity.status(HttpStatus.OK).body(UserResponse.from(user));
        } catch (JwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
