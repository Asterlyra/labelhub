-- 先创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS labelhub_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE labelhub_agent;

-- 表1：审核结果
-- 保留当前主审核字段，同时新增 media_result_json 用于保存图片、视频、markdown 等多媒体审核补充结果
CREATE TABLE IF NOT EXISTS agent_audit_results (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_item_id VARCHAR(128) NOT NULL,
  task_id VARCHAR(128) NOT NULL,
  input_hash VARCHAR(128) NOT NULL DEFAULT '',
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  audit_status VARCHAR(32) NOT NULL DEFAULT '',
  mq_payload_json JSON NULL,
  llm_prompt_text LONGTEXT NULL,
  scores_json JSON NULL,
  summary TEXT NOT NULL,
  confidence FLOAT NOT NULL DEFAULT 0,
  media_result_json JSON NULL,
  weighted_score FLOAT NOT NULL DEFAULT 0,
  attempt_count INT NOT NULL DEFAULT 0,
  last_error TEXT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_audit_results_task_item_id (task_item_id),
  INDEX idx_audit_results_input (task_item_id, input_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 表2：预标注建议
CREATE TABLE IF NOT EXISTS agent_annotation_suggestions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  suggestion_id VARCHAR(128) NOT NULL,
  task_id VARCHAR(128) NOT NULL,
  task_item_id VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  input_hash VARCHAR(128) NOT NULL DEFAULT '',
  request_payload_json JSON NOT NULL,
  annotation_data_json JSON NULL,
  llm_prompt_text LONGTEXT NULL,
  raw_model_output_text LONGTEXT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  last_error TEXT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_annotation_suggestions_suggestion_id (suggestion_id),
  INDEX idx_agent_suggestions_task_item (task_id, task_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
