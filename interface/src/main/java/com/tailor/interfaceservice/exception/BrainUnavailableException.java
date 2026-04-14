package com.tailor.interfaceservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class BrainUnavailableException extends RuntimeException {
    public BrainUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
