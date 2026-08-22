# feedhub

Агрегатор RSS/Atom-лент на Kotlin + Spring Boot. Подписывается на источники,
в фоне параллельно их опрашивает, дедуплицирует статьи и отдаёт ленту через REST API.

Проект писался как учебный: код намеренно прокомментирован там, где встречается
идиома Kotlin, которой нет в Java. Ищи по строке `Kotlin-заметка` — это мини-курс,
разложенный по местам, где фича реально работает, а не абстрактный пример из туториала.

> **Разбираешь код впервые?** Открой [STUDY.md](STUDY.md) — там порядок чтения
> файлов по пути одного запроса, с контрольными вопросами на каждом этапе.

**Стек:** Kotlin 2.3 · Spring Boot 4.1 · PostgreSQL 17 · Flyway · Gradle · Testcontainers · JDK 21

---

## Как запустить

Нужны: JDK 21 и Docker.

```bash
cd feedhub
./gradlew bootRun
```

Spring Boot сам поднимет PostgreSQL из `compose.yaml` (модуль `spring-boot-docker-compose`),
Flyway накатит схему и добавит четыре живые ленты для затравки. Через ~15 секунд
после старта планировщик сделает первый цикл обновления.

Открыть в браузере: **http://localhost:8080** — там веб-интерфейс читалки.
Список подписок со счётчиками непрочитанного, лента статей, поиск, отметки
о прочтении, добавление и удаление лент.

Интерфейс — это один файл [index.html](src/main/resources/static/index.html)
без сборки и фреймворков; он ходит в тот же REST API, что и `curl`. Ради него
не пришлось менять ни строчки на бэкенде — хороший признак того, что API
получился самодостаточным.

Проверить то же самое из консоли:

```bash
curl localhost:8080/actuator/health
curl localhost:8080/api/feeds
curl "localhost:8080/api/articles?size=5"
```

Тесты:

```bash
./gradlew test          # юнит + интеграционные (последним нужен Docker)
```

---

## API

| Метод    | Путь                        | Что делает                                        |
|----------|-----------------------------|---------------------------------------------------|
| `GET`    | `/api/feeds`                | список подписок со счётчиками статей              |
| `GET`    | `/api/feeds/{id}`           | одна подписка                                     |
| `POST`   | `/api/feeds`                | добавить подписку `{"url": "..."}` + сразу скачать |
| `DELETE` | `/api/feeds/{id}`           | удалить подписку вместе со статьями               |
| `POST`   | `/api/feeds/{id}/enable`    | вернуть в строй ленту, выключенную после ошибок   |
| `POST`   | `/api/feeds/{id}/refresh`   | обновить одну ленту сейчас                        |
| `POST`   | `/api/feeds/refresh`        | обновить все ленты сейчас                         |
| `GET`    | `/api/articles`             | лента статей с фильтрами и пагинацией             |
| `GET`    | `/api/articles/{id}`        | одна статья                                       |
| `POST`   | `/api/articles/{id}/read`   | пометить прочитанной                              |
| `DELETE` | `/api/articles/{id}/read`   | снять отметку о прочтении                         |

Параметры `GET /api/articles`: `feedId`, `unreadOnly`, `search`, `page`, `size` (максимум 100).

```bash
curl -X POST localhost:8080/api/feeds \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://blog.jetbrains.com/kotlin/feed/"}'

curl "localhost:8080/api/articles?search=coroutines&unreadOnly=true&size=10"
```

Ошибки отдаются в формате RFC 7807 (Problem Details):

```json
{ "type": "https://feedhub.local/errors/404", "title": "Not Found",
  "status": 404, "detail": "Не найдено: Лента с id=42" }
```

---

## Что где лежит

```
src/main/kotlin/dev/rahim/feedhub/
├── config/
│   ├── RefreshProperties.kt      типобезопасный конфиг из application.yml
│   └── WebClientConfig.kt        настройка HTTP-клиента
├── domain/
│   ├── Feed.kt                   сущность подписки + поведение
│   ├── FeedStatus.kt             ACTIVE / FAILING / DISABLED
│   └── Article.kt                сущность статьи
├── repository/                   Spring Data JPA
├── fetch/                        ⭐ ядро проекта
│   ├── FeedFetcher.kt            suspend-загрузка по HTTP + условный GET
│   ├── RssParser.kt              разбор RSS 2.0 и Atom 1.0
│   ├── FetchResult.kt            sealed-иерархии результатов
│   ├── ArticleIngestService.kt   запись в БД (блокирующая, @Transactional)
│   ├── FeedRefreshService.kt     параллельное обновление на корутинах
│   └── FeedRefreshScheduler.kt   @Scheduled → runBlocking
├── service/                      бизнес-логика, доменные ошибки
└── web/                          контроллеры, DTO, обработчик ошибок
```

