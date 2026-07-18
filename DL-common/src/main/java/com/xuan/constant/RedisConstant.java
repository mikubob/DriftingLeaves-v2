package com.xuan.constant;

public class RedisConstant {

    public static final String ARTICLE_VIEW_COUNT = "article:viewCount";
    public static final String ARTICLE_LIKE_COUNT = "article:likeCount";
    public static final String ARTICLE_LIKE_USER_SET = "article:like:users:";
    public static final String LOCK_LIKE_COUNT_SYNC = "lock:likeCountSync";
    public static final String LOCK_VIEW_COUNT_SYNC = "lock:viewCountSync";
    public static final String RATE_LIMIT_KEY = "visitor:rate:";
    public static final String BLOCKED_KEY = "visitor:blocked:";
    public static final String TOKEN_PREFIX = "token:active:";
    public static final String KEY_VERIFY_CODE_PREFIX = "verify:code:";
    public static final String KEY_RATE_LIMIT_PREFIX = "verify:rate:";
    public static final String KEY_ATTEMPT_COUNT_PREFIX = "verify:attempt:";
    public static final String KEY_LOCK_PREFIX = "verify:lock:";
    public static final String VISITOR_KEY = "visitor:fingerprint:";

    public static final String KEY_LOGIN_ATTEMPT_PREFIX = "login:attempt:ip:";
    public static final String KEY_LOGIN_LOCK_PREFIX = "login:lock:ip:";

    public static final String SERVER_MONITOR_OVERVIEW_TIMELINE = "server:monitor:overview:timeline";
    public static final String SERVER_MONITOR_CURRENT_OVERVIEW = "server:monitor:current:overview";
    public static final String SERVER_MONITOR_CURRENT_LOAD = "server:monitor:current:load";
    public static final String SERVER_MONITOR_CURRENT_CPU = "server:monitor:current:cpu";
    public static final String SERVER_MONITOR_CURRENT_MEMORY = "server:monitor:current:memory";
    public static final String SERVER_MONITOR_CURRENT_DISK_PREFIX = "server:monitor:current:disk:";
    public static final String SERVER_MONITOR_CURRENT_NETWORK_PREFIX = "server:monitor:current:network:";
    public static final String SERVER_MONITOR_CURRENT_DISK_IO_PREFIX = "server:monitor:current:diskio:";
    public static final String SERVER_MONITOR_NETWORK_TIMELINE_PREFIX = "server:monitor:network:timeline:";
    public static final String SERVER_MONITOR_DISK_IO_TIMELINE_PREFIX = "server:monitor:diskio:timeline:";
    public static final String SERVER_MONITOR_DISK_OPTIONS = "server:monitor:current:options:disk";
    public static final String SERVER_MONITOR_NETWORK_OPTIONS = "server:monitor:current:options:network";
    public static final String SERVER_MONITOR_DISK_IO_OPTIONS = "server:monitor:current:options:diskio";

    private RedisConstant() {
    }
}
