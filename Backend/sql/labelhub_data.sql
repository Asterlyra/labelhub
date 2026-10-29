SET NAMES utf8mb4;

-- ============================================================
-- LabelHub 数据标注平台 - 初始数据
-- ============================================================

USE `labelhub`;

-- ----------------------------
-- 初始角色
-- ----------------------------
INSERT INTO `role` (`id`, `role_name`, `role_key`, `description`) VALUES
                                                                      (1, '项目管理员', 'PROJECT_ADMIN', '项目管理、分配任务、质量审核、数据导出、系统管理'),
                                                                      (2, '标注员', 'LABELER', '领取任务、执行标注、暂存草稿、提交结果'),
                                                                      (3, '审核员', 'REVIEWER', '审核标注结果、通过/驳回/修正、质量评分');

-- ----------------------------
-- 初始权限
-- ----------------------------
INSERT INTO `permission` (`id`, `perm_name`, `perm_key`, `type`, `parent_id`, `sort_order`) VALUES
-- 项目管理权限
(1, '创建项目', 'project:create', 2, 0, 1),
(2, '编辑项目', 'project:update', 2, 0, 2),
(3, '删除项目', 'project:delete', 2, 0, 3),
(4, '查看项目', 'project:view', 2, 0, 4),
-- 任务管理权限
(5, '创建任务', 'task:create', 2, 0, 5),
(6, '分配任务', 'task:assign', 2, 0, 6),
(7, '查看任务', 'task:view', 2, 0, 7),
(8, '领取任务', 'task:claim', 2, 0, 8),
-- 标注权限
(9, '提交标注', 'annotation:submit', 2, 0, 9),
(10, '查看标注', 'annotation:view', 2, 0, 10),
-- 审核权限
(11, '审核标注', 'review:approve', 2, 0, 11),
(12, '驳回标注', 'review:reject', 2, 0, 12),
-- 数据管理权限
(13, '导入数据', 'data:import', 2, 0, 13),
(14, '导出数据', 'data:export', 2, 0, 14),
-- 系统管理权限
(15, '字典管理', 'system:dict', 2, 0, 15),
(16, '日志查看', 'system:log', 2, 0, 16),
(17, '用户管理', 'system:user', 2, 0, 17);

-- ----------------------------
-- 角色-权限关联
-- ----------------------------
-- PROJECT_ADMIN 拥有所有权限
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
                                                               (1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,13),(1,14),(1,15),(1,16),(1,17);

-- LABELER 拥有查看、标注相关权限
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
                                                               (2,4),(2,7),(2,8),(2,9),(2,10);

-- REVIEWER 拥有查看、审核相关权限
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
                                                               (3,4),(3,7),(3,10),(3,11),(3,12);

-- ----------------------------
-- 初始账号需由部署者安全创建
-- ----------------------------
