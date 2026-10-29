package com.labelhub.common.constant;

public class RedisKeyConstant {

    /** Sa-Token 用户角色列表缓存 */
    public static final String SATOKEN_ROLES = "satoken:roles:";

    /** Sa-Token 用户权限列表缓存 */
    public static final String SATOKEN_PERMS = "satoken:perms:";

    public static final long SATOKEN_TIMEOUT = 60L;

    /** 数据导入进度 */
    public static final String DATA_IMPORT_PROGRESS = "data:import:progress:";

    /** 模板物料缓存 */
    public static final String MATERIAL_CACHE_KEY_PREFIX = "template:materials:";

    /** 模板物料超时时间 */
    public static final long MATERIAL_CACHE_TIMEOUT = 60L;

    /** AI预审任务上下文缓存 */
    public static final String AI_REVIEW_CONTEXT_CACHE_KEY_PREFIX = "ai:review:context:";

    /** AI预审任务上下文超时时间 */
    public static final long AI_REVIEW_CONTEXT_CACHE_TIMEOUT = 60L;
}
