package com.tsaptest.backend.admin;

import com.tsaptest.backend.chat.ChatService;
import com.tsaptest.backend.portfolio.PortfolioService;
import com.tsaptest.backend.user.User;
import com.tsaptest.backend.user.UserRepository;
import com.tsaptest.backend.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 계정 관리의 가드 규칙: 자기 자신·ADMIN 계정은 이 API로 건드릴 수 없고,
 * 완전삭제는 연관 데이터(포트폴리오·채팅)를 함께 지운다.
 */
class AdminUserServiceTest {

    private static final long ADMIN_ID = 100L;

    private final PasswordEncoder bcrypt = new BCryptPasswordEncoder(4);

    private UserRepository userRepository;
    private PortfolioService portfolioService;
    private ChatService chatService;
    private TempPasswordMailer mailer;
    private AdminUserService service;

    private User client;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        portfolioService = mock(PortfolioService.class);
        chatService = mock(ChatService.class);
        mailer = mock(TempPasswordMailer.class);
        service = new AdminUserService(
                userRepository, bcrypt, portfolioService, chatService, mailer);

        client = mock(User.class);
        when(client.getId()).thenReturn(1L);
        when(client.getEmail()).thenReturn("client@tsaptest.com");
        when(client.getDisplayName()).thenReturn("Eleanor Whitfield");
        when(client.getRole()).thenReturn(UserRole.CLIENT);
        when(client.isEnabled()).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(client));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- 생성 ----

    @Test
    void createUserRejectsAdminRole() {
        assertThatThrownBy(() -> service.createUser(
                "new-admin@tsaptest.com", "Mallory", UserRole.ADMIN, "Password!123"))
                .isInstanceOf(AdminOperationException.class)
                .satisfies(e -> assertThat(((AdminOperationException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUserRejectsDuplicateEmailWithConflict() {
        when(userRepository.findByEmailIgnoreCase("client@tsaptest.com"))
                .thenReturn(Optional.of(client));

        assertThatThrownBy(() -> service.createUser(
                "Client@TSAPtest.com ", "Dup", UserRole.CLIENT, "Password!123"))
                .isInstanceOf(AdminOperationException.class)
                .satisfies(e -> assertThat(((AdminOperationException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void createUserNormalizesEmailAndHashesPassword() {
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);

        service.createUser("  New.Client@TSAPtest.com ", " Norah Jung ",
                UserRole.ADVISOR, "Password!123");

        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("new.client@tsaptest.com");
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Norah Jung");
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.ADVISOR);
        assertThat(saved.getValue().isEnabled()).isTrue();
        // 평문 저장 금지 — BCrypt 해시로만
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("Password!123");
        assertThat(bcrypt.matches("Password!123", saved.getValue().getPasswordHash())).isTrue();
    }

    // ---- 공통 가드 ----

    @Test
    void cannotModifyOwnAccount() {
        assertThatThrownBy(() -> service.setEnabled(1L, 1L, false))
                .isInstanceOf(AdminOperationException.class)
                .satisfies(e -> assertThat(((AdminOperationException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void cannotModifyAdminAccounts() {
        User otherAdmin = mock(User.class);
        when(otherAdmin.getRole()).thenReturn(UserRole.ADMIN);
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherAdmin));

        assertThatThrownBy(() -> service.deleteUser(ADMIN_ID, 2L))
                .isInstanceOf(AdminOperationException.class);
        assertThatThrownBy(() -> service.setEnabled(ADMIN_ID, 2L, false))
                .isInstanceOf(AdminOperationException.class);
        assertThatThrownBy(() -> service.resetPassword(ADMIN_ID, 2L))
                .isInstanceOf(AdminOperationException.class);
        verify(userRepository, never()).delete(any());
        verify(mailer, never()).sendTempPassword(any(), any(), any());
    }

    @Test
    void unknownTargetReturnsNotFound() {
        assertThatThrownBy(() -> service.setEnabled(ADMIN_ID, 999L, false))
                .isInstanceOf(AdminOperationException.class)
                .satisfies(e -> assertThat(((AdminOperationException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---- 비활성화 / 비밀번호 초기화 / 삭제 ----

    @Test
    void setEnabledTogglesTarget() {
        service.setEnabled(ADMIN_ID, 1L, false);

        verify(client).setEnabled(false);
        verify(userRepository).save(client);
    }

    @Test
    void resetPasswordStoresHashAndEmailsPlainTempPassword() {
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> mailed = ArgumentCaptor.forClass(String.class);

        service.resetPassword(ADMIN_ID, 1L);

        verify(client).setPasswordHash(hash.capture());
        verify(mailer).sendTempPassword(
                eq("client@tsaptest.com"), eq("Eleanor Whitfield"), mailed.capture());
        // 메일로 나간 평문이 저장된 해시와 일치해야 하고, 평문 그대로 저장되면 안 된다
        assertThat(bcrypt.matches(mailed.getValue(), hash.getValue())).isTrue();
        assertThat(hash.getValue()).isNotEqualTo(mailed.getValue());
        assertThat(mailed.getValue()).hasSize(12);
    }

    @Test
    void deleteUserRemovesRelatedDataThenUser() {
        service.deleteUser(ADMIN_ID, 1L);

        verify(portfolioService).deleteDataForUser(1L);
        verify(chatService).deleteDataForUser(1L);
        verify(userRepository).delete(client);
    }
}
