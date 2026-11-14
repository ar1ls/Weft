package com.github.ar1ls.weft.sandbox.beans;

public class MessageFormatter implements Formatter {

    @Override
    public String format(String message) {
        return "[" + message + "]";
    }

}
