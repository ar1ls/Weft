package com.github.ar1ls.weft.api.exception;

public class WeftException extends RuntimeException {

    public WeftException(String message) {
        super(message);
    }

    public WeftException(String message, Throwable cause) {
        super(message, cause);
    }

}
