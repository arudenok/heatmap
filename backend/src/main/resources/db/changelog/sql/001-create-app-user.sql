--liquibase formatted sql

--changeset heatmap:001-create-app-user
--comment: таблица пользователей приложения и их ролей. username - это логин Сигма (только цифры),
--comment: отдельного email нет - раньше был, но дублировал логин Сигма и его убрали.
--comment: id - UUID (не BIGINT IDENTITY): DEFAULT RANDOM_UUID() покрывает прямые INSERT (сиды),
--comment: приложение через Hibernate (@GeneratedValue(strategy = GenerationType.UUID)) генерирует id сам.
CREATE TABLE app_user
(
    id            UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT ck_app_user_role CHECK (role IN ('ADMIN', 'USER'))
);
