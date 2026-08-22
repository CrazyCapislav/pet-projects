-- Full-text search over article title and summary.
--
-- Replaces LIKE '%term%', which matched substrings inside words ("ai" matched
-- "domain", "available") and could not use an index because of the leading
-- wildcard.
--
-- The 'english' configuration stems English words, so "coroutines" also matches
-- "coroutine". Russian titles are indexed without stemming: a single-language
-- configuration is a deliberate trade-off for mixed-language feeds, the
-- alternative being a second tsvector column per language.

create or replace function article_tsv(p_title text, p_summary text)
    returns tsvector
    language sql
    immutable
    parallel safe
as
$$
select to_tsvector('english', coalesce(p_title, '') || ' ' || coalesce(p_summary, ''))
$$;

-- GIN index on the same expression the query uses, so the planner can use it.
create index idx_articles_fts on articles using gin (article_tsv(title, summary));

-- Boolean wrapper callable from JPA Criteria. PostgreSQL inlines simple SQL
-- functions, so the planner still sees the indexed expression.
create or replace function article_matches(p_title text, p_summary text, p_query text)
    returns boolean
    language sql
    immutable
    parallel safe
as
$$
select article_tsv(p_title, p_summary) @@ plainto_tsquery('english', p_query)
$$;
