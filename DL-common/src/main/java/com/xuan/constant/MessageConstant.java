package com.xuan.constant;

/**
 * 信息提示常量类
 */
public class MessageConstant {
    public static final String PASSWORD_ERROR = "密码不正确，请检查后重新输入";
    public static final String NEW_PASSWORD_NOT_MATCH = "两次输入的新密码不一致，请重新确认";
    public static final String OLD_PASSWORD_ERROR = "原密码不正确，请检查后重新输入";
    public static final String NEW_PASSWORD_NOT_CHANGE = "新密码不能与原密码相同，请更换后再提交";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在，请检查用户名后重试";
    public static final String UPLOAD_FAILED = "文件上传失败，请稍后重试";
    public static final String FILE_EMPTY = "上传文件为空，请选择有效文件";
    public static final String ALREADY_EXIST = "数据已存在，请勿重复提交";
    public static final String UNKNOWN_ERROR = "服务暂时开小差了，请稍后重试";
    public static final String EMAIL_SEND_ERROR = "邮件验证码发送失败，请检查邮箱地址或稍后重试";
    public static final String VERIFY_CODE_ERROR = "邮件验证码不正确";
    public static final String VERIFY_CODE_LOCK = "验证码输入错误次数过多，已被锁定，请等待";
    public static final String LOGIN_CREDENTIAL_ERROR = "用户名或密码或验证码错误";
    public static final String ACCOUNT_LOCKED = "登录失败次数过多，请等待";
    public static final String NOT_LOGIN = "请先登录后再继续操作";
    public static final String NOT_AUTHORIZED = "登录状态已失效，请重新登录";
    public static final String VISITOR_VERIFY_CODE_ERROR = "游客无须邮箱验证码，请输入：";
    public static final String VISITOR_BLOCKED = "当前访问已被限制，如有疑问请联系站点管理员";
    public static final String INVALID_EMAIL_FORMAT = "邮箱格式不正确，请输入有效邮箱地址";
    public static final String INVALID_QQ_FORMAT = "QQ号格式不正确，请输入有效 QQ 号";
    public static final String EMAIL_OR_QQ_REQUIRED = "请至少填写邮箱或 QQ 号，方便后续联系";
    public static final String RSS_NOT_FOUND = "订阅记录不存在，可能已被取消，请刷新后重试";
    public static final String RSS_ALREADY_EXISTS = "该邮箱已订阅，请勿重复提交";
    public static final String CONFIG_KEY_EXISTS = "配置键已存在，请更换后再保存";
    public static final String ARTICLE_NOT_FOUND = "文章不存在或已被删除，请刷新列表后重试";
    public static final String CATEGORY_NOT_FOUND = "分类不存在或已被删除，请刷新列表后重试";
    public static final String CATEGORY_HAS_ARTICLES = "该分类下还有文章，请先移动或删除相关文章后再删除分类";
    public static final String COMMENT_NOT_FOUND = "评论不存在或已被删除，请刷新后重试";
    public static final String COMMENT_NOT_EDIT = "无权编辑此评论，请确认是否为本人提交";
    public static final String COMMENT_NOT_DELETE = "无权删除此评论，请确认是否为本人提交";
    public static final String GUEST_READ_ONLY = "游客账号仅支持查看，无法进行新增、修改或删除操作";
    public static final String SERVER_MONITOR_ADMIN_ONLY = "服务器信息仅由管理员查看";
    public static final String MESSAGE_NOT_FOUND = "留言不存在或已被删除，请刷新后重试";
    public static final String MESSAGE_NOT_EDIT = "无权编辑此留言，请确认是否为本人提交";
    public static final String MESSAGE_NOT_DELETE = "无权删除此留言，请确认是否为本人提交";

    // 博客端注册相关
    public static final String USERNAME_EXISTS = "用户名已被注册，请更换后重试";
    public static final String EMAIL_EXISTS = "邮箱已被注册，请直接登录或更换邮箱";
    public static final String EMAIL_VERIFY_CODE_ERROR = "邮箱验证码错误或已过期";
    public static final String EMAIL_VERIFY_CODE_LOCKED = "验证码尝试次数过多，已被锁定，请稍后重试";
    public static final String EMAIL_VERIFY_CODE_COOLDOWN = "验证码发送过于频繁，请稍后重试";
    public static final String REGISTER_FAILED = "注册失败，请稍后重试";
    public static final String GUEST_ROLE_NOT_FOUND = "系统角色 GUEST 未初始化，请联系管理员";
}
