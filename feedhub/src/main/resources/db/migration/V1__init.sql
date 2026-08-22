-- Схема БД. Flyway применяет миграции по порядку версий и запоминает
-- применённые в таблице flyway_schema_history.
--
-- Правило, которое стоит усвоить сразу: применённую миграцию НИКОГДА не правят.
-- Flyway хранит контрольную сумму файла, и любое изменение задним числом
-- сломает запуск на всех окружениях, где она уже прошла. Нужна правка —
-- пишешь новый файл V2__....sql.

create table feeds
(
    id                    bigserial primary key,
    url                   varchar(2000) not null,
    title                 varchar(500),
    site_url              varchar(2000),
    status                varchar(32)   not null default 'ACTIVE',

    -- Заголовки условного GET: с ними сервер отвечает 304 вместо полного тела.
    etag                  varchar(500),
    last_modified         varchar(200),

    last_fetched_at       timestamptz,
    last_success_at       timestamptz,
    consecutive_failures  integer       not null default 0,
    last_error            varchar(500),
    created_at            timestamptz   not null default now()
);

-- Уникальность по URL: одна подписка на один адрес.
-- Индекс на varchar(2000) целиком PostgreSQL не потянет (лимит строки B-tree),
-- поэтому индексируем хэш адреса — этого достаточно для проверки уникальности.
create unique index uk_feeds_url on feeds (md5(url));

create table articles
(
    id           bigserial primary key,
    feed_id      bigint        not null references feeds (id) on delete cascade,
    guid         varchar(500)  not null,
    title        varchar(1000) not null,
    link         varchar(2000) not null,
    author       varchar(255),
    summary      text,
    published_at timestamptz,
    fetched_at   timestamptz   not null default now(),
    is_read      boolean       not null default false,

    -- Главная защита от дублей. Работает даже когда два параллельных
    -- обновления одной ленты одновременно прошли проверку в коде.
    constraint uk_articles_feed_guid unique (feed_id, guid)
);

-- Индексы под реальные запросы приложения:

-- сортировка ленты по дате публикации (ArticleService.search)
create index idx_articles_published_at on articles (published_at desc);

-- фильтр по конкретной подписке + сортировка внутри неё
create index idx_articles_feed_published on articles (feed_id, published_at desc);

-- частичный индекс: строки с is_read = true в него не попадают.
-- Со временем прочитанного становится 95% таблицы, и такой индекс
-- остаётся крошечным, хотя обслуживает самый частый фильтр.
create index idx_articles_unread on articles (feed_id) where is_read = false;
