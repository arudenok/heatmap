--liquibase formatted sql

--changeset heatmap:008-create-notification
--comment: уведомления - админам о новых заявках на модерацию, пользователям о результате модерации их заявки
CREATE TABLE notification
(
    id         UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    user_id    UUID         NOT NULL,
    type       VARCHAR(32)  NOT NULL,
    tool_id    UUID,
    tool_name  VARCHAR(255) NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL,
    reason     VARCHAR(1000),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX idx_notification_user_created ON notification (user_id, created_at DESC);
