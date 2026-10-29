ALTER TABLE agent_audit_results
  ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT FIRST,
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (id),
  ADD UNIQUE KEY uk_agent_audit_results_task_item_id (task_item_id);

ALTER TABLE agent_annotation_suggestions
  ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT FIRST,
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (id),
  ADD UNIQUE KEY uk_agent_annotation_suggestions_suggestion_id (suggestion_id);
