--liquibase formatted sql

--changeset heatmap:007-seed-users context:for_demo
--comment: демо-пользователи. Логин Сигма состоит только из цифр. Пароли см. в README (admin123 / user123).
--comment: Весь этот файл - демонстрационные данные (не схема), помечены context:for_demo, чтобы их можно
--comment: было легко исключить (spring.liquibase.contexts=!for_demo) при развёртывании на реальных данных.
INSERT INTO app_user (username, password_hash, full_name, role, enabled)
VALUES ('1000', '$2b$10$g5SS9yMyIPS6niJLZ.PRcOQMK0MtGyFUiB20BSY9n3tav7AOmt1uS', 'Администратор реестра', 'ADMIN', TRUE);

INSERT INTO app_user (username, password_hash, full_name, role, enabled)
VALUES ('1001', '$2b$10$3jKB.7hLGjbwx50ShP3WAuMzhKKUyFJhz1iR.Eq6HGj3meQz0DH72', 'Иванов И.И.', 'USER', TRUE);

--changeset heatmap:007-seed-tools context:for_demo
--comment: демонстрационные записи реестра инструментов
INSERT INTO ai_tool (name, description, stage, status, framework, source_label, owner_name, downloads, dau, efficiency_pct, created_by)
VALUES
    ('AutoTest-GPT', 'Генерация тест-кейсов на основе спецификаций', 'ACCESS', 'PUBLISHED', 'Openspec', 'https://github.com/heatmap-tools/autotest-gpt', 'Иванов И.И.', 124, NULL, 92, (SELECT id FROM app_user WHERE username = '1001')),
    ('CodeReview-Agent', 'Автоматический ревью кода с рекомендациями', 'USAGE', 'PUBLISHED', 'Superpowers', 'https://github.com/heatmap-tools/codereview-agent', 'Петрова А.С.', 89, NULL, 87, NULL),
    ('DocAssist', 'Генерация технической документации по коду', 'HABIT', 'PUBLISHED', 'SDD не применим', 'https://github.com/heatmap-tools/docassist', 'Смирнов Д.К.', 1200, 1200, 94, NULL),
    ('TestPilot-AI', 'Автономное тестирование с AI-агентами', 'STANDARD', 'PUBLISHED', 'Openspec', 'https://github.com/heatmap-tools/testpilot-ai', 'Козлов М.А.', 3400, NULL, 98, NULL),
    ('SpecWriter', 'Черновики спецификаций из пользовательских историй', 'ACCESS', 'PENDING', 'Openspec', 'https://github.com/heatmap-tools/specwriter', 'Иванов И.И.', 12, NULL, 71, (SELECT id FROM app_user WHERE username = '1001')),
    ('RefactorBot', 'Подсказки по рефакторингу legacy-кода', 'USAGE', 'PUBLISHED', 'Superpowers', 'https://github.com/heatmap-tools/refactorbot', 'Кузнецова О.В.', 64, NULL, 81, NULL),
    ('DataLens-AI', 'Автоматическая разметка и профилирование датасетов', 'HABIT', 'PUBLISHED', 'SDD не применим', 'https://github.com/heatmap-tools/datalens-ai', 'Волков П.Н.', 940, 610, 88, NULL),
    ('SecScan-Agent', 'Сканирование уязвимостей в пул-реквестах', 'STANDARD', 'PUBLISHED', 'Openspec', 'https://github.com/heatmap-tools/secscan-agent', 'Смирнов Д.К.', 2100, NULL, 95, NULL),
    ('UIComposer', 'Генерация UI-компонентов по макетам', 'ACCESS', 'PENDING', 'Superpowers', 'https://github.com/heatmap-tools/uicomposer', 'Петрова А.С.', 8, NULL, 65, NULL),
    ('LoadForecast-AI', 'Прогноз нагрузки на основе исторических метрик', 'USAGE', 'PUBLISHED', 'SDD не применим', 'https://github.com/heatmap-tools/loadforecast-ai', 'Козлов М.А.', 47, NULL, 79, NULL);

