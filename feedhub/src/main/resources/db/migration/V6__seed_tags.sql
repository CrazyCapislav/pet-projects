-- Tag the feeds seeded in V2 so the tag filter has something to show on a
-- fresh install. Written defensively: the feeds may already have been deleted.

insert into tags (name)
values ('kotlin'),
       ('jvm'),
       ('news')
on conflict do nothing;

insert into feed_tags (feed_id, tag_id)
select f.id, t.id
from feeds f
         join tags t on t.name in ('kotlin', 'jvm')
where f.url = 'https://blog.jetbrains.com/kotlin/feed/'
on conflict do nothing;

insert into feed_tags (feed_id, tag_id)
select f.id, t.id
from feeds f
         join tags t on t.name = 'jvm'
where f.url = 'https://spring.io/blog.atom'
on conflict do nothing;

insert into feed_tags (feed_id, tag_id)
select f.id, t.id
from feeds f
         join tags t on t.name in ('kotlin', 'jvm')
where f.url = 'https://habr.com/ru/rss/hubs/kotlin/articles/?fl=ru'
on conflict do nothing;

insert into feed_tags (feed_id, tag_id)
select f.id, t.id
from feeds f
         join tags t on t.name = 'news'
where f.url = 'https://news.ycombinator.com/rss'
on conflict do nothing;
