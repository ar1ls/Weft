package com.github.ar1ls.weft.api.exception;

public class ShutdownException extends WeftException {

    public ShutdownException(String message) {
        super(message);
    }

    public ShutdownException(String message, Throwable cause) {
        super(message, cause);
    }

}
