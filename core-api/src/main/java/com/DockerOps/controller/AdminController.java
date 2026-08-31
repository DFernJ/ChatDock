package com.DockerOps.controller;

import com.DockerOps.dto.request.CreateUserRequest;
import com.DockerOps.dto.request.GenerateCodeRequest;
import com.DockerOps.dto.request.UpdateUserRequest;
import com.DockerOps.dto.response.AdminUserResponse;
import com.DockerOps.dto.response.CodeResponse;
import com.DockerOps.model.users.Code;
import com.DockerOps.model.users.User;
import com.DockerOps.service.admin.AdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final int MIN_PASSWORD_LENGTH = 8;

    @Autowired
    private AdminService adminService;

    @PreAuthorize("hasAuthority('PERM_ROOT') and hasAuthority('ROLE_ADMIN')")
    @GetMapping("/users")
    public List<AdminUserResponse> listUsers() {
        log.info("Listing users");
        return adminService.listUsers().stream().map(AdminUserResponse::from).toList();
    }

    @PreAuthorize("hasAuthority('PERM_ROOT') and hasAuthority('ROLE_ADMIN')")
    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        if (request.password() == null || request.password().length() < MIN_PASSWORD_LENGTH) {
            log.warn("Rejected user creation for username={}: password too short", request.username());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        User user = adminService.createUser(request);
        log.info("Created user id={}, username={}", user.getId(), user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminUserResponse.from(user));
    }

    @PreAuthorize("hasAuthority('PERM_ROOT') and hasAuthority('ROLE_ADMIN')")
    @PatchMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable UUID id, @RequestBody UpdateUserRequest request, Authentication authentication) {
        if (isSelf(id, authentication)) {
            log.warn("Rejected self-update attempt on user id={}", id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Cannot modify your own account");
        }
        User updated = adminService.updateUser(id, request);
        log.info("Updated user id={}", id);
        return ResponseEntity.ok(AdminUserResponse.from(updated));
    }

    @PreAuthorize("hasAuthority('PERM_ROOT') and hasAuthority('ROLE_ADMIN')")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable UUID id, Authentication authentication) {
        if (isSelf(id, authentication)) {
            log.warn("Rejected self-delete attempt on user id={}", id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Cannot delete your own account");
        }
        User admin = (User) authentication.getPrincipal();
        adminService.deleteUser(id, admin);
        log.info("Deleted user id={}", id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('PERM_ROOT') and hasAuthority('ROLE_ADMIN')")
    @GetMapping("/codes")
    public List<CodeResponse> listCodes() {
        log.info("Listing registration codes");
        return adminService.listCodes().stream().map(CodeResponse::from).toList();
    }

    @PreAuthorize("hasAuthority('PERM_ROOT') and hasAuthority('ROLE_ADMIN')")
    @PostMapping("/codes")
    public ResponseEntity<?> generateCode(@RequestBody GenerateCodeRequest request, Authentication authentication) {
        User issuer = (User) authentication.getPrincipal();
        Code code = adminService.generateCode(request, issuer);
        log.info("Generated registration code id={} issued by {}", code.getId(), issuer.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(CodeResponse.from(code));
    }

    @PreAuthorize("hasAuthority('PERM_ROOT') and hasAuthority('ROLE_ADMIN')")
    @DeleteMapping("/codes/{id}")
    public ResponseEntity<?> deleteCode(@PathVariable UUID id) {
        adminService.deleteCode(id);
        log.info("Deleted registration code id={}", id);
        return ResponseEntity.noContent().build();
    }

    private boolean isSelf(UUID id, Authentication authentication) {
        return ((User) authentication.getPrincipal()).getId().equals(id);
    }
}
