package com.tsaptest.backend.admin;

import org.springframework.http.HttpStatus;

/** 관리자 작업 거부 사유 — 컨트롤러 @ExceptionHandler가 상태코드+메시지로 변환한다. */
public class AdminOperationException extends RuntimeException {

    private final HttpStatus status;

    public AdminOperationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
