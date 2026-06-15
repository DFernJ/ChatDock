package com.DockerOps.controller;

import com.DockerOps.dto.request.CreateUserRequest;
import com.DockerOps.dto.request.GenerateCodeRequest;
import com.DockerOps.dto.request.UpdateUserRequest;
import com.DockerOps.dto.response.AdminUserResponse;
import com.DockerOps.dto.response.CodeResponse;
import com.DockerOps.model.users.Code;
import com.DockerOps.model.users.User;
import com.DockerOps.service.admin.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final int MIN_PASSWORD_LENGTH = 8;

    @Autowired
    private AdminService adminService;

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @GetMapping("/users")
    public List<AdminUserResponse> listUsers() {
        return adminService.listUsers().stream().map(AdminUserResponse::from).toList();
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        if (request.password() == null || request.password().length() < MIN_PASSWORD_LENGTH) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        User user = adminService.createUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminUserResponse.from(user));
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @PatchMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable UUID id, @RequestBody UpdateUserRequest request, Authentication authentication) {
        if (isSelf(id, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Cannot modify your own account");
        }
        User updated = adminService.updateUser(id, request);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(AdminUserResponse.from(updated));
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable UUID id, Authentication authentication) {
        if (isSelf(id, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Cannot delete your own account");
        }
        if (!adminService.deleteUser(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @GetMapping("/codes")
    public List<CodeResponse> listCodes() {
        return adminService.listCodes().stream().map(CodeResponse::from).toList();
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @PostMapping("/codes")
    public ResponseEntity<?> generateCode(@RequestBody GenerateCodeRequest request, Authentication authentication) {
        Code code = adminService.generateCode(request, (User) authentication.getPrincipal());
        return ResponseEntity.status(HttpStatus.CREATED).body(CodeResponse.from(code));
    }

    @PreAuthorize("hasAuthority('PERM_ROOT')")
    @DeleteMapping("/codes/{id}")
    public ResponseEntity<?> deleteCode(@PathVariable UUID id) {
        if (!adminService.deleteCode(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    private boolean isSelf(UUID id, Authentication authentication) {
        return ((User) authentication.getPrincipal()).getId().equals(id);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
