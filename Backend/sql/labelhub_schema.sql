SET NAMES utf8mb4;

-- ============================================================
-- LabelHub 数据标注平台 - 建表脚本
-- ============================================================

CREATE DATABASE IF NOT EXISTS `labelhub` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `labelhub`;

-- ----------------------------
-- 1. 用户与权限表
-- ----------------------------

CREATE TABLE `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    `nickname` VARCHAR(50) COMMENT '昵称',
    `email` VARCHAR(100) COMMENT '邮箱',
    `phone` VARCHAR(20) COMMENT '手机号',
    `avatar` VARCHAR(255) COMMENT '头像URL',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0禁用 1启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE `role` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `role_name` VARCHAR(50) NOT NULL COMMENT '角色名称',
    `role_key` VARCHAR(50) NOT NULL UNIQUE COMMENT '角色标识',
    `description` VARCHAR(255) COMMENT '角色描述',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE `user_role` (
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    PRIMARY KEY (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

CREATE TABLE `permission` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `perm_name` VARCHAR(100) NOT NULL COMMENT '权限名称',
    `perm_key` VARCHAR(100) NOT NULL UNIQUE COMMENT '权限标识',
    `type` TINYINT COMMENT '类型: 1菜单 2按钮',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父权限ID',
    `path` VARCHAR(255) COMMENT '路由路径',
    `icon` VARCHAR(100) COMMENT '图标',
    `sort_order` INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

CREATE TABLE `role_permission` (
    `role_id` BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    PRIMARY KEY (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- ----------------------------
-- 2. 模板搭建器表
-- ----------------------------

CREATE TABLE `template` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `name`        VARCHAR(200) NOT NULL COMMENT '模板名称',
    `description` TEXT COMMENT '模板描述',
    `status`      TINYINT DEFAULT 1 COMMENT '状态: 0禁用 1启用',
    `creator_id`  BIGINT NOT NULL COMMENT '创建人ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX `idx_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标注模板表';

