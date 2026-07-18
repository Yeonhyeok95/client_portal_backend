package com.tsaptest.backend.admin;

import com.tsaptest.backend.admin.AdminUserService.AdminUserDto;
import com.tsaptest.backend.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 계정 관리 API — SecurityConfig에서 /api/admin/**는 ROLE_ADMIN 전용. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    public record CreateUserRequest(
            @NotBlank @Email String email,
            @NotBlank String displayName,
            @NotNull UserRole role,
            @NotBlank @Size(min = 8, max = 72) String initialPassword) {
    }

    public record UpdateEnabledRequest(@NotNull Boolean enabled) {
    }

    public record ErrorResponse(String message) {
    }

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public List<AdminUserDto> list() {
        return adminUserService.listUsers();
    }

    @PostMapping
    public ResponseEntity<AdminUserDto> create(@Valid @RequestBody CreateUserRequest request) {
        AdminUserDto created = adminUserService.createUser(
                request.email(), request.displayName(), request.role(),
                request.initialPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}/enabled")
    public AdminUserDto setEnabled(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable Long id,
                                   @Valid @RequestBody UpdateEnabledRequest request) {
        return adminUserService.setEnabled(actorId(jwt), id, request.enabled());
    }

    /** 임시 비밀번호는 사용자 이메일로만 발송 — 응답 본문 없음(202). */
    @PostMapping("/{id}/password-reset")
    public ResponseEntity<Void> resetPassword(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable Long id) {
        adminUserService.resetPassword(actorId(jwt), id);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable Long id) {
        adminUserService.deleteUser(actorId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private static Long actorId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    @ExceptionHandler(AdminOperationException.class)
    ResponseEntity<ErrorResponse> handleAdminOperation(AdminOperationException e) {
        return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getMessage()));
    }
}
