package com.xuan.exception;

/**
 * 账号锁定异常
 * <p>
 * 用于登录失败次数过多后返回锁定剩余时间。
 */
public class AccountLockedException extends BaseException {

    public AccountLockedException() {
    }

    public AccountLockedException(String msg) {
        super(msg);
    }
}
