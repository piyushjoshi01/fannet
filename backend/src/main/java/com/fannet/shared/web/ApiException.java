package com.fannet.shared.web;

import org.springframework.http.HttpStatus;

/** Base class for expected, client-facing errors. Modules extend it or use the subclasses. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
