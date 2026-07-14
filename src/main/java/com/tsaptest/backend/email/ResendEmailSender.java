package com.tsaptest.backend.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Resend API 공용 발송기 — 문의 폼과 2FA 코드가 함께 쓴다.
 * API 키가 비어 있으면 발송 대신 내용을 로그로 남긴다 (dev 폴백).
 */
@Component
public class ResendEmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    /** 도메인 인증 전 Resend 샌드박스 발신 주소 */
    private static final String FROM = "TSAPtest <onboarding@resend.dev>";

    private final RestClient restClient;
    private final String apiKey;

    public ResendEmailSender(@Value("${app.resend.api-key}") String apiKey) {
        this.restClient = RestClient.builder().baseUrl("https://api.resend.com").build();
        this.apiKey = apiKey;
    }

    /** @return 실제 발송했으면 true, 폴백(로그)이면 false */
    public boolean send(String to, String replyTo, String subject, String text) {
        if (apiKey.isBlank()) {
            log.warn("RESEND_API_KEY not set — email NOT sent. To: {} / Subject: {}\n{}", to, subject, text);
            return false;
        }
        Map<String, Object> payload = replyTo == null
                ? Map.of("from", FROM, "to", List.of(to), "subject", subject, "text", text)
                : Map.of("from", FROM, "to", List.of(to), "reply_to", replyTo,
                        "subject", subject, "text", text);
        restClient.post()
                .uri("/emails")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        return true;
    }
}
