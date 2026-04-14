package com.tailor.interfaceservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class PdfParsingException extends RuntimeException {
    public PdfParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
