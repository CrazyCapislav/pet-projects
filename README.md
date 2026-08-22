# Проекты

Рахим Кенжаев — backend-разработчик, Kotlin / Java. Выпускник ИТМО, факультет информатики и вычислительной техники.

Три законченных проекта, каждый решает свой класс задач.

---

## [feedhub](https://github.com/CrazyCapislav/feedhub) — агрегатор RSS-лент

**Kotlin · Spring Boot 4 · PostgreSQL · Coroutines**

Сервис подписывается на источники, в фоне параллельно их опрашивает, дедуплицирует статьи и отдаёт единую ленту через REST API.

Задача выбрана ради конкурентности: обход двадцати лент занимает шесть секунд последовательно и меньше секунды параллельно. Внутри — fan-out на корутинах с ограничением одновременных загрузок, условный GET, чёткая граница между неблокирующей работой с сетью и блокирующей записью в базу, полнотекстовый поиск PostgreSQL и дедупликация по канонической ссылке.

35 тестов · GitHub Actions · Docker · OpenAPI

---

## [bookswap](https://github.com/CrazyCapislav/bookswap) — платформа обмена книгами

**Java 21 · Spring Boot · Spring Cloud · PostgreSQL · React**

Один и тот же продукт реализован дважды: как монолит и как система из семи микросервисов. Интерес не в предметной области, а в контрасте между двумя архитектурами.

Что изменилось при разделении: транзакция перестала быть одной, отказ соседнего сервиса стал штатным сценарием — отсюда OpenFeign с fallback и circuit breaker Resilience4j, конфигурация уехала в Config Server, а адреса сервисов — в Eureka.

163 теста · агрегированное покрытие JaCoCo · Docker Compose

---

## [gradus](https://github.com/CrazyCapislav/gradus) — платформа ведения учебных проектов

**TypeScript · Express · Prisma · PostgreSQL · React 19**

Выпускная квалификационная работа. Преподаватель разбивает проект на этапы с дедлайнами, студенты сдают работы, система следит за сроками и рассылает уведомления.

Аутентификация построена всерьёз: пара access/refresh токенов с ротацией, отзыв сессий через базу, подтверждение почты, OAuth 2.0 через Google и ITMO ID, разграничение прав по ролям. Дедлайны обрабатывает cron-задача с флагами идемпотентности — повторная рассылка невозможна даже после перезапуска.

88 тестов · порог покрытия на CI · Docker Compose

---

## Стек

**Языки:** Kotlin, Java, TypeScript, SQL
**Каркасы:** Spring Boot, Spring Cloud, Spring Security, Express, React
**Данные:** PostgreSQL, Hibernate/JPA, Prisma, Flyway
**Инструменты:** Gradle, Maven, Docker, Docker Compose, GitHub Actions, JUnit, Testcontainers, Vitest

[github.com/CrazyCapislav](https://github.com/CrazyCapislav)
