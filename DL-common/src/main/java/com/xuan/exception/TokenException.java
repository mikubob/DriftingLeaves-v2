package com.xuan.exception;

public class TokenException extends RuntimeException {
    public TokenException() {
    }

    public TokenException(String msg) {
        super(msg);
    }

    public TokenException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
