-- :name create-zdl-lex-article-table :!
create table if not exists zdl_lex_article (
  id varchar(255) not null,
  last_modified timestamp not null,
  status varchar(32) not null,
  type varchar(32) not null,
  pos varchar(32),
  provenance varchar(32),
  source varchar(32),
  primary key (id)
);

-- :name insert-zdl-lex-article :!
insert into zdl_lex_article
            (id, last_modified, status, type, pos, provenance, source)
values (:id, :last-modified, :status, :type, :pos, :provenance, :source);

-- :name delete-zdl-lex-article :!
delete from zdl_lex_article where id = :id;

-- :name create-zdl-lex-article-last-modified-index :!
create index if not exists zdl_lex_article_last_modified_index
  on zdl_lex_article (last_modified);

-- :name select-outdated-zdl-lex-articles :*
select id from zdl_lex_article where last_modified < :threshold;

-- :name create-zdl-lex-article-link-table :!
create table if not exists zdl_lex_article_link (
  id varchar(255) not null,
  anchor varchar(255) not null,
  incoming boolean not null,
  primary key (id, anchor, incoming),
  foreign key (id) references zdl_lex_article (id) on delete cascade
);

-- :name insert-zdl-lex-article-link :!
insert into zdl_lex_article_link (id, anchor, incoming)
values (:id, :anchor, :incoming);

-- :name delete-zdl-lex-article-links :!
delete from zdl_lex_article_link where id = :id;

-- :name create-lex-info-form-table :!
create table if not exists lex_info_form (
  form varchar(255) not null,
  article_id varchar(255) not null,
  article_type varchar(32) not null,
  primary key (article_id, article_type, form),
);

-- :name create-lex-info-form-query-index :!
create index if not exists lex_info_form_query_index
  on lex_info_form (form, article_type);

-- :name insert-lex-info-form :!
insert into lex_info_form (form, article_id, article_type)
values (:form, :id, :type);

-- :name delete-lex-info-forms :!
delete from lex_info_form where article_id = :id and article_type = :type;

-- :name select-sample-links :*
select from_article.id as from_id, to_article.id as to_id, from_anchor.anchor
  from zdl_lex_article_link from_anchor
         join zdl_lex_article from_article
             on from_article.id = from_anchor.id
         join zdl_lex_article_link to_anchor
             on from_anchor.anchor = to_anchor.anchor
         join zdl_lex_article to_article
             on to_article.id = to_anchor.id
             and to_article.id <> from_article.id
 where from_anchor.incoming = false and to_anchor.incoming = true
 limit 10;

-- :name select-sample-articles :*
select * from zdl_lex_article_link where incoming = false limit 10;
