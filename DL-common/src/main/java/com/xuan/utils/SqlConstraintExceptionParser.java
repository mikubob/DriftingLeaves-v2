package com.xuan.utils;

import lombok.extern.slf4j.Slf4j;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 约束异常解析工具类
 * 用于从不同数据库的异常消息中提取约束信息
 */
@Slf4j
public class SqlConstraintExceptionParser {

    private static final Pattern MYSQL_DUPLICATE_PATTERN = 
        Pattern.compile("Duplicate entry '([^']+)' for key '([^']+)'");
    
    private static final Pattern POSTGRES_DUPLICATE_PATTERN = 
        Pattern.compile("duplicate key value violates unique constraint \"([^\"]+)\".*Key \\(([^)]+)\\)=\\(([^)]+)\\) already exists", Pattern.DOTALL);
    
    /**
     * 解析 SQL 完整性约束异常
     * 
     * @param exception SQL 完整性约束异常
     * @return 解析结果，如果解析失败返回 null
     */
    public static ConstraintInfo parse(SQLIntegrityConstraintViolationException exception) {
        if (exception == null || exception.getMessage() == null) {
            return null;
        }
        
        String message = exception.getMessage();
        log.debug("解析 SQL 约束异常: {}", message);
        
        ConstraintInfo info = parseMySqlFormat(message);
        if (info != null) {
            return info;
        }
        
        info = parsePostgresFormat(message);
        if (info != null) {
            return info;
        }
        
        log.warn("无法解析 SQL 约束异常消息: {}", message);
        return null;
    }
    
    /**
     * 解析 MySQL 格式的重复键异常
     */
    private static ConstraintInfo parseMySqlFormat(String message) {
        Matcher matcher = MYSQL_DUPLICATE_PATTERN.matcher(message);
        if (matcher.find()) {
            String duplicateValue = matcher.group(1);
            String constraintName = matcher.group(2);
            return new ConstraintInfo(duplicateValue, constraintName, "MySQL");
        }
        return null;
    }
    
    /**
     * 解析 PostgreSQL 格式的重复键异常
     */
    private static ConstraintInfo parsePostgresFormat(String message) {
        Matcher matcher = POSTGRES_DUPLICATE_PATTERN.matcher(message);
        if (matcher.find()) {
            String constraintName = matcher.group(1);
            String columnName = matcher.group(2);
            String duplicateValue = matcher.group(3);
            return new ConstraintInfo(duplicateValue, constraintName, "PostgreSQL");
        }
        return null;
    }
    
    /**
     * 约束信息封装类
     */
    public static class ConstraintInfo {
        private final String duplicateValue;
        private final String constraintName;
        private final String databaseType;
        
        public ConstraintInfo(String duplicateValue, String constraintName, String databaseType) {
            this.duplicateValue = duplicateValue;
            this.constraintName = constraintName;
            this.databaseType = databaseType;
        }
        
        public String getDuplicateValue() {
            return duplicateValue;
        }
        
        public String getConstraintName() {
            return constraintName;
        }
        
        public String getDatabaseType() {
            return databaseType;
        }
        
        @Override
        public String toString() {
            return String.format("ConstraintInfo{value='%s', constraint='%s', db='%s'}", 
                duplicateValue, constraintName, databaseType);
        }
    }
}
