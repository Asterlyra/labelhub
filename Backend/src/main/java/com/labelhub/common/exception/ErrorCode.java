package com.labelhub.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 通用
    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    PARAM_ERROR(400, "参数错误"),
    RATE_LIMITED(429, "请求过于频繁，请稍后再试"),

    // 认证 11xxx
    NOT_LOGIN(11001, "未登录或登录已过期"),
    NOT_ROLE(11002, "无此角色权限"),
    NOT_PERMISSION(11003, "无此操作权限"),
    ACCOUNT_DISABLED(11004, "账号已被禁用"),
    ACCOUNT_OR_PASSWORD_ERROR(11005, "账号或密码错误"),

    // 用户 12xxx
    USER_NOT_FOUND(12001, "用户不存在"),
    USERNAME_EXISTS(12002, "用户名已存在"),

    // 项目 13xxx
    PROJECT_NOT_FOUND(13001, "项目不存在"),
    NOT_PROJECT_MEMBER(13002, "您不是该项目成员"),

    // 任务 14xxx
    TASK_NOT_FOUND(14001, "任务不存在"),
    TASK_ALREADY_CLAIMED(14002, "该任务已被领取"),

    // 标注 15xxx
    ANNOTATION_NOT_FOUND(15001, "标注结果不存在"),

    // 数据 16xxx
    DATA_IMPORT_FAIL(16001, "数据导入失败"),
    DATA_ITEM_NOT_FOUND(16002, "数据项不存在"),

    // 模板 17xxx
    TEMPLATE_NOT_FOUND(17001, "模板不存在"),
    MATERIAL_TYPE_NOT_SUPPORTED(17002, "不支持的物料类型"),
    MATERIAL_CONFIG_INVALID(17003, "物料配置参数不合法"),

    // Agent 18xxx
    AGENT_REQUEST_FAILED(18001, "Agent请求失败"),
    AGENT_RESPONSE_INVALID(18002, "Agent返回数据格式错误");

    private final int code;
    private final String message;
}
