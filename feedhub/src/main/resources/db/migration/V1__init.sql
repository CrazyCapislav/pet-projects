-- Initial schema.
--
-- Flyway applies migrations in version order and records them in
-- flyway_schema_history together with a checksum. An applied migration is never
-- edited afterwards: the checksum would no longer match and every environment
-- that already ran it would fail to start. Changes go into a new file.

create table feeds
(
    id                   bigserial primary key,
    url                  varchar(2000) not null,
    title                varchar(500),
    site_url             varchar(2000),
    status               varchar(32)   not null default 'ACTIVE',

    -- Conditional GET validators; sending them back lets the server answer 304.
    etag                 varchar(500),
    last_modified        varchar(200),

    last_fetched_at      timestamptz,
    last_success_at      timestamptz,
    consecutive_failures integer       not null default 0,
    last_error           varchar(500),
    created_at           timestamptz   not null default now()
);

-- One subscription per URL. A B-tree index cannot cover varchar(2000) in full,
-- so uniqueness is enforced on the hash of the address.
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

    -- The only reliable guard against duplicates: two concurrent refreshes of
    -- the same feed pass the application-level check at the same time.
    constraint uk_articles_feed_guid unique (feed_id, guid)
);

-- Indexes follow the queries the application actually issues.

-- Article list ordered by publication date.
create index idx_articles_published_at on articles (published_at desc);

-- Same, restricted to one subscription.
create index idx_articles_feed_published on articles (feed_id, published_at desc);

-- Partial index: read articles eventually make up most of the table but never
-- enter this index, so it stays small while serving the most common filter.
create index idx_articles_unread on articles (feed_id) where is_read = false;
