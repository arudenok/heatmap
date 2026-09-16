--liquibase formatted sql

--changeset heatmap:002-create-ai-tool
--comment: реестр AI-инструментов PDLC. Роль - множественная, вынесена в отдельную
--comment: таблицу tool_role (см. 003-create-tool-role-segment.sql; поле "Сегмент" и его
--comment: таблица tool_segment убраны полностью - заменены свободным полем "Ограничения").
--comment: description - VARCHAR без указания длины (в H2 это практически неограниченный размер);
--comment: CLOB здесь не подходит - Hibernate не даёт использовать lower() (нужен для поиска) на CLOB-поле.
--comment: плашки "Топ" нет отдельной колонкой - она вычисляется на лету (см. ToolService.computeTopIds).
CREATE TABLE ai_tool
(
    id                UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    description       VARCHAR      NOT NULL,
    short_description VARCHAR(300),
    stage             VARCHAR(16)  NOT NULL DEFAULT 'ACCESS',
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    framework         VARCHAR(64),
    tool_constraints  VARCHAR(500),
    source_label      VARCHAR(128) NOT NULL,
    owner_name     VARCHAR(255) NOT NULL,
    downloads      INTEGER      NOT NULL DEFAULT 0,
    dau            INTEGER,
    efficiency_pct INTEGER      NOT NULL DEFAULT 0,
    views          BIGINT       NOT NULL DEFAULT 0,
    rating_sum     BIGINT       NOT NULL DEFAULT 0,
    ratings_count  INTEGER      NOT NULL DEFAULT 0,
    created_by     UUID,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rejection_reason VARCHAR(1000),
    -- Новое поле "Сегмент" - НЕ то же самое, что старое (см. комментарий выше) - оно
    -- отдельное, видно и редактируется только администратором (обычно на модерации),
    -- поэтому колонка называется admin_segment во избежание путаницы с историческим полем.
    admin_segment  VARCHAR(128),
    CONSTRAINT fk_ai_tool_created_by FOREIGN KEY (created_by) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT ck_ai_tool_stage CHECK (stage IN ('ACCESS', 'USAGE', 'HABIT', 'STANDARD')),
    CONSTRAINT ck_ai_tool_status CHECK (status IN ('PENDING', 'PUBLISHED', 'REJECTED', 'ARCHIVED')),
    CONSTRAINT ck_ai_tool_efficiency CHECK (efficiency_pct BETWEEN 0 AND 100)
);

--changeset heatmap:002-ai-tool-idx-stage
CREATE INDEX idx_ai_tool_stage ON ai_tool (stage);

--changeset heatmap:002-ai-tool-idx-status
CREATE INDEX idx_ai_tool_status ON ai_tool (status);

--changeset heatmap:002-ai-tool-idx-views
CREATE INDEX idx_ai_tool_views ON ai_tool (views);