---

## Kotlin: где что искать в коде

Проект специально устроен так, чтобы каждая заметная фича языка встречалась
в живом контексте. Открывай файл, читай комментарий рядом — это быстрее,
чем читать про фичу отдельно.

| Что учим | Где смотреть |
|---|---|
| `val` / `var`, null-safety, `?.`, `?:`, `!!` | [Feed.kt](src/main/kotlin/dev/rahim/feedhub/domain/Feed.kt) |
| `data class`, `copy`, деструктуризация | [Dtos.kt](src/main/kotlin/dev/rahim/feedhub/web/dto/Dtos.kt) |
| `sealed interface` + исчерпывающий `when` | [FetchResult.kt](src/main/kotlin/dev/rahim/feedhub/fetch/FetchResult.kt) |
| extension-функции | [RssParser.kt](src/main/kotlin/dev/rahim/feedhub/fetch/RssParser.kt) (низ файла) |
| scope-функции `let` / `apply` / `also` / `takeIf` | RssParser.kt, FeedFetcher.kt, ArticleService.kt |
| `runCatching`, `Result<T>` | [FeedFetcher.kt](src/main/kotlin/dev/rahim/feedhub/fetch/FeedFetcher.kt) |
| корутины: `suspend`, `async`, `awaitAll`, `Semaphore` | [FeedRefreshService.kt](src/main/kotlin/dev/rahim/feedhub/fetch/FeedRefreshService.kt) |
| `withContext(Dispatchers.IO)` — граница с блокирующим кодом | FeedRefreshService.kt, ArticleIngestService.kt |
| `runBlocking` и когда он уместен | [FeedRefreshScheduler.kt](src/main/kotlin/dev/rahim/feedhub/fetch/FeedRefreshScheduler.kt) |
| коллекции: `mapNotNull`, `firstNotNullOfOrNull`, `distinctBy`, `fold` | RssParser.kt, ArticleIngestService.kt, FetchResult.kt |
| `companion object` как фабрика | Dtos.kt |
| дефолтные и именованные аргументы | ArticleController.kt |
| лямбды вместо SAM-интерфейсов | [ArticleService.kt](src/main/kotlin/dev/rahim/feedhub/service/ArticleService.kt) (Specification) |
| имена тестов в обратных кавычках | [RssParserTest.kt](src/test/kotlin/dev/rahim/feedhub/fetch/RssParserTest.kt) |

---

## Три вещи, которые тут стоит понять по-настоящему

Если из проекта вынести только это — он уже окупился.

**1. Граница между корутинами и блокирующим кодом.**
JDBC блокирующий, а `@Transactional` привязан к потоку через `ThreadLocal`.
Поэтому suspend-функция никогда не помечается `@Transactional` напрямую:
корутина может продолжиться в другом потоке и потерять транзакцию. Правильная
схема — `withContext(Dispatchers.IO) { блокирующий @Transactional метод }`.
Смотри `FeedRefreshService` и `ArticleIngestService`. На собеседовании это
спрашивают почти всегда, когда в резюме написано «Kotlin + корутины».

**2. Structured concurrency.**
`coroutineScope { }` не завершится, пока не завершатся все запущенные внутри
корутины, а падение одной отменяет остальные. Потерять запущенную задачу
физически нельзя — в отличие от `ExecutorService`, где забытый `Future`
живёт своей жизнью и молча съедает ресурсы.

**3. Sealed-типы вместо исключений для ожидаемых исходов.**
`FetchOutcome` описывает три нормальных исхода загрузки: тело, 304, ошибка.
Компилятор следит, чтобы `when` покрывал все. Добавишь четвёртый вариант —
код не соберётся, пока не обработаешь его везде. С исключениями такой
гарантии нет: забытый `catch` обнаруживается только в проде.

---

## Грабли, на которые проект уже наступил за тебя

Каждый пункт — реальная строчка в коде, а не теория.

- **`open-in-view: false`** в `application.yml`. Дефолт `true` держит
  Hibernate-сессию открытой на весь HTTP-запрос: ленивые связи подгружаются
  прямо во время сериализации JSON, и N+1 запросов не видно ни в одном логе
  сервиса. Отключается один раз и навсегда.
- **`@EntityGraph(attributePaths = ["feed"])` в `ArticleRepository`.** Прямое
  следствие предыдущего пункта: связь LAZY + `open-in-view: false` = падение
  с `LazyInitializationException` при сборке DTO. Лечится не возвратом
  open-in-view, а явной загрузкой связи одним JOIN. Этот тест в проекте
  реально падал, пока не появился entity graph.
