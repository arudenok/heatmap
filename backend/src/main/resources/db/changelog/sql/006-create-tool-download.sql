--liquibase formatted sql

--changeset heatmap:006-create-tool-download
--comment: уникальные скачивания инструмента пользователем - повторные клики того же пользователя не учитываются
CREATE TABLE tool_download
(
    id         UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    tool_id    UUID      NOT NULL,
    user_id    UUID      NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_tool_download_tool FOREIGN KEY (tool_id) REFERENCES ai_tool (id),
    CONSTRAINT fk_tool_download_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uq_tool_download_tool_user UNIQUE (tool_id, user_id)
);
