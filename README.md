# ib-lab-1 — защищённый REST API

Лабораторная работа 1: разработка защищённого backend-приложения с автоматической
проверкой кода на уязвимости в CI/CD.

## Стек

| Компонент      | Выбор                                   |
|----------------|-----------------------------------------|
| Язык           | Java 17                                 |
| Сборка         | Gradle (Kotlin DSL)                     |
| Фреймворк      | Spring Boot 4.1.1 (Web MVC, Security)   |
| ORM            | Hibernate / Spring Data JPA             |
| БД             | PostgreSQL 16                           |
| Миграции       | Liquibase                               |
| Токены         | JJWT (HS256)                            |
| Хэширование    | BCrypt (cost 12)                        |
| Санитизация    | OWASP Java HTML Sanitizer + HtmlUtils   |

## API

Все тела запросов и ответов — `application/json`.

### `POST /auth/registration`

```json
{ "login": "alice", "password": "SuperSecret123", "passwordConfirmation": "SuperSecret123" }
```

`201 Created` — пользователь создан. `400 Bad Request` — логин занят, пароли не совпадают
или не пройдена валидация (логин 3–50 символов `[a-zA-Z0-9_]`, пароль от 8 символов без пробелов).

### `POST /auth/login`

```json
{ "login": "alice", "password": "SuperSecret123" }
```

`200 OK`:

```json
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "type": "Bearer", "expiresIn": 3600000, "userId": 1, "login": "alice" }
```

`401 Unauthorized` с телом `{"message": "Bad credentials"}` — и для неверного пароля,
и для несуществующего пользователя (защита от перебора логинов).

### `GET /api/data`

Требует заголовок `Authorization: Bearer <token>`. Параметры: `page` (по умолчанию 0),
`size` (по умолчанию 20, максимум 100). Возвращает список постов, отсортированный по дате создания.

```json
[ { "id": 1, "title": "First post", "content": "Hello world",
    "createdAt": "2026-09-19T12:00:00Z", "author": { "id": 1, "login": "alice" } } ]
```

### `POST /api/data`

Требует заголовок `Authorization: Bearer <token>`.

```json
{ "title": "First post", "content": "Hello world" }
```

`201 Created` — созданный пост. Автор берётся из JWT, а не из тела запроса.

Обращение к `/api/data` без токена, с просроченным или подделанным токеном → `401 Unauthorized`.

## Меры защиты

**SQL-инъекции.** Доступ к БД идёт только через Spring Data JPA / Hibernate
(`UserRepository`, `PostRepository`) — производные методы запросов и биндинг параметров.
Конкатенации строк в SQL нет нигде, нативных запросов нет. Payload вида `' OR '1'='1`
воспринимается как обычная строка (тест `loginIsNotVulnerableToSqlInjection`).

**XSS.** Двухуровневая защита в `HtmlSanitizer`:
на входе (`PostService`) разметка вырезается политикой OWASP-санитайзера, на выходе
(`PostMapper`) все строки экранируются `HtmlUtils.htmlEscape` — ни один ответ API не может
содержать исполняемую разметку. Дополнительно выставляются заголовки `X-Content-Type-Options`,
`X-Frame-Options: DENY`, `Referrer-Policy: no-referrer` и жёсткий CSP (`SecurityConfig`).

**Broken Authentication.**
- Пароли хранятся только в виде BCrypt-хэша (cost 12), поле `passwordHash` помечено `@JsonIgnore`
  и никогда не попадает в ответ.
- При успешном логине выдаётся подписанный JWT (HS256, срок жизни 1 час).
- `JwtAuthenticationFilter` — middleware, проверяющее токен на каждом запросе: подпись,
  срок действия и существование пользователя. Некорректный токен очищает `SecurityContext`.
- Сессий нет (`SessionCreationPolicy.STATELESS`), все эндпоинты кроме `/auth/**` закрыты
  правилом `anyRequest().authenticated()`.
- Секреты (`JWT_SECRET`, пароль БД) читаются из окружения, `.env` в `.gitignore`.
- `GlobalExceptionHandler` не отдаёт наружу стек-трейсы и внутренние сообщения.

## Запуск

```bash
cp .env.example .env      # заполнить значения
docker compose up -d      # PostgreSQL
./gradlew bootRun         # API на http://localhost:8080
```

Схему создаёт Liquibase при старте (`src/main/resources/db/changelog`),
`spring.jpa.hibernate.ddl-auto=none`.

## Тесты

```bash
./gradlew test
```

17 интеграционных тестов покрывают регистрацию (в т.ч. хранение только хэша),
логин, отказ в доступе без токена и с подделанным токеном, экранирование XSS-payload
и обработку SQLi-payload.

## CI/CD

`.github/workflows/ci.yml` — на каждый push и pull request:

| Job                | Что проверяет                                      |
|--------------------|----------------------------------------------------|
| `build`            | Сборка Gradle и весь набор тестов                  |
| `codeql`           | SAST: CodeQL, набор запросов `security-extended`   |
| `semgrep`          | SAST: правила `p/java`, `p/owasp-top-ten`, `p/secrets` |
| `dependency-check` | SCA: OWASP Dependency-Check, падение при CVSS ≥ 7  |
| `secrets`          | Gitleaks: секреты в коде и в истории git           |