--changeset heatmap:007-seed-tool-roles-segments context:for_demo
--comment: роль/сегмент демо-инструментов (по одной строке на значение - см. 003-create-tool-role-segment.sql)
INSERT INTO tool_role (tool_id, role)
VALUES
    ((SELECT id FROM ai_tool WHERE name = 'AutoTest-GPT'), 'Тестирование'),
    ((SELECT id FROM ai_tool WHERE name = 'CodeReview-Agent'), 'Разработка'),
    ((SELECT id FROM ai_tool WHERE name = 'DocAssist'), 'Аналитика'),
    ((SELECT id FROM ai_tool WHERE name = 'TestPilot-AI'), 'Тестирование'),
    ((SELECT id FROM ai_tool WHERE name = 'SpecWriter'), 'Аналитика'),
    ((SELECT id FROM ai_tool WHERE name = 'RefactorBot'), 'Разработка'),
    ((SELECT id FROM ai_tool WHERE name = 'DataLens-AI'), 'Аналитика'),
    ((SELECT id FROM ai_tool WHERE name = 'SecScan-Agent'), 'Разработка'),
    ((SELECT id FROM ai_tool WHERE name = 'UIComposer'), 'Разработка'),
    ((SELECT id FROM ai_tool WHERE name = 'LoadForecast-AI'), 'Аналитика');

INSERT INTO tool_segment (tool_id, segment)
VALUES
    ((SELECT id FROM ai_tool WHERE name = 'AutoTest-GPT'), 'Backend'),
    ((SELECT id FROM ai_tool WHERE name = 'CodeReview-Agent'), 'Backend'),
    ((SELECT id FROM ai_tool WHERE name = 'DocAssist'), 'Для всех'),
    ((SELECT id FROM ai_tool WHERE name = 'TestPilot-AI'), 'Backend'),
    ((SELECT id FROM ai_tool WHERE name = 'SpecWriter'), 'Для всех'),
    ((SELECT id FROM ai_tool WHERE name = 'RefactorBot'), 'Backend'),
    ((SELECT id FROM ai_tool WHERE name = 'DataLens-AI'), 'Data/ML'),
    ((SELECT id FROM ai_tool WHERE name = 'SecScan-Agent'), 'Security'),
    ((SELECT id FROM ai_tool WHERE name = 'UIComposer'), 'Frontend'),
    ((SELECT id FROM ai_tool WHERE name = 'LoadForecast-AI'), 'Backend');

--changeset heatmap:007-seed-impact-habit context:for_demo
--comment: секция влияния на метрики - блок Habit
INSERT INTO impact_block (code, icon, title, badge_text, badge_status, sort_order)
VALUES ('HABIT', '🔥', 'Habit · Охват и удержание', 'Таргет достигнут', 'ACHIEVED', 1);

INSERT INTO impact_row (block_id, label, metric_value, color_variant, is_footer, sort_order)
VALUES
    ((SELECT id FROM impact_block WHERE code = 'HABIT'), 'Проникновение (скачивания)', '14 230', 'BLUE', FALSE, 1),
    ((SELECT id FROM impact_block WHERE code = 'HABIT'), 'DAU / MAU', '1 420 / 6 800', 'BLUE', FALSE, 2),
    ((SELECT id FROM impact_block WHERE code = 'HABIT'), 'Retention (1 нед / 4 нед)', '64% / 41%', 'GOLD', FALSE, 3),
    ((SELECT id FROM impact_block WHERE code = 'HABIT'), 'Sticky Factor (DAU/MAU)', '20.9% ▲', 'GREEN', FALSE, 4),
    ((SELECT id FROM impact_block WHERE code = 'HABIT'), 'Итог: положительное влияние на продуктивность', 'Да', 'GREEN', TRUE, 5);

--changeset heatmap:007-seed-impact-standard context:for_demo
--comment: секция влияния на метрики - блок Process Standard
INSERT INTO impact_block (code, icon, title, badge_text, badge_status, sort_order)
VALUES ('STANDARD', '⭐', 'Process Standard · Генерация и принятие', 'Таргет выполнен', 'ACHIEVED', 2);

INSERT INTO impact_row (block_id, label, metric_value, color_variant, is_footer, sort_order)
VALUES
    ((SELECT id FROM impact_block WHERE code = 'STANDARD'), 'Объём генерации (строк)', '348 000', 'BLUE', FALSE, 1),
    ((SELECT id FROM impact_block WHERE code = 'STANDARD'), 'Принятого кода в прод', '212 000 (61%)', 'GREEN', FALSE, 2),
    ((SELECT id FROM impact_block WHERE code = 'STANDARD'), 'Сэкономленные кодочасы', '~1 240 ч', 'BLUE', FALSE, 3),
    ((SELECT id FROM impact_block WHERE code = 'STANDARD'), 'Adoption Rate (доля GenAI)', '34%', 'GOLD', FALSE, 4),
    ((SELECT id FROM impact_block WHERE code = 'STANDARD'), 'ЦС по usage / артефакты', '4.8 / 5.0', 'GREEN', TRUE, 5);
