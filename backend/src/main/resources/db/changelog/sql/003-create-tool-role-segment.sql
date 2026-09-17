--liquibase formatted sql

--changeset heatmap:003-create-tool-role-segment
--comment: роль инструмента - множественная (инструмент может относиться сразу к нескольким),
--comment: поэтому хранится как отдельная таблица "многие-ко-многим" (по одной строке на
--comment: каждое значение), а не колонкой в ai_tool. Раньше здесь же создавалась таблица
--comment: tool_segment для поля "Сегмент" - оно убрано полностью (заменено полем "Ограничения",
--comment: см. tool_constraint/tool_framework ниже - тоже множественные, как и роль).
CREATE TABLE tool_role
(
    tool_id UUID        NOT NULL,
    role    VARCHAR(64) NOT NULL,
    CONSTRAINT pk_tool_role PRIMARY KEY (tool_id, role),
    CONSTRAINT fk_tool_role_tool FOREIGN KEY (tool_id) REFERENCES ai_tool (id) ON DELETE CASCADE
);

--changeset heatmap:003-create-tool-constraint
--comment: "Ограничения" - множественное поле (как и роль выше), поэтому тоже отдельная таблица
--comment: "многие-ко-многим", а не колонка в ai_tool (см. AiTool.constraints). Столбец называется
--comment: constraint_value, а не constraint - это слово зарезервировано в SQL.
CREATE TABLE tool_constraint
(
    tool_id         UUID         NOT NULL,
    constraint_value VARCHAR(128) NOT NULL,
    CONSTRAINT pk_tool_constraint PRIMARY KEY (tool_id, constraint_value),
    CONSTRAINT fk_tool_constraint_tool FOREIGN KEY (tool_id) REFERENCES ai_tool (id) ON DELETE CASCADE
);

--changeset heatmap:003-create-tool-framework
--comment: "Агентский фреймворк" - тоже стал множественным полем (как роль и ограничения выше,
--comment: одна и та же форма/фильтр MultiSelectDropdown для всех трёх), поэтому тоже отдельная
--comment: таблица "многие-ко-многим", а не колонка в ai_tool (см. AiTool.framework).
CREATE TABLE tool_framework
(
    tool_id   UUID        NOT NULL,
    framework VARCHAR(64) NOT NULL,
    CONSTRAINT pk_tool_framework PRIMARY KEY (tool_id, framework),
    CONSTRAINT fk_tool_framework_tool FOREIGN KEY (tool_id) REFERENCES ai_tool (id) ON DELETE CASCADE
);
