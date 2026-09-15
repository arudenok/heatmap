--liquibase formatted sql

--changeset heatmap:011-create-tool-note
--comment: Заметки администраторов к инструменту - внутренняя переписка между администраторами,
--comment: не видна обычным пользователям. Каждая заметка - отдельная запись своего автора,
--comment: поэтому один администратор не затирает заметки другого, а редактировать/удалить
--comment: можно только собственную (см. ToolNoteService). При удалении инструмента заметки
--comment: удаляются вместе с ним (ON DELETE CASCADE).
CREATE TABLE tool_note
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    tool_id    BIGINT        NOT NULL,
    author_id  BIGINT        NOT NULL,
    text       VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP     NOT NULL,
    updated_at TIMESTAMP     NOT NULL,
    CONSTRAINT fk_tool_note_tool FOREIGN KEY (tool_id) REFERENCES ai_tool (id) ON DELETE CASCADE,
    CONSTRAINT fk_tool_note_author FOREIGN KEY (author_id) REFERENCES app_user (id)
);

CREATE INDEX idx_tool_note_tool_created ON tool_note (tool_id, created_at ASC);
