# HeatMap — реестр AI-инструментов PDLC

Полноценное веб-приложение по референсу: реестр AI-инструментов с этапами зрелости
Access → Usage → Habit → Process Standard, фильтрами, разделом «Влияние на метрики»,
авторизацией/регистрацией и разделением ролей администратор/пользователь.

## Стек

- **Backend**: Kotlin + Spring Boot 3.3 (Web, Security, Data JPA), сборка Maven
- **База данных**: файловая **H2** (`backend/data/heatmap.mv.db`) — не требует поднятия отдельной СУБД
- **Миграции**: Liquibase, changelog'и в формате **SQL** (`backend/src/main/resources/db/changelog/sql`)
- **Аутентификация**: JWT (Bearer), роли `ADMIN` / `USER`
- **Frontend**: Vue 3 (Composition API, `<script setup>`) + Vite + Pinia + Vue Router + Axios

## Структура проекта

```
heatmap-project/
├── backend/     # Kotlin/Spring Maven-проект
├── frontend/    # Vue 3 + Vite проект
└── docker-compose.yml
```

## Запуск backend

Требуется JDK 21 и Maven (или используйте `./mvnw`, если добавите wrapper).

```bash
cd backend
mvn spring-boot:run
```

При первом запуске Liquibase создаст схему и заполнит демо-данными
(`backend/src/main/resources/db/changelog/sql/004-seed-data.sql`). Файл базы данных
появится в `backend/data/heatmap.mv.db` — просто удалите папку `data/`, чтобы начать с чистой базы.

Backend поднимется на `http://localhost:8080`.

### Демо-доступы (созданы миграцией)

| Роль          | Логин    | Пароль     |
|---------------|----------|------------|
| Администратор | `admin`  | `admin123` |
| Пользователь  | `ivanov` | `user123`  |

**Обязательно смените эти пароли (или удалите демо-пользователей) перед тем, как выкатывать куда-либо, кроме локальной машины.**

### Переменные окружения (необязательно)

| Переменная         | По умолчанию                                             | Назначение                          |
|--------------------|-----------------------------------------------------------|--------------------------------------|
| `DB_URL`           | `jdbc:h2:file:./data/heatmap;AUTO_SERVER=TRUE`            | Строка подключения к H2              |
| `JWT_SECRET`       | dev-значение из `application.yml`                          | Base64-секрет для подписи JWT        |
| `JWT_ACCESS_TTL_MIN` | `720` (12 часов)                                          | Срок жизни токена, в минутах         |
| `CORS_ORIGINS`     | `http://localhost:5173`                                    | Разрешённые origin для фронтенда     |
| `SERVER_PORT`      | `8080`                                                      | Порт backend                         |

## Запуск frontend

Требуется Node.js 18+.

```bash
cd frontend
npm install
npm run dev
```

Откройте `http://localhost:5173` — запросы к `/api/*` в dev-режиме проксируются на backend
(`vite.config.js`). Для production-сборки:

```bash
npm run build
```

## Запуск через Docker Compose

```bash
docker compose up --build
```

- Frontend: `http://localhost:8081`
- Backend API: `http://localhost:8080`

Данные H2 сохраняются в volume `heatmap-data`, поэтому переживают перезапуск контейнеров.

## Роли и права доступа

- **USER** — просматривает реестр, может отправить новый инструмент («Добавить инструмент»).
  Новая заявка попадает на этап **Access** со статусом «на модерации» и не видна в общих
  вкладках, пока администратор её не одобрит. Пользователь видит свои заявки в блоке
  «Мои заявки на модерации» и может отозвать те, что ещё не рассмотрены.
- **ADMIN** — всё то же самое, плюс раздел **«Администрирование»**:
  - модерация заявок (одобрить / отклонить),
  - управление пользователями (роль, блокировка),
  - редактирование значений в разделе «Влияние на метрики».

## Основные API-эндпоинты

| Метод  | Путь                              | Доступ         | Описание                                  |
|--------|-----------------------------------|----------------|--------------------------------------------|
| POST   | `/api/auth/register`              | публичный      | Регистрация нового пользователя (роль USER)|
| POST   | `/api/auth/login`                 | публичный      | Вход, логин или e-mail + пароль            |
| GET    | `/api/auth/me`                    | авторизован    | Текущий пользователь                       |
| GET    | `/api/tools?tab=&role=&framework=&segment=&search=` | авторизован | Список инструментов по вкладке/фильтрам |
| GET    | `/api/tools/counts`               | авторизован    | Счётчики по вкладкам                       |
| GET    | `/api/tools/stats`                | авторизован    | Данные для карточек статистики             |
| GET    | `/api/tools/filter-options`       | авторизован    | Значения для выпадающих фильтров           |
| GET    | `/api/tools/mine`                 | авторизован    | Свои инструменты (включая заявки)          |
| POST   | `/api/tools`                      | авторизован    | Создать заявку на инструмент               |
| PATCH  | `/api/tools/{id}`                 | владелец/админ | Изменить инструмент                        |
| DELETE | `/api/tools/{id}`                 | владелец/админ | Удалить/отозвать заявку                    |
| GET    | `/api/impact`                     | авторизован    | Блоки «Влияние на метрики»                 |
| GET    | `/api/admin/tools/pending`        | ADMIN          | Очередь модерации                          |
| POST   | `/api/admin/tools/{id}/approve`   | ADMIN          | Одобрить заявку                            |
| POST   | `/api/admin/tools/{id}/reject`    | ADMIN          | Отклонить заявку                           |
| GET    | `/api/admin/users`                | ADMIN          | Список пользователей                       |
| PATCH  | `/api/admin/users/{id}/role`      | ADMIN          | Сменить роль                               |
| PATCH  | `/api/admin/users/{id}/enabled`   | ADMIN          | Заблокировать/разблокировать               |
| PATCH  | `/api/admin/impact/rows/{id}`     | ADMIN          | Изменить значение строки метрики           |

## Важное примечание про сборку в этой сессии

Backend собирался и проверялся здесь только через ручной построчный код-ревью: в этой
песочнице исходящий доступ к Maven Central (repo.maven.apache.org и его зеркалам)
заблокирован политикой сети, поэтому выполнить `mvn package` и получить обратную связь
компилятора не удалось. Код написан на стандартных, давно стабильных API Spring Boot 3.3 /
Kotlin 1.9 / jjwt 0.12, поэтому должен собраться без проблем на машине с обычным доступом
в интернет — но при первом запуске стоит внимательно посмотреть вывод `mvn spring-boot:run`.
Frontend, наоборот, полностью установлен (`npm install`) и собран (`npm run build`) прямо
в этой сессии — ошибок нет.

## Backend не стартует: "IllegalArgumentException: <версия JDK>" при `mvn spring-boot:run`

Это не баг в коде проекта, а известная проблема самого `kotlin-maven-plugin` 1.9.x
(https://youtrack.jetbrains.com/issue/KT-83610): его встроенный парсер версии Java не
понимает очень новые JDK (25, 26...). Если `java -version` в терминале показывает
что-то вроде `26.0.2`, компилятор Kotlin просто падает при старте, ещё до компиляции.

**Быстрое решение** — собирать проект под JDK 21 (проект и так таргетируется на 21):

```bash
brew install openjdk@21   # если ещё не установлен
cd backend
./run.sh                  # сам подставит JAVA_HOME на JDK 21 и запустит mvn spring-boot:run
```

Либо вручную, без скрипта:

```bash
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
mvn spring-boot:run
```

Проверить, какие JDK вообще установлены на Mac: `/usr/libexec/java_home -V`.
