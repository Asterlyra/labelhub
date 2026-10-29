CREATE TABLE IF NOT EXISTS agent_annotation_suggestions (
  suggestion_id VARCHAR(128) PRIMARY KEY,
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
  INDEX idx_agent_suggestions_task_item (task_id, task_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
