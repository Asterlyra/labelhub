CREATE TABLE role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(64) NOT NULL,
    role_key VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    create_time TIMESTAMP
);

CREATE TABLE permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    perm_key VARCHAR(128) NOT NULL,
    perm_name VARCHAR(128)
);

CREATE TABLE role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL
);

CREATE TABLE user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL
);

CREATE TABLE user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(64),
    email VARCHAR(128),
    phone VARCHAR(32),
    avatar VARCHAR(255),
    status INT,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    deleted INT DEFAULT 0
);

CREATE TABLE dataset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    source_format VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    total_count INT,
    success_count INT,
    failed_count INT,
    schema_json CLOB,
    created_by BIGINT,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    deleted INT DEFAULT 0
);

CREATE TABLE dataset_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    item_key VARCHAR(128),
    row_index INT,
    data_type VARCHAR(32),
    content_json CLOB,
    parse_status VARCHAR(32),
    error_message VARCHAR(500),
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    deleted INT DEFAULT 0
);

CREATE TABLE template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    status INT,
    creator_id BIGINT,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    deleted INT DEFAULT 0
);

CREATE TABLE material (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    type VARCHAR(64) NOT NULL,
    field VARCHAR(64) NOT NULL,
    label VARCHAR(128) NOT NULL,
    required INT,
    sort_order INT,
    config CLOB,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE TABLE task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    dataset_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    quota INT,
    reward_rule VARCHAR(255),
    deadline TIMESTAMP,
    creator_id BIGINT,
    ai_review_enabled BOOLEAN,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    deleted INT DEFAULT 0
);

CREATE TABLE task_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    tag_name VARCHAR(64) NOT NULL,
    create_time TIMESTAMP
);

CREATE TABLE task_reviewer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    review_order INT NOT NULL
);

CREATE TABLE task_distribution_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    strategy_type VARCHAR(64) NOT NULL,
    config CLOB,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE TABLE task_AIreview_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    review_prompt CLOB,
    dimensions CLOB,
    pass_threshold DECIMAL(10, 2),
    review_threshold DECIMAL(10, 2),
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE TABLE task_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    dataset_item_id BIGINT NOT NULL,
    labeler_id BIGINT,
    status VARCHAR(32) NOT NULL,
    annotation_data CLOB,
    current_review_order INT,
    reviewer_id BIGINT,
    review_comment VARCHAR(1000),
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE TABLE task_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    total_count BIGINT,
    unassigned_count BIGINT,
    claimed_count BIGINT,
    submitted_count BIGINT,
    rejected_count BIGINT,
    reviewed_count BIGINT,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    CONSTRAINT uk_task_progress_task UNIQUE (task_id)
);

CREATE TABLE task_item_flow_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_item_id BIGINT NOT NULL,
    operator_id VARCHAR(64),
    operator_action VARCHAR(64) NOT NULL,
    operator_order INT NOT NULL,
    annotation_snapshot CLOB,
    comment VARCHAR(1000),
    create_time TIMESTAMP
);
