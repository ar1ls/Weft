package com.github.ar1ls.weft.api.exception;

public class MissingBeanException extends WeftException {

    public MissingBeanException(String message) {
        super(message);
    }

    public MissingBeanException(String message, Throwable cause) {
        super(message, cause);
    }

}
