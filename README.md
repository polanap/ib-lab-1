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

**Подделка логов (A09).** Данные, пришедшие от пользователя, попадают в лог только через
`LogSanitizer`: символы CR и LF заменяются, поэтому одно сообщение всегда остаётся одной
строкой и в журнал нельзя дописать фальшивую запись. Применяется в `GlobalExceptionHandler`
и `JwtAuthenticationFilter` — находка `CRLF_INJECTION_LOGS` от Find Security Bugs.

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

## CI/CD с security-сканерами

`.github/workflows/ci.yml` — проверки запускаются автоматически при каждом `push`
в `master`/`main` и при создании pull request (плюс ручной запуск через `workflow_dispatch`).

| Job                | Тип      | Инструмент                                    | Поведение при находках                |
|--------------------|----------|-----------------------------------------------|---------------------------------------|
| `build`            | тесты    | Gradle + JUnit                                 | падает при падении теста              |
| `spotbugs`         | **SAST** | SpotBugs 4.10 + Find Security Bugs 1.14        | падает при любой находке              |
| `dependency-check` | **SCA**  | OWASP Dependency-Check 12.2                    | падает при CVSS ≥ 7 (High/Critical)   |
| `codeql`           | SAST     | CodeQL, набор `security-extended`              | результаты во вкладке Security        |
| `secrets`          | secrets  | Gitleaks                                       | падает при найденном секрете          |

Отчёты всех сканеров выгружаются как артефакты сборки (`actions/upload-artifact`),
SARIF-отчёт SpotBugs дополнительно публикуется в GitHub Code Scanning.

### SAST — SpotBugs + Find Security Bugs

Подключён как gradle-плагин, поэтому запускается одинаково локально и в CI:

```bash
./gradlew spotbugsMain spotbugsTest     # отчёты: build/reports/spotbugs/
```

Настройки в `build.gradle.kts`: максимальная глубина анализа (`Effort.MAX`),
минимальный порог уверенности (`Confidence.LOW`), отчёты HTML + SARIF, `ignoreFailures = false`.

- `config/spotbugs/exclude.xml` — исключения. Только информационные правила
  (`SPRING_ENDPOINT`, `SERVLET_HEADER`) и ограничения API Spring
  (`THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION`), каждое с обоснованием.
  Настоящие находки исправляются в коде, а не подавляются.
- `config/spotbugs/find-sec-bugs-taint.txt` — собственные санитайзеры проекта
  (`HtmlSanitizer`, `LogSanitizer`) объявлены для taint-анализа как `SAFE`,
  иначе анализатор не видит, что данные очищены.

Первый прогон нашёл 13 замечаний, по ним внесены правки:

| Находка                                  | Что сделано                                                       |
|------------------------------------------|-------------------------------------------------------------------|
| `CRLF_INJECTION_LOGS` (×2)               | добавлен `LogSanitizer` — защита от подделки записей в логе (A09) |
| `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` (×2) | приведение principal заменено на проверку `instanceof`       |
| `SE_BAD_FIELD`, `SE_NO_SERIALVERSIONID`  | сущности реализуют `Serializable`, добавлен `serialVersionUID`     |
| `SIC_INNER_SHOULD_BE_STATIC_ANON`        | анонимный класс в тестах заменён на именованный статический        |

Текущее состояние: **0 находок** в `main` и в `test`.

### SCA — OWASP Dependency-Check

Тоже gradle-плагин:

```bash
./gradlew dependencyCheckAnalyze        # отчёты: build/reports/dependency-check-report.html и .json
```

Настройки: `failBuildOnCVSS = 7.0` (сборка падает на High/Critical), сканируется
только `runtimeClasspath`, разобранные ложные срабатывания — в
`config/dependency-check/suppressions.xml` (пока пуст).

Первый запуск скачивает базу NVD целиком и без API-ключа занимает десятки минут.
Ключ выдаётся бесплатно на <https://nvd.nist.gov/developers/request-an-api-key>;
в CI он передаётся через секрет репозитория `NVD_API_KEY`, а база кэшируется
через `actions/cache` (`~/.gradle/dependency-check-data`). Локально:

```bash
NVD_API_KEY=<ключ> ./gradlew dependencyCheckAnalyze
```
