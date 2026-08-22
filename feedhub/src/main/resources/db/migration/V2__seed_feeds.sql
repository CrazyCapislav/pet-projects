-- Несколько живых лент, чтобы после первого запуска сразу было что смотреть.
-- Не нравятся — удали через DELETE /api/feeds/{id}, ничего не сломается.

insert into feeds (url, title)
values ('https://blog.jetbrains.com/kotlin/feed/', 'Kotlin Blog'),
       ('https://spring.io/blog.atom', 'Spring Blog'),
       ('https://habr.com/ru/rss/hubs/kotlin/articles/?fl=ru', 'Habr / Kotlin'),
       ('https://news.ycombinator.com/rss', 'Hacker News');
