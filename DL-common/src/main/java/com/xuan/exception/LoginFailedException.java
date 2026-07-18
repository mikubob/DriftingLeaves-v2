package com.xuan.exception;

/**
 * 登录失败异常
 * <p>
 * 用于登录阶段统一返回模糊错误提示，避免泄露账号存在性、密码/验证码正确性等敏感信息。
 */
public class LoginFailedException extends BaseException {

    public LoginFailedException() {
    }

    public LoginFailedException(String msg) {
        super(msg);
    }
}
