package com.tsaptest.backend.contact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class ContactEmailService {

    private static final Logger log = LoggerFactory.getLogger(ContactEmailService.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String adminEmail;

    public ContactEmailService(@Value("${app.resend.api-key}") String apiKey,
                               @Value("${app.contact.admin-email}") String adminEmail) {
        this.restClient = RestClient.builder().baseUrl("https://api.resend.com").build();
        this.apiKey = apiKey;
        this.adminEmail = adminEmail;
    }

    /**
     * 관리자에게 문의 내용을 이메일로 발송. @Async라 호출자는 기다리지 않으며,
     * 발송 실패는 사용자에게 노출하지 않고 서버 로그로만 남긴다.
     */
    @Async
    public void sendContactNotification(ContactRequest request) {
        String body = """
                New contact request from the website.

                Name: %s
                Email: %s
                Telephone: %s

                Message:
                %s
                """.formatted(
                request.name(),
                request.email(),
                blankToDash(request.phone()),
                blankToDash(request.message()));

        if (apiKey.isBlank()) {
            // dev 폴백: RESEND_API_KEY 미설정 시 발송 대신 내용만 로그로 확인
            log.warn("RESEND_API_KEY not set — contact email NOT sent. Content:\n{}", body);
            return;
        }

        try {
            Map<String, Object> payload = Map.of(
                    // 도메인 인증 전 Resend 샌드박스 발신 주소
                    "from", "TSAPtest Contact <onboarding@resend.dev>",
                    "to", List.of(adminEmail),
                    "reply_to", request.email(),
                    "subject", "New contact request — " + request.name(),
                    "text", body);
            restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Contact email sent to {}", adminEmail);
        } catch (Exception e) {
            log.error("Failed to send contact email", e);
        }
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
