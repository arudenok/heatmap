--liquibase formatted sql

--changeset heatmap:003-create-tool-role-segment
--comment: роль и сегмент инструмента - множественные (инструмент может относиться сразу
--comment: к нескольким), поэтому хранятся как отдельные таблицы "многие-ко-многим"
--comment: (по одной строке на каждое значение), а не колонками в ai_tool.
CREATE TABLE tool_role
(
    tool_id BIGINT      NOT NULL,
    role    VARCHAR(64) NOT NULL,
    CONSTRAINT pk_tool_role PRIMARY KEY (tool_id, role),
    CONSTRAINT fk_tool_role_tool FOREIGN KEY (tool_id) REFERENCES ai_tool (id) ON DELETE CASCADE
);

CREATE TABLE tool_segment
(
    tool_id BIGINT      NOT NULL,
    segment VARCHAR(64) NOT NULL,
    CONSTRAINT pk_tool_segment PRIMARY KEY (tool_id, segment),
    CONSTRAINT fk_tool_segment_tool FOREIGN KEY (tool_id) REFERENCES ai_tool (id) ON DELETE CASCADE
);
