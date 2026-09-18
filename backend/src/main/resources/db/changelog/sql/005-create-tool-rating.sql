--liquibase formatted sql

--changeset heatmap:005-create-tool-rating
--comment: оценки инструмента пользователями по 5-балльной шкале - предлагается после скачивания
CREATE TABLE tool_rating
(
    id            UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    tool_id       UUID      NOT NULL,
    user_id       UUID      NOT NULL,
    rating_value  INT       NOT NULL,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    -- ON DELETE CASCADE - удаление инструмента не должно падать на FK, если у него уже
    -- есть оценки (см. ToolService.delete/rate - гонка удаление-vs-оценка под нагрузкой).
    CONSTRAINT fk_tool_rating_tool FOREIGN KEY (tool_id) REFERENCES ai_tool (id) ON DELETE CASCADE,
    CONSTRAINT fk_tool_rating_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uq_tool_rating_tool_user UNIQUE (tool_id, user_id)
);
