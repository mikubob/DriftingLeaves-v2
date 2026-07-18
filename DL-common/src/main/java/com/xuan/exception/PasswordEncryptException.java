package com.xuan.exception;

/**
 * 密码加密异常
 */
public class PasswordEncryptException extends BaseException {

    public PasswordEncryptException() {
    }

    public PasswordEncryptException(String msg) {
        super(msg);
    }
}
