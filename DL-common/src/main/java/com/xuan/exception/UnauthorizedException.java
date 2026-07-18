package com.xuan.exception;

public class UnauthorizedException extends TokenException{
    public UnauthorizedException() {
    }
    public UnauthorizedException(String msg) {
        super(msg);
    }
}
