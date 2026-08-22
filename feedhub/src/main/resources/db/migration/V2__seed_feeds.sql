-- A few live feeds so a fresh install has content right after the first
-- refresh cycle. Remove them through DELETE /api/feeds/{id} if not wanted.

insert into feeds (url, title)
values ('https://blog.jetbrains.com/kotlin/feed/', 'Kotlin Blog'),
       ('https://spring.io/blog.atom', 'Spring Blog'),
       ('https://habr.com/ru/rss/hubs/kotlin/articles/?fl=ru', 'Habr / Kotlin'),
       ('https://news.ycombinator.com/rss', 'Hacker News');
