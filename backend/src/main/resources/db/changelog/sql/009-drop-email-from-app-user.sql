--liquibase formatted sql

--changeset heatmap:009-drop-email-from-app-user
--comment: Логин Сигма - это и есть username, отдельный дублирующий email-столбец убираем.
--comment: У существующих пользователей email хранил настоящий логин Сигма, а username был
--comment: служебным слагом ('admin'/'ivanov') - переносим логин Сигма в username, чтобы вход
--comment: по нему продолжал работать, и только после этого дропаем сам email.
UPDATE app_user SET username = email;
ALTER TABLE app_user DROP CONSTRAINT uq_app_user_email;
ALTER TABLE app_user DROP COLUMN email;
