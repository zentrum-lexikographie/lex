-- :name create-zdl-lex-article-table :!
create table if not exists zdl_lex_article (
  id varchar(255) not null,
  status varchar(32) not null,
  type varchar(32) not null,
  pos varchar(32),
  provenance varchar(32)
  source varchar(32),
  primary key (id)
);

-- :name insert-zdl-lex-article :!
insert into zdl_lex_article (id, status, type, pos, provenance, source)
values (:id, :status, :type, :pos, :provenance, :source);

-- :name delete-zdl-lex-article :!
delete from zdl_lex_article where id = :id;

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

-- :name create-lex-info-form-table :!
create table if not exists lex_info_form (
  form varchar(255) not null,
  article_id varchar(255) not null
  article_type varchar(32) not null,
  primary key (article_id, article_type, form),
);

-- :name create-lex-info-form-query-index :!
create index if not exists lex_info_form_query_index
  on lex_info_form (form, article_type);

-- :name delete-lex-info-forms :!
delete from lex_info_form
 where article_id = :article_id and article_type = :article_type;
