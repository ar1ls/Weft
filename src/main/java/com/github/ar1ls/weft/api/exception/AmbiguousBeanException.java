package com.github.ar1ls.weft.api.exception;

public class AmbiguousBeanException extends DefinitionException {

    public AmbiguousBeanException(String message) {
        super(message);
    }

    public AmbiguousBeanException(String message, Throwable cause) {
        super(message, cause);
    }

}