CREATE TABLE `material` (
    `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
    `template_id`  BIGINT NOT NULL COMMENT '所属模板ID',
    `type`         VARCHAR(30) NOT NULL COMMENT '物料类型: input/textarea/radio/checkbox/tagSelect/richText/fileUpload/imageUpload/jsonEditor/llmInteraction/showItem',
    `field`        VARCHAR(100) NOT NULL COMMENT '字段标识（英文，模板内唯一）',
    `label`        VARCHAR(200) NOT NULL COMMENT '显示标签',
    `required`     TINYINT DEFAULT 0 COMMENT '是否必填: 0否 1是',
    `sort_order`   INT DEFAULT 0 COMMENT '排序序号',
    `config`       JSON COMMENT '物料专属配置',
    `create_time`  DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_template` (`template_id`),
    UNIQUE KEY `uk_template_field` (`template_id`, `field`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板物料表';

-- ----------------------------
-- 3. 数据集管理表
-- ----------------------------

CREATE TABLE `dataset` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `name`          VARCHAR(200) NOT NULL COMMENT '数据集名称',
    `description`   TEXT COMMENT '数据集说明',
    `source_format` VARCHAR(20) NOT NULL COMMENT '原始格式: xlsx/json/jsonl',
    `status`        VARCHAR(20) DEFAULT 'UPLOADED' COMMENT '状态: UPLOADED/PARSING/PARSED/FAILED',
    `total_count`   INT DEFAULT 0 COMMENT '总样本数',
    `success_count` INT DEFAULT 0 COMMENT '成功解析数',
    `failed_count`  INT DEFAULT 0 COMMENT '失败解析数',
    `schema_json`   JSON COMMENT '字段映射配置',
    `created_by`    BIGINT NOT NULL COMMENT '上传人ID',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX `idx_creator` (`created_by`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据集表';

CREATE TABLE `dataset_file` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `dataset_id`    BIGINT NOT NULL COMMENT '所属数据集ID',
    `original_name` VARCHAR(500) NOT NULL COMMENT '原始文件名',
    `file_url`      VARCHAR(1000) NOT NULL COMMENT 'MinIO公开访问URL',
    `content_type`  VARCHAR(100) COMMENT 'MIME类型',
    `file_size`     BIGINT COMMENT '文件大小(字节)',
    `file_hash`     VARCHAR(64) COMMENT '文件SHA-256哈希',
    `format`        VARCHAR(20) NOT NULL COMMENT '文件格式: xlsx/json/jsonl',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_dataset` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据集文件表';

CREATE TABLE `dataset_item` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `dataset_id`    BIGINT NOT NULL COMMENT '所属数据集ID',
    `item_key`      VARCHAR(100) COMMENT '原始样本标识',
    `row_index`     INT COMMENT '原始行号',
    `data_type`     VARCHAR(20) NOT NULL COMMENT '数据类型: TEXT/IMAGE/AUDIO/VIDEO/MIXED/JSON',
    `content_json`  JSON NOT NULL COMMENT '映射后的待标注内容',
    `parse_status`  VARCHAR(20) DEFAULT 'SUCCESS' COMMENT '解析状态: SUCCESS/FAILED',
    `error_message` VARCHAR(500) COMMENT '解析错误信息',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX `idx_dataset` (`dataset_id`),
    INDEX `idx_parse_status` (`parse_status`),
    INDEX `idx_data_type` (`data_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据项表';

-- ----------------------------
-- 4. 任务管理表
-- ----------------------------

CREATE TABLE `task` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `title`       VARCHAR(200) NOT NULL COMMENT '任务标题',
    `description` TEXT COMMENT '任务描述',
    `dataset_id`  BIGINT NOT NULL COMMENT '绑定数据集ID',
    `template_id` BIGINT NOT NULL COMMENT '绑定模板ID',
    `status`      VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED/PAUSED/REVIEWING/ENDED',
    `quota`       INT NOT NULL DEFAULT 0 COMMENT '配额，0表示不限',
    `reward_rule` VARCHAR(500) COMMENT '奖励规则',
    `deadline`    DATETIME COMMENT '截止时间',
    `creator_id`  BIGINT NOT NULL COMMENT '创建人ID',
    `ai_review_enabled` TINYINT DEFAULT 0 COMMENT '是否启用AI预审：0否 1是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX `idx_creator` (`creator_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_dataset` (`dataset_id`),
    INDEX `idx_template` (`template_id`),
    INDEX `idx_deadline` (`deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务主表';

CREATE TABLE `task_tag` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `task_id`     BIGINT NOT NULL COMMENT '所属任务ID',
    `tag_name`    VARCHAR(50) NOT NULL COMMENT '标签名称',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_task` (`task_id`),
    INDEX `idx_tag_name` (`tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务标签表';

CREATE TABLE `task_item` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT,
    `task_id`         BIGINT NOT NULL COMMENT '所属任务ID',
    `dataset_item_id` BIGINT NOT NULL COMMENT '关联数据集数据项ID',
    `labeler_id`      BIGINT COMMENT '标注员ID',
    `status`          VARCHAR(20) DEFAULT 'UNASSIGNED' COMMENT '状态: UNASSIGNED/CLAIMED/DRAFT/SUBMITTED/REVIEWING/REJECTED/REVIEWED',
    `annotation_data` JSON COMMENT '标注结果',
    `current_review_order` INT COMMENT '当前审核顺序',
    `reviewer_id`     BIGINT COMMENT '审核员ID',
    `review_comment`  TEXT COMMENT '审核意见',
    `create_time`     DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_task` (`task_id`),
    INDEX `idx_labeler` (`labeler_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_task_status_id` (`task_id`, `status`, `id`),
    INDEX `idx_reviewer` (`reviewer_id`),
    UNIQUE KEY `uk_task_item` (`task_id`, `dataset_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务数据项表';

CREATE TABLE `task_progress` (
    `id`               BIGINT PRIMARY KEY AUTO_INCREMENT,
    `task_id`          BIGINT NOT NULL COMMENT '任务ID',
    `total_count`      BIGINT NOT NULL DEFAULT 0 COMMENT '任务数据项总数',
    `unassigned_count` BIGINT NOT NULL DEFAULT 0 COMMENT '未分配数量',
    `claimed_count`    BIGINT NOT NULL DEFAULT 0 COMMENT '已领取数量',
    `submitted_count`  BIGINT NOT NULL DEFAULT 0 COMMENT '已提交数量',
    `rejected_count`   BIGINT NOT NULL DEFAULT 0 COMMENT '已驳回数量',
    `reviewed_count`   BIGINT NOT NULL DEFAULT 0 COMMENT '已审核数量',
    `create_time`      DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_task_progress_task` (`task_id`),
    INDEX `idx_unassigned_count` (`unassigned_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务进度统计表';

CREATE TABLE `task_reviewer` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `task_id`     BIGINT NOT NULL COMMENT '任务ID',
    `reviewer_id` BIGINT NOT NULL COMMENT '审核员ID',
    `review_order` INT NOT NULL COMMENT '审核顺序',
    INDEX `idx_task` (`task_id`),
    INDEX `idx_reviewer` (`reviewer_id`),
    UNIQUE KEY `uk_task_review_order` (`task_id`, `review_order`),
    UNIQUE KEY `uk_task_reviewer` (`task_id`, `reviewer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务审核员关联表';

CREATE TABLE `task_item_flow_record` (
    `id`                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    `task_item_id`        BIGINT NOT NULL COMMENT '任务项ID',
    `operator_id`         VARCHAR(64) NOT NULL COMMENT '操作人员ID或AI',
    `operator_action`     VARCHAR(32) NOT NULL COMMENT '操作类型：SUBMIT/AI_PRE_REVIEW/APPROVE/REJECT',
    `operator_order`      INT NOT NULL COMMENT '任务项内操作顺序',
    `annotation_snapshot` JSON COMMENT '标注数据快照',
    `comment`             TEXT COMMENT '提交说明、AI结果摘要或审核意见',
    `create_time`         DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_task_item_operator_order` (`task_item_id`, `operator_order`),
    INDEX `idx_task_item` (`task_item_id`),
    INDEX `idx_operator_id` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务项流转记录表';

CREATE TABLE `task_distribution_config` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `task_id`       BIGINT NOT NULL COMMENT '任务ID',
    `strategy_type` VARCHAR(30) NOT NULL COMMENT '分发策略类型',
    `config`        JSON COMMENT '策略配置',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务分发配置表';

CREATE TABLE `task_AIreview_config` (
    `id`               BIGINT PRIMARY KEY AUTO_INCREMENT,
    `task_id`          BIGINT NOT NULL COMMENT '任务ID',
    `review_prompt`    TEXT NOT NULL COMMENT '审核Prompt模板',
    `dimensions`       JSON NOT NULL COMMENT '评分维度配置',
    `pass_threshold`   DECIMAL(5,2) NOT NULL DEFAULT 85.00 COMMENT '自动通过阈值',
    `review_threshold` DECIMAL(5,2) NOT NULL DEFAULT 70.00 COMMENT '人工复审阈值',
    `create_time`      DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务AI预审配置表';

CREATE TABLE `export_job` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `task_id`       BIGINT NOT NULL COMMENT '任务ID',
    `format`        VARCHAR(20) NOT NULL COMMENT '导出格式: json/jsonl/csv/xlsx',
    `status`        VARCHAR(20) NOT NULL COMMENT '状态: PENDING/PROCESSING/SUCCESS/FAILED',
    `config_json`   JSON COMMENT '导出字段映射配置',
    `file_url`      VARCHAR(1000) COMMENT '导出文件URL',
    `error_message` VARCHAR(500) COMMENT '失败原因',
    `created_by`    BIGINT NOT NULL COMMENT '创建人ID',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX `idx_task` (`task_id`),
    INDEX `idx_created_by` (`created_by`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导出任务表';
