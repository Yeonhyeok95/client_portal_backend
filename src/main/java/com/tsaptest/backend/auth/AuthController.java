package com.tsaptest.backend.auth;

import com.tsaptest.backend.user.User;
import com.tsaptest.backend.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record UserInfo(String email, String name, String role) {
    }

    public record LoginResponse(String token, UserInfo user) {
    }

    public record ErrorResponse(String message) {
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Optional<User> found = userRepository.findByEmailIgnoreCase(request.email().trim());
        // 계정 존재 여부가 드러나지 않도록 실패 사유는 하나의 메시지로 통일
        if (found.isEmpty()
                || !passwordEncoder.matches(request.password(), found.get().getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid email or password."));
        }
        User user = found.get();
        String token = tokenService.issue(user);
        return ResponseEntity.ok(new LoginResponse(
                token,
                new UserInfo(user.getEmail(), user.getDisplayName(), user.getRole().name())));
    }
}
