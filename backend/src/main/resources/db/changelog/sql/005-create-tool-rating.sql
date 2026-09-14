--liquibase formatted sql

--changeset heatmap:005-create-tool-rating
--comment: оценки инструмента пользователями по 5-балльной шкале - предлагается после скачивания
CREATE TABLE tool_rating
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tool_id       BIGINT    NOT NULL,
    user_id       BIGINT    NOT NULL,
    rating_value  INT       NOT NULL,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    CONSTRAINT fk_tool_rating_tool FOREIGN KEY (tool_id) REFERENCES ai_tool (id),
    CONSTRAINT fk_tool_rating_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uq_tool_rating_tool_user UNIQUE (tool_id, user_id)
);
