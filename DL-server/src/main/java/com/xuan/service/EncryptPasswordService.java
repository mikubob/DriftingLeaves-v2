package com.xuan.service;

/**
 * 密码加密服务
 */
public interface EncryptPasswordService {
    
    /**
     * 计算密码+盐的哈希
     * @param password
     * @param salt
     * @return
     */
    String hashPassword(String password, String salt);
}
