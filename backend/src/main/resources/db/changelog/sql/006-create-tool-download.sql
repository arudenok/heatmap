--liquibase formatted sql

--changeset heatmap:006-create-tool-download
--comment: уникальные скачивания инструмента пользователем - повторные клики того же пользователя не учитываются
CREATE TABLE tool_download
(
    id         UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    tool_id    UUID      NOT NULL,
    user_id    UUID      NOT NULL,
    created_at TIMESTAMP NOT NULL,
    -- ON DELETE CASCADE - удаление инструмента не должно падать на FK, если у него уже
    -- есть скачивания (см. ToolService.delete/incrementDownload - та же гонка, что и у tool_rating).
    CONSTRAINT fk_tool_download_tool FOREIGN KEY (tool_id) REFERENCES ai_tool (id) ON DELETE CASCADE,
    CONSTRAINT fk_tool_download_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uq_tool_download_tool_user UNIQUE (tool_id, user_id)
);
