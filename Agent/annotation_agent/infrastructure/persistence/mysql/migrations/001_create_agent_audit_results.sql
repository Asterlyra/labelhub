CREATE TABLE IF NOT EXISTS agent_audit_results (
  task_item_id VARCHAR(128) PRIMARY KEY,
  task_id VARCHAR(128) NOT NULL,
  input_hash VARCHAR(128) NOT NULL DEFAULT '',
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  audit_status VARCHAR(32) NOT NULL DEFAULT '',
  mq_payload_json JSON NULL,
  llm_prompt_text LONGTEXT NULL,
  scores_json JSON NULL,
  summary TEXT NOT NULL,
  confidence FLOAT NOT NULL DEFAULT 0,
  weighted_score FLOAT NOT NULL DEFAULT 0,
  attempt_count INT NOT NULL DEFAULT 0,
  last_error TEXT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_audit_results_input (task_item_id, input_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