- **`ddl-auto: validate`, а не `update`.** Схемой управляет Flyway. `update`
  однажды молча испортит прод — например, не удалит колонку, но добавит новую,
  и вы месяц будете писать в старую.
- **Уникальный индекс `(feed_id, guid)` в БД, а не только проверка в коде.**
  Два параллельных обновления одной ленты пройдут проверку «нет ли уже такой
  статьи» одновременно. Гарантию даёт только БД.
- **`URI.create(url)` вместо строки** в `WebClient.uri()`. Строка трактуется
  как URI-шаблон, и ссылка с фигурными скобками роняет запрос.
- **Один запрос `findExistingGuids` вместо N проверок.** Классический N+1,
  который на ленте в 50 статей превращается в 50 обращений к БД.
- **`Semaphore` на число параллельных загрузок.** Без него 500 подписок
  означают 500 одновременных HTTP-запросов и бан по IP.
- **Защита от XXE** в `RssParser`. Мы парсим XML, пришедший из интернета:
  без отключения внешних сущностей чужая лента может прочитать файл с нашего сервера.
- **Частичный индекс** `where is_read = false`. Прочитанные статьи со временем
  составят 95% таблицы, но в индекс не попадут — он останется маленьким.

---

## Что делать дальше: план на пару месяцев

Задания идут по возрастанию сложности. Каждое — законченный кусок, после
которого проект остаётся рабочим. Делай по одному и коммить отдельно:
осмысленная история коммитов сама по себе аргумент на собеседовании.

### Уровень 1 — освоиться в Kotlin и Spring

1. **Теги для подписок.** Сущность `Tag`, связь many-to-many с `Feed`,
   фильтр `GET /api/articles?tag=kotlin`. Тренирует связи в JPA и миграции.
2. **Отметить всё прочитанным.** `POST /api/feeds/{id}/read-all` одним
   UPDATE-запросом, а не выгрузкой всех статей в память. Тренирует `@Modifying`.
3. **Полнотекстовый поиск.** Заменить `LIKE '%...%'` на `tsvector` + GIN-индекс
   PostgreSQL. Замерь разницу на 100k статей — это уже разговор про
   производительность, а не про синтаксис.

### Уровень 2 — то, что отличает джуна от мидла

4. **OPML-импорт/экспорт.** Стандартный формат обмена подписками — можно
   залить свой реальный список из любой читалки. Ещё один парсер XML и
   работа с `multipart/form-data`.
5. **Пользователи и JWT.** Spring Security, регистрация, привязка подписок
   к пользователю. Самая ожидаемая тема на собеседовании; без неё pet-проект
   выглядит незаконченным.
6. **Тесты HTTP-загрузки на MockWebServer.** Сейчас `FeedFetcher` не покрыт:
   проверь 200, 304, 404, таймаут и битый XML. Тренирует тестирование
   suspend-функций (`runTest` из `kotlinx-coroutines-test`).
7. **Метрики.** Micrometer-счётчики: сколько лент обновлено, сколько упало,
   гистограмма времени загрузки. Плюс Prometheus и Grafana в `compose.yaml`.

### Уровень 3 — уровень «покажу на собеседовании»

8. **Умное расписание.** Сейчас все ленты опрашиваются с одинаковым интервалом.
   Сделай адаптивный: часто обновляемые — чаще, мёртвые — раз в сутки,
   с экспоненциальным backoff после ошибок.
9. **Кэш и rate limiting.** Redis для кэша ленты статей; ограничение на
   частоту обращений к одному хосту, чтобы не долбить чужой сайт.
10. **CI и деплой.** GitHub Actions: сборка, тесты, ktlint/detekt, сборка
    Docker-образа (`./gradlew bootBuildImage`). Задеплой на любую VPS.
    **Живой URL в резюме стоит больше, чем ещё три фичи.**
11. **Фронтенд.** Минимальный SPA или даже server-side на Thymeleaf.
    Скриншот в README превращает «репозиторий с кодом» в «продукт».

### Чего осознанно нет и почему

Проект не размазан на микросервисы, Kafka и Kubernetes. Для pet-проекта это
антипаттерн: сложность растёт, а понимания не прибавляется. Один хорошо
сделанный сервис с тестами, миграциями, CI и живым URL производит на
собеседовании лучшее впечатление, чем пять пустых микросервисов.

---

## Полезное под рукой

- Логи SQL: раскомментируй `org.hibernate.SQL: DEBUG` в `application.yml`
- Подключиться к БД: `psql -h localhost -U feedhub -d feedhub` (пароль `feedhub`)
- Сбросить БД целиком: `docker compose down -v && ./gradlew bootRun`
- Форматирование кода: настрой ktlint или detekt — это задание 10
