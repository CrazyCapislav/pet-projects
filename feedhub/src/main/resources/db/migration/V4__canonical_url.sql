-- Cross-feed deduplication.
--
-- The same article can arrive through several subscriptions: a site-wide feed
-- and a section feed usually overlap. The (feed_id, guid) constraint only
-- prevents duplicates inside one feed, so the article showed up twice.
--
-- canonical_url is the article link stripped of tracking parameters and
-- fragments, which makes the same article comparable across feeds.

alter table articles
    add column canonical_url varchar(2000);

-- Backfill: drop utm_* / fbclid / yclid parameters and the fragment, then clean
-- up a dangling '?' or '&'. New rows get the same value computed in the
-- application, see ArticleIngestService.
update articles
set canonical_url = regexp_replace(
        regexp_replace(
                regexp_replace(link, '#.*$', ''),
                '([?&])(utm_[^&]*|fbclid=[^&]*|yclid=[^&]*|gclid=[^&]*)', '\1', 'g'),
        '[?&]+$', '');

-- Remove duplicates that already accumulated, keeping the earliest row.
delete
from articles a using articles b
where a.canonical_url = b.canonical_url
  and a.canonical_url is not null
  and a.id > b.id;

alter table articles
    alter column canonical_url set not null;

create index idx_articles_canonical_url on articles (canonical_url);
