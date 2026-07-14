package com.tsaptest.backend.contact;

import com.tsaptest.backend.email.ResendEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ContactEmailService {

    private static final Logger log = LoggerFactory.getLogger(ContactEmailService.class);

    private final ResendEmailSender emailSender;
    private final String adminEmail;

    public ContactEmailService(ResendEmailSender emailSender,
                               @Value("${app.contact.admin-email}") String adminEmail) {
        this.emailSender = emailSender;
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

        try {
            if (emailSender.send(adminEmail, request.email(),
                    "New contact request — " + request.name(), body)) {
                log.info("Contact email sent to {}", adminEmail);
            }
        } catch (Exception e) {
            log.error("Failed to send contact email", e);
        }
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
