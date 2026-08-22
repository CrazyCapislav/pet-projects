-- Tags let a subscription belong to several topics ("kotlin", "news") so the
-- article list can be filtered by topic instead of by a single feed.

create table tags
(
    id         bigserial primary key,
    name       varchar(50) not null,
    created_at timestamptz not null default now()
);

-- Tag names are case-insensitive: "Kotlin" and "kotlin" are the same tag.
-- The application lowercases before saving; the index enforces it.
create unique index uk_tags_name on tags (lower(name));

create table feed_tags
(
    feed_id bigint not null references feeds (id) on delete cascade,
    tag_id  bigint not null references tags (id) on delete cascade,
    primary key (feed_id, tag_id)
);

-- Reverse lookup: all feeds carrying a given tag.
create index idx_feed_tags_tag_id on feed_tags (tag_id);
