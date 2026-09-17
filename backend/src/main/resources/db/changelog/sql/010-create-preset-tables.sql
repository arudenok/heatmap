--liquibase formatted sql

--changeset heatmap:010-create-preset-role
--comment: Справочник пресетов "Роль / направление" (см. AiTool.roles/PresetRole). Раньше
--comment: варианты для выбора собирались только из уже существующих у инструментов значений
--comment: (см. ToolService.filterOptions) - роль, которую ещё никому не присвоили, нельзя
--comment: было выбрать иначе как через "свой вариант". Теперь базовый набор - реальные строки
--comment: в этой таблице, а не константа в коде сервиса.
CREATE TABLE preset_role
(
    role VARCHAR(64) NOT NULL PRIMARY KEY
);

--changeset heatmap:010-seed-preset-role
--comment: Базовый набор ролей - не демо-данные (в отличие от 007-seed-data-for_demo.sql),
--comment: поэтому без context:for_demo - нужен в любом окружении, не только для демонстрации.
INSERT INTO preset_role (role)
VALUES
    ('Аналитика'),
    ('Разработка'),
    ('Тестирование'),
    ('CJE'),
    ('DevOps'),
    ('Ad-hoc'),
    ('R&D'),
    ('Владелец продукта'),
    ('Delivery Lead'),
    ('Архитектор'),
    ('Сопровождение'),
    ('Дизайнер');

--changeset heatmap:010-create-preset-constraint
--comment: Справочник пресетов "Ограничения" (см. AiTool.constraints/PresetConstraint). Раньше
--comment: список был захардкожен константой PRESET_CONSTRAINTS прямо в ToolService.kt - теперь
--comment: реальные строки в этой таблице, как и preset_role выше.
CREATE TABLE preset_constraint
(
    constraint_value VARCHAR(128) NOT NULL PRIMARY KEY
);

--changeset heatmap:010-seed-preset-constraint
--comment: Значения перенесены как есть, один в один, из прежней константы PRESET_CONSTRAINTS.
INSERT INTO preset_constraint (constraint_value)
VALUES
    ('ЕФС Сотрудники'),
    ('ЕФС ФЛ'),
    ('Мобильные платформы'),
    ('Неплатформенный сегмент Brokerage Controller'),
    ('Неплатформенный сегмент Data'),
    ('ПКАП'),
    ('Портальные решения'),
    ('ППРБ'),
    ('BPM'),
    ('PCI DSS'),
    ('WEB платформа');
