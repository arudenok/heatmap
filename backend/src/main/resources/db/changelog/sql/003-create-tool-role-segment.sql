--liquibase formatted sql

--changeset heatmap:003-create-tool-role-segment
--comment: роль инструмента - множественная (инструмент может относиться сразу к нескольким),
--comment: поэтому хранится как отдельная таблица "многие-ко-многим" (по одной строке на
--comment: каждое значение), а не колонкой в ai_tool. Раньше здесь же создавалась таблица
--comment: tool_segment для поля "Сегмент" - оно убрано полностью (заменено полем "Ограничения",
--comment: обычной колонкой ai_tool.tool_constraints, т.к. это не множественный выбор, а свободный текст).
CREATE TABLE tool_role
(
    tool_id UUID        NOT NULL,
    role    VARCHAR(64) NOT NULL,
    CONSTRAINT pk_tool_role PRIMARY KEY (tool_id, role),
    CONSTRAINT fk_tool_role_tool FOREIGN KEY (tool_id) REFERENCES ai_tool (id) ON DELETE CASCADE
);
