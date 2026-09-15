--liquibase formatted sql

--changeset heatmap:010-add-rejection-reason-to-ai-tool
--comment: Причина отклонения заявки модератором - показывается автору инструмента.
ALTER TABLE ai_tool ADD COLUMN rejection_reason VARCHAR(1000);

--changeset heatmap:010-add-reason-to-notification
--comment: Причина отклонения - снимок в уведомлении об отклонении заявки.
ALTER TABLE notification ADD COLUMN reason VARCHAR(1000);
